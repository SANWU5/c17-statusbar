// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Bitmap;

/** Bounded one-off new-artwork sampling. No blur, canvas, frame polling or full-size readback. */
final class QsMediaColor {
    private static final int HARDWARE_EDGE = 8;
    private QsMediaColor() { }

    static int sample(Bitmap source) {
        if (source == null || source.isRecycled()) throw new IllegalArgumentException("Media artwork unavailable");
        Bitmap scaled = null, readable = source;
        try {
            if (source.getConfig() == Bitmap.Config.HARDWARE) {
                float scale = Math.min(1f, HARDWARE_EDGE / (float)Math.max(source.getWidth(), source.getHeight()));
                scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)), true);
                readable = scaled.getConfig() == Bitmap.Config.HARDWARE
                        ? scaled.copy(Bitmap.Config.ARGB_8888, false) : scaled;
                if (readable == null) throw new IllegalArgumentException("Media artwork sample unavailable");
            }
            int width = readable.getWidth(), height = readable.getHeight();
            int[] pixel = new int[1];
            long red = 0L, green = 0L, blue = 0L, alpha = 0L;
            // 3x3 cell centers avoid artwork borders while keeping the work strictly bounded.
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                readable.getPixels(pixel, 0, 1, Math.min(width - 1, (2*x+1)*width/6),
                        Math.min(height - 1, (2*y+1)*height/6), 1, 1);
                int color = pixel[0], a = color >>> 24;
                alpha += a;
                red += ((color >>> 16) & 255) * a;
                green += ((color >>> 8) & 255) * a;
                blue += (color & 255) * a;
            }
            // Transparent artwork has no representative fill; preserve transparency, never guess gray.
            return alpha == 0L ? 0 : 0xff000000 | ((int)(red/alpha) << 16)
                    | ((int)(green/alpha) << 8) | (int)(blue/alpha);
        } finally {
            if (readable != source && readable != scaled && readable != null) readable.recycle();
            if (scaled != source && scaled != null) scaled.recycle();
        }
    }
}
