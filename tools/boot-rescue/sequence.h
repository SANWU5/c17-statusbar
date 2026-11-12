// SPDX-License-Identifier: GPL-3.0-only
#pragma once

// Shared verbatim by the Android reader and the freestanding host-test WebAssembly.
// No device access, clock access, file access or reset operation is possible here.
//
// The command (companion 2.3): five short presses of Volume Up within five seconds, during the boot animation.
// It deliberately avoids every key the system itself acts on while it boots: Power (screen off, power menu),
// Volume Down (Android enters safe mode when it is held while the system starts) and both volume keys together
// (the accessibility shortcut). Any of those, or any other key, pressed in between cancels the command.
namespace c17rescue {
using Tick = unsigned long long;
class Sequence {
public:
    static constexpr int kDevices = 32, kPresses = 5;
    static constexpr Tick kWindowMs = 5000, kMirrorMs = 40;
    static constexpr int kVolumeUp = 1;
    int stage = 0;  // Volume Up presses counted so far; the command fires when the last one is released.
    bool fired = false;
    unsigned held[kDevices] = {};
    unsigned counts[3] = {};
    Tick first = 0, last = 0, lastDown[3] = {};
    int lastDevice[3] = {-1, -1, -1};

    void sequenceReset() { stage = 0; first = 0; last = 0; }
    void dropped(int device) {
        if (device < 0 || device >= kDevices) return;
        for (int key = 0; key < 3; ++key)
            if (held[device] & (1U << key)) { if (counts[key]) --counts[key]; }
        held[device] = 0;
        sequenceReset();
    }
    // Power, Volume Up and Volume Down are tracked: the two keys that are not the command still cancel it.
    static int keyIndex(int code) { return code == 116 ? 0 : code == 115 ? 1 : code == 114 ? 2 : -1; }
    bool input(int device, int code, int value, Tick now) {
        if (fired || device < 0 || device >= kDevices) return false;
        if (stage && (now < last || now < first || now - first > kWindowMs)) sequenceReset();
        int key = keyIndex(code);
        if (key < 0) { if (value == 1) sequenceReset(); return false; }
        if (value == 2) return false; // Kernel autorepeat never counts as another press.
        unsigned bit = 1U << key;
        if (value == 0) {
            if (!(held[device] & bit)) return false;
            held[device] &= ~bit;
            if (counts[key]) --counts[key];
            if (!counts[key] && key == kVolumeUp && stage == kPresses) { fired = true; return true; }
            return false;
        }
        if (value != 1 || (held[device] & bit)) return false;
        held[device] |= bit;
        bool mirrorHeld = counts[key] != 0;
        ++counts[key];
        // Two different keys down at once (the accessibility shortcut among them) is never the command.
        for (int other = 0; other < 3; ++other) if (other != key && counts[other]) { sequenceReset(); return false; }
        if (mirrorHeld) return false;
        bool mirrorLate = lastDevice[key] >= 0 && lastDevice[key] != device
                && now >= lastDown[key] && now - lastDown[key] <= kMirrorMs;
        if (mirrorLate) return false;
        lastDevice[key] = device; lastDown[key] = now;
        if (key != kVolumeUp) { sequenceReset(); return false; }
        if (!stage) first = now;
        if (stage < kPresses) ++stage;
        last = now;
        return false;
    }
};
}
