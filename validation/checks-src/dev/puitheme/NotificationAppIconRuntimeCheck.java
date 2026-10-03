package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.lang.reflect.Field;
import java.util.*;

/** Exercises transactions with queued real loader tasks; drawing cannot trigger a package read. */
public final class NotificationAppIconRuntimeCheck {
    static int checks;
    static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
    static final class Owner extends Context {
        int registered,unregistered;
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){registered++;return null;}
        @Override public void unregisterReceiver(BroadcastReceiver receiver){unregistered++;}
    }
    static final class Source extends Drawable {
        final Bitmap texture=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888);int draws;
        Source(){texture.eraseColor(0xfff03060);texture.pixels[texture.pixels.length-1]=0xff3050e0;}
        @Override public int getIntrinsicWidth(){return 256;}@Override public int getIntrinsicHeight(){return 128;}
        @Override public void draw(Canvas canvas){draws++;android.graphics.Rect bounds=getBounds();canvas.drawBitmap(texture,null,new RectF(bounds.left,bounds.top,bounds.right,bounds.bottom),new Paint(0));}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(android.graphics.ColorFilter filter){}@Override public int getOpacity(){return -3;}
    }
    static final class Manager extends android.content.pm.PackageManager {
        final Source source=new Source();int reads;
        @Override public Drawable getApplicationIcon(String name){reads++;return source;}
        @Override public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName component,int flags){return null;}
        @Override public int getComponentEnabledSetting(android.content.ComponentName component){return 0;}
        @Override public void setComponentEnabledSetting(android.content.ComponentName component,int state,int flags){}
    }
    static void rasterContract()throws Exception{
        Manager manager=new Manager();Context owner=new Context(){@Override public android.content.pm.PackageManager getPackageManager(){return manager;}};
        java.lang.reflect.Method load=NotificationAppIconRuntime.class.getDeclaredMethod("load",Context.class,String.class);load.setAccessible(true);
        Bitmap image=(Bitmap)load.invoke(null,owner,"com.source");eq(1,manager.reads);eq(1,manager.source.draws);
        eq(128,image.getWidth());eq(64,image.getHeight());eq(0xfff03060,image.pixels[0]);eq(0xff3050e0,image.pixels[image.pixels.length-1]);
        eq(Bitmap.Config.ARGB_8888,image.getConfig());
    }
    static int size(NotificationAppIconRuntime runtime)throws Exception{Field f=NotificationAppIconRuntime.class.getDeclaredField("cache");f.setAccessible(true);return ((Map<?,?>)f.get(runtime)).size();}
    static void drain(ArrayDeque<Runnable> queue){while(!queue.isEmpty())queue.remove().run();}
    public static void run()throws Exception{
        rasterContract();
        ArrayDeque<Runnable> work=new ArrayDeque<>(),done=new ArrayDeque<>();int[] reads={0},updates={0};
        Bitmap red=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);red.eraseColor(0xffea3050);
        Bitmap blue=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);blue.eraseColor(0xff2040ec);
        Bitmap[] current={red};
        Owner owner=new Owner();
        NotificationAppIconRuntime runtime=new NotificationAppIconRuntime(work::add,done::add,(ctx,pkg)->{reads[0]++;return pkg.equals("com.missing")?null:current[0];},()->updates[0]++);
        runtime.configure(owner,Arrays.asList("com.source","com.source","com.missing"));eq(2,size(runtime));eq(1,owner.registered);eq(2,work.size());
        runtime.configure(owner,Arrays.asList("com.source","com.missing"));eq(2,work.size());
        drain(work);drain(done);eq(2,reads[0]);eq(red,runtime.icon("com.source"));eq(null,runtime.icon("com.missing"));
        for(int i=0;i<1000;i++){eq(red,runtime.icon("com.source"));runtime.configure(owner,Arrays.asList("com.source","com.missing"));}
        eq(2,reads[0]);eq(0,work.size());eq(1,owner.registered); // Negative cache also loads once.
        runtime.packageChanged("com.unrelated",false);eq(0,work.size());
        runtime.packageChanged("com.source",false);eq(null,runtime.icon("com.source"));eq(1,work.size());
        // Added/changed/replaced during one in-flight read coalesce after discarding old pixels.
        work.remove().run();runtime.packageChanged("com.source",false);runtime.packageChanged("com.source",false);
        current[0]=blue;drain(done);eq(1,work.size());eq(null,runtime.icon("com.source"));drain(work);drain(done);eq(blue,runtime.icon("com.source"));eq(4,reads[0]);
        runtime.packageChanged("com.source",true);eq(null,runtime.icon("com.source"));eq(0,work.size());
        runtime.configure(owner,Arrays.asList("com.source","com.missing"));eq(0,work.size());
        runtime.packageChanged("com.source",false);drain(work);drain(done);eq(blue,runtime.icon("com.source"));eq(5,reads[0]);
        runtime.packageChanged("com.source",false);work.remove().run();runtime.configure(owner,Collections.emptyList());drain(done);eq(null,runtime.icon("com.source"));eq(0,size(runtime));eq(1,owner.unregistered);
        List<String> many=new ArrayList<>();for(int i=0;i<128;i++)many.add("com.fixture.app"+i);runtime.configure(owner,many);eq(128,size(runtime));
        many.add("com.fixture.extra");boolean rejected=false;try{runtime.configure(owner,many);}catch(IllegalArgumentException expected){rejected=true;}eq(true,rejected);eq(128,size(runtime));
        rejected=false;try{runtime.configure(owner,Collections.singletonList("../com.source"));}catch(IllegalArgumentException expected){rejected=true;}eq(true,rejected);eq(128,size(runtime));
        int observed=updates[0];runtime.releaseRuntime();drain(work);drain(done);eq(0,size(runtime));eq(observed,updates[0]);eq(2,owner.unregistered);
        runtime.configure(owner,Collections.singletonList("com.source"));eq(0,size(runtime));
        NotificationAppIconRuntime allocation=new NotificationAppIconRuntime(work::add,done::add,(ctx,pkg)->{throw new OutOfMemoryError("bounded allocation failed");},()->{});
        allocation.configure(owner,Collections.singletonList("com.source"));drain(work);drain(done);eq(null,allocation.icon("com.source"));
        allocation.configure(owner,Collections.singletonList("com.source"));eq(0,work.size());allocation.releaseRuntime();
        System.out.println(checks+" app artwork cache checks passed (one load, negative cache, package revocation, stale generation, bounded memory and release)");
    }
}
