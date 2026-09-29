package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.util.Collections;
import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;

/** Tracks native text and styling per view; one visible-view scheduler serves both time locations. */
public final class TextControls {
    public static final int NONE = 0, CLOCK = 1, CARRIER = 2;
    private final Handler handler;
    private final Map<TextView,Entry> views = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Integer> internal = ThreadLocal.withInitial(() -> 0);
    private Context context;
    private boolean interactive = true, pending;
    private volatile boolean clockEnabled;
    private volatile String clockPattern = TimeFormat.CLOCK_DEFAULT, carrierPattern = TimeFormat.CARRIER_DEFAULT;
    private volatile String carrierMode = "original", carrierText = "";
    private volatile Map<String,Integer> colors = StatusBarSettings.COLOR_DEFAULTS;
    private volatile Map<String,Boolean> alpha = Collections.emptyMap();
    private float clockX, clockY, carrierX, carrierY, clockScale = 100, carrierScale = 100, clockSpacing, carrierSpacing;
    private int clockWeight = 600, carrierWeight = 600;
    private final Runnable tick = () -> { pending = false; refresh(); };

    private static final class Entry {
        int kind, nativeTint;
        float nativeSize, nativeSpacing;
        Typeface nativeFace;
        CharSequence nativeText;
        CharSequence nativeDescription;
        Entry(TextView view, int kind) {
            this.kind = kind; nativeTint = view.getCurrentTextColor();
            nativeSize = view.getTextSize(); nativeFace = view.getTypeface();
            nativeSpacing = view.getLetterSpacing(); nativeText = view.getText();
            nativeDescription = view.getContentDescription();
        }
    }

    public TextControls(Handler handler) { this.handler = handler; }
    public boolean isInternal() { return internal.get() != 0; }
    public void enter() { internal.set(internal.get() + 1); }
    public void exit() { internal.set(Math.max(0, internal.get() - 1)); }

    public static int namedKind(String className, String resourceName, String parentClass) {
        if (className.equals("com.oplus.systemui.statusbar.widget.StatClock")) return CLOCK;
        if (className.equals("com.android.systemui.statusbar.policy.Clock")) {
            String parent = parentClass.toLowerCase(Locale.ROOT);
            if (parent.contains("qs") || parent.contains("shade") || parent.contains("keyguard")) return NONE;
            return resourceName.matches("clock|clock_(left|right|center)|status_bar_clock") ? CLOCK : NONE;
        }
        if (className.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) return CARRIER;
        if (parentClass.equals("com.android.systemui.shade.carrier.ShadeCarrier")
                && resourceName.matches("carrier_text|carrier_name")) return CARRIER;
        return NONE;
    }

    public int kind(View view) {
        if (!(view instanceof TextView)) return NONE;
        String name = view.getClass().getName();
        if (name.equals("com.oplus.systemui.qs.widget.OplusQSCarrierText")) return NONE;
        if (name.equals("com.oplus.systemui.statusbar.widget.StatClock")) return CLOCK;
        if (name.equals("com.oplus.systemui.qs.widget.OplusSecondCarrierText")) return CARRIER;
        String parent = view.getParent() == null ? "" : view.getParent().getClass().getName();
        if (!name.equals("com.android.systemui.statusbar.policy.Clock")
                && !parent.equals("com.android.systemui.shade.carrier.ShadeCarrier")) return NONE;
        String resource = "";
        try { if (view.getId() != View.NO_ID) resource = view.getResources().getResourceEntryName(view.getId()); }
        catch (Exception ignored) { }
        return namedKind(name, resource, parent);
    }

    private Entry entry(TextView view) {
        synchronized (views) {
            Entry entry = views.get(view);
            if (entry == null) {
                int kind = kind(view);
                if (kind == NONE) return null;
                entry = new Entry(view, kind); views.put(view, entry);
            }
            return entry;
        }
    }

