package com.happy.cinemaframe;
import org.json.*;import java.util.*;
public class SoundtrackRegression{
 static JSONObject film(String id,String title,int year)throws Exception{return new JSONObject().put("id",id).put("title",title).put("originalTitle",title).put("year",year);}
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  List<JSONObject> movies=Arrays.asList(film("up","Up",2009),film("her","Her",2013),film("brother","Brother",2000),film("dune84","Dune",1984),film("dune21","Dune",2021));
  JSONObject album=new JSONObject().put("name","Up (Original Motion Picture Soundtrack)").put("release_date","2009-01-01");check(SoundtrackMatcher.match(movies,album).getString("id").equals("up"),"Short exact film name");
  album.put("name","Brother (Original Soundtrack)");check(SoundtrackMatcher.match(movies,album).getString("id").equals("brother"),"Substring film collision");
  album.put("name","Dune (Original Motion Picture Soundtrack)").put("release_date","2021-10-01");check(SoundtrackMatcher.match(movies,album).getString("id").equals("dune21"),"Wrong remake");
  album.put("release_date","2010-01-01");check(SoundtrackMatcher.match(movies,album)==null,"Ambiguous remake should be rejected");
  album.put("name","Greatest Film Soundtracks: Up, Her and Dune");check(SoundtrackMatcher.match(movies,album)==null,"Compilation mislabeled as film");
  album.put("name","Up Karaoke Party");check(SoundtrackMatcher.match(movies,album)==null,"Non-soundtrack accepted");
  check(SoundtrackMatcher.queries(new JSONObject().put("style","dance")).contains("dance soundtrack"),"Dance search missing");
  System.out.println("PASS Spotify album-to-film matching: short titles, collisions, remakes, compilations, query mapping");
 }
}
