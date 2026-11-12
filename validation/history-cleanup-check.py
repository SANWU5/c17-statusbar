#!/usr/bin/env python3
"""Execute destructive maintenance only in isolated directories with a fake package manager."""
from pathlib import Path
import importlib.util
import subprocess
import tempfile
import sys

spec = importlib.util.spec_from_file_location('perf_fixture', Path(__file__).with_name('performance-module-check.py'))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
base = Path(tempfile.mkdtemp(prefix='c17-history-cleanup-'))
bash = sys.argv[1]
checks = 0
def check(value, label):
    global checks
    checks += 1
    assert value, label

def make(name):
    f = fixture.Fixture(base, bash, name)
    script = (fixture.ROOT / 'app/src/main/assets/history-cleanup.sh').read_text('utf-8')
    script = f.rewrite(script).replace('/data/user_de', f.posix + '/data/user_de').replace('/data/user', f.posix + '/data/user')
    # Avoid double prefix substitution of user_de.
    script = script.replace(f.posix + f.posix, f.posix)
    (f.module / 'history.sh').write_text(script, encoding='utf-8', newline='\n')
    (f.bin / 'pidof').write_text('#!/bin/sh\nexit 1\n', encoding='utf-8', newline='\n')
    (f.bin / 'pm').write_text('#!/bin/sh\nprintf "pm %s\\n" "$*" >> "$C17_FIXTURE_LOG"\n[ "$C17_FIXTURE_CLEAR_FAIL" != 1 ] || exit 1\necho Success\n', encoding='utf-8', newline='\n')
    subprocess.run([bash, '-c', 'chmod 755 "$1"/bin/*', 'fixture', f.posix], check=True)
    for storage in ['user', 'user_de']:
        host = f.path / f'data/{storage}/0/com.android.systemui'
        for name in ['c17-resident-cache', 'c17_plugins', 'foreign-cache']:
            directory = host / 'files' / name; directory.mkdir(parents=True); (directory / 'proof').write_text('keep or delete')
        (host / 'shared_prefs').mkdir()
        for name in ['c17_plugin_freeedit_v2.xml', 'c17_qs_more_private.xml.bak', 'native.xml', 'declared_plugin.xml']:
            (host / 'shared_prefs' / name).write_text('fixture')
        (host / 'databases').mkdir(); (host / 'databases/native.db').write_text('native')
    return f

def run(f, *args, **env):
    return subprocess.run([bash, '-c', 'export PATH="$1/bin:/usr/bin:/bin"; shift; exec bash "$@"', 'fixture', f.posix,
                           f.posix + '/data/adb/modules/c17_performance/history.sh', *args], env=f.env(**env), capture_output=True, text=True, timeout=60)

f = make('success')
r = run(f, '0', 'declared_plugin')
check(r.returncode == 0, r.stdout + r.stderr)
for storage in ['user', 'user_de']:
    host = f.path / f'data/{storage}/0/com.android.systemui'
    check(not (host / 'files/c17_plugins').exists(), 'plugin data removed')
    check(not (host / 'files/c17-resident-cache').exists(), 'resident cache removed')
    check(not (host / 'shared_prefs/c17_plugin_freeedit_v2.xml').exists(), 'layout removed')
    check((host / 'shared_prefs/native.xml').exists() and (host / 'databases/native.db').exists(), 'native data untouched')
    check((host / 'files/foreign-cache/proof').exists(), 'unowned files untouched')
check(not (f.path / 'data/user_de/0/com.android.systemui/shared_prefs/declared_plugin.xml').exists(), 'declared plugin removed')
check((f.module / 'disable').exists(), 'addon stays disabled')
check('pm clear --user 0 dev.puitheme.iosstatusbar' in f.commands(), 'only this user and app cleared')
for name, user, args, flag, expected in [
    ('invalid-user', '../0', [], {}, 'invalid_user'),
    ('invalid-name', '0', ['../native'], {}, 'preference_name'),
    ('app-failure', '0', [], {'C17_FIXTURE_CLEAR_FAIL': '1'}, 'app_cleanup'),
    ('restore-failure', '0', [], {'C17_FIXTURE_NO_CANCEL': '1'}, 'performance_restore')]:
    f = make(name); r = run(f, user, *args, **flag)
    check(r.returncode != 0 and expected in r.stdout, name + r.stdout + r.stderr)
    if name != 'app-failure': check(not any(line.startswith('pm clear') for line in f.commands()), name + ' stops before app clearing')
f = make('link')
target = f.path / 'data/user_de/0/com.android.systemui/shared_prefs/c17_plugin_freeedit_v2.xml'
target.unlink(); outside = f.path / 'outside'; outside.write_text('preserve')
try:
    target.symlink_to(outside)
except OSError:
    # Git Bash represents test links itself on Windows without SeCreateSymbolicLink.
    subprocess.run([bash, '-c', 'ln -s "$1/outside" "$1/data/user_de/0/com.android.systemui/shared_prefs/c17_plugin_freeedit_v2.xml"', 'fixture', f.posix],
                   env={**f.env(), 'MSYS': 'winsymlinks'}, check=True)
r = run(f, '0')
check(r.returncode != 0 and 'host_path_link' in r.stdout, 'link rejected')
check(outside.read_text() == 'preserve' and not any(line.startswith('pm clear') for line in f.commands()), 'link target untouched')
print(f'history-cleanup: {checks} checks passed; isolated directories only: {base}')
