package dev.puitheme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView;
import com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne;
import com.oplus.systemui.qs.base.res.SepQSResPool;
import com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable;
import com.oplus.systemui.qs.base.res.drawable.TileDrawableWrapper;
import com.oplusos.systemui.common.outline.RoundRectOutlineProvider;
import com.oplusos.systemui.common.util.QSBlurConfigProvider;
import java.util.Objects;
import org.json.JSONObject;

/** Native pool -> fresh builder -> shaped wrapper, current labels and exact restore paths. */
public final class QsTileFullFillCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++; if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static final class Pair {
        final Object first, second;
        public Pair(Object first, Object second) { this.first = first; this.second = second; }
        public Object getFirst() { return first; }
        public Object getSecond() { return second; }
    }
    public static final class Foreground { public int topLayerColor; Foreground(int color) { topLayerColor = color; } }
    public static final class Mix { public final Foreground foregroundShaderParam; Mix(int color) { foregroundShaderParam = new Foreground(color); } }
    public static final class AnimatedFill extends MixColorTileDrawable {
        public Object currentConfig;
        AnimatedFill() { maskColor = 0xff111111; currentConfig = new Pair(null, 0xffffffff); }
    }
    public static final class AnimatedBuilder {
        public AnimatedFill fill;
        public Drawable build(View body, boolean animated, Integer fixedState) { fill = new AnimatedFill(); return fill; }
    }
    public static final class State { public int state = 2; }
    public static final class Icon extends ViewGroup {
        // Real native field is private; background and glyph are distinct sibling views.
        private View iconBgView = new View(new Context());
        final View iconView = new View(new Context());
        public final MixColorTileDrawable circle = new MixColorTileDrawable();
        Icon() { super(new Context()); iconBgView.setBackground(circle); addView(iconBgView); addView(iconView); }
        public Drawable getIconBgDrawable() { return circle; }
    }
    public static final class Wrapper extends TileDrawableWrapper {
        Drawable child; int alpha = 173, writes; boolean failAfterSet;
        Wrapper(Drawable child) { super(new RoundRectOutlineProvider(24f, 3f)); this.child = child; }
        public Drawable getDrawable() { return child; }
        public void setDrawable(Drawable value) {
            writes++; child = value;
            if (value != null) { value.setBounds(getBounds()); value.setState(getState()); value.setAlpha(alpha); }
            if (failAfterSet) { failAfterSet = false; throw new IllegalStateException("partial native setter"); }
        }
        @Override public void setAlpha(int value) { alpha = value; if (child != null) child.setAlpha(value); }
        @Override public int getAlpha() { return alpha; }
    }
    public static final class Tile extends OplusQSResizeableTileViewTwoXOne {
        public final State current = new State();
        public final Icon glyph = new Icon();
        public final android.content.res.Resources resources=new android.content.res.Resources();
        public final View body = new View(new Context(){public android.content.res.Resources getResources(){return resources;}});
        public final ViewGroup labelTitle = new ViewGroup(new Context()), labelDesc = new ViewGroup(new Context());
        public final Object manager = new Object();
        public Wrapper background = new Wrapper(new MixColorTileDrawable()), themeBackground = new Wrapper(null);
        public boolean isBeingDragged, edit;
        public int nativeLabelUpdates;
        public Pair nativePair = new Pair(ColorStateList.valueOf(0xffffffff), ColorStateList.valueOf(0x99ffffff));
        public QsTileFullFill owner;
        Tile() {
            labelTitle.addView(new TextView(new Context())); labelTitle.addView(new TextView(new Context()));
            labelDesc.addView(new TextView(new Context())); labelDesc.addView(new TextView(new Context()));
            glyph.iconBgView.setAlpha(.7f); background.setBounds(0, 0, 552, 248);
            // Device: the native 2x1 fill is already cached on the body tag.
            QSBlurConfigProvider.setBlurDrawableTag(body, background.child);
            handleLabelColorChanged(nativePair);
        }
        @Override public Object getTileState() { return current; }
        @Override public Object getIconView() { return glyph; }
        @Override public Drawable getBgDrawable() { return background; }
        @Override public Drawable getThemeDrawable() { return themeBackground; }
        @Override public View getBg() { return body; }
        @Override public android.content.res.Resources getResources(){return resources;}
        public Object getLabelResourceManager() { return manager; }
        public boolean isEditMode() { return edit; }
        public void refreshTileInnerLabelColor(Object ignored) {
            nativeLabelUpdates++;
            handleLabelColorChanged(owner == null ? nativePair : (Pair) owner.labelColors(manager, current, nativePair));
        }
        public void handleLabelColorChanged(Pair value) {
            for (int i = 0; i < labelTitle.getChildCount(); i++) ((TextView) labelTitle.getChildAt(i)).setTextColor((ColorStateList) value.first);
            for (int i = 0; i < labelDesc.getChildCount(); i++) ((TextView) labelDesc.getChildAt(i)).setTextColor((ColorStateList) value.second);
        }
        TextView title() { return (TextView) labelTitle.getChildAt(0); }
        TextView desc() { return (TextView) labelDesc.getChildAt(1); }
    }
    private static Bundle settings(boolean enabled, boolean appearance) {
        Bundle values = new Bundle(); values.putBoolean(QsTileFullFill.ENABLED, enabled);
        values.putBoolean(QsTileAppearance.MASTER, appearance);
        values.putInt("qs_global_light_color", 0xff123456); values.putInt("qs_global_dark_color", 0xffffffff);
        return values;
    }
    private static SepQSResPool.Builder resetPool() {
        SepQSResPool.Builder builder = new SepQSResPool.Builder();
        SepQSResPool.INSTANCE.normal.value = builder; SepQSResPool.INSTANCE.theme.value = null;
        return builder;
    }
    private static void lifecycle() {
        SepQSResPool.Builder builder = resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        Drawable original = tile.background.child;
        appearance.configure(settings(false, false)); full.configure(settings(false, false)); full.refreshTile(tile);
        equal(original, tile.background.child); equal(0, builder.builds);
        appearance.configure(settings(true, false)); full.configure(settings(true, false));
        Drawable replacement = tile.background.child;
        equal(true, replacement != original); equal(1, builder.builds);
        equal(tile.body, builder.body); equal(true, builder.animated); equal(null, builder.fixedState);
        equal(View.INVISIBLE, tile.glyph.iconBgView.getVisibility()); equal(.7f, tile.glyph.iconBgView.getAlpha()); equal(0xff000000, tile.title().getCurrentTextColor());
        equal(0x99000000, tile.desc().getCurrentTextColor()); equal(true, tile.nativeLabelUpdates > 0);
        equal(173, tile.background.getAlpha()); equal(24f, tile.background.getPathProvider().getCornerRadius(tile.background));
        int writes = tile.background.writes, invalidations = tile.invalidations, labels = tile.nativeLabelUpdates;
        for (int i = 0; i < 1000; i++) full.measuredTile(tile);
        equal(writes, tile.background.writes); equal(invalidations, tile.invalidations); equal(labels, tile.nativeLabelUpdates); equal(1, builder.builds);
        tile.current.state = 1; tile.background.setState(new int[]{1}); full.refreshTile(tile);
        equal(original, tile.background.child); equal(1, original.getState()[0]); equal(.7f, tile.glyph.iconBgView.getAlpha());
        equal(View.INVISIBLE, tile.glyph.iconBgView.getVisibility());
        equal(0xffffffff, tile.title().getCurrentTextColor()); equal(0x99ffffff, tile.desc().getCurrentTextColor());
        tile.current.state = 2; full.refreshTile(tile); equal(2, builder.builds);
        tile.nativePair = new Pair(ColorStateList.valueOf(0xffeeddaa), ColorStateList.valueOf(0x88ddeeff));
        full.refreshTile(tile); full.configure(settings(false, false));
        equal(original, tile.background.child); equal(0xffeeddaa, tile.title().getCurrentTextColor()); equal(0x88ddeeff, tile.desc().getCurrentTextColor());
        full.release();
    }
    private static void nativeThemeContract() {
        SepQSResPool.Builder normal = resetPool(), theme = new SepQSResPool.Builder();
        theme.color = 0xff001166;
        SepQSResPool.INSTANCE.theme.value = theme;
        QsTileAppearance appearance = new QsTileAppearance(); QsTileFullFill full = new QsTileFullFill(appearance);
        Tile tile = new Tile(); tile.owner = full;
        Drawable normalOriginal = tile.background.child, themeOriginal = new MixColorTileDrawable();
        tile.themeBackground.setDrawable(themeOriginal);
        // Native onThemeApplied hides the normal white layer and displays the dark theme layer.
        tile.background.setAlpha(0); tile.themeBackground.setAlpha(255);
        full.configure(settings(true, false)); full.refreshTile(tile);
        equal(1, normal.builds); equal(1, theme.builds);
        equal(true, normal.animated); equal(false, theme.animated);
        equal(tile.body, normal.body); equal(tile.body, theme.body);
        equal(null, normal.fixedState); equal(null, theme.fixedState);
        equal(true, tile.background.child != normalOriginal); equal(true, tile.themeBackground.child != themeOriginal);
        equal(true, tile.background.child != tile.themeBackground.child);
        equal(0xff000000, tile.title().getCurrentTextColor()); equal(0x99000000, tile.desc().getCurrentTextColor());
        tile.background.setAlpha(255); tile.themeBackground.setAlpha(0); full.refreshTile(tile);
        equal(0xff000000, tile.title().getCurrentTextColor()); equal(0x99000000, tile.desc().getCurrentTextColor());
        tile.background.setAlpha(64); tile.themeBackground.setAlpha(191); full.refreshTile(tile);
        equal(0xff000000, tile.title().getCurrentTextColor());
        tile.background.setAlpha(0);
        ((MixColorTileDrawable) tile.themeBackground.child).maskColor = 0;
        tile.nativePair = new Pair(ColorStateList.valueOf(0xffeeddaa), ColorStateList.valueOf(0x88ddeeff));
        full.refreshTile(tile);
        equal(0xff000000, tile.title().getCurrentTextColor()); equal(0x88000000, tile.desc().getCurrentTextColor());
        full.configure(settings(false, false));
        equal(normalOriginal, tile.background.child); equal(themeOriginal, tile.themeBackground.child);
        full.release();
    }
    private static void nativeReplacementAndFailures() {
        SepQSResPool.Builder builder = resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        full.configure(settings(true, false)); full.refreshTile(tile);
        Drawable newerNative = new MixColorTileDrawable(); tile.background.setDrawable(newerNative); full.refreshTile(tile);
        equal(true, tile.background.child != newerNative);
        full.configure(settings(false, false)); equal(newerNative, tile.background.child);
        full.configure(settings(true, false));
        Drawable nativeAtFailure = new MixColorTileDrawable(); tile.background.setDrawable(nativeAtFailure); tile.background.failAfterSet = true;
        full.refreshTile(tile); equal(nativeAtFailure, tile.background.child); equal(.7f, tile.glyph.iconBgView.getAlpha());
        builder.fail = true; full.refreshTile(tile); equal(nativeAtFailure, tile.background.child);
        builder.fail = false; full.refreshTile(tile); equal(true, nativeAtFailure != tile.background.child);
        Wrapper newerWrapper = new Wrapper(new MixColorTileDrawable()); Drawable newerOriginal = newerWrapper.child;
        Wrapper oldWrapper = tile.background; tile.background = newerWrapper; full.refreshTile(tile);
        equal(nativeAtFailure, oldWrapper.child); equal(true, newerOriginal != newerWrapper.child);
        full.detach(tile); equal(newerOriginal, newerWrapper.child); equal(.7f, tile.glyph.iconBgView.getAlpha());
        full.release();
    }
    private static void nativeAnimatedTargetAndRestore() {
        resetPool(); AnimatedBuilder builder = new AnimatedBuilder(); SepQSResPool.INSTANCE.normal.value = builder;
        QsTileAppearance appearance = new QsTileAppearance(); QsTileFullFill full = new QsTileFullFill(appearance);
        Tile tile = new Tile(); tile.owner = full;
        // Actual device TextSwitcher has two direct TextViews, including its hidden outgoing view.
        tile.nativePair = new Pair(ColorStateList.valueOf(0xe6ffffff), ColorStateList.valueOf(0x89ffffff));
        tile.handleLabelColorChanged(tile.nativePair);
        Drawable original = tile.background.child;
        full.configure(settings(true, false)); full.refreshTile(tile);
        equal(0xff111111, builder.fill.maskColor); // Native animation still displays its dark start color.
        for (int i = 0; i < 2; i++) {
            equal(0xe6000000, ((TextView) tile.labelTitle.getChildAt(i)).getCurrentTextColor());
            equal(0x89000000, ((TextView) tile.labelDesc.getChildAt(i)).getCurrentTextColor());
        }
        builder.fill.maskColor = 0xffffffff; // Animation ends without a tile-state/layout callback.
        full.measuredTile(tile);
        equal(0xe6000000, tile.title().getCurrentTextColor());
        builder.fill.currentConfig = new Pair(null, 0xff001166); full.refreshTile(tile);
        equal(0xe6000000, tile.title().getCurrentTextColor()); // Requested black even for a dark theme target.
        builder.fill.maskColor = 0xff001166;
        builder.fill.currentConfig = new Pair(new Mix(0xffffffff), 0); full.refreshTile(tile);
        equal(0xe6000000, tile.title().getCurrentTextColor()); // Foreground material does not choose label RGB.
        builder.fill.currentConfig = new Pair(null, 0); full.refreshTile(tile);
        equal(0xe6000000, tile.title().getCurrentTextColor());
        builder.fill.currentConfig = new Pair(null, 0xffffffff); full.refreshTile(tile);
        full.configure(settings(false, false));
        equal(original, tile.background.child); equal(.7f, tile.glyph.iconBgView.getAlpha());
        for (int i = 0; i < 2; i++) {
            equal(0xe6ffffff, ((TextView) tile.labelTitle.getChildAt(i)).getCurrentTextColor());
            equal(0x89ffffff, ((TextView) tile.labelDesc.getChildAt(i)).getCurrentTextColor());
        }
        full.release();
    }
    private static void stylesAndIsolation() throws Throwable {
        SepQSResPool.Builder builder = resetPool(); builder.color = 0xff001166;
        QsTileAppearance appearance = new QsTileAppearance(); QsTileFullFill full = new QsTileFullFill(appearance);
        Tile tile = new Tile(); tile.owner = full; full.configure(settings(true, false)); full.refreshTile(tile);
        equal(0xff000000, tile.title().getCurrentTextColor()); // Requested black regardless of native accent.
        Bundle custom = settings(true, true); custom.putBoolean("qs_global_light_gradient_enabled", true);
        custom.putInt("qs_global_light_color", 0xffffffff); custom.putInt("qs_global_light_gradient_color", 0xffdddddd);
        custom.putFloat("qs_global_light_opacity", 50f); appearance.configure(custom); full.configure(custom);
        equal(0xff000000, tile.title().getCurrentTextColor());
        QsTileCardFill fill=(QsTileCardFill)tile.background.child;
        com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable blur=
                ((com.oplus.systemui.plugins.drawable.MixColorPluginDrawable)fill.getDrawable()).blur();
        com.oplus.posteffect.drawable.BlendDrawable engine=blur.viewBlurProxy.actual.blurDrawable;engine.setBounds(0,0,552,248);
        appearance.drawBlur(blur,new Canvas(),blur::draw);appearance.drawGlassContent(engine,new Canvas(),engine::onDrawContent);
        LinearGradient gradient = (LinearGradient)engine.recorded.inputs.get("c17QsFill");
        equal(0x80ffffff, gradient.colors[0]); equal(0x80dddddd, gradient.colors[1]); equal(engine.drawableShader.shader!=engine.recorded,true);
        tile.current.state = 1; full.refreshTile(tile);
        appearance.drawGlassContent(engine,new Canvas(),engine::onDrawContent); equal(engine.drawableShader.shader,engine.recorded);
        OplusQSResizeableTileView other = new OplusQSResizeableTileView(); other.state = new State();
        Drawable otherFill = new MixColorTileDrawable(); other.bg = otherFill; full.refreshTile(other); equal(otherFill, other.bg);
        tile.current.state = 2; full.refreshTile(tile); tile.edit = true; full.refreshTile(tile); equal(.7f, tile.glyph.iconBgView.getAlpha());
        full.release();
    }
    private static void circleAndLatestNativeRestore() {
        resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        View circle = tile.glyph.iconBgView, glyph = tile.glyph.iconView;
        Drawable original = circle.getBackground(), foreground = new MixColorTileDrawable();
        circle.setForeground(foreground);
        full.configure(settings(true, false)); full.refreshTile(tile);
        // Hide the entire native circle view (both materials), not just a drawable layer.
        equal(View.INVISIBLE, circle.getVisibility()); equal(.7f, circle.getAlpha());
        equal(original, circle.getBackground()); equal(foreground, circle.getForeground());
        equal(View.VISIBLE, glyph.getVisibility()); equal(1f, glyph.getAlpha());
        // Native theme/press updates must remain live while the view is hidden.
        Drawable latest = new MixColorTileDrawable(); circle.setBackground(latest); circle.setAlpha(.35f);
        tile.nativePair = new Pair(ColorStateList.valueOf(0xddeeffff), ColorStateList.valueOf(0x77010203));
        full.refreshTile(tile); equal(View.INVISIBLE, circle.getVisibility()); equal(.35f, circle.getAlpha());
        equal(0xdd000000, tile.title().getCurrentTextColor()); equal(0x77000000, tile.desc().getCurrentTextColor());
        tile.isBeingDragged = true; full.refreshTile(tile);
        equal(View.INVISIBLE, circle.getVisibility()); equal(.35f, circle.getAlpha()); equal(latest, circle.getBackground());
        equal(0xdd000000, tile.title().getCurrentTextColor()); equal(0x77000000, tile.desc().getCurrentTextColor());
        tile.isBeingDragged = false; full.refreshTile(tile); tile.edit = true; full.refreshTile(tile);
        equal(View.INVISIBLE, circle.getVisibility()); equal(.35f, circle.getAlpha());
        tile.edit = false; full.refreshTile(tile); tile.current.state = 1; full.refreshTile(tile);
        // Inactive 2x1 tiles keep the native fill and labels, but the circle stays hidden too.
        equal(View.INVISIBLE, circle.getVisibility()); equal(latest, circle.getBackground()); equal(.35f, circle.getAlpha());
        equal(0xddeeffff, tile.title().getCurrentTextColor()); equal(0x77010203, tile.desc().getCurrentTextColor());
        tile.edit = true; full.refreshTile(tile); equal(View.INVISIBLE, circle.getVisibility());
        tile.edit = false; full.refreshTile(tile); equal(View.INVISIBLE, circle.getVisibility());
        tile.current.state = 0; full.refreshTile(tile); equal(View.INVISIBLE, circle.getVisibility());
        full.configure(settings(false, false)); equal(View.VISIBLE, circle.getVisibility());
        full.configure(settings(true, false)); equal(View.INVISIBLE, circle.getVisibility());
        tile.current.state = 2; full.refreshTile(tile); equal(View.INVISIBLE, circle.getVisibility());
        equal(0xdd000000, tile.title().getCurrentTextColor());
        tile.current.state = 1; full.refreshTile(tile); full.detach(tile); equal(View.VISIBLE, circle.getVisibility());
        tile.current.state = 2; full.refreshTile(tile); full.configure(settings(false, false));
        equal(View.VISIBLE, circle.getVisibility()); equal(.35f, circle.getAlpha());
        full.configure(settings(true, false)); circle.setVisibility(View.GONE); full.refreshTile(tile);
        full.detach(tile); equal(View.GONE, circle.getVisibility()); // New native visibility wins.
        circle.setVisibility(View.INVISIBLE); full.refreshTile(tile); full.detach(tile);
        equal(View.INVISIBLE, circle.getVisibility()); // Originally hidden stays hidden.
        circle.setVisibility(View.VISIBLE); full.refreshTile(tile);
        View replacement = new View(new Context()); replacement.setAlpha(.4f);
        tile.glyph.iconBgView = replacement; full.refreshTile(tile);
        equal(View.VISIBLE, circle.getVisibility()); equal(.35f, circle.getAlpha());
        equal(View.INVISIBLE, replacement.getVisibility()); equal(.4f, replacement.getAlpha());
        full.release(); equal(View.VISIBLE, replacement.getVisibility()); equal(.4f, replacement.getAlpha());
    }
    private static void noFillAndOtherCardsStayNative() {
        SepQSResPool.Builder builder = resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); full.configure(settings(true, false));
        com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne one =
                new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne();
        Icon icon = new Icon(); one.icon = icon; one.state = new State();
        Drawable original = new MixColorTileDrawable(); one.bg = original;
        full.refreshTile(one); full.measuredTile(one);
        equal(original, one.bg); equal(View.VISIBLE, icon.iconBgView.getVisibility()); equal(1f, icon.iconBgView.getAlpha());
        com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView headphone =
                new com.oplus.deviceplugin.sdk.ui.view.separatecardview.RectangleDeviceCardView();
        headphone.body.setBackground(original);
        full.refreshTile(headphone); full.measuredTile(headphone);
        equal(original, headphone.body.getBackground()); equal(View.VISIBLE, headphone.getVisibility());
        equal(0, builder.builds);
        // A registered 2x1 manager without a successful full fill must keep its native labels.
        Tile failed = new Tile(); failed.owner = full; builder.fail = true;
        full.refreshTile(failed);
        equal(failed.nativePair, full.labelColors(failed.manager, failed.current, failed.nativePair));
        equal(0xffffffff, failed.title().getCurrentTextColor()); equal(View.VISIBLE, failed.glyph.iconBgView.getVisibility());
        builder.fail = false; failed.glyph.iconBgView = null; Drawable bodyOriginal = failed.background.child;
        full.refreshTile(failed);
        equal(bodyOriginal, failed.background.child); equal(0xffffffff, failed.title().getCurrentTextColor());
        full.release();
    }
    private static void nativeRecycleBoundary() throws Throwable {
        resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        Drawable original = tile.background.child; View circle = tile.glyph.iconBgView;
        full.configure(settings(true, false)); full.refreshTile(tile);
        Object expected = new Object();
        Object result = full.aroundDetach(tile, () -> {
            equal(original, tile.background.child); equal(View.VISIBLE, circle.getVisibility());
            // Both TwoXOne and its base have the same recycle hook.
            return full.aroundDetach(tile, () -> {
                tile.nativePair = new Pair(ColorStateList.valueOf(0xe6abcdef), ColorStateList.valueOf(0x89123456));
                // Native onRecycle calls setBeingDragged(false), whose hook refreshes the tile.
                tile.isBeingDragged = false; full.refreshTile(tile);
                equal(true, original != tile.background.child); equal(View.INVISIBLE, circle.getVisibility());
                equal(0xe6000000, tile.title().getCurrentTextColor()); equal(0x89000000, tile.desc().getCurrentTextColor());
                return expected;
            });
        });
        equal(expected, result); equal(original, tile.background.child); equal(View.VISIBLE, circle.getVisibility());
        equal(.7f, circle.getAlpha()); equal(0xe6abcdef, tile.title().getCurrentTextColor()); equal(0x89123456, tile.desc().getCurrentTextColor());
        equal(tile.nativePair, full.labelColors(tile.manager, tile.current, tile.nativePair));
        full.refreshTile(tile);
        Throwable failure = new IllegalStateException("native recycle failure");
        try {
            full.aroundDetach(tile, () -> full.aroundDetach(tile, () -> {
                tile.nativePair = new Pair(ColorStateList.valueOf(0xddfedcba), ColorStateList.valueOf(0x77112233));
                full.refreshTile(tile);
                equal(View.INVISIBLE, circle.getVisibility());
                throw failure;
            }));
            throw new AssertionError("Native recycle failure was swallowed");
        } catch (Throwable actual) { equal(failure, actual); }
        equal(original, tile.background.child); equal(View.VISIBLE, circle.getVisibility()); equal(.7f, circle.getAlpha());
        equal(0xddfedcba, tile.title().getCurrentTextColor()); equal(0x77112233, tile.desc().getCurrentTextColor());
        equal(tile.nativePair, full.labelColors(tile.manager, tile.current, tile.nativePair));
        full.release();
    }
    private static void backgroundDispatch() {
        resetPool(); QsTileAppearance appearance = new QsTileAppearance(); QsTileFullFill full = new QsTileFullFill(appearance);
        Tile tile = new Tile(); tile.owner = full; Drawable original = tile.background.child; full.configure(settings(true, false));
        Looper.setCurrentForCheck(new Looper());
        try { for (int i = 0; i < 1000; i++) full.refreshTile(tile); equal(original, tile.background.child); equal(1, Handler.postedForCheck.size()); }
        finally { Looper.resetCurrentForCheck(); }
        Handler.drainForCheck(); equal(true, original != tile.background.child);
        Looper.setCurrentForCheck(new Looper());
        try { full.detach(tile); equal(true, original != tile.background.child); }
        finally { Looper.resetCurrentForCheck(); }
        Handler.drainForCheck(); equal(original, tile.background.child); full.release();
    }
    /** Device code158: the native builder returned the body's tagged 2x1 fill, so nothing filled. */
    private static void nativeTagCache() {
        SepQSResPool.Builder builder = resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        Drawable original = tile.background.child, gradient = new MixColorTileDrawable();
        QSBlurConfigProvider.setGradientDrawableTag(tile.body, gradient);
        full.configure(settings(true, false)); full.refreshTile(tile);
        equal(true, tile.background.child != original); equal(0, builder.cachedReturns);
        equal(original, QSBlurConfigProvider.getBlurDrawableTag(tile.body)); equal(gradient, QSBlurConfigProvider.getGradientDrawableTag(tile.body));
        equal(View.INVISIBLE, tile.glyph.iconBgView.getVisibility());
        equal(0xff000000, tile.title().getCurrentTextColor()); equal(0x99000000, tile.desc().getCurrentTextColor());
        // Native rebuilding through its own cache still receives its original fill.
        equal(original, builder.build(tile.body, true, null)); equal(1, builder.cachedReturns);
        builder.fail = true; tile.current.state = 1; full.refreshTile(tile); tile.current.state = 2; full.refreshTile(tile);
        equal(original, tile.background.child); equal(View.VISIBLE, tile.glyph.iconBgView.getVisibility());
        equal(original, QSBlurConfigProvider.getBlurDrawableTag(tile.body)); equal(gradient, QSBlurConfigProvider.getGradientDrawableTag(tile.body));
        builder.fail = false; full.configure(settings(true, false)); equal(true, tile.background.child != original);
        full.configure(settings(false, false)); equal(original, tile.background.child); full.release();
    }
    /** Device code161: the full fill must be one opaque colour, not translucent glass. */
    private static void pureFill() {
        resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        Drawable original = tile.background.child;
        full.configure(settings(true, false)); full.refreshTile(tile);
        Drawable fill = tile.background.child;
        equal(false, full.drawPure(original, new Canvas()));
        Canvas canvas = new Canvas();
        equal(true, full.drawPure(fill, canvas));
        equal(1, canvas.roundRects); equal(0xffffffff, canvas.lastRoundPaint.getColor() | 0xff000000);
        equal(255, canvas.lastRoundPaint.getAlpha()); // Native wrapper alpha 173 is this fill's peak: fully opaque.
        equal(24f, canvas.lastRoundRadiusX); // Native shape radius.
        fill.setAlpha(87); Canvas fading = new Canvas(); full.drawPure(fill, fading);
        equal(128, fading.lastRoundPaint.getAlpha()); // Native fades stay proportional.
        equal(null, canvas.lastRoundPaint.getShader()); // No tile colour: plain opaque white.
        // Device code162: with a tile colour the 2x1 fill is the native glass the 1x1 tiles use, coloured
        // by the same appearance code; the module paints nothing of its own over it.
        Bundle custom = settings(true, true); custom.putInt("qs_global_light_color", 0x40336699);
        appearance.configure(custom); full.configure(custom);
        Drawable customFill = tile.background.child; customFill.setAlpha(173);
        Canvas colored = new Canvas();
        equal(false, full.drawPure(customFill, colored)); equal(0, colored.roundRects);
        tile.current.state = 1; full.refreshTile(tile);
        equal(false, full.drawPure(customFill, new Canvas())); // Inactive: native glass again.
        full.release();
    }
    private static void glassOwnedFill() throws Exception {
        resetPool(); QsTileAppearance appearance = new QsTileAppearance();
        QsTileFullFill full = new QsTileFullFill(appearance); Tile tile = new Tile(); tile.owner = full;
        Drawable original = tile.background.child;
        Bundle saved = settings(true, false);
        appearance.configure(saved); full.configure(saved); full.refreshTile(tile);
        Drawable replacement = tile.background.child;
        equal(true, replacement != original);
        equal(true, full.drawPure(replacement, new Canvas()));
        int invalidations = tile.invalidations;

        Bundle glass = CustomPluginMaterialIsolation.apply(saved, new JSONObject()
                .put("system_glass", true).put("cc_glass", true));
        appearance.configure(glass); full.configure(glass);
        equal(replacement, tile.background.child);
        equal(View.INVISIBLE, tile.glyph.iconBgView.getVisibility());
        Canvas frame = new Canvas();
        if (!full.drawPure(replacement, frame)) replacement.draw(frame);
        equal(0, frame.roundRects);
        equal(173, ((MixColorTileDrawable) replacement).seenAlpha);
        equal(true, tile.invalidations > invalidations);

        invalidations = tile.invalidations;
        Bundle glassOff = CustomPluginMaterialIsolation.apply(saved, new JSONObject()
                .put("system_glass", true).put("nc_glass", true));
        appearance.configure(glassOff); full.configure(glassOff);
        equal(true, full.drawPure(replacement, new Canvas()));
        equal(true, tile.invalidations > invalidations);
        equal(View.INVISIBLE, tile.glyph.iconBgView.getVisibility());
        full.release();
        equal(original, tile.background.child);
    }
    public static void main(String[] args) throws Throwable {
        deviceCardMaterial(); nativeTagCache(); pureFill(); glassOwnedFill(); lifecycle(); nativeThemeContract(); nativeReplacementAndFailures(); nativeAnimatedTargetAndRestore(); stylesAndIsolation(); circleAndLatestNativeRestore(); noFillAndOtherCardsStayNative(); nativeRecycleBoundary(); backgroundDispatch();
        System.out.println("QsTileFullFillCheck: " + checks + " checks passed");
    }
    /** Current ROM's earbuds use a complete PluginDrawable, not a stretched OneXOne tile primitive. */
    private static void deviceCardMaterial() {
        com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds=0;
        com.oplus.deviceplugin.sdk.ui.view.drawable.b.fail=false;
        resetPool(); QsTileAppearance appearance=new QsTileAppearance();
        QsTileFullFill full=new QsTileFullFill(appearance); Tile tile=new Tile(); tile.owner=full;
        Drawable original=tile.background.child;
        Bundle custom=settings(true,true); appearance.configure(custom); full.configure(custom); full.refreshTile(tile);
        equal(1,com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds);
        equal(tile.body,com.oplus.deviceplugin.sdk.ui.view.drawable.b.body);
        equal(com.oplus.deviceplugin.sdk.ui.view.drawable.c.b,com.oplus.deviceplugin.sdk.ui.view.drawable.b.state);
        equal(original,QSBlurConfigProvider.getBlurDrawableTag(tile.body));
        equal(0,((SepQSResPool.Builder)SepQSResPool.INSTANCE.normal.value).builds);
        QsTileCardFill fill=(QsTileCardFill)tile.background.child;
        com.oplus.systemui.plugins.drawable.MixColorPluginDrawable material=
                (com.oplus.systemui.plugins.drawable.MixColorPluginDrawable)fill.getDrawable();
        Canvas canvas=new Canvas();fill.draw(canvas);
        equal(1,material.draws);equal(0,canvas.roundRects);equal(0,canvas.layers);
        equal(173,material.alpha);equal(24f,material.radius);equal(3f,material.weight);
        equal(552,material.getBounds().width());equal(248,material.getBounds().height());
        equal(24f,material.blur().viewBlurProxy.visibleScalar);
        for(float corner:material.blur().viewBlurProxy.visibleCorners)equal(24f,corner);
        int cornerCalls=material.cornerUpdates,applies=material.blur().viewBlurProxy.applied;
        for(int i=0;i<1000;i++)fill.draw(new Canvas());
        equal(cornerCalls,material.cornerUpdates);equal(applies,material.blur().viewBlurProxy.applied);
        tile.background.getPathProvider().radius=32f;tile.background.getPathProvider().weight=2f;fill.draw(new Canvas());
        equal(32f,material.radius);equal(2f,material.weight);equal(32f,material.blur().viewBlurProxy.visibleScalar);
        fill.setAlpha(64);equal(64,material.alpha);equal(64,fill.getAlpha());
        tile.resources.getConfiguration().uiMode=android.content.res.Configuration.UI_MODE_NIGHT_YES;
        full.refreshTile(tile);equal(2,com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds);
        equal(true,com.oplus.deviceplugin.sdk.ui.view.drawable.b.night);
        Drawable dark=tile.background.child;full.refreshTile(tile);equal(dark,tile.background.child);
        equal(2,com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds);
        // Inactive/unavailable never acquire an active card material.
        tile.current.state=1;full.refreshTile(tile);equal(original,tile.background.child);
        tile.current.state=0;full.refreshTile(tile);equal(original,tile.background.child);
        equal(2,com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds);
        tile.current.state=2;full.refreshTile(tile);equal(3,com.oplus.deviceplugin.sdk.ui.view.drawable.b.builds);
        // Native factory failure restores the original fill and labels, and retries after recovery.
        com.oplus.deviceplugin.sdk.ui.view.drawable.b.fail=true;
        tile.resources.getConfiguration().uiMode=android.content.res.Configuration.UI_MODE_NIGHT_NO;
        full.refreshTile(tile);equal(original,tile.background.child);equal(View.VISIBLE,tile.glyph.iconBgView.getVisibility());
        com.oplus.deviceplugin.sdk.ui.view.drawable.b.fail=false;
        full.configure(custom);equal(true,tile.background.child instanceof QsTileCardFill);
        // Disabling the colour returns to the established white fill; disabling full-fill restores native.
        Bundle white=settings(true,false);appearance.configure(white);full.configure(white);
        equal(true,tile.background.child instanceof MixColorTileDrawable);
        equal(true,full.drawPure(tile.background.child,new Canvas()));
        appearance.configure(custom);full.configure(custom);
        Drawable nativeTheme=new MixColorTileDrawable();tile.themeBackground.child=nativeTheme;
        tile.themeBackground.setBounds(0,0,552,248);tile.background.setAlpha(64);tile.themeBackground.setAlpha(191);
        full.refreshTile(tile);
        equal(true,tile.themeBackground.child instanceof QsTileCardFill);
        equal(64,((com.oplus.systemui.plugins.drawable.PluginDrawable)((QsTileCardFill)tile.background.child).getDrawable()).alpha);
        equal(191,((com.oplus.systemui.plugins.drawable.PluginDrawable)((QsTileCardFill)tile.themeBackground.child).getDrawable()).alpha);
        full.detach(tile);equal(original,tile.background.child);equal(nativeTheme,tile.themeBackground.child);
        equal(View.VISIBLE,tile.glyph.iconBgView.getVisibility());
        full.release();
    }
}
