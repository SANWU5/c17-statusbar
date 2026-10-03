// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.WeakHashMap;

/** Text-only native keyguard dates and draw-only native lock decoration. No timer,
 * typography, notification date, fingerprint, layout or unlocking state changes. */
public final class LockscreenControls {
    public static final String DATE_ENABLED="lockscreen_date_custom_enabled";
    public static final String DATE_FORMAT="lockscreen_date_custom_format";
    public static final String HIDE_LOCK="lockscreen_hide_lock_icon";
    public static final String DEFAULT_FORMAT="M月d日 {周}";
    public static final String SINGLE="com.oplus.systemui.shared.clocks.SingleClockView";
    public static final String DUAL="com.oplus.systemui.shared.clocks.DualClockView";
    public static final String RED="com.oplus.systemui.shared.clocks.RedHorizontalSingleClockView";
    public static final String CUSTOM="com.oplus.systemui.keyguard.view.CustomOplusKeyguardStyleClock";
    public static final String LOCK="com.android.keyguard.OplusLockIconView";
    // This is a View from the dynamically loaded clock APK. The similarly named
    // base.ui.view.DateMessageView is a ChildViewProxy, NOT an Android View.
    public static final String PLUGIN_DATE="com.oplus.keyguard.clock.digital.ui.view.DateMessageView";
    public static final String PLUGIN_BASE_DATE="com.oplus.keyguard.clock.base.ui.view.DateMessageView";
    public static final String PLUGIN_DIGITAL_TEXT="com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView";
    public static final String PLUGIN_BASE_TEXT="com.oplus.keyguard.clock.base.widget.CustomizedTextView";
    public static final String PLUGIN_HDR_TEXT="com.oplus.keyguard.clock.common.view.hdr.OplusHDRTextView";
    private static final String PLUGIN_EXTRA="com.oplus.keyguard.clock.digital.ui.view.ExtraMessageView";
    private static final String PLUGIN_BASE_EXTRA="com.oplus.keyguard.clock.base.ui.view.ExtraMessageView";
    private static final String PLUGIN_PACKAGE="com.oplus.keyguard.personality.clocks";
    private static final String SYSTEM_UI="com.android.systemui";
    private static final int MAX_ANCESTORS=18;
    private boolean dateEnabled,hideLock,writing,seconds;
    private long finalWrites,refreshWrites,pluginWrites;
    private String pattern=DEFAULT_FORMAT;
    private final Map<TextView,DateState> dates=new WeakHashMap<>();
    private final Map<View,Boolean> dateOwners=new WeakHashMap<>();
    private final Map<Class<?>,ClockAccess> clocks=new WeakHashMap<>();
    private final Map<Class<?>,LockAccess> locks=new WeakHashMap<>();
    private final Map<View,Boolean> lockOwners=new WeakHashMap<>();
    private final Map<ViewGroup,NativeLock> nativeLocks=new WeakHashMap<>();
    private final Map<View,Boolean> lockResources=new WeakHashMap<>();
    private final Map<TextView,Boolean> pluginDateResources=new WeakHashMap<>();

