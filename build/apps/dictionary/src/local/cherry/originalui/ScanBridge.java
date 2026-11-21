package local.cherry.originalui;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.json.*;

/** Service-owned pen scan -> original stitch/OCR -> original dictionary UI. */
final class ScanBridge implements Closeable {
 interface Listener {void error(ScanBridge source,String message);default void scanStarted(ScanBridge source){}}
 private static final String LOOKUP_SOCKET="/data/cherry-runtime/original-ui/run/lookup.sock";
 private final Listener listener;private final Object lock=new Object(),retryLock=new Object();
 private final ExecutorService work=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"original-ocr-session");t.setDaemon(true);return t;});
 private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"original-lookup-deadline");t.setDaemon(true);return t;});
 private final ArrayList<ScanFrames.Session> sessions=new ArrayList<>();private final ScanSequence sequence=new ScanSequence();
 private volatile boolean closed;private StockEngine engine;private long engineUsedAt;private LocalSocket capture,lookup;private ScanFrames.Session active;private Thread reader;private boolean retryRequested;
 ScanBridge(Listener sink){listener=sink;}
 void start(){
  work.execute(()->{try{readyEngine();}catch(Exception error){if(!closed)report("识别器准备失败，扫描时会重试");}});
  reader=new Thread(this::receive,"original-scan-reader");reader.start();
 }
 void retryCaptureNow(){synchronized(retryLock){retryRequested=true;retryLock.notifyAll();}}
 private void report(String message){if(!closed)listener.error(this,message);}
 static void closeSocket(LocalSocket socket){if(socket==null)return;try{socket.shutdownInput();}catch(IOException ignored){}try{socket.shutdownOutput();}catch(IOException ignored){}try{socket.close();}catch(IOException ignored){}}
 @Override public void close(){
  final StockEngine oldEngine;final LocalSocket oldCapture,oldLookup;
  synchronized(lock){if(closed)return;closed=true;oldEngine=engine;engine=null;oldCapture=capture;capture=null;oldLookup=lookup;lookup=null;if(active!=null)active.cancel();active=null;for(ScanFrames.Session s:sessions)s.cancel();}
  closeSocket(oldCapture);closeSocket(oldLookup);if(oldEngine!=null)oldEngine.close();sequence.reset();work.shutdownNow();deadlines.shutdownNow();retryCaptureNow();if(reader!=null)reader.interrupt();
 }
 // Called exclusively by the serial OCR executor; only close() may concurrently close it.
 private StockEngine readyEngine()throws IOException{
  StockEngine current;long now=System.nanoTime();
  synchronized(lock){if(closed)throw new IOException("扫描已停止");current=engine;if(current!=null&&now-engineUsedAt>240000000000L){engine=null;current.close();current=null;}}
  if(current==null){
   long began=System.nanoTime();current=new StockEngine();
   synchronized(lock){if(closed){current.close();throw new IOException("扫描已停止");}engine=current;}
   try{current.awaitReady();}catch(IOException failed){discardEngine(current);throw failed;}
   System.err.println("CHERRY_SCAN_METRIC worker_ready_ms="+((System.nanoTime()-began)/1000000));
  }
  engineUsedAt=System.nanoTime();return current;
 }
 private void discardEngine(StockEngine failed){if(failed==null)return;synchronized(lock){if(engine==failed)engine=null;}failed.close();}
 private void cancelOwner(Object owner){synchronized(lock){for(ScanFrames.Session s:sessions)if(s.owner==owner)s.cancel();}sequence.reset();}
 private void waitRetry(long attemptStarted)throws InterruptedException{
  // A TIP edge can wake the normal 5 s retry, with a 100 ms rate floor.
  synchronized(retryLock){
   long target=attemptStarted+5000000000L;
   while(!closed){long now=System.nanoTime();long remaining=(retryRequested?attemptStarted+100000000L:target)-now;if(remaining<=0)break;retryLock.wait(Math.max(1,(remaining+999999L)/1000000L));}
   retryRequested=false;
  }
 }
 private void receive(){
  while(!closed){
   long attemptStarted=System.nanoTime();LocalSocket connection=new LocalSocket();ScanFrames.Session current=null;boolean haveSession=false;int session=0;long lastSequence=-1,lastTimestamp=-1;
   try{
    synchronized(lock){if(closed)return;capture=connection;}
    connection.connect(new LocalSocketAddress("cherry.capture",LocalSocketAddress.Namespace.ABSTRACT));if(closed)return;
    OutputStream output=connection.getOutputStream();output.write("CHERRY/1 SCAN\n".getBytes("US-ASCII"));output.flush();
    DataInputStream input=new DataInputStream(connection.getInputStream());byte[] header=new byte[48],pixels=new byte[ScanFrames.SIZE];
    while(!closed){
     input.readFully(header);ScanFrames.Header h=ScanFrames.parse(header);
     if(h.kind==3){byte[] message=new byte[h.bytes];input.readFully(message);break;}
     if(h.kind==1){if(pixels.length<h.bytes)pixels=new byte[h.bytes];input.readFully(pixels,0,h.bytes);}
     boolean pressing=(h.flags&8)!=0;
     if(pressing&&(!haveSession||h.session!=session)){
      if(current!=null){current.cancel();throw new IOException("扫描会话中断，请重新扫描");}
      haveSession=true;session=h.session;lastSequence=-1;lastTimestamp=-1;
      listener.scanStarted(this);
      synchronized(lock){
       if(closed)return;
       if(sessions.size()>=2){System.err.println("CHERRY_SCAN_METRIC sid="+session+" outcome=rejected phase=admission error=SessionBacklogFull down_ns="+h.timestamp);sequence.reset();report("连续扫描过快，本行未接收，请稍后重扫");continue;}
       current=new ScanFrames.Session(session,sequence.begin(h.timestamp),connection,true);sessions.add(current);active=current;
      }
      final ScanFrames.Session task=current;
      try{work.execute(()->recognize(task));}catch(RejectedExecutionException stopped){task.cancel();throw new IOException("扫描已停止",stopped);}
     }
     if(h.kind==2&&!pressing&&haveSession&&h.session==session){if(current!=null){sequence.end(current.line,h.timestamp);current.end();current=null;}haveSession=false;continue;}
     if(h.kind!=1||!pressing||!haveSession||h.session!=session||current==null)continue;
     long seq=Integer.toUnsignedLong(h.sequence);if(seq<=lastSequence||h.timestamp<=lastTimestamp)throw new IOException("扫描图像顺序错误");lastSequence=seq;lastTimestamp=h.timestamp;
     if(!current.offer(pixels,h.stride,h.sequence)&&current.cancelled){System.err.println("CHERRY_SCAN_METRIC sid="+current.id+" outcome=rejected phase=frame_buffer error=FrameBacklogFull");sequence.reset();report("上一行识别较慢，本行缓存已满，请重新扫描本行");current=null;}
    }
   }catch(InterruptedIOException stopped){if(!closed)report("扫描接口连接中断，正在重试");}
   catch(IOException error){if(!closed)report("扫描接口连接中断，正在重试");}
   catch(OutOfMemoryError error){report("扫描内存不足，请分成较短的行");}
   catch(RuntimeException error){report("扫描接口异常，正在重试");}
   finally{cancelOwner(connection);synchronized(lock){if(capture==connection)capture=null;}closeSocket(connection);}
   if(!closed)try{waitRetry(attemptStarted);}catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}
  }
 }
 private void recognize(ScanFrames.Session session){
  ScanDiagnostic diagnostic=new ScanDiagnostic();StockEngine worker=null;boolean finished=false;long started=System.nanoTime(),finishMs=-1,deliveryMs=-1,liftMs=-1;
  String phase="ready",outcome="cancelled",errorKind="none";int chars=0;
  try{
   if(closed||session.cancelled)return;worker=readyEngine();if(closed||session.cancelled)return;
   phase="begin";session.activate();worker.begin(session.id);
   phase="frames";
   for(;;){ScanFrames.Frame frame=session.take();if(frame==null)break;try{diagnostic.frame(frame.pixels);int[] counts=worker.frame(session.id,frame.sequence,frame.pixels);session.received=counts[0];session.submitted=counts[1];session.lastSequence=frame.sequence;}finally{session.release(frame);}}
   if(closed||session.cancelled)return;phase="finish";long finishStarted=System.nanoTime();StockEngine.Result result=worker.finish(session.id,session.lastSequence);diagnostic.finish(result);finished=true;engineUsedAt=System.nanoTime();finishMs=(engineUsedAt-finishStarted)/1000000;
   if(session.line.released>=0)liftMs=(engineUsedAt-session.line.released)/1000000;
   if(closed||session.cancelled)return;
   phase="merge";String merged=sequence.append(session.line,result.text);chars=merged.codePointCount(0,merged.length());
   phase="deliver";long deliveryStarted=System.nanoTime();
   try{deliver(merged);}finally{deliveryMs=(System.nanoTime()-deliveryStarted)/1000000;}
   if(closed||session.cancelled)return;outcome="accepted";phase="complete";
  }catch(ScanSequence.EmptyResultException empty){
   // Preserve the last valid paragraph, but never pretend this line was recognized.
   outcome="empty";errorKind="EmptyResult";if(!closed&&!session.cancelled)report(empty.getMessage());
  }catch(Exception error){
   outcome="error";errorKind=error.getClass().getSimpleName();
   if(!closed&&!session.cancelled){sequence.reset();report(error.getMessage()==null?"识别失败，请重新扫描":error.getMessage());}
  }catch(OutOfMemoryError exhausted){outcome="error";errorKind="OutOfMemoryError";sequence.reset();report("扫描内存不足，请分成较短的行");}
  finally{
   // Always log a terminal outcome, including empty/no-frame/failed sessions.
   // Never include OCR text or exception messages in this diagnostic channel.
   if(closed||session.cancelled)outcome="cancelled";
   long pressed=session.line==null?-1:session.line.pressed,released=session.line==null?-1:session.line.released;
   System.err.println("CHERRY_SCAN_METRIC sid="+session.id+" outcome="+outcome+" phase="+phase+" error="+errorKind+" elapsed_ms="+((System.nanoTime()-started)/1000000)+" finish_ms="+finishMs+" deliver_ms="+deliveryMs+" lift_to_result_ms="+liftMs+" down_ns="+pressed+" up_ns="+released+" received="+session.received+" submitted="+session.submitted+" dropped="+session.dropped+" chars="+chars);
   // A cancelled/failed BEGIN must not leave the worker in an unfinished session.
   if(worker!=null&&!finished)discardEngine(worker);
   synchronized(lock){sessions.remove(session);if(active==session)active=null;}
  }
 }
 private void deliver(String text)throws IOException{
  byte[] request=LookupRequest.encode(text);LocalSocket connection=new LocalSocket();
  synchronized(lock){if(closed)return;lookup=connection;}
  ScheduledFuture<?> timeout=null;
  try{
   timeout=deadlines.schedule(()->closeSocket(connection),3,TimeUnit.SECONDS);
   connection.connect(new LocalSocketAddress(LOOKUP_SOCKET,LocalSocketAddress.Namespace.FILESYSTEM));if(closed)return;
   connection.getOutputStream().write(request);connection.getOutputStream().flush();
   String response=LookupRequest.readLine(connection.getInputStream());JSONObject acknowledgment;
   try{acknowledgment=new JSONObject(response);}catch(JSONException malformed){throw new IOException("原版词典回复格式错误",malformed);}
   // The final ACK schema is checked here; accepted means queued, never rendered.
   if(!"accepted".equals(acknowledgment.optString("status")))throw new IOException("原版词典暂未接收本行，请重扫");
  }catch(RejectedExecutionException stopped){if(!closed)throw new IOException("扫描结果发送已停止",stopped);}
  finally{if(timeout!=null)timeout.cancel(false);synchronized(lock){if(lookup==connection)lookup=null;}closeSocket(connection);}
 }
}
