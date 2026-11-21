package local.cherry.originalui;

import java.util.ArrayDeque;

/** Three reusable buffers: reader, latest queued frame, and renderer. */
final class LatestFrames {
 static final class Frame {final byte[] data;final int sequence;final Object source;Frame(byte[] d,int s,Object origin){data=d;sequence=s;source=origin;}}
 private final ArrayDeque<byte[]> free=new ArrayDeque<>();private Frame latest;private boolean closed;
 LatestFrames(){for(int i=0;i<3;i++)free.add(new byte[FrameProtocol.BYTES]);}
 synchronized byte[] borrow()throws InterruptedException{while(!closed&&free.isEmpty())wait();return closed?null:free.removeFirst();}
 synchronized void publish(byte[] data,int sequence,Object source){if(closed){return;}if(latest!=null)free.addLast(latest.data);latest=new Frame(data,sequence,source);notifyAll();}
 synchronized Frame take()throws InterruptedException{while(!closed&&latest==null)wait();if(closed)return null;Frame result=latest;latest=null;return result;}
 synchronized void release(byte[] data){if(!closed){free.addLast(data);notifyAll();}}
 synchronized void close(){closed=true;latest=null;free.clear();notifyAll();}
}
