#!/usr/bin/env python3
"""Run the production host material-ownership policy against opposite saved configurations."""
import locale
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
out = root / '.local-private/plugin-independence-20261104/isolation-check'
out.mkdir(parents=True, exist_ok=True)
jdk = root / '.local-toolchain/jdk/jdk-17.0.20.1+1/bin'
android = root / '.local-toolchain/sdk/platforms/android-37.0/android.jar'
json = root / 'validation/test-libs/json-20240303.jar'
cp = os.pathsep.join(map(str, (out, json, android)))
sources = [root / 'app/src/main/java/dev/puitheme/CustomPluginMaterialIsolation.java',
           root / 'validation/checks-src/dev/puitheme/CustomPluginMaterialIsolationCheck.java',
           root / 'validation/checks-src/android/os/Bundle.java']
args = ['--release', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', str(out), *map(str, sources)]
argfile = out / 'sources.args'
argfile.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args), encoding=locale.getencoding())
subprocess.run([str(jdk / 'javac.exe'), '@' + str(argfile)], check=True, cwd=root)
subprocess.run([str(jdk / 'java.exe'), '-cp', cp, 'dev.puitheme.CustomPluginMaterialIsolationCheck'], check=True, cwd=root)
