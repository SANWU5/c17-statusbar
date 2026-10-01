package dev.puitheme;
import android.os.Bundle;
import android.graphics.Canvas;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.service.notification.StatusBarNotification;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
public final class NotificationIconAreaCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
    private static Object field(Object owner,String name)throws Exception{
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    /** Allocate only metadata: the desktop test never constructs Android drawing members. */
    private static Object metadata(Class<?> type)throws Exception{
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        return unsafeClass.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),type);
    }
    private static final class ScopedResources extends Resources {
        int lookups;
        @Override public String getResourceEntryName(int id){
            lookups++;return id==101?"notificationIcons":id==202?"status_bar":id==404?"aod_notification_icons":"new_statusbar_host";
        }
        @Override public int getIdentifier(String name,String type,String pkg){return "notificationIcons".equals(name)?101:0;}
    }
    private static final class PhoneContainer extends NotificationIconContainer {
        final ScopedResources scoped=new ScopedResources();
        PhoneContainer(){super(null);scoped.getDisplayMetrics().density=1f;}
        @Override public int getId(){return 101;}
        @Override public Resources getResources(){return scoped;}
    }
    private static final class NativeHost extends ViewGroup {
        final int id;final ScopedResources scoped=new ScopedResources();
        NativeHost(int id){super(null);this.id=id;}
        @Override public int getId(){return id;}
        @Override public Resources getResources(){return scoped;}
        @Override public View findViewById(int id){for(int i=0;i<getChildCount();i++)if(getChildAt(i).getId()==id)return getChildAt(i);return null;}
    }
    @SuppressWarnings("unchecked")
    private static void firstEnableDrawing()throws Throwable{
        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconAreaCheck.class.getClassLoader());
        PhoneContainer owner=new PhoneContainer();new NativeHost(202).addView(owner);
        StatusBarIconView first=new StatusBarIconView(null);first.setNotification((StatusBarNotification)metadata(StatusBarNotification.class));owner.addView(first);
        StatusBarIconView second=new StatusBarIconView(null);second.mBundleEntry=new Object();owner.addView(second);
        Map<ViewGroup,Object> states=(Map<ViewGroup,Object>)field(area,"states");

        // Native layout must observe the cached host while the new feature is still OFF.
        area.changed(owner);equal(1,states.size());equal(0,owner.invalidations);
        int[] nativeDraws={0};NotificationIconArea.Draw nativeDraw=()->{nativeDraws[0]++;return null;};
        int lookups=owner.scoped.lookups,reads=first.getterCalls;
        for(int i=0;i<10;i++)area.draw(owner,new Canvas(),nativeDraw);
        equal(10,nativeDraws[0]);equal(lookups,owner.scoped.lookups);equal(reads,first.getterCalls);

        Bundle enabled=new Bundle();enabled.putBoolean(NotificationIconArea.MASTER,true);enabled.putString(NotificationIconArea.MODE,"heart");
        enabled.putBoolean(NotificationIconArea.SIZE_ENABLED,false);
        area.configure(null,enabled);equal(1,owner.invalidations);
        Canvas canvas=new Canvas();area.draw(owner,canvas,nativeDraw);
        equal("♥",canvas.text);equal(255,canvas.textAlpha);equal(10,nativeDraws[0]);equal(canvas.saves,canvas.restores);
        equal(2,((List<?>)field(states.get(owner),"icons")).size());

        // ColorOS preserves full-color app artwork with a zero static tint. Mono symbols use
        // the real DarkIconDispatcher decor tone, including black on a light background.
        first.staticDrawableColor=0;first.mDecorColor=0xff000000;
        canvas=new Canvas();area.draw(owner,canvas,nativeDraw);equal(0xff000000,canvas.textColor);equal(255,canvas.textAlpha);
        first.mDecorColor=0xffffffff;canvas=new Canvas();area.draw(owner,canvas,nativeDraw);equal(0xffffffff,canvas.textColor);

        first.setNotification(null);second.mBundleEntry=null;area.changed(owner);
        canvas=new Canvas();area.draw(owner,canvas,nativeDraw);equal(true,canvas.text==null);equal(11,nativeDraws[0]);
        area.configure(null,new Bundle());equal(3,owner.invalidations); // change event plus disable each invalidate once
        reads=first.getterCalls;lookups=owner.scoped.lookups;
        area.draw(owner,new Canvas(),nativeDraw);equal(12,nativeDraws[0]);equal(reads,first.getterCalls);equal(lookups,owner.scoped.lookups);

        // Bind precisely through the native Phone binder when ancestor names change, then
        // revoke that identity if the same container is reparented outside that bound area.
        NotificationIconArea bound=new NotificationIconArea();bound.resolve(NotificationIconAreaCheck.class.getClassLoader());
        PhoneContainer renamed=new PhoneContainer();NativeHost realArea=new NativeHost(303);realArea.addView(renamed);
        StatusBarIconView live=new StatusBarIconView(null);live.mBundleEntry=new Object();renamed.addView(live);
        bound.changed(renamed);equal(0,((Map<?,?>)field(bound,"states")).size());
        bound.bindPhoneArea(realArea);equal(1,((Map<?,?>)field(bound,"states")).size());equal(0,renamed.invalidations);
        bound.configure(null,enabled);canvas=new Canvas();bound.draw(renamed,canvas,nativeDraw);equal("♥",canvas.text);
        new NativeHost(404).addView(renamed);bound.changed(renamed);
        canvas=new Canvas();bound.draw(renamed,canvas,nativeDraw);equal(true,canvas.text==null);
        equal(0,((Map<?,?>)field(bound,"states")).size());equal(0,((Map<?,?>)field(bound,"phoneBindings")).size());
        equal(true,field(bound,"states") instanceof WeakHashMap);
        equal(true,field(bound,"phoneBindings") instanceof WeakHashMap);
    }
    @SuppressWarnings("unchecked")
    private static void nativeContracts()throws Exception{
        Method privateHeight=StatusBarIconView.class.getDeclaredMethod("getIconHeight");
        equal(true,Modifier.isPrivate(privateHeight.getModifiers()));
        boolean publiclyVisible=true;try{StatusBarIconView.class.getMethod("getIconHeight");}catch(NoSuchMethodException expected){publiclyVisible=false;}
        equal(false,publiclyVisible);

        NotificationIconArea area=new NotificationIconArea();area.resolve(NotificationIconAreaCheck.class.getClassLoader());
        equal(privateHeight,field(area,"iconHeight"));
        equal(StatusBarIconView.class.getMethod("getIconScale"),field(area,"iconScale"));
        equal(StatusBarIconView.class.getDeclaredField("mBundleEntry"),field(area,"bundleEntry"));
        StatusBarIconView icon=new StatusBarIconView(null);
        equal(96f,((Method)field(area,"iconHeight")).invoke(icon)); // Production resolve makes the private getter callable.
        equal(.5f,((Method)field(area,"iconScale")).invoke(icon));

        NotificationIconContainer owner=new NotificationIconContainer(null);
        owner.getResources().getDisplayMetrics().density=2f;
        owner.addView(icon);
        Class<?> stateClass=Class.forName("dev.puitheme.NotificationIconArea$State");
        Object state=metadata(stateClass);
        Field iconsField=stateClass.getDeclaredField("icons");iconsField.setAccessible(true);
        ArrayList<WeakReference<View>> icons=new ArrayList<>();iconsField.set(state,icons);
        Method update=NotificationIconArea.class.getDeclaredMethod("update",ViewGroup.class,stateClass);update.setAccessible(true);

        // A real ordinary notification is non-null; no Binder or Android constructor is needed.
        icon.setNotification((StatusBarNotification)metadata(StatusBarNotification.class));
        update.invoke(area,owner,state);equal(1,icons.size());equal(icon,icons.get(0).get());equal(48f,field(state,"height"));
        icon.nativeScale=.25f;icon.iconAppearAmount=.01f;
        update.invoke(area,owner,state);equal(24f,field(state,"height"));
        icon.iconAppearAmount=1f;update.invoke(area,owner,state);equal(24f,field(state,"height"));
        // Density affects configured dp, while native stable height is intrinsic pixels * native scale.
        owner.getResources().getDisplayMetrics().density=4f;update.invoke(area,owner,state);equal(24f,field(state,"height"));

        icon.setNotification(null);update.invoke(area,owner,state);equal(0,icons.size());
        icon.mBundleEntry=new Object();update.invoke(area,owner,state);equal(1,icons.size());equal(24f,field(state,"height"));
        icon.blocked=true;update.invoke(area,owner,state);equal(0,icons.size());
        icon.blocked=false;icon.visibleState=2;update.invoke(area,owner,state);equal(0,icons.size());
        icon.visibleState=0;icon.setVisibility(View.INVISIBLE);update.invoke(area,owner,state);equal(0,icons.size());
        icon.setVisibility(View.VISIBLE);owner.addView(new View(null));update.invoke(area,owner,state);equal(1,icons.size());

        // Re-applying options must refresh native cached tint/size after a safe-mode interval.
        Map<ViewGroup,Object> states=(Map<ViewGroup,Object>)field(area,"states");states.put(owner,state);
        equal(false,field(state,"dirty"));area.configure(null,new Bundle());equal(true,field(state,"dirty"));
        equal(1,owner.invalidations);
    }
    public static void main(String[] args)throws Throwable{
        nativeContracts();
        firstEnableDrawing();
        equal(false,NotificationIconArea.BOOLEANS.get(NotificationIconArea.MASTER));
        Bundle settings=SettingsSnapshot.fromPreferences(Collections.emptyMap());
        equal(true,SettingsSnapshot.complete(settings));equal(false,NotificationIconArea.active(settings));
        settings.putBoolean(NotificationIconArea.MASTER,true);equal(true,NotificationIconArea.active(settings));
        settings.putBoolean(StatusBarSettings.SAFE_MODE,true);equal(false,NotificationIconArea.active(settings));
        equal(false,SafetyMode.runtimeSettings(settings).get(NotificationIconArea.MASTER));equal(true,settings.get(NotificationIconArea.MASTER));
        for(String mode:new String[]{"native","heart","text","image"}){
            equal(mode,NotificationIconArea.mode(mode));
            Map<String,Object> input=new HashMap<>();input.put(NotificationIconArea.MODE,mode);
            equal(mode,ConfigTransfer.prepare(ConfigTransfer.exportJson(input),true).values().get(NotificationIconArea.MODE));
        }
        equal("native",NotificationIconArea.mode("unknown"));equal("native",NotificationIconArea.mode(null));
        equal("♥",NotificationIconArea.customText(""));equal("♥",NotificationIconArea.customText(null));
        String unicode="💙💙💙💙💙💙💙💙💙💙💙💙";equal(unicode,NotificationIconArea.customText(unicode+"x"));equal(unicode,NotificationIconArea.customText(unicode));
        equal(320f,NotificationIconArea.safeSize(1000f,4f));equal(4f,NotificationIconArea.safeSize(-100f,4f));equal(80f,NotificationIconArea.safeSize(Float.NaN,4f));
        Map<String,Object> metadata=new HashMap<>();metadata.put(NotificationIconArea.IMAGE_NAME,"private.png");metadata.put(NotificationIconArea.IMAGE_REVISION,"local");
        equal(false,ConfigTransfer.exportJson(metadata).contains("private.png"));equal(false,ConfigTransfer.exportJson(metadata).contains("local"));
        for(String bad:new String[]{"1234567890123","a\nb","a\u0000b"}){
            org.json.JSONObject document=new org.json.JSONObject();document.put("package",ConfigTransfer.PACKAGE_NAME);document.put("schema",1);
            document.put("settings",new org.json.JSONObject().put(NotificationIconArea.TEXT,bad));
            boolean rejected=false;try{ConfigTransfer.prepare(document.toString(),true);}catch(java.io.IOException expected){rejected=true;}equal(true,rejected);
        }
        System.out.println(checks+" checks passed (first-enable redraw, native binder ownership, mono tint, private getter/scale, modes, Unicode, safety and local image metadata)");
    }
}
