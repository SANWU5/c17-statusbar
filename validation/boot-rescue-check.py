#!/usr/bin/env python3
"""Exercise the production recovery shell only inside a rewritten temporary root.

No device, su, Android package manager or real SystemUI path is used. Root-only identity
and Android services are fixtures; deletion is the production find traversal on temp data.
"""
from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/performance-module/boot-rescue.sh'
BOOT = '11111111-2222-4333-8444-555555555555'
CHECKS = 0


def yes(value, label):
    global CHECKS
    CHECKS += 1
    if not value:
        raise AssertionError(label)


class Fixture:
    def __init__(self, parent, bash, name):
        self.bash = bash
        self.path = parent / name
        self.path.mkdir()
        self.posix = subprocess.check_output([bash, '-c', 'cd "$1" && pwd -P', 'fixture', str(self.path)], text=True, encoding='utf8').strip() if os.name == 'nt' else str(self.path)
        self.module = self.path / 'data/adb/modules/c17_performance'
        self.data = self.path / 'data/adb/c17-performance'
        self.module.mkdir(parents=True)
        self.data.mkdir()
        self.bin = self.path / 'bin'
        self.bin.mkdir()
        self.log = self.path / 'commands.log'
        self.proc = self.path / 'proc'
        (self.proc / 'sys/kernel/random').mkdir(parents=True)
        (self.proc / 'sys/kernel/random/boot_id').write_bytes((BOOT + '\n').encode())
        (self.proc / 'self').mkdir()
        (self.proc / 'self/mountinfo').write_text('1 0 1:1 / / rw - ext4 /dev/mock rw\n')
        self.roots = [self.path / p for p in ('data/user_de/0/com.android.systemui', 'data/user/0/com.android.systemui', 'data/media/0/Android/data/com.android.systemui')]
        for folder in self.roots:
            folder.mkdir(parents=True)
            (folder / 'settings').mkdir()
            (folder / 'settings/sentinel').write_text('SystemUI setting')
            (folder / '.hidden').write_text('hidden setting')
        self.other = self.path / 'data/user_de/0/com.other'
        self.other.mkdir()
        (self.other / 'sentinel').write_text('must remain')
        system = self.path / 'data/system'
        system.mkdir()
        (system / 'packages.list').write_bytes(b'com.android.systemui 10160 0 /data/user/0/com.android.systemui default 0\ncom.other 10161 0 /data/user/0/com.other default 0\n')
        self.uid = 10160
        self.flags = {'booted': '0', 'bootanim': 'running', 'unlocked': 'true', 'service': '0', 'pm': 'reject'}
        for key, value in self.flags.items():
            self.flag(key, value)
        self.module.joinpath('module.prop').write_text('id=c17_performance\nversionCode=12\n')
        self.script = self.module / 'boot-rescue.sh'
        text = SOURCE.read_text(encoding='utf8')
        text = text.replace('PATH=/system/bin:/system/xbin:/vendor/bin', 'PATH="' + self.posix + '/bin:/usr/bin:/bin"')
        text = re.sub(r'(?<![A-Za-z0-9_./-])/data(?=/|\b)', self.posix + '/data', text).replace('/proc/', self.posix + '/proc/')
        # The asynchronous continuation is exercised separately under --resume. Never let a
        # test leave a 15 minute child behind; no production override exists for paths or modes.
        text = text.replace('sh "$MODDIR/boot-rescue.sh" --resume </dev/null >/dev/null 2>&1 &', ': # isolated test invokes --resume explicitly')
        text = text.replace('attempt" -lt 450', 'attempt" -lt 2')
        text = text.replace('MODE=$1', '''# Native signals are a platform seam; fixtures never signal host processes.
kill() { printf 'kill %s\\n' "$*" >> "$C17_TEST_ROOT/commands.log"; return 0; }
MODE=$1''')
        self.script.write_text(text, encoding='utf8', newline='\n')
        fake = {
            'id': "printf '0\\n'\n",
            'getprop': '''case "$1" in
 sys.boot_completed) cat "$C17_TEST_ROOT/booted";;
 init.svc.bootanim) cat "$C17_TEST_ROOT/bootanim";;
 sys.user.0.ce_available) cat "$C17_TEST_ROOT/unlocked";;
 ro.product.cpu.abi) printf 'arm64-v8a\\n';;
esac
''',
            'stat': '''if [ "$1" = -c ] && [ "$2" = %u ]; then
 case "$3" in
  */data/user_de/0/com.android.systemui|*/data/user/0/com.android.systemui|*/data/data/com.android.systemui|*/data/media/0/Android/data/com.android.systemui) cat "$C17_TEST_ROOT/uid";;
  *) printf '0\\n';;
 esac
elif [ "$1" = -c ] && [ "$2" = %a ]; then
 case "$3" in */boot-rescue.????????-????-????-????-????????????) printf '600\\n';; *) exec /usr/bin/stat "$@";; esac
else exec /usr/bin/stat "$@"; fi
''',
            'cmd': '''case "$1" in
 -l) case "$(cat "$C17_TEST_ROOT/service")" in
   0) printf 'Currently running services:\\n  activity\\n';;
   1) printf 'Currently running services:\\n  activity\\n  package\\n';;
   bare) printf 'package\\n';;
   whitespace) printf 'Currently running services:\\n \\tpackage \\t\\n  android.hardware.example@1.0::IExample/default\\n';;
   bare-missing) printf 'activity\\nandroid.hardware.example@1.0::IExample/default\\n';;
   bare-error) printf 'error\\n';;
   bare-garbage) printf 'garbage\\n';;
   near-package) printf 'Currently running services:\\n  package_native\\n';;
   failed-list) printf 'Currently running services:\\n  package\\n'; exit 20;;
   transport) printf 'cmd: Unable to get default service manager!\\n' >&2; exit 20;;
   timeout) exit 124;;
   empty) :;;
   header-only) printf 'Currently running services:\\n';;
   whitespace-only) printf ' \\t\\n';;
   garbled) printf '服务列表读取失败\\n';;
   mixed) printf 'Currently running services:\\n  package\\nBinder transaction failed\\n';;
   malformed-header) printf 'Currently running services?\\n  package\\n';;
  esac;;
 package) [ "$(cat "$C17_TEST_ROOT/service")" = 1 ] && printf 'package:com.android.systemui uid:10160\\n';;
esac
''',
            'pm': '''printf 'pm %s\\n' "$*" >> "$C17_TEST_ROOT/commands.log"
case "$(cat "$C17_TEST_ROOT/pm")" in
 success) /usr/bin/find "$C17_TEST_ROOT/data/user_de/0/com.android.systemui" "$C17_TEST_ROOT/data/user/0/com.android.systemui" -mindepth 1 -delete; printf 'Success\\n';;
 reject) printf 'java.lang.SecurityException: Cannot clear data of protected package: com.android.systemui\\n'; exit 1;;
 unknown) printf 'No callback\\n'; exit 124;;
 empty) exit 0;;
 transport) printf 'Binder transaction failed\\n'; exit 1;;
 misleading) printf 'Success in old message\\n'; exit 1;;
esac
''',
            'pidof': '[ -f "$C17_TEST_ROOT/pids" ] && cat "$C17_TEST_ROOT/pids"\n',
            'sleep': 'exec /usr/bin/sleep 0.01\n',
        }
        for name, body in fake.items():
            self.bin.joinpath(name).write_text('#!/bin/sh\n' + body, encoding='utf8', newline='\n')
        (self.path / 'uid').write_text(str(self.uid) + '\n')
        subprocess.run([bash, '-c', 'chmod 755 "$1"/bin/*; chmod 700 "$1/data/adb" "$1/data/adb/modules" "$1/data/adb/modules/c17_performance" "$1/data/adb/c17-performance"; chmod 644 "$1/data/adb/modules/c17_performance/module.prop" "$1/data/adb/modules/c17_performance/boot-rescue.sh" "$1/data/system/packages.list"', 'fixture', self.posix], check=True)

    def flag(self, key, value):
        self.path.joinpath(key).write_bytes((value + '\n').encode())

    def run(self, mode, *extra):
        env = os.environ.copy()
        env['C17_TEST_ROOT'] = self.posix
        env['MSYS'] = 'winsymlinks:nativestrict'
        return subprocess.run([self.bash, str(self.script), mode, *extra], env=env, text=True, encoding='utf8', errors='replace', capture_output=True, timeout=30)

    def auth(self):
        marker = self.data / ('boot-rescue.' + BOOT)
        marker.write_bytes(('authorized\n' + BOOT + '\n').encode())
        self.chmod(marker, '600')
        return marker

    def chmod(self, path, mode):
        subprocess.run([self.bash, '-c', 'chmod "$1" "$2"', 'fixture', mode, str(path)], check=True)

    def link(self, target, link):
        if os.name == 'nt':
            # Native symlink privilege is absent on some Windows hosts. A junction is a
            # genuine reparse link and MSYS lstat/-L observes it; do not replace with a copy.
            quote = lambda value: "'" + str(value).replace("'", "''") + "'"
            command = 'New-Item -ItemType Junction -Path ' + quote(link) + ' -Target ' + quote(target) + ' | Out-Null'
            subprocess.run(['powershell.exe', '-NoProfile', '-Command', command], check=True, capture_output=True)
        else:
            os.symlink(target, link, target_is_directory=True)
        result = subprocess.run([self.bash, '-c', 'test -L "$1"', 'fixture', str(link)], capture_output=True)
        yes(result.returncode == 0, 'fixture has a genuine lstat-visible link')

    def status(self):
        return dict(line.split('=', 1) for line in self.run('--status').stdout.splitlines() if '=' in line)

    def safe(self):
        yes(self.other.joinpath('sentinel').read_text() == 'must remain', 'other package sentinel remains')

    def clean(self):
        for directory in self.roots:
            yes(directory.is_dir() and not list(directory.iterdir()), 'package root remains empty: ' + directory.name)
        self.safe()


