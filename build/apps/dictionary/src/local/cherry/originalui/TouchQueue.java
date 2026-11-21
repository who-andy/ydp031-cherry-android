package local.cherry.originalui;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Coalesce movement; never silently drop a lift or a press. */
final class TouchQueue {
 static final int LIMIT=64;
 static final class Event {final int action,pointer,x,y;Event(int a,int p,int px,int py){action=a;pointer=p;x=px;y=py;}}
 private final ArrayDeque<Event> queue=new ArrayDeque<>();private boolean closed;
 synchronized boolean offer(Event event){
  if(closed)return false;
  if(event.action==FrameProtocol.CANCEL){queue.clear();queue.add(event);notifyAll();return true;}
  Event last=queue.peekLast();if(event.action==FrameProtocol.MOVE&&last!=null&&last.action==FrameProtocol.MOVE&&last.pointer==event.pointer){queue.removeLast();queue.add(event);notifyAll();return true;}
  if(queue.size()>=LIMIT){Iterator<Event> it=queue.iterator();while(it.hasNext()){if(it.next().action==FrameProtocol.MOVE){it.remove();break;}}}
  if(queue.size()>=LIMIT){queue.clear();queue.add(new Event(FrameProtocol.CANCEL,0,0,0));notifyAll();return false;}
  queue.add(event);notifyAll();return true;
 }
 synchronized Event take()throws InterruptedException{while(!closed&&queue.isEmpty())wait();return closed?null:queue.removeFirst();}
 synchronized void close(){closed=true;queue.clear();notifyAll();}
}
