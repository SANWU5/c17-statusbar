package dev.puitheme;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import com.coui.animatekit.progressive.internal.ProgressiveProgressDriver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/** Exercises compiled ownership guards and native-driver identity, plus spring continuity. */
public final class StatusIconTransitionCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void near(float actual, float wanted, String message) {
        check(Math.abs(actual - wanted) < .00002f, message + ": " + actual);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static final class Source extends View {
        int draws;
        Source() { super(new Context()); }
        public void draw(Canvas canvas) { draws++; }
    }
    private static final class TintedSource extends View {
        final android.graphics.Paint paint = new android.graphics.Paint(0);
        int draws;
        TintedSource() { super(new Context()); }
        @Override public void draw(Canvas canvas) { draws++; canvas.drawText("native", 0f, 0f, paint); }
    }
    private static void nativeFakeDraw(StatusBarFixedIcons owner, View copy, Source original, int kind) {
        if (!owner.suppressNativeCopy(copy, original, kind)) original.draw(null);
    }
    private static final class CachedCopy extends View {
        View original;
        CachedCopy(View source) { super(new Context()); original = source; }
    }

    public static final class Helper { public ProgressiveProgressDriver progressDriver = new ProgressiveProgressDriver(); }
    public static final class Controller {
        public Helper helper = new Helper();
        public Helper getProgressiveHelper() { return helper; }
    }
    public static class ValueBase {
        public Controller controller = new Controller();
        public Controller getProgressiveController() { return controller; }
    }
    public static final class HorizontalValue extends ValueBase { }
    public static final class Animation {
        public HorizontalValue value = new HorizontalValue();
        public HorizontalValue getHorizontalTranslationValue() { return value; }
    }
    public static class PagerBase { public View mView = new View(new Context()); }
    public static final class Pager extends PagerBase {
        public Animation slidSwitchAnimation = new Animation();
        public View qsPanelView = new View(new Context());
        public float screenWidth = 1440f;
    }

    private static final class PageView extends ViewGroup {
        float x;
        int alphaWrites, transitionWrites, visibilityWrites;
        PageView() { super(new Context()); }
        @Override public float getTranslationX() { return x; }
        @Override public void setAlpha(float value) { super.setAlpha(value); alphaWrites++; }
        @Override public void setTransitionAlpha(float value) { super.setTransitionAlpha(value); transitionWrites++; }
        @Override public void setVisibility(int value) { super.setVisibility(value); visibilityWrites++; }
    }

    private static ClassLoader getLoader() { return StatusIconTransitionCheck.class.getClassLoader(); }

