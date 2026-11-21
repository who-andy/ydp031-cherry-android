package local.cherry.originalui;
import java.io.*;
import java.util.Arrays;
/** One explicitly armed local diagnostic, bounded to eight frames plus one stitch. */
final class ScanDiagnostic {
 private static final File CACHE=new File("/data/user/0/local.cherry.originalui/cache");
 private final boolean enabled;private final byte[][] first=new byte[4][],last=new byte[4][];private int count;
 ScanDiagnostic(){File marker=new File(CACHE,"scan-diagnostic.once");enabled=marker.exists()&&marker.delete();}
 void frame(byte[] raw){if(!enabled)return;if(count<4)first[count]=Arrays.copyOf(raw,ScanFrames.SIZE);last[count%4]=Arrays.copyOf(raw,ScanFrames.SIZE);count++;}
 void finish(StockEngine.Result result){if(!enabled)return;try{
  File folder=new File(CACHE,"scan-diagnostic");if(!folder.isDirectory()&&!folder.mkdir())return;
  for(int i=0;i<4;i++){if(first[i]!=null)pgm(new File(folder,"first-"+i+".pgm"),first[i],640,480);if(last[i]!=null)pgm(new File(folder,"tail-slot-"+i+".pgm"),last[i],640,480);}
  if(result.gray!=null)pgm(new File(folder,"stitched.pgm"),result.gray,result.width,result.height);
  try(FileWriter writer=new FileWriter(new File(folder,"meta.txt"))){writer.write("frames="+count+"\nlastSlot="+((count-1)%4)+"\ncharacters="+result.text.length()+"\n");}
 }catch(IOException failure){System.err.println("CHERRY_SCAN_DIAGNOSTIC write_failed");}}
 private static void pgm(File path,byte[] data,int width,int height)throws IOException{try(OutputStream output=new BufferedOutputStream(new FileOutputStream(path))){output.write(("P5\n"+width+" "+height+"\n255\n").getBytes("US-ASCII"));output.write(data);}}
}
