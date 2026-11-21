package local.cherry.originalui;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.*;

/** One cancellable request against the foreground dictionary's warm engine. */
final class TtsJob {
 private final Engine engine;private volatile boolean cancelled;
 TtsJob(Engine owner){engine=owner;}
 void cancel(){cancelled=true;engine.cancel(this);}
 byte[] synthesize(byte[] request)throws Exception{
  JSONObject input=new JSONObject(new String(request,StandardCharsets.UTF_8));
  Object language=input.get("language"),ssml=input.get("ssml"),text=input.get("text");
  if(!(language instanceof Integer)||((Integer)language<0||(Integer)language>1)||!(ssml instanceof Boolean)||!(text instanceof String)||((String)text).isEmpty())throw new IOException("TTS arguments");
  byte[] utf8=((String)text).getBytes(StandardCharsets.UTF_8);if(utf8.length>8192)throw new IOException("TTS text limit");
  return engine.run(this,utf8,(Integer)language,(Boolean)ssml);
 }
 static final class Engine {
  private static final String BASE="/payload/cherry-android/tts/";
  private final Object io=new Object();private Process process;private TtsJob active;private boolean closed;
  private final LinkedHashMap<String,byte[]> cache=new LinkedHashMap<>(4,0.75f,true);private int cacheBytes;
  private synchronized byte[] cached(String key){return cache.get(key);}
  private synchronized void remember(String key,byte[] wav){
   if(closed||wav.length>1048576)return;
   byte[] old=cache.put(key,wav);if(old!=null)cacheBytes-=old.length;cacheBytes+=wav.length;
   while(cache.size()>4||cacheBytes>2097152){Map.Entry<String,byte[]> first=cache.entrySet().iterator().next();cacheBytes-=first.getValue().length;cache.remove(first.getKey());}
  }
  void warm(){Thread t=new Thread(()->{synchronized(io){try{handshake();}catch(Exception ignored){stop();}}},"tts-warmup");t.setDaemon(true);t.start();}
  private synchronized Process ready()throws IOException{
   if(closed)throw new IOException("TTS closed");
   if(process!=null&&process.isAlive())return process;
   final Process child=new ProcessBuilder(BASE+"stock-loader","--library-path",BASE+":/payload/cherry-android/original-ui/base/runtime",BASE+"tts-worker").start();process=child;
   Thread drain=new Thread(()->{try(InputStream err=child.getErrorStream()){byte[] b=new byte[4096];while(err.read(b)!=-1){}}catch(IOException ignored){}},"tts-stderr");drain.setDaemon(true);drain.start();return child;
  }
  private Process acknowledged;
  private Process handshake()throws IOException{
   Process child=ready();if(child!=acknowledged){if(Integer.reverseBytes(new DataInputStream(child.getInputStream()).readInt())!=0x31535454)throw new IOException("TTS handshake");acknowledged=child;}return child;
  }
  byte[] run(TtsJob job,byte[] text,int language,boolean ssml)throws IOException{
   synchronized(io){if(job.cancelled)throw new IOException("TTS cancelled");synchronized(this){if(closed)throw new IOException("TTS closed");active=job;}
    try{
     String key=language+":"+ssml+":"+new String(text,StandardCharsets.UTF_8);byte[] previous=cached(key);if(previous!=null)return previous;
     Process child=handshake();if(job.cancelled)throw new IOException("TTS cancelled");DataOutputStream out=new DataOutputStream(child.getOutputStream());out.writeInt(Integer.reverseBytes(text.length));out.writeInt(Integer.reverseBytes(language));out.writeInt(Integer.reverseBytes(ssml?1:0));out.write(text);out.flush();
     DataInputStream in=new DataInputStream(child.getInputStream());int count=Integer.reverseBytes(in.readInt());if(count<=44||count>3840044)throw new IOException("TTS length");byte[] wav=new byte[count];in.readFully(wav);if(job.cancelled||wav[0]!='R'||wav[1]!='I'||wav[2]!='F'||wav[3]!='F')throw new IOException("TTS audio");remember(key,wav);return wav;
    }catch(IOException error){stop();throw error;}finally{synchronized(this){if(active==job)active=null;}}
   }
  }
  synchronized void cancel(TtsJob job){if(active==job)stop();}
  private synchronized void stop(){if(process!=null)process.destroyForcibly();process=null;}
  synchronized void close(){closed=true;stop();cache.clear();cacheBytes=0;}
 }
}