    private static class NativeCoordinates extends ViewGroup {
        private final android.view.Display display = new android.view.Display();
        private final android.os.IBinder window = (android.os.IBinder) java.lang.reflect.Proxy.newProxyInstance(
                getLoader(), new Class<?>[]{android.os.IBinder.class}, (proxy, method, args) -> null);
        int screenX, screenY, width, height;
        int alphaWrites, translationWrites, transitionWrites, visibilityWrites;
        StatusBarPhoneRightIcons alphaOwner;
        NotificationBigClock receiver;
        StatusBarNotificationRightIcons sceneOwner;
        float x;
        NativeCoordinates(int screenX, int screenY, int width, int height) {
            super(new Context()); this.screenX = screenX; this.screenY = screenY; this.width = width; this.height = height;
        }
        private android.os.IBinder windowOverride;
        NativeCoordinates(int screenX, int screenY, int width, int height, NativeCoordinates windowHost) {
            this(screenX, screenY, width, height); windowOverride = windowHost.getWindowToken();
        }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void setAlpha(float value) {
            alphaWrites++;
            if (receiver != null) value = receiver.statusIconPhoneRightAlpha(this, value);
            if (sceneOwner != null) value = sceneOwner.nativeAlpha(this, value);
            super.setAlpha(alphaOwner == null ? value : alphaOwner.nativeAlpha(this, value));
        }
        @Override public void setTranslationY(float value) {
            translationWrites++;
            if (receiver != null) value = receiver.statusIconPhoneLeftTranslationY(this, value);
            super.setTranslationY(value);
        }
        @Override public void setTransitionAlpha(float value) {
            transitionWrites++;
            super.setTransitionAlpha(receiver == null ? value : receiver.statusIconNotificationTransitionAlpha(this, value));
        }
        @Override public void setVisibility(int value) {
            visibilityWrites++;
            super.setVisibility(receiver == null ? value : receiver.nativeNotificationHeaderVisibility(this, value));
        }
        @Override public float getTranslationX() { return x; }
        @Override public android.view.Display getDisplay() { return display; }
        @Override public android.os.IBinder getWindowToken() { return windowOverride == null ? window : windowOverride; }
        @Override public void getLocationOnScreen(int[] output) {
            float actualX = screenX + x, actualY = screenY + getTranslationY();
            for (View parent = getParent() instanceof View ? (View) getParent() : null; parent != null;)
                { actualX += parent.getTranslationX(); actualY += parent.getTranslationY(); parent = parent.getParent() instanceof View ? (View) parent.getParent() : null; }
            output[0] = Math.round(actualX); output[1] = Math.round(actualY);
        }
    }
    /** Drives the actual Phone-only alpha owner and verifies native shade RIGHT never changes. */
    private static void bigClockRightOwnerChecks() throws Exception {
        NativeCoordinates shade = new NativeCoordinates(0, 0, 1440, 3168);
        NativeCoordinates panel = new NativeCoordinates(0, 0, 1440, 3168, shade);
        NativeCoordinates row = new NativeCoordinates(1050, 620, 180, 40, shade);
        NativeCoordinates fake = new NativeCoordinates(1050, 620, 180, 40, shade);
        NativeCoordinates phone = new NativeCoordinates(1080, 28, 310, 68);
        shade.addView(panel); panel.addView(row); panel.addView(fake);
        phone.setAlpha(.8f); row.setAlpha(.7f); fake.setAlpha(.6f);
        NotificationBigClock receiver = new NotificationBigClock();
        receiver.configure(new android.os.Bundle());
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, .8f, 0, false);
        StatusBarFixedIcons retired = (StatusBarFixedIcons) get(receiver, "fixedStatusIcons");
        StatusBarPhoneRightIcons right = (StatusBarPhoneRightIcons) get(receiver, "phoneRightIcons");
        phone.alphaOwner = right;
        check(!(Boolean) get(retired, "added") && !(Boolean) get(right, "owned"),
                "all masters OFF retain native Phone and header even with exact native identities");
        near(phone.getAlpha(), .8f, "all masters OFF preserve native Phone alpha");
        near(row.getAlpha(), .7f, "all masters OFF preserve native header alpha");
        near(fake.getAlpha(), .6f, "all masters OFF preserve native fake alpha");
        android.os.Bundle onlyClock = new android.os.Bundle();
        onlyClock.putBoolean(NotificationBigClockSettings.MASTER, true);
        receiver.configure(onlyClock);
        check(!(Boolean) get(right, "owned") && !(Boolean) get(retired, "added"),
                "big clock ON retains native Phone RIGHT handoff without acquiring a copy");
        check(((java.util.Map<?, ?>) get(get(receiver, "pageLeftIcons"), "copies")).isEmpty(),
                "big clock's Phone RIGHT policy never enables independent QS LEFT policy");
        near(phone.getAlpha(), .8f, "fully open RIGHT native alpha is untouched by the clock module");
        check(!receiver.suppressStatusIconCopy(fake, phone, StatusIconTransition.RIGHT),
                "native source.draw fake RIGHT stays enabled while actual Phone row is transparent");
        for (int step = 0; step <= 100; step++) {
            float phase = step / 100f;
            panel.x = step * 2f; panel.setTranslationY(step * 3f);
            shade.screenX = step % 3; shade.screenY = step % 5;
            row.screenX = 900 + step; row.screenY = 400 + step * 4;
            receiver.onSeparateQsStatusChanged(panel, row, phone, fake, phase, 0, false);
            near(phone.getAlpha(), .8f,
                    "Phone RIGHT native handoff remains untouched at phase " + step);
            near(row.getAlpha(), .7f, "native header opacity untouched at phase " + step);
            near(fake.getAlpha(), .6f, "native fake opacity untouched at phase " + step);
            near(phone.getTranslationY(), 0f, "Phone RIGHT Y remains native at phase " + step);
            near(phone.getTranslationX(), 0f, "Phone RIGHT X remains native at phase " + step);
        }
        final float[] horizontal = {0f}; receiver.setStatusIconHorizontalReader(() -> horizontal[0]);
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, .1f, 0, false);
        for (int step = 0; step <= 100; step++) {
            horizontal[0] = step / 100f; receiver.onStatusIconHorizontalProgressChanged();
            near(phone.getAlpha(), .8f, "RIGHT native phase passes through page movement " + step);
            near(row.getAlpha(), .7f, "horizontal page transition keeps header RIGHT native " + step);
            near(fake.getAlpha(), .6f, "horizontal direct-source copy keeps native opacity " + step);
        }
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, 1f, 0, false);
        int phoneWrites = phone.alphaWrites, layouts = phone.layoutRequests, invalidates = phone.invalidations;
        Object binding = get(right, "source");
        for (int frame = 0; frame < 10000; frame++)
            receiver.onSeparateQsStatusChanged(panel, row, phone, fake, 1f, 0, false);
        check(phone.alphaWrites == phoneWrites && phone.layoutRequests == layouts && phone.invalidations == invalidates,
                "ten thousand identical native callbacks schedule no alpha write, layout, or redraw");
        check(get(right, "source") == binding && phone.attachListeners.size() == 1,
                "steady Phone callbacks reuse one weak owner and detach observer");
        check(shade.getOverlay().drawables.isEmpty() && phone.getOverlay().drawables.isEmpty()
                        && shade.getViewTreeObserver().preDrawListeners.isEmpty()
                        && phone.getViewTreeObserver().preDrawListeners.isEmpty() && phone.layoutListeners.isEmpty(),
                "Phone-only RIGHT uses no overlay, bitmap, ticker, geometry listener, or traversal observer");
        phone.setAlpha(0f);
        android.os.Bundle safe = new android.os.Bundle(); safe.putBoolean(NotificationBigClockSettings.MASTER, true);
        safe.putBoolean(StatusBarSettings.SAFE_MODE, true); receiver.configure(safe);
        near(phone.getAlpha(), 0f, "safe mode restores a native zero written while module alpha was also zero");
        near(receiver.statusIconPhoneRightAlpha(phone, .55f), .55f, "safe native setter returns exact native value");
        phone.setAlpha(.55f);
        receiver.configure(onlyClock);
        near(phone.getAlpha(), .55f, "leaving safe mode retains native RIGHT rendering");
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, .1f, 0, false);
        near(phone.getAlpha(), .55f, "open RIGHT is driven only by its native parent and CC controller");
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, 0f, 0, true);
        near(phone.getAlpha(), .55f, "settled close restores latest native Phone alpha exactly");
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, 1f, 1, false);
        near(phone.getAlpha(), .55f, "keyguard never owns Phone opacity");
        near(row.getAlpha(), .7f, "safe/closed/keyguard preserve native header opacity");
        near(fake.getAlpha(), .6f, "safe/closed/keyguard preserve native fake opacity");
        android.os.Bundle onlyShade = new android.os.Bundle(); onlyShade.putBoolean(StatusBarShadeIconSettings.MASTER, true);
        receiver.configure(onlyShade);
        receiver.onSeparateQsStatusChanged(panel, row, phone, fake, .1f, 0, false);
        check(!(Boolean) get(right, "owned") && ((java.util.Map<?, ?>) get(get(receiver, "pageLeftIcons"), "copies")).isEmpty(),
                "explicit top-icon master retains complete native control-center LEFT and RIGHT");
        receiver.configure(new android.os.Bundle());
        near(phone.getAlpha(), .55f, "both masters OFF release Phone ownership");
        check(!(Boolean) get(right, "owned") && !(Boolean) get(retired, "phoneCaptureAllowed"),
                "fixed Phone geometry/copy ownership remains retired even after reconfiguration");
        check(!receiver.suppressStatusIconCopy(fake, phone, StatusIconTransition.RIGHT),
                "native RIGHT header direct drawing never inherits retired fixed-slot suppression");
        phoneRightLifecycleChecks();

        pendingHeaderVisibilityChecks();
        landscapeHeaderClockChecks();
        nativeLeftChainChecks();

    }
    private static final class HeaderClock extends android.widget.TextView {
        NotificationBigClock receiver;
        int alphaWrites, visibilityWrites;
        HeaderClock() { super(new Context()); getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT; }
        @Override public void setAlpha(float value) {
            alphaWrites++;
            super.setAlpha(receiver == null ? value : receiver.statusIconPhoneRightAlpha(this, value));
        }
        @Override public void setVisibility(int value) {
            visibilityWrites++;
            super.setVisibility(receiver == null ? value : receiver.nativeNotificationHeaderVisibility(this, value));
        }
    }
    private static final class NativeHeader extends NativeCoordinates {
        final HeaderClock clock = new HeaderClock();
        final NativeCoordinates row = new NativeCoordinates(1050, 620, 180, 40);
        final android.content.res.Resources resources = new android.content.res.Resources() {
            @Override public int getIdentifier(String name, String type, String pkg) {
                return "qs_footer_clock".equals(name) ? 701 : "quick_qs_status_icons".equals(name) ? 702 : 0;
            }
        };
        NativeHeader() { super(0, 400, 1440, 400); addView(clock); addView(row); }
        @Override public android.content.res.Resources getResources() { return resources; }
        @Override public View findViewById(int id) { return id == 701 ? clock : id == 702 ? row : null; }
    }


    private static void landscapeHeaderClockChecks() throws Exception {
        NotificationBigClock receiver = new NotificationBigClock();
        NativeHeader header = new NativeHeader();
        NativeCoordinates panel = new NativeCoordinates(0, 0, 1440, 3168);
        panel.addView(header); header.clock.receiver = receiver;
        header.clock.setAlpha(.72f);
        android.os.Bundle settings = new android.os.Bundle(); settings.putBoolean(NotificationBigClockSettings.MASTER, true);
        settings.putBoolean(NotificationBigClockSettings.LANDSCAPE_MASTER, true);
        settings.putBoolean(StatusBarShadeIconSettings.MASTER, true);
        receiver.configure(settings); receiver.onHeaderInflated(header);
        check(header.clock.getVisibility() == View.INVISIBLE, "portrait pending/native footer clock stays replaced by BigClock");
        receiver.onPanelMotionState(true, false); receiver.onPanelChanged(panel, 1f, 0, false);
        for (int turn = 0; turn < 100; turn++) {
            header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            panel.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            receiver.onConfigurationChanged();
            check(header.clock.getVisibility() == View.INVISIBLE, "landscape replacement keeps the exact native footer hidden " + turn);
            near(header.clock.getAlpha(), .72f, "landscape native footer alpha is not hidden by top-icon ownership " + turn);
            header.clock.setVisibility(View.GONE);
            check(header.clock.getVisibility() == View.GONE, "landscape keeps native GONE policy " + turn);
            header.clock.setVisibility(View.VISIBLE);
            check(header.clock.getVisibility() == View.INVISIBLE, "landscape replacement blocks duplicate native clock writers " + turn);
            header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
            panel.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
            receiver.onConfigurationChanged();
            check(header.clock.getVisibility() == View.INVISIBLE, "return to portrait reuses the same precise clock owner " + turn);
        }
        header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        header.clock.setVisibility(View.VISIBLE);
        check(header.clock.getVisibility() == View.INVISIBLE, "landscape native setter before configuration callback keeps replacement ownership");
        receiver.onConfigurationChanged();
        android.os.Bundle onlyShade = new android.os.Bundle(); onlyShade.putBoolean(StatusBarShadeIconSettings.MASTER, true);
        header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
        receiver.configure(onlyShade); receiver.onPanelChanged(panel, 1f, 0, false);
        near(header.clock.getAlpha(), 0f, "portrait top-icon-only policy may hide the exact small header clock");
        header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        header.clock.setAlpha(.43f);
        near(header.clock.getAlpha(), .43f, "landscape alpha setter before config releases the old owner without recursive restoration");
        receiver.onConfigurationChanged();
        near(header.clock.getAlpha(), .43f, "landscape configuration preserves the latest native clock alpha");
        receiver.onHeaderInflated(header);
        check(header.clock.getVisibility() == View.VISIBLE, "landscape init/attach does not newly disable the native clock");
        receiver.configure(settings);
        check(header.clock.getVisibility() == View.INVISIBLE, "landscape settings reload hides the replaced native clock");
        header.clock.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
        receiver.onConfigurationChanged();
        check(header.clock.getVisibility() == View.INVISIBLE, "portrait resumes replacement after landscape native reinitialization");
        receiver.configure(new android.os.Bundle());
        check(header.clock.getVisibility() == View.VISIBLE, "OFF restores the latest native clock visibility after orientation cycles");
        near(header.clock.getAlpha(), .43f, "OFF restores the latest native alpha after orientation cycles");
    }

    private static void pendingHeaderVisibilityChecks() throws Exception {
        NotificationBigClock receiver = new NotificationBigClock();
        android.os.Bundle clockSettings = new android.os.Bundle();
        clockSettings.putBoolean(NotificationBigClockSettings.MASTER, true);
        receiver.configure(clockSettings);
        NativeHeader first = new NativeHeader(), second = new NativeHeader();
        first.attached = first.clock.attached = false;
        first.clock.setAlpha(.65f); first.clock.receiver = receiver;
        second.clock.receiver = receiver;
        receiver.onHeaderInflated(first);
        check(first.clock.getVisibility() == View.INVISIBLE,
                "onInit before parent and child attach retains and disables the exact small clock");
        near(first.clock.getAlpha(), .65f, "replacement clock retains native alpha instead of applying an opacity phase");
        first.attached = true; receiver.onHeaderInflated(first);
        check(first.clock.getVisibility() == View.INVISIBLE,
                "parent attach before child attach cannot lose the clock visibility owner");
        first.clock.attached = true;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(first.clock.attachListeners))
            listener.onViewAttachedToWindow(first.clock);
        check(first.clock.getVisibility() == View.INVISIBLE, "child attach needs no later fraction callback to hide replaced clock");
        check(first.clock.attachListeners.size() == 2, "one exact pending child observer and one alpha observer are registered");
        receiver.onHeaderInflated(second);
        check(first.clock.getVisibility() == View.INVISIBLE && second.clock.getVisibility() == View.INVISIBLE,
                "independent notification headers both retain visibility ownership");
        first.clock.setVisibility(View.VISIBLE);
        check(first.clock.getVisibility() == View.INVISIBLE, "native VISIBLE write cannot resurrect replaced header clock");
        first.clock.setVisibility(View.GONE);
        check(first.clock.getVisibility() == View.GONE, "native GONE keeps its original layout policy");
        first.clock.setVisibility(View.VISIBLE);
        int visibilityWrites = first.clock.visibilityWrites, alphaWrites = first.clock.alphaWrites;
        for (int frame = 0; frame < 10000; frame++) receiver.onStatusIconHorizontalProgressChanged();
        check(first.clock.visibilityWrites == visibilityWrites && first.clock.alphaWrites == alphaWrites,
                "ten thousand steady replacement-clock refreshes issue no visibility or alpha setters");
        first.clock.attached = false;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(first.clock.attachListeners))
            listener.onViewDetachedFromWindow(first.clock);
        check(first.clock.getVisibility() == View.VISIBLE, "detaching the exact clock restores its latest native visibility");
        first.clock.attached = true;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(first.clock.attachListeners))
            listener.onViewAttachedToWindow(first.clock);
        check(first.clock.getVisibility() == View.INVISIBLE, "same-object child reattach immediately reclaims visibility without parent init");
        android.os.Bundle safe = new android.os.Bundle(clockSettings); safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        receiver.configure(safe);
        check(first.clock.getVisibility() == View.VISIBLE && second.clock.getVisibility() == View.VISIBLE,
                "safe mode restores all header clocks independently");
        first.clock.setVisibility(View.INVISIBLE); receiver.configure(clockSettings);
        first.clock.setVisibility(View.GONE); receiver.configure(new android.os.Bundle());
        check(first.clock.getVisibility() == View.GONE && second.clock.getVisibility() == View.VISIBLE,
                "OFF restores exact native visibility including zero, INVISIBLE and GONE observations");
        first.clock.setVisibility(View.VISIBLE); first.clock.attached = false;
        android.os.Bundle onlyShade = new android.os.Bundle(); onlyShade.putBoolean(StatusBarShadeIconSettings.MASTER, true);
        receiver.configure(onlyShade);
        NativeCoordinates panel = new NativeCoordinates(0, 0, 1440, 3168); panel.addView(first);
        receiver.onPanelMotionState(true, false); receiver.onPanelChanged(panel, 1f, 0, false);
        near(first.clock.getAlpha(), .65f, "unattached shade-only native clock retains native alpha");
        first.clock.attached = true;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(first.clock.attachListeners))
            listener.onViewAttachedToWindow(first.clock);
        near(first.clock.getAlpha(), 0f, "shade-only child attach binds the previously pending alpha source");
        check(first.clock.getVisibility() == View.VISIBLE, "shade-only policy does not inherit big-clock visibility disabling");
        receiver.configure(clockSettings);
        Field removed = ModuleLifecycle.class.getDeclaredField("removed"); removed.setAccessible(true);
        boolean previous = removed.getBoolean(null);
        try {
            removed.setBoolean(null, true); receiver.configure(clockSettings);
            check(first.clock.getVisibility() == View.VISIBLE && second.clock.getVisibility() == View.VISIBLE,
                    "uninstall restores pending/attached replacement clocks");
            check(first.clock.attachListeners.isEmpty() && second.clock.attachListeners.isEmpty(),
                    "uninstall removes both exact clock attach/alpha observers");
            Object registry = get(receiver, "notificationRightIcons");
            check(((java.util.Map<?, ?>) get(registry, "visibilityOwners")).isEmpty(),
                    "uninstall clears the weak visibility setter index");
            receiver.onHeaderInflated(first);
            first.clock.setVisibility(View.VISIBLE);
            check(first.clock.attachListeners.isEmpty() && first.clock.getVisibility() == View.VISIBLE,
                    "late native init after removal cannot re-register visibility ownership");
        } finally { removed.setBoolean(null, previous); }
    }

    private static final class NativeGlyph extends NativeCoordinates {
        int draws;
        Runnable afterAlpha;
        NativeGlyph(int x) { super(x, 28, 180, 40); }
        @Override public void draw(Canvas canvas) { draws++; }
        @Override public void setAlpha(float value) { super.setAlpha(value); if (afterAlpha != null) afterAlpha.run(); }
    }
    /** Simulates the actual dispatch: parent visibility gates RenderNode drawing, but
     * StatusBarFakeFrame then calls source.draw() without the source parent's alpha. */
    private static float renderNativeFake(NotificationBigClock receiver, NativeCoordinates copy, NativeGlyph source, int kind) {
        for (View parent = copy; parent != null; parent = parent.getParent() instanceof View ? (View) parent.getParent() : null)
            if (parent.getVisibility() != View.VISIBLE || parent.getAlpha() <= 0f || parent.getTransitionAlpha() <= 0f) return 0f;
        if (receiver.suppressStatusIconCopy(copy, source, kind)) return 0f;
        source.draw(null); return copy.getAlpha() * copy.getTransitionAlpha();
    }


    /** Both native windows remain drawable after spring zero, matching the OEM's
     * after-traversal/postOnAnimation teardown rather than assuming the shade vanished. */


    /** Original APK resetState calls updateLeftAreaState/updateRightArea together,
     * then sets all fake transitions to 1 at f0 and to 0 at f1. No module Phone owner. */
    private static void nativeLeftChainChecks() throws Exception {
        NotificationBigClock receiver = new NotificationBigClock();
        NativeCoordinates phoneWindow = new NativeCoordinates(0, 0, 1440, 80), shade = new NativeCoordinates(0, 0, 1440, 3168),
                panel = new NativeCoordinates(0, 0, 1440, 3168), qs = new NativeCoordinates(0, 0, 1440, 3168),
                parent = new NativeCoordinates(0, 0, 1440, 800), clockCopy = new NativeCoordinates(40, 620, 180, 40),
                notificationCopy = new NativeCoordinates(240, 620, 180, 40), qsClock = new NativeCoordinates(40, 300, 180, 40),
                qsClockCopy = new NativeCoordinates(40, 300, 180, 40), qsNotices = new NativeCoordinates(240, 300, 180, 40),
                qsNoticeCopy = new NativeCoordinates(240, 300, 180, 40), qsRow = new NativeCoordinates(1050, 620, 180, 40),
                qsFake = new NativeCoordinates(1050, 620, 180, 40);
        NativeGlyph clock = new NativeGlyph(40), notices = new NativeGlyph(240), right = new NativeGlyph(1050);
        phoneWindow.addView(clock); phoneWindow.addView(notices); phoneWindow.addView(right);
        shade.addView(panel); shade.addView(qs); panel.addView(parent); parent.addView(clockCopy); parent.addView(notificationCopy);
        qs.addView(qsClock); qs.addView(qsNotices); qsClock.addView(qsClockCopy); qsNotices.addView(qsNoticeCopy);
        qs.addView(qsRow); qs.addView(qsFake);
        clock.setAlpha(.9f); notices.setAlpha(.8f); clockCopy.setAlpha(.7f); notificationCopy.setAlpha(.6f);
        clockCopy.setTranslationY(37f); parent.setVisibility(View.INVISIBLE);
        for (NativeCoordinates view : new NativeCoordinates[]{clock, notices, right, clockCopy, notificationCopy, parent}) view.receiver = receiver;
        android.os.Bundle settings = new android.os.Bundle(); settings.putBoolean(NotificationBigClockSettings.MASTER, true);
        receiver.configure(settings);
        // Both actual OEM reset endpoints, in both directions and with repeated late calls.
        for (int pass = 0; pass < 100; pass++) for (float fraction : new float[]{0f, 1f, 1f, 0f}) {
            float nativeTransition = fraction == 0f ? 1f : 0f;
            qsClock.setTransitionAlpha(nativeTransition); qsNotices.setTransitionAlpha(nativeTransition);
            qsFake.setTransitionAlpha(nativeTransition); qsRow.setTransitionAlpha(fraction);
            receiver.onSeparateQsStatusChanged(qs, qsRow, right, qsFake, fraction, 0, fraction == 0f);
            receiver.onSeparateQsLeftChanged(qs, qsClock, qsClockCopy, clock, qsNotices, qsNoticeCopy, notices, 0, fraction == 0f);
            near(qsClock.getTransitionAlpha(), qsFake.getTransitionAlpha(), "OEM LEFT and passed RIGHT retain the same reset endpoint " + pass);
            near(receiver.statusIconLeftTransitionAlpha(qsClock, nativeTransition), nativeTransition, "native setFakeTransitionAlpha value is unchanged " + pass);
            check(!receiver.suppressStatusIconCopy(qsClockCopy, clock, 1), "CC native LEFT source.draw is never module-masked " + pass);
            near(clock.getAlpha(), .9f, "native Phone LEFT alpha never acquires a second owner " + pass);
            near(notices.getAlpha(), .8f, "native Phone notification alpha never acquires a second owner " + pass);
            near(clock.getTranslationY(), 0f, "native Phone LEFT never receives module geometry " + pass);
        }
        check(clock.attachListeners.isEmpty() && notices.attachListeners.isEmpty(), "no obsolete physical Phone LEFT attachment owners remain");
        receiver.onPanelMotionState(true, false); receiver.onPanelChanged(panel, 1f, 0, false);
        receiver.onFakeClockChanged(clockCopy, 1f, clock); receiver.onFakeNotificationChanged(notificationCopy, notices, 1f);
        near(clockCopy.getAlpha(), 0f, "expanded notification old clock copy remains hidden");
        near(notificationCopy.getAlpha(), 0f, "expanded notification old notification icons remain hidden");
        check(receiver.suppressStatusIconCopy(clockCopy, clock, 1), "expanded direct source.draw stays hidden even if native copy resets");
        clockCopy.setAlpha(.55f); clockCopy.setTransitionAlpha(0f); clockCopy.setTranslationY(42f);
        near(clockCopy.getAlpha(), 0f, "expanded native alpha reset stays masked and records its new baseline");
        near(clockCopy.getTransitionAlpha(), 0f, "native transition zero is no longer forced to one");
        near(clockCopy.getTranslationY(), 42f, "native notification fake Y is no longer rewritten");
        check(parent.getVisibility() == View.INVISIBLE && parent.visibilityWrites == 1,
                "the OEM invisible parent policy is never changed by module short-phase logic");
        int writes = clockCopy.alphaWrites + parent.visibilityWrites + clock.alphaWrites;
        for (int callback = 0; callback < 10000; callback++) receiver.onStatusIconHorizontalProgressChanged();
        check(writes == clockCopy.alphaWrites + parent.visibilityWrites + clock.alphaWrites, "expanded steady state does not rewrite fake/Phone/parent properties");
        receiver.onPanelChanged(panel, .1f, 0, false);
        near(clockCopy.getAlpha(), .55f, "vertical native return phase receives the latest native fake alpha");
        near(notificationCopy.getAlpha(), .6f, "vertical native return phase releases the native notification icon copy");
        check(!receiver.suppressStatusIconCopy(clockCopy, clock, 1), "native source.draw is allowed in the real vertical first/last phase");
        parent.setVisibility(View.VISIBLE); clockCopy.setTransitionAlpha(1f);
        near(renderNativeFake(receiver, clockCopy, clock, 1), .55f, "original visible native fake renders without an artificial Phone force-zero owner");
        final boolean[] moving = {true};
        receiver.onPageMotionReady(new NotificationBigClock.PageMotion() {
            public void register(View view) { throw new AssertionError("native LEFT must not register module copies"); }
            public void unregister(View view) { }
            public boolean isRunning() { return moving[0]; }
        });
        receiver.onPanelMotionState(false, true); receiver.onPanelChanged(panel, 0f, 0, false);
        receiver.onSeparateQsStatusChanged(qs, qsRow, right, qsFake, 0f, 0, true);
        near(clockCopy.getAlpha(), 0f, "horizontal zero race cannot revive the obsolete notification copy");
        receiver.onSeparateQsStatusChanged(qs, qsRow, right, qsFake, 1f, 0, false);
        moving[0] = false; receiver.onStatusIconHorizontalProgressChanged();
        near(clockCopy.getAlpha(), 0f, "pure-QS open keeps only the inactive notification copy hidden");
        check(!receiver.suppressStatusIconCopy(qsClockCopy, clock, 1), "pure-QS actual LEFT remains native");
        receiver.onSeparateQsStatusChanged(qs, qsRow, right, qsFake, 0f, 0, true);
        near(clockCopy.getAlpha(), .55f, "true global close releases notification mask to native handoff");
        check(clockCopy.attachListeners.isEmpty() && notificationCopy.attachListeners.isEmpty(), "true close retires dormant notification copy listeners");
        check(!receiver.suppressStatusIconCopy(clockCopy, clock, 1), "true close has no persistent closed fake mask");
        NativeHeader header = new NativeHeader(); panel.addView(header); header.clock.receiver = receiver;
        receiver.onHeaderInflated(header);
        check(header.clock.getVisibility() == View.INVISIBLE, "BigClock still permanently disables exact qs_footer_clock while enabled");
        receiver.onPanelMotionState(true, false); receiver.onPanelChanged(panel, 1f, 0, false);
        receiver.onFakeClockChanged(clockCopy, 1f, clock);
        android.os.Bundle safe = new android.os.Bundle(settings); safe.putBoolean(StatusBarSettings.SAFE_MODE, true); receiver.configure(safe);
        near(clockCopy.getAlpha(), .55f, "safe mode restores latest native notification alpha");
        check(header.clock.getVisibility() == View.VISIBLE, "safe mode restores exact native footer-clock visibility");
        receiver.configure(settings); receiver.onPanelChanged(panel, 1f, 0, false); receiver.onFakeClockChanged(clockCopy, 1f, clock);
        receiver.onPanelChanged(panel, 1f, 1, false);
        near(clockCopy.getAlpha(), .55f, "keyguard restores notification copy without touching Phone");
        receiver.configure(new android.os.Bundle());
        near(clock.getAlpha(), .9f, "all masters OFF retains exact original Phone LEFT alpha");
        check(!receiver.suppressStatusIconCopy(clockCopy, clock, 1), "all masters OFF removes notification copy draw suppression");
        check(shade.getOverlay().drawables.isEmpty() && phoneWindow.getOverlay().drawables.isEmpty(), "native LEFT creates no overlay, bitmap, or ticker");
    }

    private static void phoneRightLifecycleChecks() throws Exception {
        StatusBarPhoneRightIcons right = new StatusBarPhoneRightIcons();
        NativeCoordinates phone = new NativeCoordinates(1080, 28, 310, 68);
        NativeCoordinates replacement = new NativeCoordinates(1060, 28, 330, 68);
        View unknown = new View(new Context()); phone.setAlpha(.8f); replacement.setAlpha(.6f);
        phone.alphaOwner = replacement.alphaOwner = right;
        right.configure(true); right.bind(phone); right.progress(.1f, true);
        near(phone.getAlpha(), .4f, "Phone helper owns only verified source with real closing phase");
        near(right.nativeAlpha(unknown, .33f), .33f, "unrelated global View alpha setter passes through unchanged");
        right.bind(replacement);
        near(phone.getAlpha(), .8f, "replacement restores previous exact Phone source");
        near(replacement.getAlpha(), .3f, "replacement applies current phase to its own native alpha");
        check(phone.attachListeners.isEmpty() && replacement.attachListeners.size() == 1,
                "replacement removes retired source listener and retains only one exact owner");
        replacement.setAlpha(.5f);
        near(replacement.getAlpha(), .25f, "native alpha setter preserves phase during ownership");
        right.progress(Float.NaN, true); near(replacement.getAlpha(), .5f, "invalid fraction releases native alpha");
        right.progress(.1f, true); right.progress(.1f, false);
        near(replacement.getAlpha(), .5f, "keyguard or unknown native owner releases alpha");
        right.progress(1f, true); replacement.setAlpha(0f);
        right.configure(false); near(replacement.getAlpha(), 0f, "OFF restores exact native zero rather than stale visible value");
        replacement.setAlpha(.9f); right.configure(true);
        near(replacement.getAlpha(), 0f, "re-enable uses actual source baseline at current hidden phase");
        right.progress(.1f, true); near(replacement.getAlpha(), .45f, "re-enabled close animation uses latest baseline");
        replacement.attached = false;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(replacement.attachListeners))
            listener.onViewDetachedFromWindow(replacement);
        near(replacement.getAlpha(), .9f, "real attachment callback restores native source before releasing it");
        check(replacement.attachListeners.isEmpty() && ((WeakReference<?>) get(right, "source")).get() == null,
                "detach releases source and its attachment listener");
        replacement.attached = true; right.bind(replacement);
        near(replacement.getAlpha(), .45f, "normal reattachment can rebind without permanent disable");
        StatusBarPhoneRightIcons pending = new StatusBarPhoneRightIcons();
        NativeCoordinates pendingPhone = new NativeCoordinates(40, 28, 180, 40);
        pendingPhone.setAlpha(.7f); pendingPhone.attached = false; pendingPhone.alphaOwner = pending;
        pending.configure(true); pending.progress(1f, true); pending.bind(pendingPhone);
        near(pendingPhone.getAlpha(), .7f, "unattached pending alpha owner leaves native properties unchanged");
        check(pendingPhone.attachListeners.size() == 1, "verified pending source retains only its exact weak attachment observer");
        pendingPhone.setAlpha(0f); pendingPhone.attached = true;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(pendingPhone.attachListeners))
            listener.onViewAttachedToWindow(pendingPhone);
        near(pendingPhone.getAlpha(), 0f, "pending source attaches at current phase without needing another fraction callback");
        pending.configure(false);
        near(pendingPhone.getAlpha(), 0f, "pending native zero remains exact when ownership is disabled");
        pending.clear(); pendingPhone.setAlpha(.65f); pendingPhone.attached = false;
        pending.configure(true); pending.bind(pendingPhone); pending.configure(false);
        pendingPhone.attached = true;
        for (View.OnAttachStateChangeListener listener : new java.util.ArrayList<>(pendingPhone.attachListeners))
            listener.onViewAttachedToWindow(pendingPhone);
        near(pendingPhone.getAlpha(), .65f, "OFF before pending attach cannot acquire alpha on the late attachment callback");
        pending.clear(); check(pendingPhone.attachListeners.isEmpty(), "pending ownership clear retires its attachment observer");
        Field removed = ModuleLifecycle.class.getDeclaredField("removed"); removed.setAccessible(true);
        boolean previous = removed.getBoolean(null);
        try {
            removed.setBoolean(null, true); right.releaseRuntime();
            near(replacement.getAlpha(), .9f, "actual uninstall restores exact original Phone alpha");
            check(replacement.attachListeners.isEmpty() && ((WeakReference<?>) get(right, "source")).get() == null,
                    "actual uninstall drops Phone and listener references");
            right.bind(phone); right.configure(true); right.progress(.1f, true);
            near(right.nativeAlpha(phone, .37f), .37f, "late callbacks after uninstall cannot intercept native alpha");
            check(phone.attachListeners.isEmpty(), "late callbacks cannot re-register an uninstalled owner");
        } finally { removed.setBoolean(null, previous); }
        right.configure(true); right.bind(phone);
        check((Boolean) get(right, "released") && ((WeakReference<?>) get(right, "source")).get() == null,
                "permanent released owner cannot re-enable if an external lifecycle flag changes");
    }
    private static void fixedPhoneAnchorChecks() throws Exception {
        NativeCoordinates root = new NativeCoordinates(0, 0, 1440, 3168);
        NativeCoordinates qs = new NativeCoordinates(0, 0, 1440, 3168);
        NativeCoordinates row = new NativeCoordinates(1050, 620, 180, 40);
        NativeCoordinates phone = new NativeCoordinates(1080, 28, 310, 68);
        root.addView(qs); qs.addView(row);
        StatusBarFixedIcons fixed = new StatusBarFixedIcons();
        fixed.observePhoneAnchor(phone); fixed.setPhoneCaptureAllowed(true); fixed.setPhoneCaptureAllowed(false);
        java.lang.reflect.Method destination = StatusBarFixedIcons.class.getDeclaredMethod("destination", View.class, View.class, View.class, int.class, int.class);
        destination.setAccessible(true);
        check((Boolean) destination.invoke(fixed, root, row, phone, 180, 40),
                "compiled QS right slot uses actual cached closed Phone baseline");
        float[] resolved = (float[]) get(fixed, "destination");
        near(resolved[0], 1210f, "QS row shares original Phone right edge instead of lower native header margin");
        near(resolved[1], 42f, "QS row shares original Phone center instead of native header title height");
        row.screenX = 1032; row.screenY = 640; qs.x = 720f;
        phone.screenX = 10; phone.screenY = 500; phone.width = 420;
        check((Boolean) destination.invoke(fixed, root, row, phone, 180, 40), "live native page and Phone layout writes keep the pre-expand basis");
        near(resolved[0], 1210f, "horizontal/native header relayout cannot shift original final X");
        near(resolved[1], 42f, "vertical/native header relayout cannot shift original final Y");
        check((Boolean) destination.invoke(fixed, root, row, phone, 250, 50), "live row dimensions fit around original Phone edge/center");
        near(resolved[0] + 250f, 1390f, "network speed remeasure retains pre-expand right edge");
        near(resolved[1] + 25f, 62f, "network speed remeasure retains pre-expand center");
        Object buffer = get(fixed, "destination"); int layouts = row.layoutRequests, redraws = row.invalidations;
        for (int frame = 0; frame < 10000; frame++) destination.invoke(fixed, root, row, phone, 180, 40);
        check(get(fixed, "destination") == buffer && row.layoutRequests == layouts && row.invalidations == redraws,
                "ten thousand fixed destination reads reuse buffers and request no layout/redraw");
        near(phone.getAlpha(), 1f, "native destination calculation does not write original Phone alpha");
        check(phone.screenX == 10 && phone.screenY == 500 && phone.width == 420,
                "fixed slot retains native Phone geometry and does not write cached coordinates to it");
        phone.display.rotation = 1;
        check(!(Boolean) destination.invoke(fixed, root, row, phone, 180, 40),
                "rotation invalidates the exact Phone basis rather than painting a stale right row");
        phone.display.rotation = 0; fixed.observePhoneAnchor(new NativeCoordinates(0, 0, 120, 50));
        check(!(Boolean) destination.invoke(fixed, root, row, phone, 180, 40), "replaced native Phone identity cannot retain retired baseline");
        StatusBarFixedIcons tintOwner = new StatusBarFixedIcons(); TintedSource tinted = new TintedSource();
        set(tintOwner, "source", new WeakReference<>(tinted)); set(tintOwner, "added", true);
        set(tintOwner, "reveal", 1f); set(tintOwner, "left", 1110f); set(tintOwner, "top", 38f);
        tinted.setAlpha(0f); tinted.paint.setColor(0xff000000);
        android.graphics.drawable.Drawable slot = (android.graphics.drawable.Drawable) get(tintOwner, "slot");
        Canvas lightCanvas = new Canvas(); slot.draw(lightCanvas);
        check(tinted.draws == 1 && lightCanvas.textColor == 0xff000000, "owned live draw keeps native dark tint on a light background");
        tinted.paint.setColor(0xffffffff); Canvas darkCanvas = new Canvas(); slot.draw(darkCanvas);
        check(tinted.draws == 2 && darkCanvas.textColor == 0xffffffff, "background tint update reaches the next live draw without a cached bitmap or fixed ColorFilter");
        near(lightCanvas.translateX, darkCanvas.translateX, "native background color handoff does not move right X");
        near(lightCanvas.translateY, darkCanvas.translateY, "native background color handoff does not move right Y");
        near(tinted.getAlpha(), 0f, "owned draw does not temporarily restore native row alpha or create a second native group");
    }

    private static void innerTileBridgeChecks() throws Exception {
        StatusBarTileIconMotion motion = new StatusBarTileIconMotion(StatusIconTransitionCheck.class.getClassLoader());
        ViewGroup root = new ViewGroup(new Context()), unrelatedRoot = new ViewGroup(new Context());
        com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView pager =
                new com.oplus.systemui.qs.base.widget.recyclerview.ParallelCOUIRecyclerView();
        pager.width = 1440; pager.measuredWidth = 1200; root.addView(pager);
        com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager manager =
                new com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager();
        manager.setRecyclerView(pager);
        pager.managerPresent = true; pager.nativeManager = manager;
        manager.nativeScrollTo(300); manager.onPageScrolled(); motion.changed(manager);
        near(motion.fraction(root), .25f, "inner tile callback reads native measured width, not view width or padding");
        near(motion.fraction(), 0f, "unqualified inner pager cannot drive another shade root");
        near(motion.fraction(unrelatedRoot), 0f, "foreign window retains its own endpoint");
        near(StatusIconTransition.reveal(1f, 0f, motion.fraction(root)), .5f,
                "inner tile leaving fades right slot even when outer notification/QS spring stays at origin");
        near(StatusIconTransition.offsetY(1f, 0f, motion.fraction(root), 2f), 18f,
                "inner tile leaving moves right slot downward from fixed Phone destination");
        manager.nativeScrollTo(600); manager.onPageScrolled(); motion.changed(manager);
        near(StatusIconTransition.reveal(1f, 1f, motion.fraction(root)), 0f,
                "actual inner midpoint fully fades one right row on either outer page endpoint");
        manager.nativeScrollTo(900); motion.changed(manager);
        near(motion.fraction(root), .75f, "native inner return side reads current pixel progress");
        near(StatusIconTransition.reveal(1f, 1f, motion.fraction(root)), .5f, "incoming tile page restores right alpha");
        near(StatusIconTransition.offsetY(1f, 1f, motion.fraction(root), 2f), -18f,
                "incoming right row returns downward along same 18dp path");
        manager.nativeScrollTo(1200); manager.onScrollStateChanged(0); motion.changed(manager);
        near(StatusIconTransition.reveal(1f, 0f, motion.fraction(root)), 1f, "second tile page settles fully visible");
        near(StatusIconTransition.offsetY(1f, 0f, motion.fraction(root), 2f), 0f,
                "second tile page final position is unchanged Phone baseline");
        manager.nativeScrollTo(300); motion.changed(manager); float beforeCancel = motion.fraction(root);
        manager.nativeScrollTo(301); motion.changed(manager);
        check(Math.abs(StatusIconTransition.reveal(1f, 0f, beforeCancel)
                - StatusIconTransition.reveal(1f, 0f, motion.fraction(root))) < .003f,
                "actual pixel reversal and cancel preserve alpha continuity without elapsed-time animation");
        manager.nativeScrollTo(0); motion.changed(manager);
        near(StatusIconTransition.reveal(1f, 0f, motion.fraction(root)), 1f, "canceled tile drag returns to full endpoint");
        manager.nativeScrollTo(-300); motion.changed(manager);
        near(motion.fraction(root), .25f, "RTL inner offset uses same continuous phase");
        manager.setPageCount(4); manager.nativeScrollTo(2700); motion.changed(manager);
        near(motion.fraction(root), .25f, "third/fourth tile pages use modulo width rather than clamp at page one");
        Object binding = get(motion, "current"), managerBinding = get(motion, "currentManager"); int redraws = pager.invalidations;
        for (int i = 0; i < 10000; i++) { motion.changed(manager); motion.fraction(root); }
        check(get(motion, "current") == binding && get(motion, "currentManager") == managerBinding,
                "ten thousand inner callbacks reuse weak view and manager bindings");
        check(manager.offsetArrays == 0 && pager.invalidations == redraws,
                "inner progress makes no currScrollOffset array and requests no pager redraw");
        float vertical = StatusIconTransition.verticalReveal(.325f);
        near(StatusIconTransition.reveal(.325f, .25f, .25f), vertical * .25f,
                "outer and inner motion combine alpha without vertically fading the fixed row");
        near(StatusIconTransition.offsetY(.325f, .25f, .25f, 2f), 36f,
                "combined motion remains bounded to original 18dp travel");
        near(StatusIconTransition.anchorX(960, 360, 0, 300), 1020f, "inner motion never enters fixed X calculation");
        manager.setPageCount(1); motion.changed(manager);
        near(motion.fraction(root), 0f, "single tile page cannot create a transition");
        manager.setPageCount(2); manager.horizontal = false; motion.changed(manager);
        near(motion.fraction(root), 0f, "vertical tile scrolling cannot animate horizontal slot");
        manager.horizontal = true; manager.nativeScrollTo(300); motion.changed(manager);
        pager.attached = false;
        near(motion.fraction(root), 0f, "detached native pager immediately retains endpoint");
        manager.nativeScrollTo(0); pager.attached = true;
        near(motion.fraction(root), 0f, "reopened native pager reset is read before first page callback");
        manager.nativeScrollTo(600);
        near(motion.fraction(root), .5f, "live native reset can resume new progress before next page callback");
        pager.measuredWidth = 2400;
        near(motion.fraction(root), .25f, "native width remeasure recomputes real phase without a page callback");
        pager.measuredWidth = 1200;
        manager.nativeScrollTo(300); pager.parent = unrelatedRoot;
        near(motion.fraction(root), 0f, "same pager moved to another root cannot affect original slot");
        pager.parent = root;
        motion.changed(new Object());
        near(motion.fraction(root), .25f, "foreign native type cannot replace exact inner owner");
        com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager replacement =
                new com.oplus.systemui.qs.base.widget.recyclerview.StaggeredPagerLayoutManager();
        replacement.setRecyclerView(pager); replacement.nativeScrollTo(900); pager.nativeManager = replacement;
        near(motion.fraction(root), 0f, "same view with replaced native manager cannot reuse retired half-page phase");
        motion.changed(replacement);
        near(motion.fraction(root), .75f, "replacement native manager reacquires its own live phase");
        pager.nativeManager = manager; motion.changed(manager);
        manager.fail = true;
        near(motion.fraction(root), 0f, "native getter failure retains endpoint instead of stale faded slot");
        manager.fail = false; motion.changed(manager);
        near(motion.fraction(root), .25f, "native inner source can recover without replacing main right slot");
        StatusBarFixedIcons removedOwner = new StatusBarFixedIcons(); removedOwner.setTileProgressReader(motion);
        Field removed = ModuleLifecycle.class.getDeclaredField("removed"); removed.setAccessible(true);
        boolean wasRemoved = removed.getBoolean(null);
        try {
            removed.setBoolean(null, true); removedOwner.releaseRuntime(); manager.nativeScrollTo(600); motion.changed(manager);
            near(motion.fraction(root), 0f, "actual uninstall disables late inner callback progression");
            check(get(removedOwner, "tileProgress") == null, "actual runtime release drops inner reader reference");
            removedOwner.setTileProgressReader(motion);
            check(get(removedOwner, "tileProgress") == null, "late source binding cannot re-register inner reader after removal");
        } finally { removed.setBoolean(null, wasRemoved); }
    }

    private static void separateQsBridgeChecks() throws Exception {
        NotificationBigClock receiver = new NotificationBigClock();
        android.os.Bundle safe = new android.os.Bundle();
        safe.putBoolean(StatusBarSettings.SAFE_MODE, true);
        receiver.configure(safe);
        set(receiver, "fraction", .35f); set(receiver, "barState", 0);
        View phone = new View(new Context());
        com.oplus.systemui.plugins.qs.OplusQSRootViewComponent component =
                new com.oplus.systemui.plugins.qs.OplusQSRootViewComponent();
        component.mView = new View(new Context());
        component.fakeStatusContainerController.statusIconsView = new ViewGroup(new Context());
        component.fakeStatusContainerController.fakeStatusIconContainer = new ViewGroup(new Context());
        View copy = component.fakeStatusContainerController.fakeStatusIconContainer;
        ViewGroup leftRoot = new ViewGroup(new Context()); component.mView = leftRoot;
        ViewGroup leftClockContainer = new ViewGroup(new Context()), leftNotificationContainer = new ViewGroup(new Context());
        leftRoot.addView(leftClockContainer); leftRoot.addView(leftNotificationContainer);
        View leftClockCopy = new View(new Context()), leftNotificationCopy = new View(new Context());
        leftClockContainer.addView(leftClockCopy); leftNotificationContainer.addView(leftNotificationCopy);
        View leftPhoneClock = new View(new Context()), leftPhoneNotifications = new View(new Context());
        component.fakeStatusContainerController.fakeClockContainer = leftClockContainer;
        component.fakeStatusContainerController.fakeNotificationIconContainer = leftNotificationContainer;
        component.fakeStatusContainerController.qsFakeClock = () -> leftClockCopy;
        component.fakeStatusContainerController.qsFakeNotificationIcon = () -> leftNotificationCopy;
        StatusBarQsIconAccess access = new StatusBarQsIconAccess(StatusIconTransitionCheck.class.getClassLoader(),
                receiver, nativeCopy -> nativeCopy == copy ? phone : nativeCopy == leftClockCopy ? leftPhoneClock
                        : nativeCopy == leftNotificationCopy ? leftPhoneNotifications : null);
        com.oplus.systemui.plugins.qs.OplusQSRootViewComponent$qsPanelExpandFractionListener$1 listener =
                new com.oplus.systemui.plugins.qs.OplusQSRootViewComponent$qsPanelExpandFractionListener$1();
        listener.this$0 = component;
        access.changed(component);
        Object leftOwner = get(receiver, "pageLeftIcons");
        check(((java.util.Map<?, ?>) get(leftOwner, "copies")).isEmpty()
                        && leftPhoneClock.attachListeners.isEmpty() && leftPhoneNotifications.attachListeners.isEmpty(),
                "real QS field/element getter path leaves both exact LEFT containers and Phone sources native");
        component.fakeStatusContainerController.qsFakeClock = () -> { throw new IllegalStateException("Native left element unavailable"); };
        access.changed(component);
        check(((java.util.Map<?, ?>) get(leftOwner, "copies")).isEmpty()
                        && ((WeakReference<?>) get(receiver, "separateQsPanel")).get() == component.mView,
                "optional LEFT getter failure cannot acquire overrides or disturb the successful RIGHT source");
        component.fakeStatusContainerController.qsFakeClock = () -> leftClockCopy; access.changed(component);
        check((Boolean) get(receiver, "separateQsSettledClosed"), "native pure-QS zero plus closed target settles independently");
        check(((WeakReference<?>) get(receiver, "separateQsIcons")).get()
                == component.fakeStatusContainerController.statusIconsView, "bind actual QS native row, not notification header");
        check(((WeakReference<?>) get(receiver, "separateQsPhone")).get() == phone,
                "pure-QS Phone anchor comes from its exact native fake binding");
        component.qsPanelAnimatorManager.target = 1f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.force.finalPosition = 1f;
        listener.onFractionChanged(.6f); access.listenerChanged(listener);
        near((Float) get(receiver, "separateQsFraction"), .6f, "QS displayed spring reaches independent right slot");
        near((Float) get(receiver, "fraction"), .35f, "QS spring cannot overwrite notification clock/footer progress");
        check((Boolean) get(receiver, "panelSettledClosed") && !(Boolean) get(receiver, "separateQsSettledClosed"),
                "pure-QS open does not require native notification panel to expand");
        java.lang.reflect.Method bothClosed = NotificationBigClock.class.getDeclaredMethod("statusIconsSettledClosed");
        bothClosed.setAccessible(true);
        check(!(Boolean) bothClosed.invoke(receiver), "notification closed cannot close active QS right slot");
        java.lang.reflect.Method rightState = NotificationBigClock.class.getDeclaredMethod("statusIconsBarState");
        rightState.setAccessible(true);
        component.stub.keyguard = true; access.changed(component);
        check((Integer) rightState.invoke(receiver) == 1, "QS native keyguard blocks ownership despite stale notification state");
        component.stub.keyguard = false; access.changed(component);
        check((Integer) rightState.invoke(receiver) == 0, "native unlock restores QS eligibility");
        Object qsBinding = get(receiver, "separateQsIcons"), ownerBinding = get(access, "current");
        int redraws = component.mView.invalidations;
        for (int i = 0; i < 10000; i++) access.listenerChanged(listener);
        check(get(receiver, "separateQsIcons") == qsBinding && get(access, "current") == ownerBinding,
                "ten thousand unchanged native QS callbacks reuse exact weak ownership bindings");
        check(component.mView.invalidations == redraws, "safe QS progress cannot request module redraws");
        component.curRawFraction = 0f; component.qsPanelAnimatorManager.target = 1f; access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"), "replacement/open target retains ownership across zero");
        component.qsPanelAnimatorManager.target = 0f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.force.finalPosition = 0f;
        component.running = true; access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"), "native spring still running at zero retains source");
        component.running = false; component.isTracking = true; access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"), "native drag canceled at zero remains pending until tracking ends");
        component.isTracking = false; access.listenerChanged(listener);
        check((Boolean) get(receiver, "separateQsSettledClosed") && (Boolean) bothClosed.invoke(receiver),
                "actual zero/target/tracking/end state releases pure-QS ownership");
        component.qsPanelAnimatorManager.target = 1f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.skipToEnd(false);
        access.listenerChanged(listener);
        check((Boolean) get(receiver, "separateQsSettledClosed")
                        && component.qsPanelAnimatorManager.getGetCurCalculateFinalPosition() == 1f,
                "real no-animation skipToEnd(false) closes despite stale manager target one");
        component.qsPanelAnimatorManager.target = 0f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.skipToEnd(true);
        access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"),
                "actual opening target cannot be replaced by stale manager zero");
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.force.finalPosition = 0f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.mPendingPosition = 1f;
        access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"),
                "queued native opening target overrides spring's previous closed target");
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.force.finalPosition = 1f;
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.mPendingPosition = 0f;
        component.running = true; access.changed(component);
        check(!(Boolean) get(receiver, "separateQsSettledClosed"),
                "queued closing target does not close while native spring is running");
        component.running = false; access.changed(component);
        check((Boolean) get(receiver, "separateQsSettledClosed"),
                "queued native zero target settles when raw zero and spring stops");
        component.qsPanelAnimatorManager.qsPanelExpandFraction.animation.mPendingPosition = Float.NaN;
        access.changed(component);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null,
                "invalid actual pending target restores owned native sources");
        component.qsPanelAnimatorManager.qsPanelExpandFraction.skipToEnd(false);
        access.changed(component);
        check((Boolean) get(receiver, "separateQsSettledClosed"),
                "MAX_VALUE no-pending sentinel resumes real force target reading after failure");
        set(receiver, "panelSettledClosed", false);
        check(!(Boolean) bothClosed.invoke(receiver), "closed QS cannot release an open notification page");
        component.stub.keyguard = true; access.changed(component);
        check((Integer) rightState.invoke(receiver) == 0, "retired closed-QS keyguard value cannot block current notification page");
        com.oplus.systemui.plugins.qs.OplusQSRootViewComponent replacement =
                new com.oplus.systemui.plugins.qs.OplusQSRootViewComponent();
        replacement.mView = new View(new Context());
        replacement.curRawFraction = 1f; replacement.qsPanelAnimatorManager.target = 1f;
        replacement.qsPanelAnimatorManager.qsPanelExpandFraction.animation.force.finalPosition = 1f;
        replacement.fakeStatusContainerController.statusIconsView = new ViewGroup(new Context());
        access.changed(replacement);
        access.detached(component);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == replacement.mView,
                "retired component detach does not release replacement QS owner");
        access.fakeChanged(component.fakeStatusContainerController);
        check(((WeakReference<?>) get(receiver, "separateQsIcons")).get()
                == replacement.fakeStatusContainerController.statusIconsView, "foreign fake controller updates are ignored");
        replacement.stub.fail = true; access.changed(replacement);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null
                && ((WeakReference<?>) get(access, "current")).get() == null,
                "native getter exception releases its actually owned QS source");
        replacement.stub.fail = false; access.changed(replacement);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == replacement.mView,
                "a recovered native getter can reacquire exact QS binding");
        component.stub.fail = true; access.changed(component);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null
                && ((WeakReference<?>) get(access, "current")).get() == null,
                "failed replacement releases previous actual owner rather than an unbound candidate");
        component.stub.fail = false; access.changed(replacement);
        access.detached(replacement);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null
                && ((WeakReference<?>) get(receiver, "separateQsIcons")).get() == null
                && (Boolean) get(receiver, "separateQsSettledClosed"), "QS detach clears only its row and state");
        access.changed(component);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == component.mView,
                "safe/detach observation allows exact native QS reattachment");
        component.curRawFraction = Float.NaN; access.changed(component);
        check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null,
                "nonfinite QS progress restores native instead of retaining a stale overlay");
        component.curRawFraction = 0f; access.changed(component);
        Field removed = ModuleLifecycle.class.getDeclaredField("removed"); removed.setAccessible(true);
        boolean previous = removed.getBoolean(null);
        try {
            removed.setBoolean(null, true); receiver.configure(safe); access.listenerChanged(listener);
            check(((WeakReference<?>) get(receiver, "separateQsPanel")).get() == null
                    && ((WeakReference<?>) get(receiver, "separateQsPhone")).get() == null,
                    "actual uninstall clears QS ownership and blocks later callback rebind");
        } finally { removed.setBoolean(null, previous); }
    }

    public static void main(String[] arguments) throws Exception {
        bigClockRightOwnerChecks();
        fixedPhoneAnchorChecks();
        near(StatusIconTransition.reveal(0f, 0f), 1f, "vertical handoff row retains full opacity");
        near(StatusIconTransition.reveal(.65f, 0f), 1f, "vertical opening retains full opacity");
        near(StatusIconTransition.offsetY(0f, 0f, 2f), 0f, "vertical entry has no travel");
        near(StatusIconTransition.horizontalReveal(0f), 1f, "origin page visible");
        near(StatusIconTransition.horizontalReveal(.5f), 0f, "crossing midpoint transparent");
        near(StatusIconTransition.horizontalReveal(1f), 1f, "destination page visible");
        near(StatusIconTransition.offsetY(1f, .5f, 2f), 36f, "horizontal exit moves down");
        near(StatusIconTransition.offsetY(1f, 1f, 2f), 0f, "horizontal entry returns down");
        near(StatusIconTransition.verticalReveal(.325f), 1f, "vertical spring cannot fade right row");
        near(StatusIconTransition.reveal(.325f, .25f), .5f, "only horizontal spring changes alpha");
        near(StatusIconTransition.offsetY(.325f, .25f, 2f), 18f, "only horizontal spring changes travel");
        near(StatusIconTransition.offsetY(.325f, 0f, 2f), 0f, "vertical partial alone remains exactly in place");
        near(StatusIconTransition.offsetY(1f, .25f, 2f), 18f, "horizontal departure alone moves down");
        near(StatusIconTransition.offsetY(0f, .5f, 2f), 36f, "transparent midpoint remains bounded to 18dp");
        for (int i = 0; i <= 100; i++) {
            float verticalFraction = i / 100f;
            near(StatusIconTransition.reveal(verticalFraction, 0f), 1f, "every native vertical fraction keeps the right row visible");
            near(StatusIconTransition.offsetY(verticalFraction, 0f, 4f), 0f, "every native vertical fraction keeps the right row in place");
            near(StatusIconTransition.reveal(verticalFraction, .25f), .5f, "vertical changes cannot perturb a horizontal fade");
        }
        float mixedPrevious = StatusIconTransition.offsetY(.325f, 0f, 2f);
        for (int i = 1; i <= 50; i++) {
            float p = i / 100f;
            float y = StatusIconTransition.offsetY(.325f, p, 2f);
            check(y >= mixedPrevious && Math.abs(y - mixedPrevious) < 1.5f,
                    "horizontal downward exit continuous while shade is half open");
            check(Math.abs(y) <= 36f, "combined travel remains within 18dp");
            if (p < .5f) near(y, -StatusIconTransition.offsetY(.325f, 1f-p, 2f), "departure and arrival travel share equal downward range");
            mixedPrevious = y;
        }
        float previous = 1f;
        for (int i = 1; i <= 100; i++) {
            float p = i / 200f, alpha = StatusIconTransition.horizontalReveal(p);
            check(alpha <= previous, "leaving alpha monotonic"); previous = alpha;
            near(alpha, StatusIconTransition.horizontalReveal(1f - p), "reverse page direction symmetry");
            check(StatusIconTransition.offsetY(1f, p, 1f) >= 0f, "departure stage stays below final Phone position");
        }
        previous = 0f;
        for (int i = 101; i <= 200; i++) {
            float p = i / 200f, alpha = StatusIconTransition.horizontalReveal(p);
            check(alpha >= previous, "arriving alpha monotonic"); previous = alpha;
        }
        // Keep the same gesture origin when velocity changes; only a full endpoint starts a new traversal.
        float[] reversed = {.0f, .1f, .3f, .48f, .3f, .1f, .0f};
        StatusIconTransition.HorizontalTravel phase = new StatusIconTransition.HorizontalTravel(false);
        for (float p : reversed) {
            near(StatusIconTransition.reveal(1f, p), StatusIconTransition.reveal(1f, 1f-p), "cancel restores same alpha");
            near(phase.fraction(p), 1f-StatusIconTransition.horizontalReveal(p), "halfway canceled gesture retraces same downward Y path");
        }
        near(phase.fraction(.4f), 1f-StatusIconTransition.horizontalReveal(.4f), "next departure starts downward");
        near(phase.fraction(.5f), 1f, "leaving reaches bottom while fully transparent");
        near(StatusIconTransition.horizontalReveal(.5f), 0f, "phase reset happens with zero opacity");
        near(phase.fraction(.6f), -(1f-StatusIconTransition.horizontalReveal(.6f)), "incoming side starts above and travels down");
        near(phase.fraction(.7f), -(1f-StatusIconTransition.horizontalReveal(.7f)), "incoming advances toward original Phone destination");
        near(phase.fraction(.6f), -(1f-StatusIconTransition.horizontalReveal(.6f)), "incoming half reversal retains the same Y rather than swapping direction");
        near(phase.fraction(1f), 0f, "incoming settles in original Phone position");
        near(phase.fraction(.75f), .5f, "reverse complete traversal also departs downward");
        near(phase.fraction(.25f), -.5f, "reverse complete traversal also arrives downward");
        near(phase.fraction(0f), 0f, "reverse traversal restores exact original position");
        StatusIconTransition.HorizontalTravel wrapped = new StatusIconTransition.HorizontalTravel(true);
        near(wrapped.fraction(0f), 0f, "inner page initial phase endpoint");
        near(wrapped.fraction(.75f), .5f, "wrapped previous-page departure also moves down");
        near(wrapped.fraction(.25f), -.5f, "wrapped previous-page arrival also moves down");
        near(wrapped.fraction(0f), 0f, "wrapped previous-page settles without stale traversal direction");
        near(wrapped.fraction(.25f), .5f, "wrapped next-page fresh departure starts downward");
        near(wrapped.fraction(.75f), -.5f, "wrapped next-page incoming also travels downward");
        for (int i = 0; i < 10000; i++) { phase.fraction(0f); wrapped.fraction(0f); }
        near(phase.fraction(0f) + wrapped.fraction(0f), 0f, "ten thousand unchanged phase callbacks keep exact endpoint without time animation");
        near(StatusIconTransition.anchorX(1080, 310, 0, 280), 1110f, "row right edge matches closed Phone screen edge");
        near(StatusIconTransition.anchorY(28, 68, 0, 48), 38f, "row center matches closed Phone center");
        near(StatusIconTransition.anchorX(1080, 310, 14, 280), 1096f, "root screen inset converted to local X");
        near(StatusIconTransition.anchorY(28, 68, 20, 48), 18f, "root screen inset converted to local Y");
        for (int rowWidth : new int[]{210, 280, 350, 420})
            near(StatusIconTransition.anchorX(1080, 310, 0, rowWidth) + rowWidth, 1390f,
                    "live row width changes retain cached pre-expand right edge");
        for (int rowHeight : new int[]{36, 48, 68, 82})
            near(StatusIconTransition.anchorY(28, 68, 0, rowHeight) + rowHeight*.5f, 62f,
                    "live row height changes retain cached pre-expand center");
        for (float p : reversed) {
            near(StatusIconTransition.anchorX(1080, 310, 0, 280), 1110f, "horizontal progress cannot shift final X");
            near(StatusIconTransition.anchorY(28, 68, 0, 48)
                    + StatusIconTransition.offsetY(1f, p, 1f),
                    38f + 18f * (1f-StatusIconTransition.horizontalReveal(p)),
                    "only transient travel modifies fixed Phone Y");
        }

        StatusBarFixedIcons owner = new StatusBarFixedIcons();
        View shade = new View(new Context()), copy = new View(new Context()); copy.parent = shade;
        Source qs = new Source(); qs.parent = shade;
        Source phone = new Source(), unknown = new Source();
        set(owner, "root", new WeakReference<>(shade)); set(owner, "source", new WeakReference<>(qs));
        set(owner, "anchor", new WeakReference<>(phone)); set(owner, "fraction", 1f); set(owner, "added", true);
        nativeFakeDraw(owner, copy, phone, StatusIconTransition.RIGHT);
        near(phone.draws, 0f, "owned native fake's direct source.draw is suppressed");
        nativeFakeDraw(owner, copy, unknown, StatusIconTransition.RIGHT);
        near(unknown.draws, 1f, "unknown native source retains its draw");
        check(!owner.suppressNativeCopy(copy, null, StatusIconTransition.RIGHT), "null binding falls back native");
        check(!owner.suppressNativeCopy(new View(new Context()), phone, StatusIconTransition.RIGHT), "different window/root retains native");
        copy.attached = false;
        check(!owner.suppressNativeCopy(copy, phone, StatusIconTransition.RIGHT), "detached copy retains native"); copy.attached = true;
        final java.util.ArrayList<String> traces = new java.util.ArrayList<>();
        Field diagnosticsFlag = ModuleDiagnostics.class.getDeclaredField("enabled"); diagnosticsFlag.setAccessible(true);
        boolean previousDiagnostics = diagnosticsFlag.getBoolean(null); diagnosticsFlag.setBoolean(null, true);
        owner.setCopyTraceListener(traces::add);
        owner.suppressNativeCopy(copy, unknown, StatusIconTransition.RIGHT);
        check(traces.size() == 1 && traces.get(0).contains("rightMatch=false")
                && traces.get(0).contains("skip=false"), "identity diagnostic records exact mismatch without broadening ownership");
        owner.suppressNativeCopy(copy, unknown, StatusIconTransition.RIGHT);
        check(traces.size() == 1, "same ownership snapshot is not logged every frame");
        check(!owner.suppressNativeCopy(copy, phone, StatusIconTransition.RIGHT, false, "safe"),
                "explicit runtime gate keeps known native copy visible");
        check(traces.get(traces.size()-1).contains("gate=safe"), "runtime rejection reason retained in diagnostic");
        owner.setCopyTraceListener(null);
        @SuppressWarnings("unchecked") ThreadLocal<Boolean> drawing = (ThreadLocal<Boolean>) get(owner, "drawingOwnedRow");
        drawing.set(Boolean.TRUE);
        nativeFakeDraw(owner, copy, phone, StatusIconTransition.RIGHT);
        near(phone.draws, 1f, "owned overlay drawing is explicitly allowed"); drawing.remove();
        for (int kind : new int[]{StatusIconTransition.CLOCK, StatusIconTransition.NOTIFICATIONS}) {
            check(owner.suppressNativeCopy(copy, phone, kind), "left Phone fake remains blank above close range");
            check(!owner.suppressNativeCopy(copy, qs, kind), "normal shade label is retained");
            set(owner, "fraction", .2f);
            check(!owner.suppressNativeCopy(copy, phone, kind), "closing 20 percent small-icon animation retained");
            set(owner, "fraction", 1f);
        }
        owner.onNativeCopyBound(copy, phone, StatusIconTransition.RIGHT);
        int invalidated = copy.invalidations;
        check(invalidated > 0, "first owned binding invalidates cached copy pixels");
        owner.onNativeCopyBound(copy, phone, StatusIconTransition.RIGHT);
        check(copy.invalidations == invalidated, "unchanged binding does not create a redraw loop");
        owner.onNativeCopyBound(copy, qs, StatusIconTransition.RIGHT);
        check(copy.invalidations > invalidated, "source replacement invalidates cached native pixels");
        for (String state : new String[]{"safe", "closed", "keyguard", "no-source"}) {
            set(owner, "added", false);
            check(!owner.suppressNativeCopy(copy, phone, StatusIconTransition.RIGHT), state + " released ownership retains native");
        }

        // A cached parent RenderNode may bypass dispatchDraw and drawChild entirely.
        // Discover exact copy bindings once and suppress only their native node alpha.
        StatusBarFixedIcons cachedOwner = new StatusBarFixedIcons();
        ViewGroup cachedRoot = new ViewGroup(new Context()), nativeRow = new ViewGroup(new Context());
        Source cachedPhone = new Source(), cachedUnknown = new Source();
        CachedCopy cachedKnown = new CachedCopy(cachedPhone), cachedForeign = new CachedCopy(cachedUnknown);
        CachedCopy ownRowCopy = new CachedCopy(cachedPhone);
        cachedRoot.addView(nativeRow); nativeRow.addView(ownRowCopy);
        cachedRoot.addView(cachedKnown); cachedRoot.addView(cachedForeign);
        set(cachedOwner, "root", new WeakReference<>(cachedRoot)); set(cachedOwner, "source", new WeakReference<>(nativeRow));
        set(cachedOwner, "anchor", new WeakReference<>(cachedPhone)); set(cachedOwner, "fraction", 1f); set(cachedOwner, "added", true);
        cachedOwner.setNativeCopyInspector(new StatusBarFixedIcons.NativeCopyInspector() {
            public int kind(View copy) { return copy instanceof CachedCopy ? StatusIconTransition.RIGHT : -1; }
            public View copiedSource(View copy) { return ((CachedCopy) copy).original; }
        });
        java.lang.reflect.Method discover = StatusBarFixedIcons.class.getDeclaredMethod("discoverNativeCopies"); discover.setAccessible(true);
        java.lang.reflect.Method suppress = StatusBarFixedIcons.class.getDeclaredMethod("suppressSources"); suppress.setAccessible(true);
        discover.invoke(cachedOwner); suppress.invoke(cachedOwner);
        near(cachedKnown.getAlpha(), 0f, "known cached native copy suppressed without executing any draw hook");
        near(cachedForeign.getAlpha(), 1f, "unknown cached native source retains original pixels");
        near(ownRowCopy.getAlpha(), 1f, "copy inside owned row remains available to overlay composition");
        check(((java.util.Map<?,?>) get(cachedOwner, "nativeCopies")).size() == 3, "cached bindings discovered before native Java drawing");
        cachedKnown.original = cachedUnknown; cachedOwner.onNativeCopyBound(cachedKnown, cachedUnknown, StatusIconTransition.RIGHT);
        suppress.invoke(cachedOwner);
        near(cachedKnown.getAlpha(), 1f, "source replacement outside ownership restores cached native copy");
        cachedKnown.original = cachedPhone; cachedOwner.onNativeCopyBound(cachedKnown, cachedPhone, StatusIconTransition.RIGHT);
        suppress.invoke(cachedOwner); cachedKnown.parent = null; suppress.invoke(cachedOwner);
        near(cachedKnown.getAlpha(), 1f, "copy moving to another root is restored immediately");
        cachedOwner.hide();
        near(nativeRow.getAlpha(), 1f, "owner release restores native QS row");
        near(cachedPhone.getAlpha(), 1f, "owner release restores native Phone row");

        // Both native pages have independent real right rows, but share one verified shade window.
        StatusBarFixedIcons dualOwner = new StatusBarFixedIcons();
        ViewGroup dualRoot = new ViewGroup(new Context());
        Source qsPageRow = new Source(), notificationPageRow = new Source(), dualPhone = new Source();
        dualRoot.addView(qsPageRow); dualRoot.addView(notificationPageRow);
        CachedCopy secondaryCopy = new CachedCopy(notificationPageRow); dualRoot.addView(secondaryCopy);
        set(dualOwner, "root", new WeakReference<>(dualRoot)); set(dualOwner, "source", new WeakReference<>(qsPageRow));
        set(dualOwner, "companionSource", new WeakReference<>(notificationPageRow));
        set(dualOwner, "anchor", new WeakReference<>(dualPhone)); set(dualOwner, "added", true); set(dualOwner, "fraction", 1f);
        dualOwner.onNativeCopyBound(secondaryCopy, notificationPageRow, StatusIconTransition.RIGHT);
        suppress.invoke(dualOwner);
        near(qsPageRow.getAlpha(), 0f, "owned QS native row suppressed");
        near(notificationPageRow.getAlpha(), 0f, "precisely bound other native page row cannot duplicate slot");
        near(secondaryCopy.getAlpha(), 0f, "cached copy of exact companion native row suppressed");
        check(dualOwner.suppressNativeCopy(secondaryCopy, notificationPageRow, StatusIconTransition.RIGHT),
                "direct-draw companion copy uses same exact ownership guard");
        check(!dualOwner.suppressNativeCopy(secondaryCopy, unknown, StatusIconTransition.RIGHT),
                "second row does not broaden suppression to unknown native sources");
        notificationPageRow.parent = null; suppress.invoke(dualOwner);
        near(notificationPageRow.getAlpha(), 1f, "companion leaving verified shade restores native immediately");
        near(secondaryCopy.getAlpha(), 1f, "copy of retired companion also restores immediately");
        notificationPageRow.parent = dualRoot;
        set(dualOwner, "companionSource", new WeakReference<>(notificationPageRow)); suppress.invoke(dualOwner);
        dualOwner.hide();
        near(qsPageRow.getAlpha(), 1f, "close/safe releases primary QS native alpha");
        near(notificationPageRow.getAlpha(), 1f, "close/safe releases companion notification native alpha");
        check(((WeakReference<?>) get(dualOwner, "companionSource")).get() == null, "release clears exact other-page identity");
        separateQsBridgeChecks();
        innerTileBridgeChecks();

        StatusBarFixedIcons redrawOwner = new StatusBarFixedIcons();
        View redrawRoot = new View(new Context()), redrawSource = new View(new Context());
        set(redrawOwner, "source", new WeakReference<>(redrawSource));
        set(redrawOwner, "added", true); set(redrawOwner, "reveal", 1f);
        android.graphics.drawable.Drawable redrawSlot = (android.graphics.drawable.Drawable) get(redrawOwner, "slot");
        redrawSlot.setCallback(redrawRoot);
        java.lang.reflect.Method redraw = StatusBarFixedIcons.class.getDeclaredMethod("requestRedraw", boolean.class);
        redraw.setAccessible(true);
        for (int frame = 0; frame < 10000; frame++) redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 0, "ten thousand steady traversals do not request a next frame");
        redraw.invoke(redrawOwner, true);
        check(redrawRoot.invalidations == 1, "actual spring/position changes request redraw");
        redrawSource.dirty = true; redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 2, "native content changes refresh the live QS row");
        redrawSource.dirty = false; redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 2, "native source draw clearing dirty ends content redraw");
        redrawSource.dirty = true; set(redrawOwner, "reveal", 0f); redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 2, "transparent source cannot create an uncleared dirty loop");
        set(redrawOwner, "reveal", .0001f); redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 2, "rounded zero alpha also skips uncleared dirty loop");
        set(redrawOwner, "reveal", 1f); redraw.invoke(redrawOwner, false);
        check(redrawRoot.invalidations == 3, "returning page refreshes content changed while hidden");
        set(redrawOwner, "added", false); redraw.invoke(redrawOwner, true);
        check(redrawRoot.invalidations == 3, "released/safe ownership requests no overlay redraw");

        StatusBarClosingIcons transparentClosing = new StatusBarClosingIcons();
        View closingRoot = new View(new Context()), closingClock = new View(new Context());
        for (float hiddenFraction : new float[]{1f, .8f, .2f, .19999f}) {
            check(transparentClosing.show(closingRoot, closingClock, hiddenFraction),
                    "transparent return slot keeps caller alpha ownership");
            check(!(Boolean) get(transparentClosing, "added") && get(transparentClosing, "observer") == null,
                    "fully transparent return slot has no draw observer");
            check(((java.util.Map<?,?>) get(transparentClosing, "sources")).isEmpty(),
                    "transparent slot retains no stale source owner");
        }
        near(closingClock.getAlpha(), 1f, "closing helper never changes caller-owned native alpha");
        long closingGeneration = (Long) get(transparentClosing, "generation");
        set(transparentClosing, "failedRoot", new WeakReference<>(closingRoot));
        for (int frame = 0; frame < 10000; frame++) transparentClosing.show(closingRoot, closingClock, 1f);
        check(closingRoot.invalidations == 0 && get(transparentClosing, "observer") == null,
                "ten thousand fully expanded return updates create no draw traversal");
        check((Long) get(transparentClosing, "generation") == closingGeneration,
                "empty transparent release does not advance generation each native callback");
        check(((WeakReference<?>) get(transparentClosing, "failedRoot")).get() == closingRoot,
                "transparent release preserves failure retry ownership");

        StatusBarClosingIcons visibleClosing = new StatusBarClosingIcons();
        View closingNotifications = new View(new Context());
        Class<?> closingState = Class.forName("dev.puitheme.StatusBarClosingIcons$SourceState");
        java.lang.reflect.Constructor<?> stateConstructor = closingState.getDeclaredConstructor(View.class);
        stateConstructor.setAccessible(true);
        Object clockState = stateConstructor.newInstance(closingClock);
        Object notificationState = stateConstructor.newInstance(closingNotifications);
        @SuppressWarnings("unchecked") java.util.Map<View,Object> closingSources =
                (java.util.Map<View,Object>) get(visibleClosing, "sources");
        closingSources.put(closingClock, clockState); closingSources.put(closingNotifications, notificationState);
        set(visibleClosing, "added", true); set(visibleClosing, "opacity", .5f);
        android.graphics.drawable.Drawable closingSlot = (android.graphics.drawable.Drawable) get(visibleClosing, "slot");
        closingSlot.setCallback(closingRoot);
        Object drawingBuffer = get(visibleClosing, "drawingSources");
        java.lang.reflect.Method closingRedraw = StatusBarClosingIcons.class.getDeclaredMethod("requestRedraw", boolean.class);
        closingRedraw.setAccessible(true);
        for (int frame = 0; frame < 10000; frame++) closingRedraw.invoke(visibleClosing, false);
        check(closingRoot.invalidations == 0, "paused closing progress does not request its own next frame");
        check(drawingBuffer == get(visibleClosing, "drawingSources") && get(clockState, "linear") == null,
                "steady return redraw reuses its buffer without allocating matrix geometry");
        closingRedraw.invoke(visibleClosing, true);
        check(closingRoot.invalidations == 1, "closing opacity/real geometry changes refresh");
        closingNotifications.dirty = true; closingRedraw.invoke(visibleClosing, false);
        check(closingRoot.invalidations == 2, "notification source dirty refreshes independently of clock");
        closingNotifications.dirty = false; closingClock.dirty = true; closingRedraw.invoke(visibleClosing, false);
        check(closingRoot.invalidations == 3, "clock source dirty refreshes independently of notifications");
        closingClock.dirty = false; closingRedraw.invoke(visibleClosing, false);
        check(closingRoot.invalidations == 3, "source drawing clearing dirty stops return redraw");
        set(visibleClosing, "opacity", 0f); closingClock.dirty = true; closingRedraw.invoke(visibleClosing, true);
        check(closingRoot.invalidations == 3, "transparent closing owner cannot drive a dirty redraw loop");
        set(visibleClosing, "added", false); set(visibleClosing, "opacity", 1f);
        closingRedraw.invoke(visibleClosing, true);
        check(closingRoot.invalidations == 3, "released safe closing owner cannot redraw");

        owner.setCopyTraceListener(traces::add);
        int beforeReset = traces.size();
        set(owner, "traceCount", 64); owner.resetCopyTrace();
        set(owner, "added", true);
        owner.suppressNativeCopy(copy, phone, StatusIconTransition.RIGHT);
        check(traces.size() > beforeReset, "verified probe reset restores bounded ownership diagnostics");
        check((Integer) get(owner, "traceCount") < 64, "diagnostic reset starts a new bounded session");
        owner.setCopyTraceListener(null);

        Pager pager = new Pager();
        StatusBarIconPageMotion motion = new StatusBarIconPageMotion(StatusIconTransitionCheck.class.getClassLoader(), Pager.class);
        motion.bind(pager);
        ProgressiveProgressDriver driver = pager.slidSwitchAnimation.value.controller.helper.progressDriver;
        driver.onAnimatedValue(720f); near(motion.fraction(), .5f, "actual native logical offset read");
        check(motion.owns(driver), "current pager driver owned");
        check(!motion.owns(new ProgressiveProgressDriver()), "other tile/rubberband driver ignored");
        driver.onAnimatedValue(-360f); near(motion.fraction(), .25f, "opposite swipe direction uses same model");
        driver.onAnimatedValue(0f); near(motion.fraction(), 0f, "cancelled native drag returns origin");
        Helper replacement = new Helper(); replacement.progressDriver.onAnimatedValue(1080f);
        pager.slidSwitchAnimation.value.controller.helper = replacement;
        check(!motion.owns(driver), "native helper reset releases old driver");
        check(motion.owns(replacement.progressDriver), "new helper driver rebound");
        near(motion.fraction(), .75f, "new helper actual progress retained");
        pager.slidSwitchAnimation.value.controller.helper = null;
        near(motion.fraction(), 0f, "unavailable helper retains vertical-only behavior");
        near(StatusIconTransition.pageFraction(Float.NaN, 1440f), 0f, "nonfinite offset rejected");
        near(StatusIconTransition.pageFraction(720f, 0f), 0f, "zero width rejected");
        near(StatusIconTransition.pageFraction(2000f, 1440f), 1f, "spring overshoot bounded");

        StatusBarFixedIcons removalOwner = new StatusBarFixedIcons();
        View removalPhone = new View(new Context()), removalQs = new View(new Context());
        View removalCopy = new View(new Context());
        removalOwner.observePhoneAnchor(removalPhone);
        check(removalPhone.layoutListeners.size() == 1 && removalPhone.attachListeners.size() == 1
                && removalPhone.getViewTreeObserver().preDrawListeners.size() == 1,
                "actual Phone baseline listeners registered exactly once");
        removalOwner.hide(); removalOwner.releaseRuntime();
        check(removalPhone.layoutListeners.size() == 1 && removalPhone.attachListeners.size() == 1
                && removalPhone.getViewTreeObserver().preDrawListeners.size() == 1,
                "ordinary hide/safety without removal retains reactivation observations");
        removalOwner.setHorizontalProgressReader(() -> 0f);
        removalOwner.setCopyTraceListener(message -> { });
        removalOwner.setNativeCopyInspector(new StatusBarFixedIcons.NativeCopyInspector() {
            public int kind(View value) { return StatusIconTransition.RIGHT; }
            public View copiedSource(View value) { return removalPhone; }
        });
        removalOwner.setFailureListener(() -> { });
        removalOwner.onNativeCopyBound(removalCopy, removalPhone, StatusIconTransition.RIGHT);
        set(removalOwner, "source", new WeakReference<>(removalQs));
        set(removalOwner, "anchor", new WeakReference<>(removalPhone));
        set(removalOwner, "failedRoot", new WeakReference<>(closingRoot));
        set(removalOwner, "failedSource", new WeakReference<>(removalQs));
        set(removalOwner, "failedAnchor", new WeakReference<>(removalPhone));
        suppress.invoke(removalOwner);
        near(removalQs.getAlpha(), 0f, "removal setup owns actual native QS alpha");
        near(removalPhone.getAlpha(), 0f, "removal setup owns actual Phone alpha");
        Field removedField = ModuleLifecycle.class.getDeclaredField("removed"); removedField.setAccessible(true);
        boolean previouslyRemoved = removedField.getBoolean(null);
        try {
            removedField.setBoolean(null, true);
            removalOwner.releaseRuntime();
            near(removalQs.getAlpha(), 1f, "package removal restores owned QS alpha");
            near(removalPhone.getAlpha(), 1f, "package removal restores owned Phone alpha");
            check(removalPhone.layoutListeners.isEmpty() && removalPhone.attachListeners.isEmpty()
                    && removalPhone.getViewTreeObserver().preDrawListeners.isEmpty(),
                    "package removal detaches all three actual Phone observers");
            check(get(removalOwner, "phoneObserver") == null && get(removalOwner, "phonePosition") == null
                    && ((WeakReference<?>) get(removalOwner, "observedPhone")).get() == null,
                    "removed baseline caches no longer reference native Phone");
            for (String cache : new String[]{"suppressed", "nativeCopies", "copyTraceStates", "traceIds"})
                check(((java.util.Map<?,?>) get(removalOwner, cache)).isEmpty(), "removal clears " + cache);
            for (String reference : new String[]{"horizontalProgress", "copyInspector", "copyTrace", "failureListener"})
                check(get(removalOwner, reference) == null, "removal clears " + reference);
            for (String reference : new String[]{"failedRoot", "failedSource", "failedAnchor", "source", "anchor", "root"})
                check(((WeakReference<?>) get(removalOwner, reference)).get() == null, "removal clears " + reference);
            removalOwner.observePhoneAnchor(removalPhone);
            removalOwner.onNativeCopyBound(removalCopy, removalPhone, StatusIconTransition.RIGHT);
            removalOwner.resetCopyTrace(); removalOwner.onHorizontalProgressChanged();
            removalOwner.setPhoneCaptureAllowed(true);
            removalOwner.setHorizontalProgressReader(() -> 1f);
            removalOwner.setCopyTraceListener(message -> { });
            removalOwner.releaseRuntime();
            check(removalPhone.layoutListeners.isEmpty() && removalPhone.attachListeners.isEmpty()
                    && removalPhone.getViewTreeObserver().preDrawListeners.isEmpty(),
                    "late native binds/init cannot register after actual uninstall");
            check(((java.util.Map<?,?>) get(removalOwner, "nativeCopies")).isEmpty()
                    && get(removalOwner, "horizontalProgress") == null && get(removalOwner, "copyTrace") == null
                    && !(Boolean) get(removalOwner, "phoneCaptureAllowed"),
                    "late callbacks cannot repopulate removed runtime references");
        } finally { removedField.setBoolean(null, previouslyRemoved); }
        removalOwner.observePhoneAnchor(removalPhone);
        check(removalPhone.layoutListeners.isEmpty() && (Boolean) get(removalOwner, "runtimeReleased"),
                "permanently released instance stays released even if external lifecycle flag changes");

        StatusBarFixedIcons diagnosticOwner = new StatusBarFixedIcons();
        final java.util.ArrayList<String> disabledRaw = new java.util.ArrayList<>();
        diagnosticOwner.setCopyTraceListener(disabledRaw::add);
        diagnosticsFlag.setBoolean(null, false); diagnosticOwner.configureDiagnostics();
        for (int frame = 0; frame < 10000; frame++) {
            diagnosticOwner.traceRuntimeStage("gate panel missing");
            diagnosticOwner.suppressNativeCopy(copy, phone, StatusIconTransition.RIGHT);
        }
        check((Integer) get(diagnosticOwner, "diagnosticCount") == 0
                && (Integer) get(diagnosticOwner, "traceCount") == 0 && disabledRaw.isEmpty(),
                "diagnostics off uses no short/raw event budget across ten thousand callbacks");
        check(java.util.Arrays.equals((String[]) get(diagnosticOwner, "diagnosticStages"), new String[4]),
                "diagnostics off never builds a structural stage snapshot");
        diagnosticsFlag.setBoolean(null, true); diagnosticOwner.configureDiagnostics();
        for (int frame = 0; frame < 10000; frame++) diagnosticOwner.traceRuntimeStage("gate panel missing");
        check((Integer) get(diagnosticOwner, "diagnosticCount") == 1,
                "unchanged opt-in stage is recorded once across ten thousand callbacks");
        for (int stage = 0; stage < 100; stage++) diagnosticOwner.traceRuntimeStage("test structural stage " + stage);
        check((Integer) get(diagnosticOwner, "diagnosticCount") == 40, "short structural diagnostics are bounded to forty events");
        diagnosticOwner.resetCopyTrace();
        check((Integer) get(diagnosticOwner, "diagnosticCount") < 40,
                "authenticated fresh probe resets structural budget and inventories current ownership");
        diagnosticsFlag.setBoolean(null, false); diagnosticOwner.configureDiagnostics();
        diagnosticsFlag.setBoolean(null, true); diagnosticOwner.configureDiagnostics();
        check((Integer) get(diagnosticOwner, "diagnosticCount") == 0
                && (Integer) get(diagnosticOwner, "traceCount") == 0,
                "opt-in rising edge resets both short and raw diagnostic budgets");
        diagnosticsFlag.setBoolean(null, previousDiagnostics);
        System.out.println("StatusIconTransitionCheck passed: " + checks + " (compiled native copy ownership, cached pixels, actual driver identity, reversal/cancel continuity)");
    }
}
