package com.happy.cinemaframe;
import org.json.*;import java.nio.file.*;import java.util.*;
/** LIVE API driver, intentionally separate from mock regression tests. */
public class GeminiFinalLive {
 public static void main(String[] args)throws Exception{
  JSONObject dict=new JSONObject(new String(Files.readAllBytes(Paths.get("config/music-tags.json")),"UTF-8"));
  JSONArray input=new JSONArray(new String(Files.readAllBytes(Paths.get("work/gemini-final-input.json")),"UTF-8"));
  String key=new String(Files.readAllBytes(Paths.get("work/gemini-api-key.txt")),"UTF-8").replace("\uFEFF","").trim();
  long at=System.currentTimeMillis();Map<String,JSONObject> decisions=GeminiJudge.judge(input,key,dict);JSONArray rows=new JSONArray();for(JSONObject d:decisions.values())rows.put(d);
  JSONObject result=new JSONObject().put("source","Gemini 판단").put("started_at",at).put("elapsed_ms",System.currentTimeMillis()-at).put("input",input).put("decisions",rows);
  Files.write(Paths.get("work/gemini-final-live.json"),result.toString(2).getBytes("UTF-8"));
  int eligible=0;for(JSONObject d:decisions.values())if(d.getString("knowledge").equals("known")&&!d.getString("confidence").equals("low"))eligible++;
  System.out.println("LIVE Gemini final: "+rows.length()+" decisions, "+eligible+" known high/medium, "+result.getLong("elapsed_ms")+"ms");
 }
}
