// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import java.lang.ref.WeakReference;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/** Stable module weather artwork selected from actual weather semantics.
 * Native image capture remains for historical cache compatibility only. */
final class WeatherTextIcon {
    enum Kind {
        UNKNOWN(null), SUNNY("sunny"), PARTLY_CLOUDY("partly_cloudy"), CLOUDY("cloudy"),
        CLEAR_NIGHT("clear_night"), CLOUDY_NIGHT("cloudy_night"),
        LIGHT_RAIN("light_rain"), MODERATE_RAIN("moderate_rain"), HEAVY_RAIN("heavy_rain"),
        ICE_RAIN("ice_rain"), HAILSTONE("hailstone"), THUNDERSHOWERS("thundershowers"),
        THUNDERSHOWERS_HAILSTONE("thundershowers_hailstone"), THUNDERSTORM("thunderstorm"),
        LIGHT_SNOW("light_snow"), MODERATE_SNOW("moderate_snow"), HEAVY_SNOW("heavy_snow"),
        SLEET("sleet"), FOG("fog"), SINK_FLOAT("sink_float"), DUST_DEVIL("dust_devil"),
        HAZE("haze"), WIND("wind"), STORM("storm"), TORNADO("tornado"),
        SUNRISE("sunrise"), SUNSET("sunset");
        final String resource;
        Kind(String suffix) { resource = suffix == null ? null : "ic_weather_" + suffix; }
    }

    private static final Map<ClassLoader, Map<Integer, Kind>> NATIVE_RESOURCES = new HashMap<>();
    private WeatherTextIcon() { }

    /** Exact code categories audited in ColorOS 17 WeatherEffectController.WEATHER_DRAWABLE.
     * Only its five SPECIAL_INDEX entries use the provider's sunrise/sunset. */
    static Kind providerKind(int code,int daylight) {
        if (code >= 54 && code <= 58) {
            if (daylight < 0) return Kind.UNKNOWN;
            return code <= 55 ? daylight == 1 ? Kind.SUNNY : Kind.CLEAR_NIGHT
                    : daylight == 1 ? Kind.PARTLY_CLOUDY : Kind.CLOUDY_NIGHT;
        }
        switch (code) {
            case 2: case 3: case 4: case 6: case 7: return Kind.LIGHT_RAIN;
            case 1: case 8: case 9: return Kind.MODERATE_RAIN;
            case 5: case 10: case 11: case 12: case 13: case 14: case 15: case 16: return Kind.HEAVY_RAIN;
            case 17: return Kind.ICE_RAIN;
            case 18: case 19: case 20: return Kind.HAILSTONE;
            case 22: return Kind.THUNDERSHOWERS;
            case 23: return Kind.THUNDERSHOWERS_HAILSTONE;
            case 24: case 25: return Kind.THUNDERSTORM;
            case 27: case 28: case 29: case 30: return Kind.LIGHT_SNOW;
            case 21: case 26: case 31: case 32: return Kind.MODERATE_SNOW;
            case 33: case 34: case 35: case 36: return Kind.HEAVY_SNOW;
            case 37: return Kind.SLEET;
            case 38: case 39: case 40: case 41: case 42: case 43: case 44: return Kind.FOG;
            case 45: return Kind.SINK_FLOAT;
            case 46: case 47: case 48: case 49: return Kind.DUST_DEVIL;
            case 50: case 51: case 52: return Kind.HAZE;
            case 53: return Kind.PARTLY_CLOUDY;
            case 59: return Kind.CLOUDY;
            case 60: case 61: case 62: return Kind.WIND;
            case 63: case 64: case 65: case 67: return Kind.STORM;
            case 66: case 69: return Kind.CLOUDY_NIGHT;
            case 68: return Kind.CLEAR_NIGHT;
            case 70: return Kind.SUNRISE;
            case 71: return Kind.SUNSET;
            default: return Kind.UNKNOWN;
        }
    }

