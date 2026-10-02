// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Changes only the content of supported native horizontal batteries. */
public final class BatteryControls {
    private final Handler handler;
    private final BatteryAppearance appearance;
    private final PuiBatteryStyle puiStyle;
    private final BatteryTextStyle textStyle=new BatteryTextStyle();
    private final Class<?> horizontal;
    private final Method getStyle, getCharge, getLevel, getChargeId, bodyRect, paintMode;
    private final Method highContrast;
    private final Field chargeVisible, chargeId, percentIn, percentPaint;
    private final Map<View, Entry> owners = new WeakHashMap<>();
    private final Map<Drawable, Entry> drawables = new WeakHashMap<>();
    private boolean enabled = true, interactive = true;
    private float hold = 3f, fade = 1f;
    private boolean scheduled;
    private long scheduledAt;
    private final Runnable tick = () -> {
        scheduled = false;
        for (Entry entry : owners.values()) {
            if (canAnimate(entry)) {
                Drawable drawable = entry.drawable.get();
                if (drawable != null) drawable.invalidateSelf();
            }
        }
        schedule();
    };

    private static final class Entry {
        final WeakReference<View> owner;
        WeakReference<Drawable> drawable = new WeakReference<>(null);
        WeakReference<View> external = new WeakReference<>(null);
        final ChargeCycle cycle = new ChargeCycle();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect ink = new Rect();
        final ChargeBolt bolt = new ChargeBolt();
        int lastLevel = -1;
        Locale locale;
        String levelText;
        boolean nativeVisible, hiddenByModule, nativeCharging;
        Entry(View owner) { this.owner = new WeakReference<>(owner); }
    }

