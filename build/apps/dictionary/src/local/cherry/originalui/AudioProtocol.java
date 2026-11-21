package local.cherry.originalui;
import java.io.*;import java.nio.charset.StandardCharsets;
final class AudioProtocol {
 static final int MAX_CLIP=1048576;
 static final class Command {
  final String kind,uuid;final int id,size;byte[] data;
  Command(String type,String token,long request,long bytes,String mime)throws IOException{
   if(!"play".equals(type)&&!"stop".equals(type)&&!"tts".equals(type)||request<1||request>2147483647L||token==null||!token.matches("[A-Za-z0-9_-]{1,128}"))throw new IOException("audio header");
   for(int i=0;i<token.length();i++)if(Character.isISOControl(token.charAt(i)))throw new IOException("audio UUID");
   if(bytes<0||bytes>MAX_CLIP||("stop".equals(type)&&bytes!=0)||("play".equals(type)&&(bytes==0||!"audio/mpeg".equals(mime))))throw new IOException("audio length or format");
   if("tts".equals(type)&&(bytes<1||bytes>8192||!"application/json".equals(mime)))throw new IOException("TTS payload");
   kind=type;uuid=token;id=(int)request;size=(int)bytes;
  }
  boolean matches(Command other){return other!=null&&id==other.id&&uuid.equals(other.uuid);}
 }
}
