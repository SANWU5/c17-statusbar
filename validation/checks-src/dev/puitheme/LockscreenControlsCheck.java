// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.oplus.keyguard.clock.digital.ui.view.DateMessageView;
import com.oplus.keyguard.clock.digital.ui.view.ExtraMessageView;
import com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView;
import com.android.keyguard.OplusLockIconView;
import com.oplus.systemui.keyguard.view.CustomOplusKeyguardStyleClock;
import com.oplus.systemui.shared.clocks.SingleClockView;
import com.oplus.systemui.shared.clocks.DualClockView;
import com.oplus.systemui.shared.clocks.RedHorizontalSingleClockView;
import java.lang.reflect.Field;
import java.util.TimeZone;

/** Tests native final-write scope, per-date timezone, restoration and draw-only lock ownership. */
public final class LockscreenControlsCheck {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Keyguard controls "+checks);}
    private static void equal(Object a,Object b){check(a==null?b==null:a.equals(b));}
    private static Bundle settings(String pattern){Bundle b=new Bundle();b.putBoolean(LockscreenControls.DATE_ENABLED,true);b.putString(LockscreenControls.DATE_FORMAT,pattern);return b;}
    private static CharSequence write(LockscreenControls helper,TextView view,CharSequence nativeText,long now){CharSequence result=helper.formatDateAt(view,nativeText,now);view.setText(result);return result;}
    public static void main(String[] args)throws Exception {
        defaultAndRestore();dualTimeZones();redWeek();exactScope();drawOnlyLock();cacheAndSeconds();safeAndRemoval();
        pluginDate();pluginScopeAndLifecycle();nativeResourceLock();diagnosticNativeOverwrite();pluginWriterTransactions();baseProxyDate();
        System.out.println("Lockscreen controls checks passed: "+checks);
    }
    private static void defaultAndRestore(){
        LockscreenControls helper=new LockscreenControls();SingleClockView clock=new SingleClockView(new Context());
        clock.mDate.setText("native initial");helper.attach(clock);helper.configure(new Bundle());
        equal("native initial",clock.mDate.getText());float size=clock.mDate.getTextSize();Object font=clock.mDate.getTypeface();
        helper.configure(settings("yyyy年M月d日 {星期}"));
        check(!"native initial".contentEquals(clock.mDate.getText()));
        long now=1704069000000L;clock.mTimeZone=TimeZone.getTimeZone("UTC");
        equal("2024年1月1日 星期一",write(helper,clock.mDate,"native latest",now));
        equal(size,clock.mDate.getTextSize());check(font==clock.mDate.getTypeface());equal(0,clock.mDate.typefaceWrites);
        equal(0,clock.mDate.layoutRequests);equal(View.VISIBLE,clock.mDate.getVisibility());
        helper.configure(new Bundle());equal("native latest",clock.mDate.getText());
        write(helper,clock.mDate,"new OFF native",now);helper.configure(settings("yyyy"));helper.releaseRuntime();
        equal("new OFF native",clock.mDate.getText());
    }
    private static void dualTimeZones(){
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
        LockscreenControls helper=new LockscreenControls();DualClockView clock=new DualClockView(new Context());helper.attach(clock);
        helper.configure(settings("yyyy-MM-dd HH:mm {周}"));long now=1704069000000L;
        equal("2024-01-01 08:30 周一",write(helper,clock.locatedDate,"local native",now));
        equal("2023-12-31 16:30 周日",write(helper,clock.residentDate,"resident native",now));
        clock.residentTimeInfo.zone="当地语言的标准时间";
        clock.residentWeatherInfo.zone="9.0";equal("2024-01-01 09:30 周一",write(helper,clock.residentDate,"Tokyo native",now));
        clock.residentWeatherInfo.zone="5.5";equal("2024-01-01 06:00 周一",write(helper,clock.residentDate,"half native",now));
        clock.residentWeatherInfo.zone="-3.5";equal("2023-12-31 21:00 周日",write(helper,clock.residentDate,"negative native",now));
        clock.residentWeatherInfo.zone="0.0";equal("2024-01-01 00:30 周一",write(helper,clock.residentDate,"zero native",now));
        for(String zone:new String[]{null,"","made/up","GMT+99:99","NaN","Infinity","99.0"}) {
            clock.residentWeatherInfo.zone=zone;equal("invalid zone native",write(helper,clock.residentDate,"invalid zone native",now));
        }
        clock.residentWeatherInfo=null;equal("pending native",write(helper,clock.residentDate,"pending native",now));
        helper.configure(new Bundle());equal("local native",clock.locatedDate.getText());equal("pending native",clock.residentDate.getText());
        }finally{TimeZone.setDefault(previous);}
    }
    private static void redWeek(){
        LockscreenControls helper=new LockscreenControls();RedHorizontalSingleClockView clock=new RedHorizontalSingleClockView(new Context());
        clock.tvDate.setText("native date");clock.tvWeek.setText("native week");helper.attach(clock);helper.configure(settings("yyyy-MM-dd {星期}"));
        check(!"native date".contentEquals(clock.tvDate.getText()));equal("",clock.tvWeek.getText());
        write(helper,clock.tvWeek,"latest week",1704069000000L);helper.configure(new Bundle());
        equal("native date",clock.tvDate.getText());equal("latest week",clock.tvWeek.getText());
    }
    private static TextView resourceDate(Context context,String pkg,String name){
        return new TextView(context){final Resources resources=new Resources(){@Override public String getResourcePackageName(int id){return pkg;}@Override public String getResourceEntryName(int id){return name;}};
            @Override public Resources getResources(){return resources;}@Override public int getId(){return 0x7f0a06f0;}};
    }
    private static final class NamedResources extends Resources {
        final String pkg,name;int reads;
        NamedResources(String pkg,String name){this.pkg=pkg;this.name=name;}
        @Override public String getResourcePackageName(int id){reads++;return pkg;}
        @Override public String getResourceEntryName(int id){reads++;return name;}
    }
    private static FrameLayout host(Context context,String pkg,String name){
        return new FrameLayout(context){final Resources resources=new NamedResources(pkg,name);
            @Override public Resources getResources(){return resources;}@Override public int getId(){return 0x7f0a0600;}};
    }
    private static final class PluginFixture {
        final Context context=new Context();
        final FrameLayout keyguard=host(context,"com.android.systemui","keyguard_style_clock");
        final DateMessageView owner=new DateMessageView(context);
        final NamedResources dateResources=new NamedResources("com.oplus.keyguard.personality.clocks","date_text");
        PluginFixture(){
            owner.dateTextView=new MyCustomizedTextView(context){@Override public Resources getResources(){return dateResources;}@Override public int getId(){return 0xfc0900ee;}};
            owner.weekTextView=new MyCustomizedTextView(context){final Resources resources=new NamedResources("com.oplus.keyguard.personality.clocks","week_text");
                @Override public Resources getResources(){return resources;}@Override public int getId(){return 0xfc090358;}};
            owner.extraMsgView=new ExtraMessageView(context){final Resources resources=new NamedResources("com.oplus.keyguard.personality.clocks","extra_text");
                @Override public Resources getResources(){return resources;}@Override public int getId(){return 0xfc090136;}};
            owner.extraMsgView.messageContent=new MyCustomizedTextView(context){final Resources resources=new NamedResources("com.oplus.keyguard.personality.clocks","extra_message_content");
                @Override public Resources getResources(){return resources;}@Override public int getId(){return 0xfc090134;}};
            owner.addView(owner.dateTextView);owner.addView(owner.weekTextView);owner.addView(owner.extraMsgView);
            owner.extraMsgView.addView(owner.extraMsgView.messageContent);keyguard.addView(owner);
            owner.dateTextView.setText("native date");owner.weekTextView.setText("native week");owner.extraMsgView.messageContent.setText("native lunar");
        }
    }
    private static void pluginDate(){
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            LockscreenControls helper=new LockscreenControls();PluginFixture f=new PluginFixture();
            helper.attach(f.owner);helper.configure(settings("yyyy年M月d日 {星期}"));
            equal("",f.owner.weekTextView.getText());equal("",f.owner.extraMsgView.messageContent.getText());
            long now=1704069000000L;
            equal("2024年1月1日 星期一",write(helper,f.owner.dateTextView,"latest OEM date",now));
            equal("",write(helper,f.owner.weekTextView,"latest OEM week",now));
            equal("",write(helper,f.owner.extraMsgView.messageContent,"latest OEM lunar",now));
            int reads=f.dateResources.reads;CharSequence first=f.owner.dateTextView.getText();
            for(int i=1;i<1000;i++)check(first==write(helper,f.owner.dateTextView,"fresh native "+i,now+i));
            equal(reads,f.dateResources.reads); // Bound final writes never resolve resource names again.
            equal(13f,f.owner.dateTextView.getTextSize());equal(0,f.owner.dateTextView.typefaceWrites);
            equal(0,f.owner.dateTextView.layoutRequests);equal(View.VISIBLE,f.owner.weekTextView.getVisibility());
            check(helper.diagnosticSummary().contains("pluginDigital=3"));
            check(!helper.diagnosticSummary().contains("latest OEM"));
            helper.configure(new Bundle());equal("fresh native 999",f.owner.dateTextView.getText());
            equal("latest OEM week",f.owner.weekTextView.getText());equal("latest OEM lunar",f.owner.extraMsgView.messageContent.getText());
            write(helper,f.owner.dateTextView,"OFF native update",now);helper.configure(settings("yyyy"));helper.releaseRuntime();
            equal("OFF native update",f.owner.dateTextView.getText());equal("latest OEM week",f.owner.weekTextView.getText());
        }finally{TimeZone.setDefault(previous);}
    }
    private static void pluginScopeAndLifecycle(){
        long now=1704069000000L;LockscreenControls helper=new LockscreenControls();helper.configure(settings("yyyy"));
        PluginFixture f=new PluginFixture();
        f.keyguard.removeView(f.owner);FrameLayout preview=new FrameLayout(f.context);preview.addView(f.owner);
        helper.attach(f.owner);equal("preview native",write(helper,f.owner.dateTextView,"preview native",now));
        preview.removeView(f.owner);f.keyguard.addView(f.owner);
        helper.attach(f.owner.dateTextView);equal("2024",write(helper,f.owner.dateTextView,"bound plugin date",now));
        helper.attach(f.owner.weekTextView);helper.attach(f.owner.extraMsgView.messageContent);
        TextView nonField=resourceDate(f.context,"com.oplus.keyguard.personality.clocks","date_text");f.owner.addView(nonField);
        equal("not exact owner field",write(helper,nonField,"not exact owner field",now));
        TextView charging=resourceDate(f.context,"com.oplus.keyguard.personality.clocks","charging_text");f.owner.extraMsgView.addView(charging);
        equal("charging remains",write(helper,charging,"charging remains",now));
        f.keyguard.removeView(f.owner);preview.addView(f.owner);
        equal("recycled date",write(helper,f.owner.dateTextView,"recycled date",now));helper.refresh();
        equal("native week",f.owner.weekTextView.getText());equal("native lunar",f.owner.extraMsgView.messageContent.getText());
        preview.removeView(f.owner);f.keyguard.addView(f.owner);helper.attach(f.owner);
        write(helper,f.owner.dateTextView,"before detach",now);helper.detach(f.owner);equal("before detach",f.owner.dateTextView.getText());
        helper.attach(f.owner);write(helper,f.owner.dateTextView,"before ID change",now);
        helper.classificationChanged(f.owner.dateTextView);equal("before ID change",f.owner.dateTextView.getText());
        check(!View.class.isAssignableFrom(com.oplus.keyguard.clock.base.ui.view.DateMessageView.class));
        PluginFixture wrong=new PluginFixture();wrong.owner.dateTextView=resourceDate(wrong.context,"another.app","date_text");
        wrong.owner.addView(wrong.owner.dateTextView);helper.attach(wrong.owner);
        equal("wrong resource package",write(helper,wrong.owner.dateTextView,"wrong resource package",now));
        Bundle safe=settings("yyyy");safe.putBoolean(StatusBarSettings.SAFE_MODE,true);helper.configure(safe);
        equal("safe plugin",write(helper,f.owner.dateTextView,"safe plugin",now));
    }
    private static void nativeResourceLock(){
        Context context=new Context();LockscreenControls helper=new LockscreenControls();
        FrameLayout outer=host(context,"com.android.systemui","lock_icon_view");
        LinearLayout owner=new LinearLayout(context){final Resources resources=new NamedResources("com.android.systemui","lock_icon_view");
            @Override public Resources getResources(){return resources;}@Override public int getId(){return 0x7f0a0005;}};
        NamedResources iconResources=new NamedResources("com.android.systemui","lock_icon");
        ImageView icon=new ImageView(context){@Override public Resources getResources(){return iconResources;}@Override public int getId(){return 0x7f0a0006;}};
        ImageView fingerprint=new ImageView(context);TextView charging=new TextView(context);
        owner.addView(icon);owner.addView(fingerprint);owner.addView(charging);outer.addView(owner);
        helper.attach(icon);check(!helper.hideLockChild(owner,icon));
        int invalidations=owner.invalidations;Bundle b=new Bundle();b.putBoolean(LockscreenControls.HIDE_LOCK,true);helper.configure(b);
        check(owner.invalidations>invalidations);check(helper.hideLockChild(owner,icon));
        int reads=iconResources.reads;
        for(int i=0;i<1000;i++)check(helper.hideLockChild(owner,icon));
        equal(reads,iconResources.reads);check(!helper.hideLockChild(owner,fingerprint));check(!helper.hideLockChild(owner,charging));
        equal(View.VISIBLE,icon.getVisibility());equal(1f,icon.getAlpha());equal(0,icon.layoutRequests);equal(0,owner.layoutRequests);
        check(helper.diagnosticSummary().contains("lockParents=1"));
        helper.configure(new Bundle());check(!helper.hideLockChild(owner,icon));
        helper.configure(b);outer.removeView(owner);new FrameLayout(context).addView(owner);
        check(!helper.hideLockChild(owner,icon)); // Same IDs without verified keyguard ancestor are not enough.
        helper.detach(icon);check(helper.diagnosticSummary().contains("lockParents=0"));
        FrameLayout falseOwner=host(context,"com.android.systemui","lock_icon_view");falseOwner.addView(new ImageView(context));
        check(!helper.hideLockChild(falseOwner,falseOwner.getChildAt(0)));
        helper.releaseRuntime();check(!helper.hideLockChild(owner,icon));
    }
    private static void diagnosticNativeOverwrite(){
        LockscreenControls helper=new LockscreenControls();PluginFixture f=new PluginFixture();
        helper.attach(f.owner);helper.configure(settings("yyyy-MM-dd {星期}"));
        check(helper.diagnosticSummary().contains("shownSlots=1/1/1"));
        check(helper.diagnosticSummary().contains("matchSlots=1/1/1"));
        check(helper.diagnosticSummary().contains("digitalText=3"));
        check(helper.diagnosticSummary().contains("unboundVisible=0"));
        ((MyCustomizedTextView)f.owner.dateTextView).tryUpdateText("unobserved OEM date");
        ((MyCustomizedTextView)f.owner.weekTextView).tryUpdateText("unobserved OEM week");
        ((MyCustomizedTextView)f.owner.extraMsgView.messageContent).tryUpdateText("unobserved OEM lunar");
        String diagnostic=helper.diagnosticSummary();check(diagnostic.contains("matchSlots=0/0/0"));
        check(diagnostic.contains("finalWrites=0"));check(diagnostic.contains("refreshWrites=3"));
        check(!diagnostic.contains("unobserved OEM"));
        helper.refresh();check(helper.diagnosticSummary().contains("matchSlots=1/1/1"));
        TextView unbound=resourceDate(f.context,"com.oplus.keyguard.personality.clocks","date_text");f.owner.addView(unbound);
        check(helper.diagnosticSummary().contains("unboundVisible=1"));
        // Diagnostic reads do not claim a bypassed setter as the latest native input.
        helper.configure(new Bundle());equal("native date",f.owner.dateTextView.getText());
        equal("native week",f.owner.weekTextView.getText());equal("native lunar",f.owner.extraMsgView.messageContent.getText());
    }
    private static void pluginWriterTransactions(){
        LockscreenControls helper=new LockscreenControls();PluginFixture f=new PluginFixture();helper.attach(f.owner);
        helper.configure(settings("yyyy年M月d日 {星期}"));Object typeface=f.owner.dateTextView.getTypeface();float size=f.owner.dateTextView.getTextSize();
        String expected=TimeFormat.format("yyyy年M月d日 {星期}",System.currentTimeMillis(),TimeZone.getDefault());
        for(int i=0;i<100;i++)try(LockscreenControls.NativeDateWrite write=helper.beginPluginDateWrite(f.owner.dateTextView,"plugin native "+i)) {
            check(write!=null);equal(expected,write.text());
            // Real tryUpdateText → HDR override → framework final setter. Only the
            // outer native argument is remembered, never the custom nested argument.
            helper.refresh(); // A native text/layout listener may re-enter an owner refresh.
            equal(null,helper.beginPluginDateWrite(f.owner.dateTextView,write.text()));
            equal(write.text(),helper.formatDate(f.owner.dateTextView,write.text()));
            ((MyCustomizedTextView)f.owner.dateTextView).tryUpdateText(write.text());
        }
        equal(expected,f.owner.dateTextView.getText());equal(typeface,f.owner.dateTextView.getTypeface());equal(size,f.owner.dateTextView.getTextSize());
        equal(0,f.owner.dateTextView.typefaceWrites);helper.configure(new Bundle());equal("plugin native 99",f.owner.dateTextView.getText());
        try(LockscreenControls.NativeDateWrite write=helper.beginPluginDateWrite(f.owner.dateTextView,"disabled native")){
            check(write!=null);equal("disabled native",write.text());f.owner.dateTextView.setText(write.text());
        }
        helper.configure(settings("yyyy"));helper.releaseRuntime();equal("disabled native",f.owner.dateTextView.getText());
        LockscreenControls unrelated=new LockscreenControls();unrelated.configure(settings("yyyy"));
        TextView hour=resourceDate(f.context,"com.oplus.keyguard.personality.clocks","hour_ones");f.owner.addView(hour);
        equal(null,unrelated.beginPluginDateWrite(hour,"12"));equal("",hour.getText());
        TextView impostor=resourceDate(f.context,"com.oplus.keyguard.personality.clocks","date_text");f.owner.addView(impostor);
        equal(null,unrelated.beginPluginDateWrite(impostor,"not actual date field"));
    }
    private static void baseProxyDate(){
        LockscreenControls helper=new LockscreenControls();Context context=new Context();
        FrameLayout keyguard=host(context,"com.android.systemui","keyguard_style_clock"),nativeRoot=new FrameLayout(context);keyguard.addView(nativeRoot);
        com.oplus.keyguard.clock.base.ui.view.DateMessageView proxy=new com.oplus.keyguard.clock.base.ui.view.DateMessageView();proxy.viewParent=nativeRoot;
        proxy.dateTextView=resourceDate(context,"com.oplus.keyguard.personality.clocks","date_text");
        proxy.dateTextViewExt=resourceDate(context,"com.oplus.keyguard.personality.clocks","date_text_ext");
        proxy.weekTextView=resourceDate(context,"com.oplus.keyguard.personality.clocks","week_text");
        proxy.extraMsgView=baseExtra(context,"extra_text");proxy.extraMsgViewExt=baseExtra(context,"extra_text_ext");
        TextView[] texts={proxy.dateTextView,proxy.dateTextViewExt,proxy.weekTextView,proxy.extraMsgView.messageContent,proxy.extraMsgViewExt.messageContent};
        nativeRoot.addView(proxy.dateTextView);nativeRoot.addView(proxy.dateTextViewExt);nativeRoot.addView(proxy.weekTextView);
        nativeRoot.addView(proxy.extraMsgView);nativeRoot.addView(proxy.extraMsgViewExt);
        for(int i=0;i<texts.length;i++)texts[i].setText("base native "+i);
        helper.onPluginClockUpdated(proxy);helper.configure(settings("yyyy-MM-dd {星期}"));
        long now=1704069000000L;TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            equal("2024-01-01 星期一",write(helper,texts[0],"date latest",now));
            equal("2024-01-01 星期一",write(helper,texts[1],"date ext latest",now));
            for(int i=2;i<texts.length;i++)equal("",write(helper,texts[i],"extra latest "+i,now));
            check(helper.diagnosticSummary().contains("pluginBase=5"));
            for(TextView text:texts){equal(0,text.typefaceWrites);equal(13f,text.getTextSize());}
            helper.configure(new Bundle());equal("date latest",texts[0].getText());equal("date ext latest",texts[1].getText());
            for(int i=2;i<texts.length;i++)equal("extra latest "+i,texts[i].getText());
            // Theme preview has the same fields/resources but no real keyguard host.
            keyguard.removeView(nativeRoot);FrameLayout preview=new FrameLayout(context);preview.addView(nativeRoot);
            helper.configure(settings("yyyy"));helper.onPluginClockUpdated(proxy);
            equal("preview original",write(helper,texts[0],"preview original",now));
            preview.removeView(nativeRoot);keyguard.addView(nativeRoot);helper.onPluginClockUpdated(proxy);
            equal("2024",write(helper,texts[0],"reattached native",now));
            helper.detach(nativeRoot);equal("reattached native",texts[0].getText());helper.releaseRuntime();
        }finally{TimeZone.setDefault(previous);}
    }
    private static com.oplus.keyguard.clock.base.ui.view.ExtraMessageView baseExtra(Context context,String name){
        com.oplus.keyguard.clock.base.ui.view.ExtraMessageView extra=new com.oplus.keyguard.clock.base.ui.view.ExtraMessageView(context){
            final Resources resources=new NamedResources("com.oplus.keyguard.personality.clocks",name);
            @Override public Resources getResources(){return resources;}@Override public int getId(){return 0xfc090136;}};
        extra.messageContent=resourceDate(context,"com.oplus.keyguard.personality.clocks","extra_message_content");extra.addView(extra.messageContent);return extra;
    }
    private static void exactScope(){
        LockscreenControls helper=new LockscreenControls();helper.configure(settings("yyyy"));Context context=new Context();
        FrameLayout notification=new FrameLayout(context);TextView fake=resourceDate(context,"com.android.systemui","keyguard_date");notification.addView(fake);
        equal("notification date",write(helper,fake,"notification date",1704069000000L));
        CustomOplusKeyguardStyleClock custom=new CustomOplusKeyguardStyleClock(context);
        TextView date=resourceDate(context,"com.android.systemui","keyguard_date");date.setText("plugin date");custom.addView(date);helper.attach(custom);
        equal("2024",write(helper,date,"plugin update",1704069000000L));
        TextView extra=resourceDate(context,"com.android.systemui","clock_extra_text");custom.addView(extra);
        equal("battery charging",write(helper,extra,"battery charging",1704069000000L));
        TextView appDate=resourceDate(context,"app.other","date");custom.addView(appDate);
        equal("other app",write(helper,appDate,"other app",1704069000000L));
        custom.removeView(date);notification.addView(date);equal("reparented native",write(helper,date,"reparented native",1704069000000L));
        SingleClockView clock=new SingleClockView(context);TextView different=new TextView(context);clock.addView(different);
        equal("not exact field",write(helper,different,"not exact field",1704069000000L));
    }
    private static void drawOnlyLock(){
        LockscreenControls helper=new LockscreenControls();OplusLockIconView lock=new OplusLockIconView(new Context());helper.attach(lock);
        check(!helper.hideLockChild(lock,lock.mLockIcon));int original=lock.invalidations;
        Bundle config=new Bundle();config.putBoolean(LockscreenControls.HIDE_LOCK,true);helper.configure(config);check(lock.invalidations>original);
        check(helper.hideLockChild(lock,lock.mLockIcon));check(helper.hideLockChild(lock,lock.mBgView));check(!helper.hideLockChild(lock,lock.fingerprint));
        check(!helper.hideLockChild(new FrameLayout(new Context()),lock.mLockIcon));check(!helper.hideLockChild(lock,new View(new Context())));
        equal(View.VISIBLE,lock.getVisibility());equal(View.VISIBLE,lock.mLockIcon.getVisibility());equal(1f,lock.mLockIcon.getAlpha());
        equal(0,lock.layoutRequests);equal(0,lock.mLockIcon.layoutRequests);equal(0,lock.fingerprint.layoutRequests);
        helper.configure(new Bundle());check(!helper.hideLockChild(lock,lock.mLockIcon));check(lock.invalidations>original+1);
    }
    private static void cacheAndSeconds(){
        LockscreenControls helper=new LockscreenControls();SingleClockView clock=new SingleClockView(new Context());clock.mTimeZone=TimeZone.getTimeZone("UTC");helper.attach(clock);helper.configure(settings("HH:mm"));
        long now=1704069000000L;CharSequence first=write(helper,clock.mDate,"first",now);
        for(int i=1;i<1000;i++)check(first==write(helper,clock.mDate,"native "+i,now+i));
        helper.configure(settings("HH:mm:ss"));equal("00:30:01",write(helper,clock.mDate,"second",now+1000));
        equal("00:30:02",write(helper,clock.mDate,"next",now+2000));equal(0,clock.mDate.layoutRequests);
    }
    private static void safeAndRemoval()throws Exception {
        LockscreenControls helper=new LockscreenControls();SingleClockView clock=new SingleClockView(new Context());clock.mDate.setText("native");helper.attach(clock);
        Bundle invalid=settings("{invalid}");helper.configure(invalid);equal("native",clock.mDate.getText());
        Bundle safe=settings("yyyy");safe.putBoolean(StatusBarSettings.SAFE_MODE,true);safe.putBoolean(LockscreenControls.HIDE_LOCK,true);helper.configure(safe);
        equal("safe native",write(helper,clock.mDate,"safe native",1704069000000L));
        helper.configure(settings("yyyy"));Field removed=ModuleLifecycle.class.getDeclaredField("removed");removed.setAccessible(true);removed.setBoolean(null,true);
        try{equal("removed native",write(helper,clock.mDate,"removed native",1704069000000L));}finally{removed.setBoolean(null,false);}
        helper.configure(null);equal("removed native",clock.mDate.getText());
    }
}
