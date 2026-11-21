package local.cherry.originalui;
import java.io.*;
final class DeviceMode {
 static boolean gaming(){
  try(BufferedReader in=new BufferedReader(new FileReader("/payload/cherry-android/device-modes/current"))){return "game".equals(in.readLine());}
  catch(IOException absent){return false;}
 }
}
