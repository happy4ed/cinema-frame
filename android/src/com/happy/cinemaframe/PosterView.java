package com.happy.cinemaframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Two in-memory poster layers; changing layout never downloads another image. */
final class PosterView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap poster,background;
    private String mode="height";
    PosterView(Context context){super(context);}
    void setMode(String value){if(!mode.equals(value)){mode=value;invalidate();}}
    void setPoster(Bitmap image,Bitmap blurred){
        Bitmap old=poster,oldBackground=background;poster=image;background=blurred;invalidate();
        if(old!=null&&old!=image)old.recycle();
        if(oldBackground!=null&&oldBackground!=blurred)oldBackground.recycle();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(poster==null)return;
        canvas.drawColor(Color.BLACK);
        if("blur".equals(mode)&&background!=null){
            draw(canvas,background,Math.max((float)getWidth()/background.getWidth(),(float)getHeight()/background.getHeight()));
            canvas.drawColor(0x26000000);
        }
        float scale="height".equals(mode)?(float)getHeight()/poster.getHeight():(float)getWidth()/poster.getWidth();
        draw(canvas,poster,scale);
    }
    private void draw(Canvas canvas,Bitmap source,float scale){
        float width=source.getWidth()*scale,height=source.getHeight()*scale;
        float left=(getWidth()-width)/2f,top=(getHeight()-height)/2f;
        canvas.drawBitmap(source,null,new RectF(left,top,left+width,top+height),paint);
    }
    /** Small CPU blur prepared off the UI thread, compatible with Android 10. */
    static Bitmap blur(Bitmap source){
        float scale=Math.min(1f,192f/Math.max(source.getWidth(),source.getHeight()));
        int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(source,w,h,true);
        int[] pixels=new int[w*h],scratch=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        if(small!=source)small.recycle();
        for(int i=0;i<3;i++){box(pixels,scratch,w,h,6,true);box(scratch,pixels,w,h,6,false);}
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private static void box(int[] input,int[] output,int w,int h,int radius,boolean horizontal){
        int length=horizontal?w:h,lines=horizontal?h:w,divisor=radius*2+1;
        for(int line=0;line<lines;line++){
            int red=0,green=0,blue=0;
            for(int i=-radius;i<=radius;i++){
                int p=input[index(line,Math.max(0,Math.min(length-1,i)),w,horizontal)];
                red+=(p>>16)&255;green+=(p>>8)&255;blue+=p&255;
            }
            for(int i=0;i<length;i++){
                output[index(line,i,w,horizontal)]=0xff000000|((red/divisor)<<16)|((green/divisor)<<8)|(blue/divisor);
                int before=input[index(line,Math.max(0,i-radius),w,horizontal)],after=input[index(line,Math.min(length-1,i+radius+1),w,horizontal)];
                red+=((after>>16)&255)-((before>>16)&255);green+=((after>>8)&255)-((before>>8)&255);blue+=(after&255)-(before&255);
            }
        }
    }
    private static int index(int line,int point,int width,boolean horizontal){return horizontal?line*width+point:point*width+line;}
}
