// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.lang.ref.WeakReference;

/** Frosted local backdrop; captures only this bar's region and has no idle refresh work. */
final class SettingsGlassPanel extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final WeakReference<View> backdrop,host;
    private final float density;
    private Bitmap image;
    private int[] pixels,scratch;
    private int alpha=255;
    private boolean dirty=true,captureScheduled;
    private long lastCapture;
    private float selectedPosition;
    private int selectedPage;
    private ValueAnimator selection;
    private final Runnable captureKick=()->{captureScheduled=false;invalidateSelf();};

    SettingsGlassPanel(View backdrop,View host,float density) {
        this.backdrop=new WeakReference<>(backdrop);this.host=new WeakReference<>(host);this.density=density;
    }
    void invalidateBackdrop() { dirty=true;invalidateSelf(); }
    void suspend() {
        if(selection!=null){selection.cancel();selection=null;}selectedPosition=selectedPage;
        View target=host.get();if(target!=null)target.removeCallbacks(captureKick);captureScheduled=false;
    }
    void release() { suspend();if(image!=null){image.recycle();image=null;}pixels=null;scratch=null; }
    void select(int page,boolean animate) {
        if(selectedPage==page)return;selectedPage=page;
        if(selection!=null)selection.cancel();
        if(!animate){selectedPosition=page;invalidateSelf();return;}
        selection=ValueAnimator.ofFloat(selectedPosition,page);selection.setDuration(200L);selection.setInterpolator(new DecelerateInterpolator());
        selection.addUpdateListener(value->{selectedPosition=(Float)value.getAnimatedValue();invalidateSelf();});selection.start();
    }
    @Override public void draw(Canvas canvas) {
        View source=backdrop.get(),target=host.get();
        if(source!=null&&target!=null&&dirty)refreshBackdrop(source,target);
        RectF bounds=new RectF(getBounds());bounds.inset(.7f*density,.7f*density);float radius=bounds.height()/2f;
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(tint(0x66ffffff));paint.setShadowLayer(10f*density,0,0,tint(0x33101010));
        canvas.drawRoundRect(bounds,radius,radius,paint);paint.clearShadowLayer();
        if(image!=null) {
            int saved=canvas.save();Path clip=new Path();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);canvas.clipPath(clip);
            paint.setColor(tint(0xffffffff));paint.setAlpha(alpha);canvas.drawBitmap(image,null,new RectF(getBounds()),paint);canvas.restoreToCount(saved);paint.setAlpha(255);
        }
        paint.setShader(new LinearGradient(0,bounds.top,0,bounds.bottom,
                new int[]{tint(0x90ffffff),tint(0x66ffffff),tint(0x80ffffff)},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        canvas.drawRoundRect(bounds,radius,radius,paint);paint.setShader(null);
        float left=getBounds().left+4f*density,cell=(getBounds().width()-8f*density)/4f;
        RectF capsule=new RectF(left+selectedPosition*cell+4f*density,getBounds().top+4f*density,left+(selectedPosition+1f)*cell-4f*density,getBounds().bottom-4f*density);
        paint.setColor(tint(0xd8dddddd));canvas.drawRoundRect(capsule,capsule.height()/2f,capsule.height()/2f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f*density);paint.setColor(tint(0xf7ffffff));
        bounds.inset(.8f*density,.8f*density);canvas.drawRoundRect(bounds,radius,radius,paint);
        paint.setStrokeWidth(.6f*density);paint.setShader(new LinearGradient(0,bounds.top,0,bounds.bottom,tint(0x14ffffff),tint(0x24777777),Shader.TileMode.CLAMP));
        bounds.inset(density,density);canvas.drawRoundRect(bounds,radius-density,radius-density,paint);paint.setShader(null);
    }
    private void refreshBackdrop(View source,View target) {
        long now=SystemClock.uptimeMillis(),remaining=32L-(now-lastCapture);
        if(remaining>0L) {
            if(!captureScheduled) { captureScheduled=true;target.postDelayed(captureKick,remaining); }
            return;
        }
        int width=getBounds().width(),height=getBounds().height();if(width<=0||height<=0||source.getWidth()==0)return;
        float scale=Math.min(.16f,220f/width);int smallWidth=Math.max(1,Math.round(width*scale)),smallHeight=Math.max(1,Math.round(height*scale));
        try {
            if(image==null||image.getWidth()!=smallWidth||image.getHeight()!=smallHeight) {
                if(image!=null)image.recycle();image=Bitmap.createBitmap(smallWidth,smallHeight,Bitmap.Config.ARGB_8888);
                pixels=new int[smallWidth*smallHeight];scratch=new int[pixels.length];
            }
            image.eraseColor(0xfff7f7f7);Canvas capture=new Canvas(image);capture.scale(scale,scale);
            int[] sourceLocation=new int[2],targetLocation=new int[2];source.getLocationInWindow(sourceLocation);target.getLocationInWindow(targetLocation);
            capture.translate(sourceLocation[0]-targetLocation[0],sourceLocation[1]-targetLocation[1]);
            // The small bitmap clips traversal to the covered region; foreground navigation is a sibling.
            source.draw(capture);image.getPixels(pixels,0,smallWidth,0,0,smallWidth,smallHeight);
            int radius=Math.max(1,Math.min(3,Math.round(4f*density*scale)));
            for(int pass=0;pass<2;pass++){blur(pixels,scratch,smallWidth,smallHeight,radius,true);blur(scratch,pixels,smallWidth,smallHeight,radius,false);}
            refract(pixels,scratch,smallWidth,smallHeight,Math.min(smallHeight*.45f,24f*density*scale));
            image.setPixels(scratch,0,smallWidth,0,0,smallWidth,smallHeight);lastCapture=now;dirty=false;
        } catch(RuntimeException unavailable) { dirty=false; }
    }
    /** A shallow lens near the capsule rim. It only resamples the already small, blurred image. */
    private static void refract(int[] input,int[] output,int width,int height,float band) {
        float radius=height/2f,maxShift=Math.min(1.5f,height*.045f);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            float centreX=Math.max(radius,Math.min(width-radius,x+.5f)),dx=x+.5f-centreX,dy=y+.5f-radius;
            float distance=(float)Math.sqrt(dx*dx+dy*dy),edge=radius-distance;
            if(edge<0f||edge>=band||distance<.001f){output[y*width+x]=input[y*width+x];continue;}
            float fraction=1f-edge/band,shift=maxShift*fraction*fraction;
            float sx=Math.max(0f,Math.min(width-1f,x+dx/distance*shift)),sy=Math.max(0f,Math.min(height-1f,y+dy/distance*shift));
            int ix=(int)sx,iy=(int)sy,nx=Math.min(width-1,ix+1),ny=Math.min(height-1,iy+1);
            int top=blend(input[iy*width+ix],input[iy*width+nx],sx-ix),bottom=blend(input[ny*width+ix],input[ny*width+nx],sx-ix);
            output[y*width+x]=blend(top,bottom,sy-iy);
        }
    }
    private static int blend(int first,int second,float fraction) {
        int red=Math.round(((first>>>16)&255)*(1f-fraction)+((second>>>16)&255)*fraction);
        int green=Math.round(((first>>>8)&255)*(1f-fraction)+((second>>>8)&255)*fraction);
        int blue=Math.round((first&255)*(1f-fraction)+(second&255)*fraction);
        return 0xff000000|red<<16|green<<8|blue;
    }
    private static void blur(int[] input,int[] output,int width,int height,int radius,boolean horizontal) {
        int lines=horizontal?height:width,length=horizontal?width:height,size=radius*2+1;
        for(int line=0;line<lines;line++) {
            int red=0,green=0,blue=0;
            for(int offset=-radius;offset<=radius;offset++){int value=input[index(line,Math.max(0,Math.min(length-1,offset)),width,horizontal)];red+=(value>>>16)&255;green+=(value>>>8)&255;blue+=value&255;}
            for(int position=0;position<length;position++) {
                output[index(line,position,width,horizontal)]=0xff000000|(red/size)<<16|(green/size)<<8|blue/size;
                int removed=input[index(line,Math.max(0,position-radius),width,horizontal)],added=input[index(line,Math.min(length-1,position+radius+1),width,horizontal)];
                red+=((added>>>16)&255)-((removed>>>16)&255);green+=((added>>>8)&255)-((removed>>>8)&255);blue+=(added&255)-(removed&255);
            }
        }
    }
    private static int index(int line,int position,int width,boolean horizontal) { return horizontal?line*width+position:position*width+line; }
    private int tint(int color) { return (color&0xffffff)|(Math.round((color>>>24)*alpha/255f)<<24); }
    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(),38f*density);outline.setAlpha(.18f); }
    @Override public void setAlpha(int alpha) { this.alpha=Math.max(0,Math.min(255,alpha));invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
