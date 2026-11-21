package local.cherry.originalui;
import android.content.*;
/** Recover the foreground scan service after boot or an in-place APK update. */
public final class ScanBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))ScanService.ensure(context);
 }
}
