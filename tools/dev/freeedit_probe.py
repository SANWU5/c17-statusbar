"""Capture bounded, read-only free-edit state from an explicitly selected phone.

Requires an enabled API2 free-edit plugin and the C17 root admin provider. Does
not install, restart, tap, unlock, write preferences, or alter native drag state.
Outputs stay in the ignored .local-private directory; do not publish raw captures.
"""
from pathlib import Path
import argparse
import datetime
import json
import os
import shlex
import shutil
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
PLUGIN_ID = 'dev.c17.validation.qs-card'


def summarize_runtime(status, plugin):
    # Do not persist other plugins, exception messages, stacks or arbitrary strings.
    return {
        'build_token': status.get('build_token'),
        'pid': status.get('pid'),
        'health': {key: value for key, value in status.get('health', {}).items()
                   if key.startswith('custom_plugin_') and isinstance(value, (bool, int, float))},
        'plugin': {key: plugin.get(key) for key in ('id', 'versionCode', 'stage', 'hooks', 'assetFailures')},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Exact physical device serial from adb devices -l')
    parser.add_argument('--adb', help='adb executable; defaults to the local toolchain or PATH')
    parser.add_argument('--samples', type=int, default=3, choices=range(1, 11), metavar='1..10')
    args = parser.parse_args()
    local_adb = ROOT / '.local-toolchain/sdk/platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
    executable = args.adb or (str(local_adb) if local_adb.is_file() else shutil.which('adb'))
    if not executable:
        parser.error('adb is unavailable; pass --adb or install platform-tools')
    if args.serial.startswith('emulator-'):
        parser.error('Select the physical target phone; the unrelated emulator is not a valid device for this probe')

    def adb(*command):
        process = subprocess.run([executable, '-s', args.serial, *command], capture_output=True, timeout=30)
        if process.returncode:
            raise RuntimeError(process.stderr.decode('utf-8', 'replace').strip() or 'adb failed')
        return process.stdout.decode('utf-8', 'replace')

    if adb('get-state').strip() != 'device':
        raise RuntimeError('Selected phone is not online and authorized')

    def admin(request):
        command = ('content call --uri content://dev.puitheme.iosstatusbar.settings '
                   '--method custom_plugin_admin --arg ' + shlex.quote(json.dumps(request, separators=(',', ':'))))
        raw = adb('shell', 'su -c ' + shlex.quote(command))
        if 'result=' not in raw:
            raise RuntimeError('C17 admin provider is unavailable. Open C17 on the phone, then rerun the probe.')
        return json.JSONDecoder().raw_decode(raw.split('result=', 1)[1])[0]

    def runtime(request):
        pending = admin(request)
        if 'nonce' not in pending:
            raise RuntimeError('Runtime did not accept the diagnostic request')
        for _ in range(30):
            response = admin({'action': 'eval_result', 'nonce': pending['nonce']})
            if not response.get('pending'):
                if 'error' in response:
                    raise RuntimeError(str(response['error']))
                value = response.get('result')
                if isinstance(value, str):
                    try:
                        return json.loads(value)
                    except json.JSONDecodeError:
                        pass
                return value
            time.sleep(.25)
        raise RuntimeError('Runtime diagnostic timed out; no phone state was changed')

    status = runtime({'action': 'runtime_status'})
    plugin = next((item for item in status.get('plugins', []) if item.get('id') == PLUGIN_ID), None)
    if not plugin or plugin.get('stage') != 'running':
        raise RuntimeError('Free-edit plugin is not running; this probe will not enable it')
    source = Path(__file__).with_suffix('.js').read_text(encoding='utf-8').strip().rstrip(';')
    variable = '__c17ReadOnlyProbe_' + uuid.uuid4().hex
    output = ROOT / '.local-private' / ('freeedit-probe-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + variable[-8:])
    output.mkdir(parents=True, exist_ok=False)
    report = {'capturedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'runtime': summarize_runtime(status, plugin), 'samples': []}
    try:
        for _ in range(args.samples):
            schedule = ('var ' + variable + '=null;C17.runOnMain(function(){try{' + variable + '=' + source
                        + ';}catch(e){' + variable + '=JSON.stringify({error:String(e)});}});\'scheduled\';')
            runtime({'action': 'eval', 'id': PLUGIN_ID, 'source': schedule})
            for _ in range(20):
                snapshot = runtime({'action': 'eval', 'id': PLUGIN_ID, 'source': variable + '===null?"pending":' + variable + ';'})
                if snapshot != 'pending':
                    report['samples'].append(snapshot)
                    if not isinstance(snapshot, dict) or snapshot.get('schema') != 1:
                        raise RuntimeError('Snapshot was unavailable; see the local report for the returned diagnostic error')
                    break
                time.sleep(.1)
            else:
                raise RuntimeError('UI thread did not return a diagnostic snapshot')
            time.sleep(.4)
    finally:
        # Release only our diagnostic global; no UI object or lifecycle state is modified.
        try:
            runtime({'action': 'eval', 'id': PLUGIN_ID, 'source': variable + '=undefined;"released";'})
        except (RuntimeError, subprocess.SubprocessError):
            pass
        (output / 'snapshot.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Captured {len(report['samples'])} snapshots: {output / 'snapshot.json'}")


if __name__ == '__main__':
    main()
