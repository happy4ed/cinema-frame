package com.happy.cinemaframe;
import org.json.*;import java.nio.file.*;
public class IndexRulesRegression {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  JSONObject dict=new JSONObject(new String(Files.readAllBytes(Paths.get("config/music-tags.json")),"UTF-8"));long now=100000;
  JSONObject p=new JSONObject().put("music_style","jazz").put("energy","calm");JSONObject t=new JSONObject().put("artist_tags",new JSONArray().put("jazz"));
  check(!IndexRules.accepts(t,p,dict,now),"artist tags must not confirm a track");
  JSONObject d=new JSONObject().put("knowledge","known").put("confidence","high").put("styles",new JSONArray().put("bebop")).put("moods",new JSONArray().put("calm")).put("fetched_at",now-1);t.put("gemini",d);
  check(IndexRules.accepts(t,p,dict,now),"jazz subgenre must match");d.put("confidence","low");check(!IndexRules.accepts(t,p,dict,now),"low confidence accepted");d.put("confidence","medium").put("knowledge","unknown");check(!IndexRules.accepts(t,p,dict,now),"unknown accepted");d.put("knowledge","known");check(!IndexRules.accepts(t,p,dict,now+OstPolicy.TTL),"stale judgment accepted");
  JSONArray input=new JSONArray().put(new JSONObject().put("key","a"));boolean failed=false;
  try{IndexRules.validate(input,new JSONObject().put("decisions",new JSONArray()));}catch(Exception e){failed=true;}check(failed,"missing decision accepted");
  JSONObject invalid=new JSONObject().put("key","a").put("knowledge","known").put("confidence","high").put("reason","fixture").put("styles",new JSONArray().put("jazz")).put("moods",new JSONArray().put("invented_mood"));failed=false;
  try{IndexRules.validate(input,new JSONObject().put("decisions",new JSONArray().put(invalid)));}catch(Exception e){failed=true;}check(failed,"invalid mood accepted");
  System.out.println("PASS IndexRulesRegression (MOCK): Gemini final, confidence, unknown, jazz subgenres, mood, TTL, completeness");
 }
}
