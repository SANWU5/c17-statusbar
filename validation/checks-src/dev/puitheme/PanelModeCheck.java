package dev.puitheme;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import com.oplus.systemui.qs.widget.OplusQSClock;
import com.oplus.systemui.qs.widget.OplusSecondCarrierText;
import com.oplus.systemui.qs.OplusQuickStatusBarHeader;
import com.oplus.systemui.statusbar.widget.StatClock;
import java.util.Collections;

/** Mode transitions must retain saved configuration and restore the actual native text. */
public final class PanelModeCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++; if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void near(float expected,float actual) {
        checks++; if (Math.abs(expected-actual)>.0001f) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        equal(PanelMode.SEPARATE, PanelMode.parse("true")); equal(PanelMode.CLASSIC, PanelMode.parse("false"));
        for (String unknown : new String[]{null,"", "1", "0", "TRUE", "False", " true "}) equal(PanelMode.UNKNOWN,PanelMode.parse(unknown));
        equal(PanelMode.CLASSIC,PanelMode.query(key -> PanelMode.SETTING.equals(key)?"false":"true").getString("mode"));
        equal("system",PanelMode.snapshot().getString("source")); equal(true,PanelMode.isClassic());
        equal(PanelMode.SEPARATE,PanelMode.query(key -> PanelMode.DEFAULT_SETTING.equals(key)?"true":null).getString("mode"));
        equal("system_default",PanelMode.snapshot().getString("source"));
        equal(PanelMode.UNKNOWN,PanelMode.query(key -> null).getString("mode"));
        equal(PanelMode.UNKNOWN,PanelMode.query(key -> {throw new SecurityException("unavailable");}).getString("mode"));
        equal("",PanelMode.freezeReason(NotificationBigClockSettings.MASTER));
        Bundle saved = new Bundle();
        saved.putBoolean(NotificationBigClockSettings.MASTER,true); saved.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,true);
        saved.putBoolean(NotificationBigClockSettings.STACK_ENABLED,true); saved.putBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,true);
        saved.putBoolean("tiles_enabled",true); saved.putBoolean("battery_enabled",true);
        saved.putString("carrier_notification_text","独立通知"); saved.putFloat("notification_big_clock_size",77f);
        Bundle runtime=PanelMode.runtimeSettings(saved,PanelMode.CLASSIC);
        equal(false,runtime.getBoolean(NotificationBigClockSettings.MASTER,false)); equal(false,runtime.getBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER,false));
        equal(false,runtime.getBoolean(NotificationBigClockSettings.STACK_ENABLED,false)); equal(false,runtime.getBoolean(FeatureOptions.STACK_LANDSCAPE_ENABLED,false));
        equal(true,runtime.getBoolean("tiles_enabled",false)); equal(true,runtime.getBoolean("battery_enabled",false));
        equal(77f,runtime.get("notification_big_clock_size")); equal("独立通知",saved.getString("carrier_notification_text"));
        equal(true,saved.getBoolean(NotificationBigClockSettings.MASTER,false)); equal(true,PanelMode.runtimeSettings(saved,PanelMode.SEPARATE).getBoolean(NotificationBigClockSettings.MASTER,false));
        equal(true,PanelMode.runtimeSettings(saved,PanelMode.UNKNOWN).getBoolean(NotificationBigClockSettings.MASTER,false));
        unavailableWallpaper(saved);
        equal(null,CarrierPanels.legacyKey("carrier_classic_text")); equal(true,CarrierPanels.isPanel(CarrierPanels.CLASSIC));
        equal(3,CarrierPanels.GROUPS.length); equal(4,CarrierPanels.ALL_GROUPS.length);
        classicText();
        PanelMode.query(key -> null);
        System.out.println(checks + " native panel mode, saved-config isolation and classic text restoration checks passed");
    }
    private static void unavailableWallpaper(Bundle saved) {
        for (String key : ShadeWallpaperSettings.BOOLEANS.keySet()) saved.putBoolean(key,true);
        for (String scene : ShadeWallpaperSettings.SCENES) {
            saved.putString(ShadeWallpaperSettings.revisionKey(scene),"a".repeat(64));
            saved.putFloat(ShadeWallpaperSettings.brightnessKey(scene),134f);
        }
        for (String mode : new String[]{PanelMode.CLASSIC,PanelMode.SEPARATE,PanelMode.UNKNOWN,null}) {
            Bundle runtime=PanelMode.runtimeSettings(saved,mode);
            for (String key : ShadeWallpaperSettings.BOOLEANS.keySet()) {
                equal(false,runtime.getBoolean(key,true));equal(true,saved.getBoolean(key,false));
            }
            for (String scene : ShadeWallpaperSettings.SCENES) {
                equal("a".repeat(64),runtime.getString(ShadeWallpaperSettings.revisionKey(scene)));
                equal(134f,runtime.get(ShadeWallpaperSettings.brightnessKey(scene)));
            }
            equal(true,runtime.getBoolean("tiles_enabled",false));
        }
        PanelMode.report(false);equal("未完成的开发",PanelMode.freezeReason(ShadeWallpaperSettings.MASTER));
        PanelMode.report(true);equal("未完成的开发",PanelMode.freezeReason(ShadeWallpaperSettings.MASTER));
        PanelMode.query(key -> null);equal("未完成的开发",PanelMode.freezeReason(ShadeWallpaperSettings.MASTER));
    }
    private static void classicText() {
        Context context=new Context(); TextControls text=new TextControls(new Handler(Looper.getMainLooper()));
        OplusQuickStatusBarHeader header=new OplusQuickStatusBarHeader(context);
        OplusQSClock clock=new OplusQSClock(context); OplusSecondCarrierText carrier=new OplusSecondCarrierText(context);
        StatClock phone=new StatClock(context);
        header.addView(clock); header.addView(carrier); clock.setText("原生经典时钟"); carrier.setText("原生运营商"); phone.setText("原生状态栏");
        text.attachTree(header); text.attach(phone);
        Bundle settings=new Bundle(); settings.putBoolean("shade_clock_controls_enabled",true);settings.putBoolean("shade_clock_enabled",true);
        settings.putString("shade_clock_pattern","'SEP'"); settings.putBoolean("carrier_control_enabled",true);
        settings.putString("carrier_control_mode","text");settings.putString("carrier_control_text","分离");
        settings.putBoolean(ClassicTextSettings.CLOCK_MASTER,true); settings.putBoolean("classic_clock_enabled",true);
        settings.putString("classic_clock_pattern","'CLASSIC'");settings.putFloat("classic_clock_offset_x",7f);
        settings.putBoolean("carrier_classic_enabled",true); settings.putString("carrier_classic_mode","text");settings.putString("carrier_classic_text","经典独立");
        PanelMode.report(false); text.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal(PanelMode.CLOCK,text.group(clock)); equal(CarrierPanels.CLASSIC,text.group(carrier));
        equal("CLASSIC",clock.getText().toString());equal("经典独立",carrier.getText()); equal("原生状态栏",phone.getText());
        Canvas canvas=new Canvas();text.beforeDraw(clock,canvas);near(7f*4f,canvas.translateX);
        settings.putBoolean(ClassicTextSettings.CLOCK_MASTER,false);settings.putBoolean("carrier_classic_enabled",false);
        text.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal("原生经典时钟",clock.getText());equal("原生运营商",carrier.getText());
        PanelMode.report(true);text.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal("shade_clock",text.group(clock));equal(CarrierPanels.CONTROL,text.group(carrier));
        equal("SEP",clock.getText().toString());equal("分离",carrier.getText());
        PanelMode.report(false);text.configure(settings,StatusBarSettings.COLOR_DEFAULTS,Collections.emptyMap());
        equal("原生经典时钟",clock.getText());equal("原生运营商",carrier.getText());
    }
}
