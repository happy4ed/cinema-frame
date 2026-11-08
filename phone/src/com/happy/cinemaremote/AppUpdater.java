package com.happy.cinemaremote;
import android.app.*;import android.content.*;import android.content.pm.*;import android.net.Uri;import android.provider.Settings;import java.net.*;import java.io.*;import java.util.*;import org.json.*;
final class AppUpdater {
 interface Status{void show(String message);}
 private final Activity activity;private final Status status;private boolean busy=false;private File pending;
 AppUpdater(Activity a,Status s){activity=a;status=s;}
 private void message(String s){activity.runOnUiThread(()->status.show(s));}
 synchronized void check(){if(busy)return;busy=true;message("업데이트 확인 중…");new Thread(()->{try{
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/happy4ed/cinema-frame/releases/latest").openConnection();c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Cinema-Remote");c.setConnectTimeout(10000);c.setReadTimeout(20000);JSONObject release;
  try{int code=c.getResponseCode();if(code==404){message("아직 배포된 최신 릴리스가 없습니다");return;}if(code!=200)throw new IOException("릴리스 확인 실패 (HTTP "+code+")");ByteArrayOutputStream out=new ByteArrayOutputStream();copy(c.getInputStream(),out,2000000);release=new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}
  PackageInfo installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
  if(!ReleaseVersion.newer(release.getString("tag_name"),installed.versionName)){message("최신 버전입니다 · "+installed.versionName);return;}
  JSONObject asset=null;JSONArray assets=release.getJSONArray("assets");for(int i=0;i<assets.length();i++)if("Cinema-Remote.apk".equals(assets.getJSONObject(i).optString("name")))asset=assets.getJSONObject(i);
  if(asset==null)throw new IOException("릴리스에 휴대폰 APK가 없습니다");String link=asset.getString("browser_download_url");if(!link.startsWith("https://github.com/happy4ed/cinema-frame/releases/download/"))throw new IOException("잘못된 다운로드 주소");
  message("새 버전 다운로드 중…");File file=new File(activity.getCacheDir(),"Cinema-Remote.apk");HttpURLConnection download=(HttpURLConnection)new URL(link).openConnection();download.setConnectTimeout(10000);download.setReadTimeout(30000);
  try(FileOutputStream out=new FileOutputStream(file)){if(download.getResponseCode()!=200)throw new IOException("APK 다운로드 실패");copy(download.getInputStream(),out,50000000);}finally{download.disconnect();}
  if(file.length()!=asset.getLong("size"))throw new IOException("APK 크기 확인 실패");PackageInfo apk=activity.getPackageManager().getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES);
  if(apk==null||!activity.getPackageName().equals(apk.packageName)||apk.versionCode<=installed.versionCode||!Arrays.equals(installed.signatures,apk.signatures))throw new IOException("APK 버전 또는 기존 서명이 일치하지 않습니다");
  pending=file;activity.runOnUiThread(()->install(true));
 }catch(Exception e){message("업데이트 실패 · "+e.getMessage());}finally{synchronized(this){busy=false;}}},"Cinema-update").start();}
 private static void copy(InputStream in,OutputStream out,long max)throws Exception{try(InputStream input=in){byte[] b=new byte[8192];int n;long total=0;while((n=input.read(b))!=-1){total+=n;if(total>max)throw new IOException("다운로드 크기 초과");out.write(b,0,n);}}}
 void resume(){if(pending!=null&&activity.getPackageManager().canRequestPackageInstalls())install(false);}
 private void install(boolean requestPermission){try{if(pending==null)return;if(!activity.getPackageManager().canRequestPackageInstalls()){message("이 앱의 업데이트 설치를 허용한 뒤 돌아와 주세요");if(requestPermission)activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;}
  Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/Cinema-Remote.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Cinema Remote update",uri));activity.startActivity(intent);pending=null;message("설치 화면에서 업데이트를 눌러 주세요");
 }catch(Exception e){message("설치 화면을 열지 못했습니다 · "+e.getMessage());}}
}
