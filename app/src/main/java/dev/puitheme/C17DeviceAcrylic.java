// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** The SDK's five inner card bases, never the outer card, icons or spotlight foregrounds. */
final class C17DeviceAcrylic {
    private static final String PACKAGE = "com.oplus.deviceplugin.sdk.ui.view.separatecardview.";
    private static final String[][] BODIES = {
            {"RectangleDeviceCardView", "rectangleCoLayout"},
            {"SquareDeviceCardView", "square_device_constraintlayout"},
            {"NoDeviceEntranceCardView", "no_device_constraintLayout"},
            {"RectangleEntranceCardView", "rectangle_entrance_constraintLayout"},
            {"SquareEntranceCardView", "square_entrance_constraintLayout"}};
    private final Map<View, Source> cards = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, String> bodyNames = new ConcurrentHashMap<>();
    private final Map<Class<?>, GradientAccess> gradients = new ConcurrentHashMap<>();

    static final class Source {
        final WeakReference<View> card, body;
        final WeakReference<Drawable> background;
        Source(View card, View body, Drawable background) {
            this.card = new WeakReference<>(card); this.body = new WeakReference<>(body);
            this.background = new WeakReference<>(background);
        }
        boolean matches(View expectedCard) {
            View nativeBody = body.get(); Drawable base = background.get();
            return card.get() == expectedCard && expectedCard != null && nativeBody != null && base != null
                    && nativeBody.isAttachedToWindow() && expectedCard.isAttachedToWindow()
                    && nativeBody.getBackground() == base && ancestor(nativeBody, expectedCard);
        }
        boolean sameBase(Source other) {
            return other != null && card.get() == other.card.get() && body.get() == other.body.get()
                    && background.get() == other.background.get();
        }
    }

    void refresh(View card) {
        Source current = resolve(card);
        if (current == null) cards.remove(card); else cards.put(card, current);
    }

    Source exactBody(View card, View nativeView) {
        Source current = source(card);
        return current != null && current.body.get() == nativeView ? current : null;
    }

    Source drawableSource(Drawable drawable) {
        if (!(drawable instanceof GradientDrawable)) return null;
        // A GradientDrawable is admitted only as the exact SDK inner View background.
        // Do not walk arbitrary wrappers, themed foregrounds or an icon's drawable callback.
        Drawable.Callback callback = drawable.getCallback();
        if (!(callback instanceof View)) return null;
        View body = (View) callback, card = body;
        for (int depth = 0; card != null && depth < 12; depth++) {
            if (!bodyName(card).isEmpty()) {
                Source current = source(card);
                return current != null && current.body.get() == body && current.background.get() == drawable ? current : null;
            }
            ViewParent parent = card.getParent(); card = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private Source source(View card) {
        Source current = cards.get(card);
        if (current != null && current.matches(card)) return current;
        current = resolve(card);
        if (current != null) cards.put(card, current); else cards.remove(card);
        return current;
    }

    private Source resolve(View card) {
        if (card == null || !card.isAttachedToWindow()) return null;
        String name = bodyName(card); if (name.isEmpty()) return null;
        try {
            int id = card.getResources().getIdentifier(name, "id", "com.android.systemui");
            View body = id == 0 ? null : card.findViewById(id);
            if (body == null || !body.isAttachedToWindow() || !ancestor(body, card)
                    || !name.equals(body.getResources().getResourceEntryName(body.getId()))
                    || !"com.android.systemui".equals(body.getResources().getResourcePackageName(body.getId()))) return null;
            Drawable background = body.getBackground();
            return background == null ? null : new Source(card, body, background);
        } catch (RuntimeException unknownLayout) { return null; }
    }

    private String bodyName(View card) {
        if (card == null) return "";
        Class<?> type = card.getClass(); String cached = bodyNames.get(type); if (cached != null) return cached;
        if (bodyNames.size() >= 256) return "";
        String result = "";
        for (Class<?> current = type; current != null && result.isEmpty(); current = current.getSuperclass())
            for (String[] body : BODIES) if ((PACKAGE + body[0]).equals(current.getName())) { result = body[1]; break; }
        bodyNames.putIfAbsent(type, result); return result;
    }

    /** Native GradientDrawable draw computes its own drawable alpha and shape after these paint values. */
    Object drawGradient(Drawable drawable, Canvas canvas, int color, boolean tint, C17HighlightRemoval.ContentDraw nativeDraw) throws Throwable {
        GradientAccess access = gradients.get(drawable.getClass());
        if (access == null) {
            GradientAccess created = new GradientAccess(drawable.getClass()), raced = gradients.putIfAbsent(drawable.getClass(), created);
            access = raced == null ? created : raced;
        }
        Paint fill = null, stroke = null;
        try {
            if (access.valid()) {
                access.ensure.invoke(drawable);
                Object value = access.fill.get(drawable), edge = access.stroke == null ? null : access.stroke.get(drawable);
                if (value instanceof Paint) { fill = (Paint) value; stroke = edge instanceof Paint ? (Paint) edge : null; }
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) { fill = null; }
        if (fill == null) return nativeDraw.draw(canvas);
        int previousColor = fill.getColor(), previousStrokeAlpha = stroke == null ? 0 : stroke.getAlpha();
        Shader previousShader = fill.getShader();
        // Alter the draw paint, not GradientState/color lists. No invalidation or new allocation every frame.
        fill.setShader(null); fill.setColor(tint ? mixNativeTint(previousColor, color) : previousColor);
        if (stroke != null) stroke.setAlpha(0);
        try { return nativeDraw.draw(canvas); }
        finally {
            fill.setColor(previousColor); fill.setShader(previousShader);
            if (stroke != null) stroke.setAlpha(previousStrokeAlpha);
        }
    }

    /** The same RGB interpolation as acrylic AGSL; the native paint alpha owns its transition. */
    static int mixNativeTint(int original, int tint) {
        float amount = (tint >>> 24) / 255f;
        int red = Math.round(((original >>> 16) & 255) * (1f - amount) + ((tint >>> 16) & 255) * amount);
        int green = Math.round(((original >>> 8) & 255) * (1f - amount) + ((tint >>> 8) & 255) * amount);
        int blue = Math.round((original & 255) * (1f - amount) + (tint & 255) * amount);
        return (original & 0xff000000) | (red << 16) | (green << 8) | blue;
    }

    void detach(View card) { cards.remove(card); }
    void clear() { cards.clear(); bodyNames.clear(); gradients.clear(); }

    private static boolean ancestor(View child, View expected) {
        for (int depth = 0; child != null && depth < 12; depth++) {
            if (child == expected) return true;
            ViewParent parent = child.getParent(); child = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    private static final class GradientAccess {
        final Field fill, stroke; final Method ensure;
        GradientAccess(Class<?> type) { fill = field(type, "mFillPaint"); stroke = field(type, "mStrokePaint"); ensure = method(type, "ensureValidRect"); }
        boolean valid() { return fill != null && ensure != null; }
    }
    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Field result = current.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException absent) { } catch (RuntimeException denied) { return null; }
        return null;
    }
    private static Method method(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try {
            Method result = current.getDeclaredMethod(name); result.setAccessible(true); return result;
        } catch (NoSuchMethodException absent) { } catch (RuntimeException denied) { return null; }
        return null;
    }
}
