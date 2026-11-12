package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import java.lang.reflect.InvocationHandler;

/** Native vector contract: a 138x62 capsule with filled dash paths and an independent plus path. */
public class VectorDrawable extends Drawable {
    public static InvocationHandler drawInterceptorForCheck;
    public boolean outline=true,dashed=true,plus=true,failNativeForCheck;
    public int colour=0xffe6e6e6,alpha=255,nativeDraws,intrinsicWidth=138,intrinsicHeight=62;
    public final java.util.List<Rect> draws=new java.util.ArrayList<>();
    public int getIntrinsicWidth(){return intrinsicWidth;}
    public int getIntrinsicHeight(){return intrinsicHeight;}
    public int getAlpha(){return alpha;}
    public void setAlpha(int value){alpha=value;}
    public void setColorFilter(ColorFilter filter){}
    public int getOpacity(){return -3;}
    public void draw(Canvas canvas){
        InvocationHandler hook=drawInterceptorForCheck;
        if(hook==null){drawNativeForCheck(canvas);return;}
        try{hook.invoke(this,VectorDrawable.class.getMethod("draw",Canvas.class),new Object[]{canvas});}
        catch(RuntimeException|Error failure){throw failure;}
        catch(Throwable failure){throw new IllegalStateException(failure);}
    }
    public void drawNativeForCheck(Canvas canvas){
        if(failNativeForCheck)throw new IllegalStateException("native-vector-failure");
        nativeDraws++;draws.add(new Rect(getBounds()));Rect bounds=getBounds();int saved=canvas.save();
        try{
            canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/138f,bounds.height()/62f);
            Paint paint=new Paint(1);paint.setColor(colour);paint.setAlpha(Math.round(alpha*(outline?.30f:.10f)));
            if(outline){
                caps(canvas,paint,31,90,270);caps(canvas,paint,107,-90,90);
                float step=dashed?7.6f:76f,length=dashed?3.8f:76f;
                for(float x=31;x<107;x+=step){rect(canvas,paint,x,0,Math.min(107,x+length),2);rect(canvas,paint,x,60,Math.min(107,x+length),62);}
            }else{Path path=new Path();path.addRoundRect(new RectF(0,0,138,62),31,31,Path.Direction.CW);canvas.drawPath(path,paint);}
            if(plus){paint.setAlpha(Math.round(alpha*.54f));rect(canvas,paint,68,22,70,40);rect(canvas,paint,60,30,78,32);}
        }finally{canvas.restoreToCount(saved);}
    }
    private void caps(Canvas canvas,Paint paint,float cx,int begin,int end){
        int step=dashed?18:180,length=dashed?9:180;
        for(int start=begin;start<end;start+=step){int finish=Math.min(end,start+length);Path path=new Path();point(path,cx,31,31,start,true);
            for(int angle=start+1;angle<=finish;angle++)point(path,cx,31,31,angle,false);
            point(path,cx,31,29,finish,false);for(int angle=finish-1;angle>=start;angle--)point(path,cx,31,29,angle,false);path.close();canvas.drawPath(path,paint);}
    }
    private static void point(Path path,float cx,float cy,float radius,int degrees,boolean first){double a=Math.toRadians(degrees);float x=cx+(float)Math.cos(a)*radius,y=cy+(float)Math.sin(a)*radius;if(first)path.moveTo(x,y);else path.lineTo(x,y);}
    private static void rect(Canvas canvas,Paint paint,float l,float t,float r,float b){Path path=new Path();path.addRoundRect(new RectF(l,t,r,b),0,0,Path.Direction.CW);canvas.drawPath(path,paint);}
    public ConstantState getConstantState(){return new ConstantState(){public Drawable newDrawable(){VectorDrawable copy=new VectorDrawable();copy.outline=outline;copy.dashed=dashed;copy.plus=plus;copy.colour=colour;copy.alpha=alpha;copy.intrinsicWidth=intrinsicWidth;copy.intrinsicHeight=intrinsicHeight;return copy;}};}
}
