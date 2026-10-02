// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie

package dev.puitheme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** One live native icon group with a fixed Phone destination and native horizontal fade travel. */
public final class StatusBarFixedIcons {
    public interface HorizontalProgressReader {
        float fraction();
        default float fraction(View shadeRoot) { return fraction(); }
    }
    public interface CopyTraceListener { void trace(String message); }
    public interface LeftCopyPolicy {
        boolean nativeQsCopy(View copy, View source, int kind);
        default boolean suppressNotificationCopy(View copy, View source, int kind) { return false; }
    }
    public interface NativeCopyInspector {
        int kind(View copy);
        View copiedSource(View copy) throws ReflectiveOperationException;
    }
    private WeakReference<View> root = new WeakReference<>(null);
    private WeakReference<View> source = new WeakReference<>(null);
    private WeakReference<View> companionSource = new WeakReference<>(null);
    private WeakReference<View> anchor = new WeakReference<>(null);
    private WeakReference<View> transitionCopy = new WeakReference<>(null);
    private WeakReference<View> failedRoot = new WeakReference<>(null);
    private WeakReference<View> failedSource = new WeakReference<>(null);
    private WeakReference<View> failedAnchor = new WeakReference<>(null);
    private final Map<View, AlphaState> suppressed = new WeakHashMap<>();
    private final Map<View, NativeCopy> nativeCopies = new WeakHashMap<>();
    private final Map<View, String> copyTraceStates = new WeakHashMap<>();
    private final Map<View, Integer> traceIds = new WeakHashMap<>();
    private final ThreadLocal<Boolean> drawingOwnedRow = new ThreadLocal<>();
    private HorizontalProgressReader horizontalProgress;
    private HorizontalProgressReader tileProgress;
    private final StatusIconTransition.HorizontalTravel pageTravel = new StatusIconTransition.HorizontalTravel(false);
    private final StatusIconTransition.HorizontalTravel tileTravel = new StatusIconTransition.HorizontalTravel(true);
    private CopyTraceListener copyTrace;
    private NativeCopyInspector copyInspector;
    private LeftCopyPolicy leftCopyPolicy;
    private int discoveredNodes;
    private boolean horizontalMoving;
    private int traceCount, nextTraceId = 1;
    private String lastAcquisitionTrace;
    private final String[] diagnosticStages = new String[4];
    private int diagnosticCount, verticalStage = -1, horizontalStage = -1, tileStage = -1;
    private boolean diagnosticsEnabled;
    private WeakReference<View> observedPhone = new WeakReference<>(null);
    private PhonePosition phonePosition;
    private boolean phoneCaptureAllowed;
    private ViewTreeObserver phoneObserver;
    private final int[] screenPosition = new int[2];
    private final float[] destination = new float[2];
    private final Rect target = new Rect();
    private final RectF layerBounds = new RectF(), nodeBounds = new RectF();
    private final Matrix[] descendantMatrices = new Matrix[20];
    private IconSlot slot = new IconSlot();
    private ViewTreeObserver observer;
    private Runnable failureListener;
    private boolean added, busy, leftCopiesSuppressed, positionChanged, runtimeReleased;
    private int warnings, measuredNodes;
    private long generation;
    private float fraction, reveal, left, top;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        if (!added) return true;
        try {
            if (!position()) { unavailable(null); return true; }
            // Native alpha writers also run outside the fraction listener. Reclaim only these
            // three precise sources in the actual traversal, before either window is drawn.
            suppressSources();
            requestRedraw(positionChanged);
        } catch (Throwable error) { unavailable(error); }
        return true;
    };
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { unavailable(null); }
    };

    public void setFailureListener(Runnable listener) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) failureListener = listener;
    }

    public void setHorizontalProgressReader(HorizontalProgressReader reader) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) horizontalProgress = reader;
    }

    public void setTileProgressReader(HorizontalProgressReader reader) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) tileProgress = reader;
    }

    public void setCopyTraceListener(CopyTraceListener listener) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) copyTrace = listener;
    }
    public void setNativeCopyInspector(NativeCopyInspector inspector) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) copyInspector = inspector;
    }
    public void setLeftCopyPolicy(LeftCopyPolicy policy) {
        if (!runtimeReleased && !ModuleLifecycle.removed()) leftCopyPolicy = policy;
    }
    /** Transfer only an exact registered left copy out of the old fixed-slot alpha owner. */
    public void restoreNativeLeftCopy(View copy) {
        NativeCopy binding = nativeCopies.get(copy);
        if (binding == null || binding.kind == StatusIconTransition.RIGHT) return;
        AlphaState previous = suppressed.remove(copy);
        if (previous != null) previous.restore(copy);
    }

    /** Called on the UI thread only after a fresh provider-authenticated runtime probe. */
    public void resetCopyTrace() {
        if (runtimeReleased || ModuleLifecycle.removed()) return;
        resetDiagnostics();
        diagnosticStage(0, added ? "probe owner active" : "probe owner inactive");
        diagnosticStage(2, phonePosition == null ? "probe baseline missing" : "probe baseline available");
        traceCount = 0;
        copyTraceStates.clear();
        lastAcquisitionTrace = null;
        traceAcquisition(added);
        // Inventory the already known bindings even when all native parents reuse
        // cached RenderNodes and no Java dispatchDraw/drawChild is needed this frame.
        for (Map.Entry<View, NativeCopy> entry : nativeCopies.entrySet()) {
            View copy = entry.getKey(); NativeCopy binding = entry.getValue();
            if (copy != null) suppressNativeCopy(copy, binding.source.get(), binding.kind, true, "probe");
        }
        if (added) invalidateNativeCopies();
    }

    /** A destination is learned only while the real Phone status bar is fully restored. */
    public void setPhoneCaptureAllowed(boolean allowed) {
        if (runtimeReleased || ModuleLifecycle.removed()) return;
        phoneCaptureAllowed = allowed;
        if (allowed) capturePhonePosition();
    }

    public void observePhoneAnchor(View phone) {
        if (runtimeReleased || ModuleLifecycle.removed()) return;
        if (phone == null || observedPhone.get() == phone) {
            if (phoneCaptureAllowed) capturePhonePosition();
            return;
        }
        View old = observedPhone.get();
        if (old != null) {
            old.removeOnLayoutChangeListener(phoneLayout);
            old.removeOnAttachStateChangeListener(phoneAttachment);
        }
        removePhoneObserver();
        phonePosition = null;
        observedPhone = new WeakReference<>(phone);
        phone.addOnLayoutChangeListener(phoneLayout);
        phone.addOnAttachStateChangeListener(phoneAttachment);
        registerPhoneObserver(phone);
        if (phoneCaptureAllowed) capturePhonePosition();
    }

    /** Only a source already verified by the exact native fake-frame binding is returned. */
    public View observedPhoneAnchor() {
        return runtimeReleased || ModuleLifecycle.removed() ? null : observedPhone.get();
    }

    public void configureDiagnostics() {
        boolean active = ModuleDiagnostics.enabled();
        if (active && !diagnosticsEnabled) {
            resetDiagnostics(); traceCount = 0; copyTraceStates.clear(); lastAcquisitionTrace = null;
        }
        diagnosticsEnabled = active;
    }

    public void traceRuntimeStage(String stage) { diagnosticStage(0, stage); }

    private void resetDiagnostics() {
        diagnosticCount = 0; verticalStage = horizontalStage = tileStage = -1;
        for (int i = 0; i < diagnosticStages.length; i++) diagnosticStages[i] = null;
    }

    private void diagnosticStage(int lane, String stage) {
        if (!ModuleDiagnostics.enabled() || runtimeReleased || diagnosticCount >= 40
                || stage.equals(diagnosticStages[lane])) return;
        diagnosticStages[lane] = stage; diagnosticCount++;
        ModuleDiagnostics.info("hook", "Icon slot " + stage);
    }

    private boolean positionFailure(String reason) { diagnosticStage(1, reason); return false; }

    private void diagnoseProgress(float vertical, float horizontal, float tile) {
        if (!ModuleDiagnostics.enabled() || runtimeReleased || diagnosticCount >= 40) return;
        int v = vertical <= .001f ? 0 : vertical >= .999f ? 2 : 1;
        int h = horizontal <= .001f ? 0 : horizontal >= .999f ? 2 : 1;
        int t = tile <= .001f || tile >= .999f ? 0 : 1;
        if (verticalStage == v && horizontalStage == h && tileStage == t) return;
        verticalStage = v; horizontalStage = h; tileStage = t;
        diagnosticStage(3, "fraction " + (v == 0 ? "zero" : v == 2 ? "full" : "partial")
                + ", horizontal " + (h == 0 ? "origin" : h == 2 ? "destination" : "moving")
                + ", tiles " + (t == 0 ? "settled" : "moving"));
    }

    private final View.OnLayoutChangeListener phoneLayout = (view, l, t, r, b, ol, ot, or, ob) -> {
        if (phoneCaptureAllowed) capturePhonePosition();
    };
    private final ViewTreeObserver.OnPreDrawListener phonePreDraw = () -> {
        if (phoneCaptureAllowed) capturePhonePosition();
        return true;
    };
    private final View.OnAttachStateChangeListener phoneAttachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            registerPhoneObserver(view);
            if (phoneCaptureAllowed) capturePhonePosition();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            phonePosition = null;
            removePhoneObserver();
            unavailable(null);
        }
    };

    private void registerPhoneObserver(View phone) {
        if (runtimeReleased || ModuleLifecycle.removed()) return;
        try {
            ViewTreeObserver current = phone.getViewTreeObserver();
            if (phoneObserver == current || !current.isAlive()) return;
            removePhoneObserver();
            phoneObserver = current;
            current.addOnPreDrawListener(phonePreDraw);
        } catch (Throwable ignored) { }
    }

    private void removePhoneObserver() {
        try { if (phoneObserver != null && phoneObserver.isAlive()) phoneObserver.removeOnPreDrawListener(phonePreDraw); }
        catch (Throwable ignored) { }
        phoneObserver = null;
    }

    private void capturePhonePosition() {
        View phone = observedPhone.get();
        if (!phoneCaptureAllowed || added) return;
        if (phone == null) { diagnosticStage(2, "baseline native anchor missing"); return; }
        if (!phone.isAttachedToWindow() || phone.getVisibility() != View.VISIBLE
                || phone.getWidth() <= 0 || phone.getHeight() <= 0) {
            diagnosticStage(2, "baseline native anchor not laid out"); return;
        }
        if (phone.getWindowToken() == null || phone.getDisplay() == null) {
            diagnosticStage(2, "baseline native window unavailable"); return;
        }
        try {
            phone.getLocationOnScreen(screenPosition);
            phonePosition = new PhonePosition(phone, screenPosition[0], screenPosition[1]);
            diagnosticStage(2, "baseline captured");
        } catch (Throwable ignored) { phonePosition = null; diagnosticStage(2, "baseline capture failed"); }
    }

    private static final class PhonePosition {
        final int x, y, width, height, displayId, rotation, orientation, screenWidth, screenHeight;
        final float density;
        final Object window;
        PhonePosition(View phone, int x, int y) {
            this.x = x; this.y = y;
            width = phone.getWidth(); height = phone.getHeight(); window = phone.getWindowToken();
            displayId = phone.getDisplay().getDisplayId(); rotation = phone.getDisplay().getRotation();
            orientation = phone.getResources().getConfiguration().orientation;
            android.util.DisplayMetrics metrics = phone.getResources().getDisplayMetrics();
            density = metrics.density; screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels;
        }
        boolean current(View phone) {
            android.util.DisplayMetrics metrics = phone.getResources().getDisplayMetrics();
            // Live network text may remeasure this same source while the shade is open.
            // Its cached right edge/center remains the destination until a settled close.
            return phone.getWindowToken() == window
                    && phone.getDisplay() != null && phone.getDisplay().getDisplayId() == displayId
                    && phone.getDisplay().getRotation() == rotation
                    && phone.getResources().getConfiguration().orientation == orientation
                    && density == metrics.density && screenWidth == metrics.widthPixels && screenHeight == metrics.heightPixels;
        }
    }

    /** OEM copies call source.draw directly, so hiding the source's alpha cannot suppress them. */
    public boolean suppressNativeCopy(View copy, View copiedSource, int kind) {
        return suppressNativeCopy(copy, copiedSource, kind, true, "owner");
    }

    public boolean suppressNativeCopy(View copy, View copiedSource, int kind, boolean permitted, String gate) {
        if (runtimeReleased || ModuleLifecycle.removed()) return false;
        View host = root.get();
        boolean sameShade = copy != null && host != null && copy.isAttachedToWindow()
                && copy.getRootView() == host;
        boolean ownedDraw = Boolean.TRUE.equals(drawingOwnedRow.get());
        boolean matching = copiedSource == source.get() || copiedSource == anchor.get()
                || copiedSource == companionSource.get();
        boolean phoneLeft = copiedSource != null && host != null && copiedSource.getRootView() != host;
        boolean skip = permitted && StatusIconTransition.suppressCopy(added, ownedDraw,
                kind, sameShade, copiedSource != null,
                matching, phoneLeft, fraction);
        if (skip && kind != StatusIconTransition.RIGHT && leftCopyPolicy != null)
            skip = leftCopyPolicy.suppressNotificationCopy(copy, copiedSource, kind)
                    && !leftCopyPolicy.nativeQsCopy(copy, copiedSource, kind);
        traceCopy(copy, copiedSource, kind, gate, sameShade, matching, ownedDraw, skip);
        return skip;
    }

    private void traceCopy(View copy, View copiedSource, int kind, String gate, boolean sameShade,
            boolean matching, boolean ownedDraw, boolean skip) {
        if (!ModuleDiagnostics.enabled() || copyTrace == null || copy == null || traceCount >= 64 || ownedDraw) return;
        try {
            float horizontal = horizontalProgress == null ? 0f : horizontalProgress.fraction();
            String state = gate + ":" + added + ":" + sameShade + ":" + matching + ":" + skip
                    + ":" + (horizontal > .01f && horizontal < .99f) + ":" + describe(copiedSource)
                    + ":" + describe(root.get()) + ":" + describe(source.get()) + ":" + describe(anchor.get());
            if (state.equals(copyTraceStates.get(copy))) return;
            copyTraceStates.put(copy, state);
            traceCount++;
            copyTrace.trace("copy " + traceCount + " kind=" + kind + " gate=" + gate + " owned=" + added
                    + " sameRoot=" + sameShade + " rightMatch=" + matching + " skip=" + skip
                    + " v=" + fraction + " h=" + horizontal + " basis=" + (phonePosition != null)
                    + " copy=" + describe(copy) + " copyRoot=" + describe(copy.getRootView())
                    + " nativeSource=" + describe(copiedSource)
                    + " sourceRoot=" + describe(copiedSource == null ? null : copiedSource.getRootView())
                    + " qs=" + describe(source.get()) + " nativeAnchor=" + describe(anchor.get())
                    + " host=" + describe(root.get()) + " ancestors=" + ancestors(copiedSource));
        } catch (Throwable ignored) { }
    }

    private String describe(View view) {
        if (view == null) return "null";
        Integer number = traceIds.get(view);
        if (number == null) { number = nextTraceId++; traceIds.put(view, number); }
        String resource = "none";
        try { if (view.getId() > 0) resource = view.getResources().getResourceEntryName(view.getId()); }
        catch (Throwable ignored) { }
        return view.getClass().getSimpleName() + "#" + number + "/" + resource;
    }

    private String ancestors(View view) {
        StringBuilder value = new StringBuilder();
        for (int depth = 0; view != null && depth < 5; depth++) {
            if (depth > 0) value.append('>');
            value.append(describe(view));
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return value.toString();
    }

    /** Track exact copy bindings before they record a RenderNode; keep only weak references. */
    public void onNativeCopyBound(View copy, View copiedSource, int kind) {
        if (runtimeReleased || ModuleLifecycle.removed()) return;
        if (copy == null || kind < StatusIconTransition.RIGHT || kind > StatusIconTransition.NOTIFICATIONS) return;
        NativeCopy previous = nativeCopies.get(copy);
        if (previous != null && previous.source.get() == copiedSource && previous.kind == kind) return;
        nativeCopies.put(copy, new NativeCopy(copiedSource, kind));
        if (suppressNativeCopy(copy, copiedSource, kind)) copy.invalidate();
    }

    public void onHorizontalProgressChanged() {
        if (!added || runtimeReleased || ModuleLifecycle.removed()) return;
        try {
            float progress = horizontalProgress == null ? 0f : horizontalProgress.fraction();
            float tile = tileProgress == null ? 0f : tileProgress.fraction(root.get());
            boolean moving = progress > .001f && progress < .999f || tile > .001f && tile < .999f;
            if (moving && !horizontalMoving) discoverNativeCopies();
            horizontalMoving = moving;
            if (position()) { suppressSources(); requestRedraw(positionChanged); } else unavailable(null);
        }
        catch (Throwable error) { unavailable(error); }
    }

    private static final class NativeCopy {
        final WeakReference<View> source;
        final int kind;
        NativeCopy(View source, int kind) { this.source = new WeakReference<>(source); this.kind = kind; }
    }

    private void invalidateNativeCopies() {
        View host = root.get();
        for (Map.Entry<View, NativeCopy> entry : new ArrayList<>(nativeCopies.entrySet())) {
            View copy = entry.getKey(), original = entry.getValue().source.get();
            if (copy == null || host == null || copy.getRootView() != host) continue;
            if (entry.getValue().kind != StatusIconTransition.RIGHT
                    || original == source.get() || original == anchor.get()
                    || original == companionSource.get()) copy.invalidate();
        }
    }

    /** An explicit configuration reset may retry a source that failed while drawing. */
    public void resetFailure() { failedRoot.clear(); failedSource.clear(); failedAnchor.clear(); }

    /**
     * Acquire all sources atomically after validating the destination. The native QS source,
     * its moving fake copy and the separate Phone window must never be shown alongside this
     * slot. Their alpha is restored on closed, keyguard, replacement, detach and failure.
     */
    public boolean show(View shadeRoot, View qsSource, View phoneAnchor, View fakeCopy,
            float nativeFraction) {
        return show(shadeRoot, qsSource, phoneAnchor, fakeCopy, null, nativeFraction);
    }

    /** The second native page has a distinct icon row, in the same verified shade window. */
    public boolean show(View shadeRoot, View qsSource, View phoneAnchor, View fakeCopy,
            View otherPageSource, float nativeFraction) {
        if (runtimeReleased || ModuleLifecycle.removed()) { releaseRuntime(); return false; }
        if (shadeRoot == null || qsSource == null || phoneAnchor == null || !finite(nativeFraction)) {
            release(); return false;
        }
        if (failedRoot.get() == shadeRoot && failedSource.get() == qsSource
                && failedAnchor.get() == phoneAnchor) return positionFailure("acquire retained failed source");
        View companion = otherPageSource != null && otherPageSource != qsSource
                && otherPageSource.isAttachedToWindow() && otherPageSource.getRootView() == shadeRoot
                && otherPageSource.getWindowToken() == shadeRoot.getWindowToken() ? otherPageSource : null;
        if (root.get() != shadeRoot || source.get() != qsSource || anchor.get() != phoneAnchor
                || transitionCopy.get() != fakeCopy || companionSource.get() != companion) {
            release();
            failedRoot.clear(); failedSource.clear(); failedAnchor.clear();
            root = new WeakReference<>(shadeRoot);
            source = new WeakReference<>(qsSource);
            companionSource = new WeakReference<>(companion);
            anchor = new WeakReference<>(phoneAnchor);
            transitionCopy = new WeakReference<>(fakeCopy);
        }
        fraction = Math.max(0f, Math.min(1f, nativeFraction));
        try {
            if (!position()) { traceAcquisition(false); release(); return false; }
            boolean first = !added;
            if (!added) {
                added = true;
                shadeRoot.getOverlay().add(slot);
                observer = shadeRoot.getViewTreeObserver();
                if (!observer.isAlive()) { release(); return false; }
                observer.addOnPreDrawListener(preDraw);
                shadeRoot.addOnAttachStateChangeListener(attachment);
                qsSource.addOnAttachStateChangeListener(attachment);
                phoneAnchor.addOnAttachStateChangeListener(attachment);
                if (fakeCopy != null) fakeCopy.addOnAttachStateChangeListener(attachment);
                // Bind cached copies too: a parent RenderNode need not call a Java draw
                // hook again until its pixels are explicitly invalidated.
                discoverNativeCopies();
                // Replace cached native copy pixels too, not just future dispatchDraw calls.
                invalidateNativeCopies();
            }
            suppressSources();
            requestRedraw(first || positionChanged);
            traceAcquisition(true);
            return true;
        } catch (Throwable error) { unavailable(error); return false; }
    }

    public void hide() { release(); pageTravel.reset(); tileTravel.reset(); }

    /** Permanent cleanup only after actual package removal; hide/safe mode retain reactivation bindings. */
    public void releaseRuntime() {
        if (runtimeReleased || !ModuleLifecycle.removed()) return;
        runtimeReleased = true;
        phoneCaptureAllowed = false;
        release();
        View phone = observedPhone.get();
        if (phone != null) {
            try { phone.removeOnLayoutChangeListener(phoneLayout); } catch (Throwable ignored) { }
            try { phone.removeOnAttachStateChangeListener(phoneAttachment); } catch (Throwable ignored) { }
        }
        removePhoneObserver();
        observedPhone.clear(); phonePosition = null;
        failedRoot.clear(); failedSource.clear(); failedAnchor.clear();
        nativeCopies.clear(); copyTraceStates.clear(); traceIds.clear();
        horizontalProgress = tileProgress = null; copyInspector = null; copyTrace = null; failureListener = null;
        pageTravel.reset(); tileTravel.reset();
        leftCopyPolicy = null;
        lastAcquisitionTrace = null; traceCount = 0; nextTraceId = 1;
        resetDiagnostics(); diagnosticsEnabled = false;
        discoveredNodes = measuredNodes = 0; positionChanged = false;
        drawingOwnedRow.remove();
        for (int i = 0; i < descendantMatrices.length; i++) descendantMatrices[i] = null;
    }

    private void traceAcquisition(boolean success) {
        if (success) diagnosticStage(1, "acquired with valid baseline");
        if (!ModuleDiagnostics.enabled() || copyTrace == null || traceCount >= 64) return;
        try {
            View phone = anchor.get();
            String state = success + ":" + describe(root.get()) + ":" + describe(source.get()) + ":" + describe(phone)
                    + ":" + describe(observedPhone.get()) + ":" + (phonePosition != null);
            if (state.equals(lastAcquisitionTrace)) return;
            lastAcquisitionTrace = state;
            traceCount++;
            copyTrace.trace("acquire " + traceCount + " success=" + success + " v=" + fraction
                    + " host=" + describe(root.get()) + " qs=" + describe(source.get())
                    + " nativeAnchor=" + describe(phone) + " capturedAnchor=" + describe(observedPhone.get())
                    + " basis=" + (phonePosition != null) + " basisCurrent=" + (phone != null
                    && phonePosition != null && phonePosition.current(phone)) + " x=" + left + " y=" + top);
        } catch (Throwable ignored) { }
    }

    private boolean position() {
        positionChanged = false;
        View host = root.get(), icons = source.get(), phone = anchor.get(), copy = transitionCopy.get();
        if (host == null || icons == null || phone == null) return positionFailure("acquire source or anchor missing");
        if (host == icons || host == phone) return positionFailure("acquire invalid source identity");
        if (!host.isAttachedToWindow() || !icons.isAttachedToWindow() || !phone.isAttachedToWindow())
            return positionFailure("acquire source or anchor detached");
        if (host.getWindowToken() == null || host.getWindowToken() != icons.getWindowToken()
                || phone.getWindowToken() == null) return positionFailure("acquire window mismatch");
        if (host.getDisplay() == null || phone.getDisplay() == null
                || host.getDisplay().getDisplayId() != phone.getDisplay().getDisplayId())
            return positionFailure("acquire display mismatch");
        if (copy != null && (!copy.isAttachedToWindow() || copy.getWindowToken() != host.getWindowToken()))
            return positionFailure("acquire native copy detached or replaced");
        int width = icons.getWidth(), height = icons.getHeight();
        if (width <= 0 || height <= 0 || icons.getVisibility() == View.GONE)
            return positionFailure("acquire status row not measured");
        if (host.getWidth() <= 0 || host.getHeight() <= 0 || width > host.getWidth() || height > host.getHeight())
            return positionFailure("acquire shade bounds unavailable");
        if (!destination(host, icons, phone, width, height)) return false;
        float x = destination[0], y = destination[1];
        float horizontalFraction = horizontalProgress == null ? 0f : horizontalProgress.fraction();
        float tileFraction = tileProgress == null ? 0f : tileProgress.fraction(host);
        diagnoseProgress(fraction, horizontalFraction, tileFraction);
        float nextReveal = StatusIconTransition.reveal(fraction, horizontalFraction, tileFraction);
        float nextTop = y + StatusIconTransition.travelPixels(pageTravel.fraction(horizontalFraction)
                + tileTravel.fraction(tileFraction),
                host.getResources().getDisplayMetrics().density);
        positionChanged = left != x || top != nextTop || reveal != nextReveal;
        reveal = nextReveal;
        left = x;
        top = nextTop;
        boolean suppressLeft = fraction > StatusIconTransition.CLOSING_LEFT_FRACTION;
        if (leftCopiesSuppressed != suppressLeft) {
            leftCopiesSuppressed = suppressLeft;
            invalidateNativeCopies();
        }
        layerBounds.set(left, top, left + width, top + height);
        measuredNodes = 0;
        Matrix matrix = matrixAt(0);
        matrix.setTranslate(left, top);
        includeDescendants(icons, matrix, 0);
        float outset = Math.max(2f, host.getResources().getDisplayMetrics().density * 4f);
        layerBounds.inset(-outset, -outset);
        if (!layerBounds.intersect(0f, 0f, host.getWidth(), host.getHeight()))
            return positionFailure("acquire destination outside shade bounds");
        layerBounds.roundOut(target);
        if (!slot.getBounds().equals(target)) { positionChanged = true; slot.setBounds(target); }
        return true;
    }

    /** Both shade pages retain the real Phone row's pre-expand right edge and center. */
    private boolean destination(View host, View icons, View phone, int width, int height) {
        if (phone.getWidth() <= 0 || phone.getHeight() <= 0) return positionFailure("acquire native anchor not measured");
        PhonePosition baseline = phonePosition;
        if (observedPhone.get() != phone) return positionFailure("acquire baseline source changed");
        if (baseline == null) return positionFailure("acquire baseline missing");
        if (!baseline.current(phone)) return positionFailure("acquire baseline window or display changed");
        host.getLocationOnScreen(screenPosition);
        destination[0] = StatusIconTransition.anchorX(baseline.x, baseline.width, screenPosition[0], width);
        destination[1] = StatusIconTransition.anchorY(baseline.y, baseline.height, screenPosition[1], height);
        return true;
    }

    private void requestRedraw(boolean changed) {
        if (!added) return;
        View icons = source.get();
        // A public native draw clears this source's dirty flags. Do not make a
        // steady preDraw request its own next frame, or poll a fully faded source
        // which cannot draw and clear those flags until the page reveals it again.
        if (changed || (Math.round(reveal * 255f) > 0 && icons != null && icons.isDirty()))
            slot.invalidateSelf();
    }

    private void suppressSources() {
        suppress(source.get()); suppress(anchor.get()); suppress(transitionCopy.get());
        View host = root.get();
        View companion = companionSource.get();
        if (companion != null) {
            if (host != null && companion.isAttachedToWindow() && companion.getRootView() == host
                    && companion.getWindowToken() == host.getWindowToken()) suppress(companion);
            else {
                AlphaState old = suppressed.remove(companion);
                if (old != null) old.restore(companion);
                companionSource.clear();
            }
        }
        for (Map.Entry<View, NativeCopy> entry : nativeCopies.entrySet()) {
            View copy = entry.getKey(); NativeCopy binding = entry.getValue();
            if (copy == null) continue;
            if (host == null || copy.getRootView() != host || insideSource(copy)) {
                AlphaState old = suppressed.remove(copy);
                if (old != null) old.restore(copy);
                continue;
            }
            View original = binding.source.get();
            boolean known = original != null;
            boolean right = original == source.get() || original == anchor.get()
                    || original == companionSource.get();
            boolean phoneLeft = known && original.getRootView() != host;
            boolean owned = StatusIconTransition.suppressCopy(added, false, binding.kind, true,
                    known, right, phoneLeft, fraction);
            if (owned && binding.kind != StatusIconTransition.RIGHT && leftCopyPolicy != null)
                owned = leftCopyPolicy.suppressNotificationCopy(copy, original, binding.kind)
                        && !leftCopyPolicy.nativeQsCopy(copy, original, binding.kind);
            if (owned) suppress(copy);
            else {
                AlphaState old = suppressed.remove(copy);
                if (old != null) old.restore(copy);
            }
        }
    }

    private boolean insideSource(View view) {
        View qs = source.get();
        for (int depth = 0; view != null && depth < 32; depth++) {
            if (view == qs) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    private void discoverNativeCopies() {
        if (copyInspector == null) return;
        discoveredNodes = 0;
        discoverNativeCopies(root.get(), 0);
    }

    private void discoverNativeCopies(View view, int depth) {
        if (view == null || depth > 32 || discoveredNodes++ >= 4096) return;
        int kind = copyInspector.kind(view);
        if (kind >= 0) try { onNativeCopyBound(view, copyInspector.copiedSource(view), kind); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) discoverNativeCopies(group.getChildAt(i), depth + 1);
    }

    private void suppress(View view) {
        if (view == null) return;
        AlphaState state = suppressed.get(view);
        if (state == null) { state = new AlphaState(view.getAlpha()); suppressed.put(view, state); }
        else if (view.getAlpha() != 0f) state.nativeAlpha = view.getAlpha();
        if (view.getAlpha() != 0f) view.setAlpha(0f);
    }

    private static final class AlphaState {
        float nativeAlpha;
        AlphaState(float alpha) { nativeAlpha = alpha; }
        void restore(View view) { if (view.getAlpha() == 0f) view.setAlpha(nativeAlpha); }
    }

    private Matrix matrixAt(int depth) {
        Matrix matrix = descendantMatrices[depth];
        if (matrix == null) descendantMatrices[depth] = matrix = new Matrix();
        return matrix;
    }

    private void includeDescendants(View view, Matrix matrix, int depth) {
        if (++measuredNodes > 256 || depth >= descendantMatrices.length - 1) return;
        nodeBounds.set(0f, 0f, view.getWidth(), view.getHeight());
        matrix.mapRect(nodeBounds);
        layerBounds.union(nodeBounds);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            Matrix childMatrix = matrixAt(depth + 1);
            childMatrix.set(matrix);
            childMatrix.preTranslate(child.getLeft() - group.getScrollX(), child.getTop() - group.getScrollY());
            if (!child.getMatrix().isIdentity()) childMatrix.preConcat(child.getMatrix());
            includeDescendants(child, childMatrix, depth + 1);
        }
    }

    private void unavailable(Throwable error) {
        View host = root.get(), icons = source.get();
        if (host == null && !added) return;
        if (error != null) {
            failedRoot = new WeakReference<>(host);
            failedSource = new WeakReference<>(icons);
            failedAnchor = new WeakReference<>(anchor.get());
        }
        release();
        if (error != null && warnings++ < 3) {
            try { ModuleDiagnostics.error("shade-icons", "Shade icon transition unavailable; native icons retained", error); }
            catch (Throwable ignored) { }
        }
        Runnable listener = failureListener;
        long failureGeneration = generation;
        if (host != null && listener != null) try {
            host.post(() -> {
                if (generation != failureGeneration || added || failureListener != listener) return;
                try { listener.run(); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    private void release() {
        View host = root.get(), icons = source.get(), phone = anchor.get(), copy = transitionCopy.get();
        boolean remove = added;
        generation++;
        added = false;
        // Copies recorded as empty while owned must draw native content again after release.
        if (remove) invalidateNativeCopies();
        leftCopiesSuppressed = false;
        horizontalMoving = false;
        IconSlot retired = slot;
        if (observer != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(preDraw); } catch (Throwable ignored) { }
            observer = null;
        }
        if (host != null) {
            try { host.removeOnAttachStateChangeListener(attachment); } catch (Throwable ignored) { }
            if (remove && busy) {
                slot = new IconSlot();
                try { host.post(() -> removeSlot(host, retired)); } catch (Throwable ignored) { }
            } else if (remove) removeSlot(host, retired);
        }
        for (View view : new View[]{icons, phone, copy}) if (view != null) try {
            view.removeOnAttachStateChangeListener(attachment);
        } catch (Throwable ignored) { }
        for (Map.Entry<View, AlphaState> entry : new ArrayList<>(suppressed.entrySet())) {
            if (entry.getKey() != null) try { entry.getValue().restore(entry.getKey()); } catch (Throwable ignored) { }
        }
        suppressed.clear();
        if (!remove || host == null) retired.setCallback(null);
        root.clear(); source.clear(); companionSource.clear(); anchor.clear(); transitionCopy.clear();
        fraction = reveal = 0f;
    }

    private void removeSlot(View host, IconSlot drawable) {
        try { host.getOverlay().remove(drawable); } catch (Throwable ignored) { }
        drawable.setCallback(null);
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    private final class IconSlot extends Drawable {
        @Override public void draw(Canvas canvas) {
            View icons = source.get();
            if (this != slot || !added || busy || icons == null) return;
            int alpha = Math.round(reveal * 255f);
            if (alpha <= 0) return;
            busy = true;
            try {
                int saved = alpha >= 255 ? canvas.save() : canvas.saveLayerAlpha(layerBounds, alpha);
                try {
                    canvas.translate(left, top);
                    // Drawing the native QS group retains its live network speed, battery,
                    // child tint and user offsets. No second Phone/QS group is composited.
                    drawingOwnedRow.set(Boolean.TRUE);
                    try { icons.draw(canvas); }
                    finally { drawingOwnedRow.remove(); }
                } finally { canvas.restoreToCount(saved); }
            } catch (Throwable error) { unavailable(error); }
            finally { busy = false; }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
