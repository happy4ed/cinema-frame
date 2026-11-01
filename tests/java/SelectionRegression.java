package com.happy.cinemaframe;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import sun.misc.Unsafe;

/** Host regression harness: invokes the real chooser without starting Android. */
public final class SelectionRegression {
    static void set(Object core,String name,Object value)throws Exception{Field f=FrameCore.class.getDeclaredField(name);f.setAccessible(true);f.set(core,value);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        FrameCore core=(FrameCore)((Unsafe)uf.get(null)).allocateInstance(FrameCore.class);
        Map<String,JSONObject> movies=new LinkedHashMap<>(),albums=new LinkedHashMap<>();
        Set<String> seen=new HashSet<>(),songKeys=new HashSet<>();
        set(core,"lock",new Object());set(core,"movies",movies);set(core,"albums",albums);
        set(core,"seen",seen);set(core,"songKeys",songKeys);set(core,"recentMovies",new ArrayDeque<String>());
        set(core,"albumArtists",new HashMap<>());set(core,"random",new Random(7));
        for(String id:new String[]{"A","B","C"}){
            movies.put(id,new JSONObject().put("id",id).put("year",1963).put("rating",8).put("votes",90000).put("genres",new JSONArray().put("Romance")));
            JSONArray tracks=new JSONArray();for(int n=0;n<30;n++)tracks.put(new JSONObject().put("uri",id+n).put("title",id+n).put("artist","Composer").put("duration",180000));
            albums.put(id,new JSONObject().put("tracks",tracks));
        }
        JSONObject profile=new JSONObject().put("era",1960).put("genres",new JSONArray().put("Romance"));
        Method choose=FrameCore.class.getDeclaredMethod("choose",JSONObject.class,String.class,Set.class);choose.setAccessible(true);
        // A is audible; B's poster has been selected but playback is not confirmed.
        Set<String> excluded=SelectionGuard.excluded("B","A","B");
        for(int i=0;i<100;i++)check(((JSONObject)choose.invoke(core,profile,"B",excluded)).getJSONObject("movie").getString("id").equals("C"),"Selected/audible film repeated while loading");
        // If no third movie exists, wait instead of repeating A or B.
        movies.remove("C");check(choose.invoke(core,profile,"B",excluded)==null,"Empty pool repeated a blocked film");
        movies.put("C",new JSONObject().put("id","C").put("year",1963).put("rating",8).put("votes",90000).put("genres",new JSONArray().put("Romance")));
        String previous="A";
        for(int i=0;i<40;i++){
            JSONObject item=(JSONObject)choose.invoke(core,profile,previous,SelectionGuard.excluded(previous,previous,previous));
            String film=item.getJSONObject("movie").getString("id"),uri=item.getJSONObject("track").getString("uri");
            check(!film.equals(previous),"Consecutive film");check(seen.add(uri),"Repeated song");previous=film;
        }
        check(Modifier.isSynchronized(FrameCore.class.getDeclaredMethod("next",long.class).getModifiers()),"Concurrent next calls are not serialized");
        check(SelectionGuard.excluded("B","A","").contains("B"),"Request reset lost selected-film exclusion");
        System.out.println("PASS: pending-playback exclusion, empty pool, 40 transitions, song history, serialized next, request reset");
    }
}
