package dev.puitheme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.TextView;
import com.oplus.systemui.qs.widget.OplusQSCarrierText;
import com.oplus.systemui.qs.widget.OplusSecondCarrierText;
import com.oplus.systemui.qs.OplusQuickStatusBarHeader;
import com.oplus.systemui.separate.OplusQSSimpleHeader;
import com.oplus.systemui.statusbar.widget.StatClock;
import com.oplus.systemui.statusbar.widget.OplusStatCarrierText;
import com.android.keyguard.CarrierText;
import com.android.systemui.statusbar.phone.KeyguardStatusBarView;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Independently recreated shade and lockscreen carriers, restoration and safe recognition. */
public final class CarrierPanelsCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void same(Object expected, Object actual) {
        checks++;
        if (expected != actual) throw new AssertionError("Native object was not restored");
    }
    private static void near(float expected, float actual) {
        checks++;
        if (Math.abs(expected - actual) > .0001f) throw new AssertionError(expected + " != " + actual);
    }
    private static final class TestResources extends Resources {
        final boolean system;
        TestResources(boolean system) { this.system = system; }
        @Override public String getResourcePackageName(int id) { return system ? "com.android.systemui" : "other.app"; }
        @Override public String getResourceEntryName(int id) {
            switch (id) {
                case 11: return "qs_carrier_text";
                case 12: return "qs_header_carrier_text";
                case 21: return "carrier_text";
                case 22: return "keyguard_carrier_text";
                case 31: return "qs_clock_container";
                case 32: return "qs_status_bar_container_layout";
                case 41: return "keyguard_header";
                case 42: return "keyguard_status_bar_contents";
                default: return "unknown";
            }
        }
    }
    private static final class NamedCarrier extends TextView {
        final int id;
        final Resources resources;
        NamedCarrier(Context context, int id, boolean system) {
            super(context); this.id = id; resources = new TestResources(system);
        }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return resources; }
    }
    private static final class NamedHeader extends ViewGroup {
        final int id;
        final Resources resources = new TestResources(true);
        NamedHeader(Context context, int id) { super(context); this.id = id; }
        @Override public int getId() { return id; }
        @Override public Resources getResources() { return resources; }
    }

    private static final class SpanText implements CharSequence {
        final String text;
        final Object span;
        SpanText(String text, Object span) { this.text = text; this.span = span; }
        @Override public int length() { return text.length(); }
        @Override public char charAt(int i) { return text.charAt(i); }
        @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
        @Override public String toString() { return text; }
    }

    /** Mimics Android converting incoming text to a new buffer with the same characters. */
    private static final class NormalizingCarrier extends TextView {
        final Resources resources = new TestResources(true);
        CharSequence stored = new StringBuilder("");
        CharSequence lastInput;
        Object span;
        int writes;
        NormalizingCarrier(Context context) { super(context); }
        @Override public int getId() { return 11; }
        @Override public Resources getResources() { return resources; }
        @Override public CharSequence getText() { return stored; }
        @Override public void setText(CharSequence text) {
            writes++; lastInput = text;
            span = text instanceof SpanText ? ((SpanText) text).span : null;
            stored = span == null ? new StringBuilder(text == null ? "" : text.toString()) : new SpanText(text.toString(), span);
            requestLayout();
        }
    }

    private static void stableTextCheck(Context context) {
        TextControls control = new TextControls(new Handler(Looper.getMainLooper()));
        NamedHeader header = new NamedHeader(context, 31);
        NormalizingCarrier carrier = new NormalizingCarrier(context); header.addView(carrier);
        Object firstSpan = new Object(), secondSpan = new Object();
        SpanText nativeText = new SpanText("相同文字", firstSpan);
        carrier.setText(nativeText); control.attachTree(header);
        Bundle options = new Bundle();
        options.putBoolean("carrier_enabled", true);
        options.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), true);
        options.putBoolean("font_enabled", false); options.putBoolean("carrier_text_style_enabled", false);
        options.putString(StatusBarSettings.CARRIER_MODE, "text"); options.putString(StatusBarSettings.CARRIER_TEXT, "相同文字");
        int beforeReplacement = carrier.writes;
        control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        equal(beforeReplacement + 1, carrier.writes); equal(true, carrier.span == null);
        int writes = carrier.writes, layouts = carrier.layoutRequests;
        for (int i = 0; i < 300; i++) { control.attachTree(header); control.refresh(); }
        equal(writes, carrier.writes); equal(layouts, carrier.layoutRequests);
        options.putBoolean("carrier_replace_enabled", false);
        control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        equal(writes + 1, carrier.writes); same(firstSpan, carrier.span);
        // This is the exact current native buffer: restoration forces it once, even if characters match.
        SpanText latest = new SpanText("相同文字", secondSpan);
        carrier.setText(control.nativeText(carrier, latest));
        control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        same(latest, carrier.lastInput); same(secondSpan, carrier.span);
        writes = carrier.writes; layouts = carrier.layoutRequests;
        for (int i = 0; i < 300; i++) { control.attachTree(header); control.refresh(); }
        equal(writes, carrier.writes); equal(layouts, carrier.layoutRequests);
        options.putBoolean("carrier_replace_enabled", true);
        control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        equal(writes + 1, carrier.writes); equal(true, carrier.span == null);
        options.putBoolean("carrier_enabled", false);
        control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        equal(writes + 2, carrier.writes); same(latest, carrier.lastInput); same(secondSpan, carrier.span);
        writes = carrier.writes;
        for (int i = 0; i < 300; i++) control.configure(options, StatusBarSettings.COLOR_DEFAULTS, Collections.emptyMap());
        equal(writes, carrier.writes);
    }

    private static void independentPanels(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        TextControls control = new TextControls(handler);
        OplusQSSimpleHeader notificationHeader = new OplusQSSimpleHeader(context);
        OplusQuickStatusBarHeader nestedQuick = new OplusQuickStatusBarHeader(context), controlHeader = new OplusQuickStatusBarHeader(context);
        notificationHeader.addView(nestedQuick);
        OplusSecondCarrierText first = new OplusSecondCarrierText(context), second = new OplusSecondCarrierText(context);
        nestedQuick.addView(first); controlHeader.addView(second);
        first.setText("通知原始"); second.setText("控制原始");
        first.setContentDescription("通知原始描述"); second.setContentDescription("控制原始描述");
        first.setTextSize(0, 16); second.setTextSize(0, 24);
        first.setLetterSpacing(.1f); second.setLetterSpacing(.2f);
        first.setTextColor(0xccffffff); second.setTextColor(0xe6000000);
        Typeface firstFace = new Typeface(), secondFace = new Typeface(); firstFace.weight = 500; secondFace.weight = 550;
        first.setTypeface(firstFace); second.setTypeface(secondFace);
        equal(CarrierPanels.NOTIFICATION, control.group(first)); equal(CarrierPanels.CONTROL, control.group(second));
        OplusSecondCarrierText unknown = new OplusSecondCarrierText(context); unknown.setText("旧兼容原始");
        equal("carrier", control.group(unknown)); equal("", control.group(new TextView(context)));
        StatClock clock = new StatClock(context); equal("clock", control.group(clock));
        clock.setText("系统时间"); clock.shown = false;
        control.attachTree(notificationHeader); control.attachTree(controlHeader); control.attach(unknown); control.attach(clock);
        Bundle settings = new Bundle();
        settings.putBoolean("clock_controls_enabled", true);
        settings.putBoolean("carrier_enabled", true);
        settings.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), true);
        settings.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL, "enabled"), true);
        settings.putString(StatusBarSettings.CARRIER_MODE, "text"); settings.putString(StatusBarSettings.CARRIER_TEXT, "旧兼容设置");
        settings.putBoolean(StatusBarSettings.CLOCK_ENABLED, true); settings.putString(StatusBarSettings.CLOCK_PATTERN, "'CLOCK' HH:mm");
        String notification = CarrierPanels.NOTIFICATION, panel = CarrierPanels.CONTROL;
        settings.putString(CarrierPanels.key(notification, "mode"), "text"); settings.putString(CarrierPanels.key(notification, "text"), "通知独立");
        settings.putString(CarrierPanels.key(panel, "mode"), "text"); settings.putString(CarrierPanels.key(panel, "text"), "控制独立");
        settings.putFloat(CarrierPanels.key(notification, "offset_x"), 150.75f); settings.putFloat(CarrierPanels.key(notification, "offset_y"), 27.25f);
        settings.putFloat(CarrierPanels.key(panel, "offset_x"), -250.5f); settings.putFloat(CarrierPanels.key(panel, "offset_y"), 35.75f);
        settings.putFloat(CarrierPanels.key(notification, "scale"), 400); settings.putFloat(CarrierPanels.key(panel, "scale"), 175);
        settings.putFloat(CarrierPanels.key(notification, "weight"), 690); settings.putFloat(CarrierPanels.key(panel, "weight"), 730);
        settings.putFloat(CarrierPanels.key(notification, "spacing"), 12.75f); settings.putFloat(CarrierPanels.key(panel, "spacing"), -3.25f);
        Map<String,Integer> palette = new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        palette.put(CarrierPanels.key(notification, "color_light"), 0xff223344); palette.put(CarrierPanels.key(notification, "color_dark"), 0x7f112233);
        palette.put(CarrierPanels.key(panel, "color_light"), 0xff778899); palette.put(CarrierPanels.key(panel, "color_dark"), 0xff445566);
        Map<String,Boolean> alpha = new HashMap<>(); alpha.put(CarrierPanels.key(notification, "color_dark"), true);
        control.configure(settings, palette, alpha);
        equal("通知独立", first.getText()); equal("控制独立", second.getText()); equal("旧兼容设置", unknown.getText());
        equal("通知独立", first.getContentDescription()); equal("控制独立", second.getContentDescription());
        equal(true, clock.getText().toString().matches("CLOCK \\d{2}:\\d{2}"));
        near(64, first.getTextSize()); near(42, second.getTextSize()); near(64, control.styledSize(first));
        equal(690, first.getTypeface().weight); equal(730, second.getTypeface().weight);
        near(.1f + 51f / 64f, first.getLetterSpacing()); near(.2f - 13f / 42f, second.getLetterSpacing());
        equal(0x7f112233, first.getCurrentTextColor()); equal(0xe6778899, second.getCurrentTextColor());
        Canvas firstCanvas = new Canvas(), secondCanvas = new Canvas(); control.beforeDraw(first, firstCanvas); control.beforeDraw(second, secondCanvas);
        near(603, firstCanvas.translateX); near(109, firstCanvas.translateY); near(-1002, secondCanvas.translateX); near(143, secondCanvas.translateY);
        first.setText(control.nativeText(first, "最新通知")); second.setText(control.nativeText(second, "最新控制"));
        first.setContentDescription(control.nativeDescription(first, "最新通知描述")); second.setContentDescription(control.nativeDescription(second, "最新控制描述"));
        settings.putBoolean(CarrierPanels.key(notification, "replace_enabled"), false);
        control.configure(settings, palette, alpha);
        equal("最新通知", first.getText()); equal("控制独立", second.getText()); equal("最新通知描述", first.getContentDescription());
        near(64, first.getTextSize());
        settings.putBoolean(CarrierPanels.key(notification, "size_enabled"), false);
        settings.putBoolean(CarrierPanels.key(notification, "position_enabled"), false);
        settings.putBoolean(CarrierPanels.key(notification, "color_enabled"), false);
        settings.putBoolean(CarrierPanels.key(notification, "text_style_enabled"), false);
        settings.putBoolean("font_enabled", false); control.configure(settings, palette, alpha);
        near(16, first.getTextSize()); near(42, second.getTextSize()); equal(0xccffffff, first.getCurrentTextColor()); equal(0xe6778899, second.getCurrentTextColor());
        same(firstFace, first.getTypeface()); equal(730, second.getTypeface().weight); near(.1f, first.getLetterSpacing());
        firstCanvas = new Canvas(); control.beforeDraw(first, firstCanvas); near(0, firstCanvas.translateX); near(0, firstCanvas.translateY);
        settings.putBoolean(CarrierPanels.key(notification, "enabled"), false); control.configure(settings, palette, alpha);
        equal("最新通知", first.getText()); equal("控制独立", second.getText());
        settings.putBoolean(CarrierPanels.key(notification, "enabled"), true);
        for (String suffix : new String[]{"replace_enabled", "size_enabled", "position_enabled", "color_enabled", "text_style_enabled"})
            settings.putBoolean(CarrierPanels.key(notification, suffix), true);
        control.configure(settings, palette, alpha); equal("通知独立", first.getText()); equal(690, first.getTypeface().weight);
        settings.putBoolean("carrier_enabled", false); control.configure(settings, palette, alpha);
        equal("通知独立", first.getText()); equal("控制独立", second.getText());
        near(64, first.getTextSize()); near(42, second.getTextSize()); equal("旧兼容原始", unknown.getText());
        settings.putBoolean(CarrierPanels.key(notification, "enabled"), false);
        settings.putBoolean(CarrierPanels.key(panel, "enabled"), false); control.configure(settings, palette, alpha);
        equal("最新通知", first.getText()); equal("最新控制", second.getText()); near(16, first.getTextSize()); near(24, second.getTextSize());
        same(firstFace, first.getTypeface()); same(secondFace, second.getTypeface()); equal(0xccffffff, first.getCurrentTextColor()); equal(0xe6000000, second.getCurrentTextColor());
        settings.putBoolean(CarrierPanels.key(notification, "enabled"), true);
        settings.putBoolean(CarrierPanels.key(panel, "enabled"), true);
        settings.putBoolean("carrier_enabled", true); settings.putFloat(CarrierPanels.key(notification, "scale"), 0);
        control.configure(settings, palette, alpha); near(0, first.getTextSize()); near(0, control.styledSize(first)); near(42, second.getTextSize());
        settings.putFloat(CarrierPanels.key(notification, "scale"), Float.MAX_VALUE);
        settings.putFloat(CarrierPanels.key(notification, "offset_x"), Float.MAX_VALUE);
        control.configure(settings, palette, alpha); near(NumericPolicy.MAX_TEXT_PIXELS, first.getTextSize());
        firstCanvas = new Canvas(); control.beforeDraw(first, firstCanvas); near(NumericPolicy.MAX_DRAW_PIXELS, firstCanvas.translateX);
        // Reparenting uses the new panel immediately and retains this view's own native baseline.
        first.attached = false; control.detach(first); first.parent = controlHeader; first.attached = true; control.attach(first);
        equal(CarrierPanels.CONTROL, control.group(first)); equal("控制独立", first.getText()); near(28, first.getTextSize()); equal(730, first.getTypeface().weight);
        equal(0xcc445566, first.getCurrentTextColor()); firstCanvas = new Canvas(); control.beforeDraw(first, firstCanvas); near(-1002, firstCanvas.translateX);
        first.parent = nestedQuick; control.attach(first); equal(CarrierPanels.NOTIFICATION, control.group(first)); equal("通知独立", first.getText());
        settings.putFloat(CarrierPanels.key(notification, "scale"), 100); settings.putFloat(CarrierPanels.key(notification, "offset_x"), 0);
        settings.putString(CarrierPanels.key(panel, "mode"), "time"); settings.putString(CarrierPanels.key(panel, "pattern"), "'CONTROL' HH:mm:ss");
        control.configure(settings, palette, alpha); equal("通知独立", first.getText()); equal(true, second.getText().toString().matches("CONTROL \\d{2}:\\d{2}:\\d{2}"));
        equal(true, handler.delayed != null && handler.delay <= 1000);
        second.shown = false; control.visibilityChanged(); equal(true, handler.delayed == null);
        settings.putString(CarrierPanels.key(notification, "mode"), "time"); settings.putString(CarrierPanels.key(notification, "pattern"), "'NOTICE' HH:mm");
        control.configure(settings, palette, alpha); equal(true, first.getText().toString().matches("NOTICE \\d{2}:\\d{2}")); equal(true, handler.delayed != null && handler.delay <= 60000);
        first.shown = false; control.visibilityChanged(); equal(true, handler.delayed == null);
        second.shown = true; control.visibilityChanged(); equal(true, handler.delayed != null && handler.delay <= 1000);
        settings.putBoolean(CarrierPanels.key(panel, "enabled"), false); control.configure(settings, palette, alpha);
        equal("最新控制", second.getText()); equal("最新控制描述", second.getContentDescription()); equal(true, handler.delayed == null);
    }
    private static final class CountingLockCarrier extends OplusStatCarrierText {
        int textWrites, descriptionWrites, colorWrites;
        CountingLockCarrier(Context context) { super(context); }
        @Override public void setText(CharSequence value) { textWrites++; super.setText(value); }
        @Override public void setContentDescription(CharSequence value) { descriptionWrites++; super.setContentDescription(value); }
        @Override public void setTextColor(int value) { colorWrites++; super.setTextColor(value); }
    }

    private static final class SubclassLockHeader extends KeyguardStatusBarView {
        SubclassLockHeader(Context context) { super(context); }
    }

    private static void lockscreen(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        TextControls control = new TextControls(handler);
        KeyguardStatusBarView header = new KeyguardStatusBarView(context);
        ViewGroup wrapper = new ViewGroup(context); header.addView(wrapper);
        CountingLockCarrier carrier = new CountingLockCarrier(context); wrapper.addView(carrier);
        CharSequence nativeText = new StringBuilder("锁屏原生运营商");
        CharSequence nativeDescription = new StringBuilder("锁屏原生描述");
        Typeface face = new Typeface(); face.weight = 550;
        carrier.setText(nativeText); carrier.setContentDescription(nativeDescription);
        carrier.setTypeface(face); carrier.setTextSize(0, 17); carrier.setLetterSpacing(.12f); carrier.setTextColor(0xbbeeddcc);
        equal(TextControls.CARRIER, control.kind(carrier)); equal(CarrierPanels.LOCKSCREEN, control.group(carrier));
        CarrierText aosp = new CarrierText(context);
        equal(TextControls.CARRIER, control.kind(aosp)); equal(CarrierPanels.LOCKSCREEN, control.group(aosp));
        NamedCarrier generic = new NamedCarrier(context, 22, true); generic.shown = false; wrapper.addView(generic);
        equal(TextControls.CARRIER, control.kind(generic)); equal(CarrierPanels.LOCKSCREEN, control.group(generic));
        NamedCarrier resourceCarrier = new NamedCarrier(context, 21, true);
        NamedHeader resourceHeader = new NamedHeader(context, 41); resourceHeader.addView(resourceCarrier);
        equal(TextControls.CARRIER, control.kind(resourceCarrier)); equal(CarrierPanels.LOCKSCREEN, control.group(resourceCarrier));
        equal(TextControls.NONE, control.kind(new NamedCarrier(context, 22, true)));
        NamedCarrier foreign = new NamedCarrier(context, 22, false); wrapper.addView(foreign);
        equal(TextControls.NONE, control.kind(foreign));
        foreign.parent = header; equal(TextControls.NONE, control.kind(foreign));
        NamedCarrier subclassCarrier = new NamedCarrier(context, 21, true);
        SubclassLockHeader subclassHeader = new SubclassLockHeader(context); subclassHeader.addView(subclassCarrier);
        equal(TextControls.CARRIER, control.kind(subclassCarrier)); equal(CarrierPanels.LOCKSCREEN, control.group(subclassCarrier));
        OplusQSCarrierText usage = new OplusQSCarrierText(context); wrapper.addView(usage);
        equal(TextControls.NONE, control.kind(usage)); equal("", control.group(usage));
        StatClock clock = new StatClock(context); equal("clock", control.group(clock));
        int initialTextWrites = carrier.textWrites, initialDescriptionWrites = carrier.descriptionWrites;
        int initialColorWrites = carrier.colorWrites, initialLayouts = carrier.layoutRequests, initialInvalidations = carrier.invalidations;
        Bundle settings = new Bundle();
        settings.putString(StatusBarSettings.CARRIER_MODE, "text"); settings.putString(StatusBarSettings.CARRIER_TEXT, "旧下拉设置");
        settings.putFloat(StatusBarSettings.CARRIER_SCALE, 350); settings.putFloat(StatusBarSettings.CARRIER_WEIGHT, 900);
        Map<String,Integer> palette = new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        palette.put("carrier_color_light", 0xff998877); palette.put("carrier_color_dark", 0xff223344);
        control.configure(settings, palette, Collections.emptyMap()); control.attachTree(header);
        control.beforeMeasure(carrier); Canvas canvas = new Canvas(); control.beforeDraw(carrier, canvas); control.refresh();
        equal(false, control.replaces(carrier)); same(nativeText, carrier.getText()); same(nativeDescription, carrier.getContentDescription());
        same(face, carrier.getTypeface()); near(17, carrier.getTextSize()); near(.12f, carrier.getLetterSpacing()); equal(0xbbeeddcc, carrier.getCurrentTextColor());
        equal(initialTextWrites, carrier.textWrites); equal(initialDescriptionWrites, carrier.descriptionWrites); equal(initialColorWrites, carrier.colorWrites);
        equal(initialLayouts, carrier.layoutRequests); equal(initialInvalidations, carrier.invalidations); near(0, canvas.translateX); near(0, canvas.translateY);
        equal(true, handler.delayed == null);
        control.nativeSize(carrier, 0); near(0, control.styledSize(carrier)); carrier.setTextSize(0, 0);
        control.refresh(); near(0, carrier.getTextSize());
        control.nativeSize(carrier, 5000); near(5000, control.styledSize(carrier)); carrier.setTextSize(0, 5000);
        control.refresh(); near(5000, carrier.getTextSize());
        control.nativeSize(carrier, 17); carrier.setTextSize(0, 17);

        String lock = CarrierPanels.LOCKSCREEN;
        // Enabling the new location alone never inherits the old shade's mode, style or colors.
        settings.putBoolean(CarrierPanels.key(lock, "enabled"), true);
        control.configure(settings, palette, Collections.emptyMap());
        equal(false, control.replaces(carrier)); same(nativeText, carrier.getText()); near(17, carrier.getTextSize()); equal(600, carrier.getTypeface().weight);
        equal(0xbbeeddcc, carrier.getCurrentTextColor());
        settings.putString(CarrierPanels.key(lock, "mode"), "text"); settings.putString(CarrierPanels.key(lock, "text"), "独立锁屏");
        settings.putFloat(CarrierPanels.key(lock, "scale"), 175); settings.putFloat(CarrierPanels.key(lock, "offset_x"), -100.25f);
        settings.putFloat(CarrierPanels.key(lock, "offset_y"), 35.75f); settings.putFloat(CarrierPanels.key(lock, "weight"), 725);
        settings.putFloat(CarrierPanels.key(lock, "spacing"), 1.25f);
        palette.put(CarrierPanels.key(lock, "color_light"), 0xff112233); palette.put(CarrierPanels.key(lock, "color_dark"), 0x7f445566);
        Map<String,Boolean> alpha = new HashMap<>(); alpha.put(CarrierPanels.key(lock, "color_light"), true);
        control.configure(settings, palette, alpha);
        equal(true, control.replaces(carrier)); equal("独立锁屏", carrier.getText()); equal("独立锁屏", carrier.getContentDescription());
        near(29.75f, carrier.getTextSize()); near(29.75f, control.styledSize(carrier)); equal(725, carrier.getTypeface().weight);
        near(.12f + 5f / 29.75f, carrier.getLetterSpacing()); equal(IconAppearance.color(lock, 0xbbeeddcc, palette, alpha), carrier.getCurrentTextColor());
        canvas = new Canvas(); control.beforeDraw(carrier, canvas); near(-401, canvas.translateX); near(143, canvas.translateY);
        carrier.setTextColor(control.nativeColor(carrier, 0xddffffff)); equal(0xdd445566, carrier.getCurrentTextColor());
        CharSequence updated = new StringBuilder("锁屏更新原文"), description = new StringBuilder("锁屏更新描述");
        carrier.setText(control.nativeText(carrier, updated)); carrier.setContentDescription(control.nativeDescription(carrier, description));
        settings.putBoolean(CarrierPanels.key(lock, "replace_enabled"), false); control.configure(settings, palette, alpha);
        same(updated, carrier.getText()); same(description, carrier.getContentDescription()); near(29.75f, carrier.getTextSize());
        for (String suffix : new String[]{"size_enabled", "position_enabled", "color_enabled", "text_style_enabled"})
            settings.putBoolean(CarrierPanels.key(lock, suffix), false);
        settings.putBoolean("font_enabled", false); control.configure(settings, palette, alpha);
        near(17, carrier.getTextSize()); same(face, carrier.getTypeface()); near(.12f, carrier.getLetterSpacing()); equal(0xddffffff, carrier.getCurrentTextColor());
        canvas = new Canvas(); control.beforeDraw(carrier, canvas); near(0, canvas.translateX); near(0, canvas.translateY);
        for (String suffix : new String[]{"replace_enabled", "size_enabled", "position_enabled", "color_enabled", "text_style_enabled"})
            settings.putBoolean(CarrierPanels.key(lock, suffix), true);
        settings.putBoolean("font_enabled", true); settings.putString(CarrierPanels.key(lock, "mode"), "time");
        settings.putString(CarrierPanels.key(lock, "pattern"), "'LOCK' HH:mm:ss"); control.configure(settings, palette, alpha);
        equal(true, carrier.getText().toString().matches("LOCK \\d{2}:\\d{2}:\\d{2}")); equal(true, handler.delayed != null && handler.delay <= 1000);
        carrier.shown = false; control.visibilityChanged(); equal(true, handler.delayed == null);
        carrier.shown = true; control.visibilityChanged(); equal(true, handler.delayed != null);
        control.setInteractive(false); equal(true, handler.delayed == null); control.setInteractive(true); equal(true, handler.delayed != null);
        settings.putBoolean(CarrierPanels.key(lock, "enabled"), false); control.configure(settings, palette, alpha);
        same(updated, carrier.getText()); same(description, carrier.getContentDescription()); same(face, carrier.getTypeface());
        near(17, carrier.getTextSize()); near(.12f, carrier.getLetterSpacing()); equal(0xddffffff, carrier.getCurrentTextColor()); equal(true, handler.delayed == null);
        int stableWrites = carrier.textWrites, stableLayouts = carrier.layoutRequests, stableInvalidations = carrier.invalidations;
        for (int i = 0; i < 500; i++) control.refresh();
        equal(stableWrites, carrier.textWrites); equal(stableLayouts, carrier.layoutRequests); equal(stableInvalidations, carrier.invalidations);
        // Reparenting follows the real owner: SimpleHeader wins over a nested QuickHeader and lockscreen class.
        OplusQuickStatusBarHeader quick = new OplusQuickStatusBarHeader(context);
        OplusQSSimpleHeader simple = new OplusQSSimpleHeader(context); simple.addView(quick);
        carrier.parent = quick; settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION, "mode"), "text");
        settings.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), true);
        settings.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL, "enabled"), true);
        settings.putString(CarrierPanels.key(CarrierPanels.NOTIFICATION, "text"), "通知重新挂载");
        settings.putString(CarrierPanels.key(CarrierPanels.CONTROL, "mode"), "text");
        settings.putString(CarrierPanels.key(CarrierPanels.CONTROL, "text"), "控制重新挂载");
        control.configure(settings, palette, alpha); equal(CarrierPanels.NOTIFICATION, control.group(carrier)); equal("通知重新挂载", carrier.getText());
        quick.parent = null; control.refresh(); equal(CarrierPanels.CONTROL, control.group(carrier)); equal("控制重新挂载", carrier.getText());
        carrier.parent = wrapper; control.refresh(); equal(CarrierPanels.LOCKSCREEN, control.group(carrier));
        same(updated, carrier.getText()); same(face, carrier.getTypeface()); near(17, carrier.getTextSize());
        settings.putBoolean(CarrierPanels.key(lock, "enabled"), true); settings.putBoolean("carrier_enabled", false);
        control.configure(settings, palette, alpha); equal(true, control.replaces(carrier));
        equal(true, carrier.getText().toString().startsWith("LOCK "));
        settings.putBoolean(CarrierPanels.key(lock, "enabled"), false); control.configure(settings, palette, alpha);
        same(updated, carrier.getText()); equal(false, control.replaces(carrier)); equal(true, handler.delayed == null);
        settings.putBoolean(CarrierPanels.key(lock, "enabled"), true); control.configure(settings, palette, alpha); equal(true, control.replaces(carrier));
        equal(true, carrier.getText().toString().startsWith("LOCK "));
    }

    public static void main(String[] args) {
        Context context = new Context();
        Handler handler = new Handler(Looper.getMainLooper());
        TextControls control = new TextControls(handler);
        NamedHeader notifications = new NamedHeader(context, 31), settingsPanel = new NamedHeader(context, 32);
        NamedCarrier first = new NamedCarrier(context, 11, true), second = new NamedCarrier(context, 11, true);
        ViewGroup wrapper = new ViewGroup(context);
        notifications.addView(first); settingsPanel.addView(wrapper); wrapper.addView(second);
        first.setText("通知栏运营商"); second.setText("控制中心运营商");
        first.setContentDescription("通知原始描述"); second.setContentDescription("控制原始描述");
        first.setTextSize(0, 18); second.setTextSize(0, 24);
        first.setLetterSpacing(.1f); second.setLetterSpacing(.2f);
        first.setTextColor(0xceffffff); second.setTextColor(0xe6000000);
        Typeface firstFace = new Typeface(), secondFace = new Typeface();
        firstFace.weight = 500; secondFace.weight = 650;
        first.setTypeface(firstFace); second.setTypeface(secondFace);
        equal(TextControls.CARRIER, control.kind(first)); equal(TextControls.CARRIER, control.kind(second));
        NamedCarrier keyguard = new NamedCarrier(context, 21, true);
        equal(TextControls.NONE, control.kind(keyguard));
        NamedCarrier otherApp = new NamedCarrier(context, 11, false); notifications.addView(otherApp);
        equal(TextControls.NONE, control.kind(otherApp));
        NamedCarrier outside = new NamedCarrier(context, 11, true);
        equal(TextControls.NONE, control.kind(outside));
        OplusQSCarrierText usage = new OplusQSCarrierText(context); usage.setText("0.00 KB/s"); notifications.addView(usage);
        equal(TextControls.NONE, control.kind(usage));
        control.attachTree(notifications); control.attachTree(settingsPanel);
        equal("通知栏运营商", first.getText()); equal("控制中心运营商", second.getText());
        Bundle options = new Bundle();
        options.putBoolean("carrier_enabled", true);
        options.putBoolean("clock_controls_enabled", true);
        options.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), true);
        options.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL, "enabled"), true);
        options.putString(StatusBarSettings.CARRIER_MODE, "text");
        options.putString(StatusBarSettings.CARRIER_TEXT, "自定义面板");
        options.putFloat(StatusBarSettings.CARRIER_OFFSET_X, 10.25f);
        options.putFloat(StatusBarSettings.CARRIER_OFFSET_Y, -1.75f);
        options.putFloat(StatusBarSettings.CARRIER_SCALE, 125f);
        options.putFloat(StatusBarSettings.CARRIER_WEIGHT, 720f);
        options.putFloat(StatusBarSettings.CARRIER_SPACING, .5f);
        Map<String,Integer> palette = new HashMap<>(StatusBarSettings.COLOR_DEFAULTS);
        palette.put("carrier_color_dark", 0xff123456); palette.put("carrier_color_light", 0xff345678);
        palette.put(CarrierPanels.key(CarrierPanels.CONTROL, "color_dark"), 0xff123456);
        palette.put(CarrierPanels.key(CarrierPanels.CONTROL, "color_light"), 0xff345678);
        control.configure(options, palette, Collections.emptyMap());
        equal("自定义面板", first.getText()); equal("自定义面板", second.getText());
        equal("自定义面板", first.getContentDescription()); equal("自定义面板", second.getContentDescription());
        equal("0.00 KB/s", usage.getText());
        near(22.5f, first.getTextSize()); near(30, second.getTextSize());
        equal(720, first.getTypeface().weight); equal(720, second.getTypeface().weight);
        equal(0xce123456, first.getCurrentTextColor()); equal(0xe6345678, second.getCurrentTextColor());
        Canvas moved = new Canvas(); control.beforeDraw(first, moved); near(41, moved.translateX); near(-7, moved.translateY);
        first.setText(control.nativeText(first, "最新通知运营商"));
        second.setText(control.nativeText(second, "最新控制中心运营商"));
        first.setContentDescription(control.nativeDescription(first, "最新通知描述"));
        second.setContentDescription(control.nativeDescription(second, "最新控制描述"));
        options.putBoolean("carrier_replace_enabled", false);
        control.configure(options, palette, Collections.emptyMap());
        equal("最新通知运营商", first.getText()); equal("最新控制中心运营商", second.getText());
        equal("最新通知描述", first.getContentDescription()); equal("最新控制描述", second.getContentDescription());
        near(22.5f, first.getTextSize()); equal(0xce123456, first.getCurrentTextColor());
        options.putBoolean("carrier_size_enabled", false); options.putBoolean("carrier_position_enabled", false);
        options.putBoolean("carrier_color_enabled", false); options.putBoolean("carrier_text_style_enabled", false);
        control.configure(options, palette, Collections.emptyMap());
        near(18, first.getTextSize()); near(24, second.getTextSize());
        near(.1f, first.getLetterSpacing()); near(.2f, second.getLetterSpacing());
        equal(500, first.getTypeface().weight); equal(650, second.getTypeface().weight);
        equal(0xceffffff, first.getCurrentTextColor()); equal(0xe6000000, second.getCurrentTextColor());
        Canvas restored = new Canvas(); control.beforeDraw(first, restored); near(0, restored.translateX); near(0, restored.translateY);
        options.putBoolean("carrier_text_style_enabled", true); options.putBoolean("font_enabled", false);
        control.configure(options, palette, Collections.emptyMap()); equal(720, first.getTypeface().weight); equal(720, second.getTypeface().weight);
        Typeface styledFace = first.getTypeface();
        options.putBoolean("font_enabled", true); control.configure(options, palette, Collections.emptyMap());
        equal(720, first.getTypeface().weight); same(styledFace, first.getTypeface());
        options.putBoolean("font_enabled", false); options.putBoolean("carrier_text_style_enabled", false);
        control.configure(options, palette, Collections.emptyMap()); same(firstFace, first.getTypeface()); same(secondFace, second.getTypeface());
        options.putBoolean("carrier_text_style_enabled", true);
        options.putBoolean("font_enabled", true); options.putBoolean("carrier_replace_enabled", true);
        options.putBoolean("carrier_size_enabled", true); options.putBoolean("carrier_color_enabled", true);
        control.configure(options, palette, Collections.emptyMap());
        equal("自定义面板", first.getText()); equal("自定义面板", second.getText());
        options.putBoolean("carrier_enabled", false);
        options.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), false);
        options.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL, "enabled"), false);
        control.configure(options, palette, Collections.emptyMap());
        equal("最新通知运营商", first.getText()); equal("最新控制中心运营商", second.getText());
        equal("最新通知描述", first.getContentDescription()); equal("最新控制描述", second.getContentDescription());
        near(18, first.getTextSize()); near(24, second.getTextSize()); same(firstFace, first.getTypeface()); same(secondFace, second.getTypeface());
        equal(0xceffffff, first.getCurrentTextColor()); equal(0xe6000000, second.getCurrentTextColor());
        near(.1f, first.getLetterSpacing()); near(.2f, second.getLetterSpacing()); equal(true, handler.delayed == null);
        // System updates while disabled must become the new restoration baseline.
        CharSequence nativeSpans = new StringBuilder("带原生样式的文字");
        first.setText(control.nativeText(first, nativeSpans)); control.nativeSize(first, 20); first.setTextSize(0, 20);
        first.setTextColor(control.nativeColor(first, 0xbbaa9988)); control.nativeSpacing(first, .3f); first.setLetterSpacing(.3f);
        control.configure(options, palette, Collections.emptyMap()); same(nativeSpans, first.getText()); near(20, first.getTextSize());
        equal(0xbbaa9988, first.getCurrentTextColor()); near(.3f, first.getLetterSpacing());
        options.putBoolean("carrier_enabled", true); options.putString(StatusBarSettings.CARRIER_MODE, "time");
        options.putBoolean(CarrierPanels.key(CarrierPanels.NOTIFICATION, "enabled"), true);
        options.putBoolean(CarrierPanels.key(CarrierPanels.CONTROL, "enabled"), true);
        options.putString(StatusBarSettings.CARRIER_PATTERN, "'PANEL' HH:mm:ss");
        StatClock clock = new StatClock(context); clock.setText("原生时间"); control.attach(clock);
        options.putBoolean(StatusBarSettings.CLOCK_ENABLED, true); options.putString(StatusBarSettings.CLOCK_PATTERN, "'CLOCK' HH:mm");
        control.configure(options, palette, Collections.emptyMap());
        equal(true, first.getText().toString().matches("PANEL \\d{2}:\\d{2}:\\d{2}"));
        equal(first.getText(), second.getText()); equal(true, clock.getText().toString().matches("CLOCK \\d{2}:\\d{2}"));
        equal(true, handler.delayed != null && handler.delay <= 1000);
        options.putBoolean("clock_controls_enabled", false); options.putBoolean("carrier_replace_enabled", false);
        control.configure(options, palette, Collections.emptyMap()); equal("原生时间", clock.getText());
        same(nativeSpans, first.getText()); equal(true, handler.delayed == null);
        stableTextCheck(context);
        independentPanels(context);
        lockscreen(context);
        System.out.println(checks + " shade and lockscreen recognition, independent switches and restoration checks passed");
    }
}
