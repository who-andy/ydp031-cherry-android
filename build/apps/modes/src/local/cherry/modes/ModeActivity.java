package local.cherry.modes;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import org.json.JSONObject;

/** Two bounded device operations; no root grant, polling service, or arbitrary commands. */
public final class ModeActivity extends Activity {
 private TextView status;private Button study,game;private boolean busy;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(14,4,14,4);root.setBackgroundColor(0xff101722);scroll.addView(root);
  TextView title=new TextView(this);title.setText("设备模式");title.setTextColor(Color.WHITE);title.setTextSize(28);root.addView(title);
  LinearLayout choices=new LinearLayout(this);choices.setOrientation(0);root.addView(choices);
  study=button("学习模式\n随时按笔尖扫描",choices);game=button("游戏模式\n暂停扫描 · 释放内存",choices);
  status=new TextView(this);status.setTextSize(19);status.setTextColor(0xffc5d5e5);status.setPadding(4,2,4,2);status.setText("正在检查…");root.addView(status);
  TextView note=new TextView(this);note.setText("切回学习会退出地铁跑酷；切换无需重启。相机、网络和声音照常使用。");note.setTextSize(15);note.setTextColor(0xff9aafc5);root.addView(note);
  study.setOnClickListener(v->request("study"));game.setOnClickListener(v->request("game"));setContentView(scroll);
 }
 private Button button(String text,LinearLayout parent){Button b=new Button(this);b.setText(text);b.setTextSize(25);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinimumHeight(0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,5,0);parent.addView(b,p);return b;}
 @Override protected void onResume(){super.onResume();if(!busy)request("status");}
 private void enable(boolean value){study.setEnabled(value);game.setEnabled(value);}
 private void request(String action){
  if(busy)return;busy=true;enable(false);status.setText(action.equals("study")?"正在恢复词典和扫描…":action.equals("game")?"正在退出词典后台…":"正在检查…");
  new Thread(()->{
   JSONObject result=null;String failure=null;
   try(LocalSocket socket=new LocalSocket()){
    socket.connect(new LocalSocketAddress("cherry.modes",LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(40000);
    socket.getOutputStream().write((action+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();
    BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String line;int total=0;
    while((line=in.readLine())!=null){total+=line.length();if(total>16384)throw new IOException("response limit");if(line.startsWith("ERROR"))failure=line;if(line.startsWith("{\"mode\""))result=new JSONObject(line);}
    if(result==null||failure!=null)throw new IOException("operation incomplete");
   }catch(Exception e){failure="切换未完成，请稍后重试。";}
   final JSONObject data=result;final String error=failure;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;busy=false;enable(true);
    if(error!=null){status.setText(error);return;}
    boolean gaming="game".equals(data.optString("mode"));boolean backend=data.optBoolean("backend"),scanner=data.optBoolean("scanner");
    boolean ready=gaming?!backend&&!scanner:backend&&scanner;
    study.setBackgroundTintList(ColorStateList.valueOf(gaming?0xff263347:0xff167568));game.setBackgroundTintList(ColorStateList.valueOf(gaming?0xff325cba:0xff263347));
    status.setText((gaming?"游戏模式":"学习模式")+(ready?"已就绪":"尚未就绪，请再次点击模式恢复")+"  ·  可用内存 "+data.optLong("availableKb")/1024+" MB");
   });
  },"mode-request").start();
 }
}
