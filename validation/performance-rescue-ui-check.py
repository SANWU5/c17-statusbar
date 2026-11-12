#!/usr/bin/env python3
"""Exercise real fixed UI dispatch in a temporary root, with a harmless rescue backend contract."""
import argparse
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('performance_fixture',ROOT/'validation/performance-module-check.py')
fixture=importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bash',default=shutil.which('bash') or 'C:/Program Files/Git/bin/bash.exe')
    parser.add_argument('--java')
    parser.add_argument('--classpath')
    args=parser.parse_args()
    base=Path(tempfile.mkdtemp(prefix='c17-rescue-ui-'))
    test=fixture.Fixture(base,args.bash,'dispatch')
    test.data.mkdir(parents=True)
    backend=test.module/'boot-rescue.sh'
    backend.write_text('''#!/bin/sh
case "$1" in
 --status) printf 'rescue_enabled=1\\nrescue_state=waiting_unlock\\nrescue_error=user_locked\\nrescue_time=1793850000\\n';;
 --enable|--disable) printf 'rescue %s\\n' "$1" >> "$C17_FIXTURE_LOG";;
 *) printf 'UNEXPECTED %s\\n' "$1" >> "$C17_FIXTURE_LOG";exit 99;;
esac
''',encoding='utf8',newline='\n')
    subprocess.run([args.bash,'-c','chmod 755 "$1"','fixture',test.posix+'/data/adb/modules/c17_performance/boot-rescue.sh'],check=True)
    checks=0
    # Unavailable ART locking must not prevent changing the independent rescue preference.
    for mode in ('rescue-enable','rescue-disable'):
        result=test.run('webui.sh',mode,C17_FIXTURE_NO_FLOCK='1')
        assert result.returncode==0,(mode,result.stdout,result.stderr)
        assert test.commands()[-1]=='rescue --'+mode.removeprefix('rescue-'),test.commands()
        assert all('--trigger' not in line and 'package clear' not in line for line in test.commands())
        checks+=3
    result=test.run('webui.sh','status')
    assert result.returncode==0,result.stderr
    assert result.stdout.count('C17_WEB:begin')==result.stdout.count('C17_WEB:end')==1
    assert 'rescue_state=waiting_unlock\n' in result.stdout
    assert 'rescue_error=user_locked\n' in result.stdout
    checks+=4
    # A missing backend is never replaced with another command or fallback operation.
    backend.unlink()
    result=test.run('webui.sh','rescue-enable')
    assert result.returncode!=0
    checks+=1
    if args.java and args.classpath:
        generated=base/'generated';generated.mkdir()
        subprocess.run([args.java,'-cp',args.classpath,'dev.puitheme.RootPerformanceModuleCheck',str(generated)],check=True)
        for mode in ('RESCUE_ENABLE','RESCUE_DISABLE'):
            target=test.module/(mode+'.sh')
            target.write_text(test.rewrite((generated/(mode+'.sh')).read_text(encoding='utf8')),encoding='utf8',newline='\n')
            # The missing backend remains blocked by the production command trust check.
            result=test.run(mode+'.sh',None)
            assert result.returncode!=0,(mode,result.stdout,result.stderr)
            assert 'C17_DONE:0' not in result.stdout
            checks+=2
    print(f'Rescue UI fixed dispatch: {checks} checks passed; temporary fixture only ({base}).')

if __name__=='__main__':main()
