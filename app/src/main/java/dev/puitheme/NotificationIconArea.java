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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
    public static final Map<String, Boolean> BOOLEANS;
    public static final Map<String, Float> NUMBERS;
    public static final Map<String, Integer> COLORS;
    public static final Map<String, String> STRINGS;
    static {
        Map<String,Boolean> b = new LinkedHashMap<>(); b.put(MASTER,false); b.put(POSITION,true); b.put(SIZE_ENABLED,true); b.put(COLOR_ENABLED,false); BOOLEANS=Collections.unmodifiableMap(b);
        Map<String,Float> n = new LinkedHashMap<>(); n.put(X,0f); n.put(Y,0f); n.put(SIZE,20f); NUMBERS=Collections.unmodifiableMap(n);
        Map<String,Integer> c = new LinkedHashMap<>(); c.put("notification_icons_color_light",Color.BLACK); c.put("notification_icons_color_dark",Color.WHITE); COLORS=Collections.unmodifiableMap(c);
        Map<String,String> s = new LinkedHashMap<>(); s.put(MODE,"native"); s.put(TEXT,"♥"); s.put(IMAGE_NAME,""); s.put(IMAGE_REVISION,""); STRINGS=Collections.unmodifiableMap(s);
    }
    public interface Draw { Object run() throws Throwable; }
    private final Map<ViewGroup,State> states = new WeakHashMap<>();
    private final Map<ViewGroup,WeakReference<ViewGroup>> phoneBindings = new WeakHashMap<>();
    private final Map<ViewGroup,Integer> probes = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor(task -> { Thread t=new Thread(task,"C17-notification-image"); t.setDaemon(true);return t; });
    private Bundle settings = new Bundle();
    private Bitmap bitmap;
    private String displayText="♥";
    private String imageRequest = "";
    private int imageGeneration;
    private volatile boolean enabled;
    private Class<?> containerClass, iconClass;
    private Method visibleState, notification, blocked, iconHeight, iconScale, tint;
    private Field bundleEntry, decorColor;

    public void resolve(ClassLoader loader) throws ReflectiveOperationException {
        containerClass=loader.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer");
        iconClass=loader.loadClass("com.android.systemui.statusbar.StatusBarIconView");
        visibleState=iconClass.getMethod("getVisibleState"); notification=iconClass.getMethod("getNotification");
        blocked=iconClass.getMethod("isIconBlocked"); iconHeight=iconClass.getDeclaredMethod("getIconHeight");iconHeight.setAccessible(true);
        iconScale=iconClass.getMethod("getIconScale");tint=iconClass.getMethod("getStaticDrawableColor");
        try{bundleEntry=iconClass.getDeclaredField("mBundleEntry");bundleEntry.setAccessible(true);}catch(NoSuchFieldException olderAndroid){bundleEntry=null;}
        try{decorColor=iconClass.getDeclaredField("mDecorColor");decorColor.setAccessible(true);}catch(NoSuchFieldException olderAndroid){decorColor=null;}
    }
    public void configure(Context context, Bundle options) {
        settings=new Bundle(options);
        displayText=customText(options.getString(TEXT,"♥"));
        enabled=active(options);
        String wanted=enabled && "image".equals(mode(options.getString(MODE))) ? options.getString(IMAGE_REVISION,"") : "";
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
        for(State state:states.values())state.dirty=true;
        if(ModuleDiagnostics.enabled())ModuleDiagnostics.info("hooks","Notification icons configured "
                +(enabled?"active":"disabled")+" mode "+mode(options.getString(MODE))+" tracked "+states.size());
        else probes.clear();
        invalidate();
    }
    private void invalidate() { for (ViewGroup owner:new ArrayList<>(states.keySet()))if(owner!=null)owner.invalidate(); }
    public boolean ownsDrawing(Object owner) { return enabled && containerClass!=null && containerClass.isInstance(owner); }
    /** The real status-bar binder identifies this host even when its ancestor resource names change. */
    public void bindPhoneArea(ViewGroup area) {
        if(area==null||containerClass==null)return;
        int id=area.getResources().getIdentifier("notificationIcons","id","com.android.systemui");
        View child=id==0?null:area.findViewById(id);
        if(!(child instanceof ViewGroup)||!containerClass.isInstance(child)||child==area)return;
        ViewGroup owner=(ViewGroup)child;
        phoneBindings.put(owner,new WeakReference<>(area));
        changed(owner);
        probe(owner,8,"status-bar binder identified host");
    }
    public void changed(View owner) {
        ViewParent parent=owner instanceof ViewGroup && containerClass!=null&&containerClass.isInstance(owner)?(ViewGroup)owner:owner.getParent();
        if(!(parent instanceof ViewGroup)||containerClass==null||!containerClass.isInstance(parent))return;
        ViewGroup container=(ViewGroup)parent;
        State state=states.get(container);
        if(state==null){
            if(!phoneArea(container)){probe(container,16,"layout outside status-bar scope");return;}
            // Observe native hosts before the default-off setting is enabled. Their cached render
            // nodes must be invalidated on the first configuration change, without waiting for
            // a notification add/remove or layout event.
            state=new State();states.put(container,state);
        }
        state.dirty=true;
        if(enabled)container.invalidate();
        probe(container,1,"native layout or icon event registered host");
    }
    public Object draw(ViewGroup owner,Canvas canvas,Draw nativeDraw) throws Throwable {
        if(!active(settings)||containerClass==null||!containerClass.isInstance(owner))return nativeDraw.run();
        probe(owner,2,"native dispatchDraw intercepted");
        State state=states.get(owner);if(state==null){if(!phoneArea(owner))return nativeDraw.run();state=new State();states.put(owner,state);}
        if(state.dirty){if(!phoneArea(owner)){states.remove(owner);phoneBindings.remove(owner);probe(owner,16,"draw outside status-bar scope");return nativeDraw.run();}update(owner,state);}
        String mode=mode(settings.getString(MODE));
        // Missing/invalid imported pixels keep the real icons available.
        if("image".equals(mode)&&bitmap==null)mode="native";
        if(state.icons.isEmpty()){probe(owner,4,"no eligible native notification children");return nativeDraw.run();}
        if(ModuleDiagnostics.enabled())probe(owner,32,"eligible native notification children "+state.icons.size());
        float density=owner.getResources().getDisplayMetrics().density;
        float size=settings.getBoolean(SIZE_ENABLED,true)?safeSize(settings.get(SIZE),density):state.height;
        float x=settings.getBoolean(POSITION,true)?NumericPolicy.pixels(NumericPolicy.finite(settings.get(X),0f),density):0f;
        float y=settings.getBoolean(POSITION,true)?NumericPolicy.pixels(NumericPolicy.finite(settings.get(Y),0f),density):0f;
        int save=canvas.save();
        try {
            canvas.translate(x,y);
            if("native".equals(mode)) {
                float factor=size/Math.max(1f,state.height);
                float pivot=owner.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?owner.getWidth():0f;
                canvas.scale(factor,factor,pivot,owner.getHeight()/2f);
                if(settings.getBoolean(COLOR_ENABLED,false)){
                    int color=customColor(state.nativeTint);
                    state.paint.setAlpha(255);state.paint.setColorFilter(state.filter(color));
                    int layer=canvas.saveLayer(null,state.paint);
                    try{return nativeDraw.run();}finally{canvas.restoreToCount(layer);state.paint.setColorFilter(null);}
                }
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
                state.paint.setTextSize(size);String text="heart".equals(mode)?"♥":displayText;
                state.paint.getFontMetrics(state.metrics);
                canvas.drawText(text,cx,cy-(state.metrics.ascent+state.metrics.descent)/2f,state.paint);
            }
            return null;
        } finally { canvas.restoreToCount(save); }
    }
    private void update(ViewGroup owner,State state) {
        state.icons.clear();state.height=20f*owner.getResources().getDisplayMetrics().density;
        for(int i=0;i<owner.getChildCount();i++){View icon=owner.getChildAt(i);if(!iconClass.isInstance(icon))continue;
            try { int visibility=((Number)visibleState.invoke(icon)).intValue();
                if((notification.invoke(icon)==null&&(bundleEntry==null||bundleEntry.get(icon)==null))||Boolean.TRUE.equals(blocked.invoke(icon))||visibility==2||icon.getVisibility()!=View.VISIBLE)continue;
                state.icons.add(new WeakReference<>(icon));float height=((Number)iconHeight.invoke(icon)).floatValue()*((Number)iconScale.invoke(icon)).floatValue();if(height>0&&Float.isFinite(height))state.height=height;
                state.nativeTint=nativeTint(icon,state.nativeTint);
            } catch(Exception ignored){ }
        }
        state.dirty=false;
    }
    static boolean active(Bundle settings){return settings!=null&&!SafetyMode.enabled(settings)&&!ModuleLifecycle.removed()&&settings.getBoolean(MASTER,false);}
    static String mode(String value){return "heart".equals(value)||"text".equals(value)||"image".equals(value)?value:"native";}
    static String customText(String value){if(value==null||value.trim().isEmpty())return "♥";int count=value.codePointCount(0,value.length());return count>12?value.substring(0,value.offsetByCodePoints(0,12)):value;}
    static float safeSize(Object value,float density){return NumericPolicy.pixels(Math.max(1f,Math.min(80f,NumericPolicy.finite(value,20f))),density);}
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
        final ArrayList<WeakReference<View>> icons=new ArrayList<>();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final Paint.FontMetrics metrics=new Paint.FontMetrics();final RectF target=new RectF();
        int filterColor;android.graphics.PorterDuffColorFilter colorFilter;
        State(){paint.setTextAlign(Paint.Align.CENTER);}
        android.graphics.PorterDuffColorFilter filter(int color){if(colorFilter==null||filterColor!=color){filterColor=color;colorFilter=new android.graphics.PorterDuffColorFilter(color,android.graphics.PorterDuff.Mode.SRC_IN);}return colorFilter;}
    }
}
