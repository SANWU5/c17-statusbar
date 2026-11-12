// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Settings preview: weather shares the module artwork with the live row;
 * battery, signal and network use lightweight representations of native images. */
final class BigClockPreviewIcon extends ReplacementSpan {
    private static final int WEATHER = 0, BATTERY = 1, SIGNAL = 2, WIFI = 3, OFFLINE = 4, WIRED = 5;
    private final int shape, level;
    private final WeatherTextIcon.Kind kind;
    private final ModuleWeatherIcon weatherArtwork;
    private final WeatherTextIcon.IconMetrics weatherMetrics = new WeatherTextIcon.IconMetrics();
    private final boolean colours, charging;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private int ink, inkAlpha;

    BigClockPreviewIcon(WeatherTextIcon.Kind kind, boolean nativeColours) {
        shape = WEATHER; this.kind = kind == null ? WeatherTextIcon.Kind.UNKNOWN : kind; colours = nativeColours;
        level = 0; charging = false;
        weatherArtwork = this.kind == WeatherTextIcon.Kind.UNKNOWN ? null : new ModuleWeatherIcon(this.kind, nativeColours);
    }

    BigClockPreviewIcon(int token, BigClockSystemText.Snapshot state) {
        kind = null; colours = false; weatherArtwork = null;
        if (token == BigClockTextContent.BATTERY) {
            shape = BATTERY; level = state.batteryPercent;
            charging = state.charging == BigClockSystemText.Charging.CHARGING || state.charging == BigClockSystemText.Charging.FULL;
        } else if (token == BigClockTextContent.NETWORK && state.transport != BigClockSystemText.Transport.CELLULAR) {
            charging = false;
            switch (state.transport) {
                case NONE: shape = OFFLINE; level = 0; break;
                case ETHERNET: shape = WIRED; level = 0; break;
                default: shape = WIFI; level = state.wifiLevel; break;
            }
        } else { shape = SIGNAL; level = state.signalLevel; charging = false; }
    }

    private float height(Paint text) {
        if(shape==WEATHER){weatherMetrics.update(text);return weatherMetrics.height;}
        float size = text.getTextSize();
        float base = Float.isFinite(size) && size > 0 ? size : 14f;
        return Math.max(1f, base * .94f);
    }

    private float width(Paint text) {
        float h = height(text);
        switch (shape) {
            case BATTERY: return h * 1.7f;
            case WEATHER: return h * 1.15f;
            case SIGNAL: return h * .95f;
            default: return h;
        }
    }

    @Override public int getSize(Paint text, CharSequence value, int start, int end, Paint.FontMetricsInt output) {
        if (output != null) {
            text.getFontMetricsInt(output);
            float h=height(text),centre = shape==WEATHER?weatherMetrics.top+h*.5f:(output.ascent + output.descent) * .5f, half = h * .5f;
            int top = (int) Math.floor(centre - half), bottom = (int) Math.ceil(centre + half);
            if (top < output.ascent) output.ascent = top;
            if (bottom > output.descent) output.descent = bottom;
            if (top < output.top) output.top = top;
            if (bottom > output.bottom) output.bottom = bottom;
        }
        return Math.max(1, (int) Math.ceil(width(text)));
    }

    @Override public void draw(Canvas canvas, CharSequence value, int start, int end, float x, int top, int baseline, int bottom, Paint text) {
        if (shape == WEATHER) { if (weatherArtwork != null) weatherArtwork.draw(canvas, x, baseline, text); return; }
        Paint.FontMetricsInt metrics = new Paint.FontMetricsInt();
        text.getFontMetricsInt(metrics);
        float h = height(text), w = width(text), y = baseline + (metrics.ascent + metrics.descent - h) * .5f;
        ink = text.getColor(); inkAlpha = text.getAlpha();
        box.set(x, y, x + w, y + h);
        switch (shape) {
            case BATTERY: battery(canvas); break;
            case SIGNAL: bars(canvas); break;
            case WIFI: wifi(canvas); break;
            case OFFLINE: offline(canvas); break;
            case WIRED: wired(canvas); break;
            default: break;
        }
    }

    /** The text colour, or the artwork's own colour when "原色" was asked for. */
    private void colour(int artwork, boolean solid) {
        paint.setColor(colours ? artwork : ink);
        paint.setAlpha(inkAlpha);
        paint.setStyle(solid ? Paint.Style.FILL : Paint.Style.STROKE);
    }

