package local.cherry.originalui;

import java.io.IOException;

/** Groups by physical monotonic press/release times, independent of OCR latency. */
final class ScanSequence {
 static final long WINDOW_NS=2000000000L;
 static final class EmptyResultException extends IOException {EmptyResultException(){super("本行未识别出文字，请重新扫描本行");}}
 static final class Line {final long group,pressed;long released=-1;Line(long g,long t){group=g;pressed=t;}}
 private long group,lastRelease=-1,resultGroup=-1;private String accumulated="";
 synchronized Line begin(long timestamp){
  if(timestamp<0)throw new IllegalArgumentException("negative scan timestamp");
  if(lastRelease<0||timestamp<lastRelease||timestamp-lastRelease>WINDOW_NS)group++;
  lastRelease=-1;return new Line(group,timestamp);
 }
 synchronized void end(Line line,long timestamp){
  if(line.group!=group||timestamp<line.pressed){reset();return;}
  line.released=timestamp;lastRelease=timestamp;
 }
 synchronized void reset(){group++;lastRelease=-1;resultGroup=-1;accumulated="";}
 synchronized String append(Line line,String recognized)throws IOException{
  String text=recognized==null?"":recognized.replaceAll("[\\r\\n\\t]+"," ").trim();
  // A blank/bouncing contact must be reported without discarding valid earlier lines.
  if(text.isEmpty())throw new EmptyResultException();
  try{
   LookupRequest.encode(text);
   String joined=resultGroup==line.group?join(accumulated,text):text;
   LookupRequest.encode(joined);accumulated=joined;resultGroup=line.group;return joined;
  }catch(IOException invalid){reset();throw invalid;}
 }
 private static boolean cjk(int cp){Character.UnicodeBlock b=Character.UnicodeBlock.of(cp);return b==Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS||b==Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A||b==Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS||b==Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION||b==Character.UnicodeBlock.HIRAGANA||b==Character.UnicodeBlock.KATAKANA;}
 static String join(String left,String right){
  if(left.isEmpty())return right;if(right.isEmpty())return left;
  int a=left.codePointBefore(left.length()),b=right.codePointAt(0);
  boolean noSpace=(cjk(a)||"，。；：！？、）】》」』".indexOf(a)>=0)&&cjk(b)||",.;:!?，。；：！？、)]}）】》」』".indexOf(b)>=0||"([{（【《「『".indexOf(a)>=0;
  return left+(noSpace?"":" ")+right;
 }
}
