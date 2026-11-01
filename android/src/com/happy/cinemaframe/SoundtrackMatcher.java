package com.happy.cinemaframe;

import org.json.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative album -> film mapping. Reject compilations and ambiguous remakes. */
final class SoundtrackMatcher {
 private static final Pattern MARKER=Pattern.compile("(?i)(original (?:motion picture |film |movie |broadway |television )?(?:soundtrack|score|cast recording)|(?:motion picture|film|movie) soundtrack|soundtrack|music from (?:the )?(?:motion picture|film|movie))");
 static String title(String album){
  Matcher m=MARKER.matcher(album);if(!m.find())return "";
  String candidate=album.substring(0,m.start()).replaceAll("[\\s\\(\\[\\-:–—]+$","").trim();
  // A leading descriptor is not a title; only accept the explicit 'Music from...' form.
  if(candidate.isEmpty()&&album.toLowerCase(Locale.ROOT).startsWith("music from"))candidate=album.substring(m.end()).replaceAll("^[\\s\\-:–—]+","").trim();
  return normalize(candidate);
 }
 private static final Pattern MARKS=Pattern.compile("\\p{M}"),NON_WORD=Pattern.compile("[^\\p{L}\\p{N}]");
 static String normalize(String s){return NON_WORD.matcher(MARKS.matcher(Normalizer.normalize(s,Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll("");}
 static Map<String,List<JSONObject>> index(Collection<JSONObject> movies){
  Map<String,List<JSONObject>> result=new HashMap<>();
  for(JSONObject movie:movies){Set<String> keys=new HashSet<>(Arrays.asList(normalize(movie.optString("title")),normalize(movie.optString("originalTitle"))));for(String key:keys)if(!key.isEmpty())result.computeIfAbsent(key,k->new ArrayList<>()).add(movie);}
  return result;
 }
 static JSONObject match(Collection<JSONObject> movies,JSONObject album){
  return match(index(movies),album);
 }
 static JSONObject match(Map<String,List<JSONObject>> index,JSONObject album){
  String key=title(album.optString("name"));if(key.isEmpty())return null;
  List<JSONObject> hits=index.get(key);if(hits==null)return null;
  if(hits.size()==1)return hits.get(0);if(hits.size()<2)return null;
  int year=0;try{year=Integer.parseInt(album.optString("release_date").substring(0,4));}catch(Exception ignored){}
  JSONObject selected=null;for(JSONObject movie:hits)if(Math.abs(year-movie.optInt("year"))<=1){if(selected!=null)return null;selected=movie;}return selected;
 }
 static List<String> queries(JSONObject p){
  LinkedHashSet<String> queries=new LinkedHashSet<>();String style=p.optString("style"),energy=p.optString("energy");
  if(style.equals("jazz"))queries.add("jazz soundtrack");
  if(style.equals("dance"))queries.add("dance soundtrack");
  if(style.equals("musical")||style.equals("dance"))queries.add("musical soundtrack");
  if(energy.equals("calm"))queries.add("piano original motion picture soundtrack");
  if(energy.equals("epic"))queries.add("orchestral original motion picture soundtrack");
  queries.add("original motion picture soundtrack");return new ArrayList<>(queries);
 }
}
