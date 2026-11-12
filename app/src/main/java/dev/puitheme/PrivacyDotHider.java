// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme;

import android.os.Looper;
import android.view.View;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hides ColorOS's privacy dot by keeping its two corner containers fully transparent. The controller's own state
 * machine, windows and animations keep running, so turning the option off simply makes the dot visible again.
 * Only the containers' alpha is touched; the controller animates the dot image inside them.
 */
final class PrivacyDotHider {
    static final String CONTROLLER = "com.oplus.systemui.privacy.OplusPrivacyDotViewControllerImpl";
    private static final String[] CORNERS = {"tl", "tr"};
    private final Set<View> containers = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private volatile boolean hidden;

    /** The controller (re)created a corner container. */
    void created(Object result) {
        if (result instanceof View) remember((View) result);
    }

    /** Any state change of the controller: pick up containers built before this feature was switched on. */
    void observed(Object controller) {
        if (controller == null) return;
        for (String name : CORNERS) {
            try {
                Field field = controller.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value instanceof View) remember((View) value);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }

    void configure(boolean hide) {
        if (hidden == hide) return;
        hidden = hide;
        List<View> known;
        synchronized (containers) { known = new ArrayList<>(containers); }
        for (View view : known) apply(view);
    }

    boolean hidden() { return hidden; }

    private void remember(View view) {
        boolean added;
        synchronized (containers) { added = containers.add(view); }
        if (added || hidden) apply(view);
    }

    private void apply(View view) {
        Runnable update = () -> view.setAlpha(hidden ? 0f : 1f);
        // A container not attached yet is still owned by the thread that is building it.
        android.os.Handler handler = view.getHandler();
        if (handler == null || Looper.myLooper() == handler.getLooper()) update.run();
        else handler.post(update);
    }
}
