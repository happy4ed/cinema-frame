package com.happy.frame.diagnostics;
import android.content.*; import android.os.*; import android.widget.*; import android.view.Gravity; import android.util.Log; import java.text.SimpleDateFormat; import java.util.*;
public class EventReceiver extends BroadcastReceiver {
 static Toast toast;
 static String name(int c){switch(c){case 289:return "위";case 290:return "아래";case 291:return "왼쪽";case 292:return "오른쪽";case 293:return "앞쪽";case 294:return "뒤쪽";case 295:return "별 버튼";case 24:return "음량 +";case 25:return "음량 -";default:return "알 수 없는 입력 "+c;}}
 static String action(int c){switch(c){case 289:return "뒤로 가기";case 290:case 295:return "Immich 복귀";case 291:return "이전 사진 방향키";case 292:return "다음 사진 방향키";case 24:return "음량 높이기";case 25:return "음량 낮추기";default:return "할당 없음";}}
 static void show(Context c,String s){if(toast!=null)toast.cancel();toast=Toast.makeText(c,s,Toast.LENGTH_LONG);toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,200);toast.show();}
 static void record(Context c,String s){SharedPreferences p=c.getSharedPreferences("diag",0);String old=p.getString("log","");String line=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" "+s;String all=line+"\n"+old;if(all.length()>12000)all=all.substring(0,12000);p.edit().putString("log",all).putLong("last",System.currentTimeMillis()).apply();Log.i("FrameGestureDiag",s);}
 public void onReceive(Context c,Intent i){final Context app=c.getApplicationContext();String stage=i.getStringExtra("stage");final int code=i.getIntExtra("code",-1); final long now=System.currentTimeMillis();SharedPreferences p=app.getSharedPreferences("diag",0);
 if("mapped".equals(stage)){p.edit().putLong("mapped_"+code,now).apply();String kind=i.getStringExtra("kind"); String label="double".equals(kind)?"별 두 번 누름 → Cinema Frame 열기":"long".equals(kind)?"별 길게 누름 → YouTube 열기":code==295?"별 한 번 누름 → Immich 복귀":"매핑 인식: "+name(code)+" → "+action(code)+" 요청"; String s=label;record(app,s);show(app,s);}
 else if("raw".equals(stage)){String s="센서 입력: "+name(code)+" ["+code+"]";record(app,s);if(p.getLong("mapped_"+code,0)<now-1000)show(app,s);}
 else {show(app,"제스처 진단 표시 준비 완료");record(app,"진단 토스트 시험");}
 }
}
