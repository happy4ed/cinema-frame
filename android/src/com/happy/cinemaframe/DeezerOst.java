package com.happy.cinemaframe;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Metadata only: no Deezer audio or preview is downloaded. */
final class DeezerOst {
 interface Spotify { JSONArray search(String query)throws Exception; }
 private final Spotify spotify;
 DeezerOst(Spotify spotify){this.spotify=spotify;}
 private JSONObject get(String url)throws Exception {
  if(!url.startsWith("https://api.deezer.com/"))throw new IOException("Unexpected Deezer pagination URL");
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(15000);
  try{int status=c.getResponseCode();if(status!=200)throw new IOException("Deezer HTTP "+status);
   JSONObject o=new JSONObject(new String(FrameCore.read(c.getInputStream(),4000000),"UTF-8"));if(o.has("error"))throw new IOException("Deezer API: "+o.getJSONObject("error").optString("message"));return o;
  }finally{c.disconnect();}
 }
 private static String norm(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 static boolean albumMatches(JSONObject movie,String title){
  String lower=title.toLowerCase(Locale.ROOT);
  if(!(lower.contains("original motion picture")||lower.contains("original soundtrack")||lower.contains("original score")))return false;
  if(lower.matches(".*(karaoke|tribute|reimagined|inspired by|cover version).*"))return false;
  String stem=title.replaceFirst("\\s*[\\[(].*$","").trim();
  return norm(stem).equals(norm(movie.optString("title")))||norm(stem).equals(norm(movie.optString("originalTitle")));
 }
 JSONObject resolve(JSONObject movie)throws Exception {
  long started=System.currentTimeMillis();JSONArray logs=new JSONArray(),playable=new JSONArray();
  JSONObject result=new JSONObject().put("movie_id",movie.optString("id")).put("source","Deezer public API + Spotify ISRC search").put("fetched_at",started).put("expires_at",started+OstPolicy.TTL).put("tracks",playable).put("skips",logs);
  String title=movie.optString("originalTitle",movie.getString("title"));
  String query=title+" soundtrack";
  JSONArray hits=get("https://api.deezer.com/search/album?q="+URLEncoder.encode(query,"UTF-8")+"&limit=25").getJSONArray("data");
  JSONObject selected=null;
  for(int i=0;i<hits.length();i++){JSONObject a=hits.getJSONObject(i);if(albumMatches(movie,a.optString("title"))){selected=a;break;}}
  if(selected==null){logs.put(new JSONObject().put("reason","no_exact_original_soundtrack_album").put("query",query));return result;}
  String albumId=selected.optString("id");result.put("deezer_album_id",albumId).put("album_title",selected.getString("title")).put("album_url","https://www.deezer.com/album/"+albumId).put("album_match_evidence","exact film title + original soundtrack/score designation; publisher authenticity not independently certified");
  JSONArray all=new JSONArray();String url="https://api.deezer.com/album/"+albumId+"/tracks?limit=100";
  while(!url.isEmpty()){JSONObject page=get(url);JSONArray ts=page.getJSONArray("data");for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);if(!t.has("rank")||!t.has("isrc"))t=get("https://api.deezer.com/track/"+t.optString("id"));all.put(t);}url=page.optString("next");}
  JSONArray rankedInputs=new JSONArray();for(int i=0;i<all.length();i++){JSONObject t=all.getJSONObject(i);rankedInputs.put(new JSONObject().put("deezer_track_id",t.optString("id")).put("title",t.optString("title")).put("rank",t.has("rank")?t.get("rank"):JSONObject.NULL).put("isrc",t.optString("isrc")));}result.put("rank_inputs",rankedInputs);
  JSONArray selectedTop=new JSONArray();for(JSONObject t:OstPolicy.top3(all)){
   JSONObject audit=new JSONObject().put("deezer_track_id",t.optString("id")).put("title",t.getString("title")).put("rank",t.getInt("rank")).put("isrc",t.optString("isrc"));selectedTop.put(audit);
   String isrc=t.optString("isrc");if(!isrc.matches("[A-Za-z]{2}[A-Za-z0-9]{3}[0-9]{7}")){audit.put("skip_reason","missing_or_invalid_isrc");logs.put(new JSONObject(audit.toString()));continue;}
   JSONObject hit=OstPolicy.spotifyMatch(spotify.search("isrc:"+isrc),isrc);
   if(hit==null){audit.put("skip_reason","no_exact_playable_spotify_isrc");logs.put(new JSONObject(audit.toString()));continue;}
   audit.put("spotify_uri",hit.getString("uri")).put("is_playable",true);
   StringBuilder artists=new StringBuilder();JSONArray aa=hit.getJSONArray("artists");for(int j=0;j<aa.length();j++){if(j>0)artists.append(", ");artists.append(aa.getJSONObject(j).getString("name"));}
   playable.put(new JSONObject(audit.toString()).put("uri",hit.getString("uri")).put("artist",artists.toString()).put("duration",hit.getLong("duration_ms")).put("movie_id",movie.optString("id")).put("deezer_album_id",albumId).put("album_source","Deezer 순위").put("album_url",result.getString("album_url")).put("fetched_at",started));
  }
  result.put("top3",selectedTop);return result;
 }
}
