// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.ArrayList;

/** Module-owned weather symbols, v3 "圆润可爱". Chubby clouds, dotted sun rays, teardrop rain,
 * thick round strokes and softened corners on a 24-unit grid with a 1.3-unit
 * breathing gap where a front element overlaps a back one. All paths, gaps and
 * gradients are built once per kind; draw only replays them. No widget drawable,
 * resource loader, IO, bitmap or offscreen layer in draw. */
final class ModuleWeatherIcon {
    private static final float GAP = 1.3f, LINE = 2.3f, FLAKE = 1.6f;
    /** The span is 1.15 icon heights wide (27.6 units); the 24-unit artwork is centred in it. */
    private static final float OFFSET = 1.8f;
    // "原色": warm candy colours, each a top-to-bottom gradient.
    private static final int[] SUN = {0xffffe266, 0xffffae3d}, MOON = {0xfffffdf4, 0xffffe7a8},
            CLOUD = {0xffffffff, 0xffdde5f0}, DARK = {0xffc9d0dc, 0xff97a2b5},
            RAIN = {0xff86d6ff, 0xff3b9bff}, SNOW = {0xffffffff, 0xffd8eeff}, ICE = {0xffe8f8ff, 0xff94d5f7},
            BOLT = {0xffffe266, 0xffffae3d}, FOG = {0xffedf0f5, 0xffb9c2cf}, SAND = {0xfff8dfae, 0xffe0a95e},
            WIND = {0xffeef3fa, 0xffafc0d6}, STAR = {0xfffff8d6, 0xffffe08a};

    private final WeatherTextIcon.Kind kind;
    private final boolean originalColours;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WeatherTextIcon.IconMetrics metrics = new WeatherTextIcon.IconMetrics();
    private final ArrayList<Layer> layers = new ArrayList<>(3);
    private float fitScale = 1f, fitX, fitY;

    /** One colour of the artwork: a filled path, round-capped lines, and what a front element cuts out of it. */
    private static final class Layer {
        final Path fill = new Path(), line = new Path();
        final int[] colours;
        Path cut;
        float width = LINE;
        Shader shader;
        /** Same-colour stroke around the fill that rounds sharp corners (bolt, stars). */
        float soft;
        Layer(int[] colours) { this.colours = colours; }
    }

    ModuleWeatherIcon(WeatherTextIcon.Kind kind, boolean originalColours) {
        this.kind = kind; this.originalColours = originalColours;
        build();
        fitToSpan();
        if (originalColours) for (Layer layer : layers)
            layer.shader = new LinearGradient(0f, 2f, 0f, 22f, layer.colours[0], layer.colours[1], Shader.TileMode.CLAMP);
    }

    void draw(Canvas canvas, float x, int baseline, Paint text) {
        if (layers.isEmpty()) return;
        metrics.update(text);
        float height = metrics.height;
        float y = baseline + metrics.top;
        int saved = canvas.save();
        try {
            canvas.translate(x, y); canvas.scale(height / 24f, height / 24f); canvas.translate(OFFSET, 0f);
            canvas.translate(fitX, fitY); canvas.scale(fitScale, fitScale);
            for (int i = 0; i < layers.size(); i++) {
                Layer layer = layers.get(i);
                int local = layer.cut == null ? -1 : canvas.save();
                try {
                    if (layer.cut != null) canvas.clipOutPath(layer.cut);
                    if (!layer.fill.isEmpty()) {
                        colour(text, layer, true);
                        if (layer.soft > 0f) { paint.setStyle(Paint.Style.FILL_AND_STROKE); paint.setStrokeWidth(layer.soft); }
                        canvas.drawPath(layer.fill, paint);
                    }
                    if (!layer.line.isEmpty()) { colour(text, layer, false); canvas.drawPath(layer.line, paint); }
                } finally { if (local >= 0) canvas.restoreToCount(local); }
            }
        } finally { canvas.restoreToCount(saved); }
    }

    /** Include round stroke edges when fitting the selected artwork. Some source
     * clouds extend above zero and thunder bolts below 24; preserve the existing
     * text metrics without clipping those tips. Computed once, never per frame. */
    private void fitToSpan() {
        float left = 0f, top = 0f, right = 24f, bottom = 24f;
        RectF bounds = new RectF();
        for (Layer layer : layers) {
            for (int part = 0; part < 2; part++) {
                Path shape = part == 0 ? layer.fill : layer.line;
                if (shape.isEmpty()) continue;
                shape.computeBounds(bounds, true);
                float edge = (part == 0 ? layer.soft : layer.width) * .5f;
                left = Math.min(left, bounds.left - edge); top = Math.min(top, bounds.top - edge);
                right = Math.max(right, bounds.right + edge); bottom = Math.max(bottom, bounds.bottom + edge);
            }
        }
        fitScale = Math.min(1f, Math.min(24f / (right - left), 24f / (bottom - top)));
        fitX = (24f - (right + left) * fitScale) * .5f;
        fitY = (24f - (bottom + top) * fitScale) * .5f;
    }