    private void dim(boolean lit) { paint.setAlpha(lit ? inkAlpha : Math.max(28, inkAlpha / 4)); }

    private void battery(Canvas canvas) {
        float h = box.height(), body = h * .56f, cy = box.centerY(), left = box.left, right = box.right - h * .1f;
        colour(ink, false); paint.setStrokeWidth(Math.max(1f, h * .07f));
        RectF outline = new RectF(left, cy - body / 2, right, cy + body / 2);
        canvas.drawRoundRect(outline, body * .28f, body * .28f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(right + h * .02f, cy - body * .17f, box.right, cy + body * .17f), h * .03f, h * .03f, paint);
        int percent = level < 0 ? 85 : Math.min(100, level);
        float inset = h * .11f, span = outline.width() - inset * 2;
        canvas.drawRoundRect(new RectF(left + inset, cy - body / 2 + inset, left + inset + Math.max(h * .06f, span * percent / 100f), cy + body / 2 - inset),
                body * .12f, body * .12f, paint);
        if (charging) {
            paint.setColor(0xff000000 | (ink ^ 0x00ffffff)); paint.setAlpha(inkAlpha);
            float bx = outline.centerX(), s = body * .42f;
            path.reset(); path.moveTo(bx + s * .15f, cy - s); path.lineTo(bx - s * .45f, cy + s * .1f); path.lineTo(bx - s * .02f, cy + s * .1f);
            path.lineTo(bx - s * .15f, cy + s); path.lineTo(bx + s * .45f, cy - s * .1f); path.lineTo(bx + s * .02f, cy - s * .1f); path.close();
            canvas.drawPath(path, paint);
        }
    }

    private void bars(Canvas canvas) {
        int lit = level < 0 ? 3 : Math.min(4, level);
        float h = box.height() * .86f, gap = box.width() * .08f, bar = (box.width() - gap * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            colour(ink, true); dim(i < lit);
            float barHeight = h * (.34f + .22f * i), left = box.left + i * (bar + gap);
            canvas.drawRoundRect(new RectF(left, box.centerY() + h / 2 - barHeight, left + bar, box.centerY() + h / 2), bar * .3f, bar * .3f, paint);
        }
    }

    private void wifi(Canvas canvas) {
        int lit = level < 0 ? 3 : (level * 3 + 3) / 4;
        float cx = box.centerX(), h = box.height(), bottom = box.bottom - h * .12f;
        for (int i = 1; i <= 3; i++) {
            float r = h * (.22f + .22f * i);
            colour(ink, false); paint.setStrokeWidth(Math.max(1f, h * .09f)); dim(i <= lit);
            canvas.drawArc(new RectF(cx - r, bottom - r, cx + r, bottom + r), -135f, 90f, false, paint);
        }
        colour(ink, true); dim(level != 0);
        canvas.drawCircle(cx, bottom, h * .07f, paint);
    }

    private void offline(Canvas canvas) {
        float r = box.height() * .34f, cx = box.centerX(), cy = box.centerY();
        colour(ink, false); paint.setStrokeWidth(Math.max(1f, box.height() * .08f));
        canvas.drawCircle(cx, cy, r, paint);
        path.reset(); path.moveTo(cx - r * .7f, cy + r * .7f); path.lineTo(cx + r * .7f, cy - r * .7f);
        canvas.drawPath(path, paint);
    }

    private void wired(Canvas canvas) {
        float h = box.height(), cx = box.centerX(), cy = box.centerY(), r = h * .3f;
        colour(ink, false); paint.setStrokeWidth(Math.max(1f, h * .08f));
        canvas.drawRoundRect(new RectF(cx - r, cy - r * .8f, cx + r, cy + r * .8f), h * .06f, h * .06f, paint);
        path.reset(); path.moveTo(cx - r * .5f, cy + r * .8f); path.lineTo(cx - r * .5f, cy + r * 1.3f);
        path.moveTo(cx + r * .5f, cy + r * .8f); path.lineTo(cx + r * .5f, cy + r * 1.3f);
        canvas.drawPath(path, paint);
    }

    // ---- weather: a cloud, a sun or a moon, plus what falls from the cloud ----

}
