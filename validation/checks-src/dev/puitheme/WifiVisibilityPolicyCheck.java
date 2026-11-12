// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

/** Real slot request ordering, including hides during native transitions and label reuse. */
public final class WifiVisibilityPolicyCheck {
    private static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    private static int checks;
    private static void equal(Object expected, Object actual, String reason) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(reason + ": " + expected + " != " + actual);
    }
    private static void moduleWrite(WifiVisibilityPolicy slot, boolean hidden, boolean label) {
        int actual = slot.target(hidden, label);
        slot.beginModuleWrite();
        try { equal(actual, slot.requested(actual, hidden, label), "Module setter keeps its effective target"); }
        finally { slot.endModuleWrite(); }
    }
    public static void main(String[] args) {
        WifiVisibilityPolicy slot = new WifiVisibilityPolicy(VISIBLE);
        equal(GONE, slot.target(true, false), "Hiding suppresses the actual Wi-Fi slot");
        moduleWrite(slot, true, false);
        equal(VISIBLE, slot.target(false, false), "Own GONE never overwrites the native baseline");
        for (int nativeRequest : new int[]{INVISIBLE, GONE, VISIBLE}) {
            equal(GONE, slot.requested(nativeRequest, true, false), "Native update cannot leak while hidden");
            moduleWrite(slot, true, false);
            equal(nativeRequest, slot.target(false, false), "Unhide restores the latest native request");
        }
        for (int round = 0; round < 128; round++) {
            for (int nativeRequest : new int[]{VISIBLE, GONE, INVISIBLE, VISIBLE, INVISIBLE, GONE}) {
                slot.requested(nativeRequest, true, false);
                moduleWrite(slot, true, false);
                equal(nativeRequest, slot.target(false, false), "Repeated handover retains native visibility");
                moduleWrite(slot, false, false);
                equal(nativeRequest, slot.target(false, false), "Repeated restores are idempotent");
            }
        }
        slot.requested(GONE, true, false);
        equal(VISIBLE, slot.target(true, true), "Legacy cellular label keeps its borrowed Wi-Fi host");
        moduleWrite(slot, true, true);
        equal(GONE, slot.target(false, false), "Leaving cellular-label mode returns genuine native GONE");
        slot.requested(INVISIBLE, true, true);
        equal(INVISIBLE, slot.target(true, true), "Legacy label preserves native transition invisibility");
        slot.requested(VISIBLE, true, true);
        equal(GONE, slot.target(true, false), "Wi-Fi reconnect hides the real Wi-Fi without retaining label mode");
        slot.requested(INVISIBLE, true, false);
        slot.beginModuleWrite();
        try {
            slot.requested(GONE, true, false);
            slot.beginModuleWrite();
            try { slot.requested(VISIBLE, false, true); }
            finally { slot.endModuleWrite(); }
        } finally { slot.endModuleWrite(); }
        equal(INVISIBLE, slot.target(false, false), "Nested module writes never replace native requests");
        WifiVisibilityPolicy lock = new WifiVisibilityPolicy(GONE);
        equal(GONE, lock.target(false, false), "Rebuilt lockscreen host starts from its own native baseline");
        equal(INVISIBLE, slot.target(false, false), "New host cannot change an old host baseline");
        for (boolean nativeVisible : new boolean[]{false, true})
            for (boolean hidden : new boolean[]{false, true})
                for (boolean label : new boolean[]{false, true})
                    equal(label || nativeVisible && !hidden,
                            WifiVisibilityPolicy.iconVisible(nativeVisible, hidden, label),
                            "Layout visibility follows policy while native icon state stays independent");
        System.out.println("WifiVisibilityPolicyCheck passed: " + checks);
    }
}
