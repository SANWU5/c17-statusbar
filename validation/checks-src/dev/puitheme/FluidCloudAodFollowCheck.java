package dev.puitheme;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.StatusBarStateControllerImpl;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.oplus.systemui.notification.aod.OplusPanoramicAodCoreStartable;
import com.oplus.systemui.notification.aod.notification.OplusAodRowBackgroundDrawable;
import com.oplus.systemui.notification.row.material.NotificationBlurMode;
import com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp;
import com.oplusos.systemui.common.blurability.ViewBlurProxy;

/**
 * Lock-screen and AOD colour following: a card that comes up while the screen is in AOD (its native black mask is set before the
 * module has registered it), the flat tint ordinary notifications take in AOD, a track change in a settled AOD, the media colour that
 * lasts as long as the media does, the switch back to the lock composition at the start of the wake, and the optional "same card
 * colour in AOD and on the lock screen".
 */
public final class FluidCloudAodFollowCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final android.content.res.Resources PALETTE=new android.content.res.Resources() {
        @Override public int getIdentifier(String name,String type,String pkg){return name.equals("notification_material_background_color_compass")?7:0;}
        @Override public int getColor(int id,Theme theme){return 0xff667788;}
    };
    private static final class Body extends NotificationBackgroundView {
        @Override public int getWidth(){return 240;}
        @Override public android.content.res.Resources getResources(){return PALETTE;}
    }
    private static final class Card {
        final Body body=new Body();final GradientDrawable plate=new GradientDrawable();
        final NotificationBackgroundViewExtImp ext=(NotificationBackgroundViewExtImp)body.mExt;
        final ViewBlurProxy proxy=ext.proxyForCheck;
        final ExpandableNotificationRow row;
        Card(FluidCloudStyle style){
            body.actualHeight=120;body.mOnKeyguard=true;plate.setColor(0xff444444);proxy.styleForCheck=style;ext.maskStyleForCheck=style;
            row=new ExpandableNotificationRow(body.getContext(),120,0,0);body.parent=(View)row;
            ext.notifyBlurParamChanged((NotificationBlurMode)style.sceneBlurMode(ext,NotificationBlurMode.WALLPAPER_A));
            ext.viewBlurManager.mode=NotificationBlurMode.WALLPAPER_A;
        }
    }
    private static final class Rig {
        final FluidCloudStyle style=new FluidCloudStyle();final FluidCloudAccent accent=new FluidCloudAccent();final C17HighlightRemoval control=new C17HighlightRemoval();
        final Bundle options=new Bundle();final StatusBarStateControllerImpl state=new StatusBarStateControllerImpl();
        final kotlinx.coroutines.flow.p colour;final com.oplus.systemui.plugins.shared.template.section.media.s1 section;
        Rig(boolean media)throws Exception{
            Handler.postedForCheck.clear();android.os.SystemClock.uptime=1000;
            style.setAccentController(accent);ClassLoader loader=FluidCloudAodFollowCheck.class.getClassLoader();
            style.resolveScene(loader);style.resolveNotification(loader);style.resolveMedia(loader);
            style.setNotificationStyler(new FluidCloudStyle.NotificationStyler(){
                public void update(View body,boolean follow,boolean keep){control.setFluidNotificationBackground(body,follow,keep);}
                public void forget(View body){control.forgetFluidNotificationBackground(body);}
                public void lockMediaComposition(View body,boolean active){control.setLockMediaComposition(body,active);}
                public void mediaFollow(Integer color){control.setMediaFollowColor(color);}
                public void gradient(View body,Integer color){control.setFluidGradient(body,color);}
                public void overlay(View body,Integer color,java.util.function.BooleanSupplier gate){control.setFluidOverlay(body,color,gate);}
            });style.setKeyguard(true);
            ViewGroup mediaRoot=new ViewGroup(new android.content.Context());
            colour=new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(0xffa34759));
            com.oplus.systemui.plugins.shared.template.section.media.K0 model=new com.oplus.systemui.plugins.shared.template.section.media.K0(colour);
            section=new com.oplus.systemui.plugins.shared.template.section.media.s1(mediaRoot.getContext(),model,mediaRoot,null);
            ((kotlinx.coroutines.flow.p)model.U.b).value=Boolean.TRUE;
            options.putBoolean(FluidCloudAccent.ENABLED,true);options.putBoolean(FluidCloudStyle.MASTER,true);
            options.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);options.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);
            options.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);options.putBoolean(FluidCloudStyle.NOTIFICATION_FOLLOW,true);
            options.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);options.putString(FluidCloudStyle.BACKGROUND_MODE,"native");
            options.putString(FluidCloudStyle.COLOR_STYLE,FluidCloudStyle.SOLID);options.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);options.putFloat(FluidCloudAccent.LOCK_LIGHT,0f);options.putFloat(FluidCloudAccent.AOD_LIGHT,0f);
            options.putFloat(FluidCloudAccent.OPACITY,40f);accent.configure(options);style.configure(options);
            if(media)style.mediaChanged(section);
            drain();
        }
        void apply(){accent.configure(options);style.configure(options);drain();}
        void asleep(boolean dozing){state.state=1;state.dozing=dozing;style.onNativeState(state);}
        void release(){style.release();control.release();accent.release();drain();}
    }
    private static Integer overlay(C17HighlightRemoval control,View body)throws Exception{return fade(control,"fluidOverlays",body);}
    private static Integer gradient(C17HighlightRemoval control,View body)throws Exception{return fade(control,"fluidGradients",body);}
    private static Integer fade(C17HighlightRemoval control,String name,View body)throws Exception{
        java.lang.reflect.Field field=C17HighlightRemoval.class.getDeclaredField(name);field.setAccessible(true);
        C17HighlightRemoval.Fade value=(C17HighlightRemoval.Fade)((java.util.Map<?,?>)field.get(control)).get(body);
        return value==null||value.leaving?null:Integer.valueOf(value.to);
    }
    private static void drain(){Handler.drainForCheck();}
    /** The native AOD progress, as the OEM repository (one object for every card) reports it: 0 awake, 1 settled AOD. */
    private static void progress(FluidCloudStyle style,float value,Card... cards){
        for(Card card:cards){OplusPanoramicAodCoreStartable aod=(OplusPanoramicAodCoreStartable)card.ext.oplusPanoramicAodRepository;
            style.prepareSceneAodProgress(aod,value);aod.progressForCheck(value);}
        for(Card card:cards)style.sceneAodProgressChanged(card.ext.oplusPanoramicAodRepository,value);
        drain();
    }

    public static void main(String[] args)throws Throwable{
        followsThroughAod();
        mediaColourLasts();
        unifiedAodMask();
        lockWithoutMasters();
        disableDuringAod();
        unifyOnlyMasterOff();
        System.out.println("FluidCloudAodFollowCheck: "+checks+" checks passed");
    }

    /** Independent notification/glass options remain neutral while media accent is off. */
    private static void lockWithoutMasters()throws Exception{
        Rig rig=new Rig(true);FluidCloudStyle style=rig.style;C17HighlightRemoval control=rig.control;
        rig.options.putBoolean(FluidCloudAccent.ENABLED,false);rig.options.putBoolean(FluidCloudStyle.MASTER,false);
        rig.options.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);rig.options.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);
        rig.options.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,false);
        rig.apply();
        Card card=new Card(style);style.sceneMaterialChanged(card.ext);drain();
        check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"media master off: the ordinary notification's follow option cannot source media colour");
        rig.options.putBoolean(FluidCloudStyle.NOTIFICATION_FOLLOW,false);rig.apply();
        check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"its own switch off: it does not follow");
        rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,true);rig.apply();
        check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"unified AOD keeps neutral glass without bypassing the media master");
        rig.options.putBoolean(FluidCloudAccent.ENABLED,true);rig.apply();
        check(gradient(control,card.body)!=null&&(gradient(control,card.body)&0xffffff)==0xa34759,"the saved independent policy follows again only after media accent is enabled");
        rig.options.putBoolean(FluidCloudAccent.ENABLED,false);rig.apply();
        check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"turning media accent off again clears the unified notification's colour");
        rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,false);rig.apply();
        check(gradient(control,card.body)==null,"unified off: not any more");
        rig.release();
    }

    /** The master must also revoke a proven AOD hold and a cached colour while the native media view is detached. */
    private static void disableDuringAod()throws Exception{
        for(float boundary:new float[]{.5f,1f}){
            Rig rig=new Rig(true);FluidCloudStyle style=rig.style;C17HighlightRemoval control=rig.control;
            rig.options.putBoolean(FluidCloudStyle.LOCK_CAPSULE,true);
            rig.options.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);
            rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,true);rig.apply();style.setMediaPlaying(true);drain();
            Card card=new Card(style);style.sceneMaterialChanged(card.ext);drain();
            check(gradient(control,card.body)!=null,"the regression starts with a visible media colour");
            java.lang.reflect.Field cache=FluidCloudStyle.class.getDeclaredField("lastFollowMedia");cache.setAccessible(true);
            java.lang.reflect.Field cacheTime=FluidCloudStyle.class.getDeclaredField("lastFollowMediaAt");cacheTime.setAccessible(true);
            check(cache.get(style)!=null,"live following has populated the lock media cache");
            rig.asleep(true);progress(style,boundary,card);
            java.lang.reflect.Field materialField=FluidCloudStyle.class.getDeclaredField("sceneMaterial");materialField.setAccessible(true);
            FluidCloudSceneMaterial material=(FluidCloudSceneMaterial)materialField.get(style);
            check(material.holdingMediaComposition(card.body),"real native glass has a held media composition at AOD progress "+boundary);
            java.lang.reflect.Field bindings=FluidCloudAccent.class.getDeclaredField("media");bindings.setAccessible(true);
            for(Object binding:new java.util.ArrayList<>(((java.util.Map<?,?>)bindings.get(rig.accent)).values())){
                java.lang.reflect.Field attached=binding.getClass().getDeclaredField("attached");attached.setAccessible(true);attached.setBoolean(binding,false);
            }
            check(rig.accent.nativeCurrentMediaColor(null)==null,"the original media view is absent, so the regression exercises cached following");
            style.refreshAfterAccent();style.mediaOwnershipChanged();drain();
            check(gradient(control,card.body)!=null||overlay(control,card.body)!=null,"before disable, a detached playing media view correctly retains its cached AOD colour");
            rig.options.putBoolean(FluidCloudAccent.ENABLED,false);rig.apply();style.refreshAfterAccent();style.mediaOwnershipChanged();drain();
            check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"disable revokes the held colour during both the crossfade and settled AOD");
            check(cache.get(style)==null&&cacheTime.getLong(style)==0L,"disable discards cached following and its timestamp instead of resurrecting the old album colour");
            check(style.lockCapsulePlan(true)==null&&style.lockCapsulePlan(false)==null,"neither media nor non-media lock/AOD capsule bypasses the disabled master");
            OplusAodRowBackgroundDrawable nativeMask=new OplusAodRowBackgroundDrawable(card.body.getContext());nativeMask.setColor(0x78000000);
            card.ext.setPanoramicAodDrawable(nativeMask);
            check(card.ext.panoramicAodDrawable!=nativeMask&&((ColorDrawable)card.ext.panoramicAodDrawable).getColor()==0x78667788,
                    "the independent unified-AOD option retains neutral glass after media tint is removed");
            rig.colour.value=new com.oplus.systemui.plugins.shared.data.media.model.a(0xff12789a);style.mediaChanged(rig.section);drain();
            check(gradient(control,card.body)==null&&overlay(control,card.body)==null&&cache.get(style)==null,"media updates while disabled cannot refill the cache or recolour the sleeping card");
            rig.asleep(false);progress(style,0f,card);
            check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"waking never restores the revoked held colour");
            rig.options.putBoolean(FluidCloudAccent.ENABLED,true);rig.apply();
            Integer latest=gradient(control,card.body);
            check(latest!=null&&(latest&0xffffff)==0x12789a&&(latest>>>24)==102,"re-enabling follows the current track with the saved gradient and 40% opacity");
            rig.release();
        }
    }

    /** Accent-only change must refresh an already coloured ordinary row even when no remaining style requests media. */
    private static void unifyOnlyMasterOff()throws Exception{
        Rig rig=new Rig(true);FluidCloudStyle style=rig.style;C17HighlightRemoval control=rig.control;
        rig.options.putBoolean(FluidCloudStyle.MASTER,false);rig.options.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);
        rig.options.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,false);rig.options.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,false);
        rig.options.putBoolean(FluidCloudStyle.NOTIFICATION_FOLLOW,false);rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,true);
        for(String own:new String[]{FluidCloudAccent.LOCK_GRADIENT,FluidCloudAccent.AOD_GRADIENT,FluidCloudAccent.LOCK_CUSTOM,FluidCloudAccent.AOD_CUSTOM})rig.options.putBoolean(own,false);
        rig.apply();Card card=new Card(style);style.sceneMaterialChanged(card.ext);drain();
        check(overlay(control,card.body)!=null&&gradient(control,card.body)==null,"unified AOD alone follows media while the media master is enabled");
        rig.asleep(true);progress(style,1f,card);
        check(overlay(control,card.body)!=null,"the unified-only ordinary row is media coloured in settled AOD");
        rig.options.putBoolean(FluidCloudAccent.ENABLED,false);
        // Exercise only the accent listener: no row rebind, style configure, native state event or polling helps cleanup.
        rig.accent.configure(rig.options);drain();
        check(overlay(control,card.body)==null&&gradient(control,card.body)==null,"accent-only master disable cleans an old ordinary colour even when all media policies are now inactive");
        OplusAodRowBackgroundDrawable nativeMask=new OplusAodRowBackgroundDrawable(card.body.getContext());nativeMask.setColor(0x78000000);
        card.ext.setPanoramicAodDrawable(nativeMask);
        check(card.ext.panoramicAodDrawable!=nativeMask&&((ColorDrawable)card.ext.panoramicAodDrawable).getColor()==0x78667788,
                "unified-only ordinary row keeps neutral AOD registration after its old media colour is revoked");
        rig.release();
    }

    private static void followsThroughAod()throws Exception{
        Rig rig=new Rig(true);FluidCloudStyle style=rig.style;C17HighlightRemoval control=rig.control;
        // 1. Awake lock screen: the ordinary card follows the media colour with the common gradient.
        Card awake=new Card(style);style.sceneMaterialChanged(awake.ext);drain();
        check(gradient(control,awake.body)!=null&&(gradient(control,awake.body)&0xffffff)==0xa34759,"the awake ordinary card follows the media colour with the gradient");
        check(overlay(control,awake.body)==null,"the awake card is not given a flat layer as well");

        // 2. The screen goes to AOD. During the crossfade the awake gradient stays (the native animation needs that recipe);
        // once the AOD has settled the card has no wallpaper under it, so it takes the same colour as one flat tint.
        Integer awakeGradient=gradient(control,awake.body);
        rig.asleep(true);progress(style,.5f,awake);drain();
        check(awakeGradient.equals(gradient(control,awake.body)),"during the AOD crossfade the awake gradient is untouched");
        progress(style,1f,awake);
        check(overlay(control,awake.body)!=null&&gradient(control,awake.body)==null,"once the AOD has settled the card takes one flat tint, not a tint fading into black");
        check((overlay(control,awake.body)&0xffffff)==0xa34759,"and it is the same media colour");

        // 3. A card that comes up in AOD: its native black mask is set before the module has registered it.
        Card late=new Card(style);
        progress(style,1f,awake,late);
        OplusAodRowBackgroundDrawable nativeMask=new OplusAodRowBackgroundDrawable(late.body.getContext());nativeMask.setColor(0x78000000);nativeMask.setBounds(0,0,240,80);
        late.ext.setPanoramicAodDrawable(nativeMask);
        check(late.ext.panoramicAodDrawable==nativeMask,"an unregistered card keeps its native mask for now");
        style.sceneMaterialChanged(late.ext);drain();
        check(late.ext.panoramicAodDrawable!=nativeMask&&late.ext.panoramicAodDrawable instanceof ColorDrawable
                &&((ColorDrawable)late.ext.panoramicAodDrawable).getColor()==0x78667788,
                "once registered, the card that came up in AOD swaps the native black mask for the neutral glass");
        check(nativeMask.getColor()==0x78000000,"the shared native mask is never edited");
        check(overlay(control,late.body)!=null&&gradient(control,late.body)==null,
                "a card that came up in AOD takes the flat tint: a gradient would fade out into the AOD's dark base");
        check((overlay(control,late.body)&0xffffff)==0xa34759,"the flat tint is the media colour");

        // 4. A track change while the AOD is settled recolours every card.
        progress(style,1f,awake,late);
        rig.colour.value=new com.oplus.systemui.plugins.shared.data.media.model.a(0xff12789a);style.mediaChanged(rig.section);drain();
        check((overlay(control,late.body)&0xffffff)==0x12789a,"the card that came up in AOD follows the new track");
        check((overlay(control,awake.body)&0xffffff)==0x12789a,"the card that was awake follows the new track in settled AOD");

        // 5. Waking: the card is back on the lock composition the moment the screen is awake, while the AOD is still fading out, so the
        // change of colour layer happens under the dark start of the wake and not when the transition ends.
        rig.asleep(false);drain();
        check(gradient(control,late.body)!=null&&gradient(control,awake.body)!=null&&overlay(control,awake.body)==null,
                "at the start of the wake both cards are back on the gradient, before the AOD progress has even returned");
        progress(style,0f,awake,late);
        check((gradient(control,late.body)&0xffffff)==0x12789a&&gradient(control,late.body).equals(gradient(control,awake.body)),
                "after waking both cards use the same gradient, one colour");
        check(overlay(control,late.body)==null&&overlay(control,awake.body)==null,"and no flat tint is left over");

        // 6. AOD has its own gradient switch, for every card alike: on, the AOD keeps a gradient.
        rig.options.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);rig.apply();
        rig.asleep(true);progress(style,1f,awake,late);
        check(gradient(control,awake.body)!=null&&gradient(control,late.body)!=null&&overlay(control,late.body)==null,
                "with the AOD gradient on the AOD keeps a gradient");
        rig.release();
    }

    /** The music card is not always bound while the screen sleeps: the colour lasts as long as the media does, not 20 seconds. */
    private static void mediaColourLasts()throws Exception{
        Rig rig=new Rig(true);FluidCloudStyle style=rig.style;C17HighlightRemoval control=rig.control;
        style.setMediaPlaying(true);
        rig.options.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,true);rig.apply();
        Card card=new Card(style);style.sceneMaterialChanged(card.ext);drain();
        Integer before=gradient(control,card.body);
        check(before!=null,"playing: the ordinary card follows");
        Object accentMedia=null;
        for(java.lang.reflect.Field field:FluidCloudAccent.class.getDeclaredFields()) if(field.getName().equals("media")){field.setAccessible(true);accentMedia=field.get(rig.accent);}
        check(accentMedia instanceof java.util.Map,"the accent keeps its media bindings in a map");
        for(Object binding:new java.util.ArrayList<>(((java.util.Map<?,?>)accentMedia).values())){
            java.lang.reflect.Field attached=binding.getClass().getDeclaredField("attached");attached.setAccessible(true);attached.setBoolean(binding,false);
        }
        style.refreshAfterAccent();style.mediaOwnershipChanged();drain();
        check(gradient(control,card.body)!=null&&gradient(control,card.body).equals(before),
                "a media card that is briefly detached while the music plays keeps the colour instead of dropping to the native one");
        // A long AOD: ten minutes later the card still has the music's colour, so waking changes nothing.
        rig.asleep(true);progress(style,1f,card);
        android.os.SystemClock.uptime+=10L*60L*1000L;style.mediaOwnershipChanged();drain();
        check(overlay(control,card.body)!=null&&(overlay(control,card.body)&0xffffff)==(before&0xffffff),"ten minutes of AOD later it still has the colour");
        rig.asleep(false);progress(style,0f,card);
        check(gradient(control,card.body)!=null&&(gradient(control,card.body)&0xffffff)==(before&0xffffff),"and waking brings the same colour back, no change");
        // It ends when playback does, or when the media card is really gone.
        style.setMediaPlaying(false);style.expireStopGraceForCheck();drain();
        check(gradient(control,card.body)==null&&overlay(control,card.body)==null,"once playback has stopped the card goes back to its native material");
        style.setMediaPlaying(true);drain();
        check(gradient(control,card.body)!=null,"playing again follows again");
        style.mediaDisposed(rig.section);drain();
        check(gradient(control,card.body)==null,"a media card that is really gone leaves no colour to follow");
        rig.release();
    }

    /** No media playing: with the option the AOD card keeps the lock card's neutral glass instead of the black AOD mask. */
    private static void unifiedAodMask()throws Exception{
        Rig rig=new Rig(false);FluidCloudStyle style=rig.style;
        Card plain=new Card(style);style.sceneMaterialChanged(plain.ext);drain();
        rig.asleep(true);progress(style,1f,plain);
        OplusAodRowBackgroundDrawable nativeMask=new OplusAodRowBackgroundDrawable(plain.body.getContext());nativeMask.setColor(0x78000000);nativeMask.setBounds(0,0,240,80);
        plain.ext.setPanoramicAodDrawable(nativeMask);
        check(plain.ext.panoramicAodDrawable==nativeMask,"with nothing to follow, and the option off, the AOD card keeps the system's black mask");
        rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,true);rig.apply();
        style.sceneMaterialChanged(plain.ext);drain();
        plain.ext.setPanoramicAodDrawable(nativeMask);
        check(plain.ext.panoramicAodDrawable instanceof ColorDrawable&&((ColorDrawable)plain.ext.panoramicAodDrawable).getColor()==0x78667788,
                "with the option on the AOD card takes the lock card's neutral glass in place of the black mask");
        check(nativeMask.getColor()==0x78000000,"the system's mask object is never edited");
        rig.options.putBoolean(LockNotificationSettings.UNIFY_AOD,false);rig.apply();
        plain.ext.setPanoramicAodDrawable(nativeMask);
        check(plain.ext.panoramicAodDrawable==nativeMask,"switching it off gives the black mask back");
        rig.release();
    }
}
