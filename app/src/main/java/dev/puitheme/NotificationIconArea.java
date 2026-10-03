// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only the real Phone notification container is customized; shelf/AOD/QS are untouched. */
public final class NotificationIconArea {
    public static final String MASTER = "notification_icons_enabled", MODE = "notification_icons_mode";
    public static final String TEXT = "notification_icons_text", IMAGE_NAME = "notification_icons_image_name", IMAGE_REVISION = "notification_icons_image_revision";
    public static final String POSITION = "notification_icons_position_enabled", SIZE_ENABLED = "notification_icons_size_enabled", COLOR_ENABLED = "notification_icons_color_enabled";
    public static final String X = "notification_icons_offset_x", Y = "notification_icons_offset_y", SIZE = "notification_icons_size";
    public static final String COUNT_ENABLED="notification_icons_count_enabled", MAX_COUNT="notification_icons_max_count";
    public static final String SPACING_ENABLED="notification_icons_spacing_enabled", SPACING="notification_icons_spacing";
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, Integer> COLORS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String,Boolean> b = new LinkedHashMap<>(); b.put(MASTER,false); b.put(POSITION,true); b.put(SIZE_ENABLED,true); b.put(COLOR_ENABLED,false);b.put(COUNT_ENABLED,false);b.put(SPACING_ENABLED,false); BOOLEANS=Collections.unmodifiableMap(b);
        Map<String,Float> n = new LinkedHashMap<>(); n.put(X,0f); n.put(Y,0f); n.put(SIZE,20f);n.put(MAX_COUNT,3f);n.put(SPACING,0f); NUMBERS=Collections.unmodifiableMap(n);
        Map<String,Integer> c = new LinkedHashMap<>(); c.put("notification_icons_color_light",Color.BLACK); c.put("notification_icons_color_dark",Color.WHITE); COLORS=Collections.unmodifiableMap(c);
        Map<String,String> s = new LinkedHashMap<>(); s.put(MODE,"native"); s.put(TEXT,"♥"); s.put(IMAGE_NAME,""); s.put(IMAGE_REVISION,""); STRINGS=Collections.unmodifiableMap(s);
    }
    public interface Draw { Object run() throws Throwable; }
    private final Map<ViewGroup,State> states = new WeakHashMap<>();
    private final Map<View,NativeSize> nativeSizes = new WeakHashMap<>();
    private final Map<View,PackageIdentity> packages = new WeakHashMap<>();
    private final NotificationIconOverrides overrides=new NotificationIconOverrides();
    private final NotificationAppIconRuntime applicationIcons=new NotificationAppIconRuntime(this::applicationIconsChanged);
    private final ThreadLocal<GlyphPool> glyphPools=new ThreadLocal<>();
    private final ThreadLocal<GlyphScope> drawingGlyph=new ThreadLocal<>();
    private final Map<ViewGroup,WeakReference<ViewGroup>> phoneBindings = new WeakHashMap<>();
    private final Map<ViewGroup,Integer> probes = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor(task -> { Thread t=new Thread(task,"C17-notification-image"); t.setDaemon(true);return t; });
    private Bundle settings = new Bundle();
    private Bitmap bitmap;
    private String displayText="♥";
    private String imageRequest = "";
    private int imageGeneration;
    private volatile boolean enabled,released;
    private Class<?> containerClass, iconClass;
    private Method visibleState, notification, blocked, iconHeight, iconScale, tint;
    private Field bundleEntry, decorColor;
    private Method bundleChildren,representativeEntry,entryNotification;
    private Field maxIcons;
    private Method setMaxIcons,updateState;
    private Method actualPaddingStart,actualPaddingEnd,setMeasuredDimension;
    private volatile boolean spacingReady;
    private final ThreadLocal<Float> spacingCalculation=new ThreadLocal<>();
    private final ThreadLocal<Boolean> applyingCount=ThreadLocal.withInitial(() -> false);
    private boolean countWarning;

    public void resolve(ClassLoader loader) throws ReflectiveOperationException {
        containerClass=loader.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer");
        iconClass=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
        visibleState=iconClass.getMethod("getVisibleState"); notification=iconClass.getMethod("getNotification");
        blocked=iconClass.getMethod("isIconBlocked"); iconHeight=iconClass.getDeclaredMethod("getIconHeight");iconHeight.setAccessible(true);
        iconScale=iconClass.getMethod("getIconScale");tint=iconClass.getMethod("getStaticDrawableColor");
        try{bundleEntry=iconClass.getDeclaredField("mBundleEntry");bundleEntry.setAccessible(true);}catch(NoSuchFieldException olderAndroid){bundleEntry=null;}
        try{
            bundleChildren=loader.loadClass("com.android.systemui.statusbar.notification.collection.BundleEntry").getMethod("getChildren");
            representativeEntry=loader.loadClass("com.android.systemui.statusbar.notification.collection.ListEntry").getMethod("getRepresentativeEntry");
            entryNotification=loader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry").getMethod("getSbn");
        }catch(ClassNotFoundException|NoSuchMethodException unsupportedBundle){bundleChildren=null;representativeEntry=null;entryNotification=null;}
        try{decorColor=iconClass.getDeclaredField("mDecorColor");decorColor.setAccessible(true);}catch(NoSuchFieldException olderAndroid){decorColor=null;}
        try {
            maxIcons=containerClass.getDeclaredField("mMaxIcons");maxIcons.setAccessible(true);
            setMaxIcons=containerClass.getDeclaredMethod("setMaxIconsAmount",Integer.TYPE);setMaxIcons.setAccessible(true);
            updateState=containerClass.getDeclaredMethod("updateState");updateState.setAccessible(true);
        } catch(NoSuchFieldException|NoSuchMethodException unsupportedCount) {maxIcons=null;setMaxIcons=null;updateState=null;}
        try{
            actualPaddingStart=containerClass.getDeclaredMethod("getActualPaddingStart");
            actualPaddingEnd=containerClass.getDeclaredMethod("getActualPaddingEnd");
            setMeasuredDimension=View.class.getDeclaredMethod("setMeasuredDimension",Integer.TYPE,Integer.TYPE);setMeasuredDimension.setAccessible(true);
        }catch(NoSuchMethodException unsupportedSpacing){actualPaddingStart=null;actualPaddingEnd=null;setMeasuredDimension=null;}
    }
    public void configure(Context context, Bundle options) {
        if(released)return;
        boolean previousSpacing=spacingActive();float previousGap=NumericPolicy.finite(settings.get(SPACING),0f);
        settings=new Bundle(options);
        overrides.configure(options);
        applicationIcons.configure(context,overrides.sourcePackages());
        displayText=customText(options.getString(TEXT,"♥"));
        enabled=active(options)||overrides.enabled();
        discardStaleArtwork();
        String wanted=active(options) && "image".equals(mode(options.getString(MODE))) ? options.getString(IMAGE_REVISION,"") : "";
        if (!wanted.equals(imageRequest)) {
            imageRequest=wanted; final int generation=++imageGeneration; bitmap=null;
            if (!wanted.isEmpty() && context!=null) images.execute(() -> {
                Bitmap loaded=null;
                try(InputStream input=context.getContentResolver().openInputStream(Uri.parse(NotificationIconRepository.URI))) {
                    BitmapFactory.Options decode=new BitmapFactory.Options(); decode.inJustDecodeBounds=true;
                    // Provider owns normalized <=128px PNG. Still bound stream for a replaced provider/file.
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096];int count;
                    while(input!=null&&(count=input.read(buffer))!=-1) { if(bytes.size()+count>128*1024)throw new java.io.IOException("Oversize icon");bytes.write(buffer,0,count); }
                    byte[] data=bytes.toByteArray();BitmapFactory.decodeByteArray(data,0,data.length,decode);
                    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(data);StringBuilder hash=new StringBuilder(64);
                    for(byte value:digest){hash.append(Character.forDigit((value>>>4)&15,16));hash.append(Character.forDigit(value&15,16));}
                    if(!wanted.equals(hash.toString()))throw new java.io.IOException("Icon revision mismatch");
                    if(decode.outWidth<=0||decode.outHeight<=0||decode.outWidth>128||decode.outHeight>128)throw new java.io.IOException("Invalid icon dimensions");
                    decode.inJustDecodeBounds=false;loaded=BitmapFactory.decodeByteArray(data,0,data.length,decode);
                } catch(Exception ignored) { }
                final Bitmap ready=loaded;
                main.post(() -> { if(generation!=imageGeneration || ModuleLifecycle.removed())return;bitmap=ready;invalidate(); });
            });
        }
        for(ViewGroup owner:states.keySet().toArray(new ViewGroup[0])) {
            State state=states.get(owner);if(state==null)continue;
            applyCount(owner,state,true);state.dirty=true;state.sizeDiagnostics.clear();invalidateNativeIcons(owner);
        }
        if(previousSpacing!=spacingActive()||previousGap!=NumericPolicy.finite(settings.get(SPACING),0f))refreshSpacing();
        if(ModuleDiagnostics.enabled())ModuleDiagnostics.info("hooks","Notification icons configured "
                +(enabled?"active":"disabled")+" mode "+mode(options.getString(MODE))+" tracked "+states.size());
        else probes.clear();
        invalidate();
    }
    private void invalidate() { for (ViewGroup owner:new ArrayList<>(states.keySet()))if(owner!=null)owner.invalidate(); }
    private void applicationIconsChanged(){
        if(released)return;
        discardStaleArtwork();
        for(ViewGroup owner:states.keySet().toArray(new ViewGroup[0])){
            State state=states.get(owner);if(state==null)continue;
            state.dirty=true;invalidateNativeIcons(owner);owner.invalidate();
        }
    }
    private void discardStaleArtwork(){
        // Owners and icon views can remain attached after customization is switched off.
        // Drop their bitmap baselines now; an inactive draw never rebuilds these records.
        if(!enabled){nativeSizes.clear();return;}
        nativeSizes.entrySet().removeIf(entry->{NativeSize size=entry.getValue();return size.artwork!=null
                &&(size.rule==null||size.artwork!=applicationIcons.icon(size.rule.sourcePackage));});
    }
    public boolean ownsDrawing(Object owner) { return enabled && containerClass!=null && containerClass.isInstance(owner); }
    /** The real status-bar binder identifies this host even when its ancestor resource names change. */
    public void bindPhoneArea(ViewGroup area) {
        if(released||area==null||containerClass==null)return;
        int id=area.getResources().getIdentifier("notificationIcons","id","com.android.systemui");
        View child=id==0?null:area.findViewById(id);
        if(!(child instanceof ViewGroup)||!containerClass.isInstance(child)||child==area)return;
        ViewGroup owner=(ViewGroup)child;
        phoneBindings.put(owner,new WeakReference<>(area));
        changed(owner);
        probe(owner,8,"status-bar binder identified host");
    }
    public void changed(View owner) {
        if(released||Boolean.TRUE.equals(applyingCount.get()))return;
        ViewParent parent=owner instanceof ViewGroup && containerClass!=null&&containerClass.isInstance(owner)?(ViewGroup)owner:owner.getParent();
        if(!(parent instanceof ViewGroup)||containerClass==null||!containerClass.isInstance(parent))return;
        ViewGroup container=(ViewGroup)parent;
        State state=states.get(container);
        if(state!=null&&!phoneArea(container)) {
            restoreCount(container,state,true);forgetRestored(container,state);return;
        }
        if(state==null){
            if(!phoneArea(container)){probe(container,16,"layout outside status-bar scope");return;}
            // Observe native hosts before the default-off setting is enabled. Their cached render
            // nodes must be invalidated on the first configuration change, without waiting for
            // a notification add/remove or layout event.
            state=new State();states.put(container,state);
        }
        applyCount(container,state,true);
        state.dirty=true;
        if(enabled)container.invalidate();
        probe(container,1,"native layout or icon event registered host");
    }
    /** Package metadata is refreshed only for native notification/artwork events, never a draw. */
    public void notificationChanged(View icon){if(released)return;packages.remove(icon);changed(icon);}
    public Object draw(ViewGroup owner,Canvas canvas,Draw nativeDraw) throws Throwable {
        boolean customize=active(settings);
        if((!customize&&!overrides.enabled())||containerClass==null||!containerClass.isInstance(owner)) {
            State inactive=states.get(owner);if(inactive!=null)restoreCount(owner,inactive,true);
            return nativeDraw.run();
        }
        probe(owner,2,"native dispatchDraw intercepted");
        State state=states.get(owner);if(state==null){if(!phoneArea(owner))return nativeDraw.run();state=new State();states.put(owner,state);}
        if(state.dirty){if(!phoneArea(owner)){restoreCount(owner,state,true);forgetRestored(owner,state);probe(owner,16,"draw outside status-bar scope");return nativeDraw.run();}update(owner,state);}
        String mode=customize?mode(settings.getString(MODE)):"native";
        // Missing/invalid imported pixels keep the real icons available.
        if("image".equals(mode)&&bitmap==null)mode="native";
        if("native".equals(mode)&&countActive()&&safeCount(settings.get(MAX_COUNT),owner.getChildCount())==0)return null;
        if(state.icons.isEmpty()){probe(owner,4,"no eligible native notification children");return nativeDraw.run();}
        if(ModuleDiagnostics.enabled())probe(owner,32,"eligible native notification children "+state.icons.size());
        float density=owner.getResources().getDisplayMetrics().density;
        float size=customize&&settings.getBoolean(SIZE_ENABLED,true)?safeSize(settings.get(SIZE),density):state.height;
        float x=customize&&settings.getBoolean(POSITION,true)?NumericPolicy.pixels(NumericPolicy.finite(settings.get(X),0f),density):0f;
        float y=customize&&settings.getBoolean(POSITION,true)?NumericPolicy.pixels(NumericPolicy.finite(settings.get(Y),0f),density):0f;
        int save=canvas.save();
        try {
            canvas.translate(x,y);
            if("native".equals(mode)) {
                // Size belongs to each actual glyph, not the whole row: intrinsic app artwork,
                // ImageView FIT_CENTER and native icon scales differ between notification apps.
                // Scaling the row by its last child's intrinsic height both shrank other glyphs
                // and moved native spacing. drawNativeIcon applies each stable native baseline.
                // Tint only the native glyph branch. A row-wide layer would recolor source
                // application artwork and the native transition/overflow dot as well.
                return nativeDraw.run();
            }
            View first=null;float alpha=0f;
            for(WeakReference<View> ref:state.icons){View icon=ref.get();if(icon!=null&&icon.getParent()==owner&&icon.getVisibility()==View.VISIBLE){if(first==null)first=icon;alpha=Math.max(alpha,icon.getAlpha());}}
            if(first==null||alpha<=0f)return null;
            int nativeTint=state.nativeTint;
            nativeTint=nativeTint(first,nativeTint);
            int color=settings.getBoolean(COLOR_ENABLED,false)?customColor(nativeTint):nativeTint;
            state.paint.setColor(color);state.paint.setAlpha(Math.round(Color.alpha(color)*Math.min(1f,Math.max(0f,alpha))));
            float cx=first.getLeft()+first.getTranslationX()+first.getWidth()/2f,cy=owner.getHeight()/2f;
            if("image".equals(mode)){
                Bitmap image=bitmap;float ratio=(float)image.getWidth()/image.getHeight();float width=ratio>=1?size:size*ratio,height=ratio>=1?size/ratio:size;
                state.target.set(cx-width/2,cy-height/2,cx+width/2,cy+height/2);
                state.paint.setAlpha(Math.round(255f*Math.min(1f,Math.max(0f,alpha))));
                if(settings.getBoolean(COLOR_ENABLED,false))state.paint.setColorFilter(state.filter(color));
                try{canvas.drawBitmap(image,null,state.target,state.paint);}finally{state.paint.setColorFilter(null);}
            } else {
                state.paint.setTextSize(NumericPolicy.textPixels(size));String text="heart".equals(mode)?"♥":displayText;
                state.paint.getFontMetrics(state.metrics);
                canvas.drawText(text,cx,cy-(state.metrics.ascent+state.metrics.descent)/2f,state.paint);
            }
            return null;
        } finally { canvas.restoreToCount(save); }
    }
    private void update(ViewGroup owner,State state) {
        Map<View,NativeSize> previous=new IdentityHashMap<>();
        for(WeakReference<View> old:state.icons){View icon=old.get();NativeSize size=icon==null?null:nativeSizes.get(icon);
            if(size!=null&&size.owner.get()==owner){previous.put(icon,size);nativeSizes.remove(icon);}}
        state.icons.clear();state.height=20f*owner.getResources().getDisplayMetrics().density;
        boolean measured=false;
        for(int i=0;i<owner.getChildCount();i++){View icon=owner.getChildAt(i);if(!iconClass.isInstance(icon))continue;
            try { int visibility=((Number)visibleState.invoke(icon)).intValue();
                Object source=notification.invoke(icon),bundle=source==null&&bundleEntry!=null?bundleEntry.get(icon):null;
                if((source==null&&bundle==null)||Boolean.TRUE.equals(blocked.invoke(icon))||visibility==2||icon.getVisibility()!=View.VISIBLE)continue;
                state.icons.add(new WeakReference<>(icon));float height=nativeHeight(icon,state);
                if(height>0&&Float.isFinite(height)){
                    if(!measured){state.height=height;measured=true;}
                    if(visibility==0){
                        float target=active(settings)&&settings.getBoolean(SIZE_ENABLED,true)
                                ?safeSize(settings.get(SIZE),owner.getResources().getDisplayMetrics().density):height;
                        NotificationIconOverrides.Rule rule=overrides.enabled()?overrides.match(packageName(icon,source,bundle)):null;
                        NativeSize old=previous.get(icon);long revision=overrides.revision();int color=nativeTint(icon,state.nativeTint);
                        boolean colorOverride=active(settings)&&settings.getBoolean(COLOR_ENABLED,false);
                        if(colorOverride)color=customColor(color);
                        Bitmap artwork=rule!=null&&rule.isAppIcon()?applicationIcons.icon(rule.sourcePackage):null;
                        NativeSize next=old!=null&&old.height==height&&old.target==target&&old.nativeScale==state.nativeScale
                                &&old.rule==rule&&old.revision==revision&&old.color==color&&old.colorOverride==colorOverride&&old.artwork==artwork?old
                                :new NativeSize(owner,height,target,state.nativeScale,rule,revision,color,colorOverride,artwork);
                        nativeSizes.put(icon,next);
                        // Child RenderNodes cache the onDraw scale separately from the container.
                        // Only a new glyph/native geometry or configuration change dirties them.
                        if(old!=next)icon.invalidate();
                        diagnoseNativeSize(owner,icon,state,height);
                    }
                }
                state.nativeTint=nativeTint(icon,state.nativeTint);
            } catch(Exception ignored){ }
        }
        state.dirty=false;
    }
    /** Real ordinary SBNs are immutable identities. Bundles can represent several apps: a
     * mixed/unknown native bundle deliberately stays native rather than borrowing its first app. */
    private String packageName(View icon,Object source,Object bundle)throws ReflectiveOperationException {
        Object identity=source!=null?source:bundle;PackageIdentity previous=packages.get(icon);
        if(previous!=null&&previous.source.get()==identity)return previous.packageName;
        String packageName=null;
        if(source instanceof StatusBarNotification)packageName=((StatusBarNotification)source).getPackageName();
        else if(bundle!=null&&bundleChildren!=null&&bundleChildren.getDeclaringClass().isInstance(bundle)){
            Object children=bundleChildren.invoke(bundle);
            if(children instanceof List){List<?> entries=(List<?>)children;boolean same=!entries.isEmpty()&&entries.size()<=256;
                if(same)for(Object child:entries){
                    if(child==null||!representativeEntry.getDeclaringClass().isInstance(child)){same=false;break;}
                    Object entry=representativeEntry.invoke(child);
                    if(entry==null||!entryNotification.getDeclaringClass().isInstance(entry)){same=false;break;}
                    Object sbn=entryNotification.invoke(entry);String next=sbn instanceof StatusBarNotification?((StatusBarNotification)sbn).getPackageName():null;
                    if(next==null||packageName!=null&&!packageName.equals(next)){same=false;break;}packageName=next;
                }
                if(!same)packageName=null;
            }
        }
        packages.put(icon,new PackageIdentity(identity,packageName));return packageName;
    }
    /** Actual ImageView bounds/matrix precede StatusBarIconView's own mIconScale in native onDraw.
     * Never include animated mIconAppearAmount or our previous canvas scale in this baseline. */
    private float nativeHeight(View icon,State state)throws ReflectiveOperationException {
        float height=((Number)iconHeight.invoke(icon)).floatValue();
        state.rawWidth=0f;state.rawHeight=height;state.matrixHeight=height;
        if(icon instanceof ImageView){
            ImageView image=(ImageView)icon;Drawable drawable=image.getDrawable();
            if(drawable!=null){
                android.graphics.Rect bounds=drawable.getBounds();
                float width=bounds.width(),drawHeight=bounds.height();
                if(width<=0||drawHeight<=0){width=drawable.getIntrinsicWidth();drawHeight=drawable.getIntrinsicHeight();}
                if(width>0&&drawHeight>0){
                    state.rawWidth=width;state.rawHeight=drawHeight;
                    state.nativeBounds.set(0,0,width,drawHeight);Matrix matrix=image.getImageMatrix();
                    if(matrix!=null)matrix.mapRect(state.nativeBounds);
                    height=state.nativeBounds.height();state.matrixHeight=height;
                }
            }
        }
        state.nativeScale=((Number)iconScale.invoke(icon)).floatValue();return height*state.nativeScale;
    }
    private void diagnoseNativeSize(ViewGroup owner,View icon,State state,float height){
        if(!ModuleDiagnostics.enabled())return;
        float density=owner.getResources().getDisplayMetrics().density;
        float target=active(settings)&&settings.getBoolean(SIZE_ENABLED,true)?safeSize(settings.get(SIZE),density):height;
        float factor=NumericPolicy.scale((double)target/height,height);
        long signature=Float.floatToIntBits(state.rawWidth);
        for(float value:new float[]{state.rawHeight,state.matrixHeight,state.nativeScale,density,target,factor})
            signature=31*signature+Float.floatToIntBits(value);
        Long last=state.sizeDiagnostics.get(icon);if(last!=null&&last.longValue()==signature)return;
        state.sizeDiagnostics.put(icon,signature);
        ModuleDiagnostics.info("hooks","Notification glyph native bounds "+state.rawWidth+"x"+state.rawHeight
                +" matrix-height "+state.matrixHeight+" matrix-scale "+state.matrixHeight/state.rawHeight+" native-scale "+state.nativeScale
                +" target-dp "+target/density+" density "+density+" applied-scale "+factor);
    }
    /** Exact StatusBarIconView.onDraw hook establishes ownership only. The native dot is drawn
     * after its invoke-super ImageView.onDraw branch, and must never inherit glyph resizing. */
    public Object drawNativeIcon(View icon,Canvas canvas,Draw nativeDraw)throws Throwable {
        boolean customize=active(settings),replace=overrides.enabled();
        if((!customize&&!replace)||canvas==null)return nativeDraw.run();
        String display=customize?mode(settings.getString(MODE)):"native";
        if(!"native".equals(display)&&!("image".equals(display)&&bitmap==null))return nativeDraw.run();
        NativeSize baseline=nativeSizes.get(icon);
        ViewGroup owner=baseline==null?null:baseline.owner.get();
        if(owner==null||icon.getParent()!=owner||!states.containsKey(owner))return nativeDraw.run();
        if(!(customize&&(settings.getBoolean(SIZE_ENABLED,true)||settings.getBoolean(COLOR_ENABLED,false)))
                &&!(replace&&baseline.rule!=null&&baseline.revision==overrides.revision()))return nativeDraw.run();
        GlyphPool pool=glyphPools.get();if(pool==null){pool=new GlyphPool();glyphPools.set(pool);}
        if(pool.depth==pool.frames.length)return nativeDraw.run();
        GlyphScope scope=pool.frames[pool.depth++];scope.icon=icon;scope.canvas=canvas;scope.baseline=baseline;
        scope.previous=drawingGlyph.get();drawingGlyph.set(scope);
        try{return nativeDraw.run();}
        finally{if(scope.previous==null)drawingGlyph.remove();else drawingGlyph.set(scope.previous);
            scope.icon=null;scope.canvas=null;scope.baseline=null;scope.previous=null;pool.depth--;}
    }
    /** Only the real invoke-super glyph branch sees this transform; ordinary ImageViews and
     * the surrounding StatusBarIconView dot/appear animation stay native. */
    public Object drawNativeGlyph(View icon,Canvas canvas,Draw nativeDraw)throws Throwable {
        GlyphScope scope=drawingGlyph.get();
        if(scope==null||scope.icon!=icon||scope.canvas!=canvas)return nativeDraw.run();
        NativeSize baseline=scope.baseline;
        if(baseline.rule!=null&&overrides.enabled()&&baseline.revision==overrides.revision()&&baseline.paint!=null){
            // StatusBarIconView already owns iconScale * appearAmount. ImageView's matrix is
            // bypassed only for this replacement, so compensate nativeScale once when preparing.
            if(baseline.rule.isAppIcon()){
                Bitmap image=baseline.artwork;
                // Package changes invalidate the bitmap identity immediately; no stale pixels
                // can be reused while the container waits for its next native recording.
                if(image!=null&&image==applicationIcons.icon(baseline.rule.sourcePackage)){
                    float side=NumericPolicy.pixels(baseline.target/baseline.nativeScale,1f);
                    float ratio=(float)image.getWidth()/image.getHeight();
                    float width=ratio>=1?side:side*ratio,height=ratio>=1?side/ratio:side;
                    float cx=icon.getWidth()/2f,cy=icon.getHeight()/2f;
                    baseline.imageTarget.set(cx-width/2,cy-height/2,cx+width/2,cy+height/2);
                    canvas.drawBitmap(image,null,baseline.imageTarget,baseline.paint);return null;
                }
            }else{
                canvas.drawText(baseline.rule.text,icon.getWidth()/2f,icon.getHeight()/2f-baseline.textCenter,baseline.paint);return null;
            }
        }
        if(!active(settings))return nativeDraw.run();
        boolean resize=settings.getBoolean(SIZE_ENABLED,true),recolor=settings.getBoolean(COLOR_ENABLED,false)&&baseline.tintPaint!=null;
        if(!resize&&!recolor)return nativeDraw.run();
        int save=canvas.save();
        try{
            if(resize)canvas.scale(baseline.factor,baseline.factor,icon.getWidth()/2f,icon.getHeight()/2f);
            if(recolor){int layer=canvas.saveLayer(null,baseline.tintPaint);try{return nativeDraw.run();}finally{canvas.restoreToCount(layer);}}
            return nativeDraw.run();
        }
        finally{canvas.restoreToCount(save);}
    }
    static boolean active(Bundle settings){return settings!=null&&!SafetyMode.enabled(settings)&&!ModuleLifecycle.removed()&&settings.getBoolean(MASTER,false);}
    static String mode(String value){return "heart".equals(value)||"text".equals(value)||"image".equals(value)?value:"native";}
    static String customText(String value){if(value==null||value.trim().isEmpty())return "♥";int count=value.codePointCount(0,value.length());return count>12?value.substring(0,value.offsetByCodePoints(0,12)):value;}
    static float safeSize(Object value,float density){return NumericPolicy.pixels(Math.max(0f,NumericPolicy.finite(value,20f)),density);}
    /** Saved integers can be very large; native layout only needs the actual available children. */
    static int safeCount(Object value,int children) {
        float count=NumericPolicy.finite(value,NUMBERS.get(MAX_COUNT));
        return Math.min(Math.max(0,children),Math.max(0,Math.round(count)));
    }
    private boolean countActive() {
        return maxIcons!=null&&active(settings)&&Boolean.TRUE.equals(settings.get(COUNT_ENABLED))
                &&"native".equals(mode(settings.getString(MODE)));
    }

    private boolean spacingActive(){return spacingReady&&!released&&active(settings)&&settings.getBoolean(SPACING_ENABLED,false)
            &&"native".equals(mode(settings.getString(MODE)))&&NumericPolicy.finite(settings.get(SPACING),0f)!=0f;}
    /** Both native cursor/overflow hooks must be installed before measurement can add gaps. */
    public boolean setSpacingReady(boolean ready){
        boolean previous=spacingReady;spacingReady=!released&&ready&&maxIcons!=null&&actualPaddingStart!=null
                &&actualPaddingEnd!=null&&setMeasuredDimension!=null;
        if(previous!=spacingReady)refreshSpacing();return spacingReady;
    }
    private void refreshSpacing(){
        for(ViewGroup owner:states.keySet().toArray(new ViewGroup[0]))if(owner!=null){
            owner.requestLayout();
            if(updateState!=null)try{updateState.invoke(owner);}catch(ReflectiveOperationException|RuntimeException unavailable){ }
            State state=states.get(owner);if(state!=null)state.dirty=true;owner.invalidate();
        }
    }
    /** The native method owns icon states, animation fractions, overflow, and RTL mirroring. */
    public SpacingScope enterSpacingCalculation(View owner){
        Float previous=spacingCalculation.get();
        if(spacingActive()&&nativeContainer(owner)&&phoneArea(owner))spacingCalculation.set(
                NumericPolicy.pixels(NumericPolicy.finite(settings.get(SPACING),0f),owner.getResources().getDisplayMetrics().density));
        else spacingCalculation.remove();
        return new SpacingScope(previous);
    }
    public final class SpacingScope implements AutoCloseable{
        private Float previous;private boolean closed;
        private SpacingScope(Float previous){this.previous=previous;}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)spacingCalculation.remove();else spacingCalculation.set(previous);previous=null;}
    }
    public boolean needsSpacingAdvance(){return spacingCalculation.get()!=null&&spacingActive();}
    /** The exact native helper returns appearance * width * increasedScale + cursor. */
    public float spacingAdvance(float nativeResult,float appearance,float width,float scale){
        Float gap=spacingCalculation.get();if(gap==null||!spacingActive()||!Float.isFinite(nativeResult)||!Float.isFinite(appearance))return nativeResult;
        return nativeResult+Math.max(0f,Math.min(1f,appearance))*spacingExtra(width,scale,gap);
    }
    static float spacingExtra(float width,float scale,float gap){
        if(!Float.isFinite(width)||!Float.isFinite(scale)||!Float.isFinite(gap)||width<=0f||scale<=0f)return 0f;
        return Math.max(1f-width*scale,gap);
    }
    /** Non-last native overflow reserves the current slot and the next dot slot plus their gap. */
    public Object[] adjustSpacingOverflow(Object[] args){
        Float gap=spacingCalculation.get();
        if(gap==null||!spacingActive()||args==null||args.length!=4||!Boolean.FALSE.equals(args[0])
                ||!(args[1] instanceof Float)||!(args[3] instanceof Float))return args;
        float extra=spacingExtra((Float)args[3],1f,gap);if(extra==0f)return args;
        Object[] result=args.clone();result[1]=(Float)args[1]+extra;return result;
    }
    /** Run after native onMeasure; only this container's desired width includes the same gaps. */
    public void measureSpacing(View owner,int widthSpec){
        if(!spacingActive()||!nativeContainer(owner)||!phoneArea(owner))return;
        ViewGroup container=(ViewGroup)owner;
        try{
            float gap=NumericPolicy.pixels(NumericPolicy.finite(settings.get(SPACING),0f),owner.getResources().getDisplayMetrics().density);
            int count=(int)Math.min(container.getChildCount(),Math.max(0L,(long)maxIcons.getInt(owner)+1L));
            double desired=((Number)actualPaddingStart.invoke(owner)).floatValue()+((Number)actualPaddingEnd.invoke(owner)).floatValue();
            for(int i=0;i<count;i++){
                View child=container.getChildAt(i);int width=child.getMeasuredWidth();desired+=width;
                if(i+1<count)desired+=spacingExtra(width,1f,gap);
            }
            if(!Double.isFinite(desired))return;
            int wanted=(int)Math.ceil(Math.max(0d,Math.min(0x00ffffff,desired)));
            int mode=View.MeasureSpec.getMode(widthSpec),limit=View.MeasureSpec.getSize(widthSpec);
            if(mode==View.MeasureSpec.EXACTLY)wanted=limit;else if(mode==View.MeasureSpec.AT_MOST)wanted=Math.min(wanted,limit);
            int widthState=owner.getMeasuredWidthAndState()&0xff000000;
            setMeasuredDimension.invoke(owner,wanted|widthState,owner.getMeasuredHeight());
        }catch(ReflectiveOperationException|RuntimeException unavailable){/* A missing measurement bridge retains native width. */}
    }

    /** Before the exact native setter: preserve its requested value even while ours is applied. */
    public int nativeMaxIcons(View owner,int requested) {
        if(released||Boolean.TRUE.equals(applyingCount.get())||maxIcons==null||!nativeContainer(owner))return requested;
        ViewGroup container=(ViewGroup)owner;State state=states.get(container);
        if(!phoneArea(owner)) {
            if(state!=null){state.countOwned=false;forgetRestored(container,state);}
            return requested;
        }
        if(state==null){state=new State();states.put(container,state);}
        state.nativeMax=requested;state.dirty=true;
        if(!countActive()){state.countOwned=false;return requested;}
        state.countOwned=true;state.appliedMax=safeCount(settings.get(MAX_COUNT),container.getChildCount());
        return state.appliedMax;
    }

    /** Before native measure/translation calculation; no replacement layout or visibility writes. */
    public void beforeLayout(View owner) {
        if(released||Boolean.TRUE.equals(applyingCount.get())||!nativeContainer(owner)||maxIcons==null)return;
        ViewGroup container=(ViewGroup)owner;State state=states.get(container);
        if(!phoneArea(owner)){if(state!=null){restoreCount(container,state,false);forgetRestored(container,state);}return;}
        if(state==null){state=new State();states.put(container,state);}
        applyCount(container,state,false);
    }
    public boolean nativeContainer(Object owner){return containerClass!=null&&containerClass.isInstance(owner);}
    public void detach(View owner) {
        if(!(owner instanceof ViewGroup))return;
        ViewGroup container=(ViewGroup)owner;State state=states.get(container);
        if(state!=null)restoreCount(container,state,false);
        if(state!=null)forgetRestored(container,state);else phoneBindings.remove(container);
        probes.remove(container);
    }
    private void forgetRestored(ViewGroup owner,State state) {
        // Keep a failed restoration weakly tracked so a later config/native event can retry.
        if(!state.countOwned)states.remove(owner);
        for(WeakReference<View> ref:state.icons){View icon=ref.get();NativeSize size=icon==null?null:nativeSizes.get(icon);
            if(size!=null&&size.owner.get()==owner){nativeSizes.remove(icon);icon.invalidate();}}
        for(WeakReference<View> ref:state.icons){View icon=ref.get();if(icon!=null)packages.remove(icon);}
        phoneBindings.remove(owner);
    }
    private void invalidateNativeIcons(ViewGroup owner){
        if(iconClass==null)return;
        for(int i=0;i<owner.getChildCount();i++){View child=owner.getChildAt(i);if(iconClass.isInstance(child))child.invalidate();}
    }
    public void releaseRuntime(){
        boolean restoreSpacing=spacingActive();
        if(released)return;enabled=false;overrides.releaseRuntime();applicationIcons.releaseRuntime();settings=new Bundle();imageGeneration++;bitmap=null;imageRequest="";
        for(ViewGroup owner:new ArrayList<>(states.keySet())){
            State state=states.get(owner);if(state!=null)restoreCount(owner,state,true);
            invalidateNativeIcons(owner);owner.invalidate();
        }
        if(restoreSpacing)refreshSpacing();
        states.clear();nativeSizes.clear();packages.clear();phoneBindings.clear();probes.clear();released=true;spacingReady=false;spacingCalculation.remove();images.shutdownNow();drawingGlyph.remove();glyphPools.remove();
    }
    private void applyCount(ViewGroup owner,State state,boolean refresh) {
        if(maxIcons==null||Boolean.TRUE.equals(applyingCount.get()))return;
        if(!countActive()||!phoneArea(owner)){restoreCount(owner,state,refresh);return;}
        try {
            int current=maxIcons.getInt(owner);
            if(!state.countOwned||current!=state.appliedMax)state.nativeMax=current;
            int wanted=safeCount(settings.get(MAX_COUNT),owner.getChildCount());
            state.countOwned=true;state.appliedMax=wanted;
            if(current!=wanted){writeCount(owner,wanted,refresh);state.dirty=true;}
        } catch(Throwable error) {restoreCount(owner,state,refresh,false);countFailure(error);}
    }
    private void restoreCount(ViewGroup owner,State state,boolean refresh) {
        restoreCount(owner,state,refresh,true);
    }
    private void restoreCount(ViewGroup owner,State state,boolean refresh,boolean observeNativeChange) {
        if(!state.countOwned||maxIcons==null)return;
        try {
            int current=maxIcons.getInt(owner);
            // A direct native resource update wins over our older snapshot.
            // A failed write is rolled back against the saved baseline, not its partial value.
            if(observeNativeChange&&current!=state.appliedMax)state.nativeMax=current;
            try {if(current!=state.nativeMax)writeCount(owner,state.nativeMax,refresh);}
            finally {if(maxIcons.getInt(owner)==state.nativeMax)state.countOwned=false;state.dirty=true;}
        } catch(Throwable error){countFailure(error);}
    }
    private void writeCount(ViewGroup owner,int wanted,boolean refresh) throws ReflectiveOperationException {
        boolean previous=applyingCount.get();applyingCount.set(true);
        try {
            // Android 17's exact setter only assigns mMaxIcons. Writing that same field
            // avoids re-entering the external setter hook and permits atomic rollback if
            // the subsequent native state processor throws.
            maxIcons.setInt(owner,wanted);
            if(refresh){updateState.invoke(owner);owner.requestLayout();}
        } finally {if(previous)applyingCount.set(true);else applyingCount.remove();}
    }
    private void countFailure(Throwable error) {
        if(!countWarning){countWarning=true;ModuleDiagnostics.error("hook","Native notification icon count unavailable; original layout retained",error);}
    }
    static boolean nativeLight(int color){return Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114<128000;}
    private int customColor(int nativeTint){String key=nativeLight(nativeTint)?"notification_icons_color_light":"notification_icons_color_dark";
        int color=settings.getInt(key,nativeTint);return settings.getBoolean(StatusBarSettings.alphaKey(key),false)?color:(color|0xff000000);}
    private int nativeTint(View icon,int fallback){
        try{
            int color=((Number)tint.invoke(icon)).intValue();
            if(color!=0)return color;
            // Native 0 means keep the app artwork uncolored. onDarkChanged writes the actual
            // DarkIconDispatcher tone to mDecorColor, which is suitable for a mono glyph.
            if(decorColor!=null){color=((Number)decorColor.get(icon)).intValue();if(color!=0)return color;}
        }catch(Exception ignored){ }
        return fallback;
    }
    private boolean phoneArea(View owner) {
        WeakReference<ViewGroup> binding=phoneBindings.get(owner);
        ViewGroup boundArea=binding==null?null:binding.get();
        if(boundArea!=null){
            for(View node=owner;node!=null;){
                if(node==boundArea)return true;
                ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;
            }
            phoneBindings.remove(owner);
        }
        boolean area=false;
        for(View node=owner;node!=null;){
            String name="";try{if(node.getId()!=View.NO_ID)name=node.getResources().getResourceEntryName(node.getId());}catch(Exception ignored){ }
            if("notification_icon_area".equals(node.getTag())||"notificationIcons".equals(name)||"notification_icon_area".equals(name))area=true;
            if(area && ("status_bar".equals(name)||"status_bar_start_side_container".equals(name)||"status_bar_start_side_content".equals(name)))return true;
            ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;
        }
        return false;
    }
    private void probe(ViewGroup owner,int stage,String message){
        if(!ModuleDiagnostics.enabled())return;
        Integer previous=probes.get(owner);
        if(previous!=null&&(previous&stage)!=0||previous==null&&probes.size()>=16)return;
        probes.put(owner,(previous==null?0:previous)|stage);
        ModuleDiagnostics.info("hooks","Notification icons "+message+" class "+owner.getClass().getName());
    }
    private static final class State {
        boolean dirty=true;float height;int nativeTint=Color.WHITE;
        float rawWidth,rawHeight,matrixHeight,nativeScale;
        boolean countOwned;int nativeMax,appliedMax;
        final ArrayList<WeakReference<View>> icons=new ArrayList<>();
        final Map<View,Long> sizeDiagnostics=new WeakHashMap<>();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final Paint.FontMetrics metrics=new Paint.FontMetrics();final RectF target=new RectF(),nativeBounds=new RectF();
        int filterColor;android.graphics.PorterDuffColorFilter colorFilter;
        State(){paint.setTextAlign(Paint.Align.CENTER);}
        android.graphics.PorterDuffColorFilter filter(int color){if(colorFilter==null||filterColor!=color){filterColor=color;colorFilter=new android.graphics.PorterDuffColorFilter(color,android.graphics.PorterDuff.Mode.SRC_IN);}return colorFilter;}
    }
    private static final class NativeSize {
        final WeakReference<ViewGroup> owner;final float height,target,nativeScale,factor;
        final NotificationIconOverrides.Rule rule;final long revision;final int color;final Paint paint,tintPaint;final float textCenter;
        final boolean colorOverride;final Bitmap artwork;final RectF imageTarget;
        NativeSize(ViewGroup owner,float height,float target,float nativeScale,NotificationIconOverrides.Rule rule,long revision,int color,boolean colorOverride,Bitmap artwork){
            this.owner=new WeakReference<>(owner);this.height=height;this.target=target;this.nativeScale=nativeScale;
            this.factor=NumericPolicy.scale((double)target/height,height);
            this.rule=rule;this.revision=revision;this.color=color;this.colorOverride=colorOverride;this.artwork=artwork;
            imageTarget=artwork==null?null:new RectF();
            if(colorOverride){tintPaint=new Paint(Paint.ANTI_ALIAS_FLAG);tintPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(color,android.graphics.PorterDuff.Mode.SRC_IN));}
            else tintPaint=null;
            if(rule!=null&&nativeScale>0&&Float.isFinite(nativeScale)){
                paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                if(rule.isAppIcon()){textCenter=0;return;}
                paint.setTextAlign(Paint.Align.CENTER);paint.setColor(color);
                float side=NumericPolicy.textPixels(target/nativeScale);paint.setTextSize(side);
                Paint.FontMetrics metrics=new Paint.FontMetrics();paint.getFontMetrics(metrics);
                float measured=Math.max(paint.measureText((CharSequence)rule.text,0,rule.text.length()),metrics.descent-metrics.ascent);
                if(measured>side)paint.setTextSize(NumericPolicy.textPixels((double)side*side/measured));
                paint.getFontMetrics(metrics);textCenter=(metrics.ascent+metrics.descent)/2f;
            }else{paint=null;textCenter=0;}
        }
    }
    private static final class PackageIdentity {
        final WeakReference<Object> source;final String packageName;
        PackageIdentity(Object source,String packageName){this.source=new WeakReference<>(source);this.packageName=packageName;}
    }
    private static final class GlyphScope {View icon;Canvas canvas;NativeSize baseline;GlyphScope previous;}
    private static final class GlyphPool {
        int depth;final GlyphScope[] frames=new GlyphScope[16];
        GlyphPool(){for(int i=0;i<frames.length;i++)frames[i]=new GlyphScope();}
    }
}
