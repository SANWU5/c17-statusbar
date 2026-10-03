// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.ref.WeakReference;

/** SystemUI-side read-only cache. Provider I/O and image decoding never occur in a draw callback. */
public final class IconPackRuntime {
    private static final int MAX_PIXELS=4*1024*1024;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"C17-icon-packs");thread.setDaemon(true);return thread;});
    private static final Map<View,Boolean> OBSERVED=new WeakHashMap<>();
    private static volatile Map<String,Bitmap> loaded=Collections.emptyMap();
    private static String selection="",assignmentSelection="";private static boolean enabled;private static long generation;
    private static volatile Runnable invalidationListener;
    private IconPackRuntime(){ }
    private static final class DrawState {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF target=new RectF();
    }
    private static final ThreadLocal<DrawState> DRAW=ThreadLocal.withInitial(DrawState::new);
    public static void setInvalidationListener(Runnable listener){invalidationListener=listener;}
    public static void configure(Context context,Bundle settings){configure(context,settings,null);}
    public static void configure(Context context,Bundle settings,Runnable onChanged){
        boolean active=context!=null&&settings!=null&&settings.getBoolean(IconPackRepository.MASTER,false)
                &&!SafetyMode.enabled(settings)&&!ModuleLifecycle.removed();
        String source=active?settings.getString(IconPackRepository.LAYERS,"[]"):"[]";
        String assignmentSource=active?settings.getString(IconPackAssignments.ASSIGNMENTS,"[]"):"[]";
        List<IconPackAssignments.Assignment> assignments;
        try{IconPackRepository.layers(source);assignments=IconPackAssignments.rules(assignmentSource);}
        catch(IOException malformed){active=false;source="[]";assignmentSource="[]";assignments=Collections.emptyList();}
        final List<IconPackAssignments.Assignment> explicit=assignments;
        final boolean use=active;final long token;
        synchronized(IconPackRuntime.class){
            if(enabled==active&&selection.equals(source)&&assignmentSelection.equals(assignmentSource))return;
            enabled=active;selection=source;assignmentSelection=assignmentSource;token=++generation;loaded=Collections.emptyMap();
        }
        invalidate(onChanged);
        if(!use)return;
        final List<IconPackRepository.Layer> requested;
        try{requested=IconPackRepository.layers(source);}catch(IOException impossible){return;}
        Context application=context.getApplicationContext();final Context resolverContext=application==null?context:application;
        WORKER.execute(()->{
            List<IconPackSelection.Pack> packs=new ArrayList<>();
            Set<String> explicitSources=new HashSet<>();for(IconPackAssignments.Assignment assignment:explicit)explicitSources.add(assignment.packId);
            for(IconPackRepository.Layer layer:requested){
                if(!layer.enabled||(!layer.autoMatch&&!explicitSources.contains(layer.id)))continue;if(!current(token))return;
                try{
                    byte[] bytes;
                    try(InputStream input=resolverContext.getContentResolver().openInputStream(IconPackRepository.manifestUri(layer.id))){
                        if(input==null)continue;bytes=IconPackArchive.readEntry(input,IconPackManifest.MAX_BYTES);
                    }
                    packs.add(new IconPackSelection.Pack(layer.id,IconPackManifest.parse(bytes),layer.autoMatch));
                }catch(IOException|RuntimeException unavailable){
                    ModuleDiagnostics.info("icons","An imported icon library is unavailable; preserving native fallback");
                }catch(OutOfMemoryError unavailable){
                    ModuleDiagnostics.info("icons","Icon cache memory limit reached; preserving native fallback");break;
                }
            }
            // Only the first usable candidate for each effective role is decoded. A completely
            // covered lower library contributes metadata only, not additional bitmap memory.
            Map<String,Bitmap> result=IconPackSelection.resolve(IconPackSelection.plan(packs,explicit),MAX_PIXELS,()->!current(token),(asset,remaining)->{
                Bitmap bitmap=readMask(resolverContext,asset.id,asset.path,remaining);
                return bitmap==null?null:new IconPackSelection.Decoded<>(bitmap,bitmap.getWidth()*bitmap.getHeight());
            });
            synchronized(IconPackRuntime.class){if(!current(token))return;loaded=Collections.unmodifiableMap(result);}
            invalidate(onChanged);
        });
    }
    private static synchronized boolean current(long token){return enabled&&generation==token&&!ModuleLifecycle.removed();}
    private static Bitmap readMask(Context context,String id,String asset,int remaining)throws IOException{
        byte[] bytes;
        try(InputStream input=context.getContentResolver().openInputStream(IconPackRepository.assetUri(id,asset))){
            if(input==null)return null;bytes=IconPackArchive.readEntry(input,IconPackArchive.MAX_ENTRY);
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(!"image/png".equals(bounds.outMimeType)||bounds.outWidth<=0||bounds.outHeight<=0
                ||bounds.outWidth>256||bounds.outHeight>256||bounds.outWidth*bounds.outHeight>remaining)return null;
        Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(decoded==null)return null;
        try{return decoded.extractAlpha();}finally{decoded.recycle();}
    }
    /** Observe actual glyph views, never whole hierarchy scans or a strong retained window reference. */
    public static void observe(View view){if(view!=null)synchronized(OBSERVED){OBSERVED.put(view,Boolean.TRUE);}}
    private static void invalidate(Runnable onChanged){
        final List<WeakReference<View>> views=new ArrayList<>();
        synchronized(OBSERVED){for(View view:OBSERVED.keySet())if(view!=null)views.add(new WeakReference<>(view));}
        final Runnable listener=invalidationListener;
        new Handler(Looper.getMainLooper()).post(()->{
            for(WeakReference<View> reference:views){View view=reference.get();if(view!=null&&view.isAttachedToWindow())view.invalidate();}
            if(onChanged!=null)onChanged.run();
            if(listener!=null&&listener!=onChanged)listener.run();
        });
    }
    public static void release(){synchronized(IconPackRuntime.class){enabled=false;selection="";assignmentSelection="";generation++;loaded=Collections.emptyMap();}invalidate(null);synchronized(OBSERVED){OBSERVED.clear();}}
    public static void releaseRuntime(){release();invalidationListener=null;}
    public static Bitmap bitmapForHint(String slot,boolean on){return hint(slot,on);}
    public static Bitmap bitmapForWifi(int level,boolean connected){return wifi(level,connected);}
    public static Bitmap bitmapForCellular(int level,boolean connected){return cellular(level,connected);}
    public static Bitmap bitmapForBattery(int level,boolean charging){return battery(level,charging);}
    public static Bitmap hint(String slot,boolean on){
        if(ModuleLifecycle.removed())return null;
        return slot==null?null:loaded.get("hint."+slot+(on?".on":".off"));
    }
    public static Bitmap wifi(int level,boolean connected){
        if(ModuleLifecycle.removed())return null;
        return loaded.get("wifi."+(connected?Math.max(0,Math.min(4,level)):"none"));
    }
    public static Bitmap cellular(int level,boolean connected){
        if(ModuleLifecycle.removed())return null;
        return loaded.get("cellular."+(connected?Math.max(0,Math.min(4,level)):"none"));
    }
    public static Bitmap battery(int level,boolean charging){
        if(ModuleLifecycle.removed())return null;
        return loaded.get("battery."+(charging?"charging.":"")+(Math.max(0,Math.min(100,level))/10*10));
    }
    /** Draw a tintable alpha mask in the renderer's existing bounds; native layout/spacing stays intact. */
    public static boolean draw(Bitmap bitmap,Canvas canvas,RectF bounds,int color){
        if(bitmap==null||bitmap.isRecycled()||canvas==null||bounds==null||bounds.width()<=0f||bounds.height()<=0f)return false;
        DrawState state=DRAW.get();Paint paint=state.paint;paint.setColor(color);
        float scale=Math.min(bounds.width()/bitmap.getWidth(),bounds.height()/bitmap.getHeight());
        float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;
        RectF target=state.target;target.set(bounds.centerX()-width*.5f,bounds.centerY()-height*.5f,bounds.centerX()+width*.5f,bounds.centerY()+height*.5f);
        canvas.drawBitmap(bitmap,null,target,paint);return true;
    }
}
