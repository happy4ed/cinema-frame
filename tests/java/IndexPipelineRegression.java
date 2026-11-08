package com.happy.cinemaframe;
import org.json.*;import java.nio.file.*;
public class IndexPipelineRegression {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  JSONObject rainy=OstPolicy.interpret("비 오는 날 재즈",new JSONObject());check(rainy.getJSONArray("genres").length()==0,"music style incorrectly constrains film genres");
  check(!OstPolicy.unrestrictedMusic(rainy),"jazz must not use an unclassified cached playlist");
  check(OstPolicy.unrestrictedMusic(OstPolicy.interpret("전체 랜덤",rainy)),"unrestricted random must allow cached-first playback");
  JSONObject sf=OstPolicy.interpret("80년대 SF 웅장한",rainy);check(sf.getJSONArray("genres").toString().equals("[\"Sci-Fi\"]")&&sf.getInt("era")==1980,"explicit movie constraints lost");
  check(MusicStyle.requested("빅밴드 재즈").equals("big band"),"specific subgenre lost");
  JSONObject m=new JSONObject().put("title","The Hunger Games");check(!AlbumIdentity.title(m,"The Hunger Games: The Ballad of Songbirds & Snakes (Original Motion Picture Soundtrack)"),"sequel accepted");
  check(!AlbumIdentity.release(new JSONObject().put("year",2008),new JSONObject().put("album_type","single"),new JSONObject().put("title","Gran Torino")),"single accepted");
  JSONArray rows=new JSONObject(new String(Files.readAllBytes(Paths.get("docs/evidence/2026-11-08/final-judge-album-recovery/album-audit-30.json")),"UTF-8")).getJSONArray("records");
  for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);JSONObject movie=new JSONObject().put("title",r.getString("movie")).put("originalTitle",r.getString("original_title"));check(AlbumIdentity.title(movie,r.getString("album")),"audited title rejected: "+r.getString("movie"));}
  System.out.println("PASS IndexPipelineRegression: MOCK style/era/subgenre/sequel/single + 30 RECORDED REAL title fixtures (not new network calls)");
 }
}