    /** The native getResId() already applies its sunrise/sunset rule. Reverse
     * that exact loader's R constants, never infer night from a weather ID. */
    static synchronized Kind nativeKind(ClassLoader loader, int resourceId) {
        if (loader == null || resourceId == 0) return Kind.UNKNOWN;
        Map<Integer, Kind> resources = NATIVE_RESOURCES.get(loader);
        if (resources == null) {
            resources = new HashMap<>();
            try {
                Class<?> drawable = loader.loadClass("com.oplus.keyguard.clock.common.R$drawable");
                for (Kind kind : Kind.values()) if (kind.resource != null) try {
                    Field field = drawable.getDeclaredField(kind.resource);
                    if (field.getType() == Integer.TYPE && Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true); int value = field.getInt(null);
                        if (value != 0) resources.put(value, kind);
                    }
                } catch (ReflectiveOperationException | RuntimeException unsupportedResource) { /* Explicit unknown. */ }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupportedClock) { /* Explicit unknown. */ }
            if (NATIVE_RESOURCES.size() >= 4) NATIVE_RESOURCES.clear();
            NATIVE_RESOURCES.put(loader, resources);
        }
        Kind kind = resources.get(resourceId);
        return kind == null ? Kind.UNKNOWN : kind;
    }

    static synchronized void clearNativeCache() { NATIVE_RESOURCES.clear(); }

    /** AOSP state names are declared enum constants from the captured model. */
    static Kind aospKind(String name) {
        if (name == null) return Kind.UNKNOWN;
        switch (name) {
            case "SUNNY": case "MOSTLY_SUNNY": return Kind.SUNNY;
            case "CLEAR_NIGHT": case "MOSTLY_CLEAR_NIGHT": return Kind.CLEAR_NIGHT;
            case "PARTLY_CLOUDY": case "MOSTLY_CLOUDY_DAY": return Kind.PARTLY_CLOUDY;
            case "PARTLY_CLOUDY_NIGHT": case "MOSTLY_CLOUDY_NIGHT": return Kind.CLOUDY_NIGHT;
            case "CLOUDY": return Kind.CLOUDY;
            case "DRIZZLE": return Kind.LIGHT_RAIN;
            case "SHOWERS_RAIN": return Kind.MODERATE_RAIN;
            case "HEAVY_RAIN": return Kind.HEAVY_RAIN;
            case "FLURRIES": return Kind.LIGHT_SNOW;
            case "SNOW_SHOWERS_SNOW": return Kind.MODERATE_SNOW;
            case "HEAVY_SNOW": case "BLIZZARD": return Kind.HEAVY_SNOW;
            case "MIXED_RAIN_HAIL_RAIN_SLEET": case "WINTRY_MIX_RAIN_SNOW": return Kind.SLEET;
            case "SLEET_HAIL": return Kind.HAILSTONE;
            case "ISOLATED_SCATTERED_TSTORMS_DAY": case "ISOLATED_SCATTERED_TSTORMS_NIGHT": return Kind.THUNDERSHOWERS;
            case "HAZE_FOG_DUST_SMOKE": return Kind.HAZE;
            case "WINDY_BREEZY": case "BLOWING_SNOW": return Kind.WIND;
            case "TROPICAL_STORM_HURRICANE": return Kind.STORM;
            case "TORNADO": return Kind.TORNADO;
            default: return Kind.UNKNOWN;
        }
    }

    /** Immutable vector recording or factory only: never a live host drawable,
     * view, callback or resource loader. Picture copying does not read pixels. */
    static final class NativeImage {
        private final Picture picture;
        private final Drawable.ConstantState state;
        private final WeakReference<Drawable> nativeDrawable;
        private final WeakReference<Picture> nativePicture;
        final int width,height;
        /** True when the vector recording was made by drawing the live native drawable once at its own bounds. */
        final boolean recorded;
        private NativeImage(Drawable owner,Picture source,Drawable.ConstantState factory,int width,int height) {
            this(owner,source,factory,width,height,false);
        }
        private NativeImage(Drawable owner,Picture source,Drawable.ConstantState factory,int width,int height,boolean recorded) {
            this.recorded=recorded;
            picture=source==null?null:recorded?source:new Picture(source);state=factory;
            nativeDrawable=new WeakReference<>(owner);nativePicture=new WeakReference<>(source);
            this.width=width;this.height=height;
        }
        static NativeImage capture(Drawable owner,NativeImage previous) { return capture(owner,previous,false); }
        /** A drawable that is neither a PictureDrawable nor clonable (the renderer's own SVG class changes between
         * widget versions) is recorded once, non-mutating, at the bounds the host already gave it. */
        private static NativeImage record(Drawable owner,NativeImage previous,boolean refresh) {
            try {
                android.graphics.Rect bounds=owner.getBounds();int width=bounds.width(),height=bounds.height();
                if(width<=0||height<=0||width>4096||height>4096)return null;
                if(!refresh&&previous!=null&&previous.recorded&&previous.nativeDrawable.get()==owner
                        &&previous.width==width&&previous.height==height)return previous;
                Picture recording=new Picture();
                Canvas canvas=recording.beginRecording(width,height);
                try {canvas.translate(-bounds.left,-bounds.top);owner.draw(canvas);}
                finally {recording.endRecording();}
                return new NativeImage(owner,recording,null,width,height,true);
            }catch(RuntimeException|LinkageError unavailable){return null;}
        }
        static NativeImage capture(Drawable owner,NativeImage previous,boolean refresh) {
            if(owner==null)return null;
            try {
                Picture recording=owner instanceof PictureDrawable?((PictureDrawable)owner).getPicture():null;
                if(owner instanceof PictureDrawable&&recording==null)return null;
                int width=recording==null?owner.getIntrinsicWidth():recording.getWidth();
                int height=recording==null?owner.getIntrinsicHeight():recording.getHeight();
                if(width<=0||height<=0||width>4096||height>4096)return recording==null?record(owner,previous,refresh):null;
                if(previous!=null&&previous.nativeDrawable.get()==owner&&previous.nativePicture.get()==recording
                        &&previous.width==width&&previous.height==height)return previous;
                Drawable.ConstantState factory=recording==null?owner.getConstantState():null;
                if(recording==null&&factory==null)return record(owner,previous,refresh);
                return new NativeImage(owner,recording,factory,width,height);
            }catch(RuntimeException|LinkageError unavailable){return null;}
        }
        private Drawable create() {
            if(picture!=null)return new PictureDrawable(picture);
            if(state==null)return null;
            try {
                Drawable fresh=state.newDrawable();
                if(fresh==null||fresh==nativeDrawable.get())return null;
                Drawable owned=fresh.mutate();
                return owned==nativeDrawable.get()?null:owned;
            }catch(RuntimeException|LinkageError unavailable){return null;}
        }
    }

    /** Cache the visible CJK glyph box, rather than enlarging icons by the font's em size. */
    static final class IconMetrics {
        private final android.graphics.Rect ink=new android.graphics.Rect();
        private final Paint.FontMetricsInt fallback=new Paint.FontMetricsInt();
        private android.graphics.Typeface face;
        private float size=Float.NaN,scale,skew;
        private int flags;
        private String variation;
        float top,height;
        void update(Paint paint){
            if(face==paint.getTypeface()&&size==paint.getTextSize()&&scale==paint.getTextScaleX()
                    &&skew==paint.getTextSkewX()&&flags==paint.getFlags()
                    &&java.util.Objects.equals(variation,paint.getFontVariationSettings()))return;
            face=paint.getTypeface();size=paint.getTextSize();scale=paint.getTextScaleX();skew=paint.getTextSkewX();flags=paint.getFlags();
            variation=paint.getFontVariationSettings();
            paint.getTextBounds("国",0,1,ink);
            if(ink.height()>0){top=ink.top;height=ink.height();}
            else {paint.getFontMetricsInt(fallback);top=fallback.ascent;height=Math.max(1,fallback.descent-fallback.ascent);}
        }
    }
    static final class Span extends ReplacementSpan {
        final Kind kind;
        final NativeImage image;
        /** True keeps the lock-screen widget's own colours instead of the text colour silhouette. */
        final boolean nativeColours;
        private final Drawable graphic;
        private final ModuleWeatherIcon artwork;
        private final RectF bounds=new RectF();
        private final Paint tint=new Paint();
        private final Paint.FontMetricsInt metrics=new Paint.FontMetricsInt();
        private final IconMetrics iconMetrics=new IconMetrics();
        private final PorterDuffXfermode sourceIn=new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
        Span(Kind kind,NativeImage image) { this(kind,image,false); }
        Span(Kind kind,NativeImage image,boolean nativeColours) {
            this.kind=kind==null?Kind.UNKNOWN:kind;this.image=image;this.nativeColours=nativeColours;graphic=image==null?null:image.create();
            artwork=image==null&&this.kind!=Kind.UNKNOWN?new ModuleWeatherIcon(this.kind,nativeColours):null;
        }
        private float height(Paint paint){iconMetrics.update(paint);return iconMetrics.height;}
        private float width(Paint paint){return height(paint)*(image==null?1.15f:Math.max(.25f,Math.min(4f,(float)image.width/image.height)));}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt output) {
            if(output!=null) {
                paint.getFontMetricsInt(output);
                iconMetrics.update(paint);
                int iconTop=(int)Math.floor(iconMetrics.top),iconBottom=(int)Math.ceil(iconMetrics.top+iconMetrics.height);
                if(iconTop<output.ascent)output.ascent=iconTop;if(iconBottom>output.descent)output.descent=iconBottom;
                if(iconTop<output.top)output.top=iconTop;if(iconBottom>output.bottom)output.bottom=iconBottom;
            }
            // The single space before the icon is the only gap; no trailing pad.
            return Math.max(1,(int)Math.ceil(width(paint)));
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,
                int top,int baseline,int bottom,Paint paint) {
            if(artwork!=null){artwork.draw(canvas,x,baseline,paint);return;}
            if(graphic==null)return;
            float height=height(paint),width=width(paint);paint.getFontMetricsInt(metrics);
            float y=baseline+iconMetrics.top;
            bounds.set(x,y,x+width,y+height);
            // The parent material requests opaque coverage. Its one existing
            // effect already colours all glyphs, so preserve the native vector
            // directly on this common path without an extra offscreen layer.
            boolean colour=!nativeColours&&(paint.getColor()!=0xffffffff||paint.getAlpha()!=255||paint.getShader()!=null);
            // Native colours keep the widget artwork; only the text alpha (fades) still applies.
            int alpha=nativeColours?paint.getAlpha():255;
            int saved=colour?canvas.saveLayer(bounds,null):alpha!=255?canvas.saveLayerAlpha(bounds,alpha):canvas.save();
            try {
                int local=canvas.save();
                try {
                    canvas.translate(x,y);canvas.scale(width/image.width,height/image.height);
                    // PictureDrawable clips/translates its Picture; changing
                    // bounds alone does not scale the native vector recording.
                    graphic.setBounds(0,0,image.width,image.height);graphic.draw(canvas);
                }
                finally {canvas.restoreToCount(local);}
                // Use the exact native silhouette with the surrounding component's
                // colour/alpha/shader. No resource lookup, decode or bitmap copy.
                if(colour){tint.set(paint);tint.setStyle(Paint.Style.FILL);tint.setXfermode(sourceIn);canvas.drawRect(bounds,tint);}
            }finally {canvas.restoreToCount(saved);}
        }
    }
}
