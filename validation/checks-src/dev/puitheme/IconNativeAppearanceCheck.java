package dev.puitheme;

import android.graphics.*;
import android.graphics.drawable.*;
import java.lang.reflect.Field;
import java.util.*;

/** Actual production leaf transaction and real ColorPlan, with no injected success renderer. */
public final class IconNativeAppearanceCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Native appearance "+checks+": "+expected+" != "+actual);}
    static final class Glyph extends Drawable {
        int alpha,drawAlpha,draws,internalTint=0xffffffff;ColorFilter filter,drawFilter;boolean fail,guard,rootGuard;Drawable root;Runnable event;
        Glyph(int alpha,ColorFilter filter){this.alpha=alpha;this.filter=filter;}
        @Override public void draw(Canvas canvas){draws++;drawAlpha=alpha;drawFilter=filter;guard=IconManagementRuntime.nativeDrawing(this);rootGuard=root==null||IconManagementRuntime.nativeDrawing(root);
            Runnable next=event;event=null;if(next!=null)next.run();if(fail)throw new IllegalStateException("native leaf draw failed");}
        @Override public void setAlpha(int value){alpha=value;}@Override public int getAlpha(){return alpha;}
        @Override public void setColorFilter(ColorFilter value){filter=value;}@Override public ColorFilter getColorFilter(){return filter;}@Override public int getOpacity(){return -3;}
        int effective(){Integer color=IconManagementRuntime.filterColor(drawFilter);return Math.round(drawAlpha*((color==null?internalTint:color)>>>24)/255f);}
    }
    private static ColorFilter tint(int color){return new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN);}
    private static IconManagementSelection.ColorPlan plan(String mode,float opacity)throws Exception{
        String saved=IconManagementSettings.applyAppearance(IconManagementSettings.DEFAULT,"target:wifi",new IconManagementSettings.Appearance(mode,0xff123456,0xffabcdef,opacity));
        return IconManagementSelection.appearance(IconManagementSettings.parse(saved),"wifi.4",false,"color");
    }
    private static void leaves()throws Exception{
        IconManagementSelection.ColorPlan custom=plan("custom",50);Canvas canvas=new Canvas();ColorFilter nativeFilter=tint(0x80112233);Glyph glyph=new Glyph(200,nativeFilter);
        IconNativeAppearance.draw(glyph,canvas,custom,0x80112233);equal(50,glyph.drawAlpha);equal(0xff123456,IconManagementRuntime.filterColor(glyph.drawFilter));equal(50,glyph.effective());
        equal(200,glyph.alpha);equal(nativeFilter,glyph.filter);equal(true,glyph.guard);equal(false,IconManagementRuntime.nativeDrawing(glyph));
        Glyph internal=new Glyph(200,null);internal.internalTint=0x80112233;
        IconNativeAppearance.draw(internal,canvas,custom,internal.internalTint);equal(50,internal.drawAlpha);equal(50,internal.effective());equal(null,internal.filter);equal(200,internal.alpha);
        IconNativeAppearance.draw(internal,canvas,plan("system",50),internal.internalTint);equal(100,internal.drawAlpha);equal(50,internal.effective());equal(null,internal.drawFilter);
        ColorFilter unknown=new ColorFilter();Glyph unusual=new Glyph(173,unknown);IconNativeAppearance.draw(unusual,canvas,custom);equal(87,unusual.drawAlpha);equal(unknown,unusual.drawFilter);equal(unknown,unusual.filter);equal(173,unusual.alpha);
        glyph.fail=true;try{IconNativeAppearance.draw(glyph,canvas,custom);throw new AssertionError("native failure missing");}catch(IllegalStateException expected){equal("native leaf draw failed",expected.getMessage());}
        equal(200,glyph.alpha);equal(nativeFilter,glyph.filter);equal(false,IconManagementRuntime.nativeDrawing(glyph));
    }
    private static void layers()throws Exception{
        Canvas canvas=new Canvas();IconManagementSelection.ColorPlan custom=plan("custom",50);
        ColorFilter first=tint(0xff445566),second=tint(0x80112233);Glyph frame=new Glyph(200,first),background=new Glyph(80,second),fill=new Glyph(161,null);
        LayerDrawable nested=new LayerDrawable(new Drawable[]{background,fill}),outer=new LayerDrawable(new Drawable[]{frame,nested});outer.setBounds(2,4,60,30);outer.setLayerInset(1,3,1,5,2);
        frame.root=background.root=fill.root=outer;IconNativeAppearance.draw(outer,canvas,custom,0xffeeeeee);
        equal(100,frame.drawAlpha);equal(20,background.drawAlpha);equal(81,fill.drawAlpha);equal(200,frame.alpha);equal(80,background.alpha);equal(161,fill.alpha);
        equal(first,frame.filter);equal(second,background.filter);equal(null,fill.filter);equal(5,background.getBounds().left);equal(5,background.getBounds().top);equal(55,background.getBounds().right);equal(28,background.getBounds().bottom);
        for(Glyph glyph:new Glyph[]{frame,background,fill}){equal(true,glyph.guard);equal(true,glyph.rootGuard);equal(false,IconManagementRuntime.nativeDrawing(glyph));}equal(false,IconManagementRuntime.nativeDrawing(outer));
        background.fail=true;try{IconNativeAppearance.draw(outer,canvas,custom);throw new AssertionError("layer failure missing");}catch(IllegalStateException expected){equal("native leaf draw failed",expected.getMessage());}
        equal(200,frame.alpha);equal(80,background.alpha);equal(161,fill.alpha);equal(first,frame.filter);equal(second,background.filter);equal(null,fill.filter);background.fail=false;
        Glyph newer=new Glyph(114,tint(0xff8899aa));nested.setDrawable(0,newer);IconNativeAppearance.draw(outer,canvas,custom);equal(57,newer.drawAlpha);equal(114,newer.alpha);equal(80,background.alpha);
        Glyph shared=new Glyph(200,first);LayerDrawable repeated=new LayerDrawable(new Drawable[]{shared,shared});IconNativeAppearance.draw(repeated,canvas,custom);equal(2,shared.draws);equal(100,shared.drawAlpha);equal(200,shared.alpha);equal(first,shared.filter);
        Glyph wrappedA=new Glyph(200,first),wrappedB=new Glyph(80,second);LayerDrawable body=new LayerDrawable(new Drawable[]{wrappedA,wrappedB});DrawableWrapper clip=new DrawableWrapper(body);clip.setBounds(3,4,59,30);
        IconNativeAppearance.draw(clip,canvas,custom);equal(100,wrappedA.drawAlpha);equal(20,wrappedB.drawAlpha);equal(200,wrappedA.alpha);equal(80,wrappedB.alpha);equal(first,wrappedA.filter);equal(second,wrappedB.filter);equal(3,wrappedA.getBounds().left);equal(59,wrappedB.getBounds().right);
        Glyph replaced=new Glyph(130,first);clip.setDrawable(replaced);IconNativeAppearance.draw(clip,canvas,custom);equal(65,replaced.drawAlpha);equal(130,replaced.alpha);equal(first,replaced.filter);
    }
    @SuppressWarnings("unchecked") private static Map<Drawable,Object> cache()throws Exception{Field field=IconNativeAppearance.class.getDeclaredField("TREES");field.setAccessible(true);return (Map<Drawable,Object>)((ThreadLocal<?>)field.get(null)).get();}
    private static void cacheAndOverlap()throws Exception{
        Canvas canvas=new Canvas();IconManagementSelection.ColorPlan custom=plan("custom",50);Glyph shared=new Glyph(200,tint(0xff112233));LayerDrawable first=new LayerDrawable(new Drawable[]{shared}),second=new LayerDrawable(new Drawable[]{shared});
        shared.event=()->IconNativeAppearance.draw(second,canvas,custom);IconNativeAppearance.draw(first,canvas,custom);equal(2,shared.draws);equal(100,shared.drawAlpha);equal(200,shared.alpha);
        Object cached=cache().get(first);for(int i=0;i<1000;i++)IconNativeAppearance.draw(first,canvas,custom);equal(cached,cache().get(first));equal(200,shared.alpha);
        Field nodes=cached.getClass().getDeclaredField("nodes");nodes.setAccessible(true);
        for(Object node:(Object[])nodes.get(cached))for(String name:new String[]{"current","filter"}){Field slot=node.getClass().getDeclaredField(name);slot.setAccessible(true);equal(null,slot.get(node));}
        IconNativeAppearance.releaseRuntime();equal(0,cache().size());
    }
    private static void recoverNativeTree()throws Exception{
        Canvas canvas=new Canvas();IconManagementSelection.ColorPlan custom=plan("custom",50);
        DrawableWrapper pending=new DrawableWrapper(null);IconNativeAppearance.draw(pending,canvas,custom);
        Object waiting=cache().get(pending);for(int i=0;i<100;i++)IconNativeAppearance.draw(pending,canvas,custom);equal(waiting,cache().get(pending));
        Glyph first=new Glyph(200,tint(0xff112233));pending.setDrawable(first);IconNativeAppearance.draw(pending,canvas,custom);
        equal(100,first.drawAlpha);equal(200,first.alpha);equal(0xff123456,IconManagementRuntime.filterColor(first.drawFilter));
        pending.setDrawable(null);IconNativeAppearance.draw(pending,canvas,custom);
        Glyph second=new Glyph(80,tint(0xff445566));pending.setDrawable(second);IconNativeAppearance.draw(pending,canvas,custom);
        equal(40,second.drawAlpha);equal(80,second.alpha);equal(200,first.alpha);
        Object reused=cache().get(pending);for(int i=0;i<100;i++)IconNativeAppearance.draw(pending,canvas,custom);equal(reused,cache().get(pending));
    }
    public static void main(String[] args)throws Exception{leaves();layers();cacheAndOverlap();recoverNativeTree();System.out.println("IconNativeAppearance checks passed: "+checks);}
}
