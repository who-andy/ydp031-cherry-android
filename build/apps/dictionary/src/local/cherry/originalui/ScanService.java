package local.cherry.originalui;

import android.app.*;
import android.content.*;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import java.io.*;

/** Keeps the verified OCR worker ready; the passive tip channel never owns the camera. */
public final class ScanService extends Service implements ScanBridge.Listener {
 private static final String CHANNEL="pen-scan";
 private volatile boolean stopping;
 private volatile LocalSocket tip;
 private ScanBridge scanner;
 private Thread reader;
 private final Handler main=new Handler(Looper.getMainLooper());
 private long lastLaunch=-2000,lastError=-5000;
 static void ensure(Context context){if(!DeviceMode.gaming())context.startForegroundService(new Intent(context,ScanService.class));}
 @Override public void onCreate(){
  super.onCreate();
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  NotificationChannel channel=new NotificationChannel(CHANNEL,"笔尖扫描",NotificationManager.IMPORTANCE_MIN);
  channel.setDescription("按下笔尖即可打开词典扫描");manager.createNotificationChannel(channel);
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,OriginalUiActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(1,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("笔尖扫描已就绪").setContentText("按下笔尖自动打开词典").setContentIntent(open).setOngoing(true).build());
  if(DeviceMode.gaming()){stopSelf();return;}
  scanner=new ScanBridge(this);scanner.start();
  reader=new Thread(this::watchTip,"original-tip-watch");reader.start();
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){if(DeviceMode.gaming()){stopSelf();return START_NOT_STICKY;}if(scanner!=null)scanner.retryCaptureNow();return START_STICKY;}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){
  stopping=true;ScanBridge.closeSocket(tip);if(scanner!=null)scanner.close();if(reader!=null)reader.interrupt();main.removeCallbacksAndMessages(null);super.onDestroy();
 }
 @Override public void error(ScanBridge source,String message){main.post(()->{
  if(stopping||source!=scanner||!OriginalUiActivity.visible)return;
  long now=SystemClock.elapsedRealtime();if(now-lastError>=5000){lastError=now;Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 });}
 @Override public void scanStarted(ScanBridge source){if(source==scanner)showDictionary();}
 private void showDictionary(){main.post(()->{
  if(stopping||OriginalUiActivity.visible)return;
  long now=SystemClock.elapsedRealtime();if(now-lastLaunch<700)return;lastLaunch=now;
  try{startActivity(new Intent(this,OriginalUiActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
  catch(RuntimeException error){Log.e("CherryScan","Cannot show dictionary",error);}
 });}
 private void watchTip(){
  while(!stopping){LocalSocket connection=new LocalSocket();
   try{
    tip=connection;if(stopping)return;
    connection.connect(new LocalSocketAddress("cherry.tip",LocalSocketAddress.Namespace.ABSTRACT));
    DataInputStream input=new DataInputStream(connection.getInputStream());byte[] raw=new byte[48];boolean down=false;
    while(!stopping){input.readFully(raw);ScanFrames.Header header=ScanFrames.parse(raw);if(header.kind!=2)throw new IOException("invalid tip state");
     boolean pressed=(header.flags&3)!=0;
     if(pressed&&!down){showDictionary();ScanBridge current=scanner;if(current!=null)current.retryCaptureNow();}
     down=pressed;
    }
   }catch(IOException|RuntimeException unavailable){/* Broker restart: reconnect without claiming capture ownership. */}
   finally{ScanBridge.closeSocket(connection);if(tip==connection)tip=null;}
   if(!stopping)try{Thread.sleep(1000);}catch(InterruptedException done){return;}
  }
 }
}
