package com.happy.cinemaframe;

import android.content.*;
import android.util.Base64;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Official Spotify catalog search using user-owned OAuth PKCE, never a client secret. */
final class SpotifySearch {
 static final String REDIRECT="http://127.0.0.1:43871/callback";
 private final SharedPreferences prefs;
 private final SecureRandom random=new SecureRandom();
 private String verifier="",state="",pendingClient="",access="";
 private long pendingUntil,accessUntil;private volatile long backoffUntil;
 private volatile String status="Spotify 검색 연결이 필요합니다";
 SpotifySearch(Context c){prefs=c.getSharedPreferences("spotify_search",0);backoffUntil=prefs.getLong("search_backoff_until",0);if(connected())status="Spotify 공식 검색 연결됨";}
 boolean connected(){return !prefs.getString("refresh","").isEmpty();}
 String status(){return status;}
 long retryAt(){return SearchBudget.allowedAt(System.currentTimeMillis(),backoffUntil,prefs.getLong("next_request_at",0),prefs.getLong("request_window_at",0),prefs.getInt("request_window_count",0));}
 private void reserveRequest()throws Exception{
  long now=System.currentTimeMillis(),window=prefs.getLong("request_window_at",0);int count=prefs.getInt("request_window_count",0);
  if(now>=window+SearchBudget.WINDOW){window=now;count=0;}
  long allowed=SearchBudget.allowedAt(now,backoffUntil,prefs.getLong("next_request_at",0),window,count);
  if(backoffUntil>now||allowed-now>SearchBudget.INTERVAL)throw new IOException("Spotify 검색 대기 · 서버 재시도 시각 또는 시간당 60회 상한");
  if(allowed>now)Thread.sleep(allowed-now);
  prefs.edit().putLong("next_request_at",System.currentTimeMillis()+SearchBudget.INTERVAL).putLong("request_window_at",window).putInt("request_window_count",count+1).commit();
 }
 JSONObject budget()throws Exception{return new JSONObject().put("minimum_interval_ms",SearchBudget.INTERVAL).put("hourly_limit",SearchBudget.LIMIT).put("window_started_at",prefs.getLong("request_window_at",0)).put("window_requests",prefs.getInt("request_window_count",0)).put("server_retry_at",backoffUntil).put("next_allowed_at",retryAt());}
 private String nonce(){byte[] b=new byte[32];random.nextBytes(b);return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
 private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
 synchronized JSONObject begin(String client)throws Exception{
  if(!client.matches("[A-Fa-f0-9]{32}"))throw new IOException("Spotify Client ID는 32자리입니다");
  pendingClient=client;verifier=nonce();state=nonce();pendingUntil=System.currentTimeMillis()+600000;
  String challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
  String url="https://accounts.spotify.com/authorize?response_type=code&client_id="+client+"&redirect_uri="+enc(REDIRECT)+"&code_challenge_method=S256&code_challenge="+challenge+"&state="+state;
  return new JSONObject().put("url",url).put("redirect_uri",REDIRECT);
 }
 synchronized void complete(String code,String receivedState)throws Exception{
  if(state.isEmpty()||System.currentTimeMillis()>pendingUntil||!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),receivedState.getBytes(StandardCharsets.UTF_8)))throw new IOException("Spotify 인증이 만료됐습니다. 다시 연결해 주세요");
  if(code.isEmpty()||code.length()>4096)throw new IOException("Spotify 인증 코드가 없습니다");
  JSONObject result=token("grant_type=authorization_code&client_id="+pendingClient+"&code="+enc(code)+"&redirect_uri="+enc(REDIRECT)+"&code_verifier="+verifier);
  String refresh=result.optString("refresh_token");if(refresh.isEmpty())throw new IOException("Spotify 검색 인증을 완료하지 못했습니다");
  prefs.edit().putString("client",pendingClient).putString("refresh",refresh).apply();saveAccess(result);state="";verifier="";status="Spotify 공식 검색 연결됨";
 }
 private JSONObject token(String form)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://accounts.spotify.com/api/token").openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
  try{try(OutputStream out=c.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}if(c.getResponseCode()!=200)throw new IOException("Spotify 검색 인증 실패. 개발자 앱 설정을 확인해 주세요");return new JSONObject(new String(FrameCore.read(c.getInputStream(),100000),StandardCharsets.UTF_8));}finally{c.disconnect();}
 }
 private void saveAccess(JSONObject r){access=r.optString("access_token");accessUntil=System.currentTimeMillis()+Math.max(30,r.optInt("expires_in",3600)-60)*1000L;}
 private synchronized String access()throws Exception{
  if(!connected())throw new IOException("Spotify 검색 연결이 필요합니다");
  if(access.isEmpty()||System.currentTimeMillis()>=accessUntil){JSONObject r=token("grant_type=refresh_token&client_id="+prefs.getString("client","")+"&refresh_token="+enc(prefs.getString("refresh","")));saveAccess(r);if(r.has("refresh_token"))prefs.edit().putString("refresh",r.getString("refresh_token")).apply();}return access;
 }
 JSONArray albums(String title)throws Exception{return searchAlbums("album:\""+title.replace("\"","")+"\" soundtrack",0);}
 JSONArray searchAlbums(String query,int offset)throws Exception{return searchItems(query,offset,"album","albums");}
 JSONArray searchPlaylists(String query,int offset)throws Exception{return searchItems(query,offset,"playlist","playlists");}
 JSONArray searchTracks(String query,int offset)throws Exception{return searchItems(query,offset,"track","tracks");}
 synchronized JSONObject albumDetails(String id)throws Exception{
  if(!id.matches("[A-Za-z0-9]{22}"))throw new IOException("Invalid Spotify album ID");
  if(System.currentTimeMillis()<backoffUntil)throw new IOException("Spotify 검색 요청 간격을 조절 중입니다");
  for(int attempt=0;attempt<2;attempt++){
   reserveRequest();
   HttpURLConnection c=(HttpURLConnection)new URL("https://api.spotify.com/v1/albums/"+id).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("Authorization","Bearer "+access());
   try{int statusCode=c.getResponseCode();if(statusCode==401&&attempt==0){synchronized(this){access="";}continue;}
    if(statusCode==429){int seconds=60;try{seconds=Integer.parseInt(c.getHeaderField("Retry-After"));}catch(Exception ignored){}backoffUntil=System.currentTimeMillis()+Math.max(1,seconds)*1000L;prefs.edit().putLong("search_backoff_until",backoffUntil).apply();throw new IOException("Spotify album HTTP 429; backoff active");}
    if(statusCode!=200)throw new IOException("Spotify album HTTP "+statusCode);
    return new JSONObject(new String(FrameCore.read(c.getInputStream(),2000000),StandardCharsets.UTF_8));
   }finally{c.disconnect();}
  }throw new IOException("Spotify album authentication failed");
 }
 private synchronized JSONArray searchItems(String query,int offset,String type,String collection)throws Exception{
  if(offset<0||offset>990)throw new IllegalArgumentException("Invalid search page");
  if(System.currentTimeMillis()<backoffUntil)throw new IOException("Spotify 검색 요청 간격을 조절 중입니다");
  for(int attempt=0;attempt<2;attempt++){
   reserveRequest();
   HttpURLConnection c=(HttpURLConnection)new URL("https://api.spotify.com/v1/search?type="+type+"&limit=10&offset="+offset+"&q="+enc(query)).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("Authorization","Bearer "+access());
   try{int code=c.getResponseCode();if(code==401&&attempt==0){synchronized(this){access="";}continue;}if(code==429){int seconds=60;try{seconds=Integer.parseInt(c.getHeaderField("Retry-After"));}catch(Exception ignored){}backoffUntil=System.currentTimeMillis()+Math.max(1,seconds)*1000L;prefs.edit().putLong("search_backoff_until",backoffUntil).apply();status="Spotify 검색 제한 · 잠시 후 자동 재시도";throw new IOException(status);}if(code!=200){status="Spotify 검색 오류 (HTTP "+code+")";throw new IOException(status);}JSONObject r=new JSONObject(new String(FrameCore.read(c.getInputStream(),2000000),StandardCharsets.UTF_8));status="Spotify 공식 검색 사용 중";return r.getJSONObject(collection).getJSONArray("items");}finally{c.disconnect();}
  }throw new IOException("Spotify 검색을 다시 연결해 주세요");
 }
 static boolean matches(String title,String album){String a=normalize(title),b=normalize(album);return a.length()>=3&&b.contains(a)&&(b.contains("soundtrack")||b.contains("motionpicture")||b.contains("originalscore")||b.equals(a));}
 private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
}
