package com.happy.cinemaframe;

import org.json.*;
import java.util.*;

/** Music styles apply to individual recordings, never to film genres or artist genres. */
final class MusicStyle {
 static String requested(String text){
  String n=text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");
  String[][] aliases={{"dance","댄스","춤","디스코","dance","disco"},{"jazz","재즈","jazz"},{"rock","록","락음악","락영화","rock"},{"electronic","일렉트로닉","전자음악","edm","테크노","electronic"},{"hiphop","힙합","hiphop","hip-hop"},{"classical","클래식","classical"},{"blues","블루스","blues"},{"ambient","앰비언트","ambient"},{"ballad","발라드","ballad"},{"rnb","알앤비","r&b","rnb"},{"pop","팝음악","팝송","pop"}};
  for(String[] a:aliases)for(int i=1;i<a.length;i++)if(n.contains(a[i]))return a[0];return "";
 }
 static String label(String style){String[][] names={{"dance","댄스"},{"jazz","재즈"},{"rock","록"},{"electronic","일렉트로닉"},{"hiphop","힙합"},{"classical","클래식"},{"blues","블루스"},{"ambient","앰비언트"},{"ballad","발라드"},{"rnb","R&B"},{"pop","팝"}};for(String[] n:names)if(n[0].equals(style))return n[1];return style;}
 static List<String> queries(JSONObject profile){String style=profile.optString("music_style");if(style.equals("ballad")){String mood=profile.optString("music_mood");return Arrays.asList(mood.equals("happy")?"romantic ballad soundtrack":mood.equals("sad")?"sad ballad soundtrack":"ballad soundtrack",mood.equals("happy")?"happy ballad soundtrack":"romantic ballad soundtrack");}return queries(style);}
 static List<String> queries(String style){
  String genres=style.equals("dance")?"dance|disco|dance-pop":style.equals("electronic")?"electronic|house|techno":style.equals("hiphop")?"hip-hop|rap":style.equals("rnb")?"r&b|soul":style.equals("ballad")?"ballad|k-ballad|power ballad|korean ballad":style;
  List<String> out=new ArrayList<>();out.add("genre:\""+style+"\" album:\"motion picture\"");for(String genre:genres.split("\\|"))out.add("genre:\""+genre+"\" album:\"soundtrack\"");out.add("genre:\""+style+"\" soundtrack");return out;
 }
 static String cleanTitle(String s){return s.replaceFirst("(?i)\\s*[-–]\\s*(?:from|remaster|.*version|.*edit|.*mix).*$","").trim();}
 static boolean sameTitle(String a,String b){return SoundtrackMatcher.normalize(cleanTitle(a)).equals(SoundtrackMatcher.normalize(cleanTitle(b)));}
 static boolean unsafeVersion(String s){return s.toLowerCase(Locale.ROOT).matches(".*(karaoke|tribute|sped up|slowed|nightcore|made famous|instrumental cover).*");}
 static boolean moodMatches(JSONObject profile,JSONObject track){return !profile.optString("music_style").equals("ballad")||profile.optString("music_mood").isEmpty()||profile.optString("music_mood").equals(track.optString("music_mood"));}
 static boolean accepts(String style,JSONObject track){JSONArray styles=track.optJSONArray("music_styles");if(styles==null||track.optString("style_evidence").isEmpty())return false;for(int i=0;i<styles.length();i++)if(style.equals(styles.optString(i)))return true;return false;}
}
