package dev.puitheme;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Synthetic native weighted layout, final glyph geometry, and exact LP ownership. */
public final class DataBatterySpacingCheck {
    private static int checks;
    private static void equal(Object expected,Object actual) {
        checks++; if(!Objects.equals(expected,actual))throw new AssertionError("Data boundary "+checks+": "+expected+" != "+actual);
    }
    private static final class Parent extends ViewGroup {
        int childReads;
        Parent(){super(null);}
        @Override public View getChildAt(int index){childReads++;return super.getChildAt(index);}
    }
    private static final class Battery extends View {
        int writes,direction;
        Battery(){super(null);ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(32,20);p.leftMargin=3;p.rightMargin=7;p.topMargin=9;p.bottomMargin=11;setLayoutParams(p);writes=0;layoutRequests=0;}
        @Override public void setLayoutParams(ViewGroup.LayoutParams p){writes++;super.setLayoutParams(p);}
        @Override public int getLayoutDirection(){return direction;}
        ViewGroup.MarginLayoutParams margins(){return (ViewGroup.MarginLayoutParams)getLayoutParams();}
    }
    private static final class Container extends ViewGroup {
        Container(){super(null);getResources().getDisplayMetrics().density=2f;}
        @Override public int getPaddingStart(){return 9;}
        @Override public int getPaddingEnd(){return 12;}
    }
    private static Bundle settings(float dp){Bundle b=new Bundle();b.putBoolean("data_enabled",true);b.putBoolean("data_badge_hidden",false);b.putBoolean(DataBatterySpacing.ENABLED,true);b.putFloat(DataBatterySpacing.SPACING,dp);return b;}
    private static final class Fixture {
        final DataBatterySpacing helper=new DataBatterySpacing(new Handler(Looper.getMainLooper()));
        final Parent parent=new Parent();final Container container=new Container();final Battery battery=new Battery();
        final View mobile=new View(null),other=new View(null),wifi=new View(null);final Object owner=new Object(),info=new Object();
        Fixture(){parent.addView(container);parent.addView(battery);container.addView(other);container.addView(mobile);helper.mobileBound(mobile,"stacked_mobile");helper.mobileBound(wifi,"wifi");equal(container,helper.batteryAttached(battery,Container.class));helper.measured(container,mobile);}
        void absent(){helper.radioChanged(false,false,false,true,true);helper.labelChanged(owner,info,"");}
        void enabled(float dp){helper.configure(settings(dp));}
        boolean rtl(){return battery.direction==View.LAYOUT_DIRECTION_RTL;}
        int margin(){ViewGroup.MarginLayoutParams p=battery.margins();return p.isMarginRelative()?p.getMarginStart():rtl()?p.rightMargin:p.leftMargin;}
        void direction(int value){battery.direction=value;helper.measured(container,mobile);}
        void nativeMargin(int value,boolean copied){ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(battery.margins());if(!copied){if(p.isMarginRelative())p.setMarginStart(value);else if(rtl())p.rightMargin=value;else p.leftMargin=value;}helper.requestedLayout(battery,p);battery.setLayoutParams(p);}
        /** Original onMeasure sums padding and icon widths. The XML's weight=1,
         * width=0 icon container receives the remaining space in LinearLayout's
         * second pass. Native updateStates starts at width-paddingEnd. */
        int[] render(){int iconWidth=24+2+26+container.getPaddingStart()+container.getPaddingEnd();int lead=margin();ViewGroup.MarginLayoutParams p=battery.margins();int trail=p.isMarginRelative()?p.getMarginEnd():rtl()?p.leftMargin:p.rightMargin;int total=iconWidth+lead+32+trail;int left=300-total;int weightedWidth=total-(lead+32+trail);int mobileEnd=left+weightedWidth-container.getPaddingEnd();int mobileStart=mobileEnd-26,otherEnd=mobileStart-2,batteryStart=left+weightedWidth+lead;return rtl()?new int[]{300-batteryStart,300-mobileEnd,300-otherEnd,300-mobileStart}:new int[]{batteryStart,mobileEnd,otherEnd,mobileStart};}
        int gap(){int[] p=render();return rtl()?p[1]-p[0]:p[0]-p[1];}
        int otherGap(){int[] p=render();return Math.abs(p[3]-p[2]);}
    }
    public static void main(String[] args)throws Exception {
        Fixture f=new Fixture();f.absent();f.helper.configure(new Bundle());equal(3,f.margin());equal(15,f.gap());equal(0,f.battery.writes);
        equal(false,StatusBarSettings.bool(Collections.emptyMap(),DataBatterySpacing.ENABLED));equal(0f,StatusBarSettings.NUMERIC_DEFAULTS.get(DataBatterySpacing.SPACING));
        SettingsCatalog.Item item=SettingsCatalog.item(DataBatterySpacing.SPACING);equal("data",item.groupId);equal("dp",item.unit);equal(true,NumericPolicy.signed(item.key));equal(-100.125f,SettingsCatalog.customNumber(item,"-100.125"));equal(true,SettingEditor.forItem(item,Collections.emptyMap()).signed);equal(null,SettingsCatalog.validationError(item,1000000f));
        java.util.Map<String,Object> exported=new java.util.HashMap<>();exported.put(DataBatterySpacing.ENABLED,true);exported.put(DataBatterySpacing.SPACING,-1.125f);java.util.Map<String,Object> imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(exported),true).values();equal(true,imported.get(DataBatterySpacing.ENABLED));equal(-1.125f,imported.get(DataBatterySpacing.SPACING));
        // A write-only padding fixture would miss whether real screen-space gap changes.
        int[] before=f.render();f.enabled(8.24f);equal(19,f.margin());equal(31,f.gap());equal(before[0],f.render()[0]);equal(2,f.otherGap());equal(12,f.container.getPaddingEnd());equal(false,f.battery.margins().isMarginRelative());equal(7,f.battery.margins().rightMargin);equal(9,f.battery.margins().topMargin);equal(11,f.battery.margins().bottomMargin);equal(32,f.battery.margins().width);equal(0f,f.battery.getTranslationX());equal(0f,f.mobile.getTranslationX());equal(0,f.mobile.layoutRequests);equal(0,f.other.layoutRequests);
        int writes=f.battery.writes,layouts=f.battery.layoutRequests,reads=f.parent.childReads;
        for(int i=0;i<10000;i++){f.enabled(8.24f);f.absent();equal(true,f.helper.labelKnown(f.owner,f.info));}equal(writes,f.battery.writes);equal(layouts,f.battery.layoutRequests);equal(reads,f.parent.childReads);
        for(int i=0;i<1000;i++)f.helper.measured(f.container,f.mobile);equal(writes,f.battery.writes);equal(layouts,f.battery.layoutRequests);
        f.helper.radioChanged(true,false,false,true,true);equal(3,f.margin());f.helper.radioChanged(null,false,false,true,true);equal(3,f.margin());f.absent();equal(19,f.margin());f.helper.labelChanged(f.owner,new Object(),"5G");equal(3,f.margin());f.helper.labelChanged(f.owner,new Object(),null);equal(3,f.margin());f.helper.labelChanged(f.owner,null,"");equal(19,f.margin());
        // Native NetworkTypeText's info/type==null branch emits no node and returns
        // without a hydrated getter. Observe this branch using its actual VM identity.
        Fixture n=new Fixture();n.enabled(5);n.helper.radioChanged(false,false,false,true,true);equal(3,n.margin());n.helper.labelChanged(n.owner,null,"");equal(13,n.margin());equal(true,n.helper.labelKnown(n.owner,null));n.helper.labelChanged(n.owner,new Object(),null);equal(3,n.margin());n.helper.labelChanged(n.owner,new Object(),"");equal(13,n.margin());
        // Another bound Wi-Fi model cannot veto this native row's visible icon list.
        f.helper.radioChanged(true,false,false,true,true);f.helper.measuredNative(f.container,Arrays.asList(f.other,f.mobile),Battery.class,Container.class);equal(19,f.margin());f.helper.measuredNative(f.container,Arrays.asList(f.wifi,f.mobile),Battery.class,Container.class);equal(3,f.margin());f.helper.measuredNative(f.container,Arrays.asList(f.other,f.mobile),Battery.class,Container.class);equal(19,f.margin());View unreadable=new View(null);f.helper.unknownSlot(unreadable);f.helper.measuredNative(f.container,Arrays.asList(unreadable,f.mobile),Battery.class,Container.class);equal(3,f.margin());f.helper.measuredNative(f.container,Arrays.asList(f.other,f.mobile),Battery.class,Container.class);equal(19,f.margin());
        // Native and copied LP writes must preserve unrelated fields and not compound.
        f.nativeMargin(10,false);equal(26,f.margin());f.nativeMargin(0,true);equal(26,f.margin());f.enabled(0);equal(10,f.margin());f.enabled(-3);equal(4,f.margin());equal(16,f.gap());f.enabled(-1000000);equal(-65535,f.margin());f.enabled(Float.MAX_VALUE);equal(65535,f.margin());f.enabled(Float.NaN);equal(10,f.margin());f.enabled(5);equal(20,f.margin());
        f.direction(View.LAYOUT_DIRECTION_RTL);equal(17,f.margin());equal(29,f.gap());equal(10,f.battery.margins().leftMargin);equal(false,f.battery.margins().isMarginRelative());equal(2,f.otherGap());f.enabled(0);equal(7,f.margin());equal(19,f.gap());f.enabled(5);int rightBattery=f.render()[0];f.enabled(9);equal(rightBattery,f.render()[0]);equal(37,f.gap());f.direction(View.LAYOUT_DIRECTION_LTR);equal(28,f.margin());equal(7,f.battery.margins().rightMargin);
        Fixture r=new Fixture();ViewGroup.MarginLayoutParams rp=r.battery.margins();rp.setMarginStart(4);rp.setMarginEnd(8);r.absent();r.enabled(5);equal(14,r.margin());equal(26,r.gap());r.direction(View.LAYOUT_DIRECTION_RTL);equal(14,r.margin());equal(26,r.gap());r.enabled(0);equal(4,r.margin());equal(8,rp.getMarginEnd());equal(true,rp.isMarginRelative());
        View intervening=new View(null);f.parent.removeView(f.battery);f.parent.addView(intervening);f.parent.addView(f.battery);f.nativeMargin(0,true);equal(10,f.margin());equal(false,f.battery.margins().isMarginRelative());f.helper.measured(f.container,f.mobile);equal(10,f.margin());f.parent.removeView(intervening);f.helper.batteryAttached(f.battery,Container.class);f.helper.measured(f.container,f.mobile);equal(28,f.margin());
        f.helper.measured(f.container,f.other);equal(10,f.margin());f.helper.measured(f.container,f.mobile);equal(28,f.margin());f.helper.mobileBound(f.mobile,"wifi");equal(10,f.margin());f.helper.mobileBound(f.mobile,"mobile");f.helper.measured(f.container,f.mobile);equal(28,f.margin());f.battery.attached=false;f.helper.measured(f.container,f.mobile);equal(10,f.margin());f.battery.attached=true;f.helper.measured(f.container,f.mobile);equal(28,f.margin());f.helper.containerChanged(f.container);equal(10,f.margin());f.helper.measured(f.container,f.mobile);equal(28,f.margin());
        Bundle safe=settings(9);safe.putBoolean(StatusBarSettings.SAFE_MODE,true);f.helper.configure(safe);equal(10,f.margin());f.enabled(9);equal(28,f.margin());f.helper.configure(new Bundle());equal(10,f.margin());f.enabled(9);f.helper.batteryDetached(f.battery);equal(10,f.margin());f.helper.batteryAttached(f.battery,Container.class);f.helper.measuredNative(f.container,Arrays.asList(f.other,f.mobile),Battery.class,Container.class);equal(28,f.margin());f.helper.releaseRuntime();equal(10,f.margin());f.enabled(100);f.helper.requestedLayout(f.battery,f.battery.margins());equal(10,f.margin());
        wrapped();policy();nativeHidden();System.out.println(checks+" checks passed (native weighted screen gap, no-draw marker, exact margin ownership, RTL and changed-only updates)");
    }
    private static void nativeHidden(){
        Fixture f=new Fixture();f.absent();f.enabled(5);f.helper.labelChanged(f.owner,f.info,"5G");equal(3,f.margin());
        Bundle settings=settings(5);settings.putBoolean("data_badge_hidden",true);f.helper.configure(settings);equal(13,f.margin());
        // Preserve the native source: toggling back immediately restores its marker boundary.
        settings.putBoolean("data_badge_hidden",false);f.helper.configure(settings);equal(3,f.margin());
        settings.putBoolean("data_badge_hidden",true);settings.putBoolean("label_enabled",true);f.helper.configure(settings);equal(3,f.margin());
        settings.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);f.helper.configure(settings);equal(13,f.margin());
        f.helper.releaseRuntime();equal(3,f.margin());
    }
    private static void wrapped(){Fixture f=new Fixture();f.parent.removeView(f.container);f.parent.removeView(f.battery);Parent wrapper=new Parent(),inner=new Parent();wrapper.addView(inner);inner.addView(f.container);View trailing=new View(null);trailing.setVisibility(View.GONE);inner.addView(trailing);f.parent.addView(wrapper);f.parent.addView(f.battery);f.absent();f.enabled(5);f.helper.measured(f.container,f.mobile,Battery.class,Container.class);equal(13,f.margin());trailing.setVisibility(View.VISIBLE);f.helper.measured(f.container,f.mobile,Battery.class,Container.class);equal(3,f.margin());trailing.setVisibility(View.INVISIBLE);f.helper.measured(f.container,f.mobile,Battery.class,Container.class);equal(13,f.margin());Container second=new Container();inner.addView(second);f.helper.measured(f.container,f.mobile,Battery.class,Container.class);equal(3,f.margin());equal(null,f.helper.batteryAttached(f.battery,Container.class));inner.removeView(second);f.helper.measured(f.container,f.mobile,Battery.class,Container.class);equal(13,f.margin());f.helper.releaseRuntime();equal(3,f.margin());}
    private static void policy(){Fixture f=new Fixture();f.absent();Bundle custom=settings(5);custom.putBoolean("label_enabled",true);f.helper.configure(custom);f.helper.labelChanged(f.owner,f.info,"5G");equal(3,f.margin());f.helper.radioChanged(false,false,false,false,false);equal(13,f.margin());f.helper.dataChanged(true);equal(3,f.margin());f.helper.radioChanged(false,false,true,false,true);equal(13,f.margin());f.helper.radioChanged(false,false,false,false,true);equal(3,f.margin());custom.putBoolean(StatusBarSettings.LABEL_HIDDEN,true);f.helper.configure(custom);equal(13,f.margin());custom.putBoolean(StatusBarSettings.LABEL_HIDDEN,false);f.helper.configure(custom);equal(3,f.margin());f.helper.labelChanged(f.owner,f.info,"");equal(13,f.margin());f.helper.fallbackChanged("5G");equal(3,f.margin());f.helper.fallbackChanged("");equal(13,f.margin());custom.putBoolean("label_enabled",false);f.helper.configure(custom);equal(13,f.margin());f.helper.fallbackChanged("5G");equal(13,f.margin());equal(true,f.helper.labelKnown(f.owner,f.info));f.helper.releaseRuntime();equal(3,f.margin());}
}
