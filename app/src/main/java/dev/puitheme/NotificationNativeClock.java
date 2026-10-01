// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent plugin digit views, driven by the phone's own time-change animation controller. */
public final class NotificationNativeClock extends FrameLayout {
    private static final String PACKAGE = "com.oplus.keyguard.personality.clocks";
    private static final String DIGIT = "com.oplus.keyguard.clock.digital.ui.view.DigitalTimeView";
    private static final String TEXT = "com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView";
    private static final String ANIMATION = "com.oplus.keyguard.clock.digital.ui.controller.DigitalTimeChangeAnimationController";
    private static final Pattern TIME = Pattern.compile("(?<![\\p{Nd}])([\\p{Nd}]{1,2})[:：]([\\p{Nd}]{2})(?![\\p{Nd}])");
    private static final Map<TextView, BorderLayer> OWNED_LAYERS = new WeakHashMap<>();
    private static int creationWarnings;
    private final NativeAccess nativeAccess;
    private final List<Slot> slots = new ArrayList<>();
    private final IdentityHashMap<Paint, Shader> originalShaders = new IdentityHashMap<>();
    private String time = "";
    private Typeface typeface = Typeface.DEFAULT;
    private float size, spacing, glassDensity = 1f;
    private int color = Color.WHITE, warnings;
    private boolean available = true, glass;
    private boolean glassBorderEnabled;
    private float glassBorderWidth;
    private int glassBorderColor = Color.WHITE, borderWarnings;
    private Shader suppliedShader, glassShader;
    private float shaderHeight = -1f;
    private int shaderColor;