def final_smoke(parent, bash):
    f = Fixture(parent, bash, 'expired-pending-new-gesture')
    f.auth()
    f.data.joinpath('boot-rescue-pending').write_bytes(b'99999999-2222-4333-8444-555555555555\n10160\nce\n')
    f.flag('service', '1'); f.flag('pm', 'unknown')
    yes(f.run('--trigger').returncode != 0, 'current gesture with uncertain PM remains failed')
    yes(not f.data.joinpath('boot-rescue-pending').exists(), 'new physical authorization removes only expired continuation metadata')
    yes(f.status()['rescue_error'] == 'clear_unknown', 'expired prior continuation cannot mask current unknown PM')
    yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'uncertain request leaves all fixture package data')

    for label, name, uid in [('exact', 'com.android.systemui', '10160'), ('foreign-name', 'com.other', '10160'), ('foreign-uid', 'com.android.systemui', '10161')]:
        f = Fixture(parent, bash, 'process-' + label)
        f.auth(); f.path.joinpath('pids').write_bytes(b'12345678\n')
        process = f.proc / '12345678'
        process.mkdir()
        process.joinpath('cmdline').write_bytes(name.encode() + b'\0')
        process.joinpath('status').write_bytes(('Uid:\t' + uid + '\t' + uid + '\t' + uid + '\t' + uid + '\n').encode())
        process.joinpath('stat').write_bytes(('12345678 (com.android.systemui) S ' + '0 ' * 18 + '987654\n').encode())
        result = f.run('--trigger')
        lines = f.log.read_text().splitlines() if f.log.exists() else []
        if label == 'exact':
            yes(result.returncode == 0, 'exact SystemUI process allows reset')
            yes(lines == ['kill -STOP 12345678', 'kill -CONT 12345678', 'kill -TERM 12345678'], 'only verified PID is paused, resumed and reloaded')
            f.clean()
        else:
            yes(result.returncode != 0 and f.status()['rescue_error'] == 'reset_failed', 'foreign process identity rejected: ' + label)
            yes(not lines and f.roots[0].joinpath('.hidden').exists(), 'foreign process is never signalled or cleared: ' + label)


