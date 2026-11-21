package local.cherry.originalui;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.*;
import java.util.ArrayDeque;
import org.json.*;

/** Plays factory word audio and factory sentence synthesis with actual completion. */
final class AudioBridge implements Closeable {
 private static final String SOCKET="/data/cherry-runtime/original-ui/run/audio.sock";
 private final File cache;private final HandlerThread playbackThread=new HandlerThread("original-audio-player");private final Handler playback;private long requestStarted;
 private volatile boolean closed;private volatile Channel channel;private Thread reader;
 // Only playbackThread accesses these three fields.
 private final TtsJob.Engine speech=new TtsJob.Engine();private TtsJob synthesis;private MediaPlayer player;private AudioProtocol.Command playing;private Channel playingOwner;private File clip;private Runnable prepareTimeout;
 AudioBridge(File cacheDirectory){cache=cacheDirectory;playbackThread.start();playback=new Handler(playbackThread.getLooper());}
 void start(){speech.warm();reader=new Thread(this::receive,"original-audio-reader");reader.start();}
 private static long integer(JSONObject o,String key)throws JSONException,IOException{Object value=o.get(key);if(!(value instanceof Integer)&&!(value instanceof Long))throw new IOException("noninteger audio field");return ((Number)value).longValue();}
 private void receive(){
  while(!closed){
   Channel next=new Channel();channel=next;
   try{
    if(closed)return;next.socket.connect(new LocalSocketAddress(SOCKET,LocalSocketAddress.Namespace.FILESYSTEM));if(closed)return;next.startWriter();InputStream in=next.socket.getInputStream();
    while(!closed&&!next.closed){
     JSONObject header=new JSONObject(LookupRequest.readLine(in));if(integer(header,"version")!=1)throw new IOException("audio version");
     Object kind=header.get("kind"),uuid=header.get("uuid");if(!(kind instanceof String)||!(uuid instanceof String))throw new IOException("audio strings");
     AudioProtocol.Command command=new AudioProtocol.Command((String)kind,(String)uuid,integer(header,"requestId"),integer(header,"bytes"),header.optString("mime"));
     if(command.size>0){command.data=new byte[command.size];new DataInputStream(in).readFully(command.data);}
     if(!next.offer(command))throw new IOException("audio queue full");
    }
   }catch(IOException|JSONException|RuntimeException error){/* A closed connection settles outstanding requests in native. */}
   finally{next.close();if(channel==next)channel=null;playback.post(()->{if(playingOwner==next)finish("interrupted",0);});}
   if(!closed)try{Thread.sleep(1200);}catch(InterruptedException stopped){return;}
  }
 }
 private void runCommand(Channel owner,AudioProtocol.Command command){
  if(closed||owner.closed||channel!=owner)return;
  if("stop".equals(command.kind)){if(command.matches(playing)&&playingOwner==owner)finish("interrupted",0);return;}
  if(playing!=null)finish("interrupted",0);
  playing=command;playingOwner=owner;requestStarted=System.nanoTime();
  if("tts".equals(command.kind)){
   final TtsJob job=new TtsJob(speech);synthesis=job;final byte[] request=command.data;command.data=null;
   prepareTimeout=()->{if(synthesis==job)finish("error",-8);};playback.postDelayed(prepareTimeout,90000);
   Thread worker=new Thread(()->{byte[] result=null;try{result=job.synthesize(request);}catch(Exception error){}final byte[] wav=result;playback.post(()->{if(synthesis!=job||playing!=command||playingOwner!=owner||closed||owner.closed)return;synthesis=null;if(prepareTimeout!=null){playback.removeCallbacks(prepareTimeout);prepareTimeout=null;}if(wav==null){finish("error",-7);return;}command.data=wav;playClip(owner,command,".wav");});},"original-tts");worker.setDaemon(true);worker.start();return;
  }
  playClip(owner,command,".mp3");
 }
 private void playClip(Channel owner,AudioProtocol.Command command,String extension){
  try{
   clip=File.createTempFile("original-audio-",extension,cache);try(FileOutputStream out=new FileOutputStream(clip)){out.write(command.data);}command.data=null;
   final MediaPlayer current=new MediaPlayer();player=current;current.setAudioStreamType(AudioManager.STREAM_MUSIC);
   current.setOnPreparedListener(p->{if(player!=p||playing!=command||closed||owner.closed){if(player==p)finish("interrupted",0);return;}try{if(prepareTimeout!=null){playback.removeCallbacks(prepareTimeout);prepareTimeout=null;}p.start();if("tts".equals(command.kind))android.util.Log.i("CherryTts","playback_started request="+command.id+" elapsed_ms="+((System.nanoTime()-requestStarted)/1000000));}catch(RuntimeException failed){finish("error",-2);}});
   current.setOnCompletionListener(p->{if(player==p&&playing==command)finish("completion",0);});
   current.setOnErrorListener((p,what,extra)->{if(player==p&&playing==command)finish("error",what==0?-3:what);return true;});
   current.setDataSource(clip.getAbsolutePath());prepareTimeout=()->{if(player==current&&playing==command)finish("error",-4);};playback.postDelayed(prepareTimeout,15000);current.prepareAsync();
  }catch(IOException|RuntimeException failed){finish("error",-1);}
 }
 private void finish(String event,int code){
  if(synthesis!=null){synthesis.cancel();synthesis=null;}
  if(prepareTimeout!=null){playback.removeCallbacks(prepareTimeout);prepareTimeout=null;}
  AudioProtocol.Command old=playing;Channel owner=playingOwner;playing=null;playingOwner=null;
  MediaPlayer oldPlayer=player;player=null;if(oldPlayer!=null){try{oldPlayer.setOnPreparedListener(null);oldPlayer.setOnCompletionListener(null);oldPlayer.setOnErrorListener(null);}catch(RuntimeException ignored){}finally{try{oldPlayer.release();}catch(RuntimeException ignored){}}}
  if(clip!=null){clip.delete();clip=null;}
  if(old!=null){old.data=null;if(owner!=null)owner.event(old,event,code);}
 }
 @Override public void close(){if(closed)return;closed=true;speech.close();Channel old=channel;if(old!=null)old.close();if(reader!=null)reader.interrupt();playback.post(()->{finish("interrupted",0);playbackThread.quitSafely();});}
 private final class Channel {
  final LocalSocket socket=new LocalSocket();final ArrayDeque<AudioProtocol.Command> commands=new ArrayDeque<>();final ArrayDeque<byte[]> events=new ArrayDeque<>();volatile boolean closed;boolean draining;
  synchronized boolean offer(AudioProtocol.Command command){if(closed||commands.size()>=2)return false;commands.addLast(command);if(!draining){draining=true;playback.post(this::drain);}return true;}
  void drain(){for(;;){AudioProtocol.Command command;synchronized(this){if(closed){commands.clear();draining=false;return;}command=commands.pollFirst();if(command==null){draining=false;return;}}runCommand(this,command);}}
  void startWriter(){Thread writer=new Thread(()->{try{OutputStream out=socket.getOutputStream();while(!closed){byte[] data;synchronized(this){while(events.isEmpty()&&!closed)wait();if(closed)return;data=events.removeFirst();}out.write(data);out.flush();}}catch(IOException|InterruptedException stopped){close();}},"original-audio-events");writer.start();}
  void event(AudioProtocol.Command request,String kind,int code){
   try{JSONObject event=new JSONObject();event.put("version",1);event.put("kind",kind);event.put("uuid",request.uuid);event.put("requestId",request.id);if(code!=0)event.put("code",code);byte[] data=(event.toString()+"\n").getBytes("UTF-8");synchronized(this){if(closed)return;if(events.size()<16){events.addLast(data);notifyAll();return;}}close();}catch(JSONException|IOException impossible){close();}
  }
  void close(){synchronized(this){if(closed)return;closed=true;commands.clear();events.clear();notifyAll();}ScanBridge.closeSocket(socket);}
 }
}
