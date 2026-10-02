package dev.puitheme;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import com.oplus.systemui.statusbar.widget.StatClock;
import com.oplus.systemui.qs.widget.OplusSecondCarrierText;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
public final class TextControlsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>.0001)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) throws Exception {
        Handler handler=new Handler(Looper.getMainLooper());TextControls control=new TextControls(handler);Context context=new Context();
        StatClock clock=new StatClock(context);OplusSecondCarrierText carrier=new OplusSecondCarrierText(context);
        clock.setText("系统时钟");carrier.setText("中国移动");clock.setContentDescription("原始时间");carrier.setContentDescription("原始运营商");
        control.attach(clock);control.attach(carrier);equal("系统时钟",clock.getText());equal("中国移动",carrier.getText());equal(true,handler.delayed==null);
        Bundle settings=new Bundle();settings.putBoolean("clock_controls_enabled",true);settings.putBoolean("carrier_enabled",true);
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,true);settings.putString(StatusBarSettings.CLOCK_PATTERN,"'C17' HH:mm:ss");
        settings.putString(StatusBarSettings.CARRIER_MODE,"text");settings.putString(StatusBarSettings.CARRIER_TEXT,"aiingjie");
        settings.putFloat(StatusBarSettings.CLOCK_SCALE,123.45f);settings.putFloat(StatusBarSettings.CLOCK_WEIGHT,712f);settings.putFloat(StatusBarSettings.CLOCK_SPACING,.25f);
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal(true,clock.getText().toString().matches("C17 \\d{2}:\\d{2}:\\d{2}"));equal("aiingjie",carrier.getText());
        near(13*1.2345f,clock.getTextSize());equal(712,clock.getTypeface().weight);equal(0xccffffff,clock.getCurrentTextColor());
        equal(true,handler.delayed!=null&&handler.delay>0&&handler.delay<=1000);near(.25f*4/(13*1.2345f),clock.getLetterSpacing());
        for(int i=0;i<30;i++)control.refresh();near(13*1.2345f,clock.getTextSize());
        control.enter();control.nativeText(clock,"模块自己的文字");control.nativeSize(clock,99);control.exit();
        carrier.setText(control.nativeText(carrier,"中国联通"));equal("aiingjie",carrier.getText());
        carrier.setContentDescription(control.nativeDescription(carrier,"中国联通网络"));equal("aiingjie",carrier.getContentDescription());
        control.setInteractive(false);equal(true,handler.delayed==null);control.setInteractive(true);equal(true,handler.delayed!=null);
        clock.shown=false;control.visibilityChanged();equal(true,handler.delayed==null);clock.shown=true;control.refresh();equal(true,handler.delayed!=null);
        clock.attached=false;carrier.attached=false;control.detach(clock);control.detach(carrier);equal(true,handler.delayed==null);
        clock.attached=true;carrier.attached=true;control.attach(clock);control.attach(carrier);
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,false);settings.putString(StatusBarSettings.CARRIER_MODE,"original");
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal("系统时钟",clock.getText());equal("中国联通",carrier.getText());equal("原始时间",clock.getContentDescription());equal("中国联通网络",carrier.getContentDescription());equal(true,handler.delayed==null);
        settings.putString(StatusBarSettings.CARRIER_MODE,"time");settings.putString(StatusBarSettings.CARRIER_PATTERN,"HH:mm");control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal(true,carrier.getText().toString().matches("\\d{2}:\\d{2}"));equal(true,handler.delayed!=null&&handler.delay>0&&handler.delay<=60000);
        settings.putString(StatusBarSettings.CARRIER_PATTERN,"broken'");settings.putFloat(StatusBarSettings.CLOCK_SCALE,Float.NaN);
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());near(13,clock.getTextSize());
        equal(true,carrier.getText().toString().contains("周"));
        Map<String,Integer> palette=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);palette.put("clock_color_dark",0xff123456);
        control.configure(settings,palette,Collections.emptyMap());equal(0xcc123456,clock.getCurrentTextColor());
        equal(0xe6000000,control.nativeColor(clock,0xe6000000));
        equal(TextControls.NONE,TextControls.namedKind("com.oplus.systemui.qs.widget.OplusQSCarrierText","carrier_text",""));
        equal(TextControls.NONE,TextControls.namedKind("com.android.systemui.statusbar.policy.Clock","clock","com.android.systemui.QSHeader"));
        equal(TextControls.CARRIER,TextControls.namedKind("android.widget.TextView","carrier_text","com.android.systemui.shade.carrier.ShadeCarrier"));
        equal(TextControls.NONE,control.kind(new TextView(context)));
        shadeClocks(context);
        independentShadeClocks(context);
        classificationCaching(context);
        System.out.println(checks+" text state, precision, tint and scheduler checks passed");
    }

    private static final class ClockResources extends android.content.res.Resources {
        final String name, owner;
        ClockResources(String name, boolean system) { this.name=name; owner=system?"com.android.systemui":"other.app"; }
        @Override public String getResourceEntryName(int id) { return name; }
        @Override public String getResourcePackageName(int id) { return owner; }
    }

    private static final class ClassificationResources extends android.content.res.Resources {
        int names,packages;
        @Override public String getResourcePackageName(int id) {packages++;return "com.android.systemui";}
        @Override public String getResourceEntryName(int id) {
            names++;
            switch(id) {
                case 101:return "ordinary_text";
                case 102:return "qs_carrier_text";
                case 103:return "carrier_text";
                case 104:return "qs_footer_clock";
                case 201:return "simple_qs_container";
                case 202:return "qs_status_bar_container_layout";
                case 203:return "keyguard_header";
                default:return "unrelated_layout";
            }
        }
    }
    private static final class CacheText extends TextView {
        int id=101;
        final ClassificationResources resources;
        CacheText(Context context,ClassificationResources resources){super(context);this.resources=resources;}
        @Override public int getId(){return id;}
        @Override public android.content.res.Resources getResources(){return resources;}
    }
    private static final class CacheHeader extends android.view.ViewGroup {
        int id,parentReads;
        final ClassificationResources resources;
        CacheHeader(Context context,ClassificationResources resources,int id){super(context);this.resources=resources;this.id=id;}
        @Override public int getId(){return id;}
        @Override public android.content.res.Resources getResources(){return resources;}
        @Override public android.view.ViewParent getParent(){parentReads++;return super.getParent();}
    }
    private static Object privateField(Object owner,String name) throws Exception {
        java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void classificationCaching(Context context) throws Exception {
        Handler handler=new Handler(Looper.getMainLooper());TextControls control=new TextControls(handler);
        ClassificationResources resources=new ClassificationResources();
        CacheHeader controlHeader=new CacheHeader(context,resources,202),notificationHeader=new CacheHeader(context,resources,201);
        CacheHeader lockHeader=new CacheHeader(context,resources,203),unrelated=new CacheHeader(context,resources,204);
        CacheHeader wrapper=new CacheHeader(context,resources,-1);controlHeader.addView(wrapper);
        CacheText text=new CacheText(context,resources);wrapper.addView(text);text.setText("原生内容");
        equal(TextControls.NONE,control.kind(text));equal("",control.group(text));
        int names=resources.names,packages=resources.packages,parentReads=wrapper.parentReads+controlHeader.parentReads;
        android.graphics.Canvas canvas=new android.graphics.Canvas();
        for(int i=0;i<10000;i++){control.kind(text);control.group(text);control.beforeDraw(text,canvas);}
        equal(names,resources.names);equal(packages,resources.packages);
        equal(parentReads,wrapper.parentReads+controlHeader.parentReads);
        // An own-ID change is detected even before the setId observer is delivered.
        text.id=102;equal(TextControls.CARRIER,control.kind(text));equal(CarrierPanels.CONTROL,control.group(text));
        equal(true,resources.names>names);
        Bundle settings=new Bundle();
        for(String group:CarrierPanels.GROUPS){settings.putBoolean(CarrierPanels.key(group,"enabled"),true);settings.putString(CarrierPanels.key(group,"mode"),"text");}
        settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION,"text"),"通知内容");
        settings.putString(CarrierPanels.key(CarrierPanels.CONTROL,"text"),"控制内容");
        settings.putString(CarrierPanels.key(CarrierPanels.LOCKSCREEN,"text"),"锁屏内容");
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());control.attach(text);
        equal("控制内容",text.getText());text.setText(control.nativeText(text,"新的原生内容"));
        names=resources.names;packages=resources.packages;parentReads=wrapper.parentReads+controlHeader.parentReads;
        int layouts=text.layoutRequests,redraws=text.invalidations;
        for(int i=0;i<10000;i++){control.kind(text);control.group(text);control.replaces(text);control.beforeDraw(text,canvas);control.beforeMeasure(text);}
        equal(names,resources.names);equal(packages,resources.packages);
        equal(parentReads,wrapper.parentReads+controlHeader.parentReads);equal(layouts,text.layoutRequests);equal(redraws,text.invalidations);
        // Direct parent is unchanged: the exact native assignParent observer invalidates indexed descendants.
        wrapper.parent=notificationHeader;control.classificationChanged(wrapper);
        equal(CarrierPanels.NOTIFICATION,control.group(text));equal("通知内容",text.getText());equal(wrapper,text.getParent());
        notificationHeader.id=202;control.classificationChanged(notificationHeader);
        equal(CarrierPanels.CONTROL,control.group(text));equal("控制内容",text.getText());
        wrapper.parent=lockHeader;text.id=103;control.classificationChanged(wrapper);
        equal(TextControls.CARRIER,control.kind(text));equal(CarrierPanels.LOCKSCREEN,control.group(text));equal("锁屏内容",text.getText());
        wrapper.parent=unrelated;control.classificationChanged(wrapper);
        equal(TextControls.NONE,control.kind(text));equal("",control.group(text));equal("新的原生内容",text.getText());near(13f,text.getTextSize());
        text.setText(control.nativeText(text,"未接管时的原生更新"));
        text.parent=controlHeader;text.id=102;
        equal(TextControls.CARRIER,control.kind(text));equal(CarrierPanels.CONTROL,control.group(text));
        control.attach(text);equal("控制内容",text.getText());
        settings.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL,"enabled"),false);
        control.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());equal("未接管时的原生更新",text.getText());
        control.detach(text);text.parent=notificationHeader;notificationHeader.id=201;control.attach(text);
        equal(CarrierPanels.NOTIFICATION,control.group(text));equal("通知内容",text.getText());
        // A currently negative candidate is still indexed: adding a real header activates it without an ID change.
        CacheText dormant=new CacheText(context,resources);dormant.id=102;CacheHeader dormantWrapper=new CacheHeader(context,resources,-1);
        unrelated.addView(dormantWrapper);dormantWrapper.addView(dormant);
        equal(TextControls.NONE,control.kind(dormant));dormantWrapper.parent=controlHeader;control.classificationChanged(dormantWrapper);
        equal(TextControls.CARRIER,control.kind(dormant));equal(CarrierPanels.CONTROL,control.group(dormant));
        @SuppressWarnings("unchecked") Map<TextView,Object> cache=(Map<TextView,Object>)privateField(control,"classifications");
        equal(true,cache instanceof java.util.WeakHashMap);
        Object classification=cache.get(text);
        equal(true,privateField(classification,"parent") instanceof java.lang.ref.WeakReference);
        for(Object reference:(java.util.List<?>)privateField(classification,"ancestors"))equal(true,reference instanceof java.lang.ref.WeakReference);
        @SuppressWarnings("unchecked") Map<android.view.View,Map<TextView,Boolean>> index=(Map<android.view.View,Map<TextView,Boolean>>)privateField(control,"classificationDependents");
        equal(true,index instanceof java.util.WeakHashMap);
        for(Map<TextView,Boolean> dependents:index.values())equal(true,dependents instanceof java.util.WeakHashMap);
        for(java.lang.reflect.Field field:classification.getClass().getDeclaredFields())equal(false,android.view.View.class.isAssignableFrom(field.getType()));
    }
    private static final class NamedClock extends TextView {
        final android.content.res.Resources resources;
        NamedClock(Context context, String name, boolean system) { super(context);resources=new ClockResources(name,system); }
        @Override public int getId() { return 17; }
        @Override public android.content.res.Resources getResources() { return resources; }
    }
    private static final class ClockContainer extends android.view.ViewGroup {
        final android.content.res.Resources resources;
        ClockContainer(Context context, String name, boolean system) { super(context);resources=new ClockResources(name,system); }
        @Override public int getId() { return 18; }
        @Override public android.content.res.Resources getResources() { return resources; }
    }

    /** The animation source, real shade clock and fake clock must use one clock configuration. */
    private static void shadeClocks(Context context) {
        Handler handler=new Handler(Looper.getMainLooper());TextControls control=new TextControls(handler);
        com.oplus.systemui.separate.OplusQSSimpleHeader shade=new com.oplus.systemui.separate.OplusQSSimpleHeader(context);
        TextView status=new StatClock(context),notification=new com.oplus.systemui.qs.widget.SimpleQsClock(context);
        TextView fake=new com.oplus.systemui.qs.fake.view.QsClock(context),qs=new com.oplus.systemui.qs.widget.OplusQSClock(context);
        TextView subclass=new com.oplus.systemui.qs.widget.OplusQSClock(context) { };
        TextView recreated=new NamedClock(context,"qs_footer_clock",true);
        android.view.ViewGroup wrapper=new android.view.ViewGroup(context);shade.addView(wrapper);
        wrapper.addView(notification);wrapper.addView(fake);wrapper.addView(qs);wrapper.addView(recreated);wrapper.addView(subclass);
        TextView[] clocks={status,notification,fake,qs,recreated,subclass};
        for(TextView clock:clocks) { clock.setText("系统时钟");clock.setContentDescription("系统描述");equal(TextControls.CLOCK,control.kind(clock));equal("clock",control.group(clock));control.attach(clock); }
        OplusSecondCarrierText carrier=new OplusSecondCarrierText(context);wrapper.addView(carrier);carrier.setText("中国移动");control.attach(carrier);
        Bundle settings=new Bundle();settings.putBoolean("clock_controls_enabled",true);
        settings.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION,"enabled"),true);
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,true);settings.putString(StatusBarSettings.CLOCK_PATTERN,"'C17' HH:mm:ss");
        settings.putFloat(StatusBarSettings.CLOCK_SCALE,150f);settings.putFloat(StatusBarSettings.CLOCK_WEIGHT,712f);settings.putFloat(StatusBarSettings.CLOCK_SPACING,.25f);
        settings.putFloat(StatusBarSettings.CLOCK_OFFSET_X,3f);settings.putFloat(StatusBarSettings.CLOCK_OFFSET_Y,-2f);
        settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION,"mode"),"text");settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION,"text"),"独立通知文字");
        Map<String,Integer> palette=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);palette.put("clock_color_dark",0xff123456);
        control.configure(settings,palette,Collections.emptyMap());
        for(TextView clock:clocks) {
            equal(status.getText(),clock.getText());equal(status.getText(),clock.getContentDescription());near(19.5f,clock.getTextSize());
            equal(712,clock.getTypeface().weight);near(1f/19.5f,clock.getLetterSpacing());equal(0xcc123456,clock.getCurrentTextColor());
            android.graphics.Canvas canvas=new android.graphics.Canvas();control.beforeDraw(clock,canvas);near(12f,canvas.translateX);near(-8f,canvas.translateY);
            clock.setText(control.nativeText(clock,"系统更新"));equal(status.getText(),clock.getText());
            // Simulate the vendor probing width with its native 13px Paint size.
            for(int i=0;i<3;i++) { clock.setTextSize(0,13f);near(90f,control.restoreClockWidth(clock,60f,13f));near(19.5f,clock.getTextSize()); }
            near(0f,control.restoreClockWidth(clock,Float.NaN,13f));near(0f,control.restoreClockWidth(clock,-60f,13f));
            near(NumericPolicy.MAX_DRAW_PIXELS,control.restoreClockWidth(clock,Float.POSITIVE_INFINITY,13f));
            near(60f,control.restoreClockWidth(clock,60f,0f));near(19.5f,clock.getTextSize());
        }
        equal("独立通知文字",carrier.getText());equal(true,handler.delayed!=null&&handler.delay<=1000);
        near(60f,control.restoreClockWidth(carrier,60f,13f));
        settings.putBoolean("clock_size_enabled",false);control.configure(settings,palette,Collections.emptyMap());
        notification.setTextSize(0,13f);near(60f,control.restoreClockWidth(notification,60f,13f));near(13f,notification.getTextSize());
        settings.putBoolean("clock_size_enabled",true);control.configure(settings,palette,Collections.emptyMap());near(19.5f,notification.getTextSize());
        // Plain recreated copies require a known SystemUI ID and actual shade ownership.
        NamedClock foreign=new NamedClock(context,"qs_footer_clock",false);wrapper.addView(foreign);equal(TextControls.NONE,control.kind(foreign));
        NamedClock foreignDirect=new NamedClock(context,"qs_footer_clock",false);shade.addView(foreignDirect);equal(TextControls.NONE,control.kind(foreignDirect));
        NamedClock foreignQuick=new NamedClock(context,"oplus_qs_clock",false);new com.oplus.systemui.qs.OplusQuickStatusBarHeader(context).addView(foreignQuick);equal(TextControls.NONE,control.kind(foreignQuick));
        NamedClock unrelated=new NamedClock(context,"clock",true);wrapper.addView(unrelated);equal(TextControls.NONE,control.kind(unrelated));
        NamedClock lock=new NamedClock(context,"qs_footer_clock",true);new com.android.systemui.statusbar.phone.KeyguardStatusBarView(context).addView(lock);equal(TextControls.NONE,control.kind(lock));
        NamedClock orphan=new NamedClock(context,"oplus_qs_clock",true);equal(TextControls.NONE,control.kind(orphan));
        ClockContainer fakeContainer=new ClockContainer(context,"oplus_fake_clock_container",true);fakeContainer.addView(orphan);equal(TextControls.CLOCK,control.kind(orphan));
        NamedClock copied=new NamedClock(context,"oplus_qs_clock",true);new ClockContainer(context,"qs_fake_clock_container",false).addView(copied);equal(TextControls.NONE,control.kind(copied));
        equal(TextControls.CLOCK,TextControls.namedKind("com.oplus.systemui.qs.widget.SimpleQsClock","",""));
        equal(TextControls.CLOCK,TextControls.namedKind("com.oplus.systemui.qs.fake.view.QsClock","",""));
        equal(TextControls.CLOCK,TextControls.namedKind("com.android.systemui.statusbar.policy.Clock","qs_footer_clock","com.android.systemui.QSHeader"));
        equal(TextControls.NONE,TextControls.namedKind("com.android.systemui.statusbar.policy.Clock","clock","com.android.keyguard.KeyguardStatusView"));
        settings.putBoolean("clock_controls_enabled",false);control.configure(settings,palette,Collections.emptyMap());
        for(TextView clock:clocks) { equal("系统更新",clock.getText());equal("系统描述",clock.getContentDescription());near(13f,clock.getTextSize());near(0f,clock.getLetterSpacing());equal(0xccffffff,clock.getCurrentTextColor());near(60f,control.restoreClockWidth(clock,60f,13f));near(13f,clock.getTextSize()); }
        near(60f,control.restoreClockWidth(carrier,60f,13f));
        equal("独立通知文字",carrier.getText());equal(true,handler.delayed==null);
    }

    private static void independentShadeClocks(Context context) {
        Handler handler=new Handler(Looper.getMainLooper());TextControls control=new TextControls(handler);
        TextView status=new StatClock(context),notification=new com.oplus.systemui.qs.widget.SimpleQsClock(context);
        TextView qs=new com.oplus.systemui.qs.fake.view.QsClock(context),copied=new NamedClock(context,"qs_footer_clock",true);
        com.oplus.systemui.separate.OplusQSSimpleHeader header=new com.oplus.systemui.separate.OplusQSSimpleHeader(context);
        header.addView(notification);header.addView(copied);
        new com.oplus.systemui.qs.OplusQuickStatusBarHeader(context).addView(qs);
        TextView[] shades={notification,qs,copied};
        status.setText("原生状态栏");control.attach(status);
        for(TextView shade:shades) { shade.setText("原生下拉时钟");shade.setContentDescription("原生下拉描述");control.attach(shade); }
        OplusSecondCarrierText carrier=new OplusSecondCarrierText(context);header.addView(carrier);carrier.setText("原生运营商");control.attach(carrier);
        Bundle settings=new Bundle();settings.putBoolean("clock_controls_enabled",true);
        settings.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION,"enabled"),true);
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED,true);settings.putString(StatusBarSettings.CLOCK_PATTERN,"'STATUS' HH:mm");
        settings.putFloat(StatusBarSettings.CLOCK_SCALE,150f);settings.putFloat(StatusBarSettings.CLOCK_WEIGHT,712f);
        settings.putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,true);settings.putString(StatusBarSettings.SHADE_CLOCK_PATTERN,"'SHADE' HH:mm:ss");
        settings.putFloat(StatusBarSettings.SHADE_CLOCK_SCALE,200f);settings.putFloat(StatusBarSettings.SHADE_CLOCK_WEIGHT,800f);
        settings.putFloat(StatusBarSettings.SHADE_CLOCK_SPACING,-.5f);settings.putFloat(StatusBarSettings.SHADE_CLOCK_OFFSET_X,-3f);settings.putFloat(StatusBarSettings.SHADE_CLOCK_OFFSET_Y,2f);
        settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION,"mode"),"text");settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION,"text"),"独立运营商文字");
        Map<String,Integer> palette=new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);palette.put("clock_color_dark",0xff123456);palette.put("shade_clock_color_dark",0x80112233);
        Map<String,Boolean> alpha=new HashMap<>();alpha.put("shade_clock_color_dark",true);
        // Stored independent values are dormant until explicitly enabled; old configurations keep following status.
        control.configure(settings,palette,alpha);
        for(TextView shade:shades) { equal("clock",control.group(shade));equal(status.getText(),shade.getText());near(19.5f,shade.getTextSize());equal(712,shade.getTypeface().weight);equal(0xcc123456,shade.getCurrentTextColor()); }
        equal(true,handler.delayed!=null&&handler.delay<=60000);
        settings.putBoolean(StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED,true);control.configure(settings,palette,alpha);
        equal("clock",control.group(status));near(19.5f,status.getTextSize());equal(true,status.getText().toString().startsWith("STATUS "));
        for(TextView shade:shades) {
            equal("shade_clock",control.group(shade));equal(notification.getText(),shade.getText());equal(true,shade.getText().toString().matches("SHADE \\d{2}:\\d{2}:\\d{2}"));
            near(26f,shade.getTextSize());equal(800,shade.getTypeface().weight);near(-2f/26f,shade.getLetterSpacing());equal(0x80112233,shade.getCurrentTextColor());
            android.graphics.Canvas canvas=new android.graphics.Canvas();control.beforeDraw(shade,canvas);near(-12f,canvas.translateX);near(8f,canvas.translateY);
        }
        equal("独立运营商文字",carrier.getText());equal(true,handler.delayed!=null&&handler.delay<=1000);
        // Disabling the status group must not bypass independent shade width/style restoration.
        settings.putBoolean("clock_controls_enabled",false);control.configure(settings,palette,alpha);
        equal("原生状态栏",status.getText());near(13f,status.getTextSize());equal(false,control.clockControlsEnabled(status));
        for(TextView shade:shades) { equal(true,control.clockControlsEnabled(shade));shade.setTextSize(0,13f);near(120f,control.restoreClockWidth(shade,60f,13f));near(26f,shade.getTextSize());equal(true,control.replaces(shade)); }
        near(60f,control.restoreClockWidth(status,60f,13f));equal(true,handler.delayed!=null&&handler.delay<=1000);
        for(TextView shade:shades)shade.shown=false;control.visibilityChanged();equal(true,handler.delayed==null);
        notification.shown=true;control.visibilityChanged();equal(true,handler.delayed!=null&&handler.delay<=1000);
        control.setInteractive(false);equal(true,handler.delayed==null);control.setInteractive(true);equal(true,handler.delayed!=null&&handler.delay<=1000);
        // Every effect can be turned off independently while the shade owns its native text baseline.
        for(String suffix:new String[]{"position","size","color","text_style"})settings.putBoolean("shade_clock_"+suffix+"_enabled",false);
        control.configure(settings,palette,alpha);
        for(TextView shade:shades) { near(13f,shade.getTextSize());equal(0xccffffff,shade.getCurrentTextColor());near(0f,shade.getLetterSpacing());android.graphics.Canvas canvas=new android.graphics.Canvas();control.beforeDraw(shade,canvas);near(0f,canvas.translateX);near(0f,canvas.translateY); }
        settings.putBoolean(StatusBarSettings.SHADE_CLOCK_ENABLED,false);control.configure(settings,palette,alpha);
        for(TextView shade:shades) { equal(false,control.replaces(shade));equal("原生下拉时钟",shade.getText());equal("原生下拉描述",shade.getContentDescription()); }
        equal(true,handler.delayed==null);
        // Returning to follow mode reads the current status configuration, without copying/replacing any saved keys.
        settings.putBoolean(StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED,false);settings.putBoolean("clock_controls_enabled",true);
        settings.putString(StatusBarSettings.CLOCK_PATTERN,"'FOLLOW' HH:mm");control.configure(settings,palette,alpha);
        for(TextView shade:shades) { equal("clock",control.group(shade));equal(status.getText(),shade.getText());near(19.5f,shade.getTextSize());equal(0xcc123456,shade.getCurrentTextColor()); }
        equal("'SHADE' HH:mm:ss",settings.getString(StatusBarSettings.SHADE_CLOCK_PATTERN));
        equal("独立运营商文字",carrier.getText());equal(true,handler.delayed!=null&&handler.delay<=60000);
    }
}
