package local.cherry.originalui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/** Existing broker geometry and bounded original OCR queue, without another UI. */
final class ScanFrames {
 static final int SIZE=307200;
 static final class Header {
  int kind,bytes,width,height,stride,sequence,flags,session;long timestamp;
 }
 static Header parse(byte[] raw)throws IOException{
  if(raw.length!=48)throw new IOException("扫描数据头错误");ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
  int magic=b.getInt(),version=b.getInt();Header h=new Header();h.kind=b.getInt();h.bytes=b.getInt();h.width=b.getInt();h.height=b.getInt();h.stride=b.getInt();h.sequence=b.getInt();h.flags=b.getInt();h.session=b.getInt();h.timestamp=b.getLong();
  if(magic!=0x314d4143||version!=1||h.kind<1||h.kind>3||h.bytes<0||h.bytes>2097152)throw new IOException("扫描数据格式错误");
  if(h.kind==3&&h.bytes>1024||h.kind==2&&h.bytes!=0)throw new IOException("扫描消息长度错误");
  if(h.kind==1&&(h.width!=640||h.height!=480||h.stride<640||h.stride>2048||h.bytes!=(long)h.stride*h.height))throw new IOException("扫描图像尺寸错误");
  return h;
 }
 static final class Frame {final byte[] pixels;final int sequence;Frame(byte[] p,int s){pixels=p;sequence=s;}}
 static final class Session {
  static final int BACKLOG_LIMIT=60;
  final int id;final ScanSequence.Line line;final Object owner;
  private final ArrayDeque<Frame> pending=new ArrayDeque<>();private final ArrayDeque<byte[]> free=new ArrayDeque<>();
  volatile boolean cancelled;private boolean ended,retaining,processing;private int allocated;
  int lastSequence,received,submitted,dropped;
  Session(int session){this(session,null,null,false);}
  Session(int session,ScanSequence.Line token,Object connection,boolean buffered){id=session;line=token;owner=connection;retaining=buffered;}
  synchronized void activate(){processing=true;if(pending.size()<=2)retaining=false;}
  synchronized boolean offer(byte[] data,int stride,int sequence){
   if(ended||cancelled)return false;
   byte[] buffer;
   if(!retaining&&pending.size()>=2){buffer=pending.removeFirst().pixels;dropped++;}
   else {
    buffer=free.pollFirst();
    if(buffer==null){
     int limit=retaining?BACKLOG_LIMIT:3;
     if(allocated>=limit){if(retaining)cancel();else dropped++;return false;}
     buffer=new byte[SIZE];allocated++;
    }
   }
   if(stride==640)System.arraycopy(data,0,buffer,0,SIZE);else for(int y=0;y<480;y++)System.arraycopy(data,y*stride,buffer,y*640,640);
   pending.addLast(new Frame(buffer,sequence));notifyAll();return true;
  }
  synchronized Frame take()throws InterruptedException{
   while(pending.isEmpty()&&!ended&&!cancelled)wait();
   if(cancelled)return null;Frame frame=pending.pollFirst();
   if(processing&&pending.size()<=2)retaining=false;return frame;
  }
  synchronized void release(Frame frame){if(cancelled||!retaining&&free.size()>=3){allocated--;return;}free.addLast(frame.pixels);}
  synchronized void end(){ended=true;notifyAll();}
  synchronized void cancel(){cancelled=true;ended=true;pending.clear();free.clear();notifyAll();}
 }
}