    /** Text colour, alpha and shader by default; "原色" swaps only the colour for the layer gradient. */
    private void colour(Paint text, Layer layer, boolean fill) {
        paint.set(text);
        if (originalColours) { paint.setShader(layer.shader); paint.setColor(layer.colours[0]); paint.setAlpha(text.getAlpha()); }
        paint.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(layer.width);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(null);
    }

    // ---- geometry helpers: every sub-path is clockwise, so overlaps union under WINDING ----

    private Layer layer(int[] colours) { Layer layer = new Layer(colours); layers.add(layer); return layer; }

    private static void capsule(Path path, float x, float y, float x2, float y2) { path.moveTo(x, y); path.lineTo(x2, y2); }

    /** Chubby cloud: a full pill base and three puffs; {@code grow} inflates every part for a gap cut. */
    private static Path cloud(float dx, float dy, float s, float grow) {
        Path path = new Path();
        path.addRoundRect(new RectF(dx + s - grow, dy + 11.5f * s - grow, dx + 23f * s + grow, dy + 19.6f * s + grow),
                4.05f * s + grow, 4.05f * s + grow, Path.Direction.CW);
        path.addCircle(dx + 7f * s, dy + 12.2f * s, 4.9f * s + grow, Path.Direction.CW);
        path.addCircle(dx + 13.4f * s, dy + 8.9f * s, 6.5f * s + grow, Path.Direction.CW);
        path.addCircle(dx + 19.1f * s, dy + 12.4f * s, 4.5f * s + grow, Path.Direction.CW);
        return path;
    }

    /** The cloud above falling rain, snow or hail. */
    private static Path smallCloud(float grow) { return cloud(1.1f, -2.1f, .9f, grow); }

    /** Round sun with eight dot rays; {@code upperOnly} drops the three rays under a horizon. */
    private static void sun(Layer layer, float x, float y, float radius, float dot, boolean upperOnly) {
        layer.fill.addCircle(x, y, radius, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            if (upperOnly && i > 0 && i < 4) continue;
            double angle = Math.PI * i / 4d;
            layer.fill.addCircle(x + (float) Math.cos(angle) * (radius + dot * 2.1f), y + (float) Math.sin(angle) * (radius + dot * 2.1f), dot, Path.Direction.CW);
        }
    }

    /** Teardrop, tip up, round bottom centred at (x, y). */
    private static void drop(Path path, float x, float y, float r) {
        path.moveTo(x, y - 2.25f * r);
        path.cubicTo(x + .3f * r, y - 1.55f * r, x + r, y - .95f * r, x + r, y);
        path.arcTo(new RectF(x - r, y - r, x + r, y + r), 0f, 180f, false);
        path.cubicTo(x - r, y - .95f * r, x - .3f * r, y - 1.55f * r, x, y - 2.25f * r);
        path.close();
    }

    private static void moon(Layer layer, float x, float y, float radius) {
        Path bite = new Path();
        layer.fill.addCircle(x, y, radius, Path.Direction.CW);
        bite.addCircle(x + radius * .72f, y - radius * .56f, radius * .7f, Path.Direction.CW);
        layer.fill.op(bite, Path.Op.DIFFERENCE);
    }

    private static void star(Path path, float x, float y, float r) {
        float k = r * .25f;
        path.moveTo(x, y - r); path.quadTo(x + k, y - k, x + r, y); path.quadTo(x + k, y + k, x, y + r);
        path.quadTo(x - k, y + k, x - r, y); path.quadTo(x - k, y - k, x, y - r); path.close();
    }

    private static void bolt(Path path, float dx, float dy, float s) {
        float[] p = {13.6f, 12.6f, 8.6f, 19.2f, 11.9f, 19.2f, 10.4f, 24.4f, 16.4f, 16.6f, 13f, 16.6f, 14.6f, 12.6f};
        path.moveTo(dx + p[0] * s, dy + p[1] * s);
        for (int i = 2; i < p.length; i += 2) path.lineTo(dx + p[i] * s, dy + p[i + 1] * s);
        path.close();
    }

    /** A polygon inflated by the breathing gap, computed once with a round-joined stroke. */
    private static Path grown(Path shape) {
        Paint stroke = new Paint();
        stroke.setStyle(Paint.Style.FILL_AND_STROKE); stroke.setStrokeWidth(GAP * 2f + 1f); stroke.setStrokeJoin(Paint.Join.ROUND); // + the bolt's own 1-unit soft edge
        Path out = new Path(); stroke.getFillPath(shape, out); return out;
    }

