package local.cherry.bootswitch;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

public final class SwitchActivity extends Activity {
 private TextView state;private Button androidButton,stockButton;private boolean busy;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(12,24,12,20);scroll.addView(root);
  TextView title=new TextView(this);title.setText("系统切换");title.setTextSize(23);title.setGravity(Gravity.CENTER);root.addView(title);
  state=new TextView(this);state.setText("正在检查系统…");state.setTextSize(15);state.setPadding(0,24,0,24);state.setGravity(Gravity.CENTER);root.addView(state);
  androidButton=new Button(this);androidButton.setText("重启 Android");androidButton.setAllCaps(false);root.addView(androidButton,new LinearLayout.LayoutParams(-1,-2));
  stockButton=new Button(this);stockButton.setText("进入原厂系统");stockButton.setAllCaps(false);root.addView(stockButton,new LinearLayout.LayoutParams(-1,-2));
  TextView note=new TextView(this);note.setText("切换会重启设备。\n请先保存正在进行的操作。");note.setTextSize(13);note.setGravity(Gravity.CENTER);note.setPadding(0,20,0,0);root.addView(note);
  androidButton.setOnClickListener(v->confirm("android","重启 Android？"));stockButton.setOnClickListener(v->confirm("stock","进入原厂系统？"));
  setContentView(scroll);request("status");
 }
 private void confirm(final String target,String title){
  if(busy)return;
  new AlertDialog.Builder(this).setTitle(title).setMessage("确认后将立即重启。").setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->request(target)).show();
 }
 private void buttons(boolean enabled){androidButton.setEnabled(enabled);stockButton.setEnabled(enabled);}
 private void request(final String action){
  if(busy)return;busy=true;buttons(false);
  state.setText(action.equals("status")?"正在检查系统…":"正在保存启动选择…");
  new Thread(()->{
   boolean committed=false;String error="";String status="";
   try(LocalSocket socket=new LocalSocket()){
    socket.connect(new LocalSocketAddress("cherry.bootctl",LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(30000);
    socket.getOutputStream().write((action+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();
    BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String line;
    while((line=input.readLine())!=null){
     if(line.startsWith("ERROR")||line.startsWith("BOOTCTL_ERROR"))error=line;
     if(line.startsWith("{\"result\"")){
      JSONObject object=new JSONObject(line);String result=object.getString("result");
      if(result.equals("STATUS")){int a=object.getJSONObject("android").getInt("priority"),b=object.getJSONObject("stock").getInt("priority");status="当前：Android\n下次启动："+(a>b?"Android":b>a?"原厂系统":"尚未确定");}
     }
     if(line.startsWith("SYSTEM_SWITCH_COMMITTED ")){committed=true;runOnUiThread(()->state.setText("启动选择已保存\n正在重启…"));}
    }
   }catch(Exception e){error="接口连接失败："+e.getClass().getSimpleName();}
   final boolean changed=committed;final String failure=error,summary=status;
   runOnUiThread(()->{
    if(isFinishing())return;busy=false;
    if(!failure.isEmpty()){state.setText((changed?"启动选择已保存，但重启未确认。\n":"操作未完成。\n")+failure);buttons(true);}
    else if(action.equals("status")&&!summary.isEmpty()){state.setText(summary);buttons(true);}
    else if(changed){state.setText("启动选择已保存\n正在重启…");buttons(false);}
    else {state.setText("系统切换服务尚未就绪。\n请稍后重新打开。");buttons(false);}
   });
  },"system-switch").start();
 }
}