def service_discovery(parent, bash):
    for service in ('1', 'bare', 'whitespace'):
        f = Fixture(parent, bash, 'service-present-' + service)
        f.auth(); f.flag('service', service); f.flag('pm', 'unknown')
        result = f.run('--trigger')
        yes(result.returncode != 0 and f.status()['rescue_error'] == 'clear_unknown', 'present package service uses PM before fallback: ' + service)
        yes(f.log.exists() and f.log.read_text().splitlines() == ['pm clear --user 0 com.android.systemui'], 'one fixed PM request for valid service list: ' + service)
        yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'unknown PM with valid list preserves all data: ' + service)
        yes(not f.data.joinpath('boot-rescue-pending').exists(), 'unknown PM cannot create a raw continuation: ' + service)

    for service in ('0', 'near-package'):
        f = Fixture(parent, bash, 'service-missing-' + service)
        f.auth(); f.flag('service', service)
        yes(f.run('--trigger').returncode == 0 and f.status()['rescue_state'] == 'cleared', 'valid list without exact package permits guarded fallback: ' + service)
        yes(not f.log.exists(), 'missing exact package never sends PM request: ' + service)
        f.clean()

    for service in ('bare-missing', 'bare-error', 'bare-garbage', 'failed-list', 'transport', 'timeout', 'empty', 'header-only', 'whitespace-only', 'garbled', 'mixed', 'malformed-header'):
        f = Fixture(parent, bash, 'service-unknown-' + service)
        f.auth(); f.flag('service', service)
        result = f.run('--trigger')
        yes(result.returncode != 0 and f.status()['rescue_error'] == 'clear_unknown', 'uncertain service discovery stops recovery: ' + service)
        yes(not f.log.exists(), 'uncertain service discovery sends no PM request: ' + service)
        yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'uncertain service discovery never performs raw deletion: ' + service)
        yes(not f.data.joinpath('boot-rescue-pending').exists(), 'uncertain service discovery cannot schedule a raw retry: ' + service)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bash', default=shutil.which('bash') or 'C:/Program Files/Git/bin/bash.exe')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/c17-dev/boot-rescue-checks.json')
    parser.add_argument('--final-smoke', action='store_true', help='Recheck final phase metadata and mocked native process guards only')
    parser.add_argument('--service-discovery', action='store_true', help='Check service-list parsing and uncertain discovery without device access')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='c17-rescue-') as temporary:
        parent = Path(temporary)
        def fixture(name): return Fixture(parent, args.bash, name)
        if args.final_smoke:
            final_smoke(parent, args.bash)
            print('BootRescueFinalSmoke:', CHECKS, 'checks passed (isolated metadata/process seams)')
            return
        if args.service_discovery:
            service_discovery(parent, args.bash)
            print('BootRescueServiceDiscovery:', CHECKS, 'checks passed (isolated PM discovery)')
            return

        service_discovery(parent, args.bash)

        f = fixture('default-status')
        yes(f.status() == {'rescue_enabled': '1', 'rescue_state': 'idle', 'rescue_error': 'none', 'rescue_time': '0'}, 'default enabled, no imaginary watcher')
        yes(f.run('--disable').returncode == 0 and f.status()['rescue_enabled'] == '0', 'disable independent recovery')
        yes(f.run('--enable').returncode == 0 and f.status()['rescue_enabled'] == '1', 'reenable does not clear')
        yes(f.roots[0].joinpath('settings/sentinel').exists(), 'toggle never resets data')
        yes(f.run('--bogus').returncode != 0, 'no arbitrary action')
        yes(f.run('--trigger', '/somewhere').returncode != 0, 'no caller path accepted')
        f.data.rmdir()
        yes(f.status()['rescue_state'] == 'idle' and not f.data.exists(), 'status does not create missing root state')

        f = fixture('no-auth')
        yes(f.run('--trigger').returncode != 0, 'direct trigger without native marker rejected')
        yes(f.status()['rescue_error'] == 'authorization_rejected', 'native authorization failure explicit')
        yes(f.roots[0].joinpath('.hidden').exists(), 'unauthorized data preserved')

        f = fixture('after-boot')
        f.auth(); f.flag('booted', '1')
        yes(f.run('--trigger').returncode != 0 and f.status()['rescue_error'] == 'boot_gate_rejected', 'completed boot gate')
        yes(f.roots[0].joinpath('.hidden').exists(), 'late key sequence cannot clear')

        f = fixture('animation-stopped')
        f.auth(); f.flag('bootanim', 'stopped')
        yes(f.run('--trigger').returncode != 0, 'only boot animation running accepts trigger')

        f = fixture('raw-complete')
        f.auth(); inodes = [p.stat().st_ino for p in f.roots]
        r = f.run('--trigger')
        yes(r.returncode == 0, 'fallback reset completes: ' + r.stderr)
        yes(f.status()['rescue_state'] == 'cleared', 'fallback reports completed')
        f.clean()
        yes([p.stat().st_ino for p in f.roots] == inodes, 'all root inodes retained')
        f.roots[0].joinpath('new-setting').write_text('after reset')
        yes(f.run('--trigger').returncode == 0 and f.roots[0].joinpath('new-setting').exists(), 'once-per-boot claim prevents repeated clearing')

        f = fixture('pm-success')
        f.auth(); f.flag('service', '1'); f.flag('pm', 'success')
        yes(f.run('--trigger').returncode == 0, 'PM explicit Success accepted')
        f.clean(); yes('pm clear --user 0 com.android.systemui' in f.log.read_text(), 'exact fixed PM request')

        f = fixture('pm-rejected')
        f.auth(); f.flag('service', '1')
        yes(f.run('--trigger').returncode == 0, 'definite protected-package refusal permits guarded fallback')
        f.clean()

        f = fixture('pm-unknown')
        f.auth(); f.flag('service', '1'); f.flag('pm', 'unknown')
        yes(f.run('--trigger').returncode != 0, 'PM timeout is not success')
        yes(f.status()['rescue_error'] == 'clear_unknown', 'PM unknown recorded separately')
        yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'no raw deletion races unknown PM work')
        yes(not f.data.joinpath('boot-rescue-pending').exists(), 'unknown PM is not pending raw retry')
        for response in ('empty', 'transport', 'misleading'):
            f = fixture('pm-ambiguous-' + response)
            f.auth(); f.flag('service', '1'); f.flag('pm', response)
            yes(f.run('--trigger').returncode != 0 and f.status()['rescue_error'] == 'clear_unknown', 'ambiguous PM reply is unknown: ' + response)
            yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'ambiguous reply never falls back: ' + response)

        f = fixture('locked-then-unlocked')
        f.auth(); f.flag('unlocked', 'false')
        yes(f.run('--trigger').returncode == 0, 'early DE stage accepted')
        yes(not list(f.roots[0].iterdir()) and f.roots[1].joinpath('.hidden').exists(), 'locked clears only DE')
        yes(f.status()['rescue_state'] == 'waiting_unlock', 'not complete while CE locked')
        f.roots[0].joinpath('new-setting').write_text('do not clear twice')
        f.flag('unlocked', 'true')
        yes(f.run('--resume').returncode == 0, 'unlocked continuation completes')
        yes(f.roots[0].joinpath('new-setting').exists(), 'continuation does not clear freshly recreated DE')
        yes(not list(f.roots[1].iterdir()) and not list(f.roots[2].iterdir()), 'continuation clears CE and external')
        yes(f.status()['rescue_state'] == 'cleared', 'full completion only after unlocked')

        f = fixture('pm-success-locked')
        f.auth(); f.flag('service', '1'); f.flag('pm', 'success'); f.flag('unlocked', 'false')
        yes(f.run('--trigger').returncode == 0 and f.status()['rescue_state'] == 'waiting_unlock', 'PM Success while locked still incomplete')
        f.roots[0].joinpath('new-setting').write_text('preserve')
        f.flag('unlocked', 'true')
        yes(f.run('--resume').returncode == 0 and f.roots[0].joinpath('new-setting').exists(), 'PM pending never repeats DE')

        f = fixture('locked-timeout')
        f.auth(); f.flag('unlocked', 'false'); f.run('--trigger')
        yes(f.run('--resume').returncode != 0, 'bounded waiting expires')
        yes(f.status()['rescue_state'] == 'failed' and f.status()['rescue_error'] == 'user_locked', 'timeout visible as incomplete')
        yes(f.roots[1].joinpath('.hidden').exists(), 'timeout does not force encrypted directory clearing')

        f = fixture('pending-disabled')
        f.auth(); f.flag('unlocked', 'false'); f.run('--trigger'); f.run('--disable'); f.flag('unlocked', 'true')
        yes(f.run('--resume').returncode == 0 and f.status()['rescue_state'] == 'cancelled', 'disabled pending does not continue')
        yes(f.roots[1].joinpath('.hidden').exists(), 'disabled pending preserves CE')

        f = fixture('pending-next-boot')
        f.auth(); f.flag('unlocked', 'false'); f.run('--trigger')
        f.proc.joinpath('sys/kernel/random/boot_id').write_bytes(b'22222222-2222-4333-8444-555555555555\n')
        yes(f.run('--resume').returncode != 0 and f.status()['rescue_error'] == 'pending_expired', 'old authorization never crosses boot')

        f = fixture('wrong-owner')
        f.auth(); f.path.joinpath('uid').write_text('10161\n')
        yes(f.run('--trigger').returncode != 0 and f.status()['rescue_error'] == 'path_rejected', 'wrong private data owner stops deletion')
        yes(f.roots[0].joinpath('.hidden').exists(), 'foreign-owned private root remains')

        f = fixture('no-identity')
        f.auth(); f.path.joinpath('data/system/packages.list').write_text('com.other 10161 0 /x default 0\n')
        yes(f.run('--trigger').returncode != 0 and f.status()['rescue_error'] == 'package_unavailable', 'unknown exact package identity rejects reset')

        f = fixture('mounted-child')
        f.auth(); mount = f.posix + '/data/user_de/0/com.android.systemui/settings'
        f.proc.joinpath('self/mountinfo').write_text('1 0 1:1 / ' + mount + ' rw - ext4 /dev/mock rw\n')
        yes(f.run('--trigger').returncode != 0, 'same-device nested bind mount rejected')
        yes(f.roots[0].joinpath('settings/sentinel').exists(), 'mounted subtree preserved')

        f = fixture('external-late')
        f.auth(); external = f.path / 'data/media/0/Android/data'
        external.rename(external.with_name('data-unmounted'))
        yes(f.run('--trigger').returncode == 0 and f.status()['rescue_error'] == 'external_unavailable', 'missing emulated storage defers')
        f.roots[1].joinpath('after-ce').write_text('do not clear CE twice')
        external.with_name('data-unmounted').rename(external)
        yes(f.run('--resume').returncode == 0, 'external late mount continuation')
        yes(f.roots[1].joinpath('after-ce').exists(), 'external-only continuation preserves new CE data')
        yes(not list(f.roots[2].iterdir()), 'external-only stage clears exact external package')

        for stage in ('ce_in_progress', 'external_in_progress'):
            f = fixture('interrupted-' + stage)
            f.auth(); (f.data / ('boot-rescue-claim.' + BOOT)).mkdir()
            f.data.joinpath('boot-rescue-pending').write_bytes((BOOT + '\n10160\n' + stage + '\n').encode())
            yes(f.run('--resume').returncode != 0 and f.status()['rescue_error'] == 'clear_unknown', 'interrupted destructive phase is not replayed: ' + stage)
            yes(all(p.joinpath('.hidden').exists() for p in f.roots), 'interrupted phase preserves newly recreated files: ' + stage)

        # Exercise real links/reparse points rather than Windows shortcut files.
        f = fixture('linked-child')
        f.auth()
        f.link(f.other, f.roots[0] / 'linked')
        yes(f.run('--trigger').returncode == 0, 'nested symlink removed without following target')
        f.safe()

        f = fixture('linked-root')
        f.auth(); original = f.roots[0]; original.rename(original.with_name('original-systemui'))
        f.link(f.other, original)
        yes(f.run('--trigger').returncode != 0, 'root symlink rejected before raw removal')
        f.safe()

        f = fixture('broken-disable-link')
        target = f.path / 'removed-target'
        target.mkdir(); f.link(target, f.data / 'boot-rescue-disabled'); target.rmdir()
        yes(f.run('--status').returncode != 0, 'broken disabled link is rejected, not treated as enabled')
        yes(f.roots[0].joinpath('.hidden').exists(), 'broken control link never triggers cleanup')

        f = fixture('linked-status')
        f.link(f.other, f.data / 'boot-rescue-status')
        yes(f.run('--status').returncode != 0, 'status never follows a linked state file')

        f = fixture('aosp-ce-alias')
        f.auth(); original_user = f.roots[1].parent
        target = f.path / 'data/data'; original_user.rename(target)
        f.link(target, original_user)
        yes(f.run('--trigger').returncode == 0, 'one canonical AOSP user-0 alias is accepted')
        yes(not list((target / 'com.android.systemui').iterdir()), 'canonical alias CE contents cleared')
        f.safe()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {'checks': CHECKS, 'passed': True, 'production': str(SOURCE), 'scope': 'rewritten temp root only; no device or real package clearing'}
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('BootRescueCheck:', CHECKS, 'checks passed (isolated production shell)')


if __name__ == '__main__':
    main()