    private static Path below(float top) { Path path = new Path(); path.addRect(-2f, top, 26f, 26f, Path.Direction.CW); return path; }

    /** Straight wind line ending in a half-turn curl, upward ({@code curl} > 0) or downward. */
    private static void swoosh(Path path, float x, float y, float x2, float curl) {
        float k = Math.abs(curl) * .5f;
        path.moveTo(x, y); path.lineTo(x2, y);
        RectF oval = curl > 0 ? new RectF(x2 - k, y - 2f * k, x2 + k, y) : new RectF(x2 - k, y, x2 + k, y + 2f * k);
        path.arcTo(oval, curl > 0 ? 90f : -90f, curl > 0 ? -180f : 180f, false);
        path.rLineTo(-k * .6f, 0f);
    }

    // ---- composition: mirrors preview/cute-kinds.js one to one ----

    private void build() {
        switch (kind) {
            case SUNNY: sun(layer(SUN), 12f, 12f, 5.8f, 1.25f, false); break;
            case CLEAR_NIGHT: {
                moon(layer(MOON), 11f, 12.6f, 8.4f);
                Layer stars = layer(STAR); stars.soft = .9f;
                star(stars.fill, 19.6f, 5f, 2.4f); star(stars.fill, 21.2f, 11.2f, 1.4f); break;
            }
            case PARTLY_CLOUDY: case CLOUDY_NIGHT: {
                Layer back = layer(kind == WeatherTextIcon.Kind.PARTLY_CLOUDY ? SUN : MOON);
                if (kind == WeatherTextIcon.Kind.PARTLY_CLOUDY) sun(back, 7.8f, 7.6f, 4.3f, 1f, false);
                else moon(back, 7.4f, 7.6f, 6f);
                back.cut = cloud(3.6f, 4.8f, .84f, GAP);
                layer(CLOUD).fill.set(cloud(3.6f, 4.8f, .84f, 0f)); break;
            }
            case CLOUDY: {
                Layer back = layer(DARK); back.fill.set(cloud(9f, -2f, .62f, 0f)); back.cut = cloud(-.6f, 2.8f, .92f, GAP);
                layer(CLOUD).fill.set(cloud(-.6f, 2.8f, .92f, 0f)); break;
            }
            case LIGHT_RAIN: overhead(CLOUD, null); drops(20.8f, 1.45f, false, 8.6f, 15.4f); break;
            case MODERATE_RAIN: overhead(CLOUD, null); drops(20.6f, 1.4f, false, 6.2f, 12f, 17.8f); break;
            case HEAVY_RAIN: case STORM:
                overhead(kind == WeatherTextIcon.Kind.STORM ? DARK : CLOUD, null); drops(20.2f, 1.3f, true, 4.8f, 9.8f, 14.8f, 19.8f); break;
            case LIGHT_SNOW: overhead(CLOUD, null); flakes(8.4f, 20.4f, 15.6f, 20.4f); break;
            case MODERATE_SNOW: overhead(CLOUD, null); flakes(6f, 19.8f, 12f, 21.4f, 18f, 19.8f); break;
            case HEAVY_SNOW: overhead(CLOUD, null); flakes(4.4f, 19.6f, 9.6f, 21.6f, 14.4f, 19.6f, 19.6f, 21.6f); break;
            case SLEET: overhead(CLOUD, null); drops(20.6f, 1.35f, false, 6.6f, 17.6f); flakes(12.1f, 20.8f); break;
            case HAILSTONE: overhead(CLOUD, null); hail(1.8f, 6f, 19.6f, 12f, 21.6f, 18f, 19.6f); break;
            case ICE_RAIN: overhead(CLOUD, null); drops(20.6f, 1.35f, false, 6.4f, 17.8f); hail(1.8f, 12.1f, 21f); break;
            case THUNDERSHOWERS: case THUNDERSTORM: case THUNDERSHOWERS_HAILSTONE: thunder(); break;
            default: atmosphere(); break;
        }
    }

    private Layer overhead(int[] colours, Path cut) { Layer cloud = layer(colours); cloud.fill.set(smallCloud(0f)); cloud.cut = cut; return cloud; }

    /** Teardrops at {@code xs}; staggered rows drop every second one a little lower. */
    private void drops(float y, float r, boolean stagger, float... xs) {
        Layer rain = layer(RAIN);
        for (int i = 0; i < xs.length; i++) drop(rain.fill, xs[i], y + (stagger && i % 2 == 1 ? 1.2f : 0f), r);
    }

    /** Chubby six-armed flakes at (x, y) pairs. */
    private void flakes(float... centres) {
        Layer snow = layer(SNOW); snow.width = FLAKE;
        for (int i = 0; i < centres.length; i += 2)
            for (int degrees = 0; degrees < 180; degrees += 60) {
                double angle = Math.toRadians(degrees); float c = (float) Math.cos(angle) * 1.7f, s = (float) Math.sin(angle) * 1.7f;
                capsule(snow.line, centres[i] - c, centres[i + 1] - s, centres[i] + c, centres[i + 1] + s);
            }
    }

