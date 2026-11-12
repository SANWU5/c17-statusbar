// SPDX-License-Identifier: GPL-3.0-only
#include "sequence.h"
#include "capabilities.h"
static c17rescue::Sequence sequence;
static c17rescue::ProbeCapabilities probe;
extern "C" {
__attribute__((visibility("default"))) void reset() { sequence = c17rescue::Sequence{}; }
__attribute__((visibility("default"))) int input(int device, int key, int value, unsigned long long at) {
    return sequence.input(device, key, value, at) ? 1 : 0;
}
__attribute__((visibility("default"))) void dropped(int device) { sequence.dropped(device); }
__attribute__((visibility("default"))) int stage() { return sequence.stage; }
__attribute__((visibility("default"))) void probeReset() { probe = c17rescue::ProbeCapabilities{}; }
__attribute__((visibility("default"))) void probeObserve(int readable,int clock,int keys) { probe.observe(readable!=0,clock!=0,unsigned(keys)); }
__attribute__((visibility("default"))) unsigned probeReadable() { return probe.readable; }
__attribute__((visibility("default"))) unsigned probeUsable() { return probe.usable; }
__attribute__((visibility("default"))) unsigned probeCoverage() { return probe.coverage; }
}
