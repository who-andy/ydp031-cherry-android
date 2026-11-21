package local.cherry.originalui;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Arrays;

/** Display the original process's real pixels; this Activity supplies no dictionary UI. */
public final class OriginalUiActivity extends Activity implements SurfaceHolder.Callback {
 private static final String SOCKET="/data/cherry-runtime/original-ui/run/ui.sock";
 private SurfaceView surface;private TextView state;
 private volatile boolean resumed;private volatile Session active;private volatile Viewport viewport;
 private final boolean[] fingers=new boolean[32];private Channel touchChannel;
 private AudioBridge audioBridge;static volatile boolean visible;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
  surface=new SurfaceView(this);surface.getHolder().addCallback(this);surface.setOnTouchListener((view,event)->touch(event));root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
  state=new TextView(this);state.setTextColor(Color.WHITE);state.setTextSize(15);state.setGravity(Gravity.CENTER);state.setPadding(24,12,24,12);state.setText("正在连接原版词典…");state.setBackgroundColor(0xb0000000);root.addView(state,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
  // Keep the system navigation bar visible: Back and Home always leave this host.
  surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
 }
 @Override protected void onResume(){super.onResume();if(DeviceMode.gaming()){Toast.makeText(this,"游戏模式已暂停扫描，请切回学习模式",Toast.LENGTH_LONG).show();try{startActivity(new android.content.Intent().setClassName("local.cherry.modes","local.cherry.modes.ModeActivity"));}catch(RuntimeException unavailable){}finish();return;}resumed=true;visible=true;ScanService.ensure(this);startIfReady();if(audioBridge==null){audioBridge=new AudioBridge(getCacheDir());audioBridge.start();}}
 @Override protected void onPause(){resumed=false;visible=false;stopAudioBridge();stopSession();super.onPause();}
 @Override protected void onDestroy(){stopAudioBridge();stopSession();super.onDestroy();}
 private void stopAudioBridge(){AudioBridge old=audioBridge;audioBridge=null;if(old!=null)old.close();}
 @Override public void surfaceCreated(SurfaceHolder holder){startIfReady();}
 @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(width>0&&height>0)viewport=new Viewport(width,height);}
 @Override public void surfaceDestroyed(SurfaceHolder holder){stopSession();viewport=null;}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)cancelTouches();}
 private void startIfReady(){if(!resumed||active!=null||!surface.getHolder().getSurface().isValid())return;Session session=new Session();active=session;session.start();}
 private void stopSession(){Session old=active;cancelTouches();active=null;if(old!=null)old.close();Arrays.fill(fingers,false);touchChannel=null;}
 private void status(Session session,String message){runOnUiThread(()->{if(active==session&&resumed){state.setText(message);state.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);}});}
 private void cancelTouches(){Session session=active;if(session!=null)session.send(new TouchQueue.Event(FrameProtocol.CANCEL,0,0,0));Arrays.fill(fingers,false);}
 private boolean send(int action,int pointer,int x,int y){Session session=active;if(session==null||!session.send(new TouchQueue.Event(action,pointer,x,y))){Arrays.fill(fingers,false);return false;}return true;}
 private boolean touch(MotionEvent event){
  Session session=active;Viewport area=viewport;if(session==null||!session.hasFrame||area==null)return false;
  if(touchChannel!=session.channel){Arrays.fill(fingers,false);touchChannel=session.channel;}
  int action=event.getActionMasked(),index=event.getActionIndex();
  if(action==MotionEvent.ACTION_CANCEL){cancelTouches();return true;}
  if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
   int id=event.getPointerId(index);if(id<0||id>=fingers.length)return false;int[] point=area.point(event.getX(index),event.getY(index),false);if(point==null)return false;
   fingers[id]=send(FrameProtocol.DOWN,id,point[0],point[1]);return true;
  }
  if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++){int id=event.getPointerId(i);if(id<0||id>=fingers.length||!fingers[id])continue;int[] point=area.point(event.getX(i),event.getY(i),true);if(point!=null)send(FrameProtocol.MOVE,id,point[0],point[1]);}return true;}
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){int id=event.getPointerId(index);if(id>=0&&id<fingers.length&&fingers[id]){int[] point=area.point(event.getX(index),event.getY(index),true);if(point!=null)send(FrameProtocol.UP,id,point[0],point[1]);fingers[id]=false;}return true;}
  return true;
 }
 private static void closeSocket(LocalSocket socket){if(socket==null)return;try{socket.shutdownInput();}catch(IOException ignored){}try{socket.shutdownOutput();}catch(IOException ignored){}try{socket.close();}catch(IOException ignored){}}
 private final class Channel {
  final LocalSocket socket;final TouchQueue touches=new TouchQueue();volatile boolean closed;
  Channel(LocalSocket s){socket=s;}
  void close(){if(closed)return;closed=true;touches.close();closeSocket(socket);}
 }
 private final class Session {
  final LatestFrames frames=new LatestFrames();final Object channelLock=new Object();
  volatile Channel channel;volatile boolean closed,hasFrame;Thread reader,renderer;
  boolean alive(){return !closed&&resumed&&active==this;}
  void start(){reader=new Thread(this::read,"original-ui-read");renderer=new Thread(this::render,"original-ui-render");reader.start();renderer.start();}
  boolean send(TouchQueue.Event event){Channel current=channel;return alive()&&current!=null&&!current.closed&&current.touches.offer(event);}
  void close(){closed=true;hasFrame=false;final Channel current;synchronized(channelLock){current=channel;channel=null;}if(current!=null)current.close();frames.close();if(reader!=null)reader.interrupt();if(renderer!=null)renderer.interrupt();}
  void read(){
   while(alive()){
    LocalSocket socket=new LocalSocket();Channel attempt=null;byte[] owned=null;
    try{
     status(this,"正在连接原版词典…");socket.connect(new LocalSocketAddress(SOCKET,LocalSocketAddress.Namespace.FILESYSTEM));attempt=new Channel(socket);
     synchronized(channelLock){if(!alive()){attempt.close();return;}channel=attempt;}
     final Channel writeChannel=attempt;new Thread(()->write(writeChannel),"original-ui-input").start();
     DataInputStream input=new DataInputStream(socket.getInputStream());byte[] header=new byte[32];boolean first=true;int previous=0;
     while(alive()&&!attempt.closed){
      input.readFully(header);FrameProtocol.Header parsed=FrameProtocol.parse(header);owned=frames.borrow();if(owned==null)return;input.readFully(owned);
      if(!first&&!FrameProtocol.newer(parsed.sequence,previous)){frames.release(owned);owned=null;continue;}
      previous=parsed.sequence;first=false;frames.publish(owned,parsed.sequence,attempt);owned=null;
     }
    }catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}
    catch(IOException error){if(alive()){hasFrame=false;status(this,error.getMessage()!=null&&error.getMessage().startsWith("原版界面")?error.getMessage():"原版词典尚未连接，正在重试…");}}
    catch(RuntimeException error){if(alive()){hasFrame=false;status(this,"原版界面连接中断，正在重试…");}}
    finally{if(owned!=null)frames.release(owned);if(attempt!=null)attempt.close();else closeSocket(socket);synchronized(channelLock){if(channel==attempt){channel=null;hasFrame=false;}}}
    if(alive())try{Thread.sleep(1000);}catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}
   }
  }
  void write(Channel attempt){
   try{OutputStream output=attempt.socket.getOutputStream();while(alive()&&!attempt.closed){TouchQueue.Event event=attempt.touches.take();if(event==null)return;output.write(FrameProtocol.touch(event.action,event.pointer,event.x,event.y));output.flush();}}
   catch(IOException|InterruptedException|RuntimeException failed){attempt.close();}
  }
  void render(){
   Bitmap bitmap=null;int[] pixels=new int[FrameProtocol.WIDTH*FrameProtocol.HEIGHT];Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
   try{
    bitmap=Bitmap.createBitmap(FrameProtocol.WIDTH,FrameProtocol.HEIGHT,Bitmap.Config.ARGB_8888);
    while(alive()){
     LatestFrames.Frame frame=frames.take();if(frame==null)return;
     try{Channel frameChannel=channel;if(frame.source!=frameChannel||frameChannel==null||frameChannel.closed)continue;FrameProtocol.pixels(frame.data,pixels);bitmap.setPixels(pixels,0,FrameProtocol.WIDTH,0,0,FrameProtocol.WIDTH,FrameProtocol.HEIGHT);
      if(!alive()||!surface.getHolder().getSurface().isValid())continue;Canvas canvas=null;
      try{canvas=surface.getHolder().lockCanvas();if(canvas==null)continue;if(!alive())continue;Viewport area=new Viewport(canvas.getWidth(),canvas.getHeight());canvas.drawColor(Color.BLACK);canvas.drawBitmap(bitmap,null,new RectF(area.left,area.top,area.right,area.bottom),paint);viewport=area;synchronized(channelLock){if(frame.source==channel&&channel!=null&&!channel.closed&&!hasFrame){hasFrame=true;status(this,"");}}}
      finally{if(canvas!=null)surface.getHolder().unlockCanvasAndPost(canvas);}
     }catch(RuntimeException surfaceChanged){if(alive())status(this,"正在恢复原版界面…");}
     finally{frames.release(frame.data);}
    }
   }catch(InterruptedException stopped){Thread.currentThread().interrupt();}
   catch(OutOfMemoryError exhausted){status(this,"显示内存不足，请退出后重试");close();}
   finally{if(bitmap!=null)bitmap.recycle();}
  }
 }
}
