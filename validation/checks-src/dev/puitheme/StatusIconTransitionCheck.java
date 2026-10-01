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
        public float screenWidth = 1440f;
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
        near(StatusIconTransition.offsetY(1f, 0f, motion.fraction(root), 2f), -18f,
                "inner tile leaving moves right slot upward from fixed Phone destination");
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
                "vertical, outer and inner motion combine alpha without overwriting native progress");
        near(StatusIconTransition.offsetY(.325f, .25f, .25f, 2f), -36f * (1f - vertical * .25f),
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
        StatusBarQsIconAccess access = new StatusBarQsIconAccess(StatusIconTransitionCheck.class.getClassLoader(),
                receiver, nativeCopy -> nativeCopy == copy ? phone : null);
        com.oplus.systemui.plugins.qs.OplusQSRootViewComponent$qsPanelExpandFractionListener$1 listener =
                new com.oplus.systemui.plugins.qs.OplusQSRootViewComponent$qsPanelExpandFractionListener$1();
        listener.this$0 = component;
        access.changed(component);
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
        near(StatusIconTransition.reveal(0f, 0f), 0f, "closed vertical row transparent");
        near(StatusIconTransition.reveal(.65f, 0f), 1f, "vertical completion retained");
        near(StatusIconTransition.offsetY(0f, 0f, 2f), -36f, "vertical entry remains 18dp above");
        near(StatusIconTransition.horizontalReveal(0f), 1f, "origin page visible");
        near(StatusIconTransition.horizontalReveal(.5f), 0f, "crossing midpoint transparent");
        near(StatusIconTransition.horizontalReveal(1f), 1f, "destination page visible");
        near(StatusIconTransition.offsetY(1f, .5f, 2f), -36f, "horizontal exit moves up");
        near(StatusIconTransition.offsetY(1f, 1f, 2f), 0f, "horizontal entry returns down");
        near(StatusIconTransition.verticalReveal(.325f), .5f, "vertical spring half reveal");
        near(StatusIconTransition.reveal(.325f, .25f), .25f, "two spring alphas multiply");
        near(StatusIconTransition.offsetY(.325f, .25f, 2f), -27f, "two spring travel combines within original 18dp");
        near(StatusIconTransition.offsetY(.325f, 0f, 2f), -18f, "vertical partial alone retains upward source");
        near(StatusIconTransition.offsetY(1f, .25f, 2f), -18f, "horizontal partial alone moves up");
        near(StatusIconTransition.offsetY(0f, .5f, 2f), -36f, "both transparent endpoints retain bounded travel");
        float mixedPrevious = StatusIconTransition.offsetY(.325f, 0f, 2f);
        for (int i = 1; i <= 50; i++) {
            float p = i / 100f;
            float y = StatusIconTransition.offsetY(.325f, p, 2f);
            check(y <= mixedPrevious && Math.abs(y - mixedPrevious) < 1.5f,
                    "horizontal exit continuous while shade is half open");
            check(Math.abs(y) <= 36f, "combined travel remains within 18dp");
            near(y, StatusIconTransition.offsetY(.325f, 1f-p, 2f), "combined reversal retains identical Y");
            mixedPrevious = y;
        }
        float previous = 1f;
        for (int i = 1; i <= 100; i++) {
            float p = i / 200f, alpha = StatusIconTransition.horizontalReveal(p);
            check(alpha <= previous, "leaving alpha monotonic"); previous = alpha;
            near(alpha, StatusIconTransition.horizontalReveal(1f - p), "reverse page direction symmetry");
            check(StatusIconTransition.offsetY(1f, p, 1f) <= 0f, "horizontal stage stays above final Phone position");
        }
        previous = 0f;
        for (int i = 101; i <= 200; i++) {
            float p = i / 200f, alpha = StatusIconTransition.horizontalReveal(p);
            check(alpha >= previous, "arriving alpha monotonic"); previous = alpha;
        }
        // Replay the actual same coordinates after a quick reversal/cancellation.
        float[] reversed = {.0f, .1f, .3f, .48f, .3f, .1f, .0f};
        for (float p : reversed) {
            near(StatusIconTransition.reveal(1f, p), StatusIconTransition.reveal(1f, 1f-p), "cancel restores same alpha");
            near(StatusIconTransition.offsetY(1f, p, 3f), StatusIconTransition.offsetY(1f, 1f-p, 3f), "cancel restores same Y");
        }
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
                    38f - 18f * (1f-StatusIconTransition.horizontalReveal(p)),
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
