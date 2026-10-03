package dev.puitheme;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

/** Real regressions: stale imports, safe-mode restoration and unproven Root outputs. */
public final class SafeConfigCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static String document(String value) {
        return "{\"package\":\""+ConfigTransfer.PACKAGE_NAME+"\",\"schema\":1,\"settings\":{"
                + "\"data_activity_hidden\":"+value+",\"clock_scale\":137.25}}";
    }
    public static void main(String[] args) throws Exception {
        for (String stale : new String[]{"false", "true", "0", "-42", "\"false\"", "null", "[]", "{\"old\":[false,null]}"}) {
            Map<String,Object> imported=ConfigTransfer.prepare(document(stale),true).values();
            equal(true,imported.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
            equal(137.25f,imported.get(StatusBarSettings.CLOCK_SCALE));
        }
        Map<String,Object> stored=new HashMap<>();
        stored.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN,"bad legacy type");
        stored.put(StatusBarSettings.CLOCK_SCALE,137.25f);
        stored.put(StatusBarSettings.SAFE_MODE,true);
        stored.put(QsTileAppearance.MASTER,true);
        stored.put(QsMediaAppearance.MASTER,true);
        stored.put(NotificationClearAppearance.MASTER,true);
        stored.put(NotificationBigClockSettings.MASTER,true);
        stored.put(NetworkIconOrder.SWAP,true);
        Bundle saved=SettingsSnapshot.fromPreferences(stored);
        equal(true,SettingsSnapshot.complete(saved));
        equal(true,saved.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        Bundle safe=SafetyMode.runtimeSettings(saved);
        equal(true,FeatureOptions.from(safe).safeMode());
        for(String group:FeatureOptions.GROUPS) equal(false,FeatureOptions.from(safe).enabled(group));
        equal(false,FeatureOptions.from(safe).effective("data",StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        for(String master:new String[]{QsTileAppearance.MASTER,QsMediaAppearance.MASTER,
                NotificationClearAppearance.MASTER,NotificationBigClockSettings.MASTER}) {
            equal(false,safe.get(master));
            equal(true,saved.get(master));
        }
        equal(137.25f,safe.get(StatusBarSettings.CLOCK_SCALE));
        equal(false,safe.get(NetworkIconOrder.SWAP));
        equal(true,saved.get(NetworkIconOrder.SWAP));
        equal("bad legacy type",stored.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        saved.putBoolean(StatusBarSettings.SAFE_MODE,false);
        Bundle resumed=SafetyMode.runtimeSettings(saved);
        equal(true,resumed.get(QsTileAppearance.MASTER));
        equal(true,resumed.get(QsMediaAppearance.MASTER));
        equal(true,resumed.get(NotificationBigClockSettings.MASTER));
        equal(true,resumed.get(NetworkIconOrder.SWAP));
        equal(true,FeatureOptions.from(resumed).effective("data",StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        equal(false,ConfigTransfer.exportJson(stored).contains(StatusBarSettings.SAFE_MODE));
        saved.putBoolean(NativeDataActivity.MASTER,true);saved.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        saved.putBoolean(NotificationGroupStack.MASTER,true);
        Bundle independent=SafetyMode.runtimeSettings(saved);
        equal(false,independent.get(StatusBarSettings.DATA_ACTIVITY_HIDDEN));equal(false,independent.get(NotificationGroupStack.MASTER));
        equal(true,independent.get(NativeDataActivity.MASTER));equal(true,independent.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        saved.putBoolean(StatusBarSettings.SAFE_MODE,true);Bundle independentSafe=SafetyMode.runtimeSettings(saved);
        equal(false,independentSafe.get(NativeDataActivity.MASTER));equal(false,independentSafe.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        equal(false,FeatureOptions.from(independentSafe).effective("data",StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        equal(true,saved.get(NativeDataActivity.MASTER));equal(true,saved.get(FeatureOptions.STACK_LANDSCAPE_ENABLED));

        equal(true,RootAccess.classify(0,"C17_ROOT_UID:0\n",false).granted);
        equal(true,RootAccess.classify(0,"manager message\r\n C17_ROOT_UID:0 \r\n",false).granted);
        for(String output:new String[]{"", "0", "C17_ROOT_UID:2000", "C17_ROOT_UID:00", "prefix:C17_ROOT_UID:0", "C17_ROOT_UID:0 suffix"})
            equal(false,RootAccess.classify(0,output,false).granted);
        equal(false,RootAccess.classify(1,"C17_ROOT_UID:0",false).granted);
        equal(RootAccess.State.TIMEOUT,RootAccess.classify(0,"C17_ROOT_UID:0",true).state);
        System.out.println(checks+" checks passed (independent cellular arrows, complete snapshots, safety restoration, true Root proof)");
    }
}
