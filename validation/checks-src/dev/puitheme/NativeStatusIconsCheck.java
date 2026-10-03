package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.content.res.Resources;
import android.service.notification.StatusBarNotification;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.phone.StatusIconContainer.StatusIconState;
import java.lang.reflect.Field;
import java.util.*;

/** Exercises the inspected container's two native filters, index scopes and final state application. */
public final class NativeStatusIconsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Hints "+checks+": "+expected+" != "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.001f)throw new AssertionError("Hints "+checks+": "+expected+" != "+actual);}
    private static final int TAG=77;
    private static final class HostResources extends Resources {
        int ids;
        @Override public int getIdentifier(String name,String type,String pkg){ids++;return name.equals("status_bar_view_state_tag")&&type.equals("id")&&pkg.equals("com.android.systemui")?TAG:0;}
    }
    private static final class Host extends StatusIconContainer {
        final HostResources resources=new HostResources();
        int width=320,height=60,nativeTop=12,screenY;boolean rtl,bypassIndexes,bypassBlocked;
        Host(){resources.getDisplayMetrics().density=1f;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
        @Override public int getPaddingStart(){return 4;}
        @Override public int getPaddingEnd(){return 6;}
        @Override public int getPaddingTop(){return 2;}
        @Override public int getPaddingBottom(){return 3;}
        @Override public int getLayoutDirection(){return rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR;}
        @Override public Resources getResources(){return resources;}
        @Override public void getLocationOnScreen(int[] out){out[0]=0;out[1]=screenY;}
        @Override public View getChildAt(int index){
            if(!bypassIndexes)return super.getChildAt(index);
            NativeStatusIcons helper=hints;hints=null;
            try{return super.getChildAt(index);}finally{hints=helper;}
        }
    }
    private static final class Icon extends StatusBarIconView implements StatusIconDisplayable {
        final String name;String slot;int width=20,height=22,top=12;
        boolean visible=true;
        final StatusIconState state=new StatusIconState();
        int nativeApply,tint=0xffabcdef;
        Icon(String name,String slot){super(null);this.name=name;this.slot=slot;}
        @Override public String getSlot(){return slot;}
        @Override public boolean isIconVisible(){return visible;}
        @Override public boolean isIconBlocked(){Host parent=getParent() instanceof Host?(Host)getParent():null;return parent!=null&&parent.hints!=null&&!parent.bypassBlocked?parent.hints.iconBlocked(this,blocked):blocked;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return height;}
        @Override public int getPaddingStart(){return 1;}
        @Override public int getPaddingEnd(){return 1;}
        @Override public int getTop(){return top;}
        @Override public void offsetTopAndBottom(int value){top+=value;}
        @Override public Object getTag(int key){return key==TAG?state:null;}
    }
    private static NativeStatusIcons helper()throws Exception{NativeStatusIcons h=new NativeStatusIcons();h.resolve(NativeStatusIconsCheck.class.getClassLoader());return h;}
    private static Bundle settings(boolean on,int max,String priority,float spacing,float x,float y){
        Bundle b=new Bundle();b.putBoolean(NativeStatusIcons.MASTER,on);b.putFloat(NativeStatusIcons.MAX,max);b.putString(NativeStatusIcons.PRIORITY,priority);
        b.putFloat(NativeStatusIcons.SPACING,spacing);b.putFloat(NativeStatusIcons.X,x);b.putFloat(NativeStatusIcons.Y,y);return b;
    }
    private static boolean shown(Host host,Icon icon){return icon.visible&&!icon.isIconBlocked()&&!host.mIgnoredSlots.contains(icon.slot);}
    /** Faithful to the DEX: both stages filter independently and updateStates applies after full target calculation. */
    private static Map<String,Float> layout(NativeStatusIcons h,Host host){
        try(NativeStatusIcons.Scope scope=h.begin(host,false)){
            host.mMeasureViews.clear();
            for(int i=0;i<host.getChildCount();i++){Icon child=(Icon)host.getChildAt(i);if(shown(host,child))host.mMeasureViews.add(child);}
        }
        try(NativeStatusIcons.Scope scope=h.begin(host,true)){
            nativeTargets(host);apply(h,host);
        }
        // OEM onLayout centers each child before its AFTER hook.
        for(int i=0;i<host.getChildCount();i++)((Icon)host.getChildAt(i)).top=host.nativeTop;
        h.onLayout(host);
        LinkedHashMap<String,Float> result=new LinkedHashMap<>();
        for(View child:host.mMeasureViews){Icon icon=(Icon)child;result.put(icon.name,icon.state.getXTranslation());}
        return result;
    }
    private static void nativeTargets(Host host){
        float x=host.getWidth()-host.getPaddingEnd();
        for(int i=host.getChildCount()-1;i>=0;i--){
            Icon child=(Icon)host.getChildAt(i);child.state.visibleState=shown(host,child)?0:2;
            if(child.state.visibleState==0){x-=child.width+child.getPaddingStart()+child.getPaddingEnd();child.state.setXTranslation(host.rtl?host.width-x-child.width:x);}
            child.state.setYTranslation(child.getTranslationY(),"native initFrom");
        }
    }
    private static void apply(NativeStatusIcons h,Host host){
        for(int i=0;i<host.getChildCount();i++){
            Icon child=(Icon)host.getChildAt(i);h.beforeApply(child.state,child);
            child.setTranslationX(child.state.getXTranslation());child.nativeApply++;
        }
    }
    /** Compare actual physical positions, independently of the getChildAt layout remapping. */
    private static List<String> physicalNames(Host host){
        ArrayList<Icon> icons=new ArrayList<>();
        for(int i=0;i<host.getChildCount();i++){Icon icon=(Icon)host.getChildAt(i);if(icon.state.visibleState==0)icons.add(icon);}
        icons.sort(Comparator.comparingDouble(i->i.state.getXTranslation()));
        ArrayList<String> result=new ArrayList<>();for(Icon icon:icons)result.add(icon.name);return result;
    }
    private static List<String> visible(Host host){ArrayList<String> names=new ArrayList<>();for(View v:host.mMeasureViews)names.add(((Icon)v).name);return names;}
    private static Object read(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object plan(NativeStatusIcons h,Host host)throws Exception{return read(((Map<?,?>)read(h,"containers")).get(host),"plan");}
    public static void main(String[] args)throws Exception{
        equal(Collections.singletonMap(NativeStatusIcons.MASTER,false),NativeStatusIcons.booleanDefaults());
        equal(14f,NativeStatusIcons.floatDefaults().get(NativeStatusIcons.MAX));equal("",NativeStatusIcons.stringDefaults().get(NativeStatusIcons.PRIORITY));
        equal(Arrays.asList("bluetooth","location","alarm_clock","zen","hotspot","headset","volume","microphone","camera","privacy_call"),
                NativeStatusIcons.parsePriority("蓝牙,定位，闹钟 勿扰;热点；耳机\n静音 麦克风 相机 隐私 蓝牙 wifi mobile network_speed bad()"));
        equal(Collections.emptyList(),NativeStatusIcons.parsePriority("wifi,network_type,mobile_2,stacked_mobile,battery,time,clock,net_speed,network_speed"));
        for(String slot:Arrays.asList("wifi","wifi_secondary","mobile","stacked_mobile_1","battery","network_speed","netspeed","net_speed","network_type","clock","time"))equal(true,NativeStatusIcons.excludedSlot(slot));
        for(String slot:Arrays.asList("bluetooth","location","alarm_clock","zen","volume","nfc","hotspot","privacy_call"))equal(false,NativeStatusIcons.excludedSlot(slot));
        NativeStatusIcons h=helper();Host host=new Host();host.hints=h;
        Icon location=new Icon("location","location"),alarm=new Icon("alarm","alarm_clock"),bluetooth=new Icon("bluetooth","bluetooth"),wifi=new Icon("wifi","wifi"),mobile=new Icon("mobile","stacked_mobile");
        host.addView(location);host.addView(alarm);host.addView(bluetooth);host.addView(wifi);host.addView(mobile);
        equal(false,h.configure(new Bundle()));Map<String,Float> baseline=layout(h,host);equal(Arrays.asList("location","alarm","bluetooth","wifi","mobile"),visible(host));
        near(292f,baseline.get("mobile"));near(270f,baseline.get("wifi"));
        bluetooth.setAlpha(.37f);wifi.setAlpha(.83f);Object state=bluetooth.state;int tint=bluetooth.tint;
        equal(true,h.configure(settings(true,2,"蓝牙,定位",0,0,0)));Map<String,Float> prioritized=layout(h,host);
        equal(Arrays.asList("bluetooth","location","wifi","mobile"),visible(host));near(226f,prioritized.get("bluetooth"));near(248f,prioritized.get("location"));
        near(baseline.get("wifi"),prioritized.get("wifi"));near(baseline.get("mobile"),prioritized.get("mobile"));equal(2,alarm.state.visibleState);
        equal(state,bluetooth.state);equal(tint,bluetooth.tint);near(.37f,bluetooth.getAlpha());near(.83f,wifi.getAlpha());equal(false,alarm.blocked);equal(View.VISIBLE,alarm.getVisibility());
        equal(false,h.iconBlocked(alarm,false));equal(0,h.childIndex(host,0)); // Both overrides are strictly scoped.
        try(NativeStatusIcons.Scope a=h.begin(host,false)){
            equal(2,h.childIndex(host,0));equal(true,h.iconBlocked(alarm,false));equal(false,h.iconBlocked(wifi,false));
            try(NativeStatusIcons.Scope b=h.begin(host,true)){equal(2,h.childIndex(host,0));}equal(2,h.childIndex(host,0));
        }
        equal(0,h.childIndex(host,0));equal(false,h.iconBlocked(alarm,false));
        try{try(NativeStatusIcons.Scope a=h.begin(host,true)){throw new IllegalStateException();}}catch(IllegalStateException expected){}equal(0,h.childIndex(host,0));
        Object cached=plan(h,host);int ids=host.resources.ids,requests=host.layoutRequests,reads=host.childReads;
        for(int i=0;i<2000;i++){try(NativeStatusIcons.Scope a=h.begin(host,true)){equal(2,h.childIndex(host,0));}equal(cached,plan(h,host));}
        equal(ids,host.resources.ids);equal(requests,host.layoutRequests);equal(reads,host.childReads); // No resource lookup/child scan/replan on stable scopes.
        bluetooth.visible=false;layout(h,host);equal(Arrays.asList("location","alarm","wifi","mobile"),visible(host));
        bluetooth.visible=true;host.mIgnoredSlots.add("location");layout(h,host);equal(Arrays.asList("bluetooth","alarm","wifi","mobile"),visible(host));
        host.mIgnoredSlots.clear();bluetooth.blocked=true;layout(h,host);equal(Arrays.asList("location","alarm","wifi","mobile"),visible(host));bluetooth.blocked=false;
        h.configure(settings(true,1,"alarm_clock",0,-80,9));Map<String,Float> moved=layout(h,host);
        equal(Arrays.asList("alarm","wifi","mobile"),visible(host));near(168f,moved.get("alarm"));near(21f,alarm.top);near(0f,alarm.getTranslationY());near(baseline.get("wifi"),moved.get("wifi"));near(baseline.get("mobile"),moved.get("mobile"));
        layout(h,host);near(21f,alarm.top); // Native top resets before each static offset; it never accumulates.
        h.configure(settings(true,1,"alarm_clock",0,500,-500));moved=layout(h,host);near(748f,moved.get("alarm"));near(2f,alarm.top); // X is a real physical offset even in a tightly measured host.
        h.configure(settings(true,1,"alarm_clock",0,-500,500));moved=layout(h,host);near(-252f,moved.get("alarm"));near(35f,alarm.top);
        h.configure(settings(true,2,"蓝牙,定位",12,0,0));moved=layout(h,host);near(214f,moved.get("bluetooth"));near(248f,moved.get("location"));near(baseline.get("wifi"),moved.get("wifi"));
        host.rtl=true;layout(h,host);near(86f,bluetooth.state.getXTranslation());near(52f,location.state.getXTranslation());near(30f,wifi.state.getXTranslation());near(8f,mobile.state.getXTranslation());
        h.configure(settings(true,1,"闹钟",0,-40,0));layout(h,host);near(12f,alarm.state.getXTranslation()); // X remains a physical left/right offset in RTL.
        h.configure(settings(true,1,"闹钟",0,40,0));layout(h,host);near(92f,alarm.state.getXTranslation());
        host.rtl=false;
        // Count is zero only for hints; critical native widgets and the true mMeasureViews tail survive.
        h.configure(settings(true,0,"",0,0,0));layout(h,host);equal(Arrays.asList("wifi","mobile"),visible(host));equal(mobile,host.mMeasureViews.get(host.mMeasureViews.size()-1));
        wifi.visible=false;layout(h,host);equal(Collections.singletonList("mobile"),visible(host));near(292f,mobile.state.getXTranslation());wifi.visible=true;
        Bundle safe=settings(true,1,"蓝牙",12,-40,20);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(safe);layout(h,host);
        equal(Arrays.asList("location","alarm","bluetooth","wifi","mobile"),visible(host));near(12f,alarm.top);near(baseline.get("alarm"),alarm.state.getXTranslation());
        h.configure(settings(true,1,"蓝牙",0,-40,7));layout(h,host);near(19f,bluetooth.top);h.configure(settings(false,1,"蓝牙",0,-40,7));near(12f,bluetooth.top);layout(h,host);near(baseline.get("bluetooth"),bluetooth.state.getXTranslation());
        h.configure(settings(true,1,"蓝牙",0,0,7));layout(h,host);bluetooth.top=31;h.configure(settings(false,1,"",0,0,0));near(31f,bluetooth.top); // A newer native layout write takes precedence.
        h.configure(settings(true,14,"",0,0,0));Icon hotspot=new Icon("hotspot","hotspot");host.addView(hotspot);h.changed(host);
        layout(h,host);equal(Arrays.asList("location","alarm","bluetooth","wifi","mobile","hotspot"),visible(host));
        h.configure(settings(true,1,"hotspot",0,0,7));layout(h,host);
        equal(Arrays.asList("wifi","mobile","hotspot"),visible(host));near(19f,hotspot.top); // A hint after native network is supported, not whole-row fail-open.
        host.removeView(hotspot);h.changed(host);layout(h,host);
        bluetooth.slot="camera";h.bound(bluetooth);h.configure(settings(true,1,"隐私",0,0,0));layout(h,host);equal(Arrays.asList("bluetooth","wifi","mobile"),visible(host));
        bluetooth.setNotification(new StatusBarNotification(1));h.bound(bluetooth);layout(h,host);equal(0,bluetooth.state.visibleState); // A real notification can never be count-suppressed.
        bluetooth.setNotification(null);h.bound(bluetooth);h.configure(settings(true,99,"",0,0,0));layout(h,host);equal(5,host.mMeasureViews.size());
        h.configure(settings(true,1,"camera",0,0,9));layout(h,host);near(21f,bluetooth.top);h.detached(host);near(12f,bluetooth.top);equal(0,((Map<?,?>)read(h,"containers")).size());equal(0,((Map<?,?>)read(h,"nodes")).size());
        h.changed(host);layout(h,host);h.releaseRuntime();equal(false,h.enabled());near(12f,bluetooth.top);equal(null,h.begin(host,true));equal(false,h.configure(settings(true,14,"",0,0,0)));
        equal(0,((Map<?,?>)read(h,"containers")).size());equal(0,((Map<?,?>)read(h,"nodes")).size());
        nativeInterleavedSlots();unclippedNativeHeight();tightNativeWidthAndFallback();networkSwapTogether();
        System.out.println("NativeStatusIconsCheck: "+checks+" checks passed");
    }
    /** The order comes from the real APK array/config_statusBarIcons, not a hints-only fake row. */
    private static void nativeInterleavedSlots()throws Exception{
        NativeStatusIcons h=helper();Host host=new Host();host.hints=h;
        String[] names={"network_speed","zen","location","stacked_mobile","wifi","privacy_call","bluetooth","airplane","battery"};
        for(String name:names)host.addView(new Icon(name,name));
        h.configure(settings(true,2,"蓝牙,勿扰",0,0,7));layout(h,host);
        equal(Arrays.asList("network_speed","zen","stacked_mobile","wifi","bluetooth","battery"),visible(host));
        near(19f,((Icon)host.getChildAt(1)).top);near(19f,((Icon)host.getChildAt(6)).top);
        near(12f,((Icon)host.getChildAt(3)).top);near(12f,((Icon)host.getChildAt(4)).top);near(12f,((Icon)host.getChildAt(8)).top);
        h.configure(settings(true,14,"定位,勿扰,蓝牙,privacy_call",-1,-1,0));layout(h,host);
        equal(Arrays.asList("network_speed","location","zen","stacked_mobile","wifi","bluetooth","privacy_call","airplane","battery"),visible(host));
        try(NativeStatusIcons.Scope scope=h.begin(host,true)){
            for(int i:new int[]{0,3,4,8})equal(i,h.childIndex(host,i));
            equal(2,h.childIndex(host,1));equal(1,h.childIndex(host,2));equal(6,h.childIndex(host,5));equal(5,h.childIndex(host,6));
        }
        float[] critical=new float[4];int k=0;for(int i:new int[]{0,3,4,8})critical[k++]=((Icon)host.getChildAt(i)).state.getXTranslation();
        // Position/spacing shift only hint states and never touch native network targets.
        h.configure(settings(true,14,"定位,勿扰,蓝牙,privacy_call",1,-100,0));layout(h,host);
        k=0;for(int i:new int[]{0,3,4,8})near(critical[k++],((Icon)host.getChildAt(i)).state.getXTranslation());
        Object cached=plan(h,host),buffer=read(((Map<?,?>)read(h,"containers")).get(host),"targetBuffer");
        float[] stable=new float[host.getChildCount()];for(int j=0;j<stable.length;j++)stable[j]=((Icon)host.getChildAt(j)).state.getXTranslation();
        int ids=host.resources.ids,reads=host.childReads,requests=host.layoutRequests;
        for(int i=0;i<10000;i++)try(NativeStatusIcons.Scope scope=h.begin(host,true)){
            nativeTargets(host);apply(h,host);
            equal(cached,plan(h,host));equal(buffer,read(((Map<?,?>)read(h,"containers")).get(host),"targetBuffer"));
            // This is the real DEX lifecycle: native recalculates first, then applies every target.
            // The old test skipped recalculation and never checked the coordinates.
            for(int j=0;j<host.getChildCount();j++)near(stable[h.childIndex(host,j)],((Icon)host.getChildAt(j)).state.getXTranslation());
        }
        equal(ids,host.resources.ids);equal(requests,host.layoutRequests);
        equal(reads+host.getChildCount()*3*10000,host.childReads);
        for(Object target:(Object[])buffer)equal(null,target); // Weak host cache never retains native state/view objects.
        Bundle safe=settings(true,0,"蓝牙",1,-100,7);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);h.configure(safe);layout(h,host);
        equal(Arrays.asList(names),visible(host));for(int i=0;i<host.getChildCount();i++)near(12,((Icon)host.getChildAt(i)).top);
        h.releaseRuntime();
    }
    private static final class ClipParent extends ViewGroup{
        boolean scaled;
        ClipParent(){super(null);}
        @Override public int getWidth(){return 400;}
        @Override public int getHeight(){return 100;}
        @Override public int getPaddingTop(){return 8;}
        @Override public int getPaddingBottom(){return 10;}
        @Override public void getLocationOnScreen(int[] out){out[0]=0;out[1]=0;}
        @Override public Matrix getMatrix(){Matrix m=new Matrix();if(scaled)m.setScale(1,2);return m;}
    }
    private static void bothTargets(NativeStatusIcons h,NetworkIconOrder ordering,Host host,boolean hintsOuter){
        if(hintsOuter){
            try(NativeStatusIcons.Scope hints=h.begin(host,true);NetworkIconOrder.IndexScope network=ordering.begin(host)){
                nativeTargets(host);apply(h,host);
            }
        }else{
            try(NetworkIconOrder.IndexScope network=ordering.begin(host);NativeStatusIcons.Scope hints=h.begin(host,true)){
                nativeTargets(host);apply(h,host);
            }
        }
    }
    /** The two index hooks commute because hint sorting never crosses a network slot.
     * Test both installation orders with the real C17 interleaving and unequal network widths. */
    private static void networkSwapTogether()throws Exception{
        for(boolean rtl:new boolean[]{false,true})for(boolean hintsOuter:new boolean[]{false,true}){
            NativeStatusIcons h=helper();NetworkIconOrder ordering=new NetworkIconOrder();
            ordering.resolve(NativeStatusIconsCheck.class.getClassLoader());
            Host host=new Host();host.hints=h;host.ordering=ordering;host.rtl=rtl;
            String[] names={"network_speed","zen","location","stacked_mobile","wifi","privacy_call","bluetooth","airplane","battery"};
            for(String name:names){Icon icon=new Icon(name,name);if(name.equals("stacked_mobile"))icon.width=36;if(name.equals("wifi"))icon.width=28;host.addView(icon);}
            Bundle swap=new Bundle();swap.putBoolean(NetworkIconOrder.SWAP,true);ordering.configure(swap);
            bothTargets(h,ordering,host,hintsOuter);
            float[] networkPositions=new float[4];int k=0;for(int i:new int[]{0,3,4,8})networkPositions[k++]=((Icon)host.getChildAt(i)).state.getXTranslation();
            equal(rtl?Arrays.asList("battery","airplane","bluetooth","privacy_call","stacked_mobile","wifi","location","zen","network_speed")
                    :Arrays.asList("network_speed","zen","location","wifi","stacked_mobile","privacy_call","bluetooth","airplane","battery"),physicalNames(host));
            h.configure(settings(true,14,"location,zen,bluetooth,privacy_call",0,0,0));bothTargets(h,ordering,host,hintsOuter);
            equal(rtl?Arrays.asList("battery","airplane","privacy_call","bluetooth","stacked_mobile","wifi","zen","location","network_speed")
                    :Arrays.asList("network_speed","location","zen","wifi","stacked_mobile","bluetooth","privacy_call","airplane","battery"),physicalNames(host));
            h.configure(settings(true,14,"location,zen,bluetooth,privacy_call",12,-30,0));
            for(int repeat=0;repeat<1000;repeat++){
                bothTargets(h,ordering,host,hintsOuter);k=0;
                for(int i:new int[]{0,3,4,8})near(networkPositions[k++],((Icon)host.getChildAt(i)).state.getXTranslation());
            }
            // Count prioritizes Bluetooth globally, without suppressing native network widgets.
            h.configure(settings(true,1,"bluetooth",0,0,0));bothTargets(h,ordering,host,hintsOuter);
            equal(0,((Icon)host.getChildAt(6)).state.visibleState);
            for(int i:new int[]{1,2,5,7})equal(2,((Icon)host.getChildAt(i)).state.visibleState);
            for(int i:new int[]{0,3,4,8})equal(0,((Icon)host.getChildAt(i)).state.visibleState);
            h.releaseRuntime();ordering.releaseRuntime();
        }
    }
    /** C17 derives WRAP_CONTENT width from visible child widths plus their own padding.
     * The former boundary solver had exactly zero free horizontal space in this real case. */
    private static void tightNativeWidthAndFallback()throws Exception{
        equal(NativeStatusIcons.PRIORITY_SLOTS.length,NativeStatusIcons.PRIORITY_LABELS.length);
        equal("飞行模式",NativeStatusIcons.priorityLabel("airplane"));
        List<String> editable=NativeStatusIcons.editablePriority("camera,bluetooth,camera,wifi,unknown");
        equal("camera",editable.get(0));equal("bluetooth",editable.get(1));
        equal(NativeStatusIcons.PRIORITY_SLOTS.length,editable.size());equal(true,editable.contains("airplane"));
        for(boolean rtl:new boolean[]{false,true})for(boolean bypass:new boolean[]{false,true}){
            NativeStatusIcons h=helper();Host host=new Host();host.hints=h;host.width=126;host.rtl=rtl;
            Icon location=new Icon("location","location"),alarm=new Icon("alarm","alarm_clock"),bluetooth=new Icon("bluetooth","bluetooth"),wifi=new Icon("wifi","wifi"),battery=new Icon("battery","battery");
            location.width=18;alarm.width=28;bluetooth.width=14;battery.width=26;
            host.addView(location);host.addView(alarm);host.addView(bluetooth);host.addView(wifi);host.addView(battery);
            layout(h,host);float nativeWifi=wifi.state.getXTranslation(),nativeBattery=battery.state.getXTranslation();
            host.bypassIndexes=bypass;
            h.configure(settings(true,14,"bluetooth,location,alarm_clock",8,0,0));layout(h,host);
            equal(rtl?Arrays.asList("battery","wifi","alarm","location","bluetooth"):Arrays.asList("bluetooth","location","alarm","wifi","battery"),physicalNames(host));
            near(rtl?124f:-12f,bluetooth.state.getXTranslation());
            near(rtl?96f:12f,location.state.getXTranslation());
            near(rtl?58f:40f,alarm.state.getXTranslation());
            near(nativeWifi,wifi.state.getXTranslation());near(nativeBattery,battery.state.getXTranslation());
            // The same targets are applied more than once in a scope without accumulating the static offset.
            h.configure(settings(true,14,"bluetooth,location,alarm_clock",8,13,0));
            try(NativeStatusIcons.Scope scope=h.begin(host,true)){
                nativeTargets(host);apply(h,host);
                float[] once={location.state.getXTranslation(),alarm.state.getXTranslation(),bluetooth.state.getXTranslation()};
                for(int i=0;i<1000;i++){apply(h,host);near(once[0],location.state.getXTranslation());near(once[1],alarm.state.getXTranslation());near(once[2],bluetooth.state.getXTranslation());}
                near(rtl?137f:1f,bluetooth.state.getXTranslation());
            }
            near(nativeWifi,wifi.state.getXTranslation());near(nativeBattery,battery.state.getXTranslation());
            h.configure(settings(true,2,"bluetooth,location",-50,0,0));layout(h,host);
            equal(2,alarm.state.visibleState);equal(0,bluetooth.state.visibleState);equal(0,location.state.visibleState);
            // Closing the native gap never reverses or overlaps the retained hints.
            near(rtl?76f:36f,bluetooth.state.getXTranslation());near(rtl?58f:50f,location.state.getXTranslation());
            near(nativeWifi,wifi.state.getXTranslation());near(nativeBattery,battery.state.getXTranslation());
            host.bypassBlocked=true;h.configure(settings(true,1,"bluetooth",0,0,0));layout(h,host);
            // Even an OEM inlined isIconBlocked call cannot bypass the final count filter.
            equal(2,alarm.state.visibleState);equal(2,location.state.visibleState);equal(0,bluetooth.state.visibleState);
            near(nativeWifi,wifi.state.getXTranslation());near(nativeBattery,battery.state.getXTranslation());
            h.configure(settings(false,1,"bluetooth",8,13,0));host.bypassBlocked=false;layout(h,host);
            equal(0,alarm.state.visibleState);equal(0,location.state.visibleState);
            equal(rtl?Arrays.asList("battery","wifi","bluetooth","alarm","location"):Arrays.asList("location","alarm","bluetooth","wifi","battery"),physicalNames(host));
            h.releaseRuntime();
        }
    }
    /** OEM non-EXACT measure uses max measured icon height; XML leaves the native host unclipped. */
    private static void unclippedNativeHeight()throws Exception{
        NativeStatusIcons h=helper();Host host=new Host();host.hints=h;host.height=22;host.nativeTop=0;host.screenY=30;
        host.setClipChildren(false);host.setClipToPadding(false);ClipParent window=new ClipParent();window.addView(host);
        Icon bluetooth=new Icon("bluetooth","bluetooth");host.addView(bluetooth);
        h.configure(settings(true,14,"",0,0,9));layout(h,host);near(9,bluetooth.top);near(0,bluetooth.getTranslationY());
        h.configure(settings(true,14,"",0,0,100));layout(h,host);near(38,bluetooth.top);
        h.configure(settings(true,14,"",0,0,-100));layout(h,host);near(-22,bluetooth.top);
        window.setClipBounds(new Rect(0,20,400,60));h.configure(settings(true,14,"",0,0,100));layout(h,host);near(8,bluetooth.top);
        window.scaled=true;layout(h,host);near(0,bluetooth.top); // Unknown scaled coordinates retain native geometry.
        window.scaled=false;window.setClipBounds(null);bluetooth.setTranslationY(3);layout(h,host);near(35,bluetooth.top);near(3,bluetooth.getTranslationY());
        h.configure(settings(false,14,"",0,0,100));near(0,bluetooth.top);near(3,bluetooth.getTranslationY());
        h.detached(host);equal(0,((Map<?,?>)read(h,"containers")).size());
        h.onLayout(host);int before=host.layoutRequests;h.configure(settings(true,14,"",0,0,9));equal(before+1,host.layoutRequests);
        h.releaseRuntime();
    }
}
