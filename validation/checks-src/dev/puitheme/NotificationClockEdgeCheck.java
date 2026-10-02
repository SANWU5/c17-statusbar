package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;

/** Actual edge masks and draw paths, with a one-pixel Porter-Duff coverage oracle. */
public final class NotificationClockEdgeCheck {
    private static int checks;
    private static final Class<?> ENTRY = nested("Entry"), GEOMETRY = nested("Geometry");
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void near(float expected,float actual,String label){check(Math.abs(expected-actual)<.00002f,label+" expected="+expected+" actual="+actual);}
    private static Class<?> nested(String name){try{return Class.forName("dev.puitheme.NotificationClockEdge$"+name);}catch(Exception e){throw new AssertionError(e);}}
    private static Method method(Class<?> owner,String name,Class<?>...args)throws Exception{Method m=owner.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object instance(Class<?> type)throws Exception{Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    private static Object entry(float top,float bottom)throws Exception{Object e=instance(ENTRY);method(ENTRY,"masks",float.class,float.class).invoke(e,top,bottom);return e;}
    private static Object geometry(Canvas canvas,float hardTop,float safeTop,float range)throws Exception{
        Constructor<?> c=GEOMETRY.getDeclaredConstructor(View.class,Canvas.class,float.class,float.class,float.class,float.class,Rect.class);c.setAccessible(true);
        return c.newInstance(new View(new Context()){public int getWidth(){return 240;}public int getHeight(){return 360;}},canvas,hardTop,safeTop,range,8f,new Rect());
    }
    private static Object invoke(Method method,Object owner,Object...args)throws Throwable{
        try{return method.invoke(owner,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    private static Object fade(PixelCanvas canvas,Object geometry,Object entry,NotificationClockEdge.DrawAction action)throws Throwable{
        return invoke(method(NotificationClockEdge.class,"drawFade",Canvas.class,GEOMETRY,ENTRY,NotificationClockEdge.DrawAction.class),null,canvas,geometry,entry,action);
    }

    /** One scalar sample; only standard layer alpha/ADD and DST_IN equations are modeled.
     * No blur kernel, View drawing, RenderNode display list, or SystemUI behavior is simulated. */
    private static final class PixelCanvas extends Canvas {
        final float x=120.5f,y;
        float value,sharpValue=1f,blurValue=1f;
        private final ArrayDeque<Frame> frames=new ArrayDeque<>();
        private static final class Frame {
            final int count;final float previous;boolean layer;PorterDuff.Mode mode;
            Frame(int count,float previous){this.count=count;this.previous=previous;}
        }
        PixelCanvas(float y){this.y=y;clipBounds=new Rect(0,0,240,360);hardware=true;}
        @Override public int save(){int count=super.save();frames.push(new Frame(count,value));return count;}
        @Override public int saveLayer(float left,float top,float right,float bottom,Paint paint){
            int count=super.saveLayer(left,top,right,bottom,paint);Frame frame=frames.peek();frame.layer=true;
            frame.mode=paint!=null&&paint.mode instanceof PorterDuffXfermode?((PorterDuffXfermode)paint.mode).mode:PorterDuff.Mode.SRC_OVER;
            value=0f;return count;
        }
        @Override public void restoreToCount(int count){
            while(!frames.isEmpty()&&frames.peek().count>=count){
                Frame frame=frames.pop();if(frame.layer)value=frame.mode==PorterDuff.Mode.ADD?Math.min(1f,value+frame.previous):value+frame.previous*(1f-value);
            }
            super.restoreToCount(count);
        }
        private float alpha(Paint paint){return paint.getAlpha()/255f*(paint.getShader()==null?1f:paint.getShader().alpha(x,y));}
        @Override public void drawPaint(Paint paint){
            super.drawPaint(paint);check(((PorterDuffXfermode)paint.mode).mode==PorterDuff.Mode.DST_IN,"whole-layer mask retains DST_IN");value*=alpha(paint);
        }
        @Override public void drawRect(float left,float top,float right,float bottom,Paint paint){
            super.drawRect(left,top,right,bottom,paint);
            // For DST_IN, geometric AA coverage mixes the masked result with the old destination.
            float coverage=Math.max(0f,Math.min(x+.5f,right)-Math.max(x-.5f,left))*Math.max(0f,Math.min(y+.5f,bottom)-Math.max(y-.5f,top));
            value*=1f-coverage+coverage*alpha(paint);
        }
        @Override public void drawRenderNode(RenderNode node){
            super.drawRenderNode(node);float source=node.name.endsWith("Band")?blurValue:sharpValue;value=source+value*(1f-source);
        }
    }

    private static void gradientAndGeometry()throws Exception{
        Object entry=entry(100.25f,124.25f);Paint fade=(Paint)field(entry,"fade"),sharp=(Paint)field(entry,"sharp"),blur=(Paint)field(entry,"blur");
        Shader ramp=fade.getShader();check(ramp==sharp.getShader(),"sharp and fade share the same cached gradient");
        for(int i=0;i<=256;i++){
            float y=98.25f+i*28f/256f;float s=sharp.getShader().alpha(120.5f,y),b=blur.getShader().alpha(120.5f,y);
            check(Math.abs(1f-s-b)<=1f/255f+.00002f,"complementary masks agree within one alpha quantization step");
            check(s>=0f&&s<=1f&&b>=0f&&b<=1f,"bounded mask alpha");
        }
        near(0f,ramp.alpha(120.5f,99f),"CLAMP clears above band");near(1f,ramp.alpha(120.5f,200f),"CLAMP preserves native pixels below band");
        method(ENTRY,"masks",float.class,float.class).invoke(entry,100.25f,124.25f);check(fade.getShader()==ramp,"unchanged geometry does not allocate another mask");
        PixelCanvas canvas=new PixelCanvas(100.5f);Object g=geometry(canvas,100.25f,124.25f,24f);
        near(100.25f,((Number)field(g,"hardTop")).floatValue(),"native fractional hard boundary is preserved");
        near(100.25f,((Number)field(g,"top")).floatValue(),"fade boundary stays aligned with hard boundary");
        int captureTop=((Number)field(g,"captureTop")).intValue(),captureHeight=((Number)field(g,"captureHeight")).intValue();
        check(captureTop<100.25f&&captureTop+captureHeight>124.25f,"blur capture includes kernel padding on both sides");
    }

    private static void fractionalFade()throws Throwable{
        for(float fraction:new float[]{.05f,.125f,.25f,.375f,.49f}){
            float top=100f+fraction;PixelCanvas canvas=new PixelCanvas(100.5f);Object g=geometry(canvas,top,top+24f,24f),e=entry(top,top+24f);
            Paint paint=(Paint)field(e,"fade");float expected=paint.getShader().alpha(canvas.x,canvas.y);
            PixelCanvas oldMask=new PixelCanvas(canvas.y);oldMask.value=1f;oldMask.drawRect(0f,top,240f,top+24f,paint);
            check(oldMask.value>expected+.04f,"AA rectangle reproduces bright residual at fractional hard boundary");
            int[] draws={0};Object marker=new Object();Object result=fade(canvas,g,e,c->{draws[0]++;((PixelCanvas)c).value=1f;return marker;});
            near(expected,canvas.value,"actual fallback removes the old geometric-coverage residue");
            check(draws[0]==1&&result==marker,"fallback preserves one native invocation and exact result");
            check(canvas.paintFills==1&&canvas.rects==0,"fallback mask has no separate rectangle edge");
            check(canvas.getSaveCount()==1,"fallback restores layer and clip state");
            near(top,canvas.clipTop,"native fractional clip is retained");
        }
        PixelCanvas below=new PixelCanvas(200.5f);fade(below,geometry(below,100.25f,124.25f,24f),entry(100.25f,124.25f),c->{((PixelCanvas)c).value=.65f;return null;});
        near(.65f,below.value,"whole-layer fallback leaves fully visible notification pixels unchanged");
        PixelCanvas gap=new PixelCanvas(106.5f);fade(gap,geometry(gap,100.25f,140.25f,24f),entry(116.25f,140.25f),c->{((PixelCanvas)c).value=1f;return null;});
        near(0f,gap.value,"space above a delayed fade band is fully clear");
    }

    private static void hardwareComposition()throws Throwable{
        Class<?> rendererType=nested("Api31$Renderer");
        for(float y:new float[]{100.5f,103.5f,112.5f,124.5f,200.5f}){
            PixelCanvas canvas=new PixelCanvas(y);canvas.sharpValue=.6f;canvas.blurValue=.35f;
            Object g=geometry(canvas,100.25f,124.25f,24f),e=entry(100.25f,124.25f),renderer=instance(rendererType);
            invoke(method(rendererType,"compose",Canvas.class,GEOMETRY,ENTRY),renderer,canvas,g,e);
            float t=((Paint)field(e,"fade")).getShader().alpha(canvas.x,canvas.y);
            float b=((Paint)field(e,"blur")).getShader().alpha(canvas.x,canvas.y);
            near((.6f*t+.35f*b)*t,canvas.value,"actual sharp/blur/fade composition matches complementary alpha");
            check(canvas.paintFills==3&&canvas.rects==0,"all three hardware masks avoid AA rectangle boundaries");
            check(canvas.getSaveCount()==1&&Boolean.TRUE.equals(field(renderer,"outputDrawn")),"hardware publishes and restores one continuous output");
        }
    }

    private static void failureAndNativeOwnership()throws Throwable{
        Object e=entry(100.25f,124.25f);int[] draws={0};Object marker=new Object();
        PixelCanvas layerFail=new PixelCanvas(100.5f);layerFail.failLayerOnce=true;
        Object result=fade(layerFail,geometry(layerFail,100.25f,124.25f,24f),e,c->{draws[0]++;return marker;});
        check(result==marker&&draws[0]==1&&layerFail.getSaveCount()==1,"layer failure retains single native draw and restores clip");
        PixelCanvas maskFail=new PixelCanvas(100.5f);maskFail.failMaskOnce=true;draws[0]=0;
        result=fade(maskFail,geometry(maskFail,100.25f,124.25f,24f),e,c->{draws[0]++;return marker;});
        check(result==marker&&draws[0]==1&&maskFail.getSaveCount()==1,"mask failure does not redraw native content");
        PixelCanvas nativeFail=new PixelCanvas(100.5f);Throwable failure=new IllegalStateException("native test failure");
        try{fade(nativeFail,geometry(nativeFail,100.25f,124.25f,24f),e,c->{throw failure;});throw new AssertionError("native exception lost");}
        catch(Throwable observed){check(observed==failure,"native drawing exception remains exact");}
        check(nativeFail.getSaveCount()==1,"native exception still releases the temporary layer");
    }

    public static void main(String[] args)throws Throwable{
        gradientAndGeometry();fractionalFade();hardwareComposition();failureAndNativeOwnership();
        System.out.println("Notification clock edge checks passed: "+checks);
    }
}
