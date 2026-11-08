package com.happy.cinemaframe;

import android.content.*;
import android.net.nsd.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Receiver-hosted controller, catalogue and playback coordinator: no PC server required. */
public final class FrameCore {
    private static FrameCore singleton;
    public static synchronized FrameCore get(Context c){if(singleton==null)singleton=new FrameCore(c);return singleton;}
    public final Context context;public final String key,code;public final SpotifyEngine engine;private final SpotifySearch search;
    private final Object lock=new Object(),mbLock=new Object();private final ExecutorService jobs=Executors.newFixedThreadPool(3),http=Executors.newFixedThreadPool(8);
    private final Map<String,JSONObject> movies=new LinkedHashMap<>(),albums=new ConcurrentHashMap<>();private final Set<String> failed=new HashSet<>(),seen=new HashSet<>(),songKeys=new HashSet<>(),sessions=new HashSet<>();
    private final Map<String,Long> styleSearchTimes=new HashMap<>();private final Map<String,JSONArray> playlistSearchCache=new ConcurrentHashMap<>();
    private final Map<String,Long> scannedPlaylists=new ConcurrentHashMap<>();private final Map<String,String> movieAliasCache=new HashMap<>();
    private final Map<String,List<JSONObject>> styleTracks=new HashMap<>();
    private final List<JSONObject> ostPlan=new ArrayList<>();private int ostCursor=0;private long ostEpoch=-1;private JSONObject ostAudit=new JSONObject(),countryMapping=new JSONObject();private boolean ostBlocked=false;
    private final Map<String,Integer> searchPages=new HashMap<>();private JSONObject spotifyDiscovery=new JSONObject();
    private final Map<String,Set<String>> albumArtists=new ConcurrentHashMap<>();
    private final Random random=new SecureRandom();private final SQLiteDatabase db;
    private JSONObject state=new JSONObject(),profile=new JSONObject(),current=null,report=new JSONObject(),transport=null;private JSONArray queue=new JSONArray();
    private String phase="idle",message="휴대폰 앱에서 프레임을 연결해 주세요",request="",lastMovie="",lastSelectedMovie="";private long version=0,epoch=0,lastMb=0,lastPosition=0;private boolean confirmed=false,discovery=false,ready=false;private int attempts=0;private long attemptsSince=0;
    private final ArrayDeque<String> recentMovies=new ArrayDeque<>();private long lastReport=0,lastDiscovery=0,discoveryEpoch=-1;
    private long resumePosition=0,lastPlaybackSave=0,lastEngineStart=0;private boolean resumePaused=false;private String savedPhase="";
    private final Map<String,Long> unavailableTracks=new HashMap<>();private long selectedAt=0;private boolean retryFailedSelection=false;
    private boolean waitingNext=false;private long retryNextAt=0;private int playbackCycle=1;
    private long pairingUntil=0;private String displayMode="height";
    private long playbackApplied=-1,retryPlaybackAt=0,displayVersion=-1,lastDisplay=0;private JSONObject displayMetrics=new JSONObject();private String displayError="";private boolean displayVisible=false;
    private boolean external=false;private volatile JSONObject auth=new JSONObject();private volatile boolean spotifyReady=false;
    private FrameCore(Context c){
        context=c.getApplicationContext();search=new SpotifySearch(context);SharedPreferences prefs=context.getSharedPreferences("setup",0);displayMode=prefs.getString("display_mode","height");
        key=prefs.getString("key",UUID.randomUUID().toString()+UUID.randomUUID());code=prefs.getString("code",String.valueOf(100000+random.nextInt(900000)));external=prefs.getBoolean("existing_test_engine",false);
        lastSelectedMovie=prefs.getString("last_selected_movie","");sessions.addAll(prefs.getStringSet("sessions",Collections.emptySet()));try{profile=new JSONObject(prefs.getString("profile","{}"));request=prefs.getString("request","");JSONObject saved=new JSONObject(prefs.getString("playback","{}"));if(saved.optBoolean("active")&&saved.optJSONObject("current")!=null){current=saved.getJSONObject("current");selectedAt=System.currentTimeMillis();lastSelectedMovie=current.getJSONObject("movie").optString("id");version=1;resumePosition=saved.optLong("position");lastPosition=resumePosition;resumePaused=saved.optBoolean("paused");phase="loading";message="이전 재생을 복구하고 있습니다";}}catch(Exception ignored){}
        try{OstPolicy.migrate(profile);prefs.edit().putString("profile",profile.toString()).apply();}catch(Exception e){message=e.getMessage();}
        prefs.edit().putString("key",key).putString("code",code).apply();
        db=context.openOrCreateDatabase("cinema.db",0,null);db.execSQL("CREATE TABLE IF NOT EXISTS played(uri TEXT PRIMARY KEY,song TEXT,movie TEXT,at INTEGER)");db.execSQL("CREATE TABLE IF NOT EXISTS albums(movie TEXT PRIMARY KEY,payload TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ost_cache(movie TEXT PRIMARY KEY,payload TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS style_tracks(key TEXT PRIMARY KEY,payload TEXT)");try(Cursor cur=db.rawQuery("SELECT payload FROM style_tracks",null)){while(cur.moveToNext())try{JSONObject t=new JSONObject(cur.getString(0));styleTracks.computeIfAbsent(t.getString("movie_id"),k->new ArrayList<>()).add(t);}catch(Exception ignored){}}
        db.execSQL("CREATE TABLE IF NOT EXISTS cycle_seen(uri TEXT PRIMARY KEY,song TEXT)");if(!prefs.getBoolean("cycle_initialized",false)){db.execSQL("INSERT OR IGNORE INTO cycle_seen SELECT uri,song FROM played");prefs.edit().putBoolean("cycle_initialized",true).apply();}playbackCycle=prefs.getInt("playback_cycle",1);
        try(Cursor cur=db.rawQuery("SELECT uri,song,movie FROM played ORDER BY at",null)){while(cur.moveToNext()){lastMovie=cur.getString(2);recentMovies.remove(lastMovie);recentMovies.addLast(lastMovie);if(recentMovies.size()>15)recentMovies.removeFirst();}}
        try(Cursor cur=db.rawQuery("SELECT uri,song FROM cycle_seen",null)){while(cur.moveToNext()){seen.add(cur.getString(0));songKeys.add(cur.getString(1));}}
        if(lastSelectedMovie.isEmpty())lastSelectedMovie=lastMovie;
        engine=new SpotifyEngine(context);engine.start();
        jobs.submit(()->{try{JSONObject all=new JSONObject(asset("movies.json"));JSONArray list=all.getJSONArray("movies");countryMapping=all.optJSONObject("country_mapping");if(countryMapping==null){countryMapping=new JSONObject().put("complete",false).put("source","legacy Wikidata India-only mapping; full refresh required");Set<String> known=new HashSet<>();JSONArray india=new JSONObject(asset("film-countries.json")).getJSONArray("india");for(int j=0;j<india.length();j++)known.add(india.getString(j));int mapped=0;for(int j=0;j<list.length();j++){JSONObject m=list.getJSONObject(j);if(!m.has("countries"))m.put("countries",known.contains(m.getString("id"))?new JSONArray().put("IN"):new JSONArray());if(m.getJSONArray("countries").length()>0)mapped++;}countryMapping.put("total",list.length()).put("mapped",mapped).put("unknown",list.length()-mapped);}synchronized(lock){for(int i=0;i<list.length();i++){JSONObject m=list.getJSONObject(i);movies.put(m.getString("id"),m);}ready=true;}}catch(Exception e){message="목록 초기화 오류: "+e.getMessage();}});
        new Thread(this::serve,"cinema-http").start();advertise();
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::playbackTick,0,1100,TimeUnit.MILLISECONDS);
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(()->{try{
            if(!external&&!engine.isRunning()&&System.currentTimeMillis()-lastEngineStart>10000){synchronized(lock){resumePosition=lastPosition;resumePaused=phase.equals("paused");playbackApplied=-1;retryPlaybackAt=0;}lastEngineStart=System.currentTimeMillis();engine.start();}
            JSONObject s=json(playerBase()+"/status",null);spotifyReady=s.length()>0;
            auth=external?new JSONObject():json("http://127.0.0.1:24881/auth/code",null);
            synchronized(lock){if(waitingNext&&ready&&spotifyReady&&System.currentTimeMillis()>=retryNextAt){retryNextAt=System.currentTimeMillis()+15000;long gen=epoch;jobs.submit(()->next(gen));}if(ready&&spotifyReady&&!request.isEmpty()&&!discovery&&!ostBlocked&&ostEpoch!=epoch&&System.currentTimeMillis()-lastDiscovery>60000){long gen=epoch;jobs.submit(()->discover(gen));lastDiscovery=System.currentTimeMillis();}}
        }catch(Exception ignored){spotifyReady=false;}},1,3,TimeUnit.SECONDS);
    }
    public void display(long version,JSONObject metrics,String error,boolean visible){synchronized(lock){displayVersion=version;displayMetrics=metrics;displayError=error;displayVisible=visible;lastDisplay=System.currentTimeMillis();}}
    public void displayVisibility(boolean visible){synchronized(lock){displayVisible=visible;lastDisplay=System.currentTimeMillis();if(!visible)resumePaused=true;}if(!visible)http.submit(()->{try{json(playerBase()+"/player/pause",new JSONObject());}catch(Exception ignored){}});}
    private void playbackTick(){JSONObject item,action;long v;boolean foreground; synchronized(lock){item=current;v=version;action=transport;foreground=displayVisible;}String failure="";JSONObject player=new JSONObject();
        try{if(!spotifyReady)return;player=json(playerBase()+"/status",null);
            if(item!=null&&v!=playbackApplied&&System.currentTimeMillis()>=retryPlaybackAt){JSONObject track=item.getJSONObject("track"),active=player.optJSONObject("track");String wanted=track.getString("uri");if(player.optBoolean("stopped",true)||active==null||(!wanted.equals(active.optString("uri"))&&!wanted.equals(player.optString("context_uri"))))json(playerBase()+"/player/play",new JSONObject().put("uri",wanted).put("position",resumePosition).put("paused",resumePaused||!foreground));resumePosition=0;resumePaused=false;playbackApplied=v;player=json(playerBase()+"/status",null);}
            if(action!=null){String name=action.optString("action");if(name.equals("pause")||(name.equals("resume")&&foreground)){json(playerBase()+"/player/"+name,new JSONObject());if(name.equals("resume"))resumePaused=false;}else if(name.equals("seek")&&action.optLong("version")==v){json(playerBase()+"/player/seek",new JSONObject().put("position",action.getLong("position")));synchronized(lock){lastPosition=action.getLong("position");}}synchronized(lock){if(transport!=null&&transport.optLong("id")==action.optLong("id"))transport=null;}player=json(playerBase()+"/status",null);}
            synchronized(lock){foreground=displayVisible;}if(!foreground&&!player.optBoolean("paused",true)&&!player.optBoolean("stopped",true)){json(playerBase()+"/player/pause",new JSONObject());player=json(playerBase()+"/status",null);}
        }catch(Exception e){retryPlaybackAt=System.currentTimeMillis()+5000;failure="재생 연결을 복구하고 있습니다: "+e.getMessage();}
        try{JSONObject metrics;synchronized(lock){metrics=displayVersion==v?displayMetrics:new JSONObject();}telemetry(new JSONObject().put("version",v).put("player",player).put("poster",metrics).put("error",failure).put("app","0.3-test"));}catch(Exception ignored){}
    }
    void showPairing(){synchronized(lock){pairingUntil=System.currentTimeMillis()+180000;}}
    void hidePairing(){synchronized(lock){pairingUntil=0;}}
    private boolean pairingVisible(){return sessions.isEmpty()||System.currentTimeMillis()<pairingUntil;}
    private JSONObject volume()throws Exception{return new JSONObject().put("value",engine.getVolume()).put("max",100).put("scope","app").put("leveling",engine.getLeveling());}
    public String playerBase(){return "http://127.0.0.1:"+(external?24879:24881);}
    public static String address(){try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&!a.isLoopbackAddress()&&a.isSiteLocalAddress())return a.getHostAddress();}catch(Exception ignored){}return "Wi-Fi 연결 필요";}
    private String asset(String name)throws Exception{return new String(read(context.getAssets().open(name),12000000),"UTF-8");}
    private void advertise(){try{NsdManager nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("Cinema Frame "+code.substring(3));info.setServiceType("_cinemaframe._tcp.");info.setPort(8787);nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,new NsdManager.RegistrationListener(){public void onServiceRegistered(NsdServiceInfo i){}public void onRegistrationFailed(NsdServiceInfo i,int e){}public void onServiceUnregistered(NsdServiceInfo i){}public void onUnregistrationFailed(NsdServiceInfo i,int e){}});}catch(Exception ignored){}}
    static byte[] read(InputStream in,int max)throws Exception{try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n,total=0;while((n=s.read(b))!=-1){total+=n;if(total>max)throw new IOException("응답이 너무 큽니다");out.write(b,0,n);}return out.toByteArray();}}
    static byte[] fetch(String url,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(20000);c.setUseCaches(false);c.setRequestProperty("User-Agent",url.contains("open.spotify.com")?"Mozilla/5.0":"CinemaFramePersonal/0.2 (Android home soundtrack receiver)");c.setRequestProperty("Cache-Control","no-store");
        try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);return read(c.getInputStream(),25000000);}finally{c.disconnect();}
    }
    static JSONObject json(String url,JSONObject body)throws Exception{byte[] b=fetch(url,body);String value=new String(b,"UTF-8").trim();return value.isEmpty()||value.equals("null")?new JSONObject():new JSONObject(value);}
    private JSONObject mb(String path)throws Exception{synchronized(mbLock){long wait=1100-(System.currentTimeMillis()-lastMb);if(wait>0)Thread.sleep(wait);try{return json("https://musicbrainz.org/ws/2/"+path,null);}finally{lastMb=System.currentTimeMillis();}}}
    private static String enc(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
    private static String norm(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]","");}
    private static String songKey(JSONObject t){return norm(t.optString("title").split(" [-–] ")[0])+"|"+norm(t.optString("artist"));}
    static String posterUrl(String original,int width){int marker=original.indexOf("._V1");if(original.startsWith("https://m.media-amazon.com/")&&marker>=0)return original.substring(0,marker)+"._V1_QL85_UX"+width+"_.jpg";return original;}
    private JSONObject poster(String mid)throws Exception{
        JSONArray a=json("https://v3.sg.media-imdb.com/suggestion/x/"+mid+".json",null).optJSONArray("d");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject d=a.getJSONObject(i);if(mid.equals(d.optString("id"))&&d.has("i")){JSONObject p=d.getJSONObject("i");return new JSONObject().put("url",p.getString("imageUrl")).put("width",p.optInt("width")).put("height",p.optInt("height")).put("source","IMDb");}}
        throw new IOException("영화 포스터를 찾지 못했습니다");
    }
    private JSONObject album(String aid)throws Exception{
        String html=new String(fetch("https://open.spotify.com/embed/album/"+aid,null),"UTF-8");Matcher match=Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",Pattern.DOTALL).matcher(html);if(!match.find())throw new IOException("앨범 정보 없음");
        JSONObject e=new JSONObject(match.group(1)).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity");
        JSONArray all=e.optJSONArray("trackList");List<JSONObject> songs=new ArrayList<>();JSONObject main=null;
        if(all!=null)for(int i=0;i<all.length();i++){JSONObject t=all.getJSONObject(i);String title=t.optString("title");if(t.optLong("duration")<60000||title.toLowerCase().contains("dialogue")||!t.optString("uri").startsWith("spotify:track:"))continue;
            boolean theme=Pattern.compile("main title|main theme|overture|theme",Pattern.CASE_INSENSITIVE).matcher(title).find();JSONObject s=new JSONObject().put("uri",t.getString("uri")).put("title",title).put("artist",t.optString("subtitle")).put("duration",t.optLong("duration")).put("instrumental",theme||e.optString("name").toLowerCase().contains("score"));songs.add(s);if(main==null&&theme)main=s;
        }
        if(main!=null){songs.remove(main);songs.add(0,main);}JSONArray chosen=new JSONArray();for(int i=0;i<Math.min(4,songs.size());i++)chosen.put(songs.get(i));if(chosen.length()==0)throw new IOException("곡 없음");
        return new JSONObject().put("album",aid).put("album_title",e.optString("name")).put("tracks",chosen).put("main_verified",main!=null).put("selection","자동 OST 후보 · 인기순 아님").put("source","https://open.spotify.com/album/"+aid);
    }
    private JSONObject officialAlbum(JSONObject movie){
        if(!search.connected())return null;
        try{JSONArray hits=search.albums(movie.optString("originalTitle",movie.optString("title")));for(int i=0;i<hits.length();i++){JSONObject hit=hits.getJSONObject(i);String name=hit.optString("name");if(!SpotifySearch.matches(movie.optString("title"),name)&&!SpotifySearch.matches(movie.optString("originalTitle"),name))continue;JSONObject result=album(hit.getString("id"));result.put("relation_source","Spotify Web API search");return result;}}catch(Exception ignored){}return null;
    }
    private JSONObject resolve(JSONObject movie){String mid=movie.optString("id");synchronized(lock){if(albums.containsKey(mid))return albums.get(mid);if(failed.contains(mid))return null;}
        JSONObject searched=officialAlbum(movie);if(searched!=null){synchronized(lock){albums.put(mid,searched);db.execSQL("INSERT OR REPLACE INTO albums VALUES(?,?)",new Object[]{mid,searched.toString()});}return searched;}
        try{JSONArray rels=mb("url/?resource="+enc("https://www.imdb.com/title/"+mid+"/")+"&inc=release-group-rels&fmt=json").optJSONArray("relations");if(rels!=null)for(int i=0;i<rels.length();i++){JSONObject rg=rels.getJSONObject(i).optJSONObject("release_group");if(rg==null)continue;
            JSONArray releases=mb("release?release-group="+rg.getString("id")+"&inc=url-rels&fmt=json&limit=100").optJSONArray("releases");if(releases==null)continue;
            for(int j=0;j<releases.length();j++){JSONArray urls=releases.getJSONObject(j).optJSONArray("relations");if(urls==null)continue;for(int k=0;k<urls.length();k++){JSONObject u=urls.getJSONObject(k).optJSONObject("url");if(u==null)continue;Matcher m=Pattern.compile("open\\.spotify\\.com/(?:intl-[^/]+/)?album/([A-Za-z0-9]+)").matcher(u.optString("resource"));if(!m.find())continue;
                JSONObject result=album(m.group(1));result.put("relation_source","https://musicbrainz.org/release-group/"+rg.getString("id"));synchronized(lock){albums.put(mid,result);db.execSQL("INSERT OR REPLACE INTO albums VALUES(?,?)",new Object[]{mid,result.toString()});}return result;
            }}
        }}catch(Exception ignored){}synchronized(lock){failed.add(mid);}return null;
    }
    private String titleLookup(String text)throws Exception{
        String query=text.trim().replaceFirst("^(오늘은?|지금은?|나는?|혹시)\\s*","");query=query.split("같은|처럼|같이|느낌",2)[0];query=query.replaceAll("(?i)\\s*(ost|사운드트랙|영화음악|음악|틀어줘|듣고싶어|듣고 싶어|들려줘).*?$","").trim();
        String normalized=norm(query);if(normalized.length()<2||normalized.length()>50)return null;
        if(normalized.matches(".*(연주|보컬|노래|신나|경쾌|밝은|차분|잔잔|조용|재즈|액션|모험|애니|코미디|로맨스|공포|스릴러|판타지|뮤지컬|공상과학|웅장|비오는|비내리는|전체|랜덤|다른장르|년대|댄스|춤|초기화).*" )||normalized.equals("sf"))return null;
        SharedPreferences cache=context.getSharedPreferences("title_aliases",0);String cached=cache.getString(normalized,null);if(cached!=null&&movies.containsKey(cached))return cached;
        JSONArray results=json("https://v3.sg.media-imdb.com/suggestion/x/"+enc(query).replace("+","%20")+".json",null).optJSONArray("d");
        if(results!=null)for(int i=0;i<results.length();i++){JSONObject hit=results.getJSONObject(i);String id=hit.optString("id");if(movies.containsKey(id)){cache.edit().putString(normalized,id).apply();return id;}}
        return null;
    }
    private JSONObject interpret(String text)throws Exception{
        if(text.contains("빼고")||text.contains("제외")||text.contains("포함")){JSONObject p=legacyInterpret(text);JSONArray codes=OstPolicy.countries(profile);if(text.contains("인도")){JSONArray updated=new JSONArray();for(int i=0;i<codes.length();i++)if(!codes.getString(i).equals("IN"))updated.put(codes.getString(i));if(!text.contains("포함"))updated.put("IN");codes=updated;}if(text.contains("제외")&&text.contains("초기화"))codes=new JSONArray();p.put("excluded_countries",codes).put("pipeline","deezer-isrc-v1");p.remove("exclude_india");return p;}
        return OstPolicy.interpret(text,profile);
    }
    private JSONObject legacyInterpret(String text)throws Exception{
        JSONObject p;synchronized(lock){p=new JSONObject(profile.toString());}JSONArray exclusions=p.optJSONArray("excluded_movies");if(exclusions==null)exclusions=new JSONArray();JSONObject excludedTitles=p.optJSONObject("excluded_titles");if(excludedTitles==null)excludedTitles=new JSONObject();boolean cleared=norm(text).contains("제외")&&norm(text).contains("초기화");if(cleared){exclusions=new JSONArray();excludedTitles=new JSONObject();}
        String musicMood=p.optString("music_mood","");if(norm(text).matches(".*(행복|기쁜|즐거운|희망|happy|uplifting).*"))musicMood="happy";else if(norm(text).matches(".*(슬픈|우울|슬프|sad).*"))musicMood="sad";String musicStyle=p.optString("music_style","");String requestedStyle=MusicStyle.requested(text);
        boolean excludeIndia=p.optBoolean("exclude_india",false);boolean countryChanged=false;
        Matcher country=Pattern.compile("인도\\s*(?:영화)?\\s*(빼고|말고|제외하고|제외|포함)").matcher(text);
        if(country.find()){excludeIndia=!country.group(1).equals("포함");text=country.replaceFirst("").trim();countryChanged=true;}
        if(cleared)excludeIndia=false;
        Matcher negative=Pattern.compile("(.+?)(?:빼고|말고|제외하고|제외)").matcher(text);boolean excluded=countryChanged;
        if(!cleared&&negative.find()){String id=titleLookup(negative.group(1));if(id==null)throw new IOException("제외할 영화 제목을 확인하지 못했습니다. 영화 제목과 '빼고'를 입력해 주세요.");boolean contains=false;for(int i=0;i<exclusions.length();i++)if(id.equals(exclusions.optString(i)))contains=true;if(!contains)exclusions.put(id);excludedTitles.put(id,movies.get(id).optString("title"));text=text.substring(negative.end()).trim();excluded=true;}
        String n=norm(text);String seed=null;
        String[][] aliases={{"라라랜드","tt3783958"},{"lalaland","tt3783958"},{"비긴어게인","tt1980929"},{"beginagain","tt1980929"},{"원스","tt0907657"},{"once","tt0907657"},{"싱스트리트","tt3544112"},{"인터스텔라","tt0816692"}};
        for(String[] a:aliases)if(n.contains(a[0])){seed=a[1];break;}
        if(seed==null){int best=0;for(JSONObject m:movies.values()){String title=norm(m.optString("title"));if(title.length()>best&&title.length()>=4&&n.contains(title)){best=title.length();seed=m.optString("id");}}}
        if(seed==null&&requestedStyle.isEmpty())seed=titleLookup(text);
        if(seed==null&&requestedStyle.isEmpty()&&!excluded&&!cleared&&!n.matches(".*(연주|보컬|노래|신나|경쾌|밝은|차분|잔잔|조용|재즈|액션|모험|애니|코미디|로맨스|공포|스릴러|sf|판타지|뮤지컬|공상과학|웅장|비오는|비내리는|전체|랜덤|다른장르|년대|댄스|춤|초기화).*"))throw new IOException("영화 제목이나 분위기를 확인하지 못했습니다. 영화 제목만 입력하거나 장르 버튼을 골라 주세요.");
        if(seed==null&&n.matches(".*(신나는|경쾌한|밝은|차분한|잔잔한|조용한).*영화음악.*")){p.remove("seed");p.remove("seed_title");p.put("genres",new JSONArray()).put("style","all");}
        if(seed!=null&&movies.containsKey(seed)){JSONObject m=movies.get(seed);p=new JSONObject().put("seed",seed).put("seed_title",m.optString("title")).put("genres",m.getJSONArray("genres")).put("style",seed.equals("tt3783958")?"musical":"similar").put("instrumental",false).put("energy","balanced");}
        if(p.length()==0)p=new JSONObject().put("genres",new JSONArray()).put("style","all").put("energy","balanced");
        if(n.contains("연주만")||n.contains("보컬없이")||n.contains("노래없이"))p.put("instrumental",true);
        if(n.contains("보컬도")||n.contains("노래있는"))p.put("instrumental",false);
        if(n.contains("신나")||n.contains("경쾌")||n.contains("밝은"))p.put("energy","bright");
        if(n.contains("차분")||n.contains("잔잔")||n.contains("조용"))p.put("energy","calm");
        if(n.contains("댄스")||n.contains("춤")){p.put("style","dance").put("genres",new JSONArray("[\"Music\",\"Musical\"]")).put("energy","bright");p.remove("seed");p.remove("seed_title");}
        if(n.contains("재즈")){p.put("style","jazz").put("genres",new JSONArray("[\"Music\",\"Romance\"]"));p.remove("seed");p.remove("seed_title");}
        String genre=null;
        if(n.contains("다른장르")){String[] choices={"Action","Animation","Comedy","Romance","Sci-Fi","Music"};String old=p.optJSONArray("genres")==null?"":p.optJSONArray("genres").toString();List<String> options=new ArrayList<>();for(String g:choices)if(!old.contains(g))options.add(g);genre=options.get(random.nextInt(options.size()));}
        String[][] genres={{"액션","Action"},{"모험","Adventure"},{"애니","Animation"},{"코미디","Comedy"},{"로맨스","Romance"},{"공포","Horror"},{"스릴러","Thriller"},{"sf","Sci-Fi"},{"공상과학","Sci-Fi"},{"판타지","Fantasy"},{"뮤지컬","Musical"}};
        for(String[] g:genres)if(n.contains(g[0]))genre=g[1];
        if(n.contains("웅장")){genre="Adventure";p.put("energy","epic");}
        if(n.contains("비오는")||n.contains("비내리는")){p.put("energy","calm");if(genre==null&&seed==null)genre="Romance";}
        if(genre!=null){p.put("genres",new JSONArray().put(genre)).put("style",genre.equals("Musical")?"musical":"genre");p.remove("seed");p.remove("seed_title");}
        if(n.contains("전체장르")||n.contains("아무거나")||n.contains("전체랜덤")){p=new JSONObject().put("genres",new JSONArray()).put("style","all").put("energy","balanced");}
        Matcher decade=Pattern.compile("(19[0-9]0|20[0-9]0)년").matcher(text);if(decade.find())p.put("era",Integer.parseInt(decade.group(1)));else if(n.contains("90년대"))p.put("era",1990);else if(n.contains("전체시대"))p.remove("era");
        if(!requestedStyle.isEmpty()){musicStyle=requestedStyle;p.remove("seed");p.remove("seed_title");p.put("genres",new JSONArray()).put("style","music");}
        else if(seed!=null||genre!=null||n.contains("전체음악")||n.contains("전체장르")||n.contains("전체랜덤"))musicStyle="";
        p.put("music_mood",musicMood).put("music_style",musicStyle).put("excluded_movies",exclusions).put("excluded_titles",excludedTitles).put("exclude_india",excludeIndia);return p;
    }
    private boolean eligible(JSONObject m,JSONObject p){if(OstPolicy.countryBlocked(m,p))return false;if("deezer-isrc-v1".equals(p.optString("pipeline")))return OstPolicy.eligible(m,p);JSONArray exclusions=p.optJSONArray("excluded_movies");if(exclusions!=null)for(int i=0;i<exclusions.length();i++)if(m.optString("id").equals(exclusions.optString(i)))return false;int era=p.optInt("era",0),year=m.optInt("year");JSONArray wanted=p.optJSONArray("genres");boolean genreMatch=wanted==null||wanted.length()==0;String actual=m.optJSONArray("genres").toString();if(wanted!=null)for(int i=0;i<wanted.length();i++)if(actual.contains("\""+wanted.optString(i)+"\""))genreMatch=true;return genreMatch&&((m.optDouble("rating")>=7&&m.optInt("votes")>=10000)||(!p.optString("music_style").isEmpty()&&m.optDouble("rating")>=6&&m.optInt("votes")>=50000))&&(era==0||(year>=era&&year<era+10));}
    private Set<String> artists(String id){Set<String> cached=albumArtists.get(id);if(cached!=null)return cached;JSONObject album=albums.get(id);if(album==null)return Collections.emptySet();Set<String> names=new HashSet<>();JSONArray tracks=album.optJSONArray("tracks");if(tracks!=null)for(int i=0;i<tracks.length();i++)for(String name:tracks.optJSONObject(i).optString("artist").split("[,;]")){String normalized=norm(name);if(normalized.length()>3&&!normalized.equals("variousartists")&&!normalized.equals("originalcast"))names.add(normalized);}albumArtists.put(id,names);return names;}
    private boolean sharedArtist(String id,JSONObject p){String reference=p.optString("seed");if(reference.isEmpty()||reference.equals(id))return false;Set<String> source=artists(reference);for(String artist:artists(id))if(source.contains(artist))return true;return false;}
    private double score(JSONObject m,JSONObject p){String genres=m.optJSONArray("genres").toString();double score=1;JSONArray target=p.optJSONArray("genres");if(target!=null)for(int i=0;i<target.length();i++)if(genres.contains("\""+target.optString(i)+"\""))score+=2;
        if(p.optString("style").equals("musical")||p.optString("style").equals("jazz")){if(genres.contains("Music"))score+=8;if(genres.contains("Romance"))score+=3;if(genres.contains("War")||genres.contains("Horror"))score-=8;}
        if(sharedArtist(m.optString("id"),p))score+=4;return Math.max(.1,score);
    }
    private List<JSONObject> available(String id,JSONObject p){return available(id,p,false);}
    private List<JSONObject> available(String id,JSONObject p,boolean repeat){List<JSONObject> out=new ArrayList<>();String musicStyle=p.optString("music_style");if(!musicStyle.isEmpty()){for(JSONObject t:styleTracks.getOrDefault(id,Collections.emptyList()))if(MusicStyle.accepts(musicStyle,t)&&MusicStyle.moodMatches(p,t)&&unavailableTracks.getOrDefault(t.optString("uri"),0L)<System.currentTimeMillis()&&(repeat||(!seen.contains(t.optString("uri"))&&!songKeys.contains(songKey(t))))&&(!p.optBoolean("instrumental")||t.optBoolean("instrumental")))out.add(t);return out;}JSONObject a=albums.get(id);if(a==null)return out;JSONArray ts=a.optJSONArray("tracks");if(ts!=null)for(int i=0;i<ts.length();i++){JSONObject t=ts.optJSONObject(i);if(t!=null&&unavailableTracks.getOrDefault(t.optString("uri"),0L)<System.currentTimeMillis()&&(repeat||(!seen.contains(t.optString("uri"))&&!songKeys.contains(songKey(t))))&&(!p.optBoolean("instrumental")||t.optBoolean("instrumental")))out.add(t);}return out;}
    private double trackWeight(JSONObject t,String energy){String label=t.optString("mood","unknown"),name=t.optString("title").toLowerCase(Locale.ROOT);
        if(label.equals("unknown")){if(name.matches(".*(lullaby|nocturne|reverie|love theme|quiet|gentle).*"))label="calm";else if(name.matches(".*(dance|dancing|celebration|party|jive|rock and roll).*"))label="bright";else if(name.matches(".*(battle|march|warriors|victory).*"))label="epic";}
        if(energy.equals("balanced")||label.equals("unknown"))return 1;return label.equals(energy)?5:.2;
    }
    private JSONObject choose(JSONObject p,String last,Set<String> exclude)throws Exception{return choose(p,last,exclude,false);}
    private JSONObject choose(JSONObject p,String last,Set<String> exclude,boolean repeat)throws Exception{
        synchronized(lock){List<JSONObject> candidates=new ArrayList<>();double best=0;for(String id:(p.optString("music_style").isEmpty()?albums.keySet():styleTracks.keySet())){JSONObject m=movies.get(id);if(m!=null&&!id.equals(last)&&!exclude.contains(id)&&eligible(m,p)&&!available(id,p,repeat).isEmpty()){candidates.add(m);best=Math.max(best,score(m,p));}}
            if(candidates.isEmpty())return null;List<JSONObject> fresh=new ArrayList<>();for(JSONObject m:candidates)if(!recentMovies.contains(m.optString("id")))fresh.add(m);if(!fresh.isEmpty())candidates=fresh;double total=0;for(JSONObject m:candidates)total+=Math.sqrt(score(m,p))*(recentMovies.contains(m.optString("id"))?.2:1);double pick=random.nextDouble()*total;JSONObject movie=candidates.get(0);for(JSONObject m:candidates){pick-=Math.sqrt(score(m,p))*(recentMovies.contains(m.optString("id"))?.2:1);if(pick<=0){movie=m;break;}}
            List<JSONObject> ts=available(movie.optString("id"),p,repeat);String energy=p.optString("energy");if(energy.equals("calm")){List<JSONObject> calm=new ArrayList<>();for(JSONObject t:ts)if(t.optBoolean("instrumental"))calm.add(t);if(!calm.isEmpty())ts=calm;}
            double totalTrack=0;for(JSONObject song:ts)totalTrack+=trackWeight(song,energy);double draw=random.nextDouble()*totalTrack;JSONObject t=ts.get(0);for(JSONObject song:ts){draw-=trackWeight(song,energy);if(draw<=0){t=song;break;}}return new JSONObject().put("movie",movie).put("track",t).put("reason",!p.optString("music_style").isEmpty()?"Spotify "+MusicStyle.label(p.optString("music_style"))+" 검색 · 영화 OST":sharedArtist(movie.optString("id"),p)?"기준 영화와 같은 음악가가 참여한 OST":p.optString("style").equals("musical")?"음악 중심 영화 · 로맨틱한 OST 계열":"현재 분위기와 가까운 영화 OST").put("selection",p.optString("music_style").isEmpty()?albums.get(movie.optString("id")).optString("selection"):"Spotify 곡 장르 검색").put("album_source",t.optString("album_source",albums.containsKey(movie.optString("id"))?albums.get(movie.optString("id")).optString("relation_source","catalogue"):"Spotify genre search")).put("album_url",t.optString("album_url",albums.containsKey(movie.optString("id"))?albums.get(movie.optString("id")).optString("source"):""));}
    }
    private synchronized void next(long generation){nextOst(generation);}
    private void searchProgress(long generation,String stage,String query,int found,int matched,int added,JSONArray examples){
        try{synchronized(lock){if(generation==epoch)spotifyDiscovery=new JSONObject().put("stage",stage).put("query",query).put("results",found).put("matched_movies",matched).put("added_movies",added).put("examples",examples).put("at",System.currentTimeMillis());}}catch(Exception ignored){}
    }
    private void discoverFromSpotify(JSONObject p,long generation){
        if(!search.connected())return;
        int found=0,matched=0,added=0,attempted=0;String lastQuery="";JSONArray examples=new JSONArray();long deadline=System.currentTimeMillis()+45000;
        List<JSONObject> catalogue;synchronized(lock){catalogue=new ArrayList<>(movies.values());}Map<String,List<JSONObject>> titleIndex=SoundtrackMatcher.index(catalogue);
        searchProgress(generation,"searching","",0,0,0,examples);
        try{outer:for(String query:SoundtrackMatcher.queries(p)){
            int offset;synchronized(lock){if(generation!=epoch)return;offset=searchPages.getOrDefault(query,0);}lastQuery=query;
            searchProgress(generation,"query",query,found,matched,added,examples);
            JSONArray hits=search.searchAlbums(query,offset);found+=hits.length();
            synchronized(lock){searchPages.put(query,hits.length()<10?0:(offset+10)%200);}
            searchProgress(generation,"matching",query,found,matched,added,examples);
            for(int i=0;i<hits.length();i++){
                synchronized(lock){if(generation!=epoch)return;}
                JSONObject hit=hits.getJSONObject(i),movie=SoundtrackMatcher.match(titleIndex,hit);if(movie==null||!eligible(movie,p))continue;matched++;String id=movie.getString("id");
                synchronized(lock){if(albums.containsKey(id))continue;}
                if(attempted>=3||System.currentTimeMillis()>deadline)break outer;attempted++;
                searchProgress(generation,"album",query,found,matched,added,examples);
                try{JSONObject result=album(hit.getString("id"));result.put("relation_source","Spotify Web API reverse search").put("search_query",query);
                    synchronized(lock){if(generation!=epoch)return;albums.put(id,result);failed.remove(id);db.execSQL("INSERT OR REPLACE INTO albums VALUES(?,?)",new Object[]{id,result.toString()});}added++;examples.put(movie.optString("title"));
                }catch(Exception ignored){}
            }
            if(System.currentTimeMillis()>deadline)break;
        }}catch(Exception ignored){}finally{searchProgress(generation,"complete",lastQuery,found,matched,added,examples);}
    }
    private boolean cacheStyleTrack(JSONObject p,JSONObject hit,JSONObject movie,String query,long generation)throws Exception{
        String style=p.getString("music_style");JSONObject album=hit.getJSONObject("album");
                String id=movie.getString("id");StringBuilder artists=new StringBuilder();JSONArray credits=hit.optJSONArray("artists");if(credits!=null)for(int j=0;j<credits.length();j++){if(j>0)artists.append(", ");artists.append(credits.getJSONObject(j).optString("name"));}
                JSONObject track=new JSONObject().put("uri",hit.getString("uri")).put("title",hit.getString("name")).put("artist",artists.toString()).put("duration",hit.getLong("duration_ms")).put("movie_id",id).put("music_styles",new JSONArray().put(style)).put("music_mood",p.optString("music_mood")).put("style_evidence",query).put("album_title",album.optString("name")).put("album_source",query.startsWith("Spotify playlist:")?"Spotify playlist discovery":query.startsWith("genre:")?"Spotify genre search":"Spotify mood search").put("album_url",album.optString("id").isEmpty()?hit.optString("playlist_url"):"https://open.spotify.com/album/"+album.getString("id"));
                if(track.getLong("duration")<60000)return false;
                synchronized(lock){if(generation!=epoch)return false;List<JSONObject> tracks=styleTracks.computeIfAbsent(id,k->new ArrayList<>());boolean exists=false;for(JSONObject old:tracks)if(old.optString("uri").equals(track.getString("uri"))&&MusicStyle.accepts(style,old)&&old.optString("music_mood").equals(track.optString("music_mood")))exists=true;if(exists)return false;tracks.add(track);db.execSQL("INSERT OR REPLACE INTO style_tracks VALUES(?,?)",new Object[]{style+"|"+p.optString("music_mood")+"|"+id+"|"+track.getString("uri"),track.toString()});}return true;

    }
    private JSONObject playlistEntity(String id)throws Exception{
        String html=new String(fetch("https://open.spotify.com/embed/playlist/"+id,null),"UTF-8");Matcher m=Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",Pattern.DOTALL).matcher(html);if(!m.find())throw new IOException("Playlist data unavailable");
        return new JSONObject(m.group(1)).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity");
    }
    private JSONObject lookupMovieAlias(JSONObject album)throws Exception{
        String title=SoundtrackMatcher.title(album.optString("name"));if(title.isEmpty()||title.length()>60)return null;
        String cached;synchronized(lock){cached=movieAliasCache.get(title);}if(cached!=null)return movies.get(cached);
        JSONArray hits=json("https://v3.sg.media-imdb.com/suggestion/x/"+enc(title)+".json",null).optJSONArray("d");JSONObject selected=null;
        if(hits!=null)for(int i=0;i<hits.length();i++){JSONObject movie=movies.get(hits.getJSONObject(i).optString("id"));if(movie!=null){if(selected!=null&&!selected.optString("id").equals(movie.optString("id"))){selected=null;break;}selected=movie;}}
        synchronized(lock){movieAliasCache.put(title,selected==null?"":selected.optString("id"));}return selected;
    }
    private void discoverStylePlaylists(JSONObject p,long generation,Map<String,List<JSONObject>> index)throws Exception{
        String style=p.getString("music_style");String[] queries=style.equals("ballad")?new String[]{"movie ballads","영화 발라드","romantic movie ballads"}:new String[]{style+" movie soundtrack"};
        int found=0,matched=0,added=0,processed=0,lookups=0;JSONArray examples=new JSONArray();String lastQuery="";
        try{for(String query:queries){
            int offset;synchronized(lock){if(generation!=epoch)return;offset=searchPages.getOrDefault("playlist:"+query,0);}lastQuery="playlist:"+query;searchProgress(generation,"playlist_search",lastQuery,found,matched,added,examples);
            JSONArray lists=playlistSearchCache.get(query);boolean unseen=false;if(lists!=null)for(int k=0;k<lists.length();k++){JSONObject cached=lists.optJSONObject(k);if(cached!=null&&System.currentTimeMillis()-scannedPlaylists.getOrDefault(style+"|"+cached.optString("id"),0L)>=21600000){unseen=true;break;}}if(lists==null||!unseen){lists=search.searchPlaylists(query,offset);playlistSearchCache.put(query,lists);synchronized(lock){if(generation!=epoch)return;searchPages.put("playlist:"+query,lists.length()<10?0:(offset+10)%100);}}found+=lists.length();
            int inQuery=0;for(int i=0;i<lists.length()&&processed<3&&inQuery<1;i++){
                JSONObject list=lists.optJSONObject(i);if(list==null)continue;String id=list.optString("id");if(!id.matches("[a-zA-Z0-9]+"))continue;
                synchronized(lock){if(generation!=epoch)return;if(System.currentTimeMillis()-scannedPlaylists.getOrDefault(style+"|"+id,0L)<21600000)continue;}processed++;inQuery++;
                JSONObject entity;try{entity=playlistEntity(id);}catch(Exception e){continue;}
                JSONArray songs=entity.optJSONArray("trackList");if(songs==null)continue;int playlistLookups=0;String evidence="Spotify playlist: "+entity.optString("name")+" (search: "+query+")";
                for(int j=0;j<songs.length();j++){
                    synchronized(lock){if(generation!=epoch)return;}
                    JSONObject song=songs.optJSONObject(j);if(song==null||!song.optBoolean("isPlayable",true)||MusicStyle.unsafeVersion(song.optString("title")))continue;
                    JSONObject hit=new JSONObject().put("uri",song.optString("uri")).put("name",song.optString("title")).put("duration_ms",song.optLong("duration")).put("artists",new JSONArray().put(new JSONObject().put("name",song.optString("subtitle")))).put("album",new JSONObject().put("name","").put("id","").put("release_date",""));
                    JSONObject movie=SoundtrackMatcher.matchTrack(index,hit);
                    if(movie==null&&playlistLookups<4){playlistLookups++;lookups++;try{JSONArray results=search.searchTracks("track:\""+MusicStyle.cleanTitle(song.optString("title")).replace("\"","")+"\" artist:\""+song.optString("subtitle").split(",")[0].replace("\"","")+"\"",0);for(int k=0;k<results.length();k++){JSONObject candidate=results.optJSONObject(k);if(candidate==null||!candidate.optString("uri").equals(hit.optString("uri")))continue;hit=candidate;movie=SoundtrackMatcher.matchTrack(index,hit);if(movie==null)movie=lookupMovieAlias(hit.getJSONObject("album"));break;}}catch(Exception ignored){}}
                    if(movie==null||!eligible(movie,p))continue;matched++;
                    hit.put("playlist_url","https://open.spotify.com/playlist/"+id);if(cacheStyleTrack(p,hit,movie,evidence,generation)){added++;examples.put(movie.optString("title")+" / "+hit.optString("name"));boolean needs;synchronized(lock){needs=current==null||waitingNext;}if(needs)next(generation);}
                }
                synchronized(lock){if(generation!=epoch)return;scannedPlaylists.put(style+"|"+id,System.currentTimeMillis());}
            }
        }}finally{searchProgress(generation,"complete",lastQuery,found,matched,added,examples);}
    }
    private void discoverStyle(JSONObject p,long generation)throws Exception{
        String style=p.getString("music_style");List<JSONObject> catalogue;synchronized(lock){catalogue=new ArrayList<>(movies.values());}
        Map<String,List<JSONObject>> index=SoundtrackMatcher.index(catalogue);try{discoverStylePlaylists(p,generation,index);}catch(Exception ignored){}boolean needsStart;synchronized(lock){needsStart=current==null||waitingNext;}if(needsStart)next(generation);int found=0,matched=0,added=0;JSONArray examples=new JSONArray();String lastQuery="";
        try{for(String query:MusicStyle.queries(p)){
            int offset;synchronized(lock){if(generation!=epoch)return;offset=searchPages.getOrDefault(query,0);}lastQuery=query;searchProgress(generation,"genre_search",query,found,matched,added,examples);
            JSONArray hits=search.searchTracks(query,offset);found+=hits.length();synchronized(lock){if(generation!=epoch)return;}
            for(int i=0;i<hits.length();i++){
                JSONObject hit=hits.optJSONObject(i);if(hit==null||hit.optBoolean("is_local")||hit.has("is_playable")&&!hit.optBoolean("is_playable")||MusicStyle.unsafeVersion(hit.optString("name")))continue;
                JSONObject album=hit.optJSONObject("album");if(album==null)continue;JSONObject movie=SoundtrackMatcher.matchTrack(index,hit);if(movie==null||!eligible(movie,p))continue;matched++;
                if(!cacheStyleTrack(p,hit,movie,query,generation))continue;added++;examples.put(movie.optString("title")+" / "+hit.optString("name"));
                boolean needs;synchronized(lock){needs=current==null||waitingNext;}if(needs)next(generation);
            }
            synchronized(lock){if(generation!=epoch)return;searchPages.put(query,hits.length()<10?0:(offset+10)%200);}
        }}finally{searchProgress(generation,"complete",lastQuery,found,matched,added,examples);}
    }
    private void discover(long generation){discoverOst(generation);}
    private void discoverOst(long generation){
        JSONObject p;List<JSONObject> selected;
        synchronized(lock){if(generation!=epoch||ostBlocked||ostEpoch==generation||discovery)return;discovery=true;discoveryEpoch=generation;try{p=new JSONObject(profile.toString());}catch(Exception e){discovery=false;return;}selected=OstPolicy.candidates(movies.values(),p);}
        try{
            JSONArray results=new JSONArray();JSONObject audit=new JSONObject().put("profile",p).put("ordering","IMDb rating desc, votes desc, id asc; Deezer rank desc, track id asc; movie round-robin").put("films",results).put("started_at",System.currentTimeMillis());
            synchronized(lock){if(generation!=epoch)return;ostAudit=audit;phase=current==null?"searching":phase;message="Deezer 순위로 영화별 OST 3곡을 확인합니다";}
            List<JSONObject> resolved=new ArrayList<>();DeezerOst provider=new DeezerOst(q->search.searchTracks(q,0));
            for(JSONObject movie:selected){
                synchronized(lock){if(generation!=epoch)return;}
                JSONObject result=null;try(Cursor cursor=db.rawQuery("SELECT payload FROM ost_cache WHERE movie=?",new String[]{movie.getString("id")})){if(cursor.moveToFirst()){JSONObject cached=new JSONObject(cursor.getString(0));if(OstPolicy.fresh(cached,System.currentTimeMillis()))result=cached;}}
                if(result==null){result=provider.resolve(movie);db.execSQL("INSERT OR REPLACE INTO ost_cache VALUES(?,?)",new Object[]{movie.getString("id"),result.toString()});}
                JSONObject entry=new JSONObject().put("movie",movie).put("result",result);resolved.add(entry);
                synchronized(lock){if(generation!=epoch)return;results.put(entry);message="Deezer 순위 확인 "+resolved.size()+" / "+selected.size()+"편";}
            }
            synchronized(lock){if(generation!=epoch)return;ostPlan.clear();Set<String> uris=new HashSet<>();for(int round=0;round<3;round++)for(JSONObject entry:resolved){JSONArray ts=entry.getJSONObject("result").getJSONArray("tracks");if(round>=ts.length())continue;JSONObject t=ts.getJSONObject(round);if(!uris.add(t.getString("uri")))continue;ostPlan.add(new JSONObject().put("movie",entry.getJSONObject("movie")).put("track",t).put("reason","Deezer 순위 · ISRC 일치").put("selection","Deezer 순위").put("album_url",entry.getJSONObject("result").optString("album_url")).put("album_source","Deezer 순위"));}ostCursor=0;ostEpoch=generation;audit.put("completed_at",System.currentTimeMillis()).put("playlist_tracks",ostPlan.size());
                if(ostPlan.isEmpty()){waitingNext=false;phase="no_matches";message=selected.isEmpty()?"조건과 국가 제외를 적용한 영화 후보가 0편입니다. 제외 국가나 조건을 변경해 주세요.":"재생 가능한 ISRC 일치 OST가 없습니다. 검색 기록에서 영화별 사유를 확인해 주세요.";return;}}
            nextOst(generation);
        }catch(Exception e){synchronized(lock){if(generation==epoch){ostBlocked=true;waitingNext=false;message="선곡 중단: "+e.getMessage();if(current==null)phase="error";try{ostAudit.put("error",e.getMessage()).put("stopped_at",System.currentTimeMillis());}catch(Exception ignored){}}}}
        finally{synchronized(lock){if(discoveryEpoch==generation){discovery=false;if(generation!=epoch&&!ostBlocked){long pending=epoch;jobs.submit(()->discover(pending));}}}}
    }
    private void refreshOstQueue()throws Exception{
        queue=new JSONArray();String prev=current==null?lastSelectedMovie:current.getJSONObject("movie").getString("id");
        for(int i=ostCursor;i<ostPlan.size();i++){JSONObject x=ostPlan.get(i);String id=x.getJSONObject("movie").getString("id");if(id.equals(prev))continue;queue.put(x);prev=id;}
    }
    private synchronized void nextOst(long generation){
        try{JSONObject item=null;
            synchronized(lock){if(generation!=epoch||ostBlocked||ostEpoch!=generation)return;
                for(JSONObject x:ostPlan)if(System.currentTimeMillis()-x.getJSONObject("track").optLong("fetched_at")>=OstPolicy.TTL){ostEpoch=-1;ostPlan.clear();queue=new JSONArray();jobs.submit(()->discover(generation));return;}
                for(int pass=0;pass<2&&item==null;pass++){
                    while(ostCursor<ostPlan.size()){JSONObject x=ostPlan.get(ostCursor++);if(x.getJSONObject("movie").getString("id").equals(lastSelectedMovie))continue;if(unavailableTracks.getOrDefault(x.getJSONObject("track").getString("uri"),0L)>System.currentTimeMillis())continue;item=new JSONObject(x.toString());break;}
                    if(item==null&&pass==0){ostCursor=0;playbackCycle++;}
                }
                if(item==null){waitingNext=false;message="다른 영화의 재생 가능한 곡이 없어 대기합니다";return;}
            }
            String id=item.getJSONObject("movie").getString("id");item.put("poster",poster(id));
            synchronized(lock){if(generation!=epoch)return;current=item;lastSelectedMovie=id;selectedAt=System.currentTimeMillis();confirmed=false;waitingNext=false;retryFailedSelection=false;resumePosition=0;resumePaused=false;version++;phase="loading";lastPosition=0;message="Deezer 순위 · ISRC로 확인한 OST";context.getSharedPreferences("setup",0).edit().putString("last_selected_movie",id).apply();refreshOstQueue();}
        }catch(Exception e){synchronized(lock){if(generation==epoch){message="선곡 중단: "+e.getMessage();ostBlocked=true;waitingNext=false;}}}
    }
    private void startRequest(String text)throws Exception{
        if(text.trim().isEmpty())throw new IOException("듣고 싶은 음악을 입력해 주세요");
        synchronized(lock){if(!ready)throw new IOException("목록을 준비하고 있습니다");if(!spotifyReady)throw new IOException("먼저 Spotify를 연결해 주세요");}
        JSONObject interpreted=interpret(text);boolean music=true;long gen;
        synchronized(lock){gen=++epoch;ostBlocked=false;ostEpoch=-1;ostPlan.clear();ostCursor=0;queue=new JSONArray();ostAudit=new JSONObject();profile=interpreted;request=text;pairingUntil=0;waitingNext=false;
            if(music){current=null;confirmed=false;phase="searching";transport=null;resumePosition=0;resumePaused=false;version++;for(String query:MusicStyle.queries(interpreted))searchPages.remove(query);}
            if(current==null)phase="searching";message="요청을 받았습니다. Deezer OST와 Spotify ISRC를 확인합니다";
            context.getSharedPreferences("setup",0).edit().putString("profile",profile.toString()).putString("request",request).remove("playback").apply();
        }
        if(music)try{json(playerBase()+"/player/pause",new JSONObject());}catch(Exception ignored){}
        jobs.submit(()->discover(gen));
    }
    private void telemetry(JSONObject body)throws Exception{boolean advance=false;long gen;synchronized(lock){report=body;lastReport=System.currentTimeMillis();gen=epoch;if(current==null||phase.equals("searching")||body.optLong("version")!=version)return;if(!body.optString("error").isEmpty()){phase="error";message=body.optString("error");return;}JSONObject player=body.optJSONObject("player"),t=player==null?null:player.optJSONObject("track");if(player==null)return;if(!confirmed&&selectedAt>0&&System.currentTimeMillis()-selectedAt>45000){unavailableTracks.put(current.getJSONObject("track").optString("uri"),System.currentTimeMillis()+1800000);retryFailedSelection=true;waitingNext=true;retryNextAt=System.currentTimeMillis()+15000;selectedAt=0;jobs.submit(()->next(gen));message="재생되지 않는 곡을 건너뛰고 다른 OST를 찾습니다";return;}String uri=t==null?"":t.optString("uri");JSONObject expected=current.getJSONObject("track");
        if(confirmed&&phase.equals("playing")&&!waitingNext&&!player.optBoolean("buffering")&&lastPosition>=expected.optLong("duration")-5000&&(player.optBoolean("stopped")||(t!=null&&player.optBoolean("paused")&&t.optLong("position")<500))){phase="searching";waitingNext=true;retryNextAt=System.currentTimeMillis()+15000;advance=true;}
        else if((uri.equals(expected.optString("uri"))||(expected.optString("uri").equals(player.optString("context_uri"))&&t!=null&&norm(t.optString("name")).equals(norm(expected.optString("title")))&&Math.abs(t.optLong("duration")-expected.optLong("duration"))<3000))&&!player.optBoolean("stopped",true)){if(!confirmed){uri=expected.optString("uri");seen.add(uri);songKeys.add(songKey(expected));lastMovie=current.getJSONObject("movie").getString("id");recentMovies.remove(lastMovie);recentMovies.addLast(lastMovie);if(recentMovies.size()>15)recentMovies.removeFirst();db.execSQL("INSERT OR REPLACE INTO cycle_seen VALUES(?,?)",new Object[]{uri,songKey(expected)});db.execSQL("INSERT OR REPLACE INTO played VALUES(?,?,?,?)",new Object[]{uri,songKey(expected),lastMovie,System.currentTimeMillis()});confirmed=true;}lastPosition=t.optLong("position");phase=player.optBoolean("paused")?"paused":"playing";message="Deezer 순위 · Spotify ISRC 일치 곡 재생 중";if(System.currentTimeMillis()-lastPlaybackSave>10000||!phase.equals(savedPhase)){context.getSharedPreferences("setup",0).edit().putString("playback",new JSONObject().put("active",true).put("current",current).put("position",lastPosition).put("paused",player.optBoolean("paused")).toString()).apply();lastPlaybackSave=System.currentTimeMillis();savedPhase=phase;}}
        else if(confirmed&&!phase.equals("searching")&&!phase.equals("external")&&!player.optBoolean("buffering")&&(player.optBoolean("stopped")||!uri.isEmpty())){phase="external";context.getSharedPreferences("setup",0).edit().remove("playback").apply();message="다른 앱에서 재생을 바꾸거나 멈춰 자동 선곡을 중단했습니다. 다음 영화를 누르면 다시 시작합니다.";}
    }if(advance){jobs.submit(()->next(gen));jobs.submit(()->discover(gen));}}
    public JSONObject snapshot()throws Exception{synchronized(lock){Set<String> matchingMovies=new HashSet<>();int matching=0;for(JSONObject item:ostPlan){matching++;matchingMovies.add(item.getJSONObject("movie").getString("id"));}int count=0;for(JSONObject m:movies.values())if(eligible(m,profile))count++;return new JSONObject().put("phase",phase).put("message",message).put("request",request).put("profile",profile).put("ost_audit",ostAudit).put("country_mapping",countryMapping).put("current",current==null?JSONObject.NULL:current).put("queue",queue).put("version",version).put("confirmed",confirmed).put("display_mode",displayMode).put("frame_online",System.currentTimeMillis()-lastReport<10000).put("telemetry",report).put("catalog_count",movies.size()).put("eligible_count",count).put("music_match_count",matching).put("music_movie_count",matchingMovies.size()).put("resolved_albums",albums.size()).put("played_count",seen.size()).put("playback_cycle",playbackCycle).put("waiting_next",waitingNext).put("discovery",discovery).put("spotify_ready",spotifyReady).put("search_connected",search.connected()).put("search_status",search.status()).put("spotify_discovery",spotifyDiscovery).put("auth",auth).put("engine",external?"existing-test":"embedded").put("engine_error",engine.error).put("volume",volume()).put("display_visible",displayVisible&&System.currentTimeMillis()-lastDisplay<10000).put("display_error",displayVersion==version?displayError:"");}}
    private void serve(){try{ServerSocket server=new ServerSocket(8787);while(true){Socket socket=server.accept();http.submit(()->handle(socket));}}catch(Exception e){message="연결 서버 오류: "+e.getMessage();}}
    private static String line(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')break;if(b!='\r')out.write(b);if(out.size()>8192)throw new IOException("Header too large");}return out.toString("UTF-8");}
    private void handle(Socket socket){try{Socket s=socket;s.setSoTimeout(10000);InputStream in=s.getInputStream();String[] first=line(in).split(" ");if(first.length<2)return;String method=first[0],path=first[1].split("\\?")[0];Map<String,String> headers=new HashMap<>();String h;while(!(h=line(in)).isEmpty()){int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).toLowerCase(),h.substring(colon+1).trim());}
        int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<0||length>16000){respond(s,413,"{}".getBytes(),"application/json",null);return;}byte[] body=new byte[length];int off=0;while(off<length){int n=in.read(body,off,length-off);if(n<0)throw new IOException("Short request");off+=n;}JSONObject input=length==0?new JSONObject():new JSONObject(new String(body,"UTF-8"));
        boolean frame=key.equals(headers.get("x-frame-key")),logged=false;String cookie=headers.getOrDefault("cookie","");synchronized(lock){for(String token:sessions)if(Arrays.asList(cookie.split("; ")).contains("cf_session="+token))logged=true;}
        String origin=headers.get("origin"),host=headers.get("host");if(method.equals("POST")&&origin!=null&&!origin.equals("http://"+host)){reply(s,403,new JSONObject().put("error","Origin rejected"));return;}
        if(path.equals("/favicon.ico")){respond(s,204,new byte[0],"image/x-icon",null);return;}
        if(path.equals("/")&&method.equals("GET")){respond(s,200,asset("index.html").getBytes("UTF-8"),"text/html; charset=utf-8",null);return;}
        if(path.equals("/api/pairing/start")&&method.equals("POST")){synchronized(lock){pairingUntil=System.currentTimeMillis()+180000;}reply(s,200,new JSONObject().put("ok",true));return;}
        if(path.equals("/api/pairing/end")&&method.equals("POST")&&logged){synchronized(lock){pairingUntil=0;}reply(s,200,new JSONObject().put("ok",true));return;}
        if(path.equals("/api/pair")){synchronized(lock){if(System.currentTimeMillis()-attemptsSince>60000){attempts=0;attemptsSince=System.currentTimeMillis();}if(++attempts>10){reply(s,429,new JSONObject().put("error","잠시 후 다시 입력해 주세요"));return;}if(!code.equals(input.optString("code"))){reply(s,403,new JSONObject().put("error","연결 코드가 다릅니다"));return;}pairingUntil=0;String session=UUID.randomUUID().toString();sessions.add(session);if(sessions.size()>32)sessions.remove(sessions.iterator().next());context.getSharedPreferences("setup",0).edit().putStringSet("sessions",new HashSet<>(sessions)).apply();respond(s,200,"{\"ok\":true}".getBytes(),"application/json","Set-Cookie: cf_session="+session+"; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000\r\n");return;}}
        if(path.equals("/api/frame")&&frame){synchronized(lock){JSONObject result=new JSONObject().put("current",current==null?JSONObject.NULL:current).put("version",version).put("phase",phase).put("message",message).put("paired",!sessions.isEmpty()).put("pairing",pairingVisible()).put("request",request).put("discovery",discovery).put("spotify_ready",spotifyReady).put("confirmed",confirmed).put("display_mode",displayMode).put("player",report.optJSONObject("player")).put("reported_at",lastReport);reply(s,200,result);return;}}
        if(path.equals("/api/telemetry")&&frame){telemetry(input);reply(s,200,new JSONObject().put("ok",true));return;}
        if(!logged){reply(s,401,new JSONObject().put("error","프레임의 연결 코드를 입력해 주세요"));return;}
        if(path.equals("/api/search/auth/start")&&method.equals("POST")){reply(s,200,search.begin(input.getString("client_id")));return;}
        if(path.equals("/api/music/candidates")&&method.equals("GET")){synchronized(lock){reply(s,200,new JSONObject().put("items",new JSONArray(ostPlan)));}return;}
        if(path.equals("/api/music/search")&&method.equals("POST")){if(input.has("playlist_query")){reply(s,200,new JSONObject().put("playlists",search.searchPlaylists(input.getString("playlist_query"),0)));return;}if(input.has("query")){String query=input.getString("query");if(query.length()>200)throw new IOException("검색어가 너무 깁니다");reply(s,200,new JSONObject().put("tracks",search.searchTracks(query,0)));return;}String style=MusicStyle.requested(input.optString("text"));if(style.isEmpty())throw new IOException("지원하는 음악 장르를 입력해 주세요");JSONArray queries=new JSONArray();for(String query:MusicStyle.queries(style))queries.put(new JSONObject().put("query",query).put("tracks",search.searchTracks(query,0)));reply(s,200,new JSONObject().put("style",style).put("results",queries));return;}
        if(path.equals("/api/search/auth/complete")&&method.equals("POST")){search.complete(input.getString("code"),input.getString("state"));synchronized(lock){failed.clear();lastDiscovery=0;}reply(s,200,new JSONObject().put("connected",true));return;}
        if(path.equals("/api/countries")&&method.equals("POST")){
            JSONObject updated;long gen;synchronized(lock){updated=new JSONObject(profile.toString());if(input.has("excluded_countries"))updated.put("excluded_countries",OstPolicy.validateCountries(input.getJSONArray("excluded_countries")));updated.put("country_exclusions_enabled",input.optBoolean("enabled",true));updated.remove("exclude_india");profile=updated;gen=++epoch;ostEpoch=-1;ostBlocked=false;ostPlan.clear();ostCursor=0;queue=new JSONArray();context.getSharedPreferences("setup",0).edit().putString("profile",profile.toString()).apply();}
            if(!request.isEmpty())jobs.submit(()->discover(gen));reply(s,200,new JSONObject().put("profile",updated));return;
        }
        if(path.equals("/api/ost/audit")&&method.equals("GET")){synchronized(lock){reply(s,200,ostAudit);}return;}
        if(path.equals("/api/state")){reply(s,200,snapshot());return;}
        if(path.equals("/api/selection")){synchronized(lock){JSONArray candidates=new JSONArray(),history=new JSONArray();for(JSONObject m:movies.values())if(eligible(m,profile)){String id=m.optString("id");candidates.put(new JSONObject().put("id",id).put("title",m.optString("title")).put("countries",m.optJSONArray("countries")==null?new JSONArray():m.getJSONArray("countries")).put("score",score(m,profile)).put("resolved",albums.containsKey(id)).put("unplayed_tracks",available(id,profile).size()).put("recent",recentMovies.contains(id)).put("lookup_failed",failed.contains(id)));}try(Cursor cur=db.rawQuery("SELECT movie,uri,at FROM played ORDER BY at DESC LIMIT 16",null)){while(cur.moveToNext()){JSONObject m=movies.get(cur.getString(0));history.put(new JSONObject().put("movie",m==null?cur.getString(0):m.optString("title")).put("track",cur.getString(1)).put("at",cur.getLong(2)));}}reply(s,200,new JSONObject().put("candidates",candidates).put("recent_history",history));return;}}
        if(path.equals("/api/poster")){JSONObject c;synchronized(lock){c=current;}if(c==null){reply(s,404,new JSONObject());return;}respond(s,200,fetch(posterUrl(c.getJSONObject("poster").getString("url"),600),null),"image/jpeg",null);return;}
        if(path.equals("/api/preview")&&method.equals("POST")){JSONObject preview=interpret(input.optString("text"));int count=0;synchronized(lock){for(JSONObject m:movies.values())if(eligible(m,preview))count++;}reply(s,200,new JSONObject().put("profile",preview).put("eligible_count",count));return;}
        if(path.equals("/api/request")&&method.equals("POST")){startRequest(input.optString("text"));reply(s,202,new JSONObject().put("ok",true));return;}
        if(path.equals("/api/engine")&&method.equals("POST")){external=input.optBoolean("existing_test");context.getSharedPreferences("setup",0).edit().putBoolean("existing_test_engine",external).apply();reply(s,200,new JSONObject().put("ok",true));return;}
        if(path.equals("/api/display")&&method.equals("POST")){String mode=input.getString("mode");if(!mode.equals("width")&&!mode.equals("height")&&!mode.equals("blur"))throw new IOException("알 수 없는 포스터 표시 방식");synchronized(lock){displayMode=mode;context.getSharedPreferences("setup",0).edit().putString("display_mode",mode).apply();}reply(s,200,new JSONObject().put("display_mode",mode));return;}
        if(path.equals("/api/volume")&&method.equals("POST")){int value=input.optInt("value",engine.getVolume());if(value<0||value>100)throw new IOException("볼륨 범위 오류");engine.setVolume(value);if(input.has("leveling"))engine.setLeveling(input.getBoolean("leveling"));reply(s,200,volume());return;}
        if(path.equals("/api/control")&&method.equals("POST")){String action=input.optString("action");synchronized(lock){if(action.equals("next")){long gen=epoch;jobs.submit(()->next(gen));jobs.submit(()->discover(gen));}else if(action.equals("reconnect")){resumePosition=lastPosition;resumePaused=phase.equals("paused");playbackApplied=-1;retryPlaybackAt=0;engine.stop();spotifyReady=false;}else if(action.equals("seek")){if(!confirmed||current==null||(!phase.equals("playing")&&!phase.equals("paused")))throw new IOException("재생 중인 곡이 없습니다");if(input.getLong("version")!=version)throw new IOException("곡이 변경되었습니다. 다시 시도해 주세요");long duration=current.getJSONObject("track").optLong("duration"),position=input.getLong("position");if(position<0||duration<=0||position>=duration)throw new IOException("재생 위치 범위 오류");transport=new JSONObject().put("id",System.nanoTime()).put("action","seek").put("version",version).put("position",position);}else if(action.equals("pause")||action.equals("resume")){transport=new JSONObject().put("id",System.currentTimeMillis()).put("action",action);}else throw new IOException("알 수 없는 명령");}reply(s,200,new JSONObject().put("ok",true));return;}
        reply(s,404,new JSONObject());
    }catch(Exception e){try{reply(socket,400,new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}finally{try{socket.close();}catch(Exception ignored){}}}
    private static void reply(Socket s,int status,JSONObject o)throws Exception{respond(s,status,o.toString().getBytes("UTF-8"),"application/json; charset=utf-8",null);}
    private static void respond(Socket s,int status,byte[] data,String type,String extra)throws Exception{OutputStream out=s.getOutputStream();String header="HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n"+(extra==null?"":extra)+"\r\n";out.write(header.getBytes("UTF-8"));out.write(data);out.flush();}
}
