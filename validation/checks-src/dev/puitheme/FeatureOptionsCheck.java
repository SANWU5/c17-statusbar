package dev.puitheme;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

/** Checks upgrade defaults, isolated switches, group gating, and immutable snapshots. */
public final class FeatureOptionsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        networkLabelEligibility();
        FeatureOptions defaults = FeatureOptions.from(new HashMap<String, Object>());
        Map<String,Boolean> expectedBooleans = new HashMap<>(FeatureOptions.DEFAULTS);
        expectedBooleans.put(StatusBarSettings.DIAGNOSTICS_ENABLED, false);
        expectedBooleans.put(StatusBarSettings.SAFE_MODE, false);
        expectedBooleans.put(NetworkIconOrder.SWAP, false);
        expectedBooleans.put(C17HighlightRemoval.ENABLED, false);
        expectedBooleans.put(C17HighlightRemoval.BACKGROUND_ENABLED, false);
        expectedBooleans.put(C17HighlightRemoval.NOTIFICATION_ENABLED, true);
        expectedBooleans.put(C17HighlightRemoval.CONTROL_ENABLED, true);
        expectedBooleans.put(C17HighlightRemoval.HEADS_UP_ENABLED, true);
        expectedBooleans.put(NotificationIconOverrides.MASTER, false);
        expectedBooleans.put(IconPackRepository.MASTER, false);
        expectedBooleans.put(C17HighlightRemoval.UNIFORM_NOTIFICATION_ENABLED, false);
        expectedBooleans.put(LockscreenControls.DATE_ENABLED, false);
        expectedBooleans.put(LockscreenControls.HIDE_LOCK, false);
        expectedBooleans.putAll(NotificationClearMotion.booleanDefaults());
        expectedBooleans.put(NotificationGroupStack.MASTER, false);
        expectedBooleans.putAll(NotificationMiniStack.BOOLEANS);
        expectedBooleans.putAll(NativeStatusIcons.booleanDefaults());
        expectedBooleans.putAll(NativeNetworkBadgeControls.booleanDefaults());
        expectedBooleans.putAll(NetworkSpeedControls.BOOLEANS);
        expectedBooleans.putAll(QsTileAppearance.BOOLEANS);
        expectedBooleans.putAll(QsTileFullFill.BOOLEANS);
        expectedBooleans.putAll(QsTileCorners.BOOLEANS);
        expectedBooleans.putAll(QsMediaAppearance.BOOLEANS);
        expectedBooleans.putAll(QsCardSettings.BOOLEANS);
        expectedBooleans.put(QsWifiAlias.ENABLED,false);
        expectedBooleans.putAll(NotificationBigClockSettings.BOOLEANS);
        expectedBooleans.putAll(BigClockWidgetsSettings.BOOLEANS);
        expectedBooleans.putAll(FeatureGroups.BOOLEANS);
        expectedBooleans.putAll(ClockBackdropSettings.GLOBAL_BOOLEANS);
        expectedBooleans.putAll(NotificationClearAppearance.BOOLEANS);
        expectedBooleans.putAll(NotificationIconArea.BOOLEANS);
        expectedBooleans.putAll(BatteryTextStyle.BOOLEANS);
        expectedBooleans.putAll(QsTileIconSize.BOOLEANS);
        expectedBooleans.putAll(StatusBarShadeIconSettings.BOOLEANS);
        expectedBooleans.put(FluidCloudStyle.BACKGROUND_COLOR_CUSTOMIZED, false);
        expectedBooleans.putAll(FluidCloudScope.BOOLEANS);
        expectedBooleans.putAll(ShadeWallpaperSettings.BOOLEANS);
        expectedBooleans.putAll(LockNotificationSettings.BOOLEANS);
        expectedBooleans.put(FluidCloudAccent.ENABLED, false);
        expectedBooleans.put(CapsulePersistence.ENABLED, false);
        expectedBooleans.put(LockscreenStatusBlur.ENABLED, false);
        expectedBooleans.putAll(SpeedPosition.booleanDefaults());
        expectedBooleans.putAll(StatusBarLayoutSettings.BOOLEANS);
        expectedBooleans.put(ResidentCache.INSTALLED, false);
        expectedBooleans.put(ResidentCache.RUNNING, false);
        equal(expectedBooleans, StatusBarSettings.BOOLEAN_DEFAULTS);
        equal(6, QsCardSettings.BOOLEANS.size()); // Five current switches and the preserved code195 key.
        for (String key : QsCardSettings.BOOLEANS.keySet()) {
            equal(false, StatusBarSettings.BOOLEAN_DEFAULTS.get(key));
            equal(false, FeatureOptions.DEFAULTS.containsKey(key));
        }
        equal(false, StatusBarSettings.BOOLEAN_DEFAULTS.get(StatusBarLayoutSettings.MASTER));
        equal(false, FeatureOptions.DEFAULTS.containsKey(StatusBarLayoutSettings.MASTER));
        Bundle layoutOnly = new Bundle(); layoutOnly.putBoolean(StatusBarLayoutSettings.MASTER, true);
        equal(true, StatusBarLayoutSettings.enabled(layoutOnly));
        equal(false, FeatureOptions.from(layoutOnly).enabled("clock"));
        equal(false, FeatureOptions.from(layoutOnly).enabled("wifi"));
        equal(false,StatusBarSettings.bool(new HashMap<String,Object>(),NativeNetworkBadgeControls.MASTER));
        equal(false,FeatureOptions.DEFAULTS.containsKey(NativeNetworkBadgeControls.MASTER));
        equal(false, StatusBarSettings.BOOLEAN_DEFAULTS.get(QsTileCorners.MASTER));
        equal(false, StatusBarSettings.bool(new HashMap<String,Object>(), QsTileCorners.MASTER));
        equal(false, FeatureOptions.DEFAULTS.containsKey(QsTileCorners.MASTER));
        equal(false, StatusBarSettings.bool(new HashMap<String,Object>(), QsTileAppearance.MASTER));
        equal(false, StatusBarSettings.bool(new HashMap<String,Object>(), StatusBarSettings.DIAGNOSTICS_ENABLED));
        equal("pui", StatusBarSettings.STRING_DEFAULTS.get(StatusBarSettings.BATTERY_STYLE));
        equal(false, defaults.isEnabled("clock_enabled"));
        equal(false, defaults.enabled("clock"));
        equal(true, defaults.isEnabled("battery_charge_inside"));
        equal(false, defaults.effective("wifi", "wifi_activity_hidden"));
        equal(false, defaults.enabled("unknown"));
        equal(false, defaults.isEnabled("unknown"));
        equal(11, FeatureOptions.GROUPS.length);
        equal(158, FeatureOptions.DEFAULTS.size());
        equal(false,FeatureOptions.DEFAULTS.get(NetworkLabelFormats.ENABLED));
        equal("label",FeatureOptions.GROUP_BY_KEY.get(NetworkLabelFormats.ENABLED));
        Map<String,Object> gradeSettings=new HashMap<>();gradeSettings.put("label_enabled",true);
        gradeSettings.put(NetworkLabelFormats.ENABLED,true);gradeSettings.put(NetworkLabelFormats.PREFIX+"5g_plus","自定义 📶");
        equal(true,FeatureOptions.from(gradeSettings).effective("label",NetworkLabelFormats.ENABLED));
        equal(true,FeatureOptions.from(gradeSettings).isEnabled(NetworkLabelFormats.ENABLED));
        gradeSettings.put("label_normalize_enabled",false);
        equal(true,FeatureOptions.from(gradeSettings).effective("label",NetworkLabelFormats.ENABLED));
        // Custom contents and unify exclude each other: a saved state with both on runs the custom contents only.
        gradeSettings.put("label_normalize_enabled",true);
        equal(true,FeatureOptions.from(gradeSettings).effective("label",NetworkLabelFormats.ENABLED));
        equal(false,FeatureOptions.from(gradeSettings).effective("label","label_normalize_enabled"));
        equal("自定义 📶",gradeSettings.get(NetworkLabelFormats.PREFIX+"5g_plus"));
        gradeSettings.put(NetworkLabelFormats.ENABLED,false);
        equal(false,FeatureOptions.from(gradeSettings).effective("label",NetworkLabelFormats.ENABLED));
        equal(true,FeatureOptions.from(gradeSettings).effective("label","label_normalize_enabled"));
        Map<String,Object> retiredForeground=new HashMap<>();retiredForeground.put(FluidCloudStyle.MASTER,true);
        for(Object oldRequest:new Object[]{true,false,"true",1,null}) {
            retiredForeground.put(FluidCloudStyle.FOLLOW_FOREGROUND,oldRequest);
            FeatureOptions retired=FeatureOptions.from(retiredForeground);
            equal(false,StatusBarSettings.bool(retiredForeground,FluidCloudStyle.FOLLOW_FOREGROUND));
            equal(false,retired.isEnabled(FluidCloudStyle.FOLLOW_FOREGROUND));
            equal(false,retired.effective("fluid_cloud_style",FluidCloudStyle.FOLLOW_FOREGROUND));
            equal(false,retired.enabled("fluid_cloud_style"));
        }
        retiredForeground.put(FluidCloudStyle.FOLLOW_NOTIFICATION,true);
        equal(true,FeatureOptions.from(retiredForeground).enabled("fluid_cloud_style"));
        equal(false,FeatureOptions.from(retiredForeground).effective("fluid_cloud_style",FluidCloudStyle.FOLLOW_FOREGROUND));
        equal(false,FeatureOptions.DEFAULTS.get(FluidCloudAccent.LOCKSCREEN_ENABLED));
        equal("fluid_cloud_accent",FeatureOptions.GROUP_BY_KEY.get(FluidCloudAccent.LOCKSCREEN_ENABLED));
        equal(false,defaults.isEnabled(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        Map<String,Object> landscapeStack=new HashMap<>();landscapeStack.put(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        equal(true,FeatureOptions.from(landscapeStack).isEnabled(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        equal(false,StatusBarSettings.bool(landscapeStack,NotificationBigClockSettings.STACK_ENABLED));
        equal(false,FeatureOptions.GROUP_BY_KEY.containsKey(FeatureOptions.STACK_LANDSCAPE_ENABLED));
        for(Object retired:new Object[]{true,false,"true",0,null}){
            landscapeStack.put(NotificationGroupStack.MASTER,retired);
            equal(false,StatusBarSettings.bool(landscapeStack,NotificationGroupStack.MASTER));
        }
        equal(false,defaults.isEnabled(NativeDataActivity.MASTER));
        Map<String,Object> arrows=new HashMap<>();arrows.put(NativeDataActivity.MASTER,true);
        equal(true,FeatureOptions.from(arrows).isEnabled(NativeDataActivity.MASTER));
        equal(false,StatusBarSettings.bool(arrows,StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        equal(false,FeatureOptions.from(arrows).effective("data",StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        for(Object oldHidden:new Object[]{true,false,"false",null}){
            arrows.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN,oldHidden);
            equal(false,StatusBarSettings.bool(arrows,StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        }
        arrows.put(NativeDataActivity.MASTER,false);equal(true,StatusBarSettings.bool(arrows,StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        Map<String,Object> signalSelection = new HashMap<>();
        signalSelection.put("data_enabled",true); signalSelection.put("data_icon_enabled",true);
        signalSelection.put("data_single_enabled",false);
        FeatureOptions selection = FeatureOptions.from(signalSelection);
        equal(true,selection.singleSignal("single")); equal(false,selection.singleSignal("system"));
        signalSelection.put("data_icon_enabled",false);
        equal(true,FeatureOptions.from(signalSelection).singleSignal("single"));
        equal(false,FeatureOptions.from(signalSelection).singleSignal("system"));
        signalSelection.put("data_icon_enabled",true);
        equal(true,selection.hideNativeNetworkBadge());
        signalSelection.put("data_badge_hidden",false);
        equal(false,FeatureOptions.from(signalSelection).hideNativeNetworkBadge());
        signalSelection.put("data_enabled",false); signalSelection.put("data_badge_hidden",true);
        equal(false,FeatureOptions.from(signalSelection).singleSignal("single"));
        equal(false,FeatureOptions.from(signalSelection).hideNativeNetworkBadge());
        signalSelection.put("data_enabled",true); signalSelection.put(StatusBarSettings.SAFE_MODE,true);
        equal(false,FeatureOptions.from(signalSelection).singleSignal("single"));
        equal(false,FeatureOptions.from(signalSelection).hideNativeNetworkBadge());
        equal(false, defaults.isEnabled(DataBatterySpacing.ENABLED));
        equal(false, defaults.effective("data", DataBatterySpacing.ENABLED));
        Map<String,Object> spacing = new HashMap<>();spacing.put(DataBatterySpacing.ENABLED,true);
        equal(false, FeatureOptions.from(spacing).effective("data", DataBatterySpacing.ENABLED));
        spacing.put("data_enabled",true);
        equal(true, FeatureOptions.from(spacing).effective("data", DataBatterySpacing.ENABLED));
        spacing.put(StatusBarSettings.SAFE_MODE,true);
        equal(false, FeatureOptions.from(spacing).effective("data", DataBatterySpacing.ENABLED));
        equal("shade_clock_controls_enabled", FeatureOptions.masterKey("shade_clock"));
        equal(false, defaults.enabled("shade_clock"));
        equal(false, defaults.isEnabled("shade_clock_enabled"));
        Map<String,Object> independentClock = new HashMap<>();
        independentClock.put("clock_controls_enabled", false); independentClock.put("shade_clock_controls_enabled", true);
        independentClock.put("shade_clock_enabled", true);
        FeatureOptions shade = FeatureOptions.from(independentClock);
        equal(false, shade.enabled("clock")); equal(true, shade.enabled("shade_clock"));
        equal(true, shade.effective("shade_clock", "shade_clock_enabled"));
        equal(true, shade.position("shade_clock")); equal(true, shade.size("shade_clock"));
        equal(true, shade.color("shade_clock")); equal(true, shade.textStyle("shade_clock"));
        equal(false, defaults.enabled("tiles"));
        equal(false, defaults.effective("tiles", "tiles_fade_enabled"));
        equal(false, defaults.effective("tiles", "tiles_blur_enabled"));
        equal(false, defaults.effective("tiles", "tiles_portrait_enabled"));
        equal(false, defaults.effective("tiles", "tiles_landscape_enabled"));
        equal(false, defaults.enabled(CarrierPanels.LOCKSCREEN));
        for (String group : FeatureOptions.GROUPS) equal(false, defaults.enabled(group));
        for (String panel : CarrierPanels.GROUPS) {
            equal(false, defaults.enabled(panel));
            Map<String,Object> scene = new HashMap<>();scene.put(panel + "_enabled", true);
            FeatureOptions enabledScene = FeatureOptions.from(scene);
            equal(true, enabledScene.enabled(panel));equal(true, enabledScene.position(panel));
            equal(false, enabledScene.enabled("carrier"));
            for (String other : CarrierPanels.GROUPS) equal(panel.equals(other), enabledScene.enabled(other));
            scene.put(StatusBarSettings.SAFE_MODE, true);
            equal(false, FeatureOptions.from(scene).enabled(panel));
        }
        for (String suffix : new String[]{"position", "size", "color", "text_style"})
            equal(false, FeatureOptions.DEFAULTS.containsKey("tiles_" + suffix + "_enabled"));
        equal(false, defaults.position("tiles"));
        equal(false, defaults.size("tiles"));
        equal(false, defaults.color("tiles"));
        equal(false, defaults.textStyle("tiles"));

        for (String group : FeatureOptions.GROUPS) {
            Map<String, Object> values = new HashMap<>();
            values.put(FeatureOptions.masterKey(group), false);
            FeatureOptions off = FeatureOptions.from(values);
            equal(false, off.enabled(group));
            for (Map.Entry<String, String> entry : FeatureOptions.GROUP_BY_KEY.entrySet()) {
                String key = entry.getKey(), owner = entry.getValue();
                // Turning off one group must leave every other group untouched.
                equal(owner.equals(group) && !RadioState.WIFI_MERGED.equals(key) && !RadioState.ALWAYS_SHOWN.equals(key) ? false : defaults.effective(owner, key), off.effective(owner, key));
            }
            equal(false, off.position(group));
            equal(false, off.size(group));
            equal(false, off.color(group));
            equal(false, off.textStyle(group));
        }

        for (String key : FeatureOptions.DEFAULTS.keySet()) {
            if (StatusBarSettings.DATA_ACTIVITY_HIDDEN.equals(key)||FeatureOptions.STACK_LANDSCAPE_ENABLED.equals(key)
                    ||NativeDataActivity.MASTER.equals(key)||NativeDataActivity.COLOR_ENABLED.equals(key)) continue;
            String group = FeatureOptions.GROUP_BY_KEY.get(key);
            equal(true, group != null);
            Map<String, Object> values = new HashMap<>();
            values.put(key, false);
            FeatureOptions oneOff = FeatureOptions.from(values);
            equal(false, oneOff.isEnabled(key));
            equal(false, oneOff.effective(group, key));
            for (String other : FeatureOptions.DEFAULTS.keySet())
                if (!other.equals(key)) equal(StatusBarSettings.bool(values, other), oneOff.isEnabled(other));
            values.put(key, true);
            // A pending UI editor cannot mutate already configured hook state.
            equal(false, oneOff.isEnabled(key));
            FeatureOptions oneOn = FeatureOptions.from(values);
            // Retired switches stay off whatever is saved.
            equal(!FluidCloudStyle.FOLLOW_FOREGROUND.equals(key) && !FluidCloudStyle.SCOPE_SHADE.equals(key), oneOn.isEnabled(key));
            values.put(key, "false");
            equal(FeatureOptions.DEFAULTS.get(key), FeatureOptions.from(values).isEnabled(key));
            equal(FeatureOptions.DEFAULTS.get(key), StatusBarSettings.bool(values, key));
            Bundle bundle = new Bundle();
            bundle.putBoolean(key, false);
            equal(oneOff.values(), FeatureOptions.from(bundle).values());
        }

        for (Object legacyValue : new Object[]{false, true, "false", 0, 1}) {
            Map<String,Object> legacyArrow = new HashMap<>();
            legacyArrow.put(StatusBarSettings.DATA_ACTIVITY_HIDDEN, legacyValue);
            legacyArrow.put("data_enabled", false);
            FeatureOptions forced = FeatureOptions.from(legacyArrow);
            equal(true, forced.isEnabled(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
            equal(true, forced.effective("data", StatusBarSettings.DATA_ACTIVITY_HIDDEN));
            equal(true, StatusBarSettings.bool(legacyArrow, StatusBarSettings.DATA_ACTIVITY_HIDDEN));
            legacyArrow.put(StatusBarSettings.SAFE_MODE, true);
            FeatureOptions safe = FeatureOptions.from(legacyArrow);
            equal(true, safe.safeMode());
            equal(false, safe.effective("data", StatusBarSettings.DATA_ACTIVITY_HIDDEN));
            for (String group : FeatureOptions.GROUPS) equal(false, safe.enabled(group));
        }
        equal(false, FeatureOptions.GROUP_BY_KEY.containsKey(StatusBarSettings.DATA_ACTIVITY_HIDDEN));
        independentFluidCloudOptions();

        Map<String, Object> legacy = new HashMap<>();
        legacy.put("clock_enabled", true);
        legacy.put("clock_controls_enabled", true);
        legacy.put("battery_charge_inside", false);
        legacy.put("battery_enabled", true);
        FeatureOptions upgraded = FeatureOptions.from(legacy);
        equal(true, upgraded.enabled("clock"));
        equal(true, upgraded.effective("clock", "clock_enabled"));
        equal(false, upgraded.enabled("shade_clock"));
        equal(false, upgraded.effective("battery", "battery_charge_inside"));
        equal(true, upgraded.enabled("battery"));
        equal(FeatureOptions.from(new HashMap<String, Object>()).values(), FeatureOptions.from((Bundle) null).values());
        try {
            defaults.values().put("data_enabled", false);
            throw new AssertionError("Snapshot is mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        try {
            FeatureOptions.DEFAULTS.put("wifi_enabled", false);
            throw new AssertionError("Defaults are mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        System.out.println(checks + " checks passed (feature defaults, independent switches, group gating, bundle transport)");
    }

    private static void networkLabelEligibility() {
        for(boolean master:new boolean[]{false,true})for(boolean merged:new boolean[]{false,true})
        for(boolean always:new boolean[]{false,true})for(boolean both:new boolean[]{false,true})
        for(boolean hidden:new boolean[]{false,true})for(boolean safe:new boolean[]{false,true}) {
            Map<String,Object> settings=new HashMap<>();settings.put("label_enabled",master);
            settings.put(RadioState.WIFI_MERGED,merged);settings.put(RadioState.ALWAYS_SHOWN,always);
            settings.put(RadioState.BOTH_VISIBLE,both);settings.put(NetworkSlotGeometry.POSITION,true);
            settings.put(StatusBarSettings.LABEL_HIDDEN,hidden);settings.put(StatusBarSettings.SAFE_MODE,safe);
            FeatureOptions options=FeatureOptions.from(settings);
            equal(both,options.isEnabled(RadioState.BOTH_VISIBLE));equal(both,options.values().get(RadioState.BOTH_VISIBLE));
            equal(false,options.effective("network",RadioState.BOTH_VISIBLE));
            equal(false,options.effective("network",NetworkSlotGeometry.POSITION));
            equal(false,options.effective("label",RadioState.WIFI_MERGED));equal(false,options.effective("label",RadioState.ALWAYS_SHOWN));
            equal(!safe&&master,options.enabled("label"));equal(!safe&&master&&hidden,options.hideNetworkLabel());
            for(boolean wifi:new boolean[]{false,true}) {
                boolean shown=!safe&&master&&!hidden&&!wifi;
                equal(shown?"5GA":"",options.hideNetworkLabel()?"":RadioState.cellularLabel(options.enabled("label"),wifi,false,"5GA","",false,true,true));
                equal("",RadioState.cellularLabel(options.enabled("label"),wifi,false,"","",false,true,true));
                equal("",RadioState.cellularLabel(options.enabled("label"),wifi,true,"5GA","",false,true,true));
                equal("",RadioState.cellularLabel(options.enabled("label"),wifi,false,"5GA","",false,false,false));
            }
        }
    }

    private static void independentFluidCloudOptions() {
        // Desktop highlight removal moved to the C17 feature; its old switch stays readable for backups but never owns the page.
        String[] keys={FluidCloudStyle.FOLLOW_NOTIFICATION,FluidCloudStyle.DESKTOP_BACKGROUND};
        Map<String,Object> retiredOnly=new HashMap<>();retiredOnly.put(FluidCloudStyle.DESKTOP_HIGHLIGHTS,true);
        equal(false,FeatureOptions.from(retiredOnly).enabled("fluid_cloud_style"));
        for(int flags=0;flags<4;flags++) {
            Map<String,Object> values=new HashMap<>();
            values.put(FluidCloudAccent.ENABLED,false);
            for(int i=0;i<keys.length;i++)values.put(keys[i],(flags&(1<<i))!=0);
            FeatureOptions options=FeatureOptions.from(values);
            equal(flags!=0,options.enabled("fluid_cloud_style"));
            for(int i=0;i<keys.length;i++)equal((flags&(1<<i))!=0,options.effective("fluid_cloud_style",keys[i]));
            values.put(FluidCloudStyle.MASTER,false);
            for(String key:keys)equal(false,FeatureOptions.from(values).effective("fluid_cloud_style",key));
            values.put(FluidCloudAccent.ENABLED,true);
            equal(true,FeatureOptions.from(values).enabled("fluid_cloud_accent"));
            values.put(FluidCloudStyle.MASTER,true);
            for(int i=0;i<keys.length;i++)equal((flags&(1<<i))!=0,FeatureOptions.from(values).effective("fluid_cloud_style",keys[i]));
            values.put(StatusBarSettings.SAFE_MODE,true);
            FeatureOptions safe=FeatureOptions.from(values);
            for(String key:keys)equal(false,safe.effective("fluid_cloud_style",key));
        }
        Map<String,Object> values=new HashMap<>();values.put(FluidCloudAccent.MASK_ENABLED,true);
        values.put(FluidCloudAccent.LOCKSCREEN_ENABLED,true);
        equal(false,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.LOCKSCREEN_ENABLED));
        equal(false,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.MASK_ENABLED));
        values.put(FluidCloudAccent.ENABLED,true);
        equal(true,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.LOCKSCREEN_ENABLED));
        equal(true,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.MASK_ENABLED));
        values.put(StatusBarSettings.SAFE_MODE,true);
        equal(false,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.LOCKSCREEN_ENABLED));
        equal(false,FeatureOptions.from(values).effective("fluid_cloud_accent",FluidCloudAccent.MASK_ENABLED));
        equal(true,FeatureOptions.DEFAULTS.containsKey(FluidCloudStyle.MASTER));
    }
}
