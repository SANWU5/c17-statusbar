#!/usr/bin/env python3
"""Run the real performance-module scripts against an isolated root substitute, never a device.

Only Android identity/package-manager commands are fixtures (id, getprop, stat %u, cmd, dumpsys, sleep).
Production scripts accept no path override: the fixture rewrites their fixed /data/adb paths into a
temporary directory, exactly as the generated app commands are rewritten.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets/performance-module"
CHECKS = 0


def equal(expected, actual, label):
    global CHECKS
    CHECKS += 1
    if expected != actual:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def contains(text, part, label):
    equal(True, part in text, f"{label} ({part!r} in {text!r})")


class Fixture:
    def __init__(self, base, bash, name):
        self.bash = bash
        self.path = base / name
        self.path.mkdir()
        self.posix = subprocess.check_output([bash, "-c", 'cygpath -u "$1"', "fixture", str(self.path)], text=True, encoding="utf-8").strip() if os.name == "nt" else str(self.path)
        self.module = self.path / "data/adb/modules/c17_performance"
        self.data = self.path / "data/adb/c17-performance"
        self.boot_id = self.path / "proc/sys/kernel/random/boot_id"
        self.boot_id.parent.mkdir(parents=True)
        self.boot_id.write_text("00000000-0000-4000-8000-000000000107\n", encoding="utf-8")
        self.module.mkdir(parents=True)
        for source in ASSETS.iterdir():
            if source.is_file():
                text = source.read_text(encoding="utf-8")
                (self.module / source.name).write_text(self.rewrite(text), encoding="utf-8", newline="\n")
        self.log = self.path / "commands.log"
        self.bin = self.path / "bin"
        self.bin.mkdir()
        commands = {
            "am": "printf 'am %s\\n' \"$*\" >> \"$C17_FIXTURE_LOG\"\nexit ${C17_FIXTURE_AM_FAIL:-0}\n",
            "content": "printf 'content %s\\n' \"$*\" >> \"$C17_FIXTURE_LOG\"\nprintf 'Result: Bundle[]\\n'\n",
            "id": "printf '0\\n'\n",
            "getprop": '''case "$1" in
  ro.build.fingerprint) printf '%s\\n' "${C17_FIXTURE_BUILD:-oplus/PJZ110/17.0.1}";;
  ro.product.cpu.abi) printf 'arm64-v8a\\n';;
  sys.boot_completed)
    if [ -n "$C17_FIXTURE_BOOT_AFTER" ]; then
      count=$(cat "$C17_FIXTURE_LOG.boot-count" 2>/dev/null || printf '0')
      count=$((count + 1)); printf '%s\\n' "$count" > "$C17_FIXTURE_LOG.boot-count"
      if [ "$count" -ge "$C17_FIXTURE_BOOT_AFTER" ]; then printf '1\\n'; else printf '0\\n'; fi
    else printf '%s\\n' "${C17_FIXTURE_BOOTED:-1}"; fi;;
  *) printf '\\n';;
esac
''',
            "stat": '''case "$2" in
  %u) printf '0\\n';;
  %a) if [ -n "$C17_FIXTURE_PERMISSION" ]; then printf '%s\\n' "$C17_FIXTURE_PERMISSION"; else exec /usr/bin/stat "$@"; fi;;
  *) exec /usr/bin/stat "$@";;
esac
''',
            "sleep": "printf 'sleep %s\\n' \"$*\" >> \"$C17_FIXTURE_LOG\"\nexec /usr/bin/sleep 0.02\n",
            "dumpsys": '''printf 'dumpsys %s\\n' "$*" >> "$C17_FIXTURE_LOG"
case "$1" in
  battery)
    [ "$C17_FIXTURE_POWER_UNKNOWN" != 1 ] || exit 1
    printf '  USB powered: %s\\n  level: %s\\n  temperature: %s\\n' "${C17_FIXTURE_CHARGING:-true}" "${C17_FIXTURE_LEVEL:-80}" "${C17_FIXTURE_TEMPERATURE:-300}"; exit;;
  thermalservice)
    [ "$C17_FIXTURE_THERMAL_UNKNOWN" != 1 ] || exit 1
    printf 'Thermal Status: %s\\n' "${C17_FIXTURE_THERMAL:-0}"; exit;;
  power)
    [ "$C17_FIXTURE_SCREEN_UNKNOWN" != 1 ] || exit 1
    printf 'mInteractive=%s\\nmWakefulness=%s\\n' "${C17_FIXTURE_INTERACTIVE:-false}" "${C17_FIXTURE_WAKEFULNESS:-Asleep}"
    if [ "$C17_FIXTURE_IDLE_UNSUPPORTED" != 1 ]; then
      printf '  lastUserActivityTime=1234 (%s ms ago)\\n  lastUserActivityTimeNoChangeLights=1234 (%s ms ago)\\n' "${C17_FIXTURE_IDLE_AGE:-0}" "${C17_FIXTURE_LIGHT_AGE:-${C17_FIXTURE_IDLE_AGE:-0}}"
    fi
    exit;;
esac
if [ "$2" = dev.puitheme.iosstatusbar ]; then version=${C17_FIXTURE_MODULE_VERSION:-106}; else version=${C17_FIXTURE_VERSION:-170001}; fi
printf '    versionCode=%s minSdk=35 targetSdk=36\\n    lastUpdateTime=2026-10-01 10:00:00\\n' "$version"
''',
            "cmd": '''printf '%s\\n' "$*" >> "$C17_FIXTURE_LOG"
case "$*" in
  "package help")
    [ "$C17_FIXTURE_NO_CANCEL" != 1 ] || { printf 'legacy compile help\\n'; exit; }
    printf '  -v Verbose mode\\n  --primary-dex Primary only\\n  cancel JOB_ID\\n'
    [ "$C17_FIXTURE_NO_PRIORITY" = 1 ] || printf 'PRIORITY_BACKGROUND\\n';;
  "package list packages -f")
    printf 'package:/system_ext/priv-app/SystemUI/SystemUI.apk=com.android.systemui\\n'
    printf 'package:/system_ext/app/SystemUIPlugin/SystemUIPlugin.apk=com.oplus.systemui.plugins\\n'
    printf 'package:/system_ext/app/SystemUIPlugin/evil.apk=com.bad;touch${IFS}/tmp/x\\n'
    printf 'package:/data/app/~~x/dev.puitheme.iosstatusbar-y/base.apk=dev.puitheme.iosstatusbar\\n'
    printf 'package:/system/app/Other/Other.apk=com.example.other\\n';;
  "package list packages -U")
    printf 'package:com.android.systemui uid:10160\\npackage:com.android.launcher uid:10120\\npackage:dev.puitheme.iosstatusbar uid:10400\\npackage:com.other uid:10500\\n';;
  "package path "*) printf 'package:/system_ext/x/%s.apk\\n' "$3";;
  "package art dump "*)
    package=$4
    case "$C17_FIXTURE_ART" in
      unknown) printf 'OEM unknown format\\n'; exit;;
      false_ready) printf '[foreign.package]\\n path: /x.apk\\n arm64: [status=speed] [reason=cmdline] [primary-abi]\\n'; exit;;
    esac
    state=$(cat "$C17_FIXTURE_LOG.art.$package" 2>/dev/null || printf 'verify')
    if [ -f "$C17_FIXTURE_LOG.identity.$package" ] && [ "$(cat "$C17_FIXTURE_LOG.identity.$package")" != "${C17_FIXTURE_BUILD:-oplus/PJZ110/17.0.1}:${C17_FIXTURE_VERSION:-170001}" ]; then state=verify; fi
    [ -z "$C17_FIXTURE_ART" ] || state=$C17_FIXTURE_ART
    reason=cmdline; [ "$state" != vdex ] || { state=speed; reason=vdex; }
    printf '[%s]\\n  path: /system_ext/x/%s.apk\\n    arm64: [status=%s] [reason=%s] [primary-abi]\\n      [location is /data/dalvik-cache]\\n' "$package" "$package" "$state" "$reason"
    if [ "$C17_FIXTURE_PARTIAL_DUMP" = 1 ]; then printf ' path: /missing.apk\\n'; fi;;
  "package art cancel "*)
    [ "$C17_FIXTURE_CANCEL_FAIL" != 1 ] || { printf 'Failure: cancellation\\n'; exit 1; }
    if [ -e "$C17_FIXTURE_LOG.cancelled" ]; then printf 'Job not found\\n'; exit 1; fi
    touch "$C17_FIXTURE_LOG.cancelled"
    printf 'Job cancelled\\n';;
  "package compile "*)
    if [ "$C17_FIXTURE_REJECT_PRIVATE_STDOUT" = 1 ] && [ -f /dev/stdout ]; then
      printf 'cmd: Failure calling service package: Failed transaction (2147483646)\\n'; exit 2
    fi
    if [ "$C17_FIXTURE_CHECK_FD" = 1 ]; then
      if [ -e "/proc/$$/fd/9" ]; then printf 'inherited\\n'; else printf 'closed\\n'; fi >> "$C17_FIXTURE_LOG.fd9"
    fi
    for package; do :; done
    case "$*" in *' -f --primary-dex '*) restoring=1;; *) restoring=0;; esac
    if [ "$restoring" = 1 ]; then
      case "${C17_FIXTURE_RESTORE:-ok}" in
        fail) printf 'Final Status: FAILED\\n'; exit 1;;
        false_success) printf 'Failure: old Success\\n'; exit 1;;
        success_exit_failure) printf 'Final Status: PERFORMED\\n'; exit 1;;
      esac
      rm -f "$C17_FIXTURE_LOG.art.$package" "$C17_FIXTURE_LOG.identity.$package"
      printf 'Final Status: PERFORMED\\n'; exit
    fi
    if [ "$package" = com.oplus.systemui.plugins ] && [ "$C17_FIXTURE_PLUGIN_FAIL" = 1 ]; then printf 'Final Status: FAILED\\n'; exit 1; fi
    if [ -n "$C17_FIXTURE_TRANSIENT" ]; then
      n=$(cat "$C17_FIXTURE_LOG.transient" 2>/dev/null || printf 0)
      if [ "$n" -lt "$C17_FIXTURE_TRANSIENT" ]; then
        printf '%s' $((n + 1)) > "$C17_FIXTURE_LOG.transient"
        printf 'cmd: Failure calling service package: Failed transaction (2147483646)\\n'; exit 1
      fi
    fi
    case "${C17_FIXTURE_COMPILE:-ok}" in
      fail) printf 'Final Status: FAILED\\n'; exit 1;;
      false_success) printf 'Failure: previous Success was not current\\n'; exit 1;;
      success_exit_failure) printf 'Final Status: PERFORMED\\n'; exit 1;;
      hang)
        rm -f "$C17_FIXTURE_LOG.cancelled"
        printf "Job running. To cancel it, run 'pm art cancel 12345678-1234-4234-8234-123456789abc' in a separate shell.\\n"
        while [ ! -f "$C17_FIXTURE_LOG.cancelled" ]; do /usr/bin/sleep 0.03; done
        printf 'Final Status: CANCELLED\\n'; exit 1;;
      *)
        printf 'speed\\n' > "$C17_FIXTURE_LOG.art.$package"
        printf '%s\\n' "${C17_FIXTURE_BUILD:-oplus/PJZ110/17.0.1}:${C17_FIXTURE_VERSION:-170001}" > "$C17_FIXTURE_LOG.identity.$package"
        printf 'DexContainerFileDexoptResult{dexContainerFile=/x.apk, primaryAbi=true, abi=arm64-v8a, actualCompilerFilter=speed, status=PERFORMED, dex2oatWallTimeMillis=1500, dex2oatCpuTimeMillis=900, sizeBytes=3145728, sizeBeforeBytes=1048576, extendedStatusFlags=[]}\\n'
        printf 'Final Status: PERFORMED\\n';;
    esac;;
  *) printf 'unknown\\n'; exit 1;;
esac
''',
        }
        # Git Bash ships Perl's real OS flock, but no flock executable. Duplicate the
        # inherited descriptor just like toybox/util-linux; never fake lock success.
        commands["flock"] = '''[ "$C17_FIXTURE_NO_FLOCK" != 1 ] || exit 2
exec /usr/bin/perl -MFcntl=:flock -e '
my ($shared,$unlock,$nonblock,$fd)=(0,0,0,undef);
for (@ARGV) { if ($_ eq "-s") {$shared=1} elsif ($_ eq "-u") {$unlock=1} elsif ($_ eq "-n") {$nonblock=1} elsif (/^[0-9]+$/) {$fd=$_} else {exit 2} }
defined($fd) or exit 2; open(my $h, "<&=$fd") or exit 2;
my $mode=$unlock ? LOCK_UN : ($shared ? LOCK_SH : LOCK_EX); $mode |= LOCK_NB if $nonblock;
exit(flock($h,$mode) ? 0 : 1);
' -- "$@"
''' if os.name == "nt" else '''[ "$C17_FIXTURE_NO_FLOCK" != 1 ] || exit 2
exec /usr/bin/flock "$@"
'''
        for name, text in commands.items():
            file = self.bin / name
            file.write_text("#!/bin/sh\n" + text, encoding="utf-8", newline="\n")
            file.chmod(0o755)
        native = self.module / "bin/c17cached-arm64-v8a"
        native.parent.mkdir()
        native.write_text('''#!/bin/sh
case "$1" in
  --serve) if [ -e /proc/$$/fd/9 ]; then echo inherited; else echo closed; fi > "$C17_FIXTURE_LOG.cache-fd9"; touch "$C17_FIXTURE_LOG.cache-running";;
  --stop) rm -f "$C17_FIXTURE_LOG.cache-running"; printf 'OK\\n';;
  --status) if [ -f "$C17_FIXTURE_LOG.cache-running" ]; then printf 'cache_running=1\\ncache_protocol=1\\n'; else printf 'cache_running=0\\n'; exit 1; fi;;
esac
''', encoding="utf-8", newline="\n")
        native.chmod(0o755)
        subprocess.run([bash, "-c", 'chmod 755 "$1"/*.sh "$2"/*; chmod 644 "$1/module.prop"; chmod 700 "$1"', "fixture",
                        self.posix + "/data/adb/modules/c17_performance", self.posix + "/bin"], check=True)

    def rewrite(self, text):
        return text.replace("/data/adb", self.posix + "/data/adb").replace("/data/local/tmp", self.posix + "/data/local/tmp").replace(
            "/proc/sys/kernel/random/boot_id", self.posix + "/proc/sys/kernel/random/boot_id").replace("OPERATION_SECONDS=180", "OPERATION_SECONDS=2")

    def env(self, **flags):
        env = os.environ.copy()
        env.update({"PATH": self.posix + "/bin:/usr/bin:/bin", "C17_FIXTURE_LOG": self.posix + "/commands.log", "MSYS": "winsymlinks:nativestrict"})
        env.update(flags)
        return env

    def run(self, script="common.sh", mode="boot", **flags):
        command = [self.bash, "-c", 'export PATH="$1/bin:/usr/bin:/bin"; shift; exec bash "$@"', "fixture", self.posix,
                   self.posix + "/data/adb/modules/c17_performance/" + script]
        if mode:
            command.append(mode)
        # The unchanged 300 boot probes take >60s on MSYS (measured 62.469s, and
        # slower under concurrent JVM work); only the host process budget differs.
        return subprocess.run(command, env=self.env(**flags), text=True, encoding="utf-8", capture_output=True, timeout=180 if os.name == "nt" else 60)

    def commands(self):
        return self.log.read_text(encoding="utf-8").splitlines() if self.log.exists() else []

    def status(self):
        values = {}
        for line in (self.data / "status").read_text(encoding="utf-8").splitlines():
            key, _, value = line.partition("=")
            values[key] = value
        return values

    def wait_complete(self, expected=None, mode=None):
        # MSYS process creation is slower under concurrent JVM checks; keep the
        # detached-worker test bounded without mistaking a completed job for failure.
        deadline = time.monotonic() + (60 if os.name == "nt" else 15)
        while time.monotonic() < deadline:
            try:
                values = self.status()
                if values.get("state") in ("ok", "current", "deferred", "failed") and (expected is None or values.get("state") == expected) and (mode is None or values.get("mode") == mode):
                    return values
            except FileNotFoundError:
                pass
            time.sleep(0.05)
        raise AssertionError("detached root script did not finish its fixture work")


def compiles(lines):
    return [line.split()[-1] for line in lines if line.startswith("package compile ") and "-m speed " in line]


def ledger_of(fixture):
    values = {}
    for line in (fixture.data / "ledger").read_text(encoding="utf-8").splitlines():
        key, _, value = line.partition("=")
        values[key] = value
    return values


def scripts(base, bash):
    reuse_without_compiler(base, bash)
    f = Fixture(base, bash, "boot")
    result = f.run("service.sh", None)
    equal(0, result.returncode, "first boot exit " + result.stdout + result.stderr)
    equal(["com.android.systemui", "com.oplus.systemui.plugins"], compiles(f.commands()), "first boot compiles only SystemUI/plugin")
    status = f.status()
    for key, value in dict(state="ok", compiled="2", reused="0", packages="2", verified="2", deferred="0", verification="verified", priority="background", module_version_code="14", error="none", mode="boot").items():
        equal(value, status[key], "first boot " + key)
    equal("00000000-0000-4000-8000-000000000107", status["boot"], "current boot")
    for package in compiles(f.commands()):
        equal(True, (f.data / ("package." + package)).is_file(), "per-package success recorded")
        equal(True, (f.data / ("managed." + package)).is_file(), "only attempted target marked for restore")
    prop = (f.module / "module.prop").read_text(encoding="utf-8")
    contains(prop, "name=Status C17 附属模块", "module rename")
    contains(prop, "version=2.3\nversionCode=14\n", "root version")
    contains(prop, "description=已就绪\n", "concise ready state")
    equal(True, (f.data / "status").read_bytes().endswith(b"\n"), "status final LF")
    equal(False, any("com.bad" in line or "com.example.other" in line for line in f.commands() if "compile" in line), "only trusted native targets")
    equal(True, all("-p PRIORITY_BACKGROUND --primary-dex" in line for line in f.commands() if "-m speed " in line), "ART receives background priority and narrow scope")

    f.log.unlink()
    result = f.run("service.sh", None)
    equal(0, result.returncode, "unchanged boot exit")
    equal([], compiles(f.commands()), "valid ART is reused")
    for key, value in dict(state="current", compiled="0", reused="2", verified="2").items(): equal(value, f.status()[key], "unchanged " + key)
    equal(True, any("package art dump" in line for line in f.commands()), "unchanged version still checks real ART")

    equal(False, any("dev.puitheme.iosstatusbar" in line for line in f.commands() if "compile" in line), "this app is installed but never compiled")
    ledger = ledger_of(f)
    for key, value in dict(compiles="2", wall_ms="3000", cpu_ms="1800", size_kb="4096", reuses="2", avoided_ms="3000").items():
        equal(value, ledger.get(key), "benefit ledger " + key)
    equal(True, int(ledger["since"]) > 0, "benefit ledger start time")
    equal("1500", (f.data / "cost.com.android.systemui").read_text().strip(), "per-package compile cost kept for the estimate")

    transient = Fixture(base, bash, "transient-failure")
    result = transient.run(mode="run", C17_FIXTURE_TRANSIENT="2")
    equal("ok", transient.status()["state"], "a package manager that answers late is retried")
    equal("2", transient.status()["compiled"], "retried compile counted once")
    equal(4, len(compiles(transient.commands())), "two failed attempts then success, then the neighbour")
    equal(False, (transient.data / "active-job").exists(), "a call that never started a job leaves no marker")
    lost = Fixture(base, bash, "transient-lockout")
    result = lost.run(mode="run", C17_FIXTURE_TRANSIENT="99")
    equal("failed", lost.status()["state"], "persistent package manager failure is reported")
    equal(False, (lost.data / "active-job").exists(), "failure without a job id cannot lock later tasks")
    lost.log.unlink()
    result = lost.run(mode="run")
    equal("ok", lost.status()["state"], "next run is not locked out")

    stale = Fixture(base, bash, "expired-unknown-marker")
    stale.data.mkdir(parents=True)
    marker = stale.data / "active-job"
    marker.write_text(stale.boot_id.read_text(encoding="utf-8") + "unknown\n", encoding="utf-8")
    old = time.time() - 20 * 60
    os.utime(marker, (old, old))
    result = stale.run("webui.sh", "status")
    equal(True, "active_job=0" in result.stdout, "expired unknown marker no longer reads as a task in flight")
    result = stale.run(mode="run")
    equal("ok", stale.status()["state"], "expired unknown marker no longer blocks work")
    equal(2, len(compiles(stale.commands())), "work resumes after the marker expires")
    equal(False, marker.exists(), "expired marker removed")
    fresh = Fixture(base, bash, "fresh-unknown-marker")
    fresh.data.mkdir(parents=True)
    fresh_marker = fresh.data / "active-job"
    fresh_marker.write_text(fresh.boot_id.read_text(encoding="utf-8") + "unknown\n", encoding="utf-8")
    result = fresh.run("webui.sh", "status")
    equal(True, "active_job=unknown" in result.stdout, "recent unknown marker still reads as unconfirmed")
    result = fresh.run(mode="run")
    equal("compile_cancel_failed", fresh.status()["error"], "recent unknown marker still blocks")
    equal([], compiles(fresh.commands()), "no overlap with a recent unconfirmed task")
    f.log.unlink()
    result = f.run("service.sh", None, C17_FIXTURE_MODULE_VERSION="137")
    equal([], compiles(f.commands()), "C17 callback APK update does not invalidate unchanged native ART")
    f.log.unlink()
    result = f.run(C17_FIXTURE_BUILD="oplus/PJZ110/17.0.2")
    equal(2, len(compiles(f.commands())), "OTA dependency mismatch recompiled")
    f.log.unlink()
    result = f.run(C17_FIXTURE_BUILD="oplus/PJZ110/17.0.2", C17_FIXTURE_VERSION="170002")
    equal(2, len(compiles(f.commands())), "native APK update recompiled")

    partial = Fixture(base, bash, "partial")
    result = partial.run(C17_FIXTURE_PLUGIN_FAIL="1")
    equal(True, result.returncode != 0, "one failed component is failure")
    equal("compile_failed", partial.status()["error"], "first failure survives later operations")
    equal("1", partial.status()["compiled"], "partial success remains recorded")
    equal(True, (partial.data / "package.com.android.systemui").exists(), "successful neighbour retains cache")
    equal(False, (partial.data / "package.com.oplus.systemui.plugins").exists(), "failure never cached")
    partial.log.unlink()
    result = partial.run()
    equal(0, result.returncode, "partial retry exit")
    equal(["com.oplus.systemui.plugins"], compiles(partial.commands()), "retry only the failed component")
    equal("1", partial.status()["reused"], "successful neighbour reused")

    f.log.unlink()
    result = f.run("common.sh", "run", C17_FIXTURE_BUILD="oplus/PJZ110/17.0.2", C17_FIXTURE_VERSION="170002", C17_FIXTURE_INTERACTIVE="true", C17_FIXTURE_WAKEFULNESS="Awake")
    equal(0, result.returncode, "manual action exit")
    equal([], compiles(f.commands()), "manual action also avoids needless full rebuild")
    contains(result.stdout, "state=current", "manual prints truthful current result")

    native = Fixture(base, bash, "native")
    result = native.run(C17_FIXTURE_ART="speed")
    equal([], compiles(native.commands()), "already native speed never overwritten")
    equal("2", native.status()["verified"], "native status truly verified")
    equal([], list(native.data.glob("managed.*")), "native reused results are not ours to reset")
    native.log.unlink()
    result = native.run("uninstall.sh", None)
    equal(0, result.returncode, "native-only uninstall")
    equal([], [line for line in native.commands() if "compile" in line], "native compiled artifacts preserved on removal")

    unknown = Fixture(base, bash, "unknown")
    result = unknown.run(C17_FIXTURE_ART="unknown")
    equal("ok", unknown.status()["state"], "unknown OEM query can still compile with cancellable ART")
    equal("unverified", unknown.status()["verification"], "unknown format never verified")
    equal("0", unknown.status()["verified"], "no fake ART evidence")
    unknown.log.unlink()
    result = unknown.run(C17_FIXTURE_ART="unknown")
    equal([], compiles(unknown.commands()), "unknown same identity does not repeatedly waste energy")
    equal("2", unknown.status()["reused"], "historical command success explicitly reused")
    equal("unverified", unknown.status()["verification"], "historical success never becomes current proof")
    unknown.log.unlink()
    result = unknown.run(C17_FIXTURE_ART="vdex")
    equal(2, len(compiles(unknown.commands())), "dependency mismatch overrides success cache")
    equal("failed", unknown.status()["state"], "remaining stale ART cannot be called ready")
    equal("art_unverified", unknown.status()["error"], "stale artifacts error")
    equal("0", unknown.status()["compiled"], "successful command with stale ART not counted as usable")
    for flag in ("false_ready",):
        malformed = Fixture(base, bash, flag)
        result = malformed.run(C17_FIXTURE_ART=flag)
        equal("0", malformed.status()["verified"], "foreign package dump not trusted")
    malformed = Fixture(base, bash, "partial-dump")
    result = malformed.run(C17_FIXTURE_ART="speed", C17_FIXTURE_PARTIAL_DUMP="1")
    equal("0", malformed.status()["verified"], "missing split status not trusted")

    for name, flags, reason in (
        ("awake", dict(C17_FIXTURE_INTERACTIVE="true", C17_FIXTURE_WAKEFULNESS="Awake"), "device_interactive"),
        ("hot-boundary", dict(C17_FIXTURE_TEMPERATURE="550"), "device_hot"),
        ("low-boundary", dict(C17_FIXTURE_LEVEL="15"), "battery_low"),
        ("power-unknown", dict(C17_FIXTURE_POWER_UNKNOWN="1"), "power_unknown"),
        ("screen-unknown", dict(C17_FIXTURE_SCREEN_UNKNOWN="1"), "screen_unknown"),
        ("old-art", dict(C17_FIXTURE_NO_CANCEL="1"), "art_cancel_unavailable"),
    ):
        g = Fixture(base, bash, name)
        result = g.run(**flags)
        equal(0, result.returncode, "safe defer " + name)
        equal("deferred", g.status()["state"], "defer state " + name)
        equal(reason, g.status()["error"], "defer reason " + name)
        equal([], compiles(g.commands()), "no interfering compile " + name)
        equal(False, (g.data / ".lock").exists(), "one-shot job exits " + name)
        equal(False, "dumpsys thermalservice" in g.commands(), "automatic never consults framework thermal status " + name)
    for charging in ("true", "false"):
        permitted = Fixture(base, bash, "auto-boundary-" + charging)
        result = permitted.run(C17_FIXTURE_LEVEL="16", C17_FIXTURE_TEMPERATURE="549", C17_FIXTURE_CHARGING=charging,
                               C17_FIXTURE_THERMAL="6", C17_FIXTURE_INTERACTIVE="false", C17_FIXTURE_WAKEFULNESS="Dozing")
        equal(0, result.returncode, "automatic boundary passes regardless of charging " + charging)
        equal("ok", permitted.status()["state"], "16 percent and 54.9 C can compile " + charging)
        equal(2, len(compiles(permitted.commands())), "both components compile at permitted boundary " + charging)
        equal(False, "dumpsys thermalservice" in permitted.commands(), "thermal severity not queried " + charging)
        # Fresh identities require a new compile; the battery boundary must still reject both charger states.
        permitted.log.unlink()
        result = permitted.run(C17_FIXTURE_BUILD="oplus/PJZ110/17.0.138", C17_FIXTURE_LEVEL="15", C17_FIXTURE_CHARGING=charging)
        equal("deferred", permitted.status()["state"], "15 percent rejected even with charger " + charging)
        equal("battery_low", permitted.status()["error"], "15 percent boundary reason " + charging)
        equal([], compiles(permitted.commands()), "15 percent never starts compile " + charging)
    permitted = Fixture(base, bash, "auto-thermal-unknown")
    result = permitted.run(C17_FIXTURE_THERMAL_UNKNOWN="1", C17_FIXTURE_CHARGING="unknown")
    equal("ok", permitted.status()["state"], "unavailable thermal and charging fields do not gate automatic compile")
    equal(2, len(compiles(permitted.commands())), "unknown ignored conditions still compile")
    equal(False, "dumpsys thermalservice" in permitted.commands(), "unknown framework thermal is not queried")
    for name, flags in (
        ("manual-hot-low-awake", dict(C17_FIXTURE_LEVEL="1", C17_FIXTURE_TEMPERATURE="800", C17_FIXTURE_CHARGING="false",
                                     C17_FIXTURE_THERMAL="6", C17_FIXTURE_INTERACTIVE="true", C17_FIXTURE_WAKEFULNESS="Awake")),
        ("manual-unknown", dict(C17_FIXTURE_POWER_UNKNOWN="1", C17_FIXTURE_THERMAL_UNKNOWN="1", C17_FIXTURE_SCREEN_UNKNOWN="1")),
    ):
        explicit = Fixture(base, bash, name)
        result = explicit.run(mode="run", **flags)
        equal(0, result.returncode, "manual ignores all device condition gates " + name)
        equal("ok", explicit.status()["state"], "manual completes " + name)
        equal(2, len(compiles(explicit.commands())), "manual explicitly starts both components " + name)
        equal(False, any(line in ("dumpsys battery", "dumpsys power", "dumpsys thermalservice") for line in explicit.commands()),
              "manual never queries device condition state " + name)
        equal(False, "sleep 15" in explicit.commands(), "manual never enters condition wait " + name)
    manual_capability = Fixture(base, bash, "manual-no-cancel")
    result = manual_capability.run(mode="run", C17_FIXTURE_NO_CANCEL="1", C17_FIXTURE_POWER_UNKNOWN="1")
    equal("deferred", manual_capability.status()["state"], "manual still requires cancellable ART capability")
    equal("art_cancel_unavailable", manual_capability.status()["error"], "manual capability failure stays explicit")
    equal([], compiles(manual_capability.commands()), "manual missing ART capability never starts unbounded work")
    compatibility = Fixture(base, bash, "priority-unknown")
    result = compatibility.run(C17_FIXTURE_NO_PRIORITY="1")
    equal("compatibility", compatibility.status()["priority"], "no pretend ART background priority on old ROM")
    equal(True, all("-p PRIORITY_BACKGROUND" not in line for line in compatibility.commands()), "unsupported priority not sent")

    timed = Fixture(base, bash, "timeout")
    result = timed.run(mode="run", C17_FIXTURE_COMPILE="hang", C17_FIXTURE_POWER_UNKNOWN="1", C17_FIXTURE_SCREEN_UNKNOWN="1",
                       C17_FIXTURE_THERMAL_UNKNOWN="1")
    equal(True, result.returncode != 0, "bounded hung compilation exits")
    equal("compile_timeout", timed.status()["error"], "real timeout recorded")
    equal(True, any("package art cancel 12345678-1234-4234-8234-123456789abc" == line for line in timed.commands()), "service-side ART operation cancelled")
    equal(False, (timed.data / "active-job").exists(), "confirmed cancellation clears pending job")
    equal(False, any(line in ("dumpsys battery", "dumpsys power", "dumpsys thermalservice") for line in timed.commands()),
          "manual monitoring skips conditions throughout the timed operation")
    stuck = Fixture(base, bash, "cancel-failure")
    result = stuck.run(mode="run", C17_FIXTURE_COMPILE="hang", C17_FIXTURE_CANCEL_FAIL="1")
    equal("compile_cancel_failed", stuck.status()["error"], "cannot confirm cancellation is explicit failure")
    equal(True, (stuck.data / "active-job").is_file(), "unconfirmed ART UUID retained")
    stuck.log.unlink()
    result = stuck.run(mode="run", C17_FIXTURE_CANCEL_FAIL="1")
    equal([], compiles(stuck.commands()), "do not overlap an unconfirmed ART job")
    result = stuck.run(mode="run")
    equal("ok", stuck.status()["state"], "successful recovery of old UUID permits next work")

    result = f.run(C17_FIXTURE_BOOTED="0")
    equal(True, result.returncode != 0, "boot timeout exit")
    equal("boot_timeout", f.status()["error"], "boot timeout record")
    result = f.run(C17_FIXTURE_BOOT_AFTER="100", C17_FIXTURE_ART="speed")
    equal(0, result.returncode, "slow boot is supported")
    equal("current", f.status()["state"], "slow boot valid ART reused")
    for failure in ("false_success", "success_exit_failure"):
        bad = Fixture(base, bash, "compile-" + failure)
        result = bad.run(mode="run", C17_FIXTURE_COMPILE=failure)
        equal(True, result.returncode != 0, "strict compile result " + failure)
        equal("0", bad.status()["compiled"], "bad command not counted " + failure)
    (f.module / "disable").touch()
    equal(27, f.run("service.sh", None).returncode, "disabled boot refused")
    (f.module / "disable").unlink()
    equal(20, f.run(mode="evil").returncode, "unknown mode refused")

    (f.data / ".lock").mkdir()
    (f.data / ".lock/boot").write_text(f.boot_id.read_text(), encoding="utf-8")
    owner = subprocess.Popen([bash, "-c", "printf '%s\\n' $$; exec /usr/bin/sleep 60"], stdout=subprocess.PIPE, text=True)
    try:
        pid = owner.stdout.readline().strip()
        (f.data / ".lock/pid").write_text(pid, encoding="utf-8")
        equal(23, f.run(mode="run").returncode, "live lock respected")
    finally:
        # Git's Windows launcher is a parent of the actual MSYS process. Signal the
        # PID emitted by this exact fixture, then reap the launcher as well.
        subprocess.run([bash, "-c", 'kill -TERM "$1" 2>/dev/null || :', "fixture", pid], check=True)
        owner.communicate(timeout=5)
    shutil.rmtree(f.data / ".lock")

    for failure in ("fail", "false_success", "success_exit_failure"):
        bad = Fixture(base, bash, "restore-" + failure)
        bad.run(mode="run")
        result = bad.run("uninstall.sh", None, C17_FIXTURE_RESTORE=failure)
        equal(True, result.returncode != 0, "restoration failure exit " + failure)
        equal("failed", bad.status()["state"], "restoration failure retained " + failure)
        equal("restore_cancel_failed" if failure == "false_success" else "restore_failed", bad.status()["error"], "restore error " + failure)
        equal(True, bad.data.exists(), "diagnosis retained " + failure)
        equal(2, len(list(bad.data.glob("managed.*"))), "remaining restore targets retained " + failure)
        equal(False, "state=removed" in result.stdout, "no false removed result " + failure)
        if failure == "false_success":
            equal(True, (bad.data / "active-job").is_file(), "untracked Binder completion prevents overlap")
            bad.boot_id.write_text("00000000-0000-4000-8000-000000000137\\n", encoding="utf-8")
        equal(0, bad.run("uninstall.sh", None).returncode, "restore can retry " + failure)
        equal(False, bad.data.exists(), "successful restore removes data " + failure)
    f.log.unlink()
    result = f.run("uninstall.sh", None)
    equal(0, result.returncode, "normal uninstall " + result.stdout + result.stderr)
    equal(["package compile -v -p PRIORITY_BACKGROUND -m verify -f --primary-dex com.android.systemui", "package compile -v -p PRIORITY_BACKGROUND -m verify -f --primary-dex com.oplus.systemui.plugins"], [line for line in f.commands() if "compile" in line], "original filters restored without erasing profiles")
    equal(False, f.data.exists(), "successful uninstall cleans only own data")

    legacy = Fixture(base, bash, "legacy-record")
    legacy.data.mkdir(parents=True)
    (legacy.data / "fingerprint").write_text("old-success", encoding="utf-8")
    (legacy.data / "status").write_text("state=ok\ncompiled=2\npackages=2", encoding="utf-8")
    result = legacy.run(C17_FIXTURE_ART="speed")
    equal(2, len(list(legacy.data.glob("managed.*"))), "old aggregate successes migrate for future restore")
    equal("14", legacy.status()["module_version_code"], "new execution distinguishable from old records")
    equal(0, legacy.run("uninstall.sh", None).returncode, "legacy migrated cleanup")
    equal(True, all("-r install -f --primary-dex" in line for line in legacy.commands() if "compile" in line), "unknown legacy policy delegated to ROM install defaults")
    interrupted_legacy = Fixture(base, bash, "legacy-running")
    interrupted_legacy.data.mkdir(parents=True)
    (interrupted_legacy.data / "status").write_text("state=running\ncompiled=0\npackages=0", encoding="utf-8")
    result = interrupted_legacy.run(C17_FIXTURE_NO_CANCEL="1")
    equal(True, (interrupted_legacy.data / "migration-pending").is_file(), "interrupted legacy evidence survives capabilities failure")
    equal(False, (interrupted_legacy.data / "migration-complete").exists(), "failure never claims migration complete")
    result = interrupted_legacy.run(C17_FIXTURE_ART="speed")
    equal(2, len(list(interrupted_legacy.data.glob("managed.*"))), "old partially modified plugin also migrated")
    equal(True, (interrupted_legacy.data / "migration-complete").is_file(), "complete enumeration marks migration")
    equal(0, interrupted_legacy.run("uninstall.sh", None).returncode, "interrupted legacy restore succeeds")

    g = Fixture(base, bash, "links")
    target = base / "elsewhere"; target.mkdir()
    try:
        os.symlink(target, g.data)
    except OSError as failure:
        if os.name != "nt" or failure.winerror != 1314:
            raise
        # A directory junction provides the same real reparse-point rejection on Windows
        # without requiring the host's symbolic-link privilege. Verify the shell sees it.
        junction_env = os.environ.copy()
        junction_env.update(C17_CHECK_LINK=str(g.data), C17_CHECK_TARGET=str(target))
        subprocess.run(["powershell.exe", "-NoProfile", "-Command",
                        "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:C17_CHECK_LINK -Target $env:C17_CHECK_TARGET | Out-Null"],
                       env=junction_env, check=True, capture_output=True)
        linked = subprocess.run([bash, "-c", 'test -L "$1"', "fixture", str(g.data)])
        equal(0, linked.returncode, "junction is a real shell-visible link, not a skipped security check")
    equal(20, g.run(mode="run").returncode, "data link refused")
    equal([], compiles(g.commands()), "no compile through link")
    h = Fixture(base, bash, "foreign")
    moved = h.path / "data/adb/modules/other"; shutil.copytree(h.module, moved)
    result = subprocess.run([bash, "-c", 'export PATH="$1/bin:/usr/bin:/bin"; exec bash "$2" run', "fixture", h.posix, str(moved / "common.sh")], env=h.env(), text=True, capture_output=True, timeout=180 if os.name == "nt" else 60)
    equal(20, result.returncode, "foreign module path refused")


def status_descriptions(base, bash):
    for name, mode, flags, error, message in (
        ("description-hot", "boot", dict(C17_FIXTURE_TEMPERATURE="550"), "device_hot", "待电池降温"),
        ("description-low", "boot", dict(C17_FIXTURE_LEVEL="15"), "battery_low", "待电量恢复"),
        ("description-awake", "boot", dict(C17_FIXTURE_INTERACTIVE="true", C17_FIXTURE_WAKEFULNESS="Awake"), "device_interactive", "待息屏或空闲优化"),
        ("description-capability", "run", dict(C17_FIXTURE_NO_CANCEL="1"), "art_cancel_unavailable", "系统暂不支持优化"),
    ):
        f = Fixture(base, bash, name)
        result = f.run(mode=mode, **flags)
        equal(0, result.returncode, "description fixture finishes " + name)
        equal("deferred", f.status()["state"], "description keeps deferred protocol " + name)
        equal(error, f.status()["error"], "human description does not alter error data " + name)
        prop = (f.module / "module.prop").read_text(encoding="utf-8")
        contains(prop, message, "manager shows exact new policy " + name)
        equal(False, error in prop, "manager does not expose raw error code " + name)


def locking(base, bash):
    """Real inherited-descriptor flock; all signals/paths belong to these fixtures."""
    # Git Bash does not reproduce Android mksh's close-on-exec descriptor policy.
    # Keep the explicit inheritance contract for every native implementation; the
    # mksh behavior itself also requires the isolated Android directory-FD probe.
    source = (ASSETS / "common.sh").read_text(encoding="utf-8")
    wrapper = source.split("flock_call() {", 1)[1].split("\n}", 1)[0]
    for implementation in ('flock "$@"', '/system/bin/toybox flock "$@"', '"$FLOCK_BIN" flock "$@"'):
        contains(wrapper, implementation + ' 9<&9;;', "native flock explicitly inherits FD9 " + implementation)
    old_boot = "00000000-0000-4000-8000-000000000001\n"
    for name, contents, old_time, expected in (
        ("empty-old-boot", {}, True, 0),
        ("pid-only-old-boot", {"pid": "1\n"}, True, 0),
        ("boot-only-old-boot", {"boot": old_boot}, False, 0),
        ("complete-old-boot", {"boot": old_boot, "pid": "1\n"}, False, 0),
        ("empty-current-legacy", {}, False, 29),
        ("boot-only-current-legacy", {"boot": "current"}, False, 29),
        ("pid-only-dead", {"pid": "99999999\n"}, False, 0),
        ("modern-empty-owner", {"generation": "10\n"}, False, 0),
        ("modern-boot-only", {"generation": "10\n", "boot": "current"}, False, 0),
    ):
        f = Fixture(base, bash, "lock-" + name)
        lock = f.data / ".lock"; lock.mkdir(parents=True)
        for key, value in contents.items():
            (lock / key).write_text(f.boot_id.read_text() if value == "current" else value, encoding="utf-8")
        if old_time: os.utime(lock, (1, 1))
        marker = f.boot_id.read_text() + "unknown\n"
        (f.data / "active-job").write_text(marker, encoding="utf-8")
        result = f.run(mode="enable")
        equal(expected, result.returncode, "lock recovery " + name + result.stderr)
        equal(marker, (f.data / "active-job").read_text(), "lock recovery never erases ART evidence " + name)
        equal(expected != 0, lock.exists(), "only proven orphan removed " + name)

    # A live PID must be distinguished from the different process that reused it.
    owner = subprocess.Popen([bash, "-c", "printf '%s\\n' $$; exec /usr/bin/sleep 120"], stdout=subprocess.PIPE, text=True)
    try:
        pid = owner.stdout.readline().strip()
        for reused in (False, True):
            f = Fixture(base, bash, "lock-pid-reuse-" + str(reused))
            lock = f.data / ".lock"; lock.mkdir(parents=True)
            (lock / "pid").write_text(pid, encoding="utf-8")
            (lock / "boot").write_text(f.boot_id.read_text(), encoding="utf-8")
            if reused: (lock / "start").write_text("1\n", encoding="utf-8")
            equal(0 if reused else 23, f.run(mode="enable").returncode, "PID start time determines liveness")
            equal(False if reused else True, lock.exists(), "live PID owner remains protected")
    finally:
        subprocess.run([bash, "-c", 'kill -TERM "$1" 2>/dev/null || :', "fixture", pid], check=True)
        owner.communicate(timeout=5)

    f = Fixture(base, bash, "lock-no-capability")
    f.data.mkdir(parents=True)
    (f.data / "status").write_text("old trusted evidence\n", encoding="utf-8")
    for mode in ("run", "enable", "uninstall", "lockcheck"):
        result = f.run(mode=mode, C17_FIXTURE_NO_FLOCK="1")
        equal(28, result.returncode, "unsupported kernel lock explicit " + mode)
        contains(result.stdout, "error=lock_unavailable", "unsupported reason")
    equal("old trusted evidence\n", (f.data / "status").read_text(), "unsupported never overwrites old evidence")
    equal([], compiles(f.commands()), "unsupported never compiles")
    contains(f.run("webui.sh", "status", C17_FIXTURE_NO_FLOCK="1").stdout, "lock_available=0", "manager can explain unsupported")
    equal(28, f.run("webui.sh", "run", C17_FIXTURE_NO_FLOCK="1").returncode, "manager refuses unsupported submission")

    def start(fixture, mode="enable", **flags):
        return subprocess.Popen([bash, "-c", 'export PATH="$1/bin:/usr/bin:/bin"; exec bash "$2" "$3"',
                                 "fixture", fixture.posix, fixture.posix + "/data/adb/modules/c17_performance/common.sh", mode],
                                env=fixture.env(**flags), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")
    def wait_file(path):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if path.is_file(): return
            time.sleep(0.03)
        raise AssertionError("fixture barrier not reached: " + str(path))

    for point in ("LOCKED=1\n", "printf '10\\n' > \"$DATA/.lock/generation\" || return 1\n"):
        f = Fixture(base, bash, "lock-initialization-" + str(len(point)))
        script = f.module / "common.sh"
        pause = '''if [ "$C17_FIXTURE_PAUSE" = 1 ]; then
printf '%s\\n' "$$" > "$C17_FIXTURE_LOG.owner"
touch "$C17_FIXTURE_LOG.ready"
while [ ! -f "$C17_FIXTURE_LOG.release" ]; do /usr/bin/sleep 0.03 9<&-; done
fi
'''
        original = script.read_text(encoding="utf-8")
        equal(1, original.count(point), "barrier is a unique real initialization boundary")
        script.write_text(original.replace(point, point + pause), encoding="utf-8", newline="\n")
        worker = start(f, C17_FIXTURE_PAUSE="1")
        try:
            wait_file(Path(str(f.log) + ".ready"))
            equal("1", f.run(mode="lockstatus").stdout.strip(), "read-only status sees initialization as busy")
            equal(23, f.run(mode="enable").returncode, "competing initializer never steals an incomplete new lock")
            if "generation" in point:
                pid = Path(str(f.log) + ".owner").read_text().strip()
                subprocess.run([bash, "-c", 'kill -KILL "$1"', "fixture", pid], check=True)
                worker.communicate(timeout=10)
                equal(0, f.run(mode="enable").returncode, "process death releases native guard and partial metadata recovers")
            else:
                Path(str(f.log) + ".release").touch()
                stdout, stderr = worker.communicate(timeout=20)
                equal(0, worker.returncode, "initializer finishes intact " + stdout + stderr)
        finally:
            Path(str(f.log) + ".release").touch()
            if worker.poll() is None: worker.communicate(timeout=20)
        equal(False, (f.data / ".lock").exists(), "no orphan after initialization lifecycle")

    # Concurrent recovery claims share the real kernel lock; critical sections cannot overlap.
    f = Fixture(base, bash, "lock-concurrent-recovery")
    (f.data / ".lock").mkdir(parents=True)
    (f.data / ".lock/boot").write_text(old_boot, encoding="utf-8")
    script = f.module / "common.sh"
    text = script.read_text(encoding="utf-8").replace("trap cleanup EXIT\n", 'trap cleanup EXIT\nprintf "begin %s\\n" "$$" >> "$C17_FIXTURE_LOG.critical"\n/usr/bin/sleep 0.15 9<&-\n')
    text = text.replace('if [ "$GUARDED" = 1 ]; then flock_call', 'if [ "$GUARDED" = 1 ]; then printf "end %s\\n" "$$" >> "$C17_FIXTURE_LOG.critical"; flock_call')
    script.write_text(text, encoding="utf-8", newline="\n")
    workers = [start(f) for _ in range(6)]
    for worker in workers:
        worker.communicate(timeout=60)
        equal(True, worker.returncode in (0, 23), "concurrent claimant completes or reports busy")
    active = None
    for line in Path(str(f.log) + ".critical").read_text().splitlines():
        event, pid = line.split()
        if event == "begin": equal(None, active, "kernel critical sections never overlap"); active = pid
        else: equal(pid, active, "only own critical section released"); active = None
    equal(None, active, "all critical sections closed")
    equal(False, (f.data / ".lock").exists(), "concurrent recovery leaves no lock")

    f = Fixture(base, bash, "lock-art-owner-death")
    # Hold the fake operation long enough to kill its owner at the actual ART marker boundary.
    # Production uses 180s; only this copied fixture's shortened 2s deadline grows.
    script = f.module / "common.sh"
    script.write_text(script.read_text(encoding="utf-8").replace("OPERATION_SECONDS=2\n", "OPERATION_SECONDS=60\n"), encoding="utf-8", newline="\n")
    worker = start(f, mode="run", C17_FIXTURE_COMPILE="hang", C17_FIXTURE_CHECK_FD="1")
    try:
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            marker = (f.data / "active-job").read_text() if (f.data / "active-job").is_file() else ""
            if "12345678-1234-4234-8234-123456789abc" in marker: break
            time.sleep(0.03)
        else: raise AssertionError("ART fixture did not publish its real job marker")
        pid = (f.data / ".lock/pid").read_text().strip()
        equal(["closed"], Path(str(f.log) + ".fd9").read_text().splitlines(), "long ART client does not inherit kernel lock descriptor")
        subprocess.run([bash, "-c", 'kill -KILL "$1"', "fixture", pid], check=True)
        worker.communicate(timeout=10)
        equal("0", f.run(mode="lockstatus").stdout.strip(), "dead owner frees guard while ART client still exists")
        equal(marker, (f.data / "active-job").read_text(), "read/recovery probe does not erase active ART evidence")
        result = f.run(mode="run")
        equal(0, result.returncode, "next worker cancels previous job before proceeding")
        equal("ok", f.status()["state"], "post-crash run completes")
        equal(False, (f.data / "active-job").exists(), "confirmed cancellation and success close ART marker")
    finally:
        # Only this fake ART job waits on this exact signal; never cancel a device job.
        Path(str(f.log) + ".cancelled").touch()
        if worker.poll() is None: worker.communicate(timeout=30)


def on_demand(base, bash):
    for name, flags, expected in (
        ("idle-before-ten", dict(C17_FIXTURE_IDLE_AGE="599999"), "device_interactive"),
        ("idle-ten", dict(C17_FIXTURE_IDLE_AGE="600000"), "none"),
        ("idle-light-recent", dict(C17_FIXTURE_IDLE_AGE="900000", C17_FIXTURE_LIGHT_AGE="1"), "device_interactive"),
        ("idle-unavailable", dict(C17_FIXTURE_IDLE_UNSUPPORTED="1"), "idle_unknown"),
        ("idle-malformed", dict(C17_FIXTURE_IDLE_AGE="-1"), "idle_unknown"),
        ("auto-boot-incomplete", dict(C17_FIXTURE_BOOTED="0", C17_FIXTURE_IDLE_AGE="900000"), "boot_incomplete"),
    ):
        f = Fixture(base, bash, name)
        result = f.run(mode="auto", C17_FIXTURE_INTERACTIVE="true", C17_FIXTURE_WAKEFULNESS="Awake", **flags)
        equal(0, result.returncode, "bounded automatic result " + name)
        equal(expected, f.status()["error"], "reliable global input evidence " + name)
        equal("auto", f.status()["mode"], "on-demand mode is distinct from manual " + name)
        equal(2 if expected == "none" else 0, len(compiles(f.commands())), "only eligible needs compile " + name)
        if expected != "none": equal("deferred", f.status()["state"], "ineligible auto defers " + name)
    f = Fixture(base, bash, "manager-action-control-center")
    manual_flags = dict(C17_FIXTURE_BOOTED="0", C17_FIXTURE_LEVEL="1", C17_FIXTURE_TEMPERATURE="800",
                        C17_FIXTURE_CHARGING="false", C17_FIXTURE_THERMAL="6", C17_FIXTURE_INTERACTIVE="true",
                        C17_FIXTURE_WAKEFULNESS="Awake", C17_FIXTURE_IDLE_AGE="0")
    result = f.run("action.sh", None, **manual_flags)
    equal(0, result.returncode, "manager action opens control center")
    contains(result.stdout, "已提交优化", "manager Execute preserves its explicit manual request")
    contains(result.stdout, "已打开 C17 控制中心", "manager action acknowledgement")
    contains("\n".join(f.commands()), "dev.puitheme.PerformanceControlActivity", "only fixed activity entry")
    status = f.wait_complete(expected="ok", mode="run")
    equal("2", status["compiled"], "manager Execute immediately compiles only required native components")
    equal(["com.android.systemui", "com.oplus.systemui.plugins"], compiles(f.commands()), "manager manual keeps primary component scope")
    equal(False, any(line in ("dumpsys battery", "dumpsys power", "dumpsys thermalservice") for line in f.commands()),
          "manager manual never queries environment gates")
    equal(False, any(line.startswith("sleep 15") or line.startswith("sleep 2") for line in f.commands()),
          "manager manual never waits for idle, battery or system boot")
    f.log.unlink()
    equal(0, f.run("action.sh", None, **manual_flags).returncode, "manager repeated manual request acknowledged")
    equal("2", f.wait_complete(expected="current", mode="run")["reused"], "manager manual reuses valid ART")
    equal([], compiles(f.commands()), "manager manual never recompiles ready results")
    missing = Fixture(base, bash, "manager-action-no-apk")
    result = missing.run("action.sh", None, C17_FIXTURE_AM_FAIL="1", **manual_flags)
    equal(0, result.returncode, "missing APK does not discard an accepted manual request")
    contains(result.stdout, "控制中心未能打开", "missing page reported separately from optimization result")
    equal("2", missing.wait_complete(expected="ok", mode="run")["compiled"], "manual operation survives missing APK")
    capability = Fixture(base, bash, "manager-action-no-art-cancel")
    equal(0, capability.run("action.sh", None, C17_FIXTURE_NO_CANCEL="1", **manual_flags).returncode,
          "manager action acknowledges a bounded worker, not a claimed result")
    equal("art_cancel_unavailable", capability.wait_complete(expected="deferred", mode="run")["error"],
          "manager manual preserves ART capability failure")
    equal([], compiles(capability.commands()), "manager manual never bypasses necessary ART safety")
    unsafe = Fixture(base, bash, "manager-action-unconfirmed-task")
    unsafe.data.mkdir(parents=True)
    marker = unsafe.boot_id.read_text(encoding="utf-8") + "unknown\n"
    (unsafe.data / "active-job").write_text(marker, encoding="utf-8")
    equal(0, unsafe.run("action.sh", None, **manual_flags).returncode, "manager can open status for an unsafe prior task")
    status = unsafe.wait_complete(expected="failed", mode="run")
    equal("compile_cancel_failed", status["error"], "manager preserves unconfirmed cancellation error")
    equal("active_unknown", status["diagnostic"], "manager preserves the actual safety reason")
    equal(marker, (unsafe.data / "active-job").read_text(encoding="utf-8"), "unconfirmed ART marker is retained")
    equal([], compiles(unsafe.commands()), "manager action never overlaps an unconfirmed ART task")
    rejected = Fixture(base, bash, "manager-action-untrusted-script")
    equal(20, rejected.run("action.sh", None, C17_FIXTURE_PERMISSION="777").returncode, "manager action rejects writable script paths")
    equal([], rejected.commands(), "unsafe manager paths launch neither worker nor page")
    f = Fixture(base, bash, "webui-fixed-status")
    f.run(mode="run")
    result = f.run("webui.sh", "status")
    equal(0, result.returncode, "web status read succeeds")
    for marker in ("C17_WEB:begin", "C17_WEB:end", "record_present=1", "module_version_code=12", "busy=0", "active_job=0", "last_diagnostic=none", "total_compiles=2", "total_wall_ms=3000"):
        contains(result.stdout, marker, "fixed real web status field " + marker)
    equal(20, f.run("webui.sh", "run;id").returncode, "web command injection rejected")
    result = f.run("webui.sh", "disable")
    equal(0, result.returncode, "fixed disable succeeds")
    equal(True, (f.module / "disable").exists(), "disabled flag is owned by root management")
    equal(27, f.run("webui.sh", "run").returncode, "disabled module rejects manual run")
    result = f.run("webui.sh", "enable")
    equal(0, result.returncode, "fixed enable succeeds")
    equal(False, (f.module / "disable").exists(), "enable restores future checks")
    equal("current", f.wait_complete(expected="current", mode="auto")["state"], "enable only reuses ready ART")


def app_commands(base, bash, javabin, classpath):
    java = [javabin, "-Dfile.encoding=UTF-8", "-Xms16m", "-Xmx128m", "-XX:+UseSerialGC", "-cp", classpath, "dev.puitheme.RootPerformanceModuleCheck"]
    out = base / "commands"; out.mkdir()
    subprocess.run(java + [str(out)], check=True, capture_output=True)
    f = Fixture(base, bash, "app")
    (f.path / "data/adb/ksu/bin").mkdir(parents=True)
    ksud = f.path / "data/adb/ksu/bin/ksud"
    ksud.write_text('#!/bin/sh\nprintf "ksud %s\\n" "$*" >> "$C17_FIXTURE_LOG"\n'
                    'if [ "$1 $2" = "module uninstall" ] && [ "$3" = c17_resident_cache ]; then touch "' + f.posix + '/data/adb/modules/c17_resident_cache/remove"; fi\n', encoding="utf-8")
    ksud.chmod(0o755)
    legacy = f.path / "data/adb/modules/c17_resident_cache"
    legacy.mkdir(parents=True)
    (legacy / "module.prop").write_text("id=c17_resident_cache\n", encoding="utf-8")
    subprocess.run([bash, "-c", 'chmod 644 "$1/module.prop"; chmod 755 "$1"', "fixture",
                    f.posix + "/data/adb/modules/c17_resident_cache"], check=True)

    def generated(action):
        return f.rewrite((out / (action + ".sh")).read_text(encoding="utf-8"))

    def run(action, **flags):
        # MSYS may normalize the inherited Windows PATH before -c; bind fixtures inside the shell.
        return subprocess.run([bash, "-c", 'export PATH="$1/bin:/usr/bin:/bin"; ' + generated(action), "fixture", f.posix],
                              env=f.env(**flags), text=True, encoding="utf-8", capture_output=True, timeout=180 if os.name == "nt" else 60)

    # Permission guards consume octal digits as data, across Android and manager shell arithmetic modes.
    for permission in ("600", "644", "700", "755", "4700", "602", "622", "666", "775", "777"):
        result = run("CHECK", C17_FIXTURE_PERMISSION=permission)
        if permission in ("600", "644", "700", "755", "4700"):
            contains(result.stdout, "C17_PERF:installed=1", "generated reader permission guard " + permission)
        else:
            equal(20, result.returncode, "unsafe module permissions explicitly rejected " + permission)
            equal(False, "C17_PERF:begin" in result.stdout, "unsafe module never mistaken for uninstalled " + permission)
        equal(False, f.data.exists(), "permission CHECK is read-only " + permission)

    result = run("CHECK")
    equal(0, result.returncode, "check exit " + result.stdout + result.stderr)
    contains(result.stdout, "C17_PERF:manager=KernelSU", "manager detected"); contains(result.stdout, "C17_PERF:legacy=1", "old backup module reported")
    equal(False, (legacy / "remove").exists(), "a status check never removes the old module")
    result = run("INSTALL")
    equal(0, result.returncode, "install exit " + result.stdout + result.stderr)
    contains(f.log.read_text(encoding="utf-8"), "ksud module install", "manager install used")
    contains(f.log.read_text(encoding="utf-8"), "ksud module uninstall c17_resident_cache", "old backup module removed via manager")
    contains(result.stdout, "C17_PERF:legacy=0", "old backup module no longer reported")
    equal("ok", f.wait_complete(expected="ok", mode="boot")["state"], "install automatically checks suitable conditions without a SystemUI restart")
    result = run("DISABLE")
    equal(0, result.returncode, "disable exit"); equal(True, (f.module / "disable").exists(), "disable flag")
    contains(result.stdout, "C17_PERF:disabled=1", "disabled reported")
    result = run("RUN")
    equal(27, result.returncode, "run refused while disabled")
    result = run("ENABLE")
    equal(0, result.returncode, "enable exit"); equal(False, (f.module / "disable").exists(), "disable flag removed")
    equal("current", f.wait_complete(expected="current", mode="boot")["state"], "enable automatically checks the existing compilation")
    f.run("common.sh", "boot")
    result = run("CHECK")
    contains(result.stdout, "C17_PERF:last_state=current", "check reports the module record")
    modern_protocol = base / "modern-check-output.txt"
    modern_protocol.write_text(result.stdout, encoding="utf-8")
    web = subprocess.run(java + ["--web-status-file", str(modern_protocol)], text=True, encoding="utf-8", capture_output=True)
    equal(0, web.returncode, "native H5 status serializes the real generated CHECK: " + web.stdout + web.stderr)
    contains(web.stdout, "C17_WEB:begin", "native H5 framing starts")
    contains(web.stdout, "C17_WEB:end", "native H5 framing ends")
    (base / "native-web-status.txt").write_text(web.stdout, encoding="utf-8")
    protocol = base / "real-check-output.txt"
    protocol.write_text(result.stdout, encoding="utf-8")
    parsed = subprocess.run(java + ["--parse-file", str(protocol)],
                            text=True, encoding="utf-8", capture_output=True)
    equal(0, parsed.returncode, "real CHECK result must parse, including a legacy status file without final newline: " + parsed.stdout + parsed.stderr)
    # Real V1 record captured on PJZ110 had no final LF; current APK must read it without upgrading or running the module.
    (f.data / "status").write_text("state=ok\ntime=1791500000\ncompiled=2\npackages=2\nerror=none", encoding="utf-8")
    before = (f.data / "status").read_bytes()
    result = run("CHECK")
    protocol.write_text(result.stdout, encoding="utf-8")
    parsed = subprocess.run(java + ["--parse-file", str(protocol)],
                            text=True, encoding="utf-8", capture_output=True)
    equal(0, parsed.returncode, "PJZ110 legacy no-LF status is readable: " + parsed.stdout + parsed.stderr)
    equal(before, (f.data / "status").read_bytes(), "CHECK leaves the legacy record unchanged")
    result = run("RUN")
    equal(0, result.returncode, "run exit " + result.stdout + result.stderr)
    equal("current", f.wait_complete(expected="current", mode="run")["state"], "manual detached check reuses valid ART")
    # Root managers may stage script-only updates before their next boot. They are safe to run from that validated location immediately.
    staged = f.path / "data/adb/modules_update/c17_performance"
    staged.parent.mkdir(parents=True)
    shutil.copytree(f.module, staged)
    subprocess.run([bash, "-c", 'chmod 755 "$1" "$1"/*.sh; chmod 644 "$1/module.prop"', "fixture", f.posix + "/data/adb/modules_update/c17_performance"], check=True)
    before = len(compiles(f.commands()))
    result = run("RUN")
    equal(0, result.returncode, "a staged script-only update can run without a reboot")
    f.wait_complete(expected="current", mode="run")
    contains(result.stdout, "C17_PERF:pending=1", "staged manager registration is reported separately")
    equal(before, len(compiles(f.commands())), "staged script checks and reuses real valid ART")
    result = run("REMOVE")
    equal(0, result.returncode, "remove exit")
    contains(f.log.read_text(encoding="utf-8"), "ksud module uninstall c17_performance", "manager uninstall used")


def reuse_without_compiler(base, bash):
    ready = Fixture(base, bash, "ready-no-compiler")
    result = ready.run(C17_FIXTURE_ART="speed", C17_FIXTURE_NO_CANCEL="1")
    equal(0, result.returncode, "verified reuse without compile capability exits")
    for key, value in dict(state="current", verified="2", reused="2", compiled="0", deferred="0", error="none").items():
        equal(value, ready.status()[key], "verified reuse without compiler " + key)
    equal([], compiles(ready.commands()), "read-only reuse never compiles")
    equal([], list(ready.data.glob("managed.*")), "read-only reuse never takes ownership")
    equal(False, any(line.startswith("dumpsys battery") for line in ready.commands()), "reuse never waits for device conditions")

    mixed = Fixture(base, bash, "mixed-no-compiler")
    Path(str(mixed.log) + ".art.com.android.systemui").write_text("speed\n", encoding="utf-8")
    result = mixed.run(C17_FIXTURE_NO_CANCEL="1")
    equal("deferred", mixed.status()["state"], "one ready target cannot hide a pending target")
    equal("1", mixed.status()["verified"], "mixed current evidence is retained")
    equal("1", mixed.status()["deferred"], "only missing ART needs compilation")
    equal("art_cancel_unavailable", mixed.status()["error"], "missing compiler capability remains explicit")
    equal([], compiles(mixed.commands()), "missing capability starts no compilation")
    equal([], list(mixed.data.glob("managed.*")), "deferred work takes no ownership")

    unknown = Fixture(base, bash, "unknown-cached-no-compiler")
    unknown.run(C17_FIXTURE_ART="unknown")
    unknown.log.unlink()
    unknown.run(C17_FIXTURE_ART="unknown", C17_FIXTURE_NO_CANCEL="1")
    equal("deferred", unknown.status()["state"], "cached unknown results cannot bypass capability checks")
    equal("0", unknown.status()["verified"], "cached command result never counts as ART proof")
    equal([], compiles(unknown.commands()), "unknown with missing capability never compiles")

    for name in ("legacy", "active", "uninstall"):
        strict = Fixture(base, bash, "strict-capability-" + name)
        strict.data.mkdir(parents=True)
        if name == "legacy": (strict.data / "fingerprint").write_text("old", encoding="utf-8")
        if name == "active": (strict.data / "active-job").write_text(strict.boot_id.read_text() + "unknown\n", encoding="utf-8")
        strict.run(mode="uninstall" if name == "uninstall" else "boot", C17_FIXTURE_ART="speed", C17_FIXTURE_NO_CANCEL="1")
        equal("failed" if name == "uninstall" else "deferred", strict.status()["state"], "strict capability gate " + name)
        equal(False, any("art dump" in line for line in strict.commands()), "no fast path around recovery " + name)
        equal([], compiles(strict.commands()), "no unsafe compilation " + name)
        if name == "legacy": equal(True, (strict.data / "migration-pending").exists(), "legacy marker survives missing capability")
    reuse_without_compiler_boundaries(base, bash)


def reuse_without_compiler_boundaries(base, bash):
    """The verified-ART exception never bypasses bounded queries or unfinished recovery."""
    no_timer = Fixture(base, bash, "ready-no-timeout")
    # Simulate an installed but unusable timeout, which fails the exact production -k probe.
    timer = no_timer.bin / "timeout"
    timer.write_text("#!/bin/sh\nexit 127\n", encoding="utf-8", newline="\n")
    timer.chmod(0o755)
    result = no_timer.run(C17_FIXTURE_ART="speed", C17_FIXTURE_NO_CANCEL="1")
    equal(0, result.returncode, "unavailable bounded command reports deferral")
    equal("deferred", no_timer.status()["state"], "ready ART never bypasses timeout requirement")
    equal("timeout_unavailable", no_timer.status()["error"], "timeout failure remains explicit")
    equal("0", no_timer.status()["verified"], "unbounded ART was never queried")
    equal(["package list packages -U"], no_timer.commands(), "missing ART timeout still allows the independent cache UID lookup only")
    equal([], list(no_timer.data.glob("managed.*")), "missing timeout creates no ownership")

    for shape in ("empty", "invalid", "directory", "link"):
        active = Fixture(base, bash, "active-ready-no-compiler-" + shape)
        active.data.mkdir(parents=True)
        marker = active.data / "active-job"
        target = None
        if shape == "empty": marker.write_text("", encoding="utf-8")
        elif shape == "invalid": marker.write_text(active.boot_id.read_text() + "not-a-job-id\n", encoding="utf-8")
        elif shape == "directory": marker.mkdir()
        else:
            target = active.path / "outside-active-marker"
            target.mkdir()
            (target / "sentinel").write_text("untouched", encoding="utf-8")
            try:
                os.symlink(target, marker, target_is_directory=True)
            except OSError as failure:
                if os.name != "nt" or failure.winerror != 1314: raise
                link_env = os.environ.copy()
                link_env.update(C17_CHECK_LINK=str(marker), C17_CHECK_TARGET=str(target))
                subprocess.run(["powershell.exe", "-NoProfile", "-Command",
                                "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:C17_CHECK_LINK -Target $env:C17_CHECK_TARGET | Out-Null"],
                               env=link_env, check=True, capture_output=True)
            linked = subprocess.run([bash, "-c", 'test -L "$1"', "fixture", str(marker)])
            equal(0, linked.returncode, "active-job link is a real shell-visible link")
        result = active.run(C17_FIXTURE_ART="speed", C17_FIXTURE_NO_CANCEL="1")
        equal(0, result.returncode, "strict active marker defers " + shape)
        equal("deferred", active.status()["state"], "malformed active marker cannot become current " + shape)
        equal("art_cancel_unavailable", active.status()["error"], "active marker keeps cancellation gate " + shape)
        equal(False, any("art dump" in line or "art cancel" in line for line in active.commands()), "no recovery bypass " + shape)
        equal([], compiles(active.commands()), "no compilation beside unresolved active marker " + shape)
        equal([], list(active.data.glob("managed.*")), "unresolved active marker creates no ownership " + shape)
        equal(True, marker.exists(), "unresolved active marker retained " + shape)
        if target is not None: equal("untouched", (target / "sentinel").read_text(encoding="utf-8"), "linked marker target retained")

    for legacy in ("running", "pending", "pending-directory"):
        old = Fixture(base, bash, "legacy-ready-no-compiler-" + legacy)
        old.data.mkdir(parents=True)
        if legacy == "running": (old.data / "status").write_text("state=running\npackages=0\n", encoding="utf-8")
        elif legacy == "pending-directory": (old.data / "migration-pending").mkdir()
        else: (old.data / "migration-pending").write_text("legacy\n", encoding="utf-8")
        old.run(C17_FIXTURE_ART="speed", C17_FIXTURE_NO_CANCEL="1")
        equal("deferred", old.status()["state"], "unfinished legacy migration cannot reuse ready ART " + legacy)
        equal("art_cancel_unavailable", old.status()["error"], "legacy cancellation capability remains required " + legacy)
        equal(False, any("art dump" in line for line in old.commands()), "no ready-query shortcut before legacy recovery " + legacy)
        equal(True, (old.data / "migration-pending").is_dir() if legacy == "pending-directory" else (old.data / "migration-pending").is_file(), "legacy evidence retained " + legacy)
        equal(False, (old.data / "migration-complete").exists(), "no capability never completes migration " + legacy)
        equal([], list(old.data.glob("managed.*")), "blocked migration never creates ownership " + legacy)
        equal([], compiles(old.commands()), "blocked migration never compiles " + legacy)


def reuse_and_logs(base, bash):
    f = Fixture(base, bash, "reuse-and-logs")
    prop = f.bin / "getprop"
    prop.write_text(prop.read_text(encoding="utf-8").replace("#!/bin/sh\n", '#!/bin/sh\nprintf "getprop %s\\n" "$*" >> "$C17_FIXTURE_LOG"\n', 1), encoding="utf-8")
    result = f.run(mode="run")
    equal(0, result.returncode, "cold optimization succeeds")
    commands = f.commands()
    equal(1, commands.count("getprop ro.build.fingerprint"), "build identity read once for all packages")
    equal(4, sum(line.startswith("package art dump ") for line in commands), "cold run queries each ART state once before and once after compile")
    equal(2, len(compiles(commands)), "each native package compiled once")
    for record in f.data.glob("package.*"):
        record.write_text("versionCode=8\n" + record.read_text(encoding="utf-8"), encoding="utf-8")
    f.log.write_text("", encoding="utf-8")
    result = f.run(mode="run", C17_FIXTURE_ART="unknown")
    equal(0, result.returncode, "legacy identity remains reusable after companion upgrade")
    equal([], compiles(f.commands()), "companion upgrade does not recompile unchanged native APKs")
    equal("2", f.status()["reused"], "both legacy package identities reused")
    equal(2, sum(line.startswith("package art dump ") for line in f.commands()), "warm run performs only one verification query per native package")
    contains((f.data / "trace").read_text(encoding="utf-8"), "proof=identity_only", "fallback log states the actual reuse evidence")
    (f.data / "trace").write_text(("old diagnostic " + "x" * 180 + "\n") * 30, encoding="utf-8")
    f.run(mode="run", C17_FIXTURE_ART="speed")
    trace = (f.data / "trace").read_bytes()
    equal(True, len(trace) <= 2049, "execution log has a hard byte bound including final newline")
    equal(True, len(trace.splitlines()) <= 24, "execution log has a hard entry bound")
    contains(trace.decode(), "proof=native_art", "verified reuse log remains visible after trimming")
    failed = Fixture(base, bash, "failure-log")
    failed.run(mode="run", C17_FIXTURE_COMPILE="fail")
    evidence = (failed.data / "operation-last").read_text(encoding="utf-8")
    for token in ("time=", "package=", "stage=compile", "error=", "diagnostic=", "FAILED"):
        contains(evidence, token, "failure report preserves " + token)
    equal(True, len(evidence.encode()) <= 2048, "failure diagnostic remains bounded")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bash", default=shutil.which("bash"))
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--classpath", help="compiled checks and production classes for the generated-command part")
    parser.add_argument("--status-only", action="store_true", help="only exercise manager descriptions against the real scripts")
    parser.add_argument("--groups", default="locking,scripts,on_demand,descriptions,reuse,app", help="independent fixture groups: locking,scripts,on_demand,descriptions,reuse,app")
    args = parser.parse_args()
    groups = set(args.groups.split(","))
    if not groups <= {"locking", "scripts", "on_demand", "descriptions", "reuse", "app"}: parser.error("unknown fixture group")
    base = Path(tempfile.mkdtemp(prefix="c17-performance-module-"))
    if not args.status_only:
        if "locking" in groups: locking(base, args.bash)
        if "scripts" in groups: scripts(base, args.bash)
        if "on_demand" in groups: on_demand(base, args.bash)
        if "reuse" in groups: reuse_and_logs(base, args.bash)
    if args.status_only or "descriptions" in groups: status_descriptions(base, args.bash)
    print(f"performance-module scripts: {CHECKS} checks passed", flush=True)
    if args.classpath and not args.status_only and "app" in groups:
        app_commands(base, args.bash, args.java, args.classpath)
    print(f"performance-module-check: {CHECKS} checks passed (fixtures in {base})")


if __name__ == "__main__":
    main()
