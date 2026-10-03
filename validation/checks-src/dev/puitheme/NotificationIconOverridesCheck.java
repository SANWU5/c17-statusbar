package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Resources;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.android.systemui.statusbar.notification.collection.BundleEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import java.util.*;
import java.lang.reflect.Field;

public final class NotificationIconOverridesCheck {
    static int checks;
    static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
    static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);}
    static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static Bundle settings(String rules){Bundle b=new Bundle();b.putBoolean(NotificationIconOverrides.MASTER,true);b.putString(NotificationIconOverrides.RULES,rules);return b;}
    static void invalid(String json){eq(true,NotificationIconOverrides.validationError(json)!=null);}
    static void parser()throws Exception{
        eq(false,NotificationIconOverrides.booleanDefaults().get(NotificationIconOverrides.MASTER));
        eq("[]",NotificationIconOverrides.stringDefaults().get(NotificationIconOverrides.RULES));
        String json="[]";json=NotificationIconOverrides.put(json,"com.example.app","❤️",true);
        json=NotificationIconOverrides.put(json,"android","系统",false);
        eq(2,NotificationIconOverrides.rules(json).size());eq("❤️",NotificationIconOverrides.rules(json).get(0).text);
        eq(false,NotificationIconOverrides.rules(json).get(1).enabled);
        json=NotificationIconOverrides.put(json,"com.example.app","消息",false);eq(2,NotificationIconOverrides.rules(json).size());
        eq("消息",NotificationIconOverrides.rules(json).get(0).text);
        eq(json,NotificationIconOverrides.encode(NotificationIconOverrides.rules(json)));
        eq(1,NotificationIconOverrides.rules(NotificationIconOverrides.remove(json,"com.example.app")).size());
        eq(json,NotificationIconOverrides.remove(json,"not.present"));
        String appIcon=NotificationIconOverrides.putAppIcon(json,"com.target.app","com.source.app",true);
        NotificationIconOverrides.Rule image=NotificationIconOverrides.rules(appIcon).get(2);
        eq(true,image.isAppIcon());eq("appIcon",image.type);eq("com.source.app",image.sourcePackage);eq("",image.text);
        eq("消息",NotificationIconOverrides.rules(appIcon).get(0).text);eq(false,NotificationIconOverrides.rules(appIcon).get(0).enabled);
        eq(appIcon,NotificationIconOverrides.encode(NotificationIconOverrides.rules(appIcon)));
        eq("android",NotificationIconOverrides.rules(NotificationIconOverrides.putAppIcon(appIcon,"com.target.app","android",false)).get(2).sourcePackage);
        eq(false,NotificationIconOverrides.rules(NotificationIconOverrides.putAppIcon(appIcon,"com.target.app","android",false)).get(2).enabled);
        eq(null,NotificationIconOverrides.validationError("[{\"package\":\"android\",\"text\":\"👨‍👩‍👧‍👦\"}]"));
        for(String bad:new String[]{"{}","null","[1]","[{\"package\":\"bad pkg\",\"text\":\"x\"}]",
                "[{\"package\":\"com.test\",\"text\":\"x\",\"enabled\":\"true\"}]",
                "[{\"package\":\"com.test\",\"text\":\"x\",\"unknown\":1}]",
                "[{\"package\":\"com.test\",\"text\":\"x\"},{\"package\":\"com.test\",\"text\":\"y\"}]",
                "[{\"package\":\"com.test\",\"text\":\"123456789\"}]",
                "[{\"package\":\"com.test\",\"text\":\"\\n\"}]",
                "[{\"package\":\"com.test\",\"text\":\"\\uD800\"}]",
                "[{\"package\":\"com.test\",\"text\":\"\\u200D\\uFE0F\"}]",
                "[{\"package\":\"com.test\",\"type\":\"appIcon\",\"sourcePackage\":\"../com.source\"}]",
                "[{\"package\":\"com.test\",\"type\":\"appIcon\",\"sourcePackage\":\"content://com.source\"}]",
                "[{\"package\":\"com.test\",\"type\":\"appIcon\",\"sourcePackage\":\"C:\\\\image.png\"}]",
                "[{\"package\":\"com.test\",\"type\":\"appIcon\",\"sourcePackage\":\"com.source\",\"text\":\"x\"}]",
                "[{\"package\":\"com.test\",\"type\":\"text\",\"sourcePackage\":\"com.source\",\"text\":\"x\"}]",
                "[{\"package\":\"com.test\",\"type\":\"image\",\"sourcePackage\":\"com.source\"}]",
                "[{\"package\":\"com.test\",\"type\":\"appIcon\"}]"})invalid(bad);
        List<NotificationIconOverrides.Rule> many=new ArrayList<>();for(int i=0;i<128;i++)many.add(new NotificationIconOverrides.Rule("com.fixture.app"+i,"x",true));
        eq(128,NotificationIconOverrides.rules(NotificationIconOverrides.encode(many)).size());
        many.add(new NotificationIconOverrides.Rule("com.fixture.extra","x",true));boolean rejected=false;
        try{NotificationIconOverrides.encode(many);}catch(IllegalArgumentException expected){rejected=true;}eq(true,rejected);

        NotificationIconOverrides overrides=new NotificationIconOverrides();Bundle b=settings(NotificationIconOverrides.put("[]","com.example.app","X",true));
        eq(true,overrides.configure(b));long revision=overrides.revision();eq("X",overrides.match("com.example.app").text);
        eq(false,overrides.configure(b));eq(revision,overrides.revision());eq(null,overrides.match("com.other"));
        NotificationIconOverrides sources=new NotificationIconOverrides();
        sources.configure(settings(NotificationIconOverrides.putAppIcon("[]","com.target.app","com.source.app",false)));
        eq(Collections.emptyList(),sources.sourcePackages());eq(null,sources.match("com.target.app"));
        sources.configure(settings(NotificationIconOverrides.putAppIcon("[]","com.target.app","com.source.app",true)));
        eq(Collections.singletonList("com.source.app"),sources.sourcePackages());sources.releaseRuntime();eq(Collections.emptyList(),sources.sourcePackages());
        b.putBoolean(NotificationIconOverrides.MASTER,false);eq(true,overrides.configure(b));eq(null,overrides.match("com.example.app"));
        b.putBoolean(NotificationIconOverrides.MASTER,true);overrides.configure(b);eq(true,overrides.enabled());
        b.putBoolean(StatusBarSettings.SAFE_MODE,true);overrides.configure(b);eq(false,overrides.enabled());
        b.putBoolean(StatusBarSettings.SAFE_MODE,false);overrides.configure(b);eq(true,overrides.enabled());
        b.putString(NotificationIconOverrides.RULES,"[broken]");overrides.configure(b);eq(false,overrides.enabled());
        eq(false,overrides.configure(b));eq(false,overrides.enabled()); // Repeated malformed import cannot re-enable a partial map.
        overrides.releaseRuntime();eq(false,overrides.configure(settings("[]")));eq(false,overrides.enabled());
    }
    static final class ScopedResources extends Resources{
        @Override public String getResourceEntryName(int id){return id==101?"notificationIcons":id==202?"status_bar":"aod_icons";}
    }
    static final class Owner extends NotificationIconContainer{
        final ScopedResources resources=new ScopedResources();Owner(){super(null);resources.getDisplayMetrics().density=4f;}
        @Override public int getId(){return 101;}@Override public Resources getResources(){return resources;}
    }
    static final class Host extends ViewGroup{
        final int id;Host(int id){super(null);this.id=id;}
        @Override public int getId(){return id;}@Override public Resources getResources(){return new ScopedResources();}
    }
    static StatusBarIconView icon(Owner owner,String pkg){
        StatusBarIconView icon=new StatusBarIconView(null);
        GradientDrawable drawable=new GradientDrawable();drawable.setBounds(0,0,512,512);icon.setImageDrawable(drawable);
        Matrix matrix=new Matrix();matrix.setScale(.125f,.125f);icon.setImageMatrix(matrix);icon.nativeScale=.5f;
        StatusBarNotification sbn=new StatusBarNotification(1);sbn.packageName=pkg;icon.setNotification(sbn);owner.addView(icon);return icon;
    }
    static final class NativeCanvas extends Canvas{
        final ArrayDeque<float[]> saved=new ArrayDeque<>();float glyphHeight,dotSize,effectiveText;
        @Override public int save(){saved.push(new float[]{scaleX,scaleY});return super.save();}
        @Override public void restoreToCount(int count){float[] previous=saved.pop();scaleX=previous[0];scaleY=previous[1];super.restoreToCount(count);}
        @Override public void drawText(String value,float x,float y,android.graphics.Paint paint){super.drawText(value,x,y,paint);effectiveText=paint.getTextSize()*scaleY;}
    }
    static void nativeIcon(NotificationIconArea area,StatusBarIconView icon,NativeCanvas canvas)throws Throwable{
        area.drawNativeIcon(icon,canvas,()->{
            int save=canvas.save();canvas.scale(icon.nativeScale*icon.iconAppearAmount,icon.nativeScale*icon.iconAppearAmount,32,32);
            try{area.drawNativeGlyph(icon,canvas,()->{canvas.glyphHeight=64*canvas.scaleY;return null;});}
            finally{canvas.restoreToCount(save);}
            canvas.dotSize=6*canvas.scaleY;return null;
        });
    }
    static void runtime()throws Throwable{
        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconOverridesCheck.class.getClassLoader());
        Owner owner=new Owner();owner.mMaxIcons=4;new Host(202).addView(owner);
        StatusBarIconView matched=icon(owner,"com.example.app"),other=icon(owner,"com.other.app");
        area.changed(owner);
        Bundle b=settings(NotificationIconOverrides.put("[]","com.example.app","X",true));
        // Independent master ignores disabled area mode/position/color/count and uses native size.
        b.putString(NotificationIconArea.MODE,"heart");b.putFloat(NotificationIconArea.X,100f);b.putFloat(NotificationIconArea.SIZE,2f);
        b.putBoolean(NotificationIconArea.COLOR_ENABLED,true);b.putBoolean(NotificationIconArea.COUNT_ENABLED,true);b.putFloat(NotificationIconArea.MAX_COUNT,0f);
        area.configure(null,b);eq(true,area.ownsDrawing(owner));
        NativeCanvas container=new NativeCanvas();area.draw(owner,container,()->null);near(0,container.translateX);eq(null,container.text);eq(4,owner.mMaxIcons);
        NativeCanvas canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq("X",canvas.text);near(32,canvas.effectiveText);near(6,canvas.dotSize);near(0,canvas.glyphHeight);
        canvas=new NativeCanvas();nativeIcon(area,other,canvas);eq(null,canvas.text);near(32,canvas.glyphHeight);near(6,canvas.dotSize);
        StatusBarNotification source=matched.getNotification();int reads=source.packageReads;int getterReads=matched.getterCalls;
        Object cache=((Map<?,?>)field(area,"nativeSizes")).get(matched);
        for(int i=0;i<1000;i++){canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq("X",canvas.text);near(32,canvas.effectiveText);}
        eq(reads,source.packageReads);eq(getterReads,matched.getterCalls);
        // Repeated native layouts do not reread SBN package names, re-shape text or reallocate Paint.
        for(int i=0;i<50;i++){area.changed(owner);area.draw(owner,new Canvas(),()->null);}
        eq(reads,source.packageReads);eq(cache,((Map<?,?>)field(area,"nativeSizes")).get(matched));
        matched.iconAppearAmount=.1f;canvas=new NativeCanvas();nativeIcon(area,matched,canvas);near(3.2f,canvas.effectiveText);near(6,canvas.dotSize);matched.iconAppearAmount=1f;
        b.putBoolean(NotificationIconArea.MASTER,true);b.putString(NotificationIconArea.MODE,"native");b.putBoolean(NotificationIconArea.SIZE_ENABLED,true);
        b.putFloat(NotificationIconArea.SIZE,14.89f);b.putBoolean(NotificationIconArea.COLOR_ENABLED,false);b.putBoolean(NotificationIconArea.COUNT_ENABLED,false);b.putBoolean(NotificationIconArea.POSITION,false);
        area.configure(null,b);area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);near(59.56f,canvas.effectiveText);
        canvas=new NativeCanvas();nativeIcon(area,other,canvas);near(59.56f,canvas.glyphHeight);near(6,canvas.dotSize);
        b.putBoolean(NotificationIconArea.SIZE_ENABLED,false);area.configure(null,b);area.draw(owner,new Canvas(),()->null);
        canvas=new NativeCanvas();nativeIcon(area,matched,canvas);near(32,canvas.effectiveText);
        // Replacement package follows the native metadata event; no stale recycled app identity.
        StatusBarNotification replaced=new StatusBarNotification(2);replaced.packageName="com.other.app";matched.setNotification(replaced);area.notificationChanged(matched);
        area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);near(32,canvas.glyphHeight);
        replaced.packageName="com.example.app";area.notificationChanged(matched);area.draw(owner,new Canvas(),()->null);
        b.putString(NotificationIconOverrides.RULES,NotificationIconOverrides.put("[]","com.example.app","❤️",true));area.configure(null,b);
        area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq("❤️",canvas.text);
        b.putString(NotificationIconOverrides.RULES,NotificationIconOverrides.put("[]","com.example.app","X",false));area.configure(null,b);area.draw(owner,new Canvas(),()->null);
        canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);
        // A fresh config before the container re-records invalidates its previous override scope.
        b.putString(NotificationIconOverrides.RULES,NotificationIconOverrides.put("[]","com.example.app","X",true));area.configure(null,b);
        canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);
        area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq("X",canvas.text);
        int invalidations=matched.invalidations;b.putBoolean(StatusBarSettings.SAFE_MODE,true);area.configure(null,b);eq(true,matched.invalidations>invalidations);
        canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);near(32,canvas.glyphHeight);
        b.putBoolean(StatusBarSettings.SAFE_MODE,false);area.configure(null,b);area.draw(owner,new Canvas(),()->null);
        new Host(404).addView(owner);area.nativeMaxIcons(owner,4);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);
        eq(0,((Map<?,?>)field(area,"nativeSizes")).size());eq(0,((Map<?,?>)field(area,"packages")).size());
        new Host(202).addView(owner);area.changed(owner);area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq("X",canvas.text);
        area.releaseRuntime();eq(0,((Map<?,?>)field(area,"packages")).size());canvas=new NativeCanvas();nativeIcon(area,matched,canvas);eq(null,canvas.text);
        area.changed(owner);area.notificationChanged(matched);area.beforeLayout(owner);eq(8,area.nativeMaxIcons(owner,8));
        eq(0,((Map<?,?>)field(area,"states")).size());eq(0,((Map<?,?>)field(area,"packages")).size());
    }
    static void bundle()throws Throwable{
        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconOverridesCheck.class.getClassLoader());
        Owner owner=new Owner();new Host(202).addView(owner);StatusBarIconView icon=icon(owner,"com.example.app");icon.setNotification(null);
        BundleEntry bundle=new BundleEntry();NotificationEntry a=new NotificationEntry(1),b=new NotificationEntry(2);
        a.mSbn.packageName=b.mSbn.packageName="com.example.app";bundle.children.add(a);bundle.children.add(b);icon.mBundleEntry=bundle;
        area.changed(owner);area.configure(null,settings(NotificationIconOverrides.put("[]","com.example.app","X",true)));area.draw(owner,new Canvas(),()->null);
        NativeCanvas canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq("X",canvas.text);eq(1,bundle.reads);
        for(int i=0;i<1000;i++)nativeIcon(area,icon,new NativeCanvas());eq(1,bundle.reads);eq(1,a.mSbn.packageReads);eq(1,b.mSbn.packageReads);
        b.mSbn.packageName="com.other.app";area.notificationChanged(icon);area.draw(owner,new Canvas(),()->null);
        canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(null,canvas.text);near(32,canvas.glyphHeight);
        bundle.children.clear();area.notificationChanged(icon);area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(null,canvas.text);
        icon.mBundleEntry=new Object();area.notificationChanged(icon);area.draw(owner,new Canvas(),()->null);canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(null,canvas.text);
        area.releaseRuntime();
    }
    static void appArtwork()throws Throwable{
        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconOverridesCheck.class.getClassLoader());
        ArrayDeque<Runnable> worker=new ArrayDeque<>(),completion=new ArrayDeque<>();int[] reads={0};
        Bitmap artwork=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);artwork.eraseColor(0xffe03060);
        java.lang.reflect.Method changed=NotificationIconArea.class.getDeclaredMethod("applicationIconsChanged");changed.setAccessible(true);
        NotificationAppIconRuntime cache=new NotificationAppIconRuntime(worker::add,completion::add,(context,pkg)->{reads[0]++;return artwork;},()->{try{changed.invoke(area);}catch(Exception failure){throw new RuntimeException(failure);}});
        Field runtime=NotificationIconArea.class.getDeclaredField("applicationIcons");runtime.setAccessible(true);
        ((NotificationAppIconRuntime)runtime.get(area)).releaseRuntime();runtime.set(area,cache);
        Owner owner=new Owner();new Host(202).addView(owner);StatusBarIconView icon=icon(owner,"com.target.app");area.changed(owner);
        Bundle options=settings(NotificationIconOverrides.putAppIcon("[]","com.target.app","com.source.app",true));
        area.configure(new android.content.Context(),options);area.draw(owner,new Canvas(),()->null);
        NativeCanvas waiting=new NativeCanvas();nativeIcon(area,icon,waiting);eq(0,waiting.bitmapDraws);near(32,waiting.glyphHeight);
        worker.remove().run();completion.remove().run();eq(1,reads[0]);area.changed(owner);area.draw(owner,new Canvas(),()->null);
        for(int i=0;i<1000;i++){NativeCanvas canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(1,canvas.bitmapDraws);near(0,canvas.glyphHeight);near(6,canvas.dotSize);eq(0,canvas.layers);}
        eq(1,reads[0]);eq(0,worker.size());
        options.putBoolean(NotificationIconArea.MASTER,true);options.putBoolean(NotificationIconArea.SIZE_ENABLED,false);
        options.putBoolean(NotificationIconArea.COLOR_ENABLED,true);options.putInt("notification_icons_color_light",0xff00ff00);
        area.configure(new android.content.Context(),options);area.draw(owner,new Canvas(),()->null);NativeCanvas canvas=new NativeCanvas();nativeIcon(area,icon,canvas);
        eq(1,canvas.bitmapDraws);eq(0,canvas.layers);eq(0xffe03060,artwork.pixels[0]);eq(1,reads[0]);near(6,canvas.dotSize);
        // The native view remains attached, so cache revocation must release its bitmap
        // record without waiting for another customized container draw.
        eq(artwork,field(((Map<?,?>)field(area,"nativeSizes")).get(icon),"artwork"));
        options.putBoolean(NotificationIconOverrides.MASTER,false);area.configure(null,options);
        eq(0,((Map<?,?>)field(area,"nativeSizes")).size());eq(null,cache.icon("com.source.app"));
        options.putBoolean(NotificationIconOverrides.MASTER,true);area.configure(new android.content.Context(),options);
        worker.remove().run();completion.remove().run();area.draw(owner,new Canvas(),()->null);eq(2,reads[0]);
        eq(artwork,field(((Map<?,?>)field(area,"nativeSizes")).get(icon),"artwork"));
        // Revocation before the container re-records cannot reuse the old colored bitmap.
        cache.packageChanged("com.source.app",true);canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(0,canvas.bitmapDraws);near(32,canvas.glyphHeight);near(6,canvas.dotSize);
        eq(0,((Map<?,?>)field(area,"nativeSizes")).size());
        options.putBoolean(NotificationIconArea.COLOR_ENABLED,false);options.putBoolean(NotificationIconArea.MASTER,false);
        options.putBoolean(NotificationIconOverrides.MASTER,false);area.configure(null,options);canvas=new NativeCanvas();nativeIcon(area,icon,canvas);eq(0,canvas.bitmapDraws);eq(null,canvas.text);near(32,canvas.glyphHeight);
        eq(0,((Map<?,?>)field(area,"nativeSizes")).size());eq(1,((Map<?,?>)field(area,"states")).size());
        area.releaseRuntime();eq(null,cache.icon("com.source.app"));
    }
    public static void main(String[] args)throws Throwable{parser();runtime();bundle();appArtwork();NotificationAppIconRuntimeCheck.run();System.out.println(checks+" checks passed (text/app-icon strict rules, colored cache, native sizing/dots, metadata cache, mixed bundles and restoration)");}
}
