// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.Map;

/** One color policy for native views, drawables and Compose, using each surface's actual tint. */
public final class IconAppearance {
    private IconAppearance() { }

    public static int color(String item, int nativeTint, Map<String,Integer> colors,
            Map<String,Boolean> customAlpha) {
        String lightKey = item + "_color_light", darkKey = item + "_color_dark";
        int light = colors.containsKey(lightKey) ? colors.get(lightKey) : 0xff000000;
        int dark = colors.containsKey(darkKey) ? colors.get(darkKey) : 0xffffffff;
        boolean lightAlpha = Boolean.TRUE.equals(customAlpha.get(lightKey));
        boolean darkAlpha = Boolean.TRUE.equals(customAlpha.get(darkKey));
        // Defaults inherit the complete native tint, including animation and regional colors.
        if ((light & 0xffffff) == 0 && (dark & 0xffffff) == 0xffffff
                && !lightAlpha && !darkAlpha) return nativeTint;
        float white = (.2126f * ((nativeTint >>> 16) & 255)
                + .7152f * ((nativeTint >>> 8) & 255) + .0722f * (nativeTint & 255)) / 255f;
        white = Math.max(0f, Math.min(1f, white));
        int nativeAlpha = nativeTint >>> 24;
        int alpha = blend(lightAlpha ? light >>> 24 : nativeAlpha,
                darkAlpha ? dark >>> 24 : nativeAlpha, white);
        return (alpha << 24) | (blend((light >>> 16) & 255, (dark >>> 16) & 255, white) << 16)
                | (blend((light >>> 8) & 255, (dark >>> 8) & 255, white) << 8)
                | blend(light & 255, dark & 255, white);
    }

    private static int blend(int start, int end, float fraction) {
        return Math.round(start + (end - start) * fraction);
    }
}
