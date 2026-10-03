// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

/** Pure geometry. Panel expansion reveals the clock; native list scrolling collapses it. */
final class NotificationBigClockModel {
    private NotificationBigClockModel() { }

    static final class Frame {
        final float progress, clockTop, clockHeight, dateTop, reservedBottom, textSizeRatio, collapseDistance;
        final float entryTranslation, entryAlpha;
        final int weight;
        Frame(float progress, float top, float height, float dateTop, float reservedBottom,
                float textRatio, float collapseDistance, float entryTranslation, float entryAlpha, int weight) {
            this.progress = progress; this.clockTop = top; this.clockHeight = height;
            this.dateTop = dateTop; this.reservedBottom = reservedBottom;
            this.textSizeRatio = textRatio; this.entryTranslation = entryTranslation;
            this.collapseDistance = collapseDistance;
            this.entryAlpha = entryAlpha; this.weight = weight;
        }
    }

    static boolean eligible(boolean enabled, boolean portrait, int barState, boolean qsExpanded,
            float panelFraction) {
        return enabled && portrait && barState == 0 && !qsExpanded && finite(panelFraction)
                && panelFraction > 0f;
    }

    static boolean eligible(boolean enabled, int orientation, int barState, boolean qsExpanded, float panelFraction) {
        return eligible(enabled, orientation == 1 || orientation == 2, barState, qsExpanded, panelFraction);
    }

    /** The landscape header reserves only a compact top band; native notification sizes stay intact. */
    static Frame landscapeMeasured(float density, float safeTop, float expandedHeight,
            float compactHeight, float dateHeight, float dateGap, float notificationGap,
            float offsetY, float compactOffsetY, float dateOffsetY, float weight, int scrollY, float panelFraction) {
        float d = bounded(density, 1f, .5f, 8f), safe = Math.max(0f, NumericPolicy.drawPixels(safeTop));
        float date = Math.max(0f, NumericPolicy.drawPixels(dateHeight));
        float initialDate = safe + 4f * d + NumericPolicy.pixels(offsetY, d);
        float initialTop = initialDate + date + (date > 0f ? Math.min(6f * d, Math.max(0f, NumericPolicy.pixels(dateGap, d))) : 0f);
        float expanded = Math.max(0f, NumericPolicy.drawPixels(expandedHeight));
        float initialBottom = Math.max(initialTop + expanded, initialDate + NumericPolicy.pixels(dateOffsetY, d) + date);
        float distance = Math.max(1f, initialBottom + 8f * d);
        float scroll = Math.max(0, scrollY);
        float p = clamp(scroll / distance, 0f, 1f);
        // Every pixel of native list scroll moves the landscape header by exactly one pixel.
        // Interpolating a distant off-screen target over a fixed 64dp range accelerates the
        // clock ahead of its notifications. Keep glyph size and intrinsic reservation stable.
        return new Frame(p, initialTop - scroll, expanded,
                initialDate - scroll + NumericPolicy.pixels(dateOffsetY, d),
                initialBottom + Math.max(0f, NumericPolicy.pixels(notificationGap, d)), 1f, distance, 0f,
                clamp(bounded(panelFraction, 0f, 0f, 1f) / .3f, 0f, 1f), Math.round(bounded(weight, 600f, 1f, 1000f)));
    }

    static float landscapeClockBudget(float height, float density) {
        float d = bounded(density, 1f, .5f, 8f);
        return Math.max(0f, Math.min(72f * d, Math.max(0f, NumericPolicy.drawPixels(height)) * .2f));
    }
    static float scrollFade(float progress) {
        float p = clamp(finite(progress) ? progress : 0f, 0f, 1f);
        return p * p * (3f - 2f * p);
    }

    /** Sliding out is intrinsic landscape behavior, independent of optional entry blur. */
    static float landscapeHeaderAlpha(float entryAlpha, float progress) {
        return bounded(entryAlpha, 0f, 0f, 1f) * (1f - scrollFade(progress));
    }
    /** Native HGHT changes shape at a fixed em width on both panel and list gestures. */
    static float nativeHeightProgress(float scrollProgress,float panelFraction) {
        float scroll=bounded(scrollProgress,0f,0f,1f),panel=bounded(panelFraction,0f,0f,1f);
        return 1f-(1f-scroll)*panel;
    }
    static Frame withNativePanelHeight(Frame scroll,float expanded,float compact,float panelFraction) {
        float progress=nativeHeightProgress(scroll.progress,panelFraction);
        return new Frame(scroll.progress,scroll.clockTop,lerp(expanded,compact,progress),scroll.dateTop,
                scroll.reservedBottom,scroll.textSizeRatio,scroll.collapseDistance,scroll.entryTranslation,
                scroll.entryAlpha,scroll.weight);
    }

