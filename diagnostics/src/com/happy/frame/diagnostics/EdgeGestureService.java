package com.happy.frame.diagnostics;

import android.accessibilityservice.*;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Reads cursor-window coordinates, never application text or screenshots. */
public class EdgeGestureService extends AccessibilityService {
 static volatile EdgeGestureService instance;
 static volatile String foregroundPackage="", foregroundActivity="";
 private android.widget.TextView pairingView;
 void showPairing(String code){
  hidePairing(); pairingView=new android.widget.TextView(this); String message="Home Assistant · 기기 제어 (8788)\n\n"+code+"\n\n5분 안에 HA에 입력하세요"; android.text.SpannableString styled=new android.text.SpannableString(message);int start=message.indexOf(code);styled.setSpan(new android.text.style.AbsoluteSizeSpan(56,true),start,start+6,0);styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),start,start+6,0);pairingView.setText(styled); pairingView.setTextSize(18);pairingView.setTextColor(0xFFFFFFFF);pairingView.setBackgroundColor(0xF0222630);pairingView.setPadding(40,40,40,40);pairingView.setGravity(Gravity.CENTER);
  windows.addView(pairingView,params(width-dp(64),dp(300),Gravity.CENTER,"Frame LAN pairing"));
 }
 void hidePairing(){if(pairingView!=null){try{windows.removeView(pairingView);}catch(Exception ignored){}pairingView=null;}}
 private final ArrayList<View> overlays=new ArrayList<>();
 private final Handler main=new Handler(Looper.getMainLooper());
 private WindowManager windows;
 private android.net.wifi.WifiManager.WifiLock wifiLock;
 private ScheduledExecutorService cursorReader,timeouts;
 private volatile boolean alive;
 private volatile String cursorWindow;
 private android.os.IBinder windowBinder;
 private int width,height,nativeSide=-1,kdeSide=-1,activeSide=-1;
 private long enteredAt,lastScrollAt;
 private boolean scrolling,reportedCursor,reportedCursorWindow,reportedDumpFailure,reportedDumpResponse;
 private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
 @Override protected void onServiceConnected(){
  alive=true; instance=this; startForegroundService(new android.content.Intent(this,LanControlService.class));
  AccessibilityServiceInfo info=getServiceInfo();
  if(info==null)info=new AccessibilityServiceInfo();
  info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.notificationTimeout=100;setServiceInfo(info);
  if(wifiLock==null){android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wifi!=null){wifiLock=wifi.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"FrameNavigation:RemoteInput");wifiLock.setReferenceCounted(false);wifiLock.acquire();}}
  windows=(WindowManager)getSystemService(WINDOW_SERVICE);
  android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;
  removeOverlays();
  addEdge(0,Gravity.LEFT|Gravity.TOP,dp(14),-1);addEdge(1,Gravity.RIGHT|Gravity.TOP,dp(14),-1);
  addEdge(2,Gravity.BOTTOM|Gravity.LEFT,-1,dp(18));addEdge(3,Gravity.TOP|Gravity.LEFT,-1,dp(14));
  addCorner(false);addCorner(true);
  if(cursorReader!=null)cursorReader.shutdownNow();if(timeouts!=null)timeouts.shutdownNow();
  timeouts=Executors.newSingleThreadScheduledExecutor();cursorReader=Executors.newSingleThreadScheduledExecutor();
  cursorReader.scheduleWithFixedDelay(()->readKdeCursor(),0,400,TimeUnit.MILLISECONDS);
  main.removeCallbacks(tick);main.post(tick);
  EventReceiver.record(this,"모서리 버튼 준비: 왼쪽 뒤로 · 오른쪽 앱 / 가장자리 머무르기 스크롤");
 }
 private WindowManager.LayoutParams params(int w,int h,int gravity,String title){
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=gravity;p.setTitle(title);return p;
 }
 private void navigate(boolean apps,String label){
  boolean ok=true;if(apps)startActivity(new android.content.Intent(this,TaskSwitcherActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));else ok=performGlobalAction(GLOBAL_ACTION_BACK);
  if(!ok)label+=" (시스템 요청 실패)";EventReceiver.record(this,label);EventReceiver.show(this,label);
 }
 private void addCorner(final boolean right){
  View corner=new View(this){
   final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean tracking;
   private boolean inside(float x,float y){float dx=right?getWidth()-x:x,dy=getHeight()-y;return dx*dx+dy*dy<=getWidth()*getWidth();}
   @Override protected void onDraw(Canvas c){paint.setStyle(Paint.Style.FILL);paint.setColor(isPressed()?0x88777777:0x55242424);c.drawCircle(right?getWidth():0,getHeight(),getWidth(),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x55FFFFFF);c.drawCircle(right?getWidth():0,getHeight(),getWidth()-dp(0.5f),paint);}
   @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:tracking=inside(e.getX(),e.getY());setPressed(tracking);invalidate();return tracking;
    case MotionEvent.ACTION_MOVE:if(tracking){setPressed(inside(e.getX(),e.getY()));invalidate();}return tracking;
    case MotionEvent.ACTION_UP:boolean click=tracking&&inside(e.getX(),e.getY());tracking=false;setPressed(false);invalidate();if(click)performClick();return true;
    case MotionEvent.ACTION_CANCEL:tracking=false;setPressed(false);invalidate();return true;
    default:return tracking;
   }}
  };
  corner.setContentDescription(right?"앱 전환":"뒤로 가기");corner.setOnClickListener(v->navigate(right,right?"오른쪽 아래 모서리 → 앱 전환":"왼쪽 아래 모서리 → 뒤로 가기"));
  windows.addView(corner,params(dp(32),dp(32),Gravity.BOTTOM|(right?Gravity.RIGHT:Gravity.LEFT),right?"Frame corner apps":"Frame corner back"));overlays.add(corner);
 }
 private void addEdge(final int side,int gravity,int w,int h){
  View edge=new View(this);edge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  edge.setOnHoverListener((v,e)->{nativeSide=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?-1:sideAt(e.getRawX(),e.getRawY());return true;});
  edge.setOnTouchListener(new View.OnTouchListener(){float x,y;long start;boolean tracking;
   public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();start=e.getEventTime();tracking=side<3;return tracking;
    case MotionEvent.ACTION_MOVE:return tracking;
    case MotionEvent.ACTION_CANCEL:tracking=false;return true;
    case MotionEvent.ACTION_UP:if(!tracking)return false;tracking=false;float dx=e.getRawX()-x,dy=e.getRawY()-y,inward=side==0?dx:side==1?-dx:-dy,cross=side==2?Math.abs(dx):Math.abs(dy);if(e.getEventTime()-start<=2000&&inward>=dp(64)&&inward>cross*1.3f)navigate(side==2,side==2?"아래 가장자리 ↑ → 앱 전환":"좌우 가장자리 → 뒤로 가기");return true;
    default:return tracking;
   }}
  });windows.addView(edge,params(w,h,gravity,"Frame edge "+side));overlays.add(edge);
 }
 private int sideAt(float x,float y){
  if(y>=height-dp(40)&&(x<=dp(40)||x>=width-dp(40)))return -1;
  if(x<=dp(14))return 0;if(x>=width-dp(14))return 1;if(y>=height-dp(18))return 2;if(y<=dp(14))return 3;return -1;
 }
 private final Runnable tick=new Runnable(){public void run(){
  if(!alive)return;int side=nativeSide>=0?nativeSide:kdeSide;long now=SystemClock.uptimeMillis();
  if(side!=activeSide){activeSide=side;enteredAt=now;lastScrollAt=0;}
  if(side>=0&&!scrolling&&now-enteredAt>=700&&now-lastScrollAt>=850){
   if(lastScrollAt==0){String label=side==0?"왼쪽으로 스크롤":side==1?"오른쪽으로 스크롤":side==2?"아래로 스크롤":"위로 스크롤";EventReceiver.record(EdgeGestureService.this,"가장자리 감지 → "+label);EventReceiver.show(EdgeGestureService.this,label);}
   lastScrollAt=now;scroll(side);
  }main.postDelayed(this,100);
 }};
 private void scroll(int side){
  float cx=width*0.5f,cy=height*0.5f,dx=width*0.18f,dy=height*0.18f;Path path=new Path();
  if(side==0){path.moveTo(cx-dx,cy);path.lineTo(cx+dx,cy);}else if(side==1){path.moveTo(cx+dx,cy);path.lineTo(cx-dx,cy);}else if(side==2){path.moveTo(cx,cy+dy);path.lineTo(cx,cy-dy);}else{path.moveTo(cx,cy-dy);path.lineTo(cx,cy+dy);}
  scrolling=true;
  boolean submitted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,250)).build(),new GestureResultCallback(){@Override public void onCompleted(GestureDescription d){scrolling=false;}@Override public void onCancelled(GestureDescription d){scrolling=false;}},main);
  if(!submitted){scrolling=false;EventReceiver.record(this,"스크롤 요청 거부: 접근성 서비스 상태 확인");}
 }
 private void readKdeCursor(){
  if(!alive||checkSelfPermission("android.permission.DUMP")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  android.os.ParcelFileDescriptor[] pipe=null;
  try{
   if(windowBinder==null){Object manager=Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);windowBinder=((android.os.IInterface)manager).asBinder();}
   pipe=android.os.ParcelFileDescriptor.createPipe();final android.os.ParcelFileDescriptor input=pipe[0];
   ScheduledFuture<?> timeout=timeouts.schedule(()->{try{input.close();}catch(IOException ignored){}},900,TimeUnit.MILLISECONDS);
   StringBuilder text=new StringBuilder();
   try{
    windowBinder.dumpAsync(pipe[1].getFileDescriptor(),new String[]{cursorWindow==null?"windows":cursorWindow});pipe[1].close();
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(new android.os.ParcelFileDescriptor.AutoCloseInputStream(input)))){String line;while((line=reader.readLine())!=null&&text.length()<180000)text.append(line).append('\n');}
   }finally{timeout.cancel(false);}
   float px=-1,py=-1;boolean found=false;
   for(String block:text.toString().split("(?m)^  Window #")){
    Matcher identity=Pattern.compile("Window\\{([0-9a-f]+) u\\d+ org\\.kde\\.kdeconnect_tp\\}").matcher(block);
    if(!identity.find()||!block.contains("ty=2032"))continue;found=true;cursorWindow=identity.group(1);
    if(!block.contains("mViewVisibility=0x0")||!block.contains("isVisible=true"))break;
    Matcher position=Pattern.compile("mAttrs=\\{\\((-?\\d+),(-?\\d+)\\)").matcher(block),size=Pattern.compile("Requested w=(\\d+) h=(\\d+)").matcher(block);
    if(position.find()&&size.find()){px=Integer.parseInt(position.group(1))+Integer.parseInt(size.group(1))/2f;py=Integer.parseInt(position.group(2))+Integer.parseInt(size.group(2))/2f;}break;
   }
   if(text.indexOf("Permission Denial")>=0||text.indexOf("Permission denied")>=0)throw new SecurityException("window dump denied");
   if(!found)cursorWindow=null;final float x=px,y=py;final boolean cursorFound=found;
   final int responseLength=text.length();final boolean windowResponse=text.indexOf("WINDOW MANAGER WINDOWS")>=0;
   final String failure=windowResponse?"":text.substring(0,Math.min(text.length(),160)).trim();
   main.post(()->{if(!alive)return;kdeSide=x<0||y<0?-1:sideAt(x,y);if(!reportedDumpResponse){reportedDumpResponse=true;EventReceiver.record(this,"커서 진단 응답: "+responseLength+" bytes, window="+windowResponse+", cursor="+cursorFound+" "+failure);}if(cursorFound&&!reportedCursorWindow){reportedCursorWindow=true;EventReceiver.record(this,"KDE 커서 창 감시 준비");}if(x>=0&&!reportedCursor){reportedCursor=true;EventReceiver.record(this,"KDE 커서 좌표 감지 연결됨");}});
  }catch(Exception e){final String error=e.getClass().getSimpleName()+": "+e.getMessage();main.post(()->{kdeSide=-1;if(alive&&!reportedDumpFailure){reportedDumpFailure=true;EventReceiver.record(this,"KDE 커서 진단 읽기 실패: "+error);}});}finally{if(pipe!=null)for(android.os.ParcelFileDescriptor fd:pipe)try{fd.close();}catch(IOException ignored){}}
 }
 private void removeOverlays(){if(windows!=null)for(View v:overlays)try{windows.removeView(v);}catch(IllegalArgumentException ignored){}overlays.clear();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e.getPackageName()!=null){String pkg=e.getPackageName().toString(); TaskSwitcherActivity.remember(this,pkg); if(e.getClassName()!=null){String cls=e.getClassName().toString(); try{getPackageManager().getActivityInfo(new android.content.ComponentName(pkg,cls),0); foregroundPackage=pkg;foregroundActivity=cls;}catch(Exception ignored){}}}}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){instance=null;hidePairing();alive=false;main.removeCallbacksAndMessages(null);if(cursorReader!=null)cursorReader.shutdownNow();if(timeouts!=null)timeouts.shutdownNow();removeOverlays();if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();wifiLock=null;super.onDestroy();}
}