    public void attach(TextView view) {
        if (context == null) {
            context = view.getContext().getApplicationContext();
            if (context == null) context = view.getContext();
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) interactive = power.isInteractive();
        }
        Entry entry = entry(view);
        if (entry != null) { apply(view, entry, System.currentTimeMillis()); schedule(); }
    }

    public void detach(TextView view) {
        // Weak entries survive reattachment, preserving the last real carrier/clock text.
        restart();
    }

    public boolean replaces(TextView view) {
        int kind = kind(view);
        return kind == CLOCK ? clockEnabled : kind == CARRIER && !carrierMode.equals("original");
    }

    public Typeface nativeFamily() {
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                if (item.getValue().kind == CLOCK && item.getKey().isAttachedToWindow()
                        && item.getKey().isShown() && item.getValue().nativeFace != null) return item.getValue().nativeFace;
            }
        }
        return Typeface.DEFAULT;
    }

    public float styledSize(TextView view) {
        Entry entry = entry(view);
        return entry == null ? view.getTextSize() : entry.nativeSize * (entry.kind == CLOCK ? clockScale : carrierScale) / 100f;
    }

    public void beforeMeasure(TextView view) {
        Entry entry = entry(view);
        if (entry != null) applyStyle(view, entry);
    }

    public void setInteractive(boolean value) { interactive = value; if (value) refresh(); else cancel(); }

    public void configure(Bundle settings, Map<String,Integer> colors, Map<String,Boolean> alpha) {
        this.colors = colors; this.alpha = alpha;
        clockEnabled = settings.getBoolean(StatusBarSettings.CLOCK_ENABLED, false);
        clockPattern = validPattern(settings.getString(StatusBarSettings.CLOCK_PATTERN), TimeFormat.CLOCK_DEFAULT);
        carrierPattern = validPattern(settings.getString(StatusBarSettings.CARRIER_PATTERN), TimeFormat.CARRIER_DEFAULT);
        String mode = settings.getString(StatusBarSettings.CARRIER_MODE, "original");
        carrierMode = mode.equals("time") || mode.equals("text") ? mode : "original";
        String text = settings.getString(StatusBarSettings.CARRIER_TEXT, "");
        carrierText = text.replace('\n', ' ').replace('\r', ' ');
        if (carrierText.length() > TimeFormat.MAX_TEXT) carrierText = carrierText.substring(0, TimeFormat.MAX_TEXT);
        clockX = value(settings, StatusBarSettings.CLOCK_OFFSET_X, 0, -80, 80);
        clockY = value(settings, StatusBarSettings.CLOCK_OFFSET_Y, 0, -24, 24);
        carrierX = value(settings, StatusBarSettings.CARRIER_OFFSET_X, 0, -80, 80);
        carrierY = value(settings, StatusBarSettings.CARRIER_OFFSET_Y, 0, -24, 24);
        clockScale = value(settings, StatusBarSettings.CLOCK_SCALE, 100, 25, 250);
        carrierScale = value(settings, StatusBarSettings.CARRIER_SCALE, 100, 25, 250);
        clockWeight = Math.round(value(settings, StatusBarSettings.CLOCK_WEIGHT, 600, 100, 900));
        carrierWeight = Math.round(value(settings, StatusBarSettings.CARRIER_WEIGHT, 600, 100, 900));
        clockSpacing = value(settings, StatusBarSettings.CLOCK_SPACING, 0, -2, 8);
        carrierSpacing = value(settings, StatusBarSettings.CARRIER_SPACING, 0, -2, 8);
        refresh();
    }

    private static String validPattern(String pattern, String fallback) {
        return pattern != null && TimeFormat.validationError(pattern) == null ? pattern : fallback;
    }

    private static float value(Bundle settings, String key, float fallback, float min, float max) {
        Object stored = settings.get(key);
        float value = stored instanceof Number ? ((Number) stored).floatValue() : fallback;
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }

    public CharSequence nativeText(TextView view, CharSequence text) {
        if (isInternal()) return text;
        Entry entry = entry(view);
        if (entry == null) return text;
        entry.nativeText = text;
        return replacement(entry, System.currentTimeMillis());
    }

    public int nativeColor(TextView view, int color) {
        if (isInternal()) return color;
        Entry entry = entry(view);
        if (entry == null) return color;
        entry.nativeTint = color;
        return IconAppearance.color(entry.kind == CLOCK ? "clock" : "carrier", color, colors, alpha);
    }

    public CharSequence nativeDescription(TextView view, CharSequence description) {
        if (isInternal()) return description;
        Entry entry = entry(view);
        if (entry == null) return description;
        entry.nativeDescription = description;
        return replaces(view) ? replacement(entry, System.currentTimeMillis()) : description;
    }

    public void nativeSize(TextView view, float pixels) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null && pixels > 0 && !Float.isInfinite(pixels)) entry.nativeSize = pixels;
    }

    public void nativeTypeface(TextView view, Typeface typeface) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null) entry.nativeFace = typeface;
    }

    public void nativeSpacing(TextView view, float spacing) {
        if (isInternal()) return;
        Entry entry = entry(view);
        if (entry != null) entry.nativeSpacing = spacing;
    }

    private CharSequence replacement(Entry entry, long now) {
        if (entry.kind == CLOCK && clockEnabled) return TimeFormat.format(clockPattern, now);
        if (entry.kind == CARRIER && carrierMode.equals("time")) return TimeFormat.format(carrierPattern, now);
        if (entry.kind == CARRIER && carrierMode.equals("text")) return carrierText;
        return entry.nativeText;
    }

    public void beforeDraw(TextView view, Canvas canvas) {
        Entry entry = entry(view);
        if (entry == null) return;
        applyStyle(view, entry);
        float density = view.getResources().getDisplayMetrics().density;
        canvas.translate((entry.kind == CLOCK ? clockX : carrierX) * density,
                (entry.kind == CLOCK ? clockY : carrierY) * density);
    }

    private void applyStyle(TextView view, Entry entry) {
        enter();
        try {
            float size = entry.nativeSize * (entry.kind == CLOCK ? clockScale : carrierScale) / 100f;
            if (Math.abs(view.getTextSize() - size) > .001f) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            Typeface face = FontRepository.typeface(entry.nativeFace, entry.kind == CLOCK ? clockWeight : carrierWeight);
            if (view.getTypeface() != face) view.setTypeface(face);
            float spacing = entry.nativeSpacing + (entry.kind == CLOCK ? clockSpacing : carrierSpacing)
                    * view.getResources().getDisplayMetrics().density / Math.max(1f, size);
            if (Math.abs(view.getLetterSpacing() - spacing) > .0001f) view.setLetterSpacing(spacing);
            int tint = IconAppearance.color(entry.kind == CLOCK ? "clock" : "carrier", entry.nativeTint, colors, alpha);
            if (view.getCurrentTextColor() != tint) view.setTextColor(tint);
        } finally { exit(); }
    }

    private void apply(TextView view, Entry entry, long now) {
        applyStyle(view, entry);
        CharSequence text = replacement(entry, now);
        if (text == null) text = "";
        enter();
        try {
            if (!view.getText().toString().contentEquals(text)) view.setText(text);
            view.setContentDescription(replaces(view) ? text : entry.nativeDescription);
            view.invalidate();
        } finally { exit(); }
    }

    public void refresh() {
        cancel();
        long now = System.currentTimeMillis();
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                if (item.getKey().isAttachedToWindow()) apply(item.getKey(), item.getValue(), now);
            }
        }
        schedule();
    }

    private void cancel() { if (pending) handler.removeCallbacks(tick); pending = false; }
    private void restart() { cancel(); schedule(); }
    public void visibilityChanged() { restart(); }

    private void schedule() {
        if (pending || !interactive) return;
        boolean active = false, seconds = false;
        synchronized (views) {
            for (Map.Entry<TextView,Entry> item : views.entrySet()) {
                TextView view = item.getKey(); Entry entry = item.getValue();
                if (!view.isAttachedToWindow() || !view.isShown()) continue;
                if (entry.kind == CLOCK && clockEnabled) { active = true; seconds |= TimeFormat.hasSeconds(clockPattern); }
                if (entry.kind == CARRIER && carrierMode.equals("time")) { active = true; seconds |= TimeFormat.hasSeconds(carrierPattern); }
            }
        }
        if (active) { pending = true; handler.postDelayed(tick, TimeFormat.nextDelay(System.currentTimeMillis(), seconds)); }
    }
}
