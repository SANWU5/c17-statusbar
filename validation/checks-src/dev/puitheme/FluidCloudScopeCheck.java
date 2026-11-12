package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.plugins.custom.CustomRowEntry;
import com.android.systemui.statusbar.StatusBarStateControllerImpl;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.heytap.log.nx.obus.a;
import com.oplus.systemui.notification.row.material.NotificationBlurMode;
import com.oplus.systemui.plugins.seedling.card.ui.model.o;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardBackgroundView;
import com.oplus.systemui.plugins.seedling.card.ui.view.CardView;
import com.oplus.systemui.plugins.shared.template.component.light.f;
import com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView;
import com.oplus.systemui.plugins.shared.template.section.media.K0;
import com.oplus.systemui.plugins.shared.template.section.media.s1;
import com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow;
import com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp;
import com.oplus.view.ViewRootManager;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

/** Real style controllers and OEM contract stubs exercise category changes, native restoration and independent pages. */
public final class FluidCloudScopeCheck {
    private static int checks;
    private static final String TIMER_SERVICE="268451854";
    private static final int MEDIA=0xff2468ac;
    private static void equal(Object expected,Object actual,String why){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Fluid scope "+checks+" "+why+": "+expected+" != "+actual);}
    private static void check(boolean value,String why){equal(true,value,why);}
    private static void drain(){Handler.drainForCheck();}
    private static Bundle allDisabled(){Bundle settings=new Bundle();for(FluidCloudScope.Category category:FluidCloudScope.Category.values())settings.putBoolean(category.key,false);return settings;}

    private static void policy(){
        equal(13,FluidCloudScope.Category.values().length,"thirteen independently selectable categories");
        for(Bundle settings:new Bundle[]{null,new Bundle(),SettingsSnapshot.fromPreferences(java.util.Collections.emptyMap())}){
            FluidCloudScope.Policy policy=FluidCloudScope.Policy.from(settings);
            for(FluidCloudScope.Category category:FluidCloudScope.Category.values())equal(true,policy.allows(category),"defaults preserve every old category");
        }
        for(FluidCloudScope.Category disabled:FluidCloudScope.Category.values()){
            Bundle settings=new Bundle();settings.putBoolean(disabled.key,false);FluidCloudScope.Policy policy=FluidCloudScope.Policy.from(settings);
            for(FluidCloudScope.Category category:FluidCloudScope.Category.values())equal(category!=disabled,policy.allows(category),"one explicit opt-out is isolated");
            settings.putBoolean(disabled.key,true);equal(false,policy.allows(disabled),"policy retains an immutable settings snapshot");
            equal(true,FluidCloudScope.Policy.from(settings).allows(disabled),"later true restores only its category");
            for(Object wrong:new Object[]{null,0,1,0f,"false"}){
                Bundle malformed=new Bundle();
                if(wrong instanceof Integer)malformed.putInt(disabled.key,(Integer)wrong);
                else if(wrong instanceof Float)malformed.putFloat(disabled.key,(Float)wrong);
                else malformed.putString(disabled.key,(String)wrong);
                equal(true,FluidCloudScope.Policy.from(malformed).allows(disabled),"wrong runtime type preserves old coverage");
            }
        }
        FluidCloudScope.Policy disabled=FluidCloudScope.Policy.from(allDisabled());
        for(FluidCloudScope.Category category:FluidCloudScope.Category.values())equal(false,disabled.allows(category),"all categories can be opted out");
        equal(false,disabled.allows(null),"missing classification follows the other switch");
        equal(FluidCloudScope.Policy.DEFAULT,FluidCloudScope.Policy.from(new Bundle()),"equivalent defaults compare equally");
        equal(FluidCloudScope.Policy.from(allDisabled()),disabled,"equivalent opt-outs compare equally");
    }
    private static void classification(){
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify(null,null,false),"missing identity remains other");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("","com.android.deskclock",false),"publisher alone cannot distinguish timer and alarm");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("opaque-instance-key","com.sankuai.meituan",false),"one application cannot distinguish multiple delivery businesses");
        equal(FluidCloudScope.Category.TIMER,FluidCloudScope.classify(TIMER_SERVICE,"",false),"identified countdown uses timer switch");
        equal(FluidCloudScope.Category.TIMER,FluidCloudScope.classify("268451943","",false),"second registered countdown uses timer switch");
        for(String service:new String[]{"268451917","268451944","268451937"})equal(FluidCloudScope.Category.ALARM,FluidCloudScope.classify(service,"",false),"registered alarm service uses alarm switch");
        for(String service:new String[]{"268452006","default_com.android.systemui_268452006"})equal(FluidCloudScope.Category.MEDIA,FluidCloudScope.classify(service,"",false),"registered media service uses media switch");
        for(String service:new String[]{"default_com.android.deskclock_268451854","default_com.example.app_268452006","default_com.oplus.pantanal.ums_536875052"})
            equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify(service,"",false),"unproven default wrapper may contain an instance number and must not be decoded");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("countdown","",false),"display words cannot establish business identity");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("unknown_268451854","",false),"arbitrary numeric suffix cannot impersonate registered service");
        equal(FluidCloudScope.Category.MEDIA,FluidCloudScope.classify("","",true),"proven media owner uses media switch");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("future-business","",false),"future card stays configurable through other");
        String publisher="com.oplus.pantanal.ums";
        String[][] services={{"536875051","536877203","536877202"},{"536875052","536877201","536878642","536878973"},
                {"536875050","268451931"},{"536878265","536878933","268452041"},{"268452031","536879377","536879383","536879312"},
                {"536871053","536871054","536871055","536871056","536879177"},{"268451902","268451924"},{"268451845"},
                {"268451855","536874238","536879148","536874236","536879151","536874237"}};
        FluidCloudScope.Category[] categories={FluidCloudScope.Category.RIDE_HAILING,FluidCloudScope.Category.FOOD_DELIVERY,
                FluidCloudScope.Category.FOOD_PICKUP,FluidCloudScope.Category.PARCEL_PICKUP,FluidCloudScope.Category.NAVIGATION,
                FluidCloudScope.Category.TRAVEL,FluidCloudScope.Category.BATTERY,FluidCloudScope.Category.EARPHONES,FluidCloudScope.Category.SMART_SUGGESTIONS};
        for(int index=0;index<services.length;index++)for(String service:services[index])equal(categories[index],FluidCloudScope.classify(service,publisher,false),"one publisher retains its distinct registered businesses");
        equal(FluidCloudScope.Category.OTHER,FluidCloudScope.classify("536899999",publisher,false),"same publisher with unknown service remains other");
    }
    private static final class Resources extends android.content.res.Resources {
        @Override public String getResourceEntryName(int id){return id==1?"card_background_mix_color":id==2?"card_background_gradient_middle_color":"card_content_title_color";}
    }
    private static final class Floating implements AutoCloseable {
        final FluidCloudStyle style=new FluidCloudStyle();final CardView card=new CardView();
        final CardBackgroundView host=new CardBackgroundView(new Resources());final ViewRootManager manager=new ViewRootManager();final a helper=new a(null,3);
        Floating()throws Exception{
            Handler.postedForCheck.clear();a.controller=null;a.style=style;ViewRootManager.style=style;
            a.nativeBackgroundForCheck=null;a.nativeBackgroundFactoryForCheck=null;style.resolve(FluidCloudScopeCheck.class.getClassLoader());card.addView(host);bind(TIMER_SERVICE);
        }
        void bind(String service){style.cardBound(card,new o(service));helper.b(host,manager);}
        void apply(Bundle settings){style.configure(settings);drain();style.finishFillFadesForCheck();}
        @Override public void close(){style.release();drain();a.style=null;a.controller=null;ViewRootManager.style=null;}
    }
    private static Bundle transparentStyle(){Bundle settings=new Bundle();settings.putBoolean(FluidCloudStyle.MASTER,true);settings.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);settings.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");settings.putFloat(FluidCloudStyle.BACKGROUND_OPACITY,0f);return settings;}
    private static void floatingRestore()throws Exception{
        try(Floating env=new Floating()){
            Bundle settings=transparentStyle();env.apply(settings);equal(0f,env.manager.saved[7],"missing scope keys keep old transparent appearance");
            settings.putBoolean(FluidCloudScope.Category.ALARM.key,false);env.apply(settings);
            env.style.cardBound(env.card,new o("268451917"));drain();check(env.manager.saved[7]>0f,"bind event alone removes stale timer appearance when the same card becomes an opted-out alarm");
            env.style.cardBound(env.card,new o(TIMER_SERVICE));drain();equal(0f,env.manager.saved[7],"bind event alone restores timer appearance on the reused card");
            settings.putBoolean(FluidCloudScope.Category.TIMER.key,false);env.apply(settings);
            check(env.manager.saved[7]>0f,"opting out restores the native material alpha");equal(0xb3262626,a.mixed,"opting out restores actual native input colour");equal(0xff000000,a.gradient,"opting out restores actual native gradient colour");
            env.bind("future-business");equal(0f,env.manager.saved[7],"same view reused for an enabled unknown card gets its style");
            env.bind(TIMER_SERVICE);check(env.manager.saved[7]>0f,"same view rebound to disabled timer restores native material again");
            settings.putBoolean(FluidCloudScope.Category.TIMER.key,true);env.apply(settings);equal(0f,env.manager.saved[7],"reenabling timer reapplies its saved appearance");
            settings.putBoolean(FluidCloudScope.Category.OTHER.key,false);env.apply(settings);equal(0f,env.manager.saved[7],"other opt-out cannot affect known timer");
            env.bind("future-business");check(env.manager.saved[7]>0f,"other opt-out covers unrecognized business");
            Bundle highlights=allDisabled();highlights.putBoolean(C17HighlightRemoval.ENABLED,true);env.apply(highlights);
            equal(2f,env.manager.saved[3],"independent C17 highlight removal survives all category opt-outs");check(env.manager.saved[7]>0f,"independent highlight removal preserves native fill");
            env.bind(TIMER_SERVICE);Bundle backgroundWithHighlight=transparentStyle();backgroundWithHighlight.putBoolean(FluidCloudScope.Category.TIMER.key,false);backgroundWithHighlight.putBoolean(C17HighlightRemoval.ENABLED,true);env.apply(backgroundWithHighlight);
            int gradientWithSavedBackground=a.gradient;equal(2f,env.manager.saved[3],"disabled timer still receives independent highlight removal with background settings saved");
            backgroundWithHighlight.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,false);env.apply(backgroundWithHighlight);
            equal(gradientWithSavedBackground,a.gradient,"disabled timer's independent highlight removal has the same native gradient input with its saved background on or off");
            equal(0,a.gradient,"independent C17 removal clears the native gradient highlight in both cases");
        }
    }
    private static void diagnosticFacts()throws Exception{
        JSONObject raw=new JSONObject().put("scope_category","timer").put("scope_allowed",false)
                .put("service_id",TIMER_SERVICE).put("business_package","com.oplus.pantanal.ums")
                .put("private_payload",new JSONObject().put("order","fixture-private-order-8273"))
                .put("notification_text","fixture-private-notification").put("pickup_code","fixture-private-pickup-1234");
        JSONObject fact=DiagnosticReport.validateFact(new JSONObject().put("topic","fluid").put("values",raw));
        check(fact!=null,"fluid facts remain accepted");JSONObject safe=fact.getJSONObject("values");
        equal("timer",safe.getString("scope_category"),"export retains category identity");equal(false,safe.getBoolean("scope_allowed"),"export retains category decision");
        equal(TIMER_SERVICE,safe.getString("service_id"),"export retains audited service identifier");equal("com.oplus.pantanal.ums",safe.getString("business_package"),"export retains publishing package");
        for(String name:new String[]{"private_payload","notification_text","pickup_code"})equal(false,safe.has(name),"private card data is not an allowed diagnostic field");
        for(String secret:new String[]{"fixture-private-order-8273","fixture-private-notification","fixture-private-pickup-1234"})check(!fact.toString().contains(secret),"private values cannot enter the exported fact");
        JSONObject invalid=new JSONObject().put("service_id","订单 fixture-private-order-8273").put("business_package","/data/user/0/private-payload");
        JSONObject rejected=DiagnosticReport.validateFact(new JSONObject().put("topic","fluid").put("values",invalid)).getJSONObject("values");
        equal(false,rejected.has("service_id"),"notification content cannot masquerade as a service identifier");equal(false,rejected.has("business_package"),"private paths cannot masquerade as a publishing package");
    }
    private static final class Output implements FluidCloudStyle.NotificationStyler {
        final Map<View,Boolean> active=new IdentityHashMap<>();
        final Map<View,Integer> gradients=new IdentityHashMap<>(),overlays=new IdentityHashMap<>();
        @Override public void update(View body,boolean follow,boolean keep){active.put(body,follow);}
        @Override public void forget(View body){active.remove(body);gradients.remove(body);overlays.remove(body);}
        @Override public void gradient(View body,Integer color){gradients.put(body,color);}
        @Override public void overlay(View body,Integer color,java.util.function.BooleanSupplier needed){overlays.put(body,color);}
    }
    private static OplusCustomRow row(FluidCloudStyle style,Output output,String service,View card){
        OplusCustomRow row=new OplusCustomRow();row.setMBackgroundNormal(new NotificationBackgroundView());CustomRowEntry entry=new CustomRowEntry(service,"");entry.immersiveCardView=card;row.setEntry(entry);style.notificationRow(row);drain();return row;
    }
    private static void notificationRestore()throws Exception{
        try(Floating env=new Floating()){
            env.style.resolveNotification(FluidCloudScopeCheck.class.getClassLoader());Output output=new Output();env.style.setNotificationStyler(output);
            OplusCustomRow row=row(env.style,output,TIMER_SERVICE,env.card);NotificationBackgroundView body=row.getMBackgroundNormal();
            Bundle settings=new Bundle();settings.putBoolean(FluidCloudStyle.MASTER,true);settings.putBoolean(FluidCloudStyle.FOLLOW_NOTIFICATION,true);env.apply(settings);
            equal(true,output.active.get(body),"identified fluid notification follows style by default");
            settings.putBoolean(FluidCloudScope.Category.TIMER.key,false);env.apply(settings);equal(false,output.active.get(body),"timer opt-out restores native fluid notification");
            row.setEntry(new CustomRowEntry("future-business",""));env.style.notificationRow(row);drain();equal(true,output.active.get(body),"reused notification body adopts enabled new business");
            row.setEntry(new CustomRowEntry(TIMER_SERVICE,""));env.style.notificationRow(row);drain();equal(false,output.active.get(body),"reused notification body drops stale style for disabled timer");
            settings.putBoolean(FluidCloudScope.Category.TIMER.key,true);env.apply(settings);equal(true,output.active.get(body),"timer follow switch reenables current body");
        }
    }
    private static void independentAccent()throws Exception{
        try(Floating env=new Floating()){
            FluidCloudAccent accent=new FluidCloudAccent();accent.resolve(FluidCloudScopeCheck.class.getClassLoader());env.style.setAccentController(accent);a.controller=accent;
            try{
                ViewGroup media=new ViewGroup(null);env.card.addView(media);kotlinx.coroutines.flow.p flow=new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(MEDIA));
                s1 section=new s1(null,new K0(flow),media,null);env.helper.b(env.host,env.manager);accent.mediaChanged(section);
                Bundle settings=allDisabled();settings.putBoolean(FluidCloudAccent.ENABLED,true);accent.configure(settings);env.apply(settings);env.helper.b(env.host,env.manager);
                equal(MEDIA&0xffffff,a.mixed&0xffffff,"independent media accent survives category opt-out");equal(MEDIA,a.gradient,"independent media accent keeps its native gradient colour");
                equal(4f,env.manager.saved[3],"scope opt-out does not introduce highlight removal");
            }finally{accent.release();drain();}
        }
    }
    private static void followedMediaRestore()throws Exception{
        try(Floating env=new Floating()){
            FluidCloudAccent accent=new FluidCloudAccent();accent.resolve(FluidCloudScopeCheck.class.getClassLoader());env.style.setAccentController(accent);a.controller=accent;
            try{
                ViewGroup otherCard=new ViewGroup(null);kotlinx.coroutines.flow.p flow=new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(MEDIA));
                s1 section=new s1(null,new K0(flow),otherCard,null);accent.mediaChanged(section);
                Bundle settings=new Bundle();settings.putBoolean(FluidCloudAccent.ENABLED,true);settings.putBoolean(FluidCloudStyle.MASTER,true);settings.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND,true);
                settings.putBoolean(FluidCloudStyle.FOLLOW_MEDIA,true);settings.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);settings.putString(FluidCloudStyle.BACKGROUND_MODE,"custom");
                settings.putInt(FluidCloudStyle.BACKGROUND_COLOR,0xffa03020);accent.configure(settings);env.apply(settings);env.helper.b(env.host,env.manager);
                equal(MEDIA&0xffffff,a.mixed&0xffffff,"timer follows current media colour from a different card");
                settings.putBoolean(FluidCloudScope.Category.TIMER.key,false);env.apply(settings);equal(0xb3262626,a.mixed,"timer opt-out restores native colour instead of keeping followed media or custom base");
                settings.putBoolean(FluidCloudScope.Category.TIMER.key,true);env.apply(settings);equal(MEDIA&0xffffff,a.mixed&0xffffff,"reenabling timer resumes its saved media following");
                // Interrupt an actual track-colour handover. Do not use Floating.apply here: its finish-fade seam would hide a leaked timer.
                java.lang.reflect.Field mainField=FluidCloudStyle.class.getDeclaredField("main");mainField.setAccessible(true);Handler animation=(Handler)mainField.get(env.style);
                final int nextMedia=0xffb04a62;flow.value=new com.oplus.systemui.plugins.shared.data.media.model.a(nextMedia);accent.mediaChanged(section);drain();
                Runnable interrupted=animation.delayed;
                check(interrupted!=null&&animation.delay==FluidCloudStyle.FLOATING_FRAME_MS,"changing the followed track schedules the real floating colour handover");
                settings.putBoolean(FluidCloudScope.Category.TIMER.key,false);env.style.configure(settings);drain();
                equal(0xb3262626,a.mixed,"scope off during a track handover restores the native fill immediately");
                check(animation.delayed==null,"scope off cancels the unfinished colour handover callback");
                int scheduled=animation.scheduled;interrupted.run();
                check(animation.delayed==null&&animation.scheduled==scheduled,"a cancelled handover callback already dispatched cannot requeue itself");
                settings.putBoolean(FluidCloudScope.Category.TIMER.key,true);env.style.configure(settings);drain();
                equal(nextMedia&0xffffff,a.mixed&0xffffff,"reenabling after interrupted handover starts from the latest media colour");
                check(animation.delayed==null,"reopening a native card never resumes the previous business handover");
            }finally{accent.release();drain();}
        }
    }
    private static void independentLockAndOrdinary()throws Exception{
        Handler.postedForCheck.clear();FluidCloudStyle style=new FluidCloudStyle();FluidCloudAccent accent=new FluidCloudAccent();Output output=new Output();
        try{
            ClassLoader loader=FluidCloudScopeCheck.class.getClassLoader();style.setAccentController(accent);style.resolveScene(loader);style.resolveNotification(loader);style.resolveMedia(loader);style.setNotificationStyler(output);
            Bundle settings=allDisabled();settings.putBoolean(FluidCloudStyle.MASTER,false);settings.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);settings.putBoolean(FluidCloudStyle.NOTIFICATION_FOLLOW,true);settings.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);
            // Compare ownership using the original media colour, without the lock page's default 20% black mask.
            settings.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY,0f);
            accent.configure(settings);style.configure(settings);style.setKeyguard(true);StatusBarStateControllerImpl state=new StatusBarStateControllerImpl();state.state=1;style.onNativeState(state);
            ViewGroup media=new ViewGroup(new android.content.Context());MediaBackGroundView mediaBackground=new MediaBackGroundView();mediaBackground.F=new f();mediaBackground.setBackground(mediaBackground.F);media.addView(mediaBackground);
            kotlinx.coroutines.flow.p flow=new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(MEDIA));K0 model=new K0(flow);((kotlinx.coroutines.flow.p)model.U.b).value=Boolean.TRUE;
            s1 section=new s1(media.getContext(),model,media,null);section.q=mediaBackground;style.mediaChanged(section);
            OplusCustomRow music=row(style,output,"",media);music.getMBackgroundNormal().mOnKeyguard=true;
            OplusCustomRow timer=row(style,output,TIMER_SERVICE,new ViewGroup(new android.content.Context()));timer.getMBackgroundNormal().mOnKeyguard=true;
            NotificationBackgroundView ordinary=new NotificationBackgroundView();ordinary.mOnKeyguard=true;ordinary.parent=new ExpandableNotificationRow(ordinary.getContext(),120,0,0);
            NotificationBackgroundViewExtImp extension=(NotificationBackgroundViewExtImp)ordinary.mExt;extension.proxyForCheck.styleForCheck=style;
            extension.notifyBlurParamChanged((NotificationBlurMode)style.sceneBlurMode(extension,NotificationBlurMode.WALLPAPER_A));extension.viewBlurManager.mode=NotificationBlurMode.WALLPAPER_A;style.sceneMaterialChanged(extension);
            style.refreshAfterAccent();drain();style.finishFillFadesForCheck();
            equal(false,style.notificationUsesAccent(music.getMBackgroundNormal()),"independent lock custom cannot bypass the media-accent master");
            equal(null,style.normalNotificationColor(music.getMBackgroundNormal()),"media card remains neutral while the media-accent master is off");
            equal(null,style.normalNotificationColor(timer.getMBackgroundNormal()),"timer cannot follow media through an independent lock look while the master is off");
            equal(null,style.normalNotificationColor(ordinary),"ordinary notification follow cannot bypass the master either");
            settings.putBoolean(FluidCloudAccent.ENABLED,true);accent.configure(settings);style.configure(settings);drain();style.finishFillFadesForCheck();
            equal(true,style.notificationUsesAccent(music.getMBackgroundNormal()),"independent lock custom still styles a media card with all scopes off");
            equal(MEDIA,style.normalNotificationColor(music.getMBackgroundNormal()),"independent lock media keeps current album colour");
            equal(MEDIA,style.normalNotificationColor(timer.getMBackgroundNormal()),"independent lock custom still colours an opted-out timer card");
            equal(true,output.active.get(ordinary),"ordinary notification follow is independent of business scope");equal(MEDIA,style.normalNotificationColor(ordinary),"ordinary notification keeps current media colour");
            settings.putBoolean(FluidCloudStyle.NOTIFICATION_FOLLOW,false);accent.configure(settings);style.configure(settings);drain();style.finishFillFadesForCheck();
            check(!Boolean.TRUE.equals(output.active.get(ordinary)),"ordinary follow still obeys its own switch");equal(MEDIA,style.normalNotificationColor(timer.getMBackgroundNormal()),"ordinary switch cannot disable independent lock card appearance");
            settings.putBoolean(FluidCloudAccent.ENABLED,false);accent.configure(settings);drain();style.finishFillFadesForCheck();
            equal(null,style.normalNotificationColor(music.getMBackgroundNormal()),"accent-only disable revokes independent media-card colouring");
            equal(null,style.normalNotificationColor(timer.getMBackgroundNormal()),"accent-only disable revokes independent timer following");
        }finally{style.release();accent.release();drain();}
    }
    public static void main(String[] args)throws Exception{policy();classification();floatingRestore();diagnosticFacts();notificationRestore();independentAccent();followedMediaRestore();independentLockAndOrdinary();System.out.println("FluidCloudScopeCheck: "+checks+" checks passed");}
}
