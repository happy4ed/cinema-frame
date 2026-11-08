package com.happy.cinemaframe;
import org.json.*;
import java.util.*;
public class OstPolicyRegression {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
  JSONObject old=new JSONObject().put("exclude_india",true);OstPolicy.migrate(old);check(old.getJSONArray("excluded_countries").getString(0).equals("IN"),"India migration");check(!old.has("exclude_india"),"legacy field retained");
  JSONObject rainy=OstPolicy.interpret("비 오는 날 재즈",old);check(rainy.getString("music_style").equals("jazz")&&rainy.getString("music_mood").equals("rainy"),"rain/jazz parse");
  JSONObject epic=OstPolicy.interpret("80년대 SF 웅장한",rainy);check(epic.getInt("era")==1980&&epic.getJSONArray("genres").toString().equals("[\"Sci-Fi\"]")&&epic.getString("energy").equals("epic"),"SF/era overwritten");check(epic.getString("music_style").isEmpty(),"stale style carried over");
  JSONArray ts=new JSONArray();for(int i=1;i<=4;i++)ts.put(new JSONObject().put("id",i).put("rank",i*100));
  List<JSONObject> top=OstPolicy.top3(ts);check(top.size()==3&&top.get(0).getInt("id")==4&&top.get(2).getInt("id")==2,"rank order");
  boolean failed=false;try{OstPolicy.top3(new JSONArray().put(new JSONObject().put("id",1)));}catch(Exception e){failed=true;}check(failed,"missing rank must stop");
  JSONArray hits=new JSONArray().put(new JSONObject().put("uri","spotify:track:wrong").put("is_playable",true).put("external_ids",new JSONObject().put("isrc","WRONG")));
  check(OstPolicy.spotifyMatch(hits,"US1234567890")==null,"Title fallback or mismatched ISRC");
  JSONObject exact=new JSONObject().put("uri","spotify:track:exact").put("external_ids",new JSONObject().put("isrc","US1234567890"));hits.put(exact);check(OstPolicy.spotifyMatch(hits,"US1234567890")==null,"missing playable accepted");exact.put("is_playable",true);check(OstPolicy.spotifyMatch(hits,"US1234567890")==exact,"exact playable rejected");
  JSONObject cache=new JSONObject().put("fetched_at",1000).put("source","Deezer public API + Spotify ISRC search");check(OstPolicy.fresh(cache,1001),"fresh cache");check(!OstPolicy.fresh(cache,1000+OstPolicy.TTL),"7-day expiry");check(!OstPolicy.fresh(cache,999),"future cache");
  boolean invalid=false;try{OstPolicy.validateCountries(new JSONArray().put("ZZ"));}catch(Exception e){invalid=true;}check(invalid,"invalid country silently accepted");
  check(OstPolicy.COUNTRY_CODES.split(" ").length==24,"country count");
  List<JSONObject> films=new ArrayList<>();for(int i=0;i<25;i++)films.add(new JSONObject().put("id","f"+i).put("year",1985).put("rating",8).put("votes",10000+i).put("genres",new JSONArray().put("Sci-Fi")).put("countries",new JSONArray().put("CN")));
  check(OstPolicy.candidates(films,epic).size()==20&&OstPolicy.candidates(films,epic).get(0).getString("id").equals("f24"),"candidate cap/order");epic.put("excluded_countries",new JSONArray().put("CN"));check(OstPolicy.candidates(films,epic).isEmpty(),"zero candidates after exclusion");
  System.out.println("PASS OstPolicyRegression (MOCK): interpretation, migration, rank top3, missing-rank stop, exact playable ISRC, 7-day expiry, 24 countries");
 }
}
