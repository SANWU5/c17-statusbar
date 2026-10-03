package dev.puitheme;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.oplus.systemui.statusbar.phone.netspeed.OplusNetworkSpeedControllerExImpl;
import com.oplus.systemui.statusbar.phone.netspeed.widget.NetworkSpeedView;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Native queue cadence, layout recovery, settings portability; no device or saved preferences. */
public final class NetworkSpeedControlsCheck {
    private static int checks;
    private static void require(boolean actual,String message){checks++;if(!actual)throw new AssertionError(message);}
    private static void equal(Object expected,Object actual){require(java.util.Objects.equals(expected,actual),"expected "+expected+", actual "+actual);}
    private static Bundle settings(String style,float seconds){
        Bundle values=new Bundle();values.putBoolean("speed_enabled",true);
        values.putBoolean(NetworkSpeedControls.INTERVAL_ENABLED,true);
        values.putFloat(NetworkSpeedControls.INTERVAL_SECONDS,seconds);
        values.putBoolean(NetworkSpeedControls.STYLE_ENABLED,true);values.putString(NetworkSpeedControls.DISPLAY_STYLE,style);
        return values;
    }
    private static Bundle millisSettings(float millis){
        Bundle values=settings("system",4f);values.putFloat(NetworkSpeedControls.INTERVAL_MILLIS,millis);return values;
    }
    private static void apply(NetworkSpeedControls controls,NetworkSpeedView view){controls.apply(view,view.mSpeedNumber,view.mSpeedUnit);}
    private static FrameLayout.LayoutParams params(TextView view){return (FrameLayout.LayoutParams)view.getLayoutParams();}
    private static String document(String values){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+values+"}}";}
    public static void main(String[] args) throws Exception {
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(NetworkSpeedControls.INTERVAL_ENABLED));
        equal(false,StatusBarSettings.BOOLEAN_DEFAULTS.get(NetworkSpeedControls.STYLE_ENABLED));
        equal(4f,StatusBarSettings.NUMERIC_DEFAULTS.get(NetworkSpeedControls.INTERVAL_SECONDS));
        equal(250f,StatusBarSettings.NUMERIC_DEFAULTS.get(NetworkSpeedControls.INTERVAL_MILLIS));
        equal("system",StatusBarSettings.STRING_DEFAULTS.get(NetworkSpeedControls.DISPLAY_STYLE));
        equal(null,SettingsCatalog.item(NetworkSpeedControls.INTERVAL_SECONDS));
        require(SettingsCatalog.hiddenKeys().contains(NetworkSpeedControls.INTERVAL_SECONDS),"legacy seconds leaked into editor");
        SettingsCatalog.Item interval=SettingsCatalog.item(NetworkSpeedControls.INTERVAL_MILLIS);
        equal("speed",interval.groupId);equal(1f,interval.min);equal(500f,interval.max);equal(1f,interval.step);equal("ms",interval.unit);
        for(float value:new float[]{1,2.5f,250,500})equal(false,SettingsCatalog.requiresNumericTrial(interval,value));
        for(float value:new float[]{.00125f,.5f,501,1000000})equal(true,SettingsCatalog.requiresNumericTrial(interval,value));
        for(float value:new float[]{.00125f,11.125f,501f,1000000f,Float.MAX_VALUE}){
            equal(value,SettingsCatalog.customNumber(interval,Float.toString(value)));
            equal(null,SettingsCatalog.validationError(interval,value));
        }
        for(String invalid:new String[]{"0","-1","NaN","Infinity","1e1000"}){
            checks++;try{SettingsCatalog.customNumber(interval,invalid);throw new AssertionError("invalid ms input "+invalid);}
            catch(IllegalArgumentException expected){}
        }
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            require(SettingsCatalog.validationError(interval,invalid)!=null,"invalid sampling interval accepted");
        require(SettingEditor.forItem(interval,new HashMap<>()).description.contains("范围外先试用20秒"),"missing numeric trial explanation");
        equal(null,SettingsCatalog.item(NetworkSpeedControls.DISPLAY_STYLE));
        equal(null,SettingsCatalog.item(NetworkSpeedControls.STYLE_ENABLED));
        require(SettingsCatalog.hiddenKeys().contains(NetworkSpeedControls.DISPLAY_STYLE),"removed layout choice leaked");
        equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(NetworkSpeedControls.DISPLAY_STYLE));
        for(String mode:new String[]{"system","stacked","inline","number"}){
            String json=document("\"speed_display_style\":\""+mode+"\",\"speed_refresh_enabled\":true,\"speed_refresh_seconds\":11.125,\"speed_style_enabled\":true");
            Map<String,Object> portable=ConfigTransfer.prepare(json,true).values();
            equal(mode,portable.get(NetworkSpeedControls.DISPLAY_STYLE));equal(11.125f,portable.get(NetworkSpeedControls.INTERVAL_SECONDS));
            equal(11125f,portable.get(NetworkSpeedControls.INTERVAL_MILLIS));
            equal(true,portable.get(NetworkSpeedControls.INTERVAL_ENABLED));equal(true,portable.get(NetworkSpeedControls.STYLE_ENABLED));
            Map<String,Object> savedRaw=new HashMap<>(portable);
            // Export is an effective snapshot: retired layout keys are canonical system/off.
            // Reading/exporting never edits an imported or persisted legacy raw map.
            Map<String,Object> effective=ConfigTransfer.prepare(ConfigTransfer.exportJson(portable),true).values();
            equal("system",effective.get(NetworkSpeedControls.DISPLAY_STYLE));
            equal(false,effective.get(NetworkSpeedControls.STYLE_ENABLED));equal(savedRaw,portable);
        }
        for(float millis:new float[]{.00125f,250f,1234.567f,Float.MAX_VALUE}){
            Map<String,Object> portable=ConfigTransfer.prepare(document("\"speed_refresh_millis\":"+millis),true).values();
            equal(millis,portable.get(NetworkSpeedControls.INTERVAL_MILLIS));
            equal(millis,ConfigTransfer.prepare(ConfigTransfer.exportJson(portable),true).values().get(NetworkSpeedControls.INTERVAL_MILLIS));
        }
        for(String invalid:new String[]{"0","-1","1e1000"}){
            checks++;try{ConfigTransfer.prepare(document("\"speed_refresh_millis\":"+invalid),true);throw new AssertionError("invalid imported ms "+invalid);}
            catch(IOException expected){}
        }
        checks++;try{ConfigTransfer.prepare(document("\"speed_display_style\":\"upload\""),true);throw new AssertionError("invalid style imported");}
        catch(IOException expected){}
        equal(4000L,NetworkSpeedControls.intervalMillis(null));equal(4000L,NetworkSpeedControls.intervalMillis(Float.NaN));
        equal(4000L,NetworkSpeedControls.intervalMillis(Float.POSITIVE_INFINITY));
        for(float small:new float[]{-1,0,Float.MIN_VALUE,.000125f})equal(1L,NetworkSpeedControls.intervalMillis(small));
        equal(100L,NetworkSpeedControls.intervalMillis(.1f));equal(490L,NetworkSpeedControls.intervalMillis(.49f));equal(500L,NetworkSpeedControls.intervalMillis(.5f));
        equal(11250L,NetworkSpeedControls.intervalMillis(11.25f));
        require(NetworkSpeedControls.intervalMillis(Float.MAX_VALUE)>0&&NetworkSpeedControls.intervalMillis(Float.MAX_VALUE)<=Long.MAX_VALUE/8,"timer overflow");

        equal(250L,NetworkSpeedControls.millisValue(null));equal(250L,NetworkSpeedControls.millisValue(Float.NaN));
        equal(250L,NetworkSpeedControls.millisValue(Float.POSITIVE_INFINITY));
        for(float small:new float[]{-1,0,Float.MIN_VALUE,.00125f,.49f,1f})equal(1L,NetworkSpeedControls.millisValue(small));
        equal(11L,NetworkSpeedControls.millisValue(11.125f));equal(11250L,NetworkSpeedControls.millisValue(11250f));
        require(NetworkSpeedControls.millisValue(Float.MAX_VALUE)>0&&NetworkSpeedControls.millisValue(Float.MAX_VALUE)<=Long.MAX_VALUE/8,"ms timer overflow");
        NetworkSpeedControls millisecond=new NetworkSpeedControls();
        Bundle quick=millisSettings(250f);millisecond.configure(quick);equal(250L,millisecond.samplingDelay(4000));
        // The new stored unit wins over the retained old seconds key; no duplicate timer exists.
        quick.putFloat(NetworkSpeedControls.INTERVAL_SECONDS,999f);millisecond.configure(quick);equal(250L,millisecond.samplingDelay(4000));
        OplusNetworkSpeedControllerExImpl fast=new OplusNetworkSpeedControllerExImpl(millisecond::samplingDelay);
        fast.clock=10000;fast.postUpdateNetworkSpeedDelay(0);fast.advanceTo(11000,4000);
        equal(5,fast.reads);equal(4,fast.updates);equal(4000L,fast.reportedSpeed);equal(11250L,fast.nextAt);
        quick.putFloat(NetworkSpeedControls.INTERVAL_MILLIS,1f);millisecond.configure(quick);
        fast.advanceTo(11260,4000);equal(16,fast.reads);equal(15,fast.updates);equal(4000L,fast.reportedSpeed);equal(11261L,fast.nextAt);
        quick.putBoolean(StatusBarSettings.SAFE_MODE,true);millisecond.configure(quick);equal(4000L,millisecond.samplingDelay(4000));
        quick.putBoolean(StatusBarSettings.SAFE_MODE,false);quick.putBoolean("speed_enabled",false);millisecond.configure(quick);equal(4000L,millisecond.samplingDelay(4000));

        NetworkSpeedControls controls=new NetworkSpeedControls();
        equal(4000L,controls.samplingDelay(4000));
        Bundle configured=settings("system",1);controls.configure(configured);
        equal(1000L,controls.samplingDelay(4000));equal(0L,controls.samplingDelay(0));equal(-1L,controls.samplingDelay(-1));
        // Source replay counts physical getTotalByte reads, not view refreshes.
        OplusNetworkSpeedControllerExImpl sampler=new OplusNetworkSpeedControllerExImpl(controls::samplingDelay);
        sampler.clock=10000;sampler.postUpdateNetworkSpeedDelay(0);sampler.advanceTo(20000,2048);
        equal(11,sampler.reads);equal(10,sampler.updates);equal(2048L,sampler.reportedSpeed);equal(21000L,sampler.nextAt);
        configured.putFloat(NetworkSpeedControls.INTERVAL_SECONDS,5);controls.configure(configured);
        sampler.advanceTo(31000,2048);equal(14,sampler.reads);equal(2048L,sampler.reportedSpeed);equal(36000L,sampler.nextAt);
        configured.putBoolean(NetworkSpeedControls.INTERVAL_ENABLED,false);controls.configure(configured);
        sampler.advanceTo(36000,2048);equal(40000L,sampler.nextAt);
        sampler.isPause=true;sampler.advanceTo(40000,2048);equal(-1L,sampler.nextAt);equal(15,sampler.reads);
        configured.putBoolean(NetworkSpeedControls.INTERVAL_ENABLED,true);controls.configure(configured);
        equal(-1L,sampler.nextAt); // Configuring cannot revive native screen-off/disconnected samplers.
        sampler.isPause=false;sampler.postUpdateNetworkSpeedDelay(0);equal(40000L,sampler.nextAt);
        sampler.advanceTo(40000,2048);equal(45000L,sampler.nextAt);
        configured.putBoolean(StatusBarSettings.SAFE_MODE,true);controls.configure(configured);equal(4000L,controls.samplingDelay(4000));
        configured.putBoolean(StatusBarSettings.SAFE_MODE,false);configured.putBoolean("speed_enabled",false);controls.configure(configured);
        equal(4000L,controls.samplingDelay(4000));

        NetworkSpeedView view=new NetworkSpeedView();
        FrameLayout.LayoutParams originalNumber=new FrameLayout.LayoutParams(params(view.mSpeedNumber));
        FrameLayout.LayoutParams originalUnit=new FrameLayout.LayoutParams(params(view.mSpeedUnit));
        Object nativeNumberParams=params(view.mSpeedNumber),nativeUnitParams=params(view.mSpeedUnit);
        int originalWidth=view.getLayoutParams().width;
        view.mSpeedNumber.setTextColor(0x12345678);view.mSpeedUnit.setTextColor(0x87654321);
        view.mSpeedNumber.setTranslationY(-3);view.mSpeedUnit.setTranslationY(4);
        Object numberFace=view.mSpeedNumber.getTypeface(),unitFace=view.mSpeedUnit.getTypeface();
        float numberSize=view.mSpeedNumber.getTextSize(),unitSize=view.mSpeedUnit.getTextSize();
        // All deprecated saved modes must leave the genuine OEM layout intact.
        for(String mode:new String[]{"system","inline","stacked","number"}) {
            configured=settings(mode,1);controls.configure(configured);apply(controls,view);view.nativeLayout();
            equal(originalWidth,view.getLayoutParams().width);
            require(nativeNumberParams==params(view.mSpeedNumber)&&nativeUnitParams==params(view.mSpeedUnit),"deprecated mode replaced native LayoutParams");
            same(originalNumber,params(view.mSpeedNumber));same(originalUnit,params(view.mSpeedUnit));
            equal(View.VISIBLE,view.mSpeedUnit.getVisibility());
            equal(0x12345678,view.mSpeedNumber.getCurrentTextColor());equal(0x87654321,view.mSpeedUnit.getCurrentTextColor());
            equal(numberFace,view.mSpeedNumber.getTypeface());equal(unitFace,view.mSpeedUnit.getTypeface());
            equal(numberSize,view.mSpeedNumber.getTextSize());equal(unitSize,view.mSpeedUnit.getTextSize());
            equal(-3f,view.mSpeedNumber.getTranslationY());equal(4f,view.mSpeedUnit.getTranslationY());
            equal("1.0",view.mSpeedNumber.getText().toString());equal("KB/S",view.mSpeedUnit.getText().toString());
            Map<String,Object> legacy=new HashMap<>();legacy.put(NetworkSpeedControls.STYLE_ENABLED,true);legacy.put(NetworkSpeedControls.DISPLAY_STYLE,mode);
            Map<String,Object> retained=new HashMap<>(legacy);
            equal(false,StatusBarSettings.bool(legacy,NetworkSpeedControls.STYLE_ENABLED));
            equal("system",StatusBarSettings.string(legacy,NetworkSpeedControls.DISPLAY_STYLE));
            equal(retained,legacy);
        }
        for(int frame=0;frame<1000;frame++){
            apply(controls,view);
            require(nativeNumberParams==params(view.mSpeedNumber)&&nativeUnitParams==params(view.mSpeedUnit),"repeated native draws replaced LayoutParams");
            equal(originalWidth,view.getLayoutParams().width);
        }
        controls.beforeNativeLayout(view);controls.beforeNativeLayout(view);view.applyNativeConfiguration(32,14,16);
        controls.afterNativeLayout(view,view.mSpeedNumber,view.mSpeedUnit);controls.afterNativeLayout(view,view.mSpeedNumber,view.mSpeedUnit);
        equal(32,view.getWidth());equal(14,params(view.mSpeedNumber).bottomMargin);equal(16,params(view.mSpeedUnit).topMargin);
        view.mSpeedUnit.setVisibility(View.INVISIBLE);apply(controls,view);equal(View.INVISIBLE,view.mSpeedUnit.getVisibility());
        configured.putBoolean(StatusBarSettings.SAFE_MODE,true);controls.configure(configured);equal(View.INVISIBLE,view.mSpeedUnit.getVisibility());
        controls.onViewDetached(view);equal(32,view.getWidth());
        NetworkSpeedView interrupted=new NetworkSpeedView();controls.configure(settings("inline",1));apply(controls,interrupted);
        controls.beforeNativeLayout(interrupted);controls.afterNativeLayout(interrupted,null,null);apply(controls,interrupted);
        equal(24,interrupted.getWidth());
        controls.apply(null,null,null);controls.beforeNativeLayout(null);controls.afterNativeLayout(null,null,null);controls.onViewDetached(null);
        System.out.println("NetworkSpeedControlsCheck: "+checks+" checks passed");
    }
    private static void same(FrameLayout.LayoutParams expected,FrameLayout.LayoutParams actual){
        equal(expected.width,actual.width);equal(expected.height,actual.height);equal(expected.gravity,actual.gravity);
        equal(expected.leftMargin,actual.leftMargin);equal(expected.rightMargin,actual.rightMargin);
        equal(expected.topMargin,actual.topMargin);equal(expected.bottomMargin,actual.bottomMargin);
        equal(expected.isMarginRelative(),actual.isMarginRelative());equal(expected.getMarginStart(),actual.getMarginStart());equal(expected.getMarginEnd(),actual.getMarginEnd());
    }
}
