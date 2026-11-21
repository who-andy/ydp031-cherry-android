package local.cherry.originalui;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;

/** Private process pipes to original glibc engine; see STOCK-IPC.md. */
final class StockEngine implements Closeable {
 static final int MAGIC=0x31575343,MAX_IMAGE=8*1024*1024;
 private final Process process;private final DataInputStream input;private final OutputStream output;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"stock-deadline");t.setDaemon(true);return t;});
 private volatile boolean closed;
 static final class Result {String text;byte[] gray;int width,height,received,submitted;}
 private static final class Packet {int kind,session,sequence;byte[] payload;}
 StockEngine()throws IOException{this(new ProcessBuilder("/payload/cherry-android/stock-engine/run-worker.sh").start());}
 StockEngine(Process child)throws IOException{
  process=child;
  input=new DataInputStream(new BufferedInputStream(process.getInputStream(),32768));output=new BufferedOutputStream(process.getOutputStream(),32768);
  Thread stderr=new Thread(()->{try(InputStream in=process.getErrorStream()){byte[] discard=new byte[4096];while(in.read(discard)>=0){/* vendor logs may contain text; never logcat it */}}catch(IOException ignored){}},"stock-stderr");stderr.setDaemon(true);stderr.start();
 }
 void awaitReady()throws IOException{
  ScheduledFuture<?> alarm=deadline(30000);
  try{Packet ready=read();if(ready.kind!=100||ready.session!=0||ready.sequence!=0||ready.payload.length!=0)throw new IOException("识别器启动协议错误");}
  catch(IOException e){close();throw e;}finally{alarm.cancel(false);}
 }
 private ScheduledFuture<?> deadline(long ms){return timer.schedule(this::close,ms,TimeUnit.MILLISECONDS);}
 private Packet read()throws IOException{
  int magic=Integer.reverseBytes(input.readInt()),version=Integer.reverseBytes(input.readInt());Packet p=new Packet();p.kind=Integer.reverseBytes(input.readInt());int n=Integer.reverseBytes(input.readInt());p.session=Integer.reverseBytes(input.readInt());p.sequence=Integer.reverseBytes(input.readInt());int w=Integer.reverseBytes(input.readInt()),h=Integer.reverseBytes(input.readInt());
  if(magic!=MAGIC||version!=1||w!=0||h!=0||n<0||n>MAX_IMAGE+65536+16)throw new IOException("识别器返回格式错误");
  if(p.kind<100||p.kind>104||(p.kind==104&&n>1024)||(p.kind==102&&n!=8)||((p.kind==100||p.kind==101)&&n!=0)||(p.kind==103&&n<16))throw new IOException("识别器消息长度错误");
  p.payload=new byte[n];input.readFully(p.payload);return p;
 }
 private Packet request(int kind,int sid,int seq,byte[] pixels,int expect,int timeout)throws IOException{
  if(closed)throw new IOException("识别进程已停止");ScheduledFuture<?> alarm=deadline(timeout);
  try{
   int bytes=pixels==null?0:307200;ByteBuffer h=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);h.putInt(MAGIC).putInt(1).putInt(kind).putInt(bytes).putInt(sid).putInt(seq).putInt(bytes==0?0:640).putInt(bytes==0?0:480);output.write(h.array());if(pixels!=null)output.write(pixels,0,bytes);output.flush();
   Packet p=read();if(p.session!=sid||p.sequence!=seq)throw new IOException("识别器会话不匹配");if(p.kind==104)throw new IOException(new String(p.payload,"UTF-8"));if(p.kind!=expect)throw new IOException("识别器响应顺序错误");return p;
  }catch(IOException e){close();throw e;}finally{alarm.cancel(false);}
 }
 void begin(int sid)throws IOException{request(1,sid,0,null,101,5000);}
 int[] frame(int sid,int seq,byte[] raw)throws IOException{if(raw.length<307200)throw new IOException("帧长度不足");Packet p=request(2,sid,seq,raw,102,5000);ByteBuffer b=ByteBuffer.wrap(p.payload).order(ByteOrder.LITTLE_ENDIAN);return new int[]{b.getInt(),b.getInt()};}
 Result finish(int sid,int seq)throws IOException{
  Packet p=request(3,sid,seq,null,103,30000);ByteBuffer b=ByteBuffer.wrap(p.payload).order(ByteOrder.LITTLE_ENDIAN);Result r=new Result();r.width=b.getInt();r.height=b.getInt();int textBytes=b.getInt(),imageBytes=b.getInt();
  if(r.width<0||r.height<0||r.width>8192||r.height>2048||textBytes<0||textBytes>65536||imageBytes<0||imageBytes>MAX_IMAGE||imageBytes!=(long)r.width*r.height||p.payload.length!=16L+textBytes+imageBytes||(imageBytes==0&&(r.width!=0||r.height!=0)))throw new IOException("识别结果尺寸错误");
  r.text=new String(p.payload,16,textBytes,"UTF-8");if(imageBytes!=0){r.gray=new byte[imageBytes];System.arraycopy(p.payload,16+textBytes,r.gray,0,imageBytes);}return r;
 }
 @Override public void close(){if(closed)return;closed=true;process.destroy();try{process.getOutputStream().close();}catch(IOException ignored){}try{process.getInputStream().close();}catch(IOException ignored){}try{process.getErrorStream().close();}catch(IOException ignored){}timer.shutdownNow();}
}
