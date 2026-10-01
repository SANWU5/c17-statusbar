package dev.puitheme;

import android.content.SharedPreferences;
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
        equal(600f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.WEIGHT));equal(300f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.COMPACT_WEIGHT));equal(0f,NotificationBigClockSettings.NUMBERS.get(NotificationBigClockSettings.OFFSET_Y));
        equal("HH:mm",NotificationBigClockSettings.STRINGS.get(NotificationBigClockSettings.PATTERN));equal("M月d日{周} {干支}{农历日期}",NotificationBigClockSettings.STRINGS.get(NotificationBigClockSettings.DATE_PATTERN));
        equal(0xffffffff,NotificationBigClockSettings.COLORS.get(NotificationBigClockSettings.COLOR_LIGHT));equal(0xffffffff,NotificationBigClockSettings.COLORS.get(NotificationBigClockSettings.COLOR_DARK));
        for(Map.Entry<String,Boolean> value:NotificationBigClockSettings.BOOLEANS.entrySet()){equal(value.getValue(),StatusBarSettings.BOOLEAN_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.BOOLEAN,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,Float> value:NotificationBigClockSettings.NUMBERS.entrySet()){equal(value.getValue(),StatusBarSettings.NUMERIC_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.NUMBER,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,Integer> value:NotificationBigClockSettings.COLORS.entrySet()){equal(value.getValue(),StatusBarSettings.COLOR_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.COLOR,ConfigTransfer.types().get(value.getKey()));}
        for(Map.Entry<String,String> value:NotificationBigClockSettings.STRINGS.entrySet()){equal(value.getValue(),StatusBarSettings.STRING_DEFAULTS.get(value.getKey()));equal(ConfigTransfer.Type.STRING,ConfigTransfer.types().get(value.getKey()));}
        for(String group:new String[]{"shade_clock","clock",NotificationBigClockSettings.NATIVE_CLOCK,CarrierPanels.NOTIFICATION}) {
            equal(true,NotificationBigClockSettings.suppressed(group,true,true,true));equal(false,NotificationBigClockSettings.suppressed(group,false,true,true));
            equal(false,NotificationBigClockSettings.suppressed(group,true,false,true));equal(false,NotificationBigClockSettings.suppressed(group,true,true,false));
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
                "\"notification_big_clock_weight\":99","\"notification_big_clock_compact_weight\":901","\"notification_big_clock_compact_scale\":-1",
                "\"notification_big_clock_enabled\":\"true\""})invalid(setting);
        ActivationGuardPreferencesCheck.MemoryPreferences raw=new ActivationGuardPreferencesCheck.MemoryPreferences();raw.values.putAll(stored);AtomicBoolean active=new AtomicBoolean(true);
        SharedPreferences guarded=new ActivationGuardPreferences(raw,active::get,()->{});
        for(String settings:new String[]{"\"notification_big_clock_enabled\":true,\"shade_clock_scale\":200,\"carrier_notification_text\":\"横屏保留\"",
                "\"carrier_notification_text\":\"横屏保留\",\"shade_clock_scale\":200,\"notification_big_clock_enabled\":true"}) {
            equal(true,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document(settings),true)));equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal(200f,raw.values.get(StatusBarSettings.SHADE_CLOCK_SCALE));equal("横屏保留",raw.values.get("carrier_notification_text"));
            equal(false,NotificationBigClockSettings.suppressed("shade_clock",false,true,(Boolean)raw.values.get(NotificationBigClockSettings.MASTER)));
        }
        active.set(false);int writes=raw.writes;equal(false,ConfigTransfer.commit(guarded,ConfigTransfer.prepare(document("\"carrier_notification_text\":\"禁止修改\""),true)));equal(writes,raw.writes);equal(true,raw.values.get(NotificationBigClockSettings.MASTER));equal("横屏保留",raw.values.get("carrier_notification_text"));
        System.out.println("Notification big-clock settings checks passed: "+checks);
    }
}
