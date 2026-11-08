package com.happy.cinemaframe;
import org.json.*;
import java.util.*;
import java.io.IOException;

/** Final decisions are Gemini-only; artist tags are evidence, never a verdict. */
final class IndexRules {
 static boolean contains(JSONArray a,String s){if(a!=null)for(int i=0;i<a.length();i++)if(s.equals(a.optString(i)))return true;return false;}
 static String canonical(String s,JSONObject dictionary){String n=s.toLowerCase(Locale.ROOT).replaceAll("[_/-]+"," ").replaceAll("\\s+"," ").trim();return dictionary.optJSONObject("aliases")==null?n:dictionary.optJSONObject("aliases").optString(n,n);}
 static boolean styleMatches(JSONArray styles,String wanted,JSONObject dictionary){
  if(wanted.isEmpty())return true;JSONArray children=dictionary.optJSONObject("genres")==null?null:dictionary.optJSONObject("genres").optJSONArray(wanted);
  if(styles!=null)for(int i=0;i<styles.length();i++){String s=canonical(styles.optString(i),dictionary);if(s.equals(wanted)||contains(children,s))return true;}return false;
 }
 static boolean accepts(JSONObject track,JSONObject profile,JSONObject dictionary,long now){
  JSONObject d=track.optJSONObject("gemini");if(d==null||!"known".equals(d.optString("knowledge")))return false;
  if(!Arrays.asList("high","medium").contains(d.optString("confidence")))return false;
  long at=d.optLong("fetched_at");if(at<=0||now<at||now-at>=OstPolicy.TTL)return false;
  if(!styleMatches(d.optJSONArray("styles"),profile.optString("music_style"),dictionary))return false;
  String energy=profile.optString("energy");String mood=energy.equals("bright")?"upbeat":energy.equals("calm")||profile.optString("music_mood").equals("rainy")?"calm":energy.equals("epic")?"epic":"";
  return mood.isEmpty()||contains(d.optJSONArray("moods"),mood);
 }
 static Map<String,JSONObject> validate(JSONArray input,JSONObject response)throws Exception{
  Set<String> keys=new HashSet<>();for(int i=0;i<input.length();i++)if(!keys.add(input.getJSONObject(i).getString("key")))throw new IOException("Duplicate input key");
  Map<String,JSONObject> result=new LinkedHashMap<>();JSONArray ds=response.getJSONArray("decisions");
  for(int i=0;i<ds.length();i++){JSONObject d=ds.getJSONObject(i);String k=d.getString("key");if(!keys.contains(k)||result.containsKey(k))throw new IOException("Gemini key mismatch");
   if(!Arrays.asList("known","unknown").contains(d.optString("knowledge"))||!Arrays.asList("high","medium","low").contains(d.optString("confidence"))||d.optString("reason").trim().isEmpty())throw new IOException("Gemini incomplete decision");
   JSONArray styles=d.getJSONArray("styles"),moods=d.getJSONArray("moods");if(d.optString("knowledge").equals("unknown")&&(styles.length()>0||moods.length()>0))throw new IOException("Unknown Gemini decision has labels");
   for(int n=0;n<moods.length();n++)if(!Arrays.asList("epic","calm","upbeat").contains(moods.getString(n)))throw new IOException("Gemini invalid mood");
   d.put("source","Gemini 판단").put("fetched_at",System.currentTimeMillis()).put("expires_at",System.currentTimeMillis()+OstPolicy.TTL);result.put(k,d);
  }
  if(result.size()!=keys.size())throw new IOException("Gemini omitted tracks");return result;
 }
}
