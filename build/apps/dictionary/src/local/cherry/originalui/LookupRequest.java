package local.cherry.originalui;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

final class LookupRequest {
 static final int MAX_BYTES=4096;
 static byte[] encode(String recognized)throws IOException{
  String text=recognized==null?"":recognized.replaceAll("[\\r\\n\\t]+"," ").trim();if(text.isEmpty())throw new IOException("未识别出文字，请重新扫描");
  if(text.codePointCount(0,text.length())>512)throw new IOException("本行文字过长，请分段扫描");
  for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)))throw new IOException("识别文字包含无效字符，请重新扫描");
  if(text.getBytes(StandardCharsets.UTF_8).length>2048)throw new IOException("本行文字过长，请分段扫描");
  StringBuilder quoted=new StringBuilder("\"");
  for(int i=0;i<text.length();i++){char c=text.charAt(i);switch(c){case '"':quoted.append("\\\"");break;case '\\':quoted.append("\\\\");break;case '\n':quoted.append("\\n");break;case '\r':quoted.append("\\r");break;case '\t':quoted.append("\\t");break;default:if(c<32){quoted.append("\\u00");quoted.append("0123456789abcdef".charAt(c>>4));quoted.append("0123456789abcdef".charAt(c&15));}else quoted.append(c);}}
  quoted.append('"');String body="{\"mainQuery\":"+quoted+",\"isOnlyQuery\":0,\"isOcrFinish\":1,\"scanType\":1,\"fromPage\":0}\n";
  try{ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(body));if(encoded.remaining()>MAX_BYTES)throw new IOException("本行文字过长，请分段扫描");byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);return bytes;}
  catch(CharacterCodingException malformed){throw new IOException("识别文字编码错误，请重新扫描",malformed);}
 }
 static String readLine(InputStream input)throws IOException{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream(128);
  for(;;){int c=input.read();if(c<0)throw new EOFException("原版词典连接已关闭");if(c=='\n')break;if(bytes.size()>=MAX_BYTES-1)throw new IOException("原版词典回复过长");bytes.write(c);}
  try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
  catch(CharacterCodingException invalid){throw new IOException("原版词典回复格式错误",invalid);}
 }
}
