#!/usr/bin/env python3
"""Run the real early boot/service entry scripts against isolated, harmless processes."""
from pathlib import Path
import os
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/performance-module'
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else '/bin/bash'
checks = 0

def check(value, message):
    global checks
    checks += 1
    if not value: raise AssertionError(message)

with tempfile.TemporaryDirectory(prefix='c17-boot-rescue-entry-') as directory:
    base = Path(directory)
    posix = subprocess.check_output([BASH, '-c', 'cygpath -u "$1"', 'fixture', str(base)], text=True).strip() if os.name == 'nt' else str(base)
    module = base / 'data/adb/modules/c17_performance'
    module.mkdir(parents=True)
    fakebin = base / 'bin'
    fakebin.mkdir()
    log = base / 'operations'
    for name in ('post-fs-data.sh', 'service.sh'):
        text = (ASSETS / name).read_text(encoding='utf8').replace('/data/adb', posix + '/data/adb')
        (module / name).write_text(text, encoding='utf8', newline='\n')
    (module / 'boot-rescue.sh').write_text('#!/bin/sh\nprintf "rescue %s\\n" "$1" >> "$C17_TEST_LOG"\n/usr/bin/sleep 1\n', encoding='utf8', newline='\n')
    (module / 'common.sh').write_text('#!/bin/sh\nprintf "common %s\\n" "$1" >> "$C17_TEST_LOG"\n', encoding='utf8', newline='\n')
    (fakebin / 'stat').write_text('#!/bin/sh\ncase "$2" in %u) echo 0;; %a) echo 755;; *) exit 1;; esac\n', encoding='utf8', newline='\n')
    (fakebin / 'content').write_text('#!/bin/sh\necho provider >> "$C17_TEST_LOG"\n', encoding='utf8', newline='\n')
    environment = os.environ.copy()
    environment.update(PATH=posix + '/bin:/usr/bin:/bin', C17_TEST_LOG=posix + '/operations', MSYS='winsymlinks:nativestrict')
    subprocess.run([BASH, '-c', 'chmod 755 "$1"/*', 'fixture', posix + '/bin'], check=True)
    def execute(name):
        started = time.monotonic()
        result = subprocess.run([BASH, '-c', 'export PATH="$1/bin:/usr/bin:/bin"; shift; exec bash "$@"', 'fixture', posix,
                                 posix + '/data/adb/modules/c17_performance/' + name], env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=4)
        if result.returncode: print(result.stderr.decode('utf8', 'replace'))
        return result, time.monotonic() - started
    result, elapsed = execute('post-fs-data.sh')
    check(result.returncode == 0, 'early boot script succeeds')
    check(elapsed < .9, 'early boot listener does not block on worker')
    time.sleep(.2)
    check(log.read_text().splitlines() == ['rescue --start'], 'early boot only starts the fixed listener')
    time.sleep(1)
    log.write_text('')
    (module / 'disable').touch()
    result, elapsed = execute('post-fs-data.sh')
    time.sleep(.2)
    check(result.returncode == 0 and not log.read_text(), 'disabled module never starts early listener')
    (module / 'disable').unlink()
    result, elapsed = execute('service.sh')
    check(result.returncode == 0 and elapsed < .9, 'service fallback does not block on recovery worker')
    time.sleep(.2)
    lines = log.read_text().splitlines()
    check(lines.count('rescue --start') == 1 and lines.count('rescue --resume') == 1, 'service starts watcher and authorized pending resume independently')
    check('provider' in lines and 'common boot' in lines, 'existing scheduler/ART boot remains reachable')
    check(not any('--trigger' in line for line in lines), 'boot entry points never fabricate a key authorization')
    time.sleep(1)
print(f'boot-rescue boot integration: {checks} checks passed')