    public BatteryControls(Handler handler, Class<?> meter, Class<?> horizontal, Class<?> charge) throws Exception {
        this.handler = handler;
        this.horizontal = horizontal;
        appearance = new BatteryAppearance(horizontal.getSuperclass());
        puiStyle = new PuiBatteryStyle(horizontal);
        getStyle = meter.getMethod("getBatteryStyleDrawable");
        getCharge = meter.getMethod("getBatteryCharge");
        getLevel = horizontal.getMethod("getBatteryLevel");
        getChargeId = horizontal.getMethod("getChargeIconId");
        bodyRect = horizontal.getMethod("getBodyRectForLayout", RectF.class);
        paintMode = horizontal.getMethod("updatePaintXfermode", Paint.class);
        chargeVisible = field(charge, "isVisible");
        chargeId = field(charge, "iconId");
        percentIn = field(horizontal, "isShowPercentIn");
        percentPaint = field(horizontal, "percentInPaint");
        Method contrast;
        try { contrast = Canvas.class.getMethod("isHighContrastTextEnabled"); }
        catch (NoSuchMethodException unavailable) { contrast = null; }
        highContrast = contrast;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    public void configure(boolean enabled, float hold, float fade) {
        this.enabled = enabled;
        this.hold = hold;
        this.fade = fade;
        for (View owner : owners.keySet().toArray(new View[0])) sync(owner);
        reschedule();
    }

    public void configure(Bundle settings,Map<String,Integer> colors,Map<String,Boolean> alpha) {
        textStyle.configure(settings);
        appearance.configure(settings,colors,alpha);
        puiStyle.configure(settings);
        configure(FeatureOptions.from(settings).effective("battery",StatusBarSettings.BATTERY_CHARGE_INSIDE),
                setting(settings,StatusBarSettings.BATTERY_HOLD,3f),setting(settings,StatusBarSettings.BATTERY_FADE,1f));
    }

    private static float setting(Bundle settings,String key,float fallback) {
        return NumericPolicy.setting(key,settings.get(key),fallback);
    }

    public Object[] nativeColors(Drawable drawable,int progress,int background,int outline) {
        if(appearance.isApplying()||!horizontal.isInstance(drawable))return new Object[]{progress,background,outline};
        Entry entry=drawables.get(drawable);
        return appearance.nativeColors(drawable,progress,background,outline,entry!=null&&entry.nativeCharging);
    }

    public void beforeDraw(View owner,Canvas canvas) throws Exception {
        Entry entry=owners.get(owner);
        if(entry==null)return;
        appearance.beforeDraw(owner,canvas);
    }

    public void prepareDraw(Drawable drawable) throws Exception {
        if(horizontal.isInstance(drawable))puiStyle.prepare(drawable);
    }

    public void attach(View owner) {
        PowerManager power = (PowerManager) owner.getContext().getSystemService("power");
        if (power != null) interactive = power.isInteractive();
        sync(owner);
    }

    public void detach(View owner) {
        appearance.detach(owner);
        Entry entry = owners.remove(owner);
        if (entry != null) {
            Drawable drawable = entry.drawable.get();
            puiStyle.detach(owner,drawable);
            if (drawable != null) drawables.remove(drawable);
            restoreExternal(entry);
        }
        reschedule();
    }

    public void sync(View owner) {
        try { sync(owner, getCharge.invoke(owner), null); }
        catch (Exception ignored) { }
    }

    /** Binder supplies the current model before its native owner field is updated. */
    public void sync(View owner, Object charge, View external) {
        try {
            Entry entry = owners.get(owner);
            if (entry == null) { entry = new Entry(owner); owners.put(owner, entry); }
            entry.cycle.configure(hold, fade);
            Object candidate = getStyle.invoke(owner);
            Drawable drawable = horizontal.isInstance(candidate) ? (Drawable) candidate : null;
            Drawable previous = entry.drawable.get();
            if (previous != drawable) {
                if(previous!=null)puiStyle.restore(previous);
                if (previous != null) drawables.remove(previous);
                entry.drawable = new WeakReference<>(drawable);
                if (drawable != null) drawables.put(drawable, entry);
            }
            if (external != null) entry.external = new WeakReference<>(external);
            if (entry.external.get() == null) {
                int id = owner.getResources().getIdentifier("battery_charge", "id", "com.android.systemui");
                if (id != 0) entry.external = new WeakReference<>(owner.findViewById(id));
            }
            entry.nativeVisible = charge != null && chargeVisible.getBoolean(charge);
            int level = drawable != null ? (Integer) getLevel.invoke(drawable) : -1;
            boolean inside = drawable != null && percentIn.getBoolean(drawable) && level >= 0 && level <= 100;
            entry.nativeCharging=(entry.nativeVisible&&chargeId.getInt(charge)>0)
                    ||(drawable!=null&&((Integer)getChargeId.invoke(drawable))>0);
            boolean charging = inside && entry.nativeCharging;
            entry.cycle.setCharging(charging, SystemClock.uptimeMillis());
            appearance.apply(drawable,entry.nativeCharging);
            if(drawable!=null){puiStyle.prepare(drawable);puiStyle.updateView(owner,drawable);}
            appearance.updateView(owner);
            View outside = entry.external.get();
            if (enabled && charging) {
                if (outside != null) {
                    if(outside.getVisibility()!=View.GONE)outside.setVisibility(View.GONE);
                    entry.hiddenByModule = true;
                }
            } else restoreExternal(entry);
            if (drawable != null) drawable.invalidateSelf();
            owner.invalidate();
            schedule();
        } catch (Exception ignored) {
            Entry entry = owners.get(owner);
            if (entry != null) { entry.cycle.setCharging(false, SystemClock.uptimeMillis()); restoreExternal(entry); }
            reschedule();
        }
    }

    private void restoreExternal(Entry entry) {
        View outside = entry.external.get();
        if (entry.hiddenByModule && outside != null) outside.setVisibility(entry.nativeVisible ? View.VISIBLE : View.GONE);
        entry.hiddenByModule = false;
    }

    public boolean isOwner(View view) { return owners.containsKey(view); }
    public void visibilityChanged(View view) {
        if (owners.containsKey(view)) { reschedule(); view.invalidate(); }
    }

    public void setInteractive(boolean next) {
        interactive = next;
        reschedule();
        if (next) for (Entry entry : owners.values()) {
            Drawable drawable = entry.drawable.get();
            if (drawable != null) drawable.invalidateSelf();
        }
    }

    private boolean canAnimate(Entry entry) {
        View owner = entry.owner.get();
        return enabled && interactive && entry.cycle.isCharging() && entry.drawable.get() != null
                && owner != null && owner.isAttachedToWindow() && owner.isShown()
                && owner.getWindowVisibility() == View.VISIBLE;
    }

    private void reschedule() {
        handler.removeCallbacks(tick);
        scheduled = false;
        schedule();
    }

    private void schedule() {
        long now = SystemClock.uptimeMillis(), delay = Long.MAX_VALUE;
        for (Entry entry : owners.values()) if (canAnimate(entry)) delay = Math.min(delay, entry.cycle.nextDelay(now));
        if (delay == Long.MAX_VALUE) {
            if (scheduled) { handler.removeCallbacks(tick); scheduled = false; }
            return;
        }
        long at = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + Math.max(1, delay);
        if (scheduled && scheduledAt <= at) return;
        handler.removeCallbacks(tick);
        scheduled = true; scheduledAt = at;
        handler.postDelayed(tick, Math.max(1, delay));
    }

    /** Runs inside the native battery layer, so opacity and native cutout colors stay correct. */
    public boolean drawContent(Drawable drawable, Canvas canvas, RectF content) throws Exception {
        Entry entry = drawables.get(drawable);
        if (entry == null || !percentIn.getBoolean(drawable)) return false;
        boolean animate=enabled&&entry.cycle.isCharging();
        boolean customText=appearance.hasCustom("battery_text");
        boolean customStyle=textStyle.enabled();
        if(!animate&&!customText&&!customStyle)return false;
        int level = (Integer) getLevel.invoke(drawable);
        if (level < 0 || level > 100) return false;
        RectF body = (RectF) bodyRect.invoke(drawable, content);
        if (body.width() <= 0 || body.height() <= 0) return false;
        Paint paint = entry.paint;
        paint.set((Paint) percentPaint.get(drawable));
        paintMode.invoke(drawable, paint);
        if (highContrast != null && Boolean.TRUE.equals(highContrast.invoke(canvas))) {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
            paint.setColor(appearance.nativeOutline(drawable));
        }
        int nativeAlpha = paint.getAlpha();
        int contentColor=appearance.contentTint("battery_text",drawable,paint.getColor());
        if(customText) {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));paint.setColor(contentColor);
            nativeAlpha=paint.getAlpha();
        }
        float bolt = animate?(interactive?entry.cycle.boltOpacity(SystemClock.uptimeMillis()):1f):0f;
        int saved = canvas.save();
        try {
            if (bolt < 1f) {
                int textSaved = canvas.save();
                canvas.clipRect(body);
                Locale locale = Locale.getDefault();
                if (entry.lastLevel != level || !locale.equals(entry.locale)) {
                    entry.lastLevel = level; entry.locale = locale;
                    entry.levelText = NumberFormat.getIntegerInstance(locale).format(level);
                }
                String text = entry.levelText;
                View owner=entry.owner.get();
                float density=owner==null?1f:owner.getResources().getDisplayMetrics().density;
                if(customStyle&&owner!=null)textStyle.apply(paint,owner.getResources().getDisplayMetrics());
                paint.setTextAlign(Paint.Align.LEFT);
                paint.getTextBounds(text, 0, text.length(), entry.ink);
                float fit = Math.min(1f, Math.min(body.width() * .84f / Math.max(1, entry.ink.width()),
                        body.height() * .68f / Math.max(1, entry.ink.height())));
                if (!customStyle && fit < 1f) {
                    paint.setTextSize(paint.getTextSize() * fit);
                    paint.getTextBounds(text, 0, text.length(), entry.ink);
                }
                paint.setAlpha(Math.round(nativeAlpha * (1f - bolt)));
                canvas.drawText(text, body.centerX() - entry.ink.exactCenterX()+textStyle.offsetX(density),
                        body.centerY() - entry.ink.exactCenterY()+textStyle.offsetY(density), paint);
                canvas.restoreToCount(textSaved);
            }
            if (bolt > 0f) {
                entry.bolt.draw(canvas, body, drawable.getBounds(), bolt,
                        appearance.contentTint("battery_bolt",drawable,appearance.nativeOutline(drawable)));
            }
        } finally { canvas.restoreToCount(saved); }
        schedule();
        return true;
    }
}
