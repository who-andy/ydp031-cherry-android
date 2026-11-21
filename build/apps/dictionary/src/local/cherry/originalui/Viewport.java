package local.cherry.originalui;

/** Aspect-preserving letterbox geometry; touch and drawing use one transform. */
final class Viewport {
 final float left,top,right,bottom,scale;
 Viewport(int width,int height){
  if(width<=0||height<=0)throw new IllegalArgumentException("viewport size");
  scale=Math.min(width/(float)FrameProtocol.WIDTH,height/(float)FrameProtocol.HEIGHT);
  float w=FrameProtocol.WIDTH*scale,h=FrameProtocol.HEIGHT*scale;left=(width-w)/2;top=(height-h)/2;right=left+w;bottom=top+h;
 }
 int[] point(float x,float y,boolean clamp){
  if(Float.isNaN(x)||Float.isNaN(y)||Float.isInfinite(x)||Float.isInfinite(y))return null;
  if(!clamp&&(x<left||x>=right||y<top||y>=bottom))return null;
  return new int[]{Math.max(0,Math.min(FrameProtocol.WIDTH-1,(int)Math.floor((x-left)/scale))),Math.max(0,Math.min(FrameProtocol.HEIGHT-1,(int)Math.floor((y-top)/scale)))};
 }
}