    /** Measured text is never stretched. All parts use the same absolute native scroll progress. */
    static Frame measured(float panelHeight, float density, float safeTop, float expandedHeight,
            float compactHeight, float dateHeight, float dateGap, float notificationGap,
            float offsetY, float compactOffsetY, float dateOffsetY, float weight,
            float compactWeight, int scrollY, float overDistance, float panelFraction) {
        float d = bounded(density, 1f, .5f, 8f);
        float h = Math.max(1f, NumericPolicy.drawPixels(panelHeight));
        float topInset = Math.max(0f, NumericPolicy.drawPixels(safeTop));
        float date = Math.max(0f, NumericPolicy.drawPixels(dateHeight));
        float dateSpacing = date > 0f ? Math.max(0f, NumericPolicy.pixels(dateGap, d)) : 0f;
        float expanded = Math.max(0f, NumericPolicy.drawPixels(expandedHeight));
        float compact = Math.max(0f, NumericPolicy.drawPixels(compactHeight));
        float shift = NumericPolicy.pixels(offsetY, d);
        float compactShift = shift + NumericPolicy.pixels(compactOffsetY, d);
        float dateShift = NumericPolicy.pixels(dateOffsetY, d);
        float initialDateBase = Math.max(topInset + 12f * d, h * .095f) + shift;
        float finalDateBase = topInset + 6f * d + compactShift;
        float initialDateTop = initialDateBase + dateShift;
        float finalDateTop = finalDateBase + dateShift;
        float initialTop = initialDateBase + date + dateSpacing;
        float finalTop = finalDateBase + date + dateSpacing;
        float gap = Math.max(0f, NumericPolicy.pixels(notificationGap, d));
        float initialBottom = Math.max(initialTop + expanded, initialDateTop + date);
        float finalBottom = Math.max(finalTop + compact, finalDateTop + date);
        // Fixed intrinsic space means list scroll is never counted twice during collapse.
        float reserved = Math.max(topInset, initialBottom + gap);
        float range = Math.max(64f * d, Math.abs(initialBottom - finalBottom));
        float progress = clamp(Math.max(0, scrollY) / range, 0f, 1f);
        float stretch = Math.min(Math.max(0f, overDistance) * .35f, 32f * d) * (1f - progress);
        float top = lerp(initialTop, finalTop, progress) + stretch;
        float clockHeight = lerp(expanded, compact, progress);
        float currentDateTop = lerp(initialDateTop, finalDateTop, progress) + stretch;
        float fraction = bounded(panelFraction, 0f, 0f, 1f);
        return new Frame(progress, top, clockHeight, currentDateTop, reserved, 1f,
                // The native parent already follows the panel gesture. A second reserve-sized
                // translation ejects a compact clock long before the backdrop finishes closing.
                range, 0f, clamp(fraction / .3f, 0f, 1f),
                Math.round(bounded(weight, 600f, 1f, 1000f)));
    }

    static Frame calculate(float screenHeight, float density, float scale, float compactScale,
            float weight, float compactWeight, float offsetY, int scrollY, float overDistance,
            float panelFraction) {
        float d = bounded(density, 1f, .5f, 8f);
        float h = bounded(screenHeight, 1280f, 320f, 12000f);
        float largeHeight = bounded(h * .27f * bounded(scale, 100f, 40f, 180f) / 100f,
                h * .27f, Math.min(140f * d, h * .27f), h * .52f);
        float smallHeight = largeHeight * bounded(compactScale, 36f, 15f, 90f) / 100f;
        float offset = bounded(offsetY, 0f, -80f, 160f) * d;
        float largeTop = h * .15f + offset;
        float smallTop = Math.max(32f * d, h * .045f) + offset;
        float range = Math.max(80f * d, largeTop + largeHeight - smallTop - smallHeight);
        float progress = clamp(Math.max(0, scrollY) / range, 0f, 1f);
        float stretch = bounded(overDistance, 0f, 0f, h * .5f) * .55f * (1f - progress);
        stretch = Math.min(stretch, h * .15f);
        float top = lerp(largeTop, smallTop, progress);
        float height = lerp(largeHeight, smallHeight, progress) + stretch;
        float reserved = largeTop + largeHeight + 18f * d;
        float fraction = bounded(panelFraction, 0f, 0f, 1f);
        return new Frame(progress, top, height, top - 28f * d, reserved,
                lerp(1f, .78f, progress), range, 0f,
                clamp(fraction / .3f, 0f, 1f),
                Math.round(bounded(weight, 600f, 1f, 1000f)));
    }

    static float bounded(float value, float fallback, float min, float max) {
        return clamp(finite(value) ? value : fallback, min, max);
    }
    static float notificationClipTop(Frame frame, float density) {
        return frame.clockTop + frame.clockHeight + 18f * bounded(density, 1f, .5f, 8f)
                + frame.entryTranslation;
    }
    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float lerp(float from, float to, float progress) { return from + (to - from) * progress; }
}
