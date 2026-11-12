#!/usr/bin/env python3
"""Run the production service lifecycle scripts; only the Android daemon/PM are substitutes."""
import importlib.util
import os
from pathlib import Path
import tempfile
import time

spec = importlib.util.spec_from_file_location("checks", Path(__file__).with_name("performance-module-check.py"))
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)
bash = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else "/bin/bash"
with tempfile.TemporaryDirectory(prefix="c17-cache-service-") as root:
    f = c.Fixture(Path(root), bash, "lifecycle")
    def run(mode, **flags):
        return f.run(script="cache-service.sh", mode=mode, **flags)
    c.equal(0, run("status").returncode, "absent service status is readable")
    c.equal(False, f.data.exists(), "status query creates no files or directories")
    c.equal(0, run("start").returncode, "service can start without ART readiness")
    time.sleep(.15)
    c.contains(run("status").stdout, "cache_running=1", "live status comes from service")
    clients = (f.data / "cache-clients").read_text()
    c.contains(clients, "10160 com.android.systemui", "SystemUI identity comes from PM")
    c.contains(clients, "10120 com.android.launcher", "launcher identity comes from PM")
    c.equal(False, "com.other" in clients, "unrelated app is never authorized")
    (f.module / "disable").touch()
    c.equal(27, run("start").returncode, "disabled module cannot start")
    c.equal(0, run("stop").returncode, "disabled module can still stop")
    c.contains(run("status").stdout, "cache_running=0", "stop removes live state")
    (f.module / "disable").unlink()
    c.equal(20, run("start", C17_FIXTURE_PERMISSION="777").returncode, "writable module is rejected")
    cache = f.data / "cache-v1"
    cache.mkdir()
    (cache / ("10160-" + "a" * 64 + ".bin")).write_bytes(b"cached")
    c.equal(0, run("purge").returncode, "bounded uninstall clears own immediate resources")
    c.equal(False, cache.exists(), "cache directory removed")
    cache.mkdir()
    (cache / "foreign-directory").mkdir()
    c.equal(20, run("purge").returncode, "unexpected nested directory never recursively deleted")
    c.equal(True, (cache / "foreign-directory").exists(), "unexpected content retained")
    cmd = f.bin / "cmd"
    original = cmd.read_text()
    cmd.write_text(original.replace("package:com.android.systemui uid:10160", "package:com.android.systemui uid:not-a-number"))
    c.equal(26, run("start").returncode, "invalid PM identity blocks publication")
    c.equal(clients, (f.data / "cache-clients").read_text(), "failed identity refresh preserves previous complete file")
    c.equal([], list(f.data.glob(".clients.*")), "failed preparation leaves no temporary files")
    inherited = c.Fixture(Path(root), bash, "art-lock-inheritance")
    c.equal(0, inherited.run(mode="run", C17_FIXTURE_ART="speed").returncode, "ART may start the cache while holding its task guard")
    c.equal("closed", Path(str(inherited.log) + ".cache-fd9").read_text().strip(), "daemon must never retain the ART lock descriptor")
    boot = c.Fixture(Path(root), bash, "cache-before-package-manager")
    prop = boot.bin / "getprop"
    prop.write_text(prop.read_text().replace("then printf '1\\n'", "then touch \"$C17_FIXTURE_LOG.pm-ready\"; printf '1\\n'"))
    pm = boot.bin / "cmd"
    pm.write_text(pm.read_text().replace('  "package list packages -U")',
        '  "package list packages -U")\n    [ -f "$C17_FIXTURE_LOG.pm-ready" ] || exit 1'))
    c.equal(0, boot.run(script="service.sh", mode=None, C17_FIXTURE_BOOT_AFTER="2", C17_FIXTURE_ART="speed").returncode,
            "boot waits for package manager readiness")
    time.sleep(.15)
    c.contains(boot.run(script="cache-service.sh", mode="status").stdout, "cache_running=1",
               "resident cache starts after early boot package query would fail")
    c.equal("current", boot.status()["state"], "valid ART still reused after boot")
print("Cache service lifecycle:", c.CHECKS, "checks passed (native socket/SELinux require Android)")
