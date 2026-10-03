package dev.puitheme;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;

/** Models a hardware display list separately from alpha/property changes. No device claim. */
public final class StatusBarNativeCopyDrawCacheCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static final class Source extends View {
        int propertyWrites;
        Source() { super(new Context()); }
        @Override public void setAlpha(float value) { propertyWrites++; super.setAlpha(value); }
        @Override public void setTransitionAlpha(float value) { propertyWrites++; super.setTransitionAlpha(value); }
        @Override public void setTranslationX(float value) { propertyWrites++; super.setTranslationX(value); }
        @Override public void setTranslationY(float value) { propertyWrites++; super.setTranslationY(value); }
    }
    private static final class CachedFrame extends View {
        boolean dirty = true, cachedContent;
        int recordings;
        CachedFrame() { super(new Context()); }
        @Override public void invalidate() { super.invalidate(); dirty = true; }
        void draw(StatusBarNativeCopyDrawCache owner, View source, int kind, boolean suppressed) {
            if (!dirty) return;
            dirty = false; recordings++;
            owner.observed(this, source, kind, suppressed);
            cachedContent = !suppressed;
        }
    }
    private static Map<?, ?> copies(StatusBarNativeCopyDrawCache cache) throws Exception {
        Field field = StatusBarNativeCopyDrawCache.class.getDeclaredField("copies");
        field.setAccessible(true); return (Map<?, ?>) field.get(cache);
    }

    private static void realReceiverHandoff() throws Exception {
        NotificationBigClock receiver = new NotificationBigClock();
        android.os.Bundle settings = new android.os.Bundle();
        settings.putBoolean(StatusBarShadeIconSettings.MASTER, true);
        receiver.configure(settings);
        ViewGroup panel = new ViewGroup(new Context()), qs = new ViewGroup(new Context());
        ViewGroup clockOwner = new ViewGroup(new Context()), notificationOwner = new ViewGroup(new Context());
        CachedFrame clockFrame = new CachedFrame(), notificationFrame = new CachedFrame();
        Source phoneClock = new Source(), phoneNotices = new Source();
        panel.addView(clockOwner); panel.addView(notificationOwner);
        clockOwner.addView(clockFrame); notificationOwner.addView(notificationFrame);
        receiver.onPanelMotionState(true, false); receiver.onPanelChanged(panel, 1f, 0, false);
        receiver.onFakeClockChanged(clockOwner, 1f, phoneClock);
        receiver.onFakeNotificationChanged(notificationOwner, phoneNotices, 1f);
        check(receiver.suppressStatusIconCopy(clockFrame, phoneClock, StatusIconTransition.CLOCK),
                "Compiled receiver recognizes the inner clock frame below its alpha owner");
        check(receiver.suppressStatusIconCopy(notificationFrame, phoneNotices, StatusIconTransition.NOTIFICATIONS),
                "Compiled receiver recognizes the inner notification frame below its alpha owner");
        receiver.onStatusIconCopyDrawDecision(clockFrame, phoneClock, StatusIconTransition.CLOCK, true);
        receiver.onStatusIconCopyDrawDecision(notificationFrame, phoneNotices, StatusIconTransition.NOTIFICATIONS, true);
        for (int i=0; i<1000; i++) receiver.onStatusIconHorizontalProgressChanged();
        check(clockFrame.invalidations == 0 && notificationFrame.invalidations == 0,
                "Actual receiver steady refresh does not invalidate a suppressed display list");
        receiver.onSeparateQsStatusChanged(qs, null, null, null, .2f, 0, false);
        receiver.onPanelMotionState(false, true); receiver.onPanelChanged(panel, 0f, 0, false);
        check(clockFrame.invalidations == 0 && notificationFrame.invalidations == 0,
                "One page crossing zero does not release copies while the other native page is moving");
        receiver.onSeparateQsStatusChanged(qs, null, null, null, 0f, 0, true);
        check(clockFrame.invalidations == 1 && notificationFrame.invalidations == 1,
                "Global settled close invalidates both exact inner frames before the next native draw");
        check(clockOwner.invalidations == 0 && notificationOwner.invalidations == 0,
                "Alpha owners are not mistaken for the cached fake frame");
        check(phoneClock.propertyWrites == 0 && phoneNotices.propertyWrites == 0,
                "Compiled close handoff does not write the original Phone properties");
        for (int i=0; i<1000; i++) receiver.onStatusIconHorizontalProgressChanged();
        check(clockFrame.invalidations == 1 && notificationFrame.invalidations == 1,
                "Repeated endpoint callbacks do not schedule repeated native redraws");
    }
    public static void main(String[] args) throws Exception {
        final boolean[] hiding = {true};
        final int[] gateReads = {0};
        StatusBarNativeCopyDrawCache cache = new StatusBarNativeCopyDrawCache((frame, source, kind) -> {
            gateReads[0]++; return hiding[0];
        });
        Source phone = new Source(); CachedFrame clock = new CachedFrame(), right = new CachedFrame();
        ViewGroup parent = new ViewGroup(new Context()); clock.parent = parent; right.parent = parent;
        View untouched = new View(new Context());
        cache.observed(untouched, phone, StatusIconTransition.CLOCK, false);
        cache.observed(phone, phone, StatusIconTransition.CLOCK, true);
        cache.observed(new View(new Context()), phone, -1, true);
        check(copies(cache).isEmpty(), "Only an actual suppressed native copy may be tracked");
        clock.draw(cache, phone, StatusIconTransition.CLOCK, true);
        right.draw(cache, phone, StatusIconTransition.RIGHT, true);
        check(!clock.cachedContent && !right.cachedContent, "Suppression records empty display lists");
        check(copies(cache).size() == 2, "Exact frames are retained, without parent/source ownership");
        for (int i=0; i<2000; i++) cache.refresh();
        check(clock.invalidations == 0 && right.invalidations == 0, "Stable ownership never redraws per frame");
        check(gateReads[0] == 4000, "Only the two previously hit frames are visited");
        hiding[0] = false; cache.refresh();
        check(clock.invalidations == 1 && right.invalidations == 1, "Closing handoff invalidates each empty child once");
        check(parent.invalidations == 0 && untouched.invalidations == 0, "Parents and unhit views remain untouched");
        clock.draw(cache, phone, StatusIconTransition.CLOCK, false);
        right.draw(cache, phone, StatusIconTransition.RIGHT, false);
        check(clock.cachedContent && right.cachedContent, "The first native redraw replaces both cached empty lists");
        for (int i=0; i<2000; i++) cache.refresh();
        check(clock.invalidations == 1 && right.invalidations == 1, "A settled closed page has no repeated redraw");
        hiding[0] = true; cache.refresh();
        check(clock.invalidations == 2 && right.invalidations == 2, "Next gesture also invalidates the cached native content once");
        clock.draw(cache, phone, StatusIconTransition.CLOCK, true);
        right.draw(cache, phone, StatusIconTransition.RIGHT, true);
        check(!clock.cachedContent && !right.cachedContent, "Previously native lists can be suppressed again");
        for (Object state : copies(cache).values()) {
            Field source = state.getClass().getDeclaredField("source"); source.setAccessible(true);
            check(source.get(state) instanceof WeakReference, "Tracked source does not retain its native window");
        }
        check(phone.propertyWrites == 0, "No alpha/transitionAlpha/translation write touches the Phone row");
        right.attached = false; cache.refresh();
        check(copies(cache).size() == 1 && right.invalidations == 2, "Detached frames are retired without redraw");
        Object state = copies(cache).values().iterator().next();
        Field source = state.getClass().getDeclaredField("source"); source.setAccessible(true);
        ((WeakReference<?>) source.get(state)).clear(); cache.refresh();
        check(clock.invalidations == 3 && copies(cache).isEmpty(), "Collected sources release a suppressed list once");
        clock.draw(cache, phone, StatusIconTransition.CLOCK, false);
        clock.invalidate(); clock.draw(cache, phone, StatusIconTransition.CLOCK, true);
        int before = clock.invalidations; cache.release();
        check(clock.invalidations == before+1 && copies(cache).isEmpty(), "Teardown invalidates exact recorded frames and clears weak ownership");
        cache.release(); cache.refresh();
        check(clock.invalidations == before+1, "Repeated teardown has no writes");
        CachedFrame direct = new CachedFrame(); cache.observed(direct, phone, StatusIconTransition.NOTIFICATIONS, true);
        cache.observed(direct, phone, StatusIconTransition.NOTIFICATIONS, false);
        check(direct.invalidations == 1, "An observed gate change outside a panel event also invalidates once");
        cache.refresh();
        check(direct.invalidations == 2, "A later authoritative gate can suppress the newly native list");
        check(phone.propertyWrites == 0, "Release and reversal still preserve native Phone properties");
        realReceiverHandoff();
        System.out.println("Native copy display-list handoff checks passed: " + checks);
    }
}
