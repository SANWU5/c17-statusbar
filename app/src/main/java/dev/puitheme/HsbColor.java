// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import java.util.Locale;

/** HSB/HSV editing keeps hue for greys and keeps opacity policy separate from RGB. */
public final class HsbColor {
    private double hue, saturation, brightness, opacity = 100;
    private boolean customAlpha;

    public HsbColor(int color, boolean customAlpha) {
        setColor(color, customAlpha);
    }

    public double hue() { return hue; }
    public double saturation() { return saturation; }
    public double brightness() { return brightness; }
    public double opacity() { return opacity; }
    public boolean customAlpha() { return customAlpha; }

    public void setHsb(double hue, double saturation, double brightness) {
        range(hue, 360); range(saturation, 100); range(brightness, 100);
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
    }

    public void setOpacity(double opacity) {
        this.opacity = range(opacity, 100);
        customAlpha = true;
    }

    public void followSystem(boolean follow) { customAlpha = !follow; }

    public void setColor(int color, boolean customAlpha) {
        setRgb(color);
        opacity = customAlpha ? ((color >>> 24) * 100d / 255) : 100;
        this.customAlpha = customAlpha;
    }

    /** Presets select a colour without silently changing the chosen opacity. */
    public void setRgb(int color) {
        double r = ((color >> 16) & 255) / 255d;
        double g = ((color >> 8) & 255) / 255d;
        double b = (color & 255) / 255d;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        if (delta > 0) {
            double sector = max == r ? (g - b) / delta : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
            hue = (sector * 60 + 360) % 360;
        }
        saturation = max == 0 ? 0 : delta * 100 / max;
        brightness = max * 100;
    }

    public int argb() {
        double h = (hue % 360) / 60;
        double v = brightness / 100, c = v * saturation / 100;
        double x = c * (1 - Math.abs(h % 2 - 1)), m = v - c;
        double r = 0, g = 0, b = 0;
        switch ((int) h) {
            case 0: r = c; g = x; break;
            case 1: r = x; g = c; break;
            case 2: g = c; b = x; break;
            case 3: g = x; b = c; break;
            case 4: r = x; b = c; break;
            default: r = c; b = x; break;
        }
        int a = customAlpha ? channel(opacity / 100) : 255;
        return a << 24 | channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
    }

    public String hex() {
        return String.format(Locale.ROOT, customAlpha ? "#%08X" : "#%06X", customAlpha ? argb() : argb() & 0xffffff);
    }

    /** Six digits retain system opacity; eight digits explicitly select alpha, including FF. */
    public boolean readHex(String value) {
        if (value == null) return false;
        value = value.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return false;
        long parsed = Long.parseLong(value, 16);
        boolean alpha = value.length() == 8;
        setColor((int) (alpha ? parsed : parsed | 0xff000000L), alpha);
        return true;
    }

    private static double range(double value, double maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > maximum)
            throw new IllegalArgumentException("HSB value outside range");
        return value;
    }

    private static int channel(double value) { return Math.max(0, Math.min(255, (int) Math.round(value * 255))); }
}
