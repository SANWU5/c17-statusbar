package dev.puitheme;

import android.content.SharedPreferences;
import android.os.Bundle;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Preserve original horizontal/header preferences while the portrait replacement owns its space. */
public final class NotificationBigClockSettingsCheck {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("Big clock check "+checks+": "+expected+" != "+actual);}
    private static String document(String settings){return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"+settings+"}}";}
    private static void invalid(String settings) throws IOException {
        checks++;try{ConfigTransfer.prepare(document(settings),true);}catch(IOException expected){return;}throw new AssertionError("Invalid big-clock configuration accepted");
    }
    public static void main(String[] args) throws Exception {
        equal(false,NotificationBigClockSettings.BOOLEANS.get(NotificationBigClockSettings.MASTER));equal(true,NotificationBigClockSettings.BOOLEANS.get(NotificationBigClockSettings.GLASS));
        equal(100f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.SCALE));equal(36f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.COMPACT_SCALE));
        equal(1f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.VISIBLE_COUNT));
        equal(600f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.WEIGHT));equal(300f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.COMPACT_WEIGHT));equal(0f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.OFFSET_Y));
        equal("HH:mm",NotificationBigClockSettings.STRINGS.get(NotificationBigClockSettings.PATTERN));equal("M月d日{周} {干支}{农历日期}",NotificationBigClockSettings.STRINGS.get(NotificationBigClockSettings.DATE_PATTERN));
        equal(0xffffffff,NotificationBigClockSettings.COLORS.get(NotificationBigClockSettings.COLOR_LIGHT));equal(0xffffffff,NotificationBigClockSettings.COLORS.get(NotificationBigClockSettings.COLOR_DARK));
        for(Map.Entry<String,Boolean> value:NotificationBigClockSettings.BOOLEANS.entrySet()){equal(value.getValue(),StatusBarSettings.BOOLEAN_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,Float> value:NotificationBigClockSettings.NUMBERS.entrySet()){equal(value.getValue(),StatusBarSettings.NUMERIC_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,Integer> value:NotificationBigClockSettings.COLORS.entrySet()){equal(value.getValue(),StatusBarSettings.COLOR_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.COLOR,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,String> value:NotificationBigClockSettings.STRINGS.entrySet()){equal(value.getValue(),StatusBarSettings.STRING_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(value.getKey()));}
        for(String group:new String[]{"shade_clock","clock",NotificationBigClockSettings.NATIVE_CLOCK,CarrierPanels.NOTIFICATION}) {
            equal(true,NotificationBigClockSettings.suppressed(group,true,true,true));equal(true,NotificationBigClockSettings.suppressed(group,false,true,true));
            equal(false,NotificationBigClockSettings.suppressed(group,true,false,true));equal(false,NotificationBigClockSettings.suppressed(group,true,true,false));
            equal(false,NotificationBigClockSettings.suppressed(group,false,false,true));equal(false,NotificationBigClockSettings.suppressed(group,false,true,false));
        }
        for(String group:new String[]{CarrierPanels.CONTROL,CarrierPanels.LOCKSCREEN,"carrier","wifi","notification_big_clock",null})equal(false,NotificationBigClockSettings.suppressed(group,true,true,true));
        for(String key:new String[]{StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED,StatusBarSettings.SHADE_CLOCK_PATTERN,StatusBarSettings.SHADE_CLOCK_SCALE,"carrier_notification_enabled","carrier_notification_mode"}) {
            equal(true,NotificationBigClockSettings.conflictsWith(key,true));equal(true,NotificationBigClockSettings.conflictsWith(key,0f));equal(false,NotificationBigClockSettings.conflictsWith(key,false));equal(false,NotificationBigClockSettings.conflictsWith(key,null));
        }
        for(String key:new String[]{StatusBarSettings.CLOCK_PATTERN,"carrier_pattern","carrier_control_scale","carrier_lockscreen_enabled",NotificationBigClockSettings.MASTER,"shade_clockwise_enabled",null})equal(false,NotificationBigClockSettings.conflictsWith(key,true));
        Map<String,Object> stored=new LinkedHashMap<>();stored.put(NotificationBigClockSettings.MASTER,true);stored.put(NotificationBigClockSettings.PATTERN,"HH:mm:ss");stored.put(NotificationBigClockSettings.DATE_PATTERN,"M月d日 {星期}");
        stored.put(StatusBarSettings.SHADE_CLOCK_CONTROLS_ENABLED,true);stored.put(StatusBarSettings.SHADE_CLOCK_SCALE,165f);stored.put("carrier_notification_text","保留原运营商文字");
        Map<String,Object> imported=ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true).values();for(Map.Entry<String,Object> value:stored.entrySet())equal(value.getValue(),imported.get(value.getKey()));
        for(String setting:new String[]{"\"notification_big_clock_pattern\":\"{未知}\"","\"notification_big_clock_date_pattern\":\"'未配对\"",
                "\"notification_big_clock_date_pattern\":\"HH:mm\\n\"","\"notification_big_clock_date_pattern\":\"\"","\"notification_big_clock_pattern\":true",
                "\"notification_big_clock_enabled\":\"true\""})invalid(setting);
        equal(99f,ConfigTransfer.prepare(document("\"notification_big_clock_weight\":99"),true).values().get(NotificationBigClockSettings.WEIGHT));
        equal(901f,ConfigTransfer.prepare(document("\"notification_big_clock_compact_weight\":901"),true).values().get(NotificationBigClockSettings.COMPACT_WEIGHT));
        equal(-1f,ConfigTransfer.prepare(document("\"notification_big_clock_compact_scale\":-1"),true).values().get(NotificationBigClockSettings.COMPACT_SCALE));
        equal(false,NotificationBigClockSettings.BOOLEANS.get(NotificationBigClockSettings.STACK_ENABLED));
        equal(false,StatusBarSettings.bool(java.util.Collections.emptyMap(),NotificationBigClockSettings.STACK_ENABLED));
        equal(true,StatusBarSettings.bool(java.util.Collections.singletonMap(NotificationBigClockSettings.STACK_ENABLED,true),NotificationBigClockSettings.STACK_ENABLED));
        equal(true,ConfigTransfer.prepare(document("\"notification_big_clock_stack_enabled\":true"),true).values().get(NotificationBigClockSettings.STACK_ENABLED));
        equal(false,ConfigTransfer.prepare(document("\"notification_big_clock_stack_enabled\":false"),true).values().get(NotificationBigClockSettings.STACK_ENABLED));
        for(String value:new String[]{"\"true\"","1","null","[]"})invalid("\"notification_big_clock_stack_enabled\":"+value);
        stored.put(NotificationBigClockSettings.STACK_ENABLED,true);
        Bundle saved=SettingsSnapshot.fromPreferences(stored);
        equal(true,SettingsSnapshot.complete(saved));
        equal(true,saved.get(NotificationBigClockSettings.STACK_ENABLED));
        equal(true,ConfigTransfer.prepare(ConfigTransfer.exportJson(stored),true).values().get(NotificationBigClockSettings.STACK_ENABLED));
        stackingRuntimeGate(saved);
        ActivationGuardPreferencesCheck.MemoryPreferences raw=new ActivationGuardPreferencesCheck.MemoryPreferences();raw.values.putAll(stored);AtomicBoolean active=new AtomicBoolean(true);
        SharedPreferences guarded=new ActivationGuardPreferences(raw,active::get,()->{});
        equal(true,guarded.edit().putBoolean(NotificationBigClockSettings.STACK_ENABLED,true).commit());
        equal(true,guarded.getBoolean(NotificationBigClockSettings.STACK_ENABLED,false));
        equal(true,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document("\"notification_big_clock_stack_enabled\":false"),true)));
        equal(false,raw.values.get(NotificationBigClockSettings.STACK_ENABLED));
        equal(true,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document("\"notification_big_clock_stack_enabled\":true"),true)));
        equal(true,raw.values.get(NotificationBigClockSettings.STACK_ENABLED));
        for(String settings:new String[]{"\"notification_big_clock_enabled\":true,\"shade_clock_scale\":200,\"carrier_notification_text\":\"横屏保留\"",
                "\"carrier_notification_text\":\"横屏保留\",\"shade_clock_scale\":200,\"notification_big_clock_enabled\":true"}) {
            equal(true,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document(settings),true)));equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(200f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));equal("横屏保留",raw.values.get("carrier_notification_text"));
            equal(true,NotificationBigClockSettings.suppressed("shade_clock",false,true,(Boolean)raw.values.get(NotificationBigClockSettings.MASTER)));
        }
        active.set(false);int writes=raw.writes;equal(false,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document("\"carrier_notification_text\":\"禁止修改\""),true)));equal(writes,raw.writes);equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal("横屏保留",raw.values.get("carrier_notification_text"));
        completeCardCount();
        System.out.println("Notification big-clock settings checks passed: "+checks);
    }
    private static void completeCardCount() throws Exception {
        for(int count:new int[]{1,2,3,4,5,6,1000000,Integer.MAX_VALUE})
            equal(count,NotificationBigClockSettings.visibleCount(count));
        equal(Integer.MAX_VALUE,NotificationBigClockSettings.visibleCount((float)Integer.MAX_VALUE));
        equal(Integer.MAX_VALUE,NotificationBigClockSettings.visibleCount(Float.MAX_VALUE));
        equal(Integer.MAX_VALUE,NotificationBigClockSettings.visibleCount(Long.MAX_VALUE));
        for(Object invalid:new Object[]{null,"6",Float.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            equal(1,NotificationBigClockSettings.visibleCount(invalid));
        for(Number invalid:new Number[]{0,-1,-1000000,-Float.MAX_VALUE})equal(1,NotificationBigClockSettings.visibleCount(invalid));
        NotificationNativeStack stack=new NotificationNativeStack();
        java.lang.reflect.Field visible=NotificationNativeStack.class.getDeclaredField("completeCount");visible.setAccessible(true);
        for(float count:new float[]{3f,4f,5f,6f,1000000f,(float)Integer.MAX_VALUE}) {
            Bundle source=new Bundle();source.putBoolean(NotificationBigClockSettings.MASTER,true);
            source.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);source.putFloat(NotificationBigClockSettings.VISIBLE_COUNT,count);
            stack.configure(source);
            equal(NotificationBigClockSettings.visibleCount(count),visible.get(stack));
            equal(count,source.get(NotificationBigClockSettings.VISIBLE_COUNT));
        }
    }
    /** Saved requests stay intact, but only an enabled direction's clock can permit runtime stacking. */
    private static void stackingRuntimeGate(Bundle saved) throws Exception {
        NotificationNativeStack stack=new NotificationNativeStack();
        java.lang.reflect.Field enabled=NotificationNativeStack.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        equal(false,enabled.get(stack));
        for(boolean portrait:new boolean[]{false,true})for(boolean landscape:new boolean[]{false,true})
                for(boolean landscapeStack:new boolean[]{false,true})for(boolean requested:new boolean[]{false,true})
                for(boolean safe:new boolean[]{false,true}){
            Bundle source=new Bundle(saved);
            source.putBoolean(NotificationBigClockSettings.MASTER,portrait);
            source.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,landscape);
            source.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,landscapeStack);
            source.putBoolean(NotificationBigClockSettings.STACK_ENABLED,requested);
            source.putBoolean(StatusBarSettings.SAFE_MODE,safe);
            stack.configure(source);
            equal(requested&&!safe&&(portrait||landscape&&landscapeStack),enabled.get(stack));
            equal(requested,source.get(NotificationBigClockSettings.STACK_ENABLED));
            equal(portrait,source.get(NotificationBigClockSettings.MASTER));
            equal(landscape,source.get(NotificationBigClockSettings.LANDSCAPE_MASTER));
            equal(landscapeStack,source.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        }
        Bundle legacy=new Bundle();legacy.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true);
        legacy.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        stack.configure(legacy);equal(false,enabled.get(stack));
        equal(true,legacy.get(NotificationBigClockSettings.STACK_ENABLED));
        legacy.putBoolean(NotificationBigClockSettings.MASTER,true);
        stack.configure(legacy);equal(true,enabled.get(stack));
        legacy.putBoolean(NotificationBigClockSettings.MASTER,false);
        stack.configure(legacy);equal(false,enabled.get(stack));
        legacy.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        stack.configure(legacy);equal(true,enabled.get(stack));
        legacy.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,false);
        stack.configure(legacy);equal(false,enabled.get(stack));
        saved.putBoolean(StatusBarSettings.SAFE_MODE,true);
        Bundle runtime=SafetyMode.runtimeSettings(saved);
        equal(true,saved.get(NotificationBigClockSettings.STACK_ENABLED));
        equal(true,runtime.get(NotificationBigClockSettings.STACK_ENABLED));
        stack.configure(runtime);equal(false,enabled.get(stack));
        saved.putBoolean(StatusBarSettings.SAFE_MODE,false);
        stack.configure(SafetyMode.runtimeSettings(saved));equal(true,enabled.get(stack));
        equal(false,stack.enabled()); // A saved request alone cannot activate uninstalled native hooks.
        stack.configure(null);equal(false,enabled.get(stack));
    }
}
