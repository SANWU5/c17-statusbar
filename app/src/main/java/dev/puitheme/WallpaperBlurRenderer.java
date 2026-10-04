// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;

/** Cached GPU wallpaper content, not a blur of a keyguard/notification window.
 * The source is a hardware buffer of the wallpaper subtree only. No getPixels,
 * software blur, bitmap scaling, timer or foreground capture is used.
 */
final class WallpaperBlurRenderer {
    private final RenderNode blurred = new RenderNode("C17 isolated wallpaper blur");
    private final RenderNode feathered = new RenderNode("C17 continuous wallpaper feather");
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint feather = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int width, height;

    void record(Bitmap source, int width, LockscreenBlurGeometry.Plan plan, int radius, int color) {
        if (width <= 0 || plan.end <= 0 || (radius > 0 && (source == null || source.isRecycled())))
            throw new IllegalArgumentException("Invalid isolated wallpaper source");
        this.width = width; this.height = plan.end;
        blurred.discardDisplayList(); feathered.discardDisplayList();
        blurred.setRenderEffect(null);
        if (radius > 0) {
            // Extra pixels below the output feather prevent a clamped bottom edge
            // becoming visible. Both nodes contain wallpaper pixels exclusively.
            int sourceHeight = source.getHeight();
            blurred.setPosition(0, 0, width, sourceHeight); blurred.setClipToBounds(false);
            blurred.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            RecordingCanvas c = blurred.beginRecording(width, sourceHeight);
            try { c.drawBitmap(source, null, new RectF(0, 0, width, sourceHeight), image); }
            finally { blurred.endRecording(); }
        }
        feathered.setPosition(0, 0, width, height); feathered.setClipToBounds(true);
        feather.setShader(gradient(plan, 0xffffffff));
        feather.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(gradient(plan, color));
        RecordingCanvas c = feathered.beginRecording(width, height);
        try {
            if (radius > 0) {
                int save = c.saveLayer(0, 0, width, height, null);
                try {
                    c.drawRenderNode(blurred);
                    c.drawRect(0, 0, width, height, feather);
                } finally { c.restoreToCount(save); }
            }
            if ((color >>> 24) != 0) c.drawRect(0, 0, width, height, mask);
        } finally { feathered.endRecording(); }
    }

    /** Smoothstep is sampled into a continuous native shader, NOT crop/alpha bands. */
    static LinearGradient gradient(LockscreenBlurGeometry.Plan plan, int color) {
        int count = plan.end > plan.core ? 34 : 2;
        int[] colors = new int[count]; float[] positions = new float[count];
        int alpha = color >>> 24, rgb = color & 0xffffff;
        colors[0] = color; positions[0] = 0f;
        if (count == 2) { colors[1] = color; positions[1] = 1f; }
        else for (int i = 1; i < count; i++) {
            float y = plan.core + (plan.end - plan.core) * (i - 1f) / (count - 2f);
            positions[i] = y / plan.end;
            colors[i] = (Math.round(alpha * plan.strength(y)) << 24) | rgb;
        }
        return new LinearGradient(0f, 0f, 0f, plan.end, colors, positions, Shader.TileMode.CLAMP);
    }
    void draw(Canvas canvas) {
        if (canvas == null || !canvas.isHardwareAccelerated()) throw new IllegalStateException("Hardware wallpaper canvas required");
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        canvas.drawRenderNode(feathered);
    }
    void release() {
        blurred.discardDisplayList(); feathered.discardDisplayList(); blurred.setRenderEffect(null);
        feather.setShader(null); mask.setShader(null); width = height = 0;
    }
}
