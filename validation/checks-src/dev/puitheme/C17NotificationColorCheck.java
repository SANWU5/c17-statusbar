package dev.puitheme;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.oplus.posteffect.drawable.BlendDrawable;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.qs.media.QsMediaSpotLightHelper;
import com.oplus.systemui.qs.media.multilight.MultiLightDrawable;
import com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable;
import com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp;
import com.oplusos.systemui.common.outline.CornerOutlineProvider;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/** Background-only OEM branching, scope isolation and separate native media optics. */
public final class C17NotificationColorCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);}
    private static void same(Object expected,Object actual){checks++;if(expected!=actual)throw new AssertionError("identity changed");}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.00001f)throw new AssertionError("expected "+expected+" got "+actual);}
    private static Object field(Object owner,String name)throws Exception{Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(owner);}
    private static Bundle settings(boolean master,boolean notification,boolean control,boolean uniform){
        Bundle result=new Bundle();result.putBoolean(C17HighlightRemoval.ENABLED,master);
        result.putBoolean(C17HighlightRemoval.NOTIFICATION_ENABLED,notification);result.putBoolean(C17HighlightRemoval.CONTROL_ENABLED,control);
        result.putBoolean(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,uniform);
        result.putInt(C17HighlightRemoval.LIGHT_BACKGROUND,0x80aabbcc);result.putInt(C17HighlightRemoval.DARK_BACKGROUND,0x40112233);return result;
    }
    private static void bind(C17HighlightRemoval helper,View host,BlendDrawable engine){try(C17HighlightRemoval.SurfaceScope ignored=helper.beginSurface(host)){helper.bindDrawable(engine);}}
    private static final class Icon extends Drawable {
        int draws,drawSaveCount;
        public void draw(Canvas canvas){draws++;drawSaveCount=canvas.getSaveCount();}
        public void setAlpha(int value){}public void setColorFilter(ColorFilter value){}public int getOpacity(){return -3;}
    }
    public static int run()throws Throwable{
        equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(C17HighlightRemoval.NOTIFICATION_ENABLED));
        equal(true,StatusBarSettings.BOOLEAN_DEFAULTS.get(C17HighlightRemoval.CONTROL_ENABLED));
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED));
        equal(SettingsCatalog.OTHER,SettingsCatalog.group("c17_highlight_removal").category);
        equal(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED,SettingsCatalog.item(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED).key);
        independentScopes();backgroundBranches();mediaOptics();return checks;
    }

    private static void independentScopes()throws Throwable{
        C17HighlightRemoval helper=new C17HighlightRemoval();NotificationBackgroundView notification=new NotificationBackgroundView();
        OplusQSResizeableTileView control=new OplusQSResizeableTileView();View unknown=new View(null);
        BlendDrawable notificationEngine=new BlendDrawable(),controlEngine=new BlendDrawable();
        C17HighlightRemovalCheck.NativeMaterial nm=new C17HighlightRemovalCheck.NativeMaterial(),cm=new C17HighlightRemovalCheck.NativeMaterial();
        C17HighlightRemovalCheck.HookedShader ns=new C17HighlightRemovalCheck.HookedShader(helper),cs=new C17HighlightRemovalCheck.HookedShader(helper);
        nm.shader=ns;cm.shader=cs;nm.optics.values[3]=.7f;cm.optics.values[3]=.8f;
        QsTileAppearance.setField(notificationEngine,"drawableShader",nm);QsTileAppearance.setField(controlEngine,"drawableShader",cm);
        nm.optics.pushUniforms(ns);cm.optics.pushUniforms(cs);bind(helper,notification,notificationEngine);bind(helper,control,controlEngine);
        helper.configure(settings(true,true,false,false));near(0,ns.floats.get("u_opticsArray")[3]);near(.8f,cs.floats.get("u_opticsArray")[3]);
        equal(true,helper.skipSpotlight(notification));equal(false,helper.skipSpotlight(control));equal(false,helper.skipSpotlight(unknown));
        helper.configure(settings(true,false,true,false));near(.7f,ns.floats.get("u_opticsArray")[3]);near(0,cs.floats.get("u_opticsArray")[3]);
        equal(false,helper.skipSpotlight(notification));equal(true,helper.skipSpotlight(control));equal(false,helper.skipSpotlight(unknown));
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(notificationEngine)){equal(false,C17HighlightRemoval.removesOptics(notificationEngine));}
        try(C17HighlightRemoval.RecordScope record=helper.beginRecord(controlEngine)){equal(true,C17HighlightRemoval.removesOptics(controlEngine));}
        // Native recycling/reparenting must not retain the enabled page's masked uniform.
        bind(helper,notification,controlEngine);near(.8f,cs.floats.get("u_opticsArray")[3]);
        bind(helper,control,controlEngine);near(0,cs.floats.get("u_opticsArray")[3]);
        // An unknown top status bar cannot inherit the enclosing control-center policy.
        try(C17HighlightRemoval.SurfaceScope outer=helper.beginSurface(control)){
            equal(true,helper.skipSpotlight(null));
            try(C17HighlightRemoval.SurfaceScope inner=helper.beginSurface(unknown)){equal(false,helper.skipSpotlight(null));}
            equal(true,helper.skipSpotlight(null));
        }
        helper.release();near(.8f,cs.floats.get("u_opticsArray")[3]);equal(false,helper.skipSpotlight(control));
    }

    private static void backgroundBranches()throws Throwable{
        C17HighlightRemoval helper=new C17HighlightRemoval();NotificationBackgroundView host=new NotificationBackgroundView();
        host.actualWidth=200;host.actualHeight=150;host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
        NotificationBackgroundViewExtImp extension=new NotificationBackgroundViewExtImp(host);Icon icon=new Icon();extension.icon(icon);
        Canvas canvas=new Canvas();int[] nativeDraws={0};
        helper.drawNotificationBackgroundExtension(extension,canvas,c->{nativeDraws[0]++;equal(false,helper.deferNotificationIcon(extension,c));return "native";});
        equal(0,canvas.layers);equal(1,nativeDraws[0]);
        // Unified color is a separately selectable operation, independent of optical removal.
        helper.configure(settings(false,true,true,true));
        Object result=helper.drawNotificationBackgroundExtension(extension,canvas,c->{
            nativeDraws[0]++;equal(2,c.getSaveCount());equal(true,helper.deferNotificationIcon(extension,c));equal(0,icon.draws);
            // OEM fallback calls NotificationBackgroundView.draw; it must not tint twice.
            return helper.drawNotificationBackground(host,c,nested->{equal(2,nested.getSaveCount());return "colorized/native-fallback";});
        });
        equal("colorized/native-fallback",result);equal(1,canvas.layers);equal(2,nativeDraws[0]);equal(1,icon.draws);equal(1,icon.drawSaveCount);equal(1,canvas.getSaveCount());
        near(-77,canvas.layerLeft);near(-1,canvas.layerTop);near(201,canvas.layerRight);near(151,canvas.layerBottom);
        Paint light=canvas.lastLayerPaint;PorterDuffColorFilter filter=(PorterDuffColorFilter)light.getColorFilter();
        equal(0x80aabbcc,filter.getColor());equal(PorterDuff.Mode.SRC_ATOP,filter.getMode());equal(255,light.getAlpha());
        // The alpha of SRC_ATOP is native alpha: the common tint mixes, not replaces, blur.
        for(int i=0;i<1000;i++){
            Object branch=helper.drawNotificationBackgroundExtension(extension,canvas,c->{nativeDraws[0]++;return "platform/wallpaper/MCS/material/stack/AOD";});
            equal("platform/wallpaper/MCS/material/stack/AOD",branch);same(light,canvas.lastLayerPaint);equal(1,canvas.getSaveCount());
        }
        equal(1002,nativeDraws[0]);equal(1001,canvas.layers);
        host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_YES;
        helper.drawNotificationBackgroundExtension(extension,canvas,c->null);Paint dark=canvas.lastLayerPaint;
        equal(false,light==dark);equal(0x40112233,((PorterDuffColorFilter)dark.getColorFilter()).getColor());
        helper.drawNotificationBackgroundExtension(extension,canvas,c->null);same(dark,canvas.lastLayerPaint);
        // Bad/unavailable native layer still invokes the original exactly once.
        canvas.failLayerOnce=true;int before=nativeDraws[0];int layers=canvas.layers;
        helper.drawNotificationBackgroundExtension(extension,canvas,c->{nativeDraws[0]++;equal(false,helper.deferNotificationIcon(extension,c));return null;});
        equal(before+1,nativeDraws[0]);equal(layers,canvas.layers);equal(1,canvas.getSaveCount());
        // Exception and recursion must unwind both thread-local tint and native canvas saves.
        try{helper.drawNotificationBackgroundExtension(extension,canvas,c->{equal(true,helper.deferNotificationIcon(extension,c));throw new IllegalStateException("native");});throw new AssertionError("lost failure");}
        catch(IllegalStateException expected){equal("native",expected.getMessage());}
        equal(2,icon.draws);equal(1,icon.drawSaveCount);equal(1,canvas.getSaveCount());
        equal(false,helper.deferNotificationIcon(extension,canvas));same(null,field(helper,"tintingNotification") instanceof ThreadLocal ? ((ThreadLocal<?>)field(helper,"tintingNotification")).get():new Object());
        // No similarly named owner, control center, or tiny invalid native bounds is flattened.
        helper.drawNotificationBackgroundExtension(new Object(),canvas,c->{nativeDraws[0]++;return null;});equal(1,canvas.getSaveCount());
        layers=canvas.layers;helper.drawNotificationBackground(new OplusQSResizeableTileView(),canvas,c->null);equal(layers,canvas.layers);
        host.actualWidth=0;helper.drawNotificationBackgroundExtension(extension,canvas,c->null);equal(layers,canvas.layers);host.actualWidth=200;
        helper.configure(settings(true,false,true,true));helper.drawNotificationBackgroundExtension(extension,canvas,c->null);equal(layers,canvas.layers);
        Bundle safe=settings(true,true,true,true);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        helper.drawNotificationBackgroundExtension(extension,canvas,c->null);equal(layers,canvas.layers);
        helper.configure(settings(true,true,true,true));helper.drawNotificationBackgroundExtension(extension,canvas,c->null);equal(layers+1,canvas.layers);
        helper.release();helper.drawNotificationBackgroundExtension(extension,canvas,c->null);equal(layers+1,canvas.layers);
        equal(0,((Map<?,?>)field(helper,"notificationAccess")).size());
        host.getResources().getConfiguration().uiMode=Configuration.UI_MODE_NIGHT_NO;
    }

    private static void mediaOptics()throws Throwable{
        C17HighlightRemoval helper=new C17HighlightRemoval();MultiLightDrawable light=new MultiLightDrawable();Canvas canvas=new Canvas();
        OplusQsMediaBackgroundDrawable parent=new OplusQsMediaBackgroundDrawable(new CornerOutlineProvider(20f,1f));light.setCallback(parent);
        equal(false,helper.skipNativeMediaLight(light));light.draw(canvas);equal(1,light.draws);
        helper.configure(settings(true,true,false,false));equal(false,helper.skipNativeMediaLight(light));
        helper.configure(settings(true,false,true,false));equal(true,helper.skipNativeMediaLight(light));equal(true,parent.opticalInvalidations>0);
        int invalidations=parent.opticalInvalidations;for(int i=0;i<1000;i++)equal(true,helper.skipNativeMediaLight(light));equal(invalidations,parent.opticalInvalidations);
        light.setCallback(new NotificationBackgroundView());equal(false,helper.skipNativeMediaLight(light));
        light.setCallback(null);equal(false,helper.skipNativeMediaLight(light));light.setCallback(parent);
        Icon arbitrary=new Icon();arbitrary.setCallback(parent);equal(false,helper.skipNativeMediaLight(arbitrary));
        com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView media=new com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView();
        QsMediaSpotLightHelper nativeHelper=new QsMediaSpotLightHelper(media);equal(true,helper.skipNativeMediaSpotlight(nativeHelper));
        for(int i=0;i<1000;i++)equal(true,helper.skipNativeMediaSpotlight(nativeHelper));equal(1,((Map<?,?>)field(helper,"mediaSpotHosts")).size());
        media.attached=false;equal(false,helper.skipNativeMediaSpotlight(nativeHelper));media.attached=true;
        View child=new View(null);child.parent=media;QsMediaSpotLightHelper nested=new QsMediaSpotLightHelper(child);equal(true,helper.skipNativeMediaSpotlight(nested));
        child.parent=new NotificationBackgroundView();equal(false,helper.skipNativeMediaSpotlight(nested));
        equal(false,helper.skipNativeMediaSpotlight(new QsMediaSpotLightHelper(new NotificationBackgroundView())));
        equal(false,helper.skipNativeMediaSpotlight(new Object()));equal(false,helper.skipNativeMediaSpotlight(null));
        helper.configure(settings(true,true,false,false));equal(false,helper.skipNativeMediaSpotlight(nativeHelper));equal(false,helper.skipNativeMediaLight(light));
        helper.configure(settings(false,true,true,false));equal(false,helper.skipNativeMediaSpotlight(nativeHelper));equal(false,helper.skipNativeMediaLight(light));
        helper.release();equal(false,helper.skipNativeMediaLight(light));equal(0,((Map<?,?>)field(helper,"mediaLights")).size());equal(0,((Map<?,?>)field(helper,"mediaSpotHosts")).size());
    }
}
