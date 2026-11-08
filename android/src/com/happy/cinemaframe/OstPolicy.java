package com.happy.cinemaframe;
import org.json.*;
import java.util.*;
import java.util.regex.*;
import java.io.IOException;

/** Pure selection rules. No curated movie, album or recording identifiers. */
final class OstPolicy {
 static final long TTL=7L*24*60*60*1000;
 static final String COUNTRY_CODES="IN HK KR JP CN TW TH ID US CA MX GB FR DE IT ES RU TR IR EG NG BR AR AU";
 static JSONArray countries(JSONObject p)throws Exception {
  JSONArray a=p.optJSONArray("excluded_countries");
  if(a==null){a=new JSONArray();if(p.optBoolean("exclude_india"))a.put("IN");}
  return new JSONArray(a.toString());
 }
 static void migrate(JSONObject p)throws Exception {p.put("excluded_countries",countries(p));p.remove("exclude_india");}
 static boolean countryBlocked(JSONObject movie,JSONObject p){
  if(!p.optBoolean("country_exclusions_enabled",true))return false;
  JSONArray excluded=p.optJSONArray("excluded_countries"),actual=movie.optJSONArray("countries");
  if(excluded==null)return p.optBoolean("exclude_india")&&movie.optBoolean("country_india");
  if(actual==null)return false;
  for(int i=0;i<actual.length();i++)for(int j=0;j<excluded.length();j++)if(actual.optString(i).equals(excluded.optString(j)))return true;
  return false;
 }
 static JSONArray validateCountries(JSONArray input)throws Exception {
  Set<String> allowed=new HashSet<>(Arrays.asList(COUNTRY_CODES.split(" "))),seen=new LinkedHashSet<>();
  for(int i=0;i<input.length();i++){String c=input.getString(i);if(!allowed.contains(c))throw new IOException("지원하지 않는 국가: "+c);seen.add(c);}
  return new JSONArray(seen);
 }
 static JSONObject interpret(String text,JSONObject previous)throws Exception {
  JSONObject p=new JSONObject(previous.toString());migrate(p);
  String n=text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");
  // A full request starts fresh; persistent exclusions are the only inherited constraints.
  if(!n.contains("빼고")&&!n.contains("포함")&&!n.contains("제외")){
   p=new JSONObject().put("excluded_countries",countries(previous)).put("country_exclusions_enabled",previous.optBoolean("country_exclusions_enabled",true)).put("excluded_movies",previous.optJSONArray("excluded_movies")==null?new JSONArray():previous.getJSONArray("excluded_movies"));
  }
  p.put("pipeline","deezer-isrc-v1");JSONArray genres=new JSONArray();String style=MusicStyle.requested(text);
  String[][] mappings={{"sf","Sci-Fi"},{"공상과학","Sci-Fi"},{"액션","Action"},{"모험","Adventure"},{"로맨스","Romance"},{"코미디","Comedy"},{"공포","Horror"},{"스릴러","Thriller"},{"판타지","Fantasy"},{"애니","Animation"},{"뮤지컬","Musical"}};
  Set<String> gs=new LinkedHashSet<>();for(String[] pair:mappings)if(n.contains(pair[0]))gs.add(pair[1]);
  String projection="명시한 영화 장르만 적용";
  if(gs.isEmpty()&&!style.isEmpty()){gs.add("Music");projection="음악 스타일을 IMDb Music 영화 후보로 변환(음악 스타일 일치 보장은 아님)";}
  for(String g:gs)genres.put(g);p.put("genres",genres).put("music_style",style).put("style","catalogue");
  String mood=n.contains("비오는")||n.contains("비내리는")?"rainy":n.matches(".*(행복|기쁜|즐거운).*" )?"happy":n.matches(".*(슬픈|우울).*" )?"sad":"";
  String energy=n.contains("웅장")?"epic":mood.equals("rainy")||n.contains("차분")||n.contains("잔잔")||n.contains("조용")?"calm":n.contains("경쾌")||n.contains("신나")||n.contains("밝은")?"bright":"balanced";
  p.put("music_mood",mood).put("energy",energy);
  Matcher era=Pattern.compile("(?<![0-9])((?:19|20)?[0-9]0)년대").matcher(n);
  p.remove("era");if(era.find()){int y=Integer.parseInt(era.group(1));if(y<100)y+=y>=30?1900:2000;p.put("era",y);}
  if(style.isEmpty()&&gs.isEmpty()&&mood.isEmpty()&&energy.equals("balanced")&&!p.has("era")&&!n.matches(".*(전체|랜덤|아무거나).*"))throw new IOException("조건을 해석하지 못했습니다. 영화 장르·음악 스타일·시대를 명시해 주세요.");
  p.put("interpretation_note",projection+" · 분위기는 해석해 표시하며 Deezer rank는 분위기 점수가 아닙니다");
  return p;
 }
 static boolean eligible(JSONObject m,JSONObject p){
  if(countryBlocked(m,p))return false;
  JSONArray exclusions=p.optJSONArray("excluded_movies");if(exclusions!=null)for(int i=0;i<exclusions.length();i++)if(m.optString("id").equals(exclusions.optString(i)))return false;
  int era=p.optInt("era");if(era!=0&&(m.optInt("year")<era||m.optInt("year")>=era+10))return false;
  if(m.optDouble("rating")<7||m.optInt("votes")<10000)return false;
  JSONArray wanted=p.optJSONArray("genres"),actual=m.optJSONArray("genres");
  if(wanted!=null)for(int i=0;i<wanted.length();i++){boolean found=false;if(actual!=null)for(int j=0;j<actual.length();j++)if(wanted.optString(i).equals(actual.optString(j)))found=true;if(!found)return false;}
  return true;
 }
 static List<JSONObject> candidates(Collection<JSONObject> movies,JSONObject p){
  List<JSONObject> list=new ArrayList<>();for(JSONObject m:movies)if(eligible(m,p))list.add(m);
  list.sort(Comparator.comparingDouble((JSONObject m)->m.optDouble("rating")).reversed().thenComparing(Comparator.comparingInt((JSONObject m)->m.optInt("votes")).reversed()).thenComparing(m->m.optString("id")));
  return new ArrayList<>(list.subList(0,Math.min(20,list.size())));
 }
 static List<JSONObject> top3(JSONArray tracks)throws Exception {
  List<JSONObject> list=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(int i=0;i<tracks.length();i++){JSONObject t=tracks.getJSONObject(i);if(!t.has("rank")||t.isNull("rank")||!(t.get("rank") instanceof Number))throw new IOException("Deezer rank 누락: "+t.optString("id"));if(ids.add(t.optString("id")))list.add(t);}
  list.sort(Comparator.comparingInt((JSONObject t)->t.optInt("rank")).reversed().thenComparing(t->t.optString("id")));
  return new ArrayList<>(list.subList(0,Math.min(3,list.size())));
 }
 static boolean fresh(JSONObject cache,long now){long at=cache.optLong("fetched_at");return at>0&&now>=at&&now-at<TTL&&cache.optString("source").equals("Deezer public API + Spotify ISRC search");}
 static JSONObject spotifyMatch(JSONArray hits,String isrc){
  for(int i=0;i<hits.length();i++){JSONObject t=hits.optJSONObject(i);if(t==null||!t.optBoolean("is_playable",false))continue;JSONObject ids=t.optJSONObject("external_ids");if(ids!=null&&isrc.equalsIgnoreCase(ids.optString("isrc"))&&t.optString("uri").startsWith("spotify:track:"))return t;}return null;
 }
}
