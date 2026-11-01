package com.happy.frame.diagnostics;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;

/** Local app switching only; does not stop processes or inspect app contents. */
public class TaskSwitcherActivity extends Activity {
 static void remember(Context c,String pkg) {
  if(pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui") || pkg.equals("com.walnut.bluetooth"))return;
  if(c.getPackageManager().getLaunchIntentForPackage(pkg)==null)return;
  try {
   JSONArray old=new JSONArray(c.getSharedPreferences("diag",0).getString("recentApps","[]"));
   if(old.length()>0 && pkg.equals(old.getString(0)))return;
   JSONArray next=new JSONArray();next.put(pkg);
   for(int i=0;i<old.length() && next.length()<10;i++)if(!pkg.equals(old.getString(i)))next.put(old.getString(i));
   c.getSharedPreferences("diag",0).edit().putString("recentApps",next.toString()).apply();
  }catch(Exception ignored){}
 }
 private LinearLayout list;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(50,65,50,35);
  TextView title=new TextView(this);title.setText("앱 전환");title.setTextSize(28);root.addView(title);
  ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  Button close=new Button(this);close.setText("닫기");close.setOnClickListener(v->finish());root.addView(close);
  setContentView(root);
  label("최근 사용한 앱");
  LinkedHashSet<String> recent=new LinkedHashSet<>();
  try{JSONArray a=new JSONArray(getSharedPreferences("diag",0).getString("recentApps","[]"));for(int i=0;i<a.length();i++)recent.add(a.getString(i));}catch(Exception ignored){}
  Collections.addAll(recent,"de.ozerov.fully","io.github.seky443.librething","org.smarttube.stable");
  for(String pkg:recent)app(pkg);
  label("설치된 앱");
  List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
  Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
  HashSet<String> seen=new HashSet<>(recent);
  for(ResolveInfo r:apps){String pkg=r.activityInfo.packageName;if(seen.add(pkg) && !pkg.equals("com.walnut.bluetooth"))app(pkg);}
 }
 private void label(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(18);t.setPadding(0,25,0,10);list.addView(t);}
 private void app(String pkg){
  Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null)return;
  try{
   Button b=new Button(this);b.setAllCaps(false);b.setText(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)));b.setTextSize(20);
   b.setOnClickListener(v->{try{remember(this,pkg);startActivity(launch);EventReceiver.show(this,"앱 전환 → "+b.getText());finish();}catch(Exception e){EventReceiver.show(this,"앱을 열 수 없습니다");}});
   list.addView(b,new LinearLayout.LayoutParams(-1,100));
  }catch(Exception ignored){}
 }
}
