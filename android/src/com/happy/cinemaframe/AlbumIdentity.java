package com.happy.cinemaframe;
import org.json.*;import java.text.Normalizer;import java.util.*;import java.util.regex.*;
/** Port of the audited strict recovery rules, version strict-2026-11-08. */
final class AlbumIdentity {
 static String norm(String s){return Normalizer.normalize(s,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 static String core(String s){
  s=s.replaceAll("(?i)\\bepisode\\s+(?:[ivx]+|[0-9]+)\\b","").split("[\\[(]",2)[0];
  s=s.replaceFirst("(?i)^(?:original\\s+)?(?:motion picture|film)\\s+(?:soundtrack|score)\\s*[:/-]?\\s*","");
  s=s.replaceFirst("(?i)(?:\\s*[:/–-]\\s*(?:original\\s+)?(?:motion picture\\s+)?(?:soundtrack|score)|\\s+(?:original\\s+(?:motion picture\\s+)?(?:soundtrack|score)|motion picture\\s+(?:soundtrack|score)|soundtrack|music from|music composed|bande originale))\\b.*$","");return norm(s);
 }
 static boolean title(JSONObject movie,String title){
  if(Pattern.compile("\\b(live|cover|tribute|karaoke|reimagined|suite|escapades|arranged|solo piano|cinema classics)\\b|\\barr\\.",Pattern.CASE_INSENSITIVE).matcher(title).find())return false;
  String c=core(title);return !c.isEmpty()&&(c.equals(core(movie.optString("title")))||c.equals(core(movie.optString("originalTitle"))));
 }
 static boolean release(JSONObject movie,JSONObject spotify,JSONObject deezer){
  if(spotify.optString("album_type").equals("single")||deezer.optString("record_type").equals("single"))return false;
  if(Pattern.compile("soundtrack|motion picture|original score|music from|bande originale|original music",Pattern.CASE_INSENSITIVE).matcher(deezer.optString("title")).find())return true;
  String evidence=spotify.optString("release_date")+" "+spotify.optJSONArray("copyrights");Matcher years=Pattern.compile("\\b(?:19|20)[0-9]{2}\\b").matcher(evidence);
  while(years.find())if(Math.abs(Integer.parseInt(years.group())-movie.optInt("year"))<=1)return true;return false;
 }
}
