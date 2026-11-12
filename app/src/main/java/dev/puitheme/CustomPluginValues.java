// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * App289 (feature runtime.values): small values shared inside one process between the host and plugins,
 * for example the current media colour. A key is "owner/name"; the owner is "host" or a plugin id, and
 * only that owner can publish under it. Values are null, Boolean, Number or a short String.
 * A plugin's own values are removed when its scope ends, so nothing is left behind.
 */
final class CustomPluginValues {
    interface Listener { void changed(Object value); }
    static final String HOST = "host";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,40}");
    private static final Pattern OWNER = Pattern.compile("host|[A-Za-z0-9][A-Za-z0-9._-]{0,120}");
    private static final Map<String, Object> VALUES = new HashMap<>();
    private static final Map<String, List<Listener>> LISTENERS = new HashMap<>();
    private CustomPluginValues() { }

    static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }
    static boolean validKey(String key) {
        int slash = key == null ? -1 : key.lastIndexOf('/');
        return slash > 0 && OWNER.matcher(key.substring(0, slash)).matches() && validName(key.substring(slash + 1));
    }
    static Object accepted(Object value) {
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Shared value must be finite");
            return value instanceof Integer || value instanceof Long ? value : Double.valueOf(number);
        }
        if (value instanceof CharSequence && ((CharSequence) value).length() <= 256) return value.toString();
        throw new IllegalArgumentException("Shared value must be null, a boolean, a number or a short string");
    }

    /** Publishes or clears (null) one value; listeners run on the caller's thread and must not block. */
    static void publish(String owner, String name, Object value) {
        if (owner == null || !OWNER.matcher(owner).matches() || !validName(name)) throw new IllegalArgumentException("Invalid shared value name");
        Object next = accepted(value); String key = owner + "/" + name; List<Listener> notify;
        synchronized (VALUES) {
            Object previous = next == null ? VALUES.remove(key) : VALUES.put(key, next);
            if (previous == null ? next == null : previous.equals(next)) return;
            List<Listener> listeners = LISTENERS.get(key);
            notify = listeners == null ? null : new ArrayList<>(listeners);
        }
        if (notify != null) for (Listener listener : notify) listener.changed(next);
    }

    static Object get(String key) { synchronized (VALUES) { return VALUES.get(key); } }

    /** Returns the cancel action. */
    static Runnable observe(String key, Listener listener) {
        if (!validKey(key) || listener == null) throw new IllegalArgumentException("Invalid shared value key");
        synchronized (VALUES) { LISTENERS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listener); }
        return () -> {
            synchronized (VALUES) {
                List<Listener> listeners = LISTENERS.get(key);
                if (listeners != null && listeners.remove(listener) && listeners.isEmpty()) LISTENERS.remove(key);
            }
        };
    }

    /** A plugin scope ended: its published values disappear and observers see null. */
    static void clearOwner(String owner) {
        List<String> names = new ArrayList<>();
        synchronized (VALUES) { for (String key : VALUES.keySet()) if (key.startsWith(owner + "/")) names.add(key.substring(owner.length() + 1)); }
        for (String name : names) publish(owner, name, null);
    }

    /** Checks only. */
    static int listenerCount() { synchronized (VALUES) { int n = 0; for (List<Listener> l : LISTENERS.values()) n += l.size(); return n; } }
    static void resetForCheck() { synchronized (VALUES) { VALUES.clear(); LISTENERS.clear(); } }
}
