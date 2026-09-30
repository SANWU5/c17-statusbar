package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.ComposeShader;
import android.graphics.Shader;
import android.content.res.Configuration;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView;

/** Mask continuity, native draw ownership, API fallback and current OPlus manager integration. */
public final class TilePageEffectsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);
    }
    private static void near(float expected,float actual) {
        checks++;if(Float.isNaN(actual)||Float.isInfinite(actual)||Math.abs(expected-actual)>.0002f)
            throw new AssertionError(expected+" != "+actual);
    }
    private static final class NativeDraw implements TilePageEffects.DrawAction {
        int calls;Canvas canvas;boolean fail;
        public void draw(Canvas canvas) {calls++;this.canvas=canvas;if(fail)throw new IllegalStateException("native failure");}
    }
    public static final class Manager {
        final ParallelCOUIRecyclerView view;boolean horizontal=true;int offset,pageCount=2;
        Manager(ParallelCOUIRecyclerView view) {this.view=view;}
        public View getRecyclerView() {return view;}
        public boolean canScrollHorizontally() {return horizontal;}
        public int getPageCount() {return pageCount;}
        public int[] currScrollOffset() {return new int[]{offset,0};}
    }
    private static Bundle settings(boolean fade,boolean blur,float range,float radius,float strength) {
        Bundle values=new Bundle();values.putBoolean("tiles_fade_enabled",fade);values.putBoolean("tiles_blur_enabled",blur);
        values.putFloat("tiles_fade_range",range);values.putFloat("tiles_blur_radius",radius);values.putFloat("tiles_strength",strength);
        return values;
    }
    private static Canvas draw(TilePageEffects effects,ParallelCOUIRecyclerView view,boolean hardware) throws Throwable {
        Canvas canvas=new Canvas();canvas.hardware=hardware;NativeDraw nativeDraw=new NativeDraw();
        effects.draw(view,canvas,nativeDraw);equal(1,nativeDraw.calls);return canvas;
    }
    private static LinearGradient left(Shader mask){return (LinearGradient)((ComposeShader)((ComposeShader)mask).a).a;}
    private static LinearGradient right(Shader mask){return (LinearGradient)((ComposeShader)((ComposeShader)mask).a).b;}
    public static void main(String[] args) throws Throwable {
        Bundle diagnostics=new Bundle();diagnostics.putBoolean("diagnostics_enabled",false);ModuleDiagnostics.configure(diagnostics);
        for(float offset:new float[]{0,1,2,-1,Float.NaN,Float.POSITIVE_INFINITY})near(0,TilePageEffects.transitionAmount(offset));
        near(1,TilePageEffects.transitionAmount(.5f));near(1,TilePageEffects.transitionAmount(-.5f));
        near(0,TilePageEffects.edgeWeight(500,0,1000,100));near(1,TilePageEffects.edgeWeight(0,0,1000,100));
        near(1,TilePageEffects.edgeWeight(1000,0,1000,100));near(0,TilePageEffects.edgeWeight(100,0,1000,100));
        near(.5f,TilePageEffects.edgeWeight(50,0,1000,100));near(.5f,TilePageEffects.edgeWeight(950,0,1000,100));
        near(0,TilePageEffects.edgeWeight(0,0,1000,0));near(0,TilePageEffects.edgeWeight(0,0,0,100));
        float previous=0f;
        for(int i=0;i<=1000;i++) {
            float offset=i/1000f,value=TilePageEffects.transitionAmount(offset);
            equal(true,value>=0&&value<=1);near(value,TilePageEffects.transitionAmount(-offset));
            near(value,TilePageEffects.transitionAmount(1f-offset));
            if(i<=125){equal(true,value>=previous);previous=value;}
            float x=i;
            near(TilePageEffects.edgeWeight(x,0,1000,100),TilePageEffects.edgeWeight(1000-x,0,1000,100));
        }
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();TilePageEffects effects=new TilePageEffects();
        equal(true,effects.identifies(view));view.resourceName="personal_tiles_container";equal(false,effects.identifies(view));
        view.resourceName="other_tiles_container";equal(false,effects.identifies(new View(new android.content.Context())));
        equal(0,draw(effects,view,true).layers);
        effects.onPageScroll(view,.25f);equal(0,draw(effects,view,true).layers);
        effects.onScrollState(view,1);Build.VERSION.SDK_INT=30;
        Canvas fade=draw(effects,view,true);equal(1,fade.layers);equal(2,fade.rects);equal(255,fade.effectAlpha);
        Shader mask=fade.lastEffectShader;near(140,left(mask).left);near(1300,right(mask).right);
        near(0,mask.alpha(140,0));near(1,mask.alpha(140,128));near(0,mask.alpha(140,1600));
        near(0,mask.alpha(500,800));near(0,mask.alpha(140,-200));near(0,mask.alpha(140,1800));
        effects.onPageScroll(view,0f);equal(0,draw(effects,view,true).layers);
        effects.onPageScroll(view,.02f);equal(true,draw(effects,view,true).effectAlpha<255);
        effects.onPageScroll(view,.25f);
        Bundle off=settings(true,true,24,8,100);off.putBoolean("tiles_enabled",false);effects.configure(off);equal(0,draw(effects,view,true).layers);
        effects.configure(settings(true,true,24,8,0));equal(0,draw(effects,view,true).layers);
        effects.configure(settings(true,true,0,8,100));equal(0,draw(effects,view,true).layers);
        effects.configure(settings(false,false,24,8,100));equal(0,draw(effects,view,true).layers);
        effects.configure(settings(false,true,24,8,100));equal(0,draw(effects,view,false).layers);
        effects.configure(settings(true,true,24,8,50));equal(128,draw(effects,view,false).effectAlpha);
        effects.configure(settings(true,true,24,8,100));Build.VERSION.SDK_INT=35;
        RenderNode.nodes.clear();NativeDraw nativeDraw=new NativeDraw();Canvas hardware=new Canvas();hardware.hardware=true;
        effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);equal(true,nativeDraw.canvas instanceof RecordingCanvas);
        equal(2,RenderNode.nodes.size());equal(3,hardware.layers);equal(2,hardware.nodeDraws);equal(4,hardware.rects);
        RenderNode source=RenderNode.nodes.get(0),blurred=RenderNode.nodes.get(1);
        equal(1636,source.width);equal(2000,source.height);near(32,blurred.effect.radius);
        equal(-98,source.left);equal(-200,source.top);equal(Shader.TileMode.DECAL,blurred.effect.tileMode);
        equal(1,source.recordings);equal(1,blurred.recordings);
        view.width=2400;view.height=900;view.paddingLeft=72;view.paddingRight=100;
        hardware=draw(effects,view,true);equal(2,RenderNode.nodes.size());equal(2596,source.width);equal(1300,source.height);
        mask=hardware.lastEffectShader;near(72,left(mask).left);near(2300,right(mask).right);
        view.scrollX=720;view.scrollY=14;hardware=new Canvas();hardware.hardware=true;hardware.clipBounds=new android.graphics.Rect(720,14,3120,914);
        nativeDraw=new NativeDraw();effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);
        near(98-720,source.recording.translateX);near(200-14,source.recording.translateY);
        near(720,hardware.translateX);near(14,hardware.translateY);
        view.scrollX=0;view.scrollY=0;
        effects.configure(settings(true,true,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE));
        hardware=draw(effects,view,true);near(128,blurred.effect.radius);
        mask=hardware.lastEffectShader;equal(true,mask.alpha(1200,450)>=0&&mask.alpha(1200,450)<=1);
        equal(Shader.TileMode.DECAL,blurred.effect.tileMode);equal(-386,source.left);equal(-386,source.top);
        effects.configure(settings(false,true,24,8,100));hardware=draw(effects,view,true);equal(3,hardware.rects);
        effects.configure(settings(true,false,24,8,100));equal(true,source.discards>0);equal(1,draw(effects,view,true).layers);
        effects.configure(settings(true,true,24,8,100));RenderEffect.fail=true;
        fade=draw(effects,view,true);equal(1,fade.layers);equal(0,fade.nodeDraws);RenderEffect.fail=false;
        effects.configure(settings(true,true,24,8,100));RenderNode.failBegin=true;
        hardware=draw(effects,view,true);equal(0,hardware.layers);RenderNode.failBegin=false;
        effects.configure(settings(true,true,24,8,100));hardware=new Canvas();hardware.hardware=true;hardware.failNodeOnce="Blur";
        nativeDraw=new NativeDraw();effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);equal(1,hardware.clears);
        equal(1,draw(effects,view,true).layers);
        effects.configure(settings(true,true,24,8,100));hardware=new Canvas();hardware.hardware=true;hardware.failMaskOnce=true;
        nativeDraw=new NativeDraw();effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);equal(1,hardware.clears);
        effects.configure(settings(true,true,24,8,100));hardware=new Canvas();hardware.hardware=true;hardware.failClipOnce=true;
        nativeDraw=new NativeDraw();effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);equal(1,hardware.nodeDraws);equal(1,hardware.clips);
        effects.configure(settings(true,true,24,8,100));hardware=new Canvas();hardware.hardware=true;hardware.failLayerOnce=true;
        nativeDraw=new NativeDraw();effects.draw(view,hardware,nativeDraw);equal(1,nativeDraw.calls);equal(1,hardware.nodeDraws);
        effects.configure(settings(true,true,24,8,100));
        view.overflow=true;equal(3,draw(effects,view,true).layers);view.overflow=false;
        view.scrollOverflow=true;equal(3,draw(effects,view,true).layers);view.scrollOverflow=false;
        View overflowChild=new View(new android.content.Context()) {@Override public Object getTag(int key){return key==3?Boolean.TRUE:null;}};
        view.addView(overflowChild);view.overflow=true;equal(0,draw(effects,view,true).layers);
        overflowChild.setVisibility(View.INVISIBLE);equal(3,draw(effects,view,true).layers);view.overflow=false;
        effects.onScrollState(view,0);equal(0,draw(effects,view,true).layers);
        effects.onScrollState(view,2);Manager manager=new Manager(view);manager.offset=-600;effects.onLayoutManagerScroll(manager);
        equal(true,draw(effects,view,true).layers>0);manager.offset=0;effects.onLayoutManagerScroll(manager);equal(0,draw(effects,view,true).layers);
        manager.offset=Integer.MIN_VALUE;effects.onLayoutManagerScroll(manager);equal(true,draw(effects,view,true).layers>0);
        view.measuredWidth=1200;manager.offset=1200;effects.onLayoutManagerScroll(manager);equal(0,draw(effects,view,true).layers);view.measuredWidth=0;
        manager.pageCount=1;manager.offset=600;effects.onLayoutManagerScroll(manager);equal(0,draw(effects,view,true).layers);manager.pageCount=2;
        manager.horizontal=false;effects.onLayoutManagerScroll(manager);equal(0,draw(effects,view,true).layers);
        effects.onScrollState(view,1);effects.onPageScroll(view,.5f);view.width=9000;equal(0,draw(effects,view,true).layers);
        view.width=2400;view.attached=false;equal(0,draw(effects,view,true).layers);view.attached=true;
        nativeDraw=new NativeDraw();nativeDraw.fail=true;view.width=9000;
        try{effects.draw(view,new Canvas(),nativeDraw);throw new AssertionError("Expected native error");}
        catch(IllegalStateException expected){equal(1,nativeDraw.calls);}
        view.width=2400;effects.detach(view);equal(0,draw(effects,view,true).layers);
        liveNativeState();
        softMasks();orientationGates();diagnosticsRearm();nativeSplitCropGuard();safeViewportFence();pureFadeComposition();
        System.out.println("TilePageEffectsCheck passed: "+checks);
    }
    private static void diagnosticsRearm() throws Throwable {
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();TilePageEffects effects=new TilePageEffects();
        Build.VERSION.SDK_INT=35;
        Bundle values=settings(true,true,24,8,100);values.putBoolean("diagnostics_enabled",false);effects.configure(values);
        effects.onScrollState(view,1);effects.onPageScroll(view,.5f);equal(3,draw(effects,view,true).layers);
        java.lang.reflect.Field entries=TilePageEffects.class.getDeclaredField("entries");entries.setAccessible(true);
        Object entry=((java.util.Map<?,?>)entries.get(effects)).get(view);
        String[] names={"loggedBlur","loggedFade","loggedOverflow","loggedError","loggedNativeError","loggedClip"};
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);flag.setBoolean(entry,true);}
        java.lang.reflect.Field failed=entry.getClass().getDeclaredField("blurFailed");failed.setAccessible(true);failed.setBoolean(entry,true);
        effects.configure(values);equal(false,failed.getBoolean(entry));
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(true,flag.getBoolean(entry));}
        values.putBoolean("diagnostics_enabled",true);failed.setBoolean(entry,true);effects.configure(values);equal(false,failed.getBoolean(entry));
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(false,flag.getBoolean(entry));}
        equal(3,draw(effects,view,true).layers);
        for(String name:new String[]{"loggedBlur","loggedNativeError"}){
            java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(true,flag.getBoolean(entry));
        }
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);flag.setBoolean(entry,true);}
        effects.configure(values);
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(true,flag.getBoolean(entry));}
        values.putBoolean("diagnostics_enabled",false);effects.configure(values);
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(true,flag.getBoolean(entry));}
        values.putBoolean("diagnostics_enabled",true);effects.configure(values);
        for(String name:names){java.lang.reflect.Field flag=entry.getClass().getDeclaredField(name);flag.setAccessible(true);equal(false,flag.getBoolean(entry));}
        effects.detach(view);
    }
    private static void softMasks() throws Throwable {
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();TilePageEffects effects=new TilePageEffects();
        effects.onScrollState(view,1);effects.onPageScroll(view,.5f);Build.VERSION.SDK_INT=30;
        Bundle values=settings(true,true,52.83f,15.32f,100);
        effects.configure(values);Shader mask=draw(effects,view,true).lastEffectShader;
        near(52.83f*4,left(mask).right-left(mask).left);near(52.83f*4,right(mask).right-right(mask).left);
        java.util.Map<String,Object> old=new java.util.HashMap<>();old.put("tiles_fade_range",52.83f);
        near(52.83f,StatusBarSettings.settingNumber(old,"tiles_left_range",24));
        near(52.83f,StatusBarSettings.settingNumber(old,"tiles_right_range",24));
        old.put("tiles_left_range",0f);near(0,StatusBarSettings.settingNumber(old,"tiles_left_range",24));
        near(52.83f,StatusBarSettings.settingNumber(old,"tiles_right_range",24));
        values.putFloat("tiles_left_range",10);values.putFloat("tiles_right_range",20);
        values.putFloat("tiles_left_offset_x",-5);values.putFloat("tiles_right_offset_x",8);
        values.putFloat("tiles_offset_y",25);values.putFloat("tiles_region_height",100);values.putFloat("tiles_vertical_feather",10);
        effects.configure(values);mask=draw(effects,view,true).lastEffectShader;
        near(120,left(mask).left);near(160,left(mask).right);near(1252,right(mask).left);near(1332,right(mask).right);
        near(0,mask.alpha(120,100));near(1,mask.alpha(120,140));near(1,mask.alpha(120,460));
        near(0,mask.alpha(120,500));near(0,mask.alpha(120,99));near(0,mask.alpha(120,501));
        near(128f/255f,mask.alpha(120,120));near(0,mask.alpha(500,300));
        for(int i=0;i<400;i++) {
            float y=100+i;
            equal(true,mask.alpha(120,y)>=0&&mask.alpha(120,y)<=1);
            equal(true,Math.abs(mask.alpha(120,y+.01f)-mask.alpha(120,y))<.001f);
        }
        values.putFloat("tiles_left_range",0);effects.configure(values);mask=draw(effects,view,true).lastEffectShader;
        near(0,mask.alpha(120,300));near(1,mask.alpha(1332,300));
        values.putFloat("tiles_right_range",0);effects.configure(values);equal(0,draw(effects,view,true).layers);
        values.putFloat("tiles_left_range",24);effects.configure(values);
        Build.VERSION.SDK_INT=26;Canvas fallback=draw(effects,view,true);equal(2,fallback.layers);equal(4,fallback.rects);
        Build.VERSION.SDK_INT=35;effects.configure(values);RenderNode.nodes.clear();draw(effects,view,true);
        RenderNode source=RenderNode.nodes.get(0),blur=RenderNode.nodes.get(1);near(61.28f,blur.effect.radius);
        equal(Shader.TileMode.DECAL,blur.effect.tileMode);equal(-186,source.left);equal(-200,source.top);
        view.width=Integer.MAX_VALUE;equal(0,draw(effects,view,true).layers);view.width=1440;
        values.putFloat("tiles_region_height",Float.MIN_VALUE);effects.configure(values);equal(0,draw(effects,view,true).layers);
        effects.detach(view);
    }
    private static void orientationGates() throws Throwable {
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();view.managerPresent=true;
        Manager manager=new Manager(view);view.nativeManager=manager;manager.offset=360;view.nativeScrollState=1;
        TilePageEffects effects=new TilePageEffects();Bundle values=settings(true,true,24,8,100);
        values.putBoolean("tiles_portrait_enabled",false);values.putBoolean("tiles_landscape_enabled",true);effects.configure(values);
        view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        RenderNode.nodes.clear();equal(0,draw(effects,view,true).layers);equal(0,RenderNode.nodes.size());
        view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        equal(3,draw(effects,view,true).layers);RenderNode source=RenderNode.nodes.get(0);
        view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
        equal(0,draw(effects,view,true).layers);equal(1,source.discards);
        values.putBoolean("tiles_portrait_enabled",true);values.putBoolean("tiles_landscape_enabled",false);effects.configure(values);
        equal(3,draw(effects,view,true).layers);
        view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_LANDSCAPE;
        equal(0,draw(effects,view,true).layers);
        view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_UNDEFINED;
        view.width=800;view.height=1000;equal(3,draw(effects,view,true).layers);
        view.width=1800;view.height=800;equal(0,draw(effects,view,true).layers);
        View root=new View(new android.content.Context()) {@Override public int getWidth(){return 900;}@Override public int getHeight(){return 1800;}};
        view.parent=root;equal(3,draw(effects,view,true).layers);
        values.putBoolean("tiles_portrait_enabled",false);effects.configure(values);equal(0,draw(effects,view,true).layers);
        effects.detach(view);
    }
    private static void liveNativeState() throws Throwable {
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();
        view.resourceName="other_tiles_land_left_container";view.managerPresent=true;
        Manager manager=new Manager(view);view.nativeManager=manager;view.nativeScrollState=1;manager.offset=360;
        TilePageEffects effects=new TilePageEffects();equal(true,effects.identifies(view));
        int invalidations=view.invalidations;
        equal(true,draw(effects,view,true).layers>0);equal(invalidations,view.invalidations);
        for(int i=0;i<10;i++) {
            manager.offset=360+i*10;equal(true,draw(effects,view,true).layers>0);
            equal(invalidations,view.invalidations);
        }
        view.nativeScrollState=0;manager.offset=450;equal(0,draw(effects,view,true).layers);
        manager.offset=-450;equal(0,draw(effects,view,true).layers);
        manager.offset=1440;equal(0,draw(effects,view,true).layers);
        view.nativeScrollState=2;manager.offset=2880;equal(0,draw(effects,view,true).layers);
        manager.pageCount=1;manager.offset=450;equal(0,draw(effects,view,true).layers);manager.pageCount=2;
        manager.horizontal=false;equal(0,draw(effects,view,true).layers);manager.horizontal=true;
        view.measuredWidth=720;manager.offset=720;equal(0,draw(effects,view,true).layers);
        manager.offset=360;equal(true,draw(effects,view,true).layers>0);view.measuredWidth=0;
        view.width=0;equal(0,draw(effects,view,true).layers);view.width=1440;
        view.nativeManager=null;equal(0,draw(effects,view,true).layers);
        view.nativeManager=new Object();equal(0,draw(effects,view,true).layers);
        view.nativeManager=manager;equal(true,draw(effects,view,true).layers>0);
        java.lang.reflect.Field entries=TilePageEffects.class.getDeclaredField("entries");entries.setAccessible(true);
        Object entry=((java.util.Map<?,?>)entries.get(effects)).get(view);
        for(java.lang.reflect.Field field:entry.getClass().getDeclaredFields()) {
            field.setAccessible(true);Object cached=field.get(entry);
            equal(false,cached instanceof View);equal(false,cached instanceof Manager);
        }
        equal(invalidations,view.invalidations);effects.detach(view);
        equal(true,draw(effects,view,true).layers>0);equal(invalidations,view.invalidations);
    }

    private static void nativeSplitCropGuard() throws Throwable {
        Build.VERSION.SDK_INT=35;
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();TilePageEffects effects=new TilePageEffects();
        effects.configure(settings(true,true,24,8,100));effects.onScrollState(view,1);effects.onPageScroll(view,.5f);
        for(boolean nullOriginal:new boolean[]{false,true}) {
            Object originalSplit=nullOriginal?null: new Object();Object originalScroll=nullOriginal?null:new Object();
            view.setTag(4,originalSplit);view.setTag(2,originalScroll);
            int[] calls={0};Canvas target=new Canvas();target.hardware=true;
            effects.draw(view,target,recording -> {
                calls[0]++;equal(false,view.getTag(4));equal(true,view.getTag(2));
                equal(true,recording instanceof RecordingCanvas);
            });
            equal(1,calls[0]);equal(true,view.getTag(4)==originalSplit);equal(true,view.getTag(2)==originalScroll);
            calls[0]=0;target=new Canvas();target.hardware=true;
            try {
                effects.draw(view,target,recording -> {calls[0]++;equal(false,view.getTag(4));equal(true,view.getTag(2));throw new IllegalStateException("native failure");});
                throw new AssertionError("Expected native error");
            } catch(IllegalStateException expected) {equal(1,calls[0]);}
            equal(true,view.getTag(4)==originalSplit);equal(true,view.getTag(2)==originalScroll);
        }
        for(int mode=0;mode<8;mode++) {
            Bundle values=settings(true,true,24,8,100);Canvas target=new Canvas();target.hardware=true;
            Build.VERSION.SDK_INT=35;view.getResources().getConfiguration().orientation=Configuration.ORIENTATION_PORTRAIT;
            if(mode==0)values.putBoolean("tiles_enabled",false);
            if(mode==1)values.putBoolean("tiles_blur_enabled",false);
            if(mode==2)values.putFloat("tiles_blur_radius",0);
            if(mode==3)values.putFloat("tiles_strength",0);
            if(mode==4)values.putBoolean("tiles_portrait_enabled",false);
            if(mode==5)target.hardware=false;
            if(mode==6)Build.VERSION.SDK_INT=30;
            if(mode==7)RenderNode.failBegin=true;
            effects.configure(values);view.setTag(4,Boolean.TRUE);view.setTag(2,Boolean.FALSE);
            int[] calls={0};
            boolean isolatedFade=mode==1||mode==2||mode==5||mode==6;
            effects.draw(view,target,recording -> {calls[0]++;equal(!isolatedFade,view.getTag(4));equal(isolatedFade,view.getTag(2));});
            equal(1,calls[0]);equal(true,view.getTag(4));equal(false,view.getTag(2));RenderNode.failBegin=false;
        }
        Build.VERSION.SDK_INT=35;effects.detach(view);
    }

    private static void safeViewportFence() throws Throwable {
        Build.VERSION.SDK_INT=35;
        ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();view.managerPresent=true;
        Manager manager=new Manager(view);view.nativeManager=manager;view.nativeScrollState=1;manager.offset=360;
        TilePageEffects effects=new TilePageEffects();Bundle values=settings(true,true,52.83f,15.32f,100);
        values.putFloat("tiles_offset_y",-200);values.putFloat("tiles_region_height",653.33f);
        effects.configure(values);Canvas canvas=draw(effects,view,true);Shader fence=canvas.lastFenceShader;
        equal(true,fence!=null);equal(1,canvas.clips);equal(1,canvas.getSaveCount());
        near(140,canvas.clipLeft);near(1300,canvas.clipRight);near(0,canvas.clipTop);near(1600,canvas.clipBottom);
        near(140,canvas.layerLeft);near(1300,canvas.layerRight);near(0,canvas.layerTop);near(1600,canvas.layerBottom);
        near(0,fence.alpha(140,800));near(0,fence.alpha(1300,800));near(1,fence.alpha(720,0));near(1,fence.alpha(720,1600));
        near(0,fence.alpha(-186,800));near(0,fence.alpha(1626,800));near(1,fence.alpha(720,-200));near(1,fence.alpha(720,1800));
        near(1,fence.alpha(720,800));
        view.setClipToPadding(false);view.paddingTop=16;view.paddingBottom=24;
        Canvas unboundedNative=draw(effects,view,true);Shader paddedFence=unboundedNative.lastFenceShader;
        equal(false,view.getClipToPadding());near(140,unboundedNative.clipLeft);near(1300,unboundedNative.clipRight);
        near(16,unboundedNative.clipTop);near(1576,unboundedNative.clipBottom);
        near(0,paddedFence.alpha(139,800));near(0,paddedFence.alpha(1301,800));
        near(1,paddedFence.alpha(720,15));near(1,paddedFence.alpha(720,1577));
        near(0,paddedFence.alpha(140,800));near(0,paddedFence.alpha(1300,800));
        near(1,paddedFence.alpha(720,16));near(1,paddedFence.alpha(720,1576));
        equal(1,unboundedNative.getSaveCount());
        view.setClipToPadding(true);view.paddingTop=0;view.paddingBottom=0;
        for(int i=0;i<200;i++) {
            float x=140+i;equal(true,fence.alpha(x,800)>=0&&fence.alpha(x,800)<=1);
            equal(true,Math.abs(fence.alpha(x+.01f,800)-fence.alpha(x,800))<.001f);
        }
        values.putFloat("tiles_strength",.01f);values.putFloat("tiles_offset_y",100000);values.putFloat("tiles_region_height",100000);
        effects.configure(values);canvas=draw(effects,view,true);Shader weakFence=canvas.lastFenceShader;
        near(fence.alpha(200,800),weakFence.alpha(200,800));near(0,weakFence.alpha(139,800));near(0,weakFence.alpha(1301,800));
        manager.offset=1;canvas=draw(effects,view,true);Shader firstFrame=canvas.lastFenceShader;
        near(0,firstFrame.alpha(140,800));near(0,firstFrame.alpha(139,800));near(1,firstFrame.alpha(144,800));
        near(1,firstFrame.alpha(200,800));equal(true,firstFrame.alpha(142,800)>fence.alpha(142,800));
        near(1,firstFrame.alpha(720,0));near(1,firstFrame.alpha(720,2));
        manager.offset=1439;canvas=draw(effects,view,true);Shader lastFrame=canvas.lastFenceShader;
        near(0,lastFrame.alpha(1300,800));near(1,lastFrame.alpha(1296,800));near(1,lastFrame.alpha(1200,800));
        manager.offset=360;
        for(int incomingTop:new int[]{0,300,500}) {
            Canvas partial=new Canvas();partial.hardware=true;
            partial.clipBounds=new android.graphics.Rect(400,incomingTop,1000,1200);
            NativeDraw nativeDraw=new NativeDraw();effects.draw(view,partial,nativeDraw);equal(1,nativeDraw.calls);
            near(140,left(partial.lastEffectShader).left);near(1300,right(partial.lastEffectShader).right);
            near(140,partial.clipLeft);near(1300,partial.clipRight);near(0,partial.clipTop);near(1600,partial.clipBottom);
            near(400,partial.effectiveClipLeft);near(1000,partial.effectiveClipRight);
            near(incomingTop,partial.effectiveClipTop);near(1200,partial.effectiveClipBottom);
            near(1,partial.lastFenceShader.alpha(720,incomingTop));near(0,partial.lastEffectShader.alpha(720,incomingTop));
            near(1,partial.lastFenceShader.alpha(400,incomingTop));
        }
        for(int mode=0;mode<5;mode++) {
            effects.configure(settings(true,true,52.83f,15.32f,100));Canvas failed=new Canvas();failed.hardware=true;
            if(mode==0)failed.failNodeOnce="Blur";
            if(mode==1)failed.failMaskOnce=true;
            if(mode==2)failed.failLayerOnce=true;
            if(mode==3)failed.failClipOnce=true;
            if(mode==4)RenderNode.failBeginOnce="Blur";
            NativeDraw nativeDraw=new NativeDraw();effects.draw(view,failed,nativeDraw);equal(1,nativeDraw.calls);
            equal(1,failed.getSaveCount());equal(true,failed.lastFenceShader!=null);
            near(0,failed.lastFenceShader.alpha(139,800));near(0,failed.lastFenceShader.alpha(1301,800));
            near(140,failed.clipLeft);near(1300,failed.clipRight);
        }
        effects.configure(settings(true,true,52.83f,15.32f,100));view.nativeScrollState=0;
        for(int idleOffset:new int[]{1,450,-450,1439,1450,Integer.MAX_VALUE}) {
            manager.offset=idleOffset;NativeDraw nativeDraw=new NativeDraw();Canvas idle=new Canvas();idle.hardware=true;
            effects.draw(view,idle,nativeDraw);equal(1,nativeDraw.calls);equal(0,idle.layers);equal(0,idle.clips);equal(0,idle.nodeDraws);
            equal(true,nativeDraw.canvas==idle);
        }
        effects.detach(view);
    }

    private static void pureFadeComposition() throws Throwable {
        for(int sdk:new int[]{26,27,28,30,35})for(boolean hardware:new boolean[]{false,true}) {
            Build.VERSION.SDK_INT=sdk;RenderNode.nodes.clear();
            ParallelCOUIRecyclerView view=new ParallelCOUIRecyclerView();view.managerPresent=true;view.setClipToPadding(false);
            view.paddingTop=16;view.paddingBottom=24;
            Manager manager=new Manager(view);view.nativeManager=manager;view.nativeScrollState=1;manager.offset=360;
            view.getResources().getConfiguration().orientation=hardware?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
            view.resourceName=hardware?"other_tiles_land_left_container":"other_tiles_container";
            TilePageEffects effects=new TilePageEffects();Bundle values=settings(true,false,52.83f,15.32f,100);
            values.putFloat("tiles_offset_y",-200);values.putFloat("tiles_region_height",653.33f);effects.configure(values);
            Object split=new Object(),scroll=new Object();view.setTag(4,split);view.setTag(2,scroll);
            Canvas canvas=new Canvas();canvas.hardware=hardware;int[] calls={0};Canvas expected=canvas;
            effects.draw(view,canvas,nativeCanvas -> {
                calls[0]++;equal(true,nativeCanvas==expected);equal(false,view.getTag(4));equal(true,view.getTag(2));
            });
            equal(1,calls[0]);equal(true,view.getTag(4)==split);equal(true,view.getTag(2)==scroll);
            equal(0,RenderNode.nodes.size());equal(0,canvas.nodeDraws);equal(sdk<28?2:1,canvas.layers);
            equal(sdk<28?3:1,canvas.effectRects);equal(1,canvas.fenceRects);equal(1,canvas.getSaveCount());
            near(140,canvas.clipLeft);near(1300,canvas.clipRight);near(16,canvas.clipTop);near(1576,canvas.clipBottom);
            near(0,canvas.lastFenceShader.alpha(139,800));near(0,canvas.lastFenceShader.alpha(140,800));
            near(0,canvas.lastFenceShader.alpha(1300,800));near(0,canvas.lastFenceShader.alpha(1301,800));
            near(1,canvas.lastFenceShader.alpha(720,16));near(1,canvas.lastFenceShader.alpha(720,1576));
            near(1,canvas.lastFenceShader.alpha(248,800));near(1,canvas.lastFenceShader.alpha(720,800));
            for(int failure=0;failure<4;failure++) {
                Canvas failed=new Canvas();failed.hardware=hardware;
                if(failure==0)failed.failLayerOnce=true;
                if(failure==1)failed.failClipOnce=true;
                if(failure==2)failed.failMaskOnce=true;
                if(failure==3)failed.failFenceOnce=true;
                int[] count={0};boolean prepared=failure>=2;
                effects.draw(view,failed,nativeCanvas -> {
                    count[0]++;equal(true,nativeCanvas==failed);
                    equal(true,prepared?Boolean.FALSE.equals(view.getTag(4)):view.getTag(4)==split);
                    equal(true,prepared?Boolean.TRUE.equals(view.getTag(2)):view.getTag(2)==scroll);
                });
                equal(1,count[0]);equal(1,failed.getSaveCount());equal(true,view.getTag(4)==split);equal(true,view.getTag(2)==scroll);
                if(failure==2){equal(true,failed.lastFenceShader!=null);near(0,failed.lastFenceShader.alpha(139,800));}
                if(failure==3)equal(1,failed.clears);
            }
            Canvas thrown=new Canvas();thrown.hardware=hardware;int[] count={0};
            try {
                effects.draw(view,thrown,nativeCanvas -> {count[0]++;equal(false,view.getTag(4));equal(true,view.getTag(2));throw new IllegalStateException("native failure");});
                throw new AssertionError("Expected native failure");
            } catch(IllegalStateException expectedFailure) {equal(1,count[0]);}
            equal(true,view.getTag(4)==split);equal(true,view.getTag(2)==scroll);equal(1,thrown.getSaveCount());equal(1,thrown.clears);
            view.nativeScrollState=0;manager.offset=450;Canvas idle=draw(effects,view,hardware);
            equal(0,idle.layers);equal(0,idle.clips);equal(true,view.getTag(4)==split);equal(true,view.getTag(2)==scroll);
            view.nativeScrollState=1;values.putBoolean("tiles_enabled",false);effects.configure(values);Canvas off=draw(effects,view,hardware);
            equal(0,off.layers);equal(0,off.clips);equal(true,view.getTag(4)==split);equal(true,view.getTag(2)==scroll);
            effects.detach(view);
        }
        Build.VERSION.SDK_INT=35;
    }
}