    /** A missing or incompatible system plugin leaves the caller's existing clock available. */
    public static NotificationNativeClock create(Context context) {
        if (context == null) return null;
        try {
            Context plugin = context.createPackageContext(PACKAGE,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            plugin = independentThemedContext(plugin);
            return new NotificationNativeClock(context, new NativeAccess(plugin));
        } catch (Throwable error) {
            if (creationWarnings++ < 3) ModuleDiagnostics.error("bigclock",
                    "Native clock plugin unavailable; existing clock retained", error);
            return null;
        }
    }

    private static Context independentThemedContext(Context plugin) {
        // The phone APK declares Theme.KeyguardPersonalityClocks; its Theme.COUI ancestor
        // resolves digital_clock_time_text_style's couiColorLabelOnColor TextView attribute.
        // createPackageContext alone has a framework default theme, so native XML cannot
        // read that ColorStateList. Apply the APK's own theme to a private wrapper only.
        int theme = plugin.getApplicationInfo().theme;
        if (theme == 0) theme = plugin.getResources().getIdentifier(
                "Theme.KeyguardPersonalityClocks", "style", PACKAGE);
        if (theme == 0) throw new IllegalStateException("Native clock application theme unavailable");
        ContextThemeWrapper themed = new ContextThemeWrapper(plugin, theme);
        int label = themed.getResources().getIdentifier("couiColorLabelOnColor", "attr", PACKAGE);
        if (label != 0) {
            TypedArray attributes = themed.obtainStyledAttributes(new int[]{label});
            try {
                ColorStateList colors = attributes.getColorStateList(0);
                if (colors == null) throw new IllegalStateException("Native clock text color theme unresolved");
            } finally { attributes.recycle(); }
        }
        return themed;
    }

    /** Uses the actual loaded plugin's context/loader when a read-only digit template is available. */
    public static NotificationNativeClock create(Context context, View nativeTemplate) {
        if (context == null || nativeTemplate == null) return create(context);
        try {
            NativeAccess access = new NativeAccess(nativeTemplate.getContext(), nativeTemplate.getClass().getClassLoader());
            NotificationNativeClock result = new NotificationNativeClock(context, access);
            Object source = access.digitType.isInstance(nativeTemplate)
                    ? access.getVisible.invoke(nativeTemplate) : nativeTemplate;
            if (source instanceof TextView) {
                TextView text = (TextView) source;
                result.typeface = text.getPaint().getTypeface();
                if (result.typeface == null) result.typeface = text.getTypeface();
                result.size = text.getTextSize(); result.color = text.getCurrentTextColor();
                result.suppliedShader = text.getPaint().getShader();
            }
            return result;
        } catch (Throwable error) {
            if (creationWarnings++ < 3) ModuleDiagnostics.error("bigclock",
                    "Native clock template unavailable; independent plugin clock retained", error);
            return create(context);
        }
    }

    private NotificationNativeClock(Context context, NativeAccess nativeAccess) throws ReflectiveOperationException {
        super(context);
        this.nativeAccess = nativeAccess;
        size = 96f * getResources().getDisplayMetrics().density;
        setClipChildren(false); setClipToPadding(false);
        setClickable(false); setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Fail creation before the caller replaces its existing clock if native XML cannot inflate.
        Slot probe = new Slot(context, true); probe.applyTypography(); probe.cancel();
    }

    public boolean isAvailable() { return available; }
    public boolean isNativeReady() { return available; }

    /** No tick, timer, or gesture listener is installed. The caller supplies native time events. */
    public boolean setTime(String value, boolean animate, long now) {
        if (!available) return false;
        String next = value == null ? "" : value;
        if (next.equals(time)) return true;
        try {
            List<Token> tokens = tokenize(next);
            boolean sameStructure = tokens.size() == slots.size();
            for (int i = 0; sameStructure && i < tokens.size(); i++)
                sameStructure = tokens.get(i).digit == slots.get(i).digit;
            TimeRange before = TimeRange.of(time), after = TimeRange.of(next);
            boolean hourChanged = before != null && after != null && !before.hour.equals(after.hour);
            if (!sameStructure) {
                cancel(); restoreShaders(); unregisterBorderLayers(); removeAllViews(); slots.clear();
                for (Token token : tokens) {
                    Slot slot = new Slot(getContext(), token.digit);
                    slots.add(slot); addView(slot, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                    slot.registerBorderLayers();
                    slot.applyTypography(); slot.setToken(token.text, false, false, false);
                }
            } else {
                boolean canAnimate = animate && !time.isEmpty() && isAttachedToWindow();
                for (int i = 0; i < tokens.size(); i++) {
                    Token token = tokens.get(i);
                    boolean hour = after != null && token.start >= after.hourStart && token.start < after.hourEnd;
                    boolean minute = after != null && token.start >= after.minuteStart && token.start < after.minuteEnd;
                    slots.get(i).setToken(token.text, canAnimate && token.digit,
                            hour || minute && hourChanged, minute && hourChanged);
                }
            }
            time = next; refreshShaders(); requestLayout(); invalidate(); return true;
        } catch (Throwable error) {
            fail(error); return false;
        }
    }

    /** The supplied face may contain the actual lockscreen weight/height axes or a custom font. */
    public boolean setTypography(Typeface face, float textSizePx, int textColor, float letterSpacing) {
        if (!available) return false;
        Typeface nextFace = face == null ? Typeface.DEFAULT : face;
        float nextSize = finite(textSizePx) ? Math.max(0f, textSizePx) : size;
        float nextSpacing = finite(letterSpacing) ? letterSpacing : 0f;
        if (typeface == nextFace && size == nextSize && color == textColor && spacing == nextSpacing) return true;
        typeface = nextFace; size = nextSize; color = textColor; spacing = nextSpacing;
        glassShader = null;
        try {
            for (Slot slot : slots) slot.applyTypography();
            refreshShaders();
            requestLayout(); invalidate(); return true;
        } catch (Throwable error) { fail(error); return false; }
    }

    /** Optional local fill; this does not alter the lockscreen's shared color/material controllers. */
    public void setGlass(boolean enabled, float density) {
        float nextDensity = finite(density) && density > 0f ? density : 1f;
        if (glass == enabled && glassDensity == nextDensity) return;
        glass = enabled; glassDensity = nextDensity; glassShader = null; refreshShaders(); invalidate();
    }

    /** The border is recorded with each private text layer, sharing its native RenderNode effects. */
    public void setGlassBorder(boolean enabled, float widthPx, int argb) {
        float nextWidth = finite(widthPx) ? Math.max(0f, widthPx) : 0f;
        if (glassBorderEnabled == enabled && glassBorderWidth == nextWidth && glassBorderColor == argb) return;
        glassBorderEnabled = enabled; glassBorderWidth = nextWidth; glassBorderColor = argb;
        invalidateBorderLayers(); invalidate();
    }

    /** Called once after View.draw(Canvas); templates and shared lockscreen instances never enter this map. */
    public static void drawOwnedBorder(View view, Canvas canvas) {
        if (!(view instanceof TextView) || canvas == null) return;
        BorderLayer owned;
        synchronized (OWNED_LAYERS) { owned = OWNED_LAYERS.get((TextView) view); }
        if (owned == null) return;
        NotificationNativeClock owner = owned.owner.get();
        TextView layer = (TextView) view;
        if (owner == null || !owner.available || !owner.glass || !owner.glassBorderEnabled
                || owner.glassBorderWidth <= 0f || Color.alpha(owner.glassBorderColor) == 0
                || !owner.isAttachedToWindow() || owner.getVisibility() != View.VISIBLE
                || layer.getVisibility() != View.VISIBLE || layer.getWidth() <= 0 || layer.getHeight() <= 0) return;
        // Alpha can start at zero in a native render-thread transition. Record the border anyway,
        // so the same display list gains opacity together with its native fill.
        Layout layout = layer.getLayout();
        if (layout == null || layer.getText().length() == 0 || hasBitmapGlyphs(layer.getText())) return;
        try { owner.drawBorder(layer, layout, canvas, owned); }
        catch (Throwable error) {
            if (owner.borderWarnings++ < 3) ModuleDiagnostics.error("bigclock",
                    "Native clock local border unavailable; native fill retained", error);
        }
    }

    private void drawBorder(TextView layer, Layout layout, Canvas canvas, BorderLayer owned) {
        Paint paint = layout.getPaint();
        Paint.Style style = paint.getStyle();
        Paint.Join join = paint.getStrokeJoin();
        float width = paint.getStrokeWidth();
        int originalColor = paint.getColor();
        Shader shader = paint.getShader();
        int save = canvas.save();
        try {
            float offset = layer.getExtendedPaddingTop();
            if (owned.nativeText && nativeAccess.drawOffset != null) try {
                float nativeOffset = nativeAccess.drawOffset.getFloat(layer);
                if (finite(nativeOffset)) offset += nativeOffset;
            } catch (ReflectiveOperationException | RuntimeException error) { warn(error); }
            canvas.translate(layer.getCompoundPaddingLeft(), offset);
            CharSequence text = layer.getText();
            if (owned.nativeText && text.length() == 1 && Character.isDigit(text.charAt(0))
                    && layout.getLineCount() == 1) {
                Path outline = owned.digitOutline(paint, text.toString());
                if (outline == null || outline.isEmpty()) return;
                Paint edge = owned.edgePaint;
                edge.set(paint);
                edge.setStyle(Paint.Style.STROKE); edge.setStrokeJoin(Paint.Join.ROUND);
                edge.setStrokeWidth(glassBorderWidth); edge.setColor(glassBorderColor); edge.setShader(null);
                // Each native numeral has zero letter spacing and TOP|LEFT gravity. Use its
                // current Layout baseline, including the plugin's draw offset exactly once.
                canvas.translate(layout.getLineLeft(0), layout.getLineBaseline(0));
                canvas.drawPath(outline, edge);
                return;
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(glassBorderWidth); paint.setColor(glassBorderColor); paint.setShader(null);
            // Layout preserves the same shaping, spacing and variable font outline as the native fill.
            layout.draw(canvas);
        } finally {
            paint.setStyle(style); paint.setStrokeJoin(join); paint.setStrokeWidth(width);
            paint.setColor(originalColor); paint.setShader(shader);
            canvas.restoreToCount(save);
        }
    }

    private static boolean hasBitmapGlyphs(CharSequence text) {
        for (int index = 0; index < text.length();) {
            int point = Character.codePointAt(text, index);
            if (point > 0xffff || point == 0xfe0f || point == 0x200d || point == 0x20e3
                    || Character.getType(point) == Character.OTHER_SYMBOL) return true;
            index += Character.charCount(point);
        }
        // Emoji spans draw their bitmap again regardless of Paint.Style; retain their single native fill.
        return false;
    }

    private void invalidateBorderLayers() {
        for (Slot slot : slots) {
            slot.visible.invalidate(); if (slot.outgoing != null) slot.outgoing.invalidate();
        }
    }

    private void unregisterBorderLayers() {
        synchronized (OWNED_LAYERS) {
            for (Slot slot : slots) {
                OWNED_LAYERS.remove(slot.visible);
                if (slot.outgoing != null) OWNED_LAYERS.remove(slot.outgoing);
            }
        }
    }

    private static final class BorderLayer {
        final WeakReference<NotificationNativeClock> owner;
        final boolean nativeText;
        final Paint edgePaint = new Paint(), geometryPaint = new Paint();
        final Path glyphPath = new Path(), emptyPath = new Path(), outlinePath = new Path();
        private String outlineText, outlineFeatures, outlineVariations, outlineLocales;
        private Typeface outlineTypeface;
        private float outlineSize, outlineScale, outlineSkew, outlineSpacing;
        private int outlineFlags;
        private boolean outlineFakeBold, outlineReady, outlineValid;
        BorderLayer(NotificationNativeClock owner, boolean nativeText) {
            this.owner = new WeakReference<>(owner); this.nativeText = nativeText;
        }

        Path digitOutline(Paint paint, String text) {
            String features = paint.getFontFeatureSettings(), variations = paint.getFontVariationSettings();
            String locales = paint.getTextLocales().toLanguageTags();
            if (!outlineReady || !text.equals(outlineText) || outlineTypeface != paint.getTypeface()
                    || outlineSize != paint.getTextSize() || outlineScale != paint.getTextScaleX()
                    || outlineSkew != paint.getTextSkewX() || outlineSpacing != paint.getLetterSpacing()
                    || outlineFlags != paint.getFlags() || outlineFakeBold != paint.isFakeBoldText()
                    || !same(features, outlineFeatures) || !same(variations, outlineVariations)
                    || !same(locales, outlineLocales)) {
                outlineReady = false; outlineValid = false;
                glyphPath.reset(); glyphPath.setFillType(Path.FillType.WINDING);
                outlinePath.reset();
                geometryPaint.set(paint); geometryPaint.setTextAlign(Paint.Align.LEFT);
                geometryPaint.setStyle(Paint.Style.FILL); geometryPaint.setShader(null);
                geometryPaint.getTextPath(text, 0, text.length(), 0f, 0f, glyphPath);
                // OPPODigit00's 6 contains self-intersections inside its nonzero fill.
                // Normalize the complete fill before stroking: individual contour unions
                // would erase counters, while Layout.draw(STROKE) exposes internal joins.
                outlineValid = outlinePath.op(glyphPath, emptyPath, Path.Op.UNION);
                outlineText = text; outlineTypeface = paint.getTypeface();
                outlineSize = paint.getTextSize(); outlineScale = paint.getTextScaleX();
                outlineSkew = paint.getTextSkewX(); outlineSpacing = paint.getLetterSpacing();
                outlineFlags = paint.getFlags(); outlineFakeBold = paint.isFakeBoldText();
                outlineFeatures = features; outlineVariations = variations; outlineLocales = locales;
                outlineReady = true;
            }
            // A failed path operation leaves the single native fill intact, without a
            // fallback stroke that would reintroduce the font's internal crossing.
            return outlineValid ? outlinePath : null;
        }

        private static boolean same(String first, String second) {
            return first == null ? second == null : first.equals(second);
        }
    }

    /** A real lockscreen shader snapshot can be supplied instead of the optional local glass fill. */
    public void setPaintShader(Shader shader) {
        if (suppliedShader == shader) return;
        suppliedShader = shader; refreshShaders(); invalidate();
    }

    /** Only this bridge's independent digit transitions are cancelled. */
    public void cancel() {
        for (Slot slot : slots) try { slot.cancel(); }
        catch (Throwable error) { warn(error); }
    }

    public void detach() { unregisterBorderLayers(); cancel(); restoreShaders(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (available) for (Slot slot : slots) slot.registerBorderLayers();
        refreshShaders();
    }
    @Override protected void onDetachedFromWindow() { detach(); super.onDetachedFromWindow(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!available) { setMeasuredDimension(0, 0); return; }
        try { measureSlots(widthSpec, heightSpec); }
        catch (Throwable error) { fail(error); setMeasuredDimension(0, 0); }
    }

    private void measureSlots(int widthSpec, int heightSpec) {
        int width = 0, height = 0;
        int unconstrained = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int gap = Math.round(size * spacing);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i); slot.measure(unconstrained, unconstrained);
            if (i > 0) width = safeAdd(width, gap);
            width = safeAdd(width, slot.getMeasuredWidth()); height = Math.max(height, slot.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(Math.max(0, width), widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!available) return;
        try { layoutSlots(); }
        catch (Throwable error) { fail(error); }
    }

    private void layoutSlots() {
        int x = 0, gap = Math.round(size * spacing);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (i > 0) x = safeAdd(x, gap);
            int y = Math.max(0, (getMeasuredHeight() - slot.getMeasuredHeight()) / 2);
            slot.layout(x, y, safeAdd(x, slot.getMeasuredWidth()), y + slot.getMeasuredHeight());
            x = safeAdd(x, slot.getMeasuredWidth());
        }
        refreshShaders();
    }

    /** These paints belong only to our independent slots, so display lists can retain their fill. */
    private void refreshShaders() {
        if (!available) { restoreShaders(); return; }
        Shader shader = glass ? suppliedShader : null;
        if (shader == null && glass) {
            float height = Math.max(1f, getHeight());
            if (glassShader == null || shaderHeight != height || shaderColor != color) {
                // Native Paint already carries the resolved text opacity. Shader alpha describes
                // only the glass material; copying the text alpha here would apply it a second time.
                int alpha = 255;
                int low = (color & 0x00ffffff) | Math.round(alpha * .34f) << 24;
                int high = (color & 0x00ffffff) | Math.round(alpha * .82f) << 24;
                glassShader = new LinearGradient(0f, 0f, 0f, Math.max(glassDensity, height),
                        new int[]{high, low, high}, new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP);
                shaderHeight = height; shaderColor = color;
            }
            shader = glassShader;
        }
        if (shader == null) { restoreShaders(); return; }
        for (Slot slot : slots) slot.applyShader(shader);
    }

    private void applyShader(Paint paint, Shader replacement, TextView layer) {
        if (!originalShaders.containsKey(paint)) originalShaders.put(paint, paint.getShader());
        if (paint.getShader() == replacement) return;
        paint.setShader(replacement); layer.invalidate();
    }

    private void restoreShaders() {
        if (originalShaders.isEmpty()) return;
        for (Map.Entry<Paint, Shader> entry : originalShaders.entrySet()) entry.getKey().setShader(entry.getValue());
        originalShaders.clear();
        for (Slot slot : slots) {
            slot.visible.invalidate(); if (slot.outgoing != null) slot.outgoing.invalidate();
        }
    }

    private final class Slot extends FrameLayout {
        final boolean digit;
        final View nativeDigit;
        final TextView visible, outgoing;
        final Rect ink = new Rect();
        String token = "";
        int naturalHeight, inkTop;
        Typeface envelopeTypeface;
        float envelopeSize = -1f;
        boolean envelopeValid, pivotDirty = true;
        int envelopeWidth, envelopeHeight, envelopeBaseline, envelopeDrawOffset;
        Slot(Context context, boolean digit) throws ReflectiveOperationException {
            super(context); this.digit = digit;
            setClipChildren(false); setClipToPadding(false);
            if (digit) {
                View nativeRoot = nativeAccess.inflater.inflate(nativeAccess.unitLayout, this, false);
                View view = nativeRoot.findViewById(nativeAccess.digitId);
                if (!nativeAccess.digitType.isInstance(view)) throw new IllegalStateException("Native clock digit layout incompatible");
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                nativeDigit = view;
                nativeAccess.attachOutgoing.invoke(view, nativeAccess.inflater);
                Object current = nativeAccess.getVisible.invoke(view), previous = nativeAccess.getOutgoing.invoke(view);
                if (!(current instanceof TextView) || !(previous instanceof TextView))
                    throw new IllegalStateException("Native clock digit layers unavailable");
                visible = (TextView) current; outgoing = (TextView) previous;
                addView(view, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            } else {
                nativeDigit = null; outgoing = null; visible = new TextView(context);
                addView(visible, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            }
            prepareLayer(visible); if (outgoing != null) prepareLayer(outgoing);
        }
        void prepareLayer(TextView view) {
            view.setSingleLine(true); view.setIncludeFontPadding(false);
            view.setPadding(0, 0, 0, 0); view.setGravity(Gravity.TOP | Gravity.LEFT);
            view.setClickable(false); view.setFocusable(false);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            view.setFontFeatureSettings("tnum");
        }
        void registerBorderLayers() {
            synchronized (OWNED_LAYERS) {
                OWNED_LAYERS.put(visible, new BorderLayer(NotificationNativeClock.this, digit));
                if (outgoing != null) OWNED_LAYERS.put(outgoing, new BorderLayer(NotificationNativeClock.this, digit));
            }
        }
        void applyTypography() throws ReflectiveOperationException {
            styleLayer(visible); if (outgoing != null) styleLayer(outgoing);
            if (digit && (envelopeTypeface != visible.getPaint().getTypeface()
                    || envelopeSize != visible.getTextSize())) {
                envelopeValid = false; pivotDirty = true;
            }
            requestLayout();
        }
        void styleLayer(TextView view) throws ReflectiveOperationException {
            if (digit) nativeAccess.setFont.invoke(view, typeface, true, false);
            else view.setTypeface(typeface);
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size); view.setTextColor(color);
            view.setLetterSpacing(digit ? 0f : spacing);
        }
        void setToken(String next, boolean animate, boolean hourSpring, boolean stagger) throws ReflectiveOperationException {
            if (next.equals(token)) return;
            String previous = token; cancel();
            visible.setText(next);
            if (outgoing != null) outgoing.setText(previous);
            token = next;
            if (digit) { ensureDigitEnvelope(); applyDigitPivots(); }
            if (digit && animate && !previous.isEmpty()) {
                nativeAccess.switchDigit.invoke(nativeAccess.controller, nativeDigit, hourSpring, stagger);
            } else {
                resetLayer(visible, true); if (outgoing != null) resetLayer(outgoing, false);
            }
            requestLayout();
        }
        void cancel() throws ReflectiveOperationException {
            if (digit) {
                for (TextView layer : new TextView[]{visible, outgoing}) {
                    Object transition = nativeAccess.getTransition.invoke(layer);
                    if (transition != null) nativeAccess.abort.invoke(nativeAccess.controller, transition, false);
                    resetLayer(layer, layer == visible);
                }
            }
        }
        void resetLayer(TextView layer, boolean shown) throws ReflectiveOperationException {
            layer.setScaleX(1f); layer.setScaleY(1f); layer.setAlpha(shown ? 1f : 0f);
            if (digit) nativeAccess.setBlur.invoke(layer, 0f);
            layer.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            if (digit) {
                ensureDigitEnvelope();
                nativeDigit.measure(MeasureSpec.makeMeasureSpec(envelopeWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(naturalHeight, MeasureSpec.EXACTLY));
                setMeasuredDimension(envelopeWidth, envelopeHeight);
                return;
            }
            visible.measure(free, free);
            naturalHeight = Math.max(1, visible.getMeasuredHeight());
            int width = Math.max(1, visible.getMeasuredWidth());
            inkBounds(visible, token, ink);
            inkTop = ink.top;
            int height = ink.isEmpty() ? Math.max(1, Math.round(size)) : Math.max(1, ink.height());
            setMeasuredDimension(width, height);
        }
        /** A numeral slot keeps one box for this font/size, independent of its current and old digit. */
        void ensureDigitEnvelope() {
            Paint paint = visible.getPaint();
            Typeface currentFace = paint.getTypeface(); float currentSize = visible.getTextSize();
            if (envelopeValid && envelopeTypeface == currentFace && envelopeSize == currentSize) return;
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            visible.measure(free, free); outgoing.measure(free, free);
            naturalHeight = Math.max(1, Math.max(visible.getMeasuredHeight(), outgoing.getMeasuredHeight()));
            envelopeBaseline = visible.getBaseline(); envelopeDrawOffset = nativeDrawOffset(visible);
            Rect bounds = new Rect(); ink.setEmpty(); float advance = 0f;
            for (int number = 0; number <= 9; number++) {
                String numeral = Integer.toString(number);
                paint.getTextBounds(numeral, 0, numeral.length(), bounds); ink.union(bounds);
                advance = Math.max(advance, paint.measureText(numeral));
            }
            envelopeWidth = Math.max(1, (int) Math.ceil(Math.max(advance, ink.width())));
            envelopeHeight = ink.isEmpty() ? Math.max(1, Math.round(currentSize)) : Math.max(1, ink.height());
            ink.offset(0, envelopeBaseline + envelopeDrawOffset); inkTop = ink.top;
            envelopeTypeface = currentFace; envelopeSize = currentSize;
            envelopeValid = true; pivotDirty = true;
        }
        int nativeDrawOffset(TextView layer) {
            if (nativeAccess.drawOffset != null) try {
                float offset = nativeAccess.drawOffset.getFloat(layer);
                return finite(offset) ? Math.round(offset) : 0;
            } catch (ReflectiveOperationException | RuntimeException error) { warn(error); }
            return 0;
        }
        boolean digitAnimating() {
            try {
                for (TextView layer : new TextView[]{visible, outgoing}) {
                    Object transition = nativeAccess.getTransition.invoke(layer);
                    if (transition != null && (nativeAccess.isAnimating == null
                            || Boolean.TRUE.equals(nativeAccess.isAnimating.invoke(transition)))) return true;
                }
                return false;
            } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return true; }
        }
        void applyDigitPivots() {
            if (!pivotDirty) return;
            for (TextView layer : new TextView[]{visible, outgoing}) {
                layer.setPivotX(envelopeWidth * .5f);
                layer.setPivotY(inkTop + envelopeHeight * .5f);
            }
            pivotDirty = false;
        }
        void inkBounds(TextView layer, String text, Rect bounds) {
            layer.getPaint().getTextBounds(text, 0, text.length(), bounds);
            int offset = layer.getBaseline();
            if (digit) offset += nativeDrawOffset(layer);
            bounds.offset(0, offset);
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (nativeDigit != null) {
                nativeDigit.layout(0, 0, getMeasuredWidth(), naturalHeight);
                nativeDigit.setTranslationY(-inkTop);
                if (pivotDirty && !digitAnimating()) applyDigitPivots();
            } else {
                visible.layout(0, 0, getMeasuredWidth(), naturalHeight); visible.setTranslationY(-inkTop);
            }
        }
        void applyShader(Shader shader) {
            for (TextView layer : outgoing == null ? new TextView[]{visible} : new TextView[]{visible, outgoing}) {
                NotificationNativeClock.this.applyShader(layer.getPaint(), shader, layer);
                if (digit && nativeAccess.getRtPaint != null) try {
                    Object rt = nativeAccess.getRtPaint.invoke(layer);
                    if (rt instanceof Paint && rt != layer.getPaint()) NotificationNativeClock.this.applyShader((Paint) rt, shader, layer);
                } catch (ReflectiveOperationException | RuntimeException error) { warn(error); }
            }
        }
    }

    /** No fields on the controller singleton are modified; all animation targets belong to this view. */
    private static final class NativeAccess {
        final LayoutInflater inflater;
        final Class<?> digitType;
        final Object controller;
        final int unitLayout, digitId;
        final Method attachOutgoing, getVisible, getOutgoing, switchDigit, getTransition, isAnimating, abort, setFont, setBlur, getRtPaint;
        final Field drawOffset;
        NativeAccess(Context plugin) throws ReflectiveOperationException {
            this(plugin, plugin.getClassLoader());
        }
        NativeAccess(Context plugin, ClassLoader loader) throws ReflectiveOperationException {
            digitType = loader.loadClass(DIGIT);
            Class<?> textType = loader.loadClass(TEXT), animationType = loader.loadClass(ANIMATION);
            Class<?> transitionType = loader.loadClass("com.oplus.keyguard.clock.common.transition.Transition");
            Field singleton = animationType.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
            controller = singleton.get(null);
            attachOutgoing = requiredMethod(digitType, "attachInvisibleTextView$KeyguardPersonalityClocks_release", LayoutInflater.class);
            getVisible = requiredMethod(digitType, "getVisibleTextView");
            getOutgoing = requiredMethod(digitType, "getInVisibleTextView");
            switchDigit = requiredMethod(animationType, "runDigitSwitchAnimation", digitType, Boolean.TYPE, Boolean.TYPE);
            getTransition = requiredMethod(textType, "getRenderSpringTransition");
            isAnimating = method(transitionType, "isAnimating");
            abort = requiredMethod(animationType, "abortIfAnimating", transitionType, Boolean.TYPE);
            setFont = requiredMethod(textType, "setFontStyle", Typeface.class, Boolean.TYPE, Boolean.TYPE);
            setBlur = requiredMethod(textType, "setBlurRatio", Float.TYPE);
            getRtPaint = method(textType, "getRtTextPaint"); drawOffset = field(textType, "fontMetricsDrawOffsetY");
            inflater = LayoutInflater.from(plugin).cloneInContext(plugin);
            unitLayout = plugin.getResources().getIdentifier("digital_clock_layout_single_clock_digital_time_unit_layout_base", "layout", PACKAGE);
            digitId = plugin.getResources().getIdentifier("digital_time_view", "id", PACKAGE);
            if (unitLayout == 0 || digitId == 0 || controller == null)
                throw new IllegalStateException("Native clock plugin resources unavailable");
        }
    }

    private static final class Token {
        final String text;
        final boolean digit;
        final int start;
        Token(String text, boolean digit, int start) { this.text = text; this.digit = digit; this.start = start; }
    }

    private static List<Token> tokenize(String value) {
        List<Token> result = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int point = value.codePointAt(start), end = start + Character.charCount(point);
            boolean digit = Character.isDigit(point);
            if (!digit) while (end < value.length() && !Character.isDigit(value.codePointAt(end)))
                end += Character.charCount(value.codePointAt(end));
            result.add(new Token(value.substring(start, end), digit, start)); start = end;
        }
        return result;
    }

    private static final class TimeRange {
        final String hour;
        final int hourStart, hourEnd, minuteStart, minuteEnd;
        TimeRange(Matcher match) {
            hour = match.group(1); hourStart = match.start(1); hourEnd = match.end(1);
            minuteStart = match.start(2); minuteEnd = match.end(2);
        }
        static TimeRange of(String value) { Matcher match = TIME.matcher(value); return match.find() ? new TimeRange(match) : null; }
    }

    private void fail(Throwable error) {
        available = false; unregisterBorderLayers(); cancel(); restoreShaders(); setVisibility(View.GONE); warn(error);
    }
    private void warn(Throwable error) {
        if (warnings++ < 3) ModuleDiagnostics.error("bigclock", "Native clock digit update unavailable; existing clock retained", error);
    }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    private static int safeAdd(int first, int second) { return (int) Math.max(0L, Math.min(Integer.MAX_VALUE / 4L, (long) first + second)); }
    private static Method requiredMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method result = method(type, name, parameters);
        if (result == null) throw new NoSuchMethodException(name); return result;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        for (; type != null; type = type.getSuperclass()) try {
            Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
    private static Field field(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }
}