    private void hail(float radius, float... centres) {
        Layer ice = layer(ICE);
        for (int i = 0; i < centres.length; i += 2) ice.fill.addCircle(centres[i], centres[i + 1], radius, Path.Direction.CW);
    }

    private void thunder() {
        boolean storm = kind == WeatherTextIcon.Kind.THUNDERSTORM;
        Path bolt = new Path();
        if (storm) bolt(bolt, -1f, -.6f, 1.08f); else bolt(bolt, 0f, 0f, 1f);
        overhead(storm ? DARK : CLOUD, grown(bolt));
        Layer flash = layer(BOLT); flash.fill.set(bolt); flash.soft = 1f;
        if (kind == WeatherTextIcon.Kind.THUNDERSHOWERS) drops(20.4f, 1.3f, false, 5.4f, 19.4f);
        else if (!storm) hail(1.7f, 5.6f, 19.8f, 19.2f, 19.8f);
    }

    private void atmosphere() {
        switch (kind) {
            case FOG: case HAZE: {
                if (kind == WeatherTextIcon.Kind.FOG) layer(CLOUD).fill.set(cloud(1.6f, -2.6f, .84f, 0f));
                else sun(layer(SUN), 12f, 10.4f, 4.8f, 1.05f, false);
                layers.get(0).cut = below(kind == WeatherTextIcon.Kind.FOG ? 13.2f : 13.6f);
                bars(layer(FOG), 3.6f, 20.4f, 16f, 6.4f, 22.2f, 19.6f, 3.6f, 16.8f, 23.2f); break;
            }
            case SINK_FLOAT: {
                Layer dust = layer(SAND);
                float[] p = {6f, 6.6f, 1.8f, 12.4f, 4.6f, 1.4f, 18.4f, 7f, 1.9f, 9.2f, 11.4f, 1.4f, 15.8f, 11.8f, 1.6f};
                for (int i = 0; i < p.length; i += 3) dust.fill.addCircle(p[i], p[i + 1], p[i + 2], Path.Direction.CW);
                bars(dust, 3.6f, 20.4f, 16.6f, 6.4f, 22.2f, 20.8f); break;
            }
            case DUST_DEVIL: {
                Layer sand = layer(SAND);
                swoosh(sand.line, 3f, 9.6f, 14.2f, 4.6f); swoosh(sand.line, 3f, 14.4f, 18.2f, 4.8f); swoosh(sand.line, 5.2f, 19f, 12.4f, -3.8f);
                sand.fill.addCircle(20.8f, 20.6f, 1.6f, Path.Direction.CW); sand.fill.addCircle(16.6f, 22f, 1.2f, Path.Direction.CW);
                sand.fill.addCircle(21.4f, 6.4f, 1.3f, Path.Direction.CW); break;
            }
            case WIND: {
                Layer wind = layer(WIND);
                swoosh(wind.line, 2.6f, 9.2f, 15f, 4.6f); swoosh(wind.line, 2.6f, 14f, 18.8f, 5f); swoosh(wind.line, 5.4f, 18.8f, 13.8f, -4f); break;
            }
            case TORNADO:
                bars(layer(WIND), 3f, 21f, 4.6f, 5.2f, 18.4f, 9.4f, 8f, 16.6f, 14f, 10.2f, 15f, 18.6f, 11.8f, 13.8f, 22.4f); break;
            case SUNRISE: case SUNSET: {
                Layer sun = layer(SUN); sun(sun, 12f, 15.2f, 4.8f, 1.05f, true); sun.cut = below(15.4f);
                Layer horizon = layer(SUN); capsule(horizon.line, 2.6f, 17.5f, 21.4f, 17.5f);
                boolean up = kind == WeatherTextIcon.Kind.SUNRISE;
                float from = up ? 23.2f : 19.9f, to = up ? 19.9f : 23.2f, head = up ? 1.7f : -1.7f;
                capsule(horizon.line, 12f, from, 12f, to);
                horizon.line.moveTo(9.8f, to + head); horizon.line.lineTo(12f, to); horizon.line.lineTo(14.2f, to + head); break;
            }
            default: break; // UNKNOWN: no artwork; WeatherTextIcon.Span never builds one for it.
        }
    }

    /** Horizontal round-capped bars, given as (from x, to x, y) triples. */
    private static void bars(Layer layer, float... rows) {
        for (int i = 0; i < rows.length; i += 3) capsule(layer.line, rows[i], rows[i + 2], rows[i + 1], rows[i + 2]);
    }
}
