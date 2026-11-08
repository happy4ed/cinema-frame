package com.happy.cinemaframe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler();
    private PosterView image;private LinearLayout info,setup;private TextView title,genres,rating,track,artist,setupTitle,setupText,setupCode,setupHelp,pairButton,closePair;
    private ProgressLine progress;private Typeface cinema;private String base,key,code;private long applied=-1,nextPosterRetry=0,displayedVersion=-1;private FrameCore core;
    private JSONObject metrics=new JSONObject();private volatile boolean alive=true,visible=true;private String error="";
    private final Runnable animate=new Runnable(){public void run(){if(!alive)return;if(visible&&progress!=null)progress.invalidate();main.postDelayed(this,200);}};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
        cinema=font("BarlowCondensed-SemiBold.ttf",600);
        FrameLayout root=new FrameLayout(this);root.addView(new CinemaBackdrop(),new FrameLayout.LayoutParams(-1,-1));
        image=new PosterView(this);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(24),dp(90),dp(36),dp(52));
        info.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xA3000000,0xF2000000}));
        title=text(18,0xDFE4E9EA);title.setTypeface(font("Pretendard-SemiBold.otf",600));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setLineSpacing(0,1f);title.setPadding(0,dp(2),0,0);
        genres=badge(0xFFBCE4DE,0xB5264445);rating=badge(0xFFF5D59A,0xB54D3B22);
        FlowRow heading=new FlowRow();heading.addView(genres);heading.addView(rating);
        track=text(38,Color.WHITE);track.setTypeface(font("WantedSans-ExtraBold.otf",800));track.setMaxLines(3);track.setEllipsize(TextUtils.TruncateAt.END);track.setAutoSizeTextTypeUniformWithConfiguration(26,38,1,android.util.TypedValue.COMPLEX_UNIT_SP);track.setLineSpacing(0,1f);track.setPadding(0,0,0,0);
        artist=text(16,0xDFE4E9EA);artist.setTypeface(font("WantedSans-Bold.otf",700));artist.setMaxLines(2);artist.setEllipsize(TextUtils.TruncateAt.END);artist.setPadding(0,0,0,dp(3));
        info.addView(track);info.addView(artist);info.addView(heading);info.addView(title);root.addView(info,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));info.setVisibility(View.GONE);
        setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setGravity(Gravity.CENTER);setup.setPadding(dp(30),dp(36),dp(30),dp(36));
        GradientDrawable card=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xD51A2429,0xE80B1016});card.setCornerRadius(dp(20));card.setStroke(dp(1),0x406F7163);setup.setBackground(card);
        TextView brand=text(11,0xFFD3BD92);brand.setText("C I N E M A   F R A M E");brand.setGravity(Gravity.CENTER);brand.setPadding(0,0,0,dp(22));setup.addView(brand);
        setupTitle=text(36,Color.WHITE);setupTitle.setTypeface(cinema);setupTitle.setGravity(Gravity.CENTER);setup.addView(setupTitle);
        setupText=text(16,0xE5FFFFFF);setupText.setGravity(Gravity.CENTER);setupText.setLineSpacing(dp(5),1f);setupText.setPadding(0,dp(14),0,dp(18));setup.addView(setupText);
        setupCode=text(72,Color.WHITE);setupCode.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));setupCode.setGravity(Gravity.CENTER);setupCode.setLetterSpacing(.08f);setupCode.setPadding(0,dp(8),0,dp(18));setup.addView(setupCode);
        setupHelp=text(12,0xBFFFFFFF);setupHelp.setGravity(Gravity.CENTER);setupHelp.setLineSpacing(dp(4),1f);setup.addView(setupHelp);
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);center.setMargins(dp(34),dp(24),dp(34),dp(24));root.addView(setup,center);
        pairButton=text(12,0xCCD3BD92);pairButton.setText("연결");pairButton.setContentDescription("휴대폰 / Home Assistant 연결 코드 표시");pairButton.setGravity(Gravity.CENTER);pairButton.setMinWidth(dp(48));pairButton.setMinHeight(dp(48));pairButton.setOnClickListener(v->{if(core!=null)core.showPairing();});FrameLayout.LayoutParams pairPosition=new FrameLayout.LayoutParams(dp(64),dp(48),Gravity.BOTTOM|Gravity.RIGHT);pairPosition.setMargins(0,0,dp(24),dp(10));root.addView(pairButton,pairPosition);
        closePair=text(13,0xFFD3BD92);closePair.setText("닫기");closePair.setGravity(Gravity.CENTER);closePair.setMinHeight(dp(48));closePair.setOnClickListener(v->{if(core!=null)core.hidePairing();});setup.addView(closePair);
        progress=new ProgressLine();root.addView(progress,new FrameLayout.LayoutParams(-1,4,Gravity.BOTTOM));progress.setVisibility(View.GONE);setContentView(root);
        try{startForegroundService(new android.content.Intent(this,FrameService.class));core=FrameCore.get(this);base="http://127.0.0.1:8787";key=core.key;code=core.code;setupTitle.setText("Your next scene.");setupText.setText("휴대폰과 연결을 준비하고 있습니다");}
        catch(Exception e){setupTitle.setText("Cinema Frame");setupText.setText("연결을 준비하지 못했습니다. 앱을 다시 열어 주세요.");return;}
        main.post(animate);worker.scheduleWithFixedDelay(this::tick,0,1000,TimeUnit.MILLISECONDS);
    }
    private TextView text(int sp,int color){TextView t=new TextView(this);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(true);t.setPadding(0,dp(5),0,dp(5));return t;}
    private Typeface font(String name,int weight){try{return new Typeface.Builder(getAssets(),"fonts/"+name).setWeight(weight).setFontVariationSettings("'wght' "+weight).build();}catch(Exception e){return Typeface.create("sans-serif",Typeface.NORMAL);}}
    private TextView badge(int foreground,int background){TextView v=text(11,foreground);v.setTypeface(font("Pretendard-SemiBold.otf",600));v.setPadding(0,dp(1),dp(6),dp(1));return v;}
    private String genreLabel(JSONObject movie){Map<String,String> names=new HashMap<>();String[] en={"Action","Adventure","Animation","Biography","Comedy","Crime","Documentary","Drama","Family","Fantasy","History","Horror","Music","Musical","Mystery","Romance","Sci-Fi","Sport","Thriller","War","Western"};String[] ko={"액션","모험","애니메이션","전기","코미디","범죄","다큐멘터리","드라마","가족","판타지","역사","공포","음악","뮤지컬","미스터리","로맨스","SF","스포츠","스릴러","전쟁","서부극"};for(int i=0;i<en.length;i++)names.put(en[i],ko[i]);ArrayList<String> labels=new ArrayList<>();JSONArray list=movie.optJSONArray("genres");if(list!=null)for(int i=0;i<list.length();i++)labels.add(names.getOrDefault(list.optString(i),list.optString(i)));return TextUtils.join(" · ",labels);}
    private final class FlowRow extends ViewGroup {
        private final ArrayList<Rect> positions=new ArrayList<>();
        FlowRow(){super(MainActivity.this);}
        @Override protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws),x=0,y=0,row=0,gap=dp(8);positions.clear();
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int cw=child.getMeasuredWidth(),ch=child.getMeasuredHeight();if(x>0&&x+cw>width){x=0;y+=row+gap;row=0;}positions.add(new Rect(x,y,x+cw,y+ch));x+=cw+gap;row=Math.max(row,ch);}
            setMeasuredDimension(width,y+row);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){Rect box=positions.get(i);int rowBottom=box.bottom;for(Rect other:positions)if(other.top==box.top)rowBottom=Math.max(rowBottom,other.bottom);int dy=(rowBottom-box.bottom)/2;getChildAt(i).layout(box.left,box.top+dy,box.right,box.bottom+dy);}}
    }
    private byte[] request(String url,int max,boolean auth)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(20000);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-store");c.setRequestProperty("User-Agent","CinemaFrame/0.4");if(auth)c.setRequestProperty("X-Frame-Key",key);
        try{int response=c.getResponseCode();if(response<200||response>=300)throw new IOException("HTTP "+response);return FrameCore.read(c.getInputStream(),max);}finally{c.disconnect();}
    }
    private void render(JSONObject command,JSONObject current){
        boolean pairing=command.optBoolean("pairing",true),ready=command.optBoolean("spotify_ready");pairButton.setVisibility(pairing?View.GONE:View.VISIBLE);image.setMode(command.optString("display_mode","height"));
        setup.setVisibility(pairing||current==null?View.VISIBLE:View.GONE);info.setVisibility(!pairing&&current!=null?View.VISIBLE:View.GONE);image.setVisibility(pairing?View.INVISIBLE:View.VISIBLE);
        closePair.setVisibility(pairing?View.VISIBLE:View.GONE);setupCode.setVisibility(pairing?View.VISIBLE:View.GONE);setupHelp.setVisibility(pairing?View.VISIBLE:View.GONE);
        if(pairing){setupTitle.setText("A frame for your music.");setupText.setText("Cinema Remote 또는 Home Assistant에서\n아래 연결 코드를 입력해 주세요");setupCode.setText(code.substring(0,3)+" "+code.substring(3));setupHelp.setText("30초 후 자동으로 닫힙니다 · 다시 보려면 연결\n같은 Wi-Fi에 연결해 주세요\nCinema Frame · 포트 8787\n직접 연결 주소  "+FrameCore.address());}
        else if(ready&&!command.optString("request").isEmpty()){setupTitle.setText(command.optBoolean("discovery")?"영화 OST 검색 중":"조건에 맞는 OST를 찾는 중");setupText.setText(command.optString("request")+"\n\n"+command.optString("message","Spotify 검색 결과를 확인하고 있습니다"));}
        else {setupTitle.setText("Your next scene.");setupText.setText(ready?"연결되었습니다\n휴대폰에서 영화나 분위기를 골라 주세요":"휴대폰에서 Spotify 계정 연결을\n마무리해 주세요");}
        if(current!=null&&command.optLong("version")==displayedVersion){JSONObject movie=current.optJSONObject("movie"),song=current.optJSONObject("track");if(movie!=null&&song!=null){title.setText(movie.optString("title"));genres.setText(genreLabel(movie));rating.setText(movie.optString("rating_source","IMDb")+"  " + movie.optString("rating"));track.setText(song.optString("title"));artist.setText(song.optString("artist"));}}
        JSONObject player=command.optJSONObject("player"),playingTrack=player==null?null:player.optJSONObject("track");boolean confirmed=command.optBoolean("confirmed");progress.setVisibility(!pairing&&current!=null&&confirmed&&command.optLong("version")==displayedVersion&&!"external".equals(command.optString("phase"))?View.VISIBLE:View.GONE);
        if(playingTrack!=null&&confirmed)progress.update(playingTrack.optLong("position"),playingTrack.optLong("duration"),!player.optBoolean("paused")&&!player.optBoolean("stopped"),command.optLong("reported_at",System.currentTimeMillis()));
    }
    private void tick(){if(!alive||!visible)return;try{
        JSONObject command=new JSONObject(new String(request(base+"/api/frame",2000000,true),"UTF-8"));JSONObject current=command.optJSONObject("current");long version=command.optLong("version",0);ui(()->render(command,current));
        if(current!=null&&(version!=applied||(!error.isEmpty()&&System.currentTimeMillis()>=nextPosterRetry))){applied=version;error="";metrics=new JSONObject();try{
            JSONObject poster=current.getJSONObject("poster");long begin=System.currentTimeMillis();byte[] bytes=request(FrameCore.posterUrl(poster.getString("url"),2160),25000000,false);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;while((long)(bounds.outWidth/opts.inSampleSize)*(bounds.outHeight/opts.inSampleSize)>7000000)opts.inSampleSize*=2;
            final Bitmap next=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(next==null)throw new IOException("이미지 해석 실패");metrics=new JSONObject().put("source_width",bounds.outWidth).put("source_height",bounds.outHeight).put("display_width",next.getWidth()).put("display_height",next.getHeight()).put("bytes",bytes.length).put("load_ms",System.currentTimeMillis()-begin).put("disk_cache",false);
            final Bitmap blurred=PosterView.blur(next);ui(()->{displayedVersion=version;image.setPoster(next,blurred);render(command,current);});
        }catch(Exception e){nextPosterRetry=System.currentTimeMillis()+10000;error="포스터를 다시 불러옵니다: "+e.getMessage();}}
        core.display(applied,metrics,error,visible);
    }catch(Exception e){/* Connection detail stays in the phone controller, not over the artwork. */}}
    private void ui(Runnable r){if(alive)runOnUiThread(()->{if(alive)r.run();});}
    @Override public void onStart(){super.onStart();visible=true;if(core!=null)core.displayVisibility(true);}
    @Override public void onStop(){visible=false;if(core!=null)core.displayVisibility(false);super.onStop();}
    @Override public void onDestroy(){alive=false;worker.shutdownNow();main.removeCallbacks(animate);super.onDestroy();}
    private final class ProgressLine extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private long position,duration,sampledAt;private boolean running;
        ProgressLine(){super(MainActivity.this);}
        void update(long p,long d,boolean play,long at){position=p;duration=d;running=play;sampledAt=at;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setColor(0x667D858C);c.drawRect(0,0,getWidth(),getHeight(),paint);if(duration>0){long now=position+(running?Math.max(0,System.currentTimeMillis()-sampledAt):0);paint.setColor(0xFFF5D59A);c.drawRect(0,0,getWidth()*Math.min(1f,(float)now/duration),getHeight(),paint);}}
    }
    private final class CinemaBackdrop extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CinemaBackdrop(){super(MainActivity.this);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(0xFF080D13);paint.setColor(Color.WHITE);
            paint.setShader(new RadialGradient(w*.92f,h*.18f,Math.max(w,h)*.8f,new int[]{0x664E4030,0x00080D13},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w*.08f,h*.85f,Math.max(w,h)*.65f,new int[]{0x44305A59,0x00080D13},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.5f));paint.setColor(0x225F6A65);float inset=dp(20);c.drawRoundRect(inset,inset,w-inset,h-inset,dp(6),dp(6),paint);paint.setStyle(Paint.Style.FILL);
            Random dots=new Random(1947);paint.setColor(0x16C8B68F);for(int i=0;i<260;i++)c.drawCircle(dots.nextFloat()*w,dots.nextFloat()*h,dp(.5f),paint);
        }
    }
}
