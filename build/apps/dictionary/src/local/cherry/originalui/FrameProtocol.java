package local.cherry.originalui;

import java.io.*;
import java.nio.*;

/** Frozen CUIF/CUIT framing shared with the real Wayland SHM receiver. */
final class FrameProtocol {
 static final int WIDTH=800,HEIGHT=254,STRIDE=WIDTH*4,BYTES=STRIDE*HEIGHT;
 static final int FRAME_MAGIC=0x46495543,TOUCH_MAGIC=0x54495543;
 static final int CANCEL=0,DOWN=1,MOVE=2,UP=3;
 static final class Header {final int sequence;Header(int s){sequence=s;}}
 static Header parse(byte[] raw)throws IOException{
  if(raw.length!=32)throw new IOException("原版界面数据头不完整");ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
  if(b.getInt()!=FRAME_MAGIC||b.getInt()!=1||b.getInt()!=1)throw new IOException("原版界面连接版本不匹配");
  int bytes=b.getInt(),w=b.getInt(),h=b.getInt(),stride=b.getInt(),sequence=b.getInt();
  if(w!=WIDTH||h!=HEIGHT||stride!=STRIDE||bytes!=BYTES)throw new IOException("原版界面尺寸不匹配，等待重新连接");return new Header(sequence);
 }
 static boolean newer(int sequence,int previous){return sequence-previous>0;}
 static void pixels(byte[] bgra,int[] argb){
  if(bgra.length!=BYTES||argb.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("frame buffer size");
  ByteBuffer.wrap(bgra).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(argb);
  for(int i=0;i<argb.length;i++)argb[i]|=0xff000000;
 }
 static byte[] touch(int action,int pointer,int x,int y){
  if(action<CANCEL||action>UP||pointer<0||pointer>31||x<0||x>=WIDTH||y<0||y>=HEIGHT)throw new IllegalArgumentException("touch bounds");
  return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(TOUCH_MAGIC).putInt(1).putInt(action).putInt(pointer).putInt(x).putInt(y).array();
 }
}
