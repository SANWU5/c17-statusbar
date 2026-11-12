package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.oplus.systemui.notification.lockscreen.capsule.CapsuleBackgroundHelper;
import com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView;
import com.oplus.systemui.plugins.shared.template.section.media.K0;
import com.oplus.systemui.plugins.shared.template.section.media.s1;

/** The lock-screen capsule: one tint after the native draw, with the fluid-cloud colour decision. */
public final class LockCapsuleTintCheck {
    private static int checks;
    private static void check(boolean okay, String why) { checks++; if (!okay) throw new AssertionError(why); }
    private static void same(int expected, int actual, String why) {
        checks++; if (expected != actual) throw new AssertionError(why + ": expected " + Integer.toHexString(expected) + " but was " + Integer.toHexString(actual));
    }
    static final int MEDIA = 0xffd4798e, CUSTOM = 0xff336699;

    /** A capsule item: the background view and, for the music capsule, a view of the media template next to it. */
    private static ViewGroup item(boolean media) {
        ViewGroup item = new ViewGroup(new Context());
        item.addView(new View(new Context()));
        if (media) item.addView(new MediaBackGroundView());
        return item;
    }

    private static final class Rig {
        final FluidCloudAccent accent = new FluidCloudAccent();
        final FluidCloudStyle style = new FluidCloudStyle();
        final LockCapsuleTint tint = new LockCapsuleTint();
        final CapsuleBackgroundHelper helper = new CapsuleBackgroundHelper();
        final Bundle config = new Bundle();
        final s1 section;
        Rig() throws Exception {
            // The capsule is only tinted once its own switch is on (code98); every scenario below is about what it takes then.
            config.putBoolean(FluidCloudStyle.LOCK_CAPSULE, true);
            style.setAccentController(accent);
            tint.setPolicy(style::lockCapsulePlan);
            check(tint.resolve(LockCapsuleTintCheck.class.getClassLoader()) != null, "the audited helper resolves by structure");
            ViewGroup root = new ViewGroup(null);
            section = new s1(null, new K0(new kotlinx.coroutines.flow.p(new com.oplus.systemui.plugins.shared.data.media.model.a(MEDIA))), root, null);
            accent.mediaChanged(section);
        }
        void apply() { accent.configure(config); style.configure(config); }
        /** The capsule as it settles: a change starts its colour fade at the next frame (code100), so one frame starts it, the fade
         * runs out, and the canvas of the frame after that is returned. */
        Canvas draw(ViewGroup item) {
            drawNow(item);
            android.os.SystemClock.uptime += C17HighlightRemoval.COLOUR_FADE_MS + 1;
            return drawNow(item);
        }
        /** One native-then-tint draw of a capsule at this instant; returns the canvas it drew on. */
        Canvas drawNow(ViewGroup item) {
            Canvas canvas = new Canvas();
            tint.drawn(helper, (View) item.getChildAt(0), canvas);
            return canvas;
        }
    }

    public static void main(String[] args) throws Exception {
        defaultsAndStructure();
        switchedOffByDefault();
        fades();
        decisions();
        lockPageWithoutMasters();
        System.out.println("LockCapsuleTintCheck: " + checks + " checks passed (structure, accent/follow/custom order, gradient, immersive, release)");
    }

    private static void defaultsAndStructure() throws Exception {
        Rig rig = new Rig();
        ViewGroup music = item(true);
        for (View child : new View[]{music.getChildAt(0)}) check(child.getParent() == music, "the background sits in its item");
        Canvas canvas = rig.draw(music);
        check(canvas.colorFills == 0 && canvas.rectFills == 0, "nothing enabled: the capsule is left native");
        check(LockCapsuleTint.hasMediaTemplate(music, music.getChildAt(0)), "a media template view marks the music capsule");
        check(!LockCapsuleTint.hasMediaTemplate(item(false), item(false).getChildAt(0)), "another capsule has none");
        check(new LockCapsuleTint().resolve(null) == null, "no class loader, no hooks");
    }

