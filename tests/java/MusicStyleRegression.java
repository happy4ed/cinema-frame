package com.happy.cinemaframe;
import org.json.*;import java.util.*;import java.lang.reflect.*;import sun.misc.Unsafe;
public class MusicStyleRegression {
 static void check(boolean x,String message){if(!x)throw new AssertionError(message);}
 static void set(Object core,String name,Object value)throws Exception{Field f=FrameCore.class.getDeclaredField(name);f.setAccessible(true);f.set(core,value);}
 public static void main(String[] args)throws Exception{
  check(MusicStyle.requested("댄스 영화음악").equals("dance"),"Dance parse");check(MusicStyle.requested("록 음악").equals("rock"),"Rock parse");check(MusicStyle.requested("클래식").equals("classical"),"Classical parse");
  JSONObject happy=new JSONObject().put("music_style","ballad").put("music_mood","happy");check(MusicStyle.queries(happy).get(0).contains("romantic ballad"),"Happy ballad query missing");check(!MusicStyle.moodMatches(happy,new JSONObject().put("music_mood","sad")),"Sad cached track leaked into happy request");
  JSONObject disco=new JSONObject().put("uri","spotify:track:disco").put("title","Disco").put("music_styles",new JSONArray().put("dance")).put("style_evidence","genre:dance");
  check(MusicStyle.accepts("dance",disco),"Spotify dance result rejected");check(!MusicStyle.accepts("jazz",disco),"Dance leaked into jazz");
  JSONObject ballad=new JSONObject().put("title","Main Theme").put("instrumental",true);check(!MusicStyle.accepts("dance",ballad),"Old album/theme heuristic leaked into dance");
  check(MusicStyle.unsafeVersion("Theme - Karaoke"),"Karaoke accepted");
  JSONObject film=new JSONObject().put("id","fever").put("title","Saturday Night Fever").put("originalTitle","Saturday Night Fever").put("year",1977);
  Map<String,List<JSONObject>> index=SoundtrackMatcher.index(Arrays.asList(film));JSONObject hit=new JSONObject().put("name","Stayin' Alive - From \"Saturday Night Fever\" Soundtrack").put("album",new JSONObject().put("name","Greatest"));
  check(SoundtrackMatcher.matchTrack(index,hit)==film,"Explicit film credit not matched");hit.put("name","Unrelated song");check(SoundtrackMatcher.matchTrack(index,hit)==null,"Unrelated compilation matched");
  Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);FrameCore core=(FrameCore)((Unsafe)uf.get(null)).allocateInstance(FrameCore.class);
  set(core,"styleTracks",Collections.singletonMap("fever",Arrays.asList(disco)));set(core,"unavailableTracks",new HashMap<>());set(core,"seen",new HashSet<>(Arrays.asList("spotify:track:disco")));set(core,"songKeys",new HashSet<>());
  Method available=FrameCore.class.getDeclaredMethod("available",String.class,JSONObject.class,boolean.class);available.setAccessible(true);
  check(((List<?>)available.invoke(core,"fever",new JSONObject().put("music_style","dance"),false)).isEmpty(),"History ignored");
  check(((List<?>)available.invoke(core,"fever",new JSONObject().put("music_style","dance"),true)).size()==1,"Cycle cannot reuse correct genre");
  check(((List<?>)available.invoke(core,"fever",new JSONObject().put("music_style","jazz"),true)).isEmpty(),"Cycle relaxed genre");
  System.out.println("PASS music style parsing, strict recording filter, history/cycle, explicit soundtrack matching");
 }
}
