package com.happy.cinemaframe;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** One bounded request; input evidence and final confidence are kept by the index. */
final class GeminiJudge {
 static final String MODEL="gemini-2.5-flash";
 static Map<String,JSONObject> judge(JSONArray input,String key,JSONObject dictionary)throws Exception{
  if(input.length()==0)return new LinkedHashMap<>();if(input.length()>50)throw new IOException("Gemini batch exceeds 50 tracks");
  JSONArray styles=new JSONArray(Arrays.asList("jazz","classical","rock","pop","electronic","orchestral_score","dance","blues","hiphop","ambient","ballad","rnb","other"));
  JSONArray jazz=dictionary.getJSONObject("genres").getJSONArray("jazz");for(int i=0;i<jazz.length();i++)if(!IndexRules.contains(styles,jazz.getString(i)))styles.put(jazz.getString(i));
  JSONArray keys=new JSONArray();for(int i=0;i<input.length();i++)keys.put(input.getJSONObject(i).getString("key"));
  JSONObject properties=new JSONObject().put("key",enumeration(keys)).put("knowledge",enumeration(new JSONArray(Arrays.asList("known","unknown"))))
   .put("confidence",enumeration(new JSONArray(Arrays.asList("high","medium","low"))))
   .put("styles",new JSONObject().put("type","ARRAY").put("items",enumeration(styles)))
   .put("moods",new JSONObject().put("type","ARRAY").put("items",enumeration(new JSONArray(Arrays.asList("epic","calm","upbeat")))))
   .put("reason",new JSONObject().put("type","STRING"));
  JSONObject item=new JSONObject().put("type","OBJECT").put("properties",properties).put("required",new JSONArray(Arrays.asList("key","knowledge","confidence","styles","moods","reason")));
  JSONObject schema=new JSONObject().put("type","OBJECT").put("properties",new JSONObject().put("decisions",new JSONObject().put("type","ARRAY").put("items",item))).put("required",new JSONArray().put("decisions"));
  String prompt="Judge each specific OST recording, not the movie genre or composer in general. Artist top-five tags and track tags are supporting evidence only: a composer tagged jazz does not make every score jazz (for example Psycho). Return exactly one decision per input key. Assess styles including jazz subgenres, moods epic/calm/upbeat, confidence high/medium/low and one short Korean reason explaining the recording-specific basis. Do not invent musical facts. If you cannot identify the recording or have insufficient evidence, knowledge=unknown, confidence=low, styles=[], moods=[], reason explaining unknown. If uncertain set confidence=low, never force a confident label from the title or artist tags alone. For jazz subgenres also include jazz. Evidence and input:\n"+input;
  JSONObject body=new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))))
   .put("generationConfig",new JSONObject().put("temperature",0).put("responseMimeType","application/json").put("responseSchema",schema));
  HttpURLConnection c=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(120000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("x-goog-api-key",key);c.setRequestProperty("Content-Type","application/json");
  try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}int status=c.getResponseCode();if(status!=200)throw new IOException("Gemini HTTP "+status);
   JSONObject response=new JSONObject(new String(FrameCore.read(c.getInputStream(),4000000),"UTF-8"));JSONArray parts=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");StringBuilder text=new StringBuilder();for(int i=0;i<parts.length();i++)if(!parts.getJSONObject(i).optBoolean("thought"))text.append(parts.getJSONObject(i).optString("text"));
   Map<String,JSONObject> result=IndexRules.validate(input,new JSONObject(text.toString()));
   for(int i=0;i<input.length();i++){JSONObject row=input.getJSONObject(i),decision=result.get(row.getString("key"));decision.put("model",MODEL).put("evidence",row);for(int n=0;n<decision.getJSONArray("styles").length();n++)if(!IndexRules.contains(styles,decision.getJSONArray("styles").getString(n)))throw new IOException("Gemini invalid style");}
   return result;
  }finally{c.disconnect();}
 }
 private static JSONObject enumeration(JSONArray values)throws Exception{return new JSONObject().put("type","STRING").put("enum",values);}
}