    /** code98: by default the lock-screen bottom capsule stays native, whatever else is on. */
    private static void switchedOffByDefault() throws Exception {
        Rig rig = new Rig();
        rig.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE, false);
        ViewGroup music = item(true), other = item(false);
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA, true);
        rig.config.putBoolean(FluidCloudStyle.MASTER, true);
        rig.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND, true);
        rig.config.putInt(FluidCloudStyle.BACKGROUND_COLOR, 0xff203040);
        rig.apply();
        check(rig.draw(music).colorFills == 0 && rig.draw(other).colorFills == 0, "with its switch off the capsule is native although accent, follow and custom colour are on");
        rig.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE, true);
        rig.apply();
        check(rig.draw(music).colorFills == 1, "the switch turns the tint on");
        rig.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE, false);
        rig.apply();
        check(rig.draw(music).colorFills == 0, "and off again");
        // The colour style gives the capsule a gradient too (everything but the custom base colour).
        rig.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE, true);
        rig.config.putString(FluidCloudStyle.COLOR_STYLE, FluidCloudStyle.GRADIENT);
        rig.apply();
        check(rig.draw(music).lastFillShader != null, "the gradient colour style gradients the music capsule");
        check(rig.draw(other).lastFillShader != null, "and a capsule that follows the media colour");
    }

    /** code100: a capsule's colour fades in, crosses to a new colour and fades out instead of popping. */
    private static void fades() throws Exception {
        Rig rig = new Rig();
        ViewGroup music = item(true);
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.apply();
        check(rig.drawNow(music).colorFills == 0, "the first frame of a new colour is still transparent");
        android.os.SystemClock.uptime += C17HighlightRemoval.COLOUR_FADE_MS / 2;
        Canvas half = rig.drawNow(music);
        check(half.colorFills == 1 && (half.lastColorFill >>> 24) > 0x40 && (half.lastColorFill >>> 24) < 0xc0
                && (half.lastColorFill & 0xffffff) == (MEDIA & 0xffffff), "half way the colour is half shown");
        android.os.SystemClock.uptime += C17HighlightRemoval.COLOUR_FADE_MS;
        same(0xff, rig.drawNow(music).lastColorFill >>> 24, "then fully");
        rig.config.putBoolean(FluidCloudAccent.ENABLED, false);
        rig.apply();
        Canvas leaving = rig.drawNow(music);
        check(leaving.colorFills == 1 && (leaving.lastColorFill >>> 24) == 0xff, "switched off, the colour starts fading from where it was");
        android.os.SystemClock.uptime += C17HighlightRemoval.COLOUR_FADE_MS + 1;
        check(rig.drawNow(music).colorFills == 0 && rig.drawNow(music).colorFills == 0, "and is gone after the fade");
    }

    /** Lock/AOD appearance values stay saved, while the media-accent master gates every capsule media source. */
    private static void lockPageWithoutMasters() throws Exception {
        for(boolean aod:new boolean[]{false,true}) {
            ViewGroup music=item(true),other=item(false);Rig rig=new Rig();
            rig.style.setKeyguard(true);
            com.android.systemui.statusbar.StatusBarStateControllerImpl state=new com.android.systemui.statusbar.StatusBarStateControllerImpl();
            state.state=1;state.dozing=aod;rig.style.onNativeState(state);
            rig.config.putBoolean(FluidCloudStyle.LOCK_CAPSULE,true);
            rig.config.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING,false);
            rig.config.putFloat(FluidCloudAccent.LOCK_LIGHT,0f);rig.config.putFloat(FluidCloudAccent.AOD_LIGHT,0f);rig.apply();
            check(rig.draw(music).colorFills==0&&rig.draw(other).colorFills==0,"nothing asks for media colour: both capsules stay native");
            rig.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,true);rig.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,true);rig.apply();
            Canvas canvas=rig.draw(music),plain=rig.draw(other);
            check(canvas.colorFills+canvas.rectFills==0&&plain.colorFills+plain.rectFills==0,"gradient on with media master off: neither lock/AOD capsule sources media colour");
            rig.config.putBoolean(FluidCloudAccent.ENABLED,true);rig.apply();canvas=rig.draw(music);
            check(canvas.rectFills==1&&canvas.lastFillShader instanceof android.graphics.LinearGradient,"enabling media accent uses the previously saved capsule gradient");
            check((((android.graphics.LinearGradient)canvas.lastFillShader).colors[0]&0xffffff)==(MEDIA&0xffffff),"the gradient comes from current media");
            check(rig.draw(other).colorFills+rig.draw(other).rectFills==1,"a non-media capsule follows the same enabled scene policy");
            rig.config.putBoolean(FluidCloudAccent.ENABLED,false);rig.apply();canvas=rig.draw(music);plain=rig.draw(other);
            check(canvas.colorFills+canvas.rectFills==0&&plain.colorFills+plain.rectFills==0,"switching media accent off clears both capsule sources after their existing colour fade");
            rig.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT,false);rig.config.putBoolean(FluidCloudAccent.AOD_GRADIENT,false);
            rig.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM,true);rig.config.putBoolean(FluidCloudAccent.AOD_CUSTOM,true);
            rig.config.putFloat(FluidCloudAccent.LOCK_OPACITY,50f);rig.config.putFloat(FluidCloudAccent.AOD_OPACITY,50f);
            rig.config.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY,0f);rig.config.putFloat(FluidCloudAccent.AOD_MASK_OPACITY,0f);rig.apply();
            canvas=rig.draw(music);plain=rig.draw(other);
            check(canvas.colorFills+canvas.rectFills==0&&plain.colorFills+plain.rectFills==0,"custom opacity and mask cannot turn media colouring back on");
            rig.config.putBoolean(FluidCloudAccent.ENABLED,true);rig.apply();canvas=rig.draw(music);
            check(canvas.colorFills==1&&(canvas.lastColorFill&0xffffff)==(MEDIA&0xffffff),"re-enabling uses the saved custom look");
            same(0x80,canvas.lastColorFill>>>24,"the saved lock/AOD 50% opacity remains effective");
            rig.config.putBoolean(FluidCloudAccent.ENABLED,false);rig.apply();canvas=rig.draw(music);
            check(canvas.colorFills+canvas.rectFills==0,"a custom look cannot retain old media colour after disabling again");
            rig.tint.release();rig.style.release();rig.accent.release();
        }
    }

    private static void decisions() throws Exception {
        Rig rig = new Rig();
        ViewGroup music = item(true), other = item(false);
        // The music capsule's own media accent comes first, on the lock opacity and mask.
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.apply();
        Canvas canvas = rig.draw(music);
        check(canvas.colorFills == 1 && (canvas.lastColorFill & 0xffffff) == (MEDIA & 0xffffff), "the music capsule takes the media accent");
        same(0xff, canvas.lastColorFill >>> 24, "at the default full fill");
        check(rig.draw(other).colorFills == 0, "another capsule has no accent and nothing else is on");

        // Lock-screen custom opacity and mask apply to the capsule like to the cards.
        rig.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM, true);rig.config.putBoolean(FluidCloudAccent.AOD_CUSTOM, true);
        rig.config.putFloat(FluidCloudAccent.LOCK_OPACITY, 50f);rig.config.putFloat(FluidCloudAccent.AOD_OPACITY, 50f);
        rig.config.putFloat(FluidCloudAccent.LOCK_MASK_OPACITY, 0f);rig.config.putFloat(FluidCloudAccent.AOD_MASK_OPACITY, 0f);
        rig.apply();
        canvas = rig.draw(music);
        same(0x80, canvas.lastColorFill >>> 24, "lock fill opacity 50 %");
        // code100: lightened by the white base exactly like the cards, at the opacity that was set.
        same(FluidCloudStyle.lighten(0x80000000 | MEDIA & 0xffffff, FluidCloudAccent.DEFAULT_LOCK_LIGHT / 100f), canvas.lastColorFill,
                "an unmasked media colour, lightened like the cards");
        // The colours below are about the order of the decisions: without the white base they are the plain colours.
        rig.config.putFloat(FluidCloudAccent.LOCK_LIGHT, 0f);rig.config.putFloat(FluidCloudAccent.AOD_LIGHT, 0f);
        rig.apply();
        check((rig.draw(music).lastColorFill & 0xffffff) == (MEDIA & 0xffffff), "white base 0: the plain media colour");

        // The gradient is the music capsule's own and opt-in: a vertical fade to transparent in the capsule's path.
        rig.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT, true);rig.config.putBoolean(FluidCloudAccent.AOD_GRADIENT, true);
        rig.apply();
        canvas = rig.draw(music);
        check(canvas.rectFills == 1 && canvas.lastFillShader instanceof android.graphics.LinearGradient, "the lock gradient is drawn as one gradient");
        android.graphics.LinearGradient gradient = (android.graphics.LinearGradient) canvas.lastFillShader;
        same(0x80, gradient.colors[0] >>> 24, "the gradient starts at the capsule colour");
        same(0, gradient.colors[1] >>> 24, "and ends transparent");
        check(gradient.top == 0f && gradient.bottom == 192f && canvas.lastFillRect.left == 394f && canvas.lastFillRect.right == 1046f, "within the capsule's own bounds");
        check(rig.draw(other).rectFills == 0, "no gradient on another capsule");
        rig.config.putBoolean(FluidCloudAccent.LOCK_GRADIENT, false);rig.config.putBoolean(FluidCloudAccent.AOD_GRADIENT, false);

        // Following the media colour and the custom base colour reach every capsule, in that order, within the lock scope.
        // (The lock page's own looks are off here; each media source still requires the media-accent master.)
        rig.config.putBoolean(FluidCloudAccent.LOCK_CUSTOM, false);rig.config.putBoolean(FluidCloudAccent.AOD_CUSTOM, false);
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA, true);
        rig.config.putBoolean(FluidCloudStyle.FOLLOW_ONLY_PLAYING, false);
        rig.apply();
        check(rig.draw(other).colorFills == 0, "code100: following needs the style page's custom base colour");
        rig.config.putBoolean(FluidCloudStyle.MASTER, true);
        rig.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND, true);
        rig.apply();
        canvas = rig.draw(other);
        check(canvas.colorFills == 1 && (canvas.lastColorFill & 0xffffff) == (MEDIA & 0xffffff), "another capsule follows the media colour");
        rig.config.putBoolean(FluidCloudStyle.FOLLOW_MEDIA, false);
        rig.config.putBoolean(FluidCloudStyle.MASTER, true);
        rig.config.putBoolean(FluidCloudStyle.DESKTOP_BACKGROUND, true);
        rig.config.putString(FluidCloudStyle.BACKGROUND_MODE, "custom");
        rig.config.putInt(FluidCloudStyle.BACKGROUND_COLOR, CUSTOM);
        rig.config.putFloat(FluidCloudStyle.BACKGROUND_OPACITY, 100f);
        rig.apply();
        canvas = rig.draw(other);
        check(canvas.colorFills == 1 && (canvas.lastColorFill & 0xffffff) == (CUSTOM & 0xffffff), "without following, another capsule takes the custom base colour");
        check((rig.draw(music).lastColorFill & 0xffffff) == (MEDIA & 0xffffff), "the music capsule keeps its accent over the base colour");
        rig.config.putBoolean(FluidCloudStyle.SCOPE_LOCK, false);
        rig.apply();
        check(rig.draw(other).colorFills == 0, "the lock-screen scope off: the base colour no longer reaches the capsule");
        check(rig.draw(music).colorFills == 1, "the accent needs no scope");
        rig.config.putBoolean(FluidCloudStyle.SCOPE_LOCK, true);
        rig.config.putBoolean(FluidCloudAccent.ENABLED, false);
        rig.apply();
        check((rig.draw(music).lastColorFill & 0xffffff) == (CUSTOM & 0xffffff), "accent page off: the music capsule falls back to the base colour");

        // The immersive music card is the OEM's own; a missing clip is skipped; release restores.
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.apply();
        rig.helper.setImmersiveForCheck(true);
        check(rig.draw(music).colorFills == 0, "an immersive capsule is not tinted");
        rig.helper.setImmersiveForCheck(false);
        rig.helper.clearPathForCheck();
        check(rig.draw(music).colorFills == 0, "no clip path, no tint");
        rig = new Rig();
        rig.config.putBoolean(FluidCloudAccent.ENABLED, true);
        rig.apply();
        check(rig.draw(music).colorFills == 1, "tinted before release");
        rig.tint.release();
        check(rig.draw(music).colorFills == 0, "released: nothing is drawn any more");
        Bundle safe = new Bundle();
        safe.putBoolean(FluidCloudAccent.ENABLED, true);
        safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        Rig guarded = new Rig();
        guarded.accent.configure(safe);
        guarded.style.configure(safe);
        check(guarded.draw(music).colorFills == 0, "safe mode: native");
    }
}