    private static final class DateState {
        final WeakReference<View> owner;
        final ClockAccess access;
        final int slot;
        final WeakReference<View>[] path;
        CharSequence nativeText;
        String rendered;
        long bucket=Long.MIN_VALUE;
        String zoneId,pattern;
        DateState(View owner,ClockAccess access,int slot,CharSequence nativeText,WeakReference<View>[] path){
            this.owner=new WeakReference<>(owner);this.access=access;this.slot=slot;this.nativeText=nativeText;this.path=path;
        }
    }
    private static final class NativeLock {
        final WeakReference<View> icon,outer;
        NativeLock(View icon,View outer){this.icon=new WeakReference<>(icon);this.outer=new WeakReference<>(outer);}
        boolean valid(ViewGroup parent,View child){return child==icon.get()&&child.getParent()==parent&&parent.getParent()==outer.get()&&outer.get()!=null;}
    }
    private static final class ClockAccess {
        final String kind;
        final Field[] fields;
        final Field calendar,zone,residentWeather,extraContent;
        final Method residentZone,viewParent;
        ClockAccess(Class<?> type) {
            kind=nativeKind(type);
            if(SINGLE.equals(kind))fields=new Field[]{field(type,"mDate")};
            else if(DUAL.equals(kind))fields=new Field[]{field(type,"locatedDate"),field(type,"residentDate")};
            else if(RED.equals(kind))fields=new Field[]{field(type,"tvDate"),field(type,"tvWeek")};
            else if(PLUGIN_DATE.equals(kind))fields=new Field[]{field(type,"dateTextView"),field(type,"weekTextView"),field(type,"extraMsgView")};
            else if(PLUGIN_BASE_DATE.equals(kind))fields=new Field[]{field(type,"dateTextView"),field(type,"weekTextView"),field(type,"extraMsgView"),
                    field(type,"dateTextViewExt"),field(type,"extraMsgViewExt")};
            else fields=new Field[0];
            extraContent=plugin(kind)&&fields[2]!=null?field(fields[2].getType(),"messageContent"):null;
            viewParent=PLUGIN_BASE_DATE.equals(kind)?method(type,"getViewParent"):null;
            calendar=SINGLE.equals(kind)?field(type,"mCalendar"):null;
            zone=SINGLE.equals(kind)?field(type,"mTimeZone"):null;
            residentWeather=DUAL.equals(kind)?field(type,"residentWeatherInfo"):null;
            residentZone=residentWeather==null?null:method(residentWeather.getType(),"getTimeZone");
        }
        int slot(View owner,TextView text) {
            for(int i=0;i<fields.length;i++)try{
                if(text(owner,i)==text&&(!PLUGIN_DATE.equals(kind)||pluginSlotResource(text,i)))return i;
            }catch(ReflectiveOperationException|RuntimeException unsupported){/* Original text. */}
            return -1;
        }
        TextView text(Object owner,int slot)throws ReflectiveOperationException {
            Object value=fields[slot]==null?null:fields[slot].get(owner);
            if(plugin(kind)&&(slot==2||slot==4)) {
                if(!(value instanceof View)||!hasType(value.getClass(),PLUGIN_DATE.equals(kind)?PLUGIN_EXTRA:PLUGIN_BASE_EXTRA)
                        ||!resource((View)value,PLUGIN_PACKAGE,slot==4?"extra_text_ext":"extra_text"))return null;
                value=value==null||extraContent==null?null:extraContent.get(value);
            }
            return value instanceof TextView?(TextView)value:null;
        }
        TimeZone timeZone(View owner,int slot) {
            try {
                if(DUAL.equals(kind)) {
                    // Actual packageTimeInfo stores a localized display name in TimeInfo,
                    // not a timezone ID. Native local info uses the local Calendar; resident
                    // info uses WeatherInfo's numeric UTC-hour offset before formatting.
                    if(slot==0)return TimeZone.getDefault();
                    if(residentWeather==null||residentZone==null)return null;
                    Object info=residentWeather.get(owner);if(info==null)return null;
                    Object offset=residentZone.invoke(info);
                    return offset instanceof String?residentOffset((String)offset):null;
                }
                if(zone!=null) {
                    Object nativeZone=zone.get(owner);
                    if(nativeZone!=null){Method id=method(nativeZone.getClass(),"getID");
                        if(id!=null){Object name=id.invoke(nativeZone);if(name instanceof String)return validZone((String)name);}}
                }
                if(calendar!=null){Object value=calendar.get(owner);if(value instanceof Calendar)return ((Calendar)value).getTimeZone();}
                return TimeZone.getDefault(); // Native single/red date uses local Calendar/time-info.
            }catch(ReflectiveOperationException|RuntimeException unsupported){return null;}
        }
    }
    private static final class LockAccess {
        final boolean matched;
        final Field icon,background;
        LockAccess(Class<?> type){matched=hasType(type,LOCK);icon=matched?field(type,"mLockIcon"):null;background=matched?field(type,"mBgView"):null;}
    }
    public void configure(Bundle source) {
        boolean safe=source==null||SafetyMode.enabled(source)||ModuleLifecycle.removed();
        Object input=source==null?null:source.get(DATE_FORMAT);
        String next=input instanceof String?(String)input:DEFAULT_FORMAT;
        boolean valid=TimeFormat.validationError(next)==null;
        dateEnabled=!safe&&valid&&Boolean.TRUE.equals(source.get(DATE_ENABLED));
        hideLock=!safe&&Boolean.TRUE.equals(source.get(HIDE_LOCK));
        pattern=valid?next:DEFAULT_FORMAT;
        seconds=TimeFormat.hasSeconds(pattern);
        refresh();
    }
    /** Hook the final native TextView.setText write, including asynchronous date callbacks. */
    public CharSequence formatDate(TextView view,CharSequence nativeText) {
        return formatDateAt(view,nativeText,System.currentTimeMillis());
    }
    /** Enter at the plugin's real text writer, before HDR spans and the framework setter.
     * Nested framework hooks must not mistake our formatted argument for a new OEM date. */
    public NativeDateWrite beginPluginDateWrite(TextView view,CharSequence nativeText) {
        if(view==null||writing)return null;
        if(!dates.containsKey(view)) {
            Boolean known=pluginDateResources.get(view);
            if(known==null){known=pluginDateResource(view);pluginDateResources.put(view,known);}
            if(!known)return null;
        }
        CharSequence adjusted=formatDate(view,nativeText);
        if(!dates.containsKey(view))return null;
        pluginWrites++;return new NativeDateWrite(adjusted);
    }
    public final class NativeDateWrite implements AutoCloseable {
        private final CharSequence text;private final boolean previous;private boolean closed;
        NativeDateWrite(CharSequence text){this.text=text;previous=writing;writing=true;}
        public CharSequence text(){return text;}
        @Override public void close(){if(!closed){closed=true;writing=previous;}}
    }
    CharSequence formatDateAt(TextView view,CharSequence nativeText,long now) {
        if(view==null||writing)return nativeText;
        DateState state=dates.get(view);
        if(state!=null&&!validPath(view,state)){dates.remove(view);state=null;}
        // Known native clock fields are bound at attach even while disabled. The global
        // TextView hook therefore has no ancestor scan for unrelated views while OFF.
        if(state==null&&dateEnabled)state=findDate(view,nativeText);
        if(state==null)return nativeText;
        if(finalWrites<Long.MAX_VALUE)finalWrites++;
        state.nativeText=nativeText;
        return output(state,nativeText,now);
    }
    private CharSequence output(DateState state,CharSequence fallback,long now) {
        View owner=state.owner.get();
        if(!dateEnabled||ModuleLifecycle.removed()||owner==null)return fallback;
        // Red clocks own a separate native week slot. The complete user format replaces the
        // primary date; leave the secondary text empty rather than duplicate weekday text.
        if((RED.equals(state.access.kind)&&state.slot==1)
                ||plugin(state.access.kind)&&state.slot>0)return "";
        TimeZone zone=state.access.timeZone(owner,state.slot);
        if(zone==null)return fallback; // Never replace a resident date with the local timezone.
        long bucket=now/(seconds?1000L:60000L);
        String id=zone.getID();
        if(state.rendered==null||state.bucket!=bucket||!pattern.equals(state.pattern)||!id.equals(state.zoneId))try {
            state.rendered=TimeFormat.format(pattern,now,zone);state.bucket=bucket;state.pattern=pattern;state.zoneId=id;
        }catch(IllegalArgumentException invalid){return fallback;}
        return state.rendered;
    }
    private ClockAccess access(Class<?> type) {
        ClockAccess access=clocks.get(type);if(access==null){access=new ClockAccess(type);clocks.put(type,access);}return access;
    }
    private DateState findDate(TextView text,CharSequence nativeText) {
        int depth=0;
        for(View owner=parent(text);owner!=null&&depth++<MAX_ANCESTORS;owner=parent(owner)) {
            ClockAccess access=access(owner.getClass());
            int slot=access.slot(owner,text);
            if(slot<0&&CUSTOM.equals(access.kind)&&customDateResource(text))slot=0;
            if(slot>=0){DateState state=bind(text,owner,access,slot,nativeText);if(state!=null)return state;}
        }
        return null;
    }
    /** Inflation/config binding allows an existing clock to react immediately to a new setting. */
    public void onClockUpdated(View owner) {
        if(owner==null)return;ClockAccess access=access(owner.getClass());
        for(int i=0;i<access.fields.length;i++)try {
            TextView text=access.text(owner,i);
            if(text!=null&&(!PLUGIN_DATE.equals(access.kind)||pluginSlotResource(text,i))&&!dates.containsKey(text))
                bind(text,owner,access,i,text.getText());
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Original text. */}
        if(CUSTOM.equals(access.kind)&&owner instanceof ViewGroup)bindCustom((ViewGroup)owner);
        refreshOwner(owner);
    }
    /** The base clock owner is a ChildViewProxy, so it never appears in View ancestry. */
    public void onPluginClockUpdated(Object proxy) {
        if(proxy instanceof View){onClockUpdated((View)proxy);return;}
        if(proxy==null)return;ClockAccess access=access(proxy.getClass());
        if(!PLUGIN_BASE_DATE.equals(access.kind)||access.viewParent==null)return;
        try {
            Object parent=access.viewParent.invoke(proxy);if(!(parent instanceof View))return;View host=(View)parent;
            for(int i=0;i<access.fields.length;i++) {
                TextView text=access.text(proxy,i);int slot=i==3?0:i==4?2:i;
                if(text==null||!pluginFieldResource(text,i))continue;
                DateState state=dates.get(text);
                if(state!=null&&!validPath(text,state)){restore(text,state);dates.remove(text);state=null;}
                if(state==null)bind(text,host,access,slot,text.getText());
            }
            refreshOwner(host);
        }catch(ReflectiveOperationException|RuntimeException unsupported){/* Preserve native clock. */}
    }
    /** Bind once on actual native clock/lock attachment; do not add a polling timer. */
    public void attach(View view) {
        if(view==null)return;
        if(nativeKind(view.getClass())!=null)onClockUpdated(view);
        // Framework attachment catches clocks from an independent plugin ClassLoader.
        // Bind exact resource children after OEM onFinishInflate populated its fields.
        if(view instanceof TextView&&pluginDateResource((TextView)view)) {
            TextView text=(TextView)view;
            if(!dates.containsKey(text))findDate(text,text.getText());
            refreshOwner(null);
        }
        if(view instanceof ImageView)bindNativeLock((ImageView)view);
        if(hasType(view.getClass(),LOCK)) {
            lockOwners.put(view,Boolean.TRUE);
            if(hideLock)view.invalidate();
        }
    }
    private void bindCustom(ViewGroup group) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child instanceof TextView&&customDateResource((TextView)child)) {
                TextView text=(TextView)child;
                if(!dates.containsKey(text))findDate(text,text.getText());
            }else if(child instanceof ViewGroup)bindCustom((ViewGroup)child);
        }
    }
    public boolean hideLockChild(ViewGroup parent,View child) {
        if(!hideLock||ModuleLifecycle.removed()||parent==null||child==null)return false;
        NativeLock nativeLock=nativeLocks.get(parent);
        if(nativeLock!=null) {
            if(nativeLock.valid(parent,child))return true;
            View icon=nativeLock.icon.get();
            if(icon==null||!nativeLock.valid(parent,icon))nativeLocks.remove(parent);
        }
        // Also covers a pre-existing keyguard during the first configuration apply;
        // negative resource identity is cached, never resolved every draw.
        if(child instanceof ImageView&&bindNativeLock((ImageView)child)) {
            nativeLock=nativeLocks.get(parent);
            if(nativeLock!=null&&nativeLock.valid(parent,child))return true;
        }
        LockAccess access=locks.get(parent.getClass());
        if(access==null){access=new LockAccess(parent.getClass());locks.put(parent.getClass(),access);}
        if(!access.matched||access.icon==null||access.background==null)return false;
        lockOwners.put(parent,Boolean.TRUE);
        try{return child==access.icon.get(parent)||child==access.background.get(parent);}
        catch(ReflectiveOperationException|RuntimeException unsupported){return false;}
    }
    public void refresh() {
        refreshOwner(null);
        for(View owner:new ArrayList<>(lockOwners.keySet()))owner.invalidate();
        for(ViewGroup parent:new ArrayList<>(nativeLocks.keySet()))parent.invalidate();
    }
    private void refreshOwner(View onlyOwner) {
        long now=System.currentTimeMillis();boolean previous=writing;writing=true;
        try {
            for(TextView view:new ArrayList<>(dates.keySet())) {
                DateState state=dates.get(view);if(state==null)continue;
                View owner=state.owner.get();
                if(!validPath(view,state)){restore(view,state);dates.remove(view);continue;}
                if(onlyOwner!=null&&owner!=onlyOwner)continue;
                CharSequence next=output(state,state.nativeText,now);
                if(!same(view.getText(),next)){view.setText(next);if(refreshWrites<Long.MAX_VALUE)refreshWrites++;}
            }
        }finally{writing=previous;}
    }
    public void releaseRuntime() {
        dateEnabled=false;hideLock=false;refresh();dates.clear();dateOwners.clear();lockOwners.clear();nativeLocks.clear();lockResources.clear();pluginDateResources.clear();clocks.clear();locks.clear();
    }
    /** Called after native parent/ID changes. Do not retain custom text on a recycled child. */
    public void classificationChanged(View view) {
        if(view==null)return;
        lockResources.remove(view);
        if(view instanceof TextView) {
            pluginDateResources.remove(view);
            TextView text=(TextView)view;DateState state=dates.get(text);
            if(state!=null){restore(text,state);dates.remove(text);}
        }
        if(view instanceof ImageView) {
            for(ViewGroup owner:new ArrayList<>(nativeLocks.keySet())) {
                NativeLock lock=nativeLocks.get(owner);
                if(lock!=null&&lock.icon.get()==view){nativeLocks.remove(owner);owner.invalidate();}
            }
        }
    }
    /** OFF/detach restores the latest OEM write, including async lunar/weekday updates. */
    public void detach(View view) {
        if(view==null)return;
        if(view instanceof TextView) {
            pluginDateResources.remove(view);
            DateState state=dates.remove(view);if(state!=null)restore((TextView)view,state);
        }else if(dateOwners.containsKey(view)) {
            for(TextView text:new ArrayList<>(dates.keySet())) {
                DateState state=dates.get(text);
                if(state!=null&&state.owner.get()==view){restore(text,state);dates.remove(text);}
            }
        }
        dateOwners.remove(view);
        lockOwners.remove(view);nativeLocks.remove(view);lockResources.remove(view);
        if(view instanceof ImageView)classificationChanged(view);
    }
    public String diagnosticSummary() {
        int plugin=0,base=0,legacy=0,valid=0,shown=0,currentMatch=0;
        int[] slots=new int[3],matches=new int[3];
        Map<View,Boolean> hosts=new WeakHashMap<>();
        long now=System.currentTimeMillis();
        for(TextView text:new ArrayList<>(dates.keySet())) {
            DateState state=dates.get(text);if(state==null)continue;
            if(plugin(state.access.kind)) {
                if(PLUGIN_DATE.equals(state.access.kind))plugin++;else base++;
                if(state.path.length>0){View host=state.path[state.path.length-1].get();if(host!=null)hosts.put(host,Boolean.TRUE);}
            }else legacy++;
            if(validPath(text,state))valid++;
            if(diagnosticShown(text)) {
                shown++;int slot=Math.min(2,state.slot);slots[slot]++;
                if(same(text.getText(),output(state,state.nativeText,now))){currentMatch++;matches[slot]++;}
            }
        }
        // A single explicit diagnostic may inspect these tiny clock hosts to distinguish
        // an unbound base-proxy widget from a native write that bypassed our setter hook.
        // This method is NOT called by draw/layout or any periodic worker.
        int[] visibleResources=new int[5];
        for(View host:hosts.keySet())diagnosticResources(host,visibleResources,0);
        return "date="+dateEnabled+",lock="+hideLock+",dateBindings="+dates.size()
                +",pluginDigital="+plugin+",pluginBase="+base+",legacy="+legacy+",lockParents="+nativeLocks.size()
                +",legacyLockParents="+lockOwners.size()+",validPaths="+valid+",shown="+shown+",currentMatches="+currentMatch
                +",shownSlots="+slots[0]+"/"+slots[1]+"/"+slots[2]+",matchSlots="+matches[0]+"/"+matches[1]+"/"+matches[2]
                +",visibleResources="+visibleResources[0]+",unboundVisible="+visibleResources[1]
                +",digitalText="+visibleResources[2]+",baseSwitchText="+visibleResources[3]+",otherText="+visibleResources[4]
                +",finalWrites="+finalWrites+",pluginWrites="+pluginWrites+",refreshWrites="+refreshWrites+",source=pluginFields+nativeLockResource";
    }
    private static boolean diagnosticShown(View view) {
        return view.isAttachedToWindow()&&view.isShown()&&view.getVisibility()==View.VISIBLE&&view.getAlpha()>0f;
    }
    private void diagnosticResources(View view,int[] result,int depth) {
        if(depth>MAX_ANCESTORS||result[0]>=64)return;
        if(view instanceof TextView&&diagnosticShown(view)&&pluginDateResource((TextView)view)) {
            result[0]++;if(!dates.containsKey(view))result[1]++;
            if(hasType(view.getClass(),"com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView"))result[2]++;
            else if(hasType(view.getClass(),"com.oplus.keyguard.clock.base.widget.SwitchAnimTextView"))result[3]++;
            else result[4]++;
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)diagnosticResources(group.getChildAt(i),result,depth+1);
        }
    }
    private static boolean same(CharSequence a,CharSequence b){return a==b||a!=null&&b!=null&&a.toString().contentEquals(b);}
    private static View parent(View view){return view.getParent() instanceof View?(View)view.getParent():null;}
    @SuppressWarnings("unchecked")
    private DateState bind(TextView text,View owner,ClockAccess access,int slot,CharSequence nativeText) {
        View end=owner;
        if(plugin(access.kind)) {
            end=null;int depth=0;
            for(View ancestor=parent(owner);ancestor!=null&&depth++<MAX_ANCESTORS;ancestor=parent(ancestor))
                if(resource(ancestor,SYSTEM_UI,"keyguard_style_clock")){end=ancestor;break;}
            if(end==null)return null; // A theme preview or app widget is not the lockscreen.
        }
        ArrayList<WeakReference<View>> path=new ArrayList<>();boolean containsOwner=false;
        for(View current=parent(text);current!=null&&path.size()<MAX_ANCESTORS;current=parent(current)) {
            path.add(new WeakReference<>(current));if(current==owner)containsOwner=true;
            if(current==end&&containsOwner) {
                DateState state=new DateState(owner,access,slot,nativeText,path.toArray(new WeakReference[0]));
                dates.put(text,state);dateOwners.put(owner,Boolean.TRUE);return state;
            }
        }
        return null;
    }
    private static boolean validPath(TextView text,DateState state) {
        if(state.owner.get()==null)return false;
        View child=text;
        for(WeakReference<View> reference:state.path) {
            View expected=reference.get();if(expected==null||parent(child)!=expected)return false;
            child=expected;
        }
        return true;
    }
    private void restore(TextView text,DateState state) {
        boolean previous=writing;writing=true;
        try{if(!same(text.getText(),state.nativeText))text.setText(state.nativeText);}finally{writing=previous;}
    }
    private boolean bindNativeLock(ImageView icon) {
        Boolean known=lockResources.get(icon);
        if(known==null){known=resource(icon,SYSTEM_UI,"lock_icon");lockResources.put(icon,known);}
        if(!known)return false;
        View container=parent(icon),outer=container==null?null:parent(container);
        if(!(container instanceof LinearLayout)||!(outer instanceof FrameLayout)
                ||!resource(container,SYSTEM_UI,"lock_icon_view")||!resource(outer,SYSTEM_UI,"lock_icon_view"))return false;
        ViewGroup owner=(ViewGroup)container;NativeLock existing=nativeLocks.get(owner);
        if(existing==null||!existing.valid(owner,icon)) {
            nativeLocks.put(owner,new NativeLock(icon,outer));if(hideLock)owner.invalidate();
        }
        return true;
    }
    private static String nativeKind(Class<?> type) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) {
            String name=c.getName();if(name.equals(SINGLE)||name.equals(DUAL)||name.equals(RED)||name.equals(CUSTOM)||name.equals(PLUGIN_DATE)||name.equals(PLUGIN_BASE_DATE))return name;
        }return null;
    }
    private static boolean hasType(Class<?> type,String name){for(Class<?> c=type;c!=null;c=c.getSuperclass())if(c.getName().equals(name))return true;return false;}
    private static boolean customDateResource(TextView text) {
        try {
            if(!"com.android.systemui".equals(text.getResources().getResourcePackageName(text.getId())))return false;
            String name=text.getResources().getResourceEntryName(text.getId());
            return name.equals("keyguard_date")||name.equals("oplus_date")||name.equals("tv_date")||name.equals("date");
        }catch(RuntimeException unsupported){return false;}
    }
    private static boolean resource(View view,String pkg,String name) {
        // Plugin resource packages may be >= 0x80 (this firmware uses 0xfc).
        // Those valid IDs are signed-negative Java ints; only -1 means NO_ID.
        if(view==null||view.getId()==-1)return false;
        try{return pkg.equals(view.getResources().getResourcePackageName(view.getId()))
                &&name.equals(view.getResources().getResourceEntryName(view.getId()));}
        catch(RuntimeException unsupported){return false;}
    }
    private static boolean pluginDateResource(TextView text) {
        return pluginSlotResource(text,0)||pluginSlotResource(text,1)||pluginSlotResource(text,2)||resource(text,PLUGIN_PACKAGE,"date_text_ext");
    }
    private static boolean plugin(String kind){return PLUGIN_DATE.equals(kind)||PLUGIN_BASE_DATE.equals(kind);}
    private static boolean pluginFieldResource(TextView text,int field){
        return field==3?resource(text,PLUGIN_PACKAGE,"date_text_ext"):pluginSlotResource(text,field==4?2:field);
    }
    private static boolean pluginSlotResource(TextView text,int slot) {
        return resource(text,PLUGIN_PACKAGE,slot==0?"date_text":slot==1?"week_text":"extra_message_content");
    }
    private static TimeZone validZone(String id) {
        if(id==null||id.isEmpty())return null;
        TimeZone zone=TimeZone.getTimeZone(id);
        // TimeZone silently maps invalid IDs to GMT; that would corrupt dual-clock dates.
        return !"GMT".equals(zone.getID())||id.equals("GMT")||id.equals("UTC")?zone:null;
    }
    private static TimeZone residentOffset(String input) {
        try {
            float hours=Float.parseFloat(input.trim());
            if(!Float.isFinite(hours)||Math.abs(hours)>24f)return null;
            // Match native getResidentTimeInfo's floor/ceil and truncated fractional minutes.
            int whole=(int)(hours>0f?Math.floor(hours):Math.ceil(hours));
            int fraction=(int)(Math.abs(hours-whole)*60f);
            int minutes=whole*60+(hours<0f?-fraction:fraction);
            return new SimpleTimeZone(minutes*60000,"GMT"+(minutes<0?"-":"+")+Math.abs(whole)+":"+fraction);
        }catch(NumberFormatException invalid){return null;}
    }
    private static Field field(Class<?> type,String name) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(NoSuchFieldException missing){/* Parent. */}catch(RuntimeException inaccessible){return null;}return null;
    }
    private static Method method(Class<?> type,String name) {
        try{Method m=type.getMethod(name);m.setAccessible(true);return m;}catch(NoSuchMethodException missing){/* Non-public. */}catch(RuntimeException inaccessible){return null;}
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name);m.setAccessible(true);return m;}
        catch(NoSuchMethodException missing){/* Parent. */}catch(RuntimeException inaccessible){return null;}return null;
    }
}
