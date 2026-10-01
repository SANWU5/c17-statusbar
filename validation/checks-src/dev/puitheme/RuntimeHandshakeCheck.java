package dev.puitheme;

/** Pure request validation: no Android processes, broadcasts, preferences or saved reports. */
public final class RuntimeHandshakeCheck {
    private static int checks;
    private static void expect(boolean expected, boolean actual) {
        checks++;
        if (expected != actual) throw new AssertionError("Runtime handshake check " + checks);
    }
    private static boolean accept(ModuleRuntimeStatus.Challenge request, String nonce, int claimedPid,
            int callerPid, long uptime, long now, String build, int api, String framework, String version) {
        return ModuleRuntimeStatus.accepts(request, nonce, claimedPid, callerPid, uptime, now, 10010L,
                build, api, framework, version);
    }
    public static void main(String[] args) {
        String nonce = "0123456789abcdef0123456789abcdef";
        ModuleRuntimeStatus.Challenge request = new ModuleRuntimeStatus.Challenge(nonce, 1000L, 10000L);
        String build = ModuleRuntimeStatus.BUILD_TOKEN;
        expect(true, ModuleRuntimeStatus.allowedReporter(1000, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(10200, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(10300, 10200, 1000));
        expect(false, ModuleRuntimeStatus.allowedReporter(1000, 1000, 1000));
        expect(true, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, "ffffffffffffffffffffffffffffffff", 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 322, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 0, 0, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 9999, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10011, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 999, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 3500, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 9000, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, "older-build", 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 100, "LSPosed", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "", "2.2.0"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "OtherFramework", "2.2.0"));
        expect(true, accept(request, nonce, 321, 321, 10005, 1200, build, 102, "lSpOsEd Modern", "2.2.0-it"));
        expect(false, accept(request, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", null));
        expect(false, accept(null, nonce, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        expect(false, accept(request, null, 321, 321, 10005, 1200, build, 101, "LSPosed", "2.2.0"));
        ModuleRuntimeStatus.ProbeState replied = new ModuleRuntimeStatus.ProbeState();
        expect(true, replied.open());
        expect(true, replied.beginQuery());
        expect(false, replied.beginQuery()); // Duplicate notifications cannot query twice.
        expect(true, replied.finish());
        expect(false, replied.open());
        expect(false, replied.finish()); // Success, timeout and cancel clean up only once.
        expect(false, replied.beginQuery());
        ModuleRuntimeStatus.ProbeState cancelled = new ModuleRuntimeStatus.ProbeState();
        expect(true, cancelled.finish());
        expect(false, cancelled.beginQuery()); // A late response cannot revive a cancelled probe.
        expect(false, cancelled.open());
        System.out.println("Runtime handshake checks passed: " + checks);
    }
}
