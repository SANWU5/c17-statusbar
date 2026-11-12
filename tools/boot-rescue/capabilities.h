// SPDX-License-Identifier: GPL-3.0-only
#pragma once
namespace c17rescue {
// Only devices whose event timestamps can use the watcher's monotonic clock
// contribute to usable coverage. Bits: power=1, volume up=2, volume down=4.
struct ProbeCapabilities {
    unsigned readable = 0, usable = 0, coverage = 0;
    void observe(bool keyDevice, bool clockAccepted, unsigned keys) {
        if (!keyDevice) return;
        ++readable;
        if (!clockAccepted) return;
        ++usable;
        coverage |= keys & 7U;
    }
};
}
