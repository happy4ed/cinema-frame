package com.happy.frame.diagnostics;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated local control. No shell, root, ADB, or Fully API is used. */
public class LanControlService extends Service {
 static volatile LanControlService instance;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final SecureRandom random=new SecureRandom();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(12));
 private volatile boolean running;
 private ServerSocket server;
 private PowerManager.WakeLock cpu;
 private String code="";
 private long expires,lastPairRequest;
 private int attempts;
 private static final String CINEMA="com.happy.cinemaframe", FULLY="de.ozerov.fully";
 private static class Failure extends Exception { final int status; Failure(int s,String m){super(m);status=s;} }
 @Override public void onCreate(){
  super.onCreate();instance=this;
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel("lan","Frame LAN control",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  startForeground(8788,new Notification.Builder(this,"lan").setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Frame LAN control").setContentText("Home Assistant · LAN 8788").setContentIntent(open).build());
  cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"FrameLAN:server");cpu.setReferenceCounted(false);cpu.acquire();
  running=true;new Thread(()->listen(),"Frame-LAN").start();
 }
 @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){running=false;instance=null;try{if(server!=null)server.close();}catch(Exception ignored){}workers.shutdownNow();main.removeCallbacksAndMessages(null);if(cpu!=null&&cpu.isHeld())cpu.release();super.onDestroy();}
 static void localPair(){LanControlService s=instance;if(s!=null)s.main.post(()->{try{s.beginPair(true);}catch(Exception e){EventReceiver.show(s,e.getMessage());}});}
 private synchronized void beginPair(boolean local)throws Exception{
  long now=SystemClock.elapsedRealtime();
  if(!local&&now-lastPairRequest<60000&&lastPairRequest!=0)throw new Failure(429,"pairing_rate_limited");
  EdgeGestureService a=EdgeGestureService.instance;if(a==null)throw new Failure(503,"enable_accessibility");
  lastPairRequest=now;code=String.format(Locale.US,"%06d",random.nextInt(1000000));expires=now+300000;attempts=0;
  String shown=code;main.post(()->{if(EdgeGestureService.instance==a)a.showPairing(shown);});
  main.postDelayed(()->{synchronized(this){if(SystemClock.elapsedRealtime()>=expires){code="";if(EdgeGestureService.instance!=null)EdgeGestureService.instance.hidePairing();}}},300100);
 }
 private static boolean privatePeer(InetAddress a){byte[] b=a.getAddress();if(a.isLoopbackAddress())return true;if(b.length!=4)return false;int x=b[0]&255,y=b[1]&255;return x==10||(x==172&&y>=16&&y<=31)||(x==192&&y==168);}
 private void listen(){while(running){try{server=new ServerSocket(8788,16,InetAddress.getByName("0.0.0.0"));while(running){Socket s=server.accept();if(!privatePeer(s.getInetAddress())){s.close();continue;}try{workers.execute(()->handle(s));}catch(RejectedExecutionException e){s.close();}}}catch(Exception e){android.util.Log.e("FrameLAN","listen failed",e);if(running)try{Thread.sleep(2000);}catch(InterruptedException ignored){}}finally{try{if(server!=null)server.close();}catch(Exception ignored){}}}}
 private String line(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(int c;(c=in.read())!=-1;){if(c=='\n')return b.toString("US-ASCII").replace("\r","");if(b.size()>=2048)throw new Failure(400,"header_too_large");b.write(c);}throw new EOFException();}
 private void handle(Socket socket){try(Socket s=socket){s.setSoTimeout(4000);try{
  InputStream in=s.getInputStream();String[] first=line(in).split(" ");if(first.length!=3)throw new Failure(400,"invalid_request");
  Map<String,String> headers=new HashMap<>();int total=0;for(String l;!(l=line(in)).isEmpty();){total+=l.length();if(total>8192)throw new Failure(400,"header_too_large");int sep=l.indexOf(':');if(sep<1)throw new Failure(400,"invalid_header");String key=l.substring(0,sep).toLowerCase(Locale.US);if(headers.containsKey(key))throw new Failure(400,"duplicate_header");headers.put(key,l.substring(sep+1).trim());}
  // Browser-originated control is deliberately unsupported (CSRF / DNS rebinding).
  if(headers.containsKey("origin")||headers.containsKey("transfer-encoding"))throw new Failure(403,"browser_request_rejected");
  int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>4096)throw new Failure(400,"body_too_large");
  byte[] bytes=new byte[length];new DataInputStream(in).readFully(bytes);JSONObject body=length==0?new JSONObject():new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  String path=first[1],method=first[0],cookie=null;
  JSONObject result;
  if(method.equals("POST")&&path.equals("/api/pairing/start")){beginPair(false);result=new JSONObject().put("expires_in",300);}
  else if(method.equals("POST")&&path.equals("/api/pair")){String token=pair(body.optString("code"));cookie="cf_session="+token+"; HttpOnly; SameSite=Strict; Path=/; Max-Age=315360000";result=new JSONObject().put("paired",true);}
  else {if(!authenticated(headers))throw new Failure(401,"unauthorized");result=route(method,path,body);}
  respond(s,200,result,cookie);
 }catch(Failure e){respond(s,e.status,new JSONObject().put("error",e.getMessage()),null);}catch(JSONException|IllegalArgumentException e){respond(s,400,new JSONObject().put("error","invalid_request"),null);}catch(Exception e){respond(s,503,new JSONObject().put("error","control_unavailable"),null);}}catch(Exception ignored){android.util.Log.e("FrameLAN","request failed",ignored);}}
 private void respond(Socket s,int status,JSONObject body,String cookie)throws Exception{byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);String h="HTTP/1.1 "+status+" Response\r\nContent-Type: application/json\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n"+(cookie==null?"":"Set-Cookie: "+cookie+"\r\n")+"\r\n";s.getOutputStream().write(h.getBytes(StandardCharsets.US_ASCII));s.getOutputStream().write(bytes);s.getOutputStream().flush();}
 private String hash(String v)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));return android.util.Base64.encodeToString(d,android.util.Base64.NO_WRAP);}
 private synchronized String pair(String entered)throws Exception{
  if(code.isEmpty()||SystemClock.elapsedRealtime()>expires||attempts>=5)throw new Failure(401,"pairing_expired");
  attempts++;if(!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),entered.getBytes(StandardCharsets.UTF_8)))throw new Failure(401,"invalid_code");
  byte[] b=new byte[32];random.nextBytes(b);String token=android.util.Base64.encodeToString(b,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);
  SharedPreferences p=getSharedPreferences("lan",0);JSONArray old=new JSONArray(p.getString("tokens","[]")),next=new JSONArray();for(int i=Math.max(0,old.length()-7);i<old.length();i++)next.put(old.getString(i));next.put(hash(token));if(!p.edit().putString("tokens",next.toString()).commit())throw new Failure(503,"storage_failed");
  code="";main.post(()->{if(EdgeGestureService.instance!=null)EdgeGestureService.instance.hidePairing();});return token;
 }
 private boolean authenticated(Map<String,String> h)throws Exception{
  String token="",auth=h.getOrDefault("authorization","");if(auth.startsWith("Bearer "))token=auth.substring(7);
  else for(String part:h.getOrDefault("cookie","").split(";")){String t=part.trim();if(t.startsWith("cf_session="))token=t.substring(11);}
  if(token.isEmpty()||token.length()>128)return false;String digest=hash(token);JSONArray tokens=new JSONArray(getSharedPreferences("lan",0).getString("tokens","[]"));for(int i=0;i<tokens.length();i++)if(MessageDigest.isEqual(digest.getBytes(StandardCharsets.UTF_8),tokens.getString(i).getBytes(StandardCharsets.UTF_8)))return true;return false;
 }
 private <T>T onMain(Callable<T> c)throws Exception{if(Looper.myLooper()==Looper.getMainLooper())return c.call();FutureTask<T> task=new FutureTask<>(c);main.post(task);try{return task.get(3,TimeUnit.SECONDS);}catch(TimeoutException e){task.cancel(false);throw new Failure(503,"control_timeout");}catch(ExecutionException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}}
 private int integer(JSONObject b,String key,int min,int max)throws Exception{Object n=b.get(key);if(!(n instanceof Number)||((Number)n).doubleValue()!=((Number)n).intValue())throw new Failure(400,"invalid_"+key);int v=((Number)n).intValue();if(v<min||v>max)throw new Failure(400,"invalid_"+key);return v;}
 private boolean bool(JSONObject b,String key)throws Exception{Object v=b.get(key);if(!(v instanceof Boolean))throw new Failure(400,"invalid_"+key);return (Boolean)v;}
 private JSONObject route(String method,String path,JSONObject b)throws Exception{
  if(method.equals("GET")&&path.equals("/api/state"))return state();
  if(!method.equals("POST"))throw new Failure(404,"not_found");
  if(path.equals("/api/screen")){boolean on=bool(b,"on");onMain(()->{PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);if(on){if(!pm.isInteractive()){PowerManager.WakeLock wake=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"FrameLAN:wake");wake.acquire(1500);}}else{EdgeGestureService a=EdgeGestureService.instance;if(a==null||!a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))throw new Failure(503,"enable_accessibility");}return null;});}
  else if(path.equals("/api/app/foreground")){String pkg=b.getString("package"),cls=pkg.equals(CINEMA)?CINEMA+".MainActivity":pkg.equals(FULLY)?FULLY+".FullyActivity":"";String requested=b.optString("activity",cls);if(requested.startsWith("."))requested=pkg+requested;if(cls.isEmpty()||!cls.equals(requested))throw new Failure(403,"app_not_allowed");final String activity=cls;onMain(()->{EdgeGestureService a=EdgeGestureService.instance;if(a==null)throw new Failure(503,"enable_accessibility");a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(pkg,activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return null;});}
  else if(path.equals("/api/brightness")){int v=integer(b,"value",0,255);boolean auto=b.has("auto")?bool(b,"auto"):false;if(!Settings.System.canWrite(this))throw new Failure(409,"grant_write_settings");if(!Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,auto?1:0)||!Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,v))throw new Failure(503,"brightness_failed");}
  else if(path.equals("/api/volume")){if(!"music".equals(b.getString("stream")))throw new Failure(400,"unsupported_stream");AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);int v=integer(b,"level",0,a.getStreamMaxVolume(AudioManager.STREAM_MUSIC));a.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);}
  else throw new Failure(404,"not_found");
  return new JSONObject().put("accepted",true);
 }
 private JSONObject state()throws Exception{
  PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery==null?100:battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
  return new JSONObject().put("service","frame_diagnostics").put("version",1).put("screen_on",pm.isInteractive()).put("brightness",Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)).put("auto_brightness",Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)==1).put("foreground_package",EdgeGestureService.foregroundPackage).put("foreground_activity",EdgeGestureService.foregroundActivity).put("volume_music",audio.getStreamVolume(AudioManager.STREAM_MUSIC)).put("volume_music_max",audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).put("battery",level<0?JSONObject.NULL:Math.round(level*100f/Math.max(1,scale))).put("plugged",battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0).put("accessibility_ready",EdgeGestureService.instance!=null).put("write_settings",Settings.System.canWrite(this));
 }
}
