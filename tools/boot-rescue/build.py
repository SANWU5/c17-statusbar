#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build only the auditable fixed-purpose Android boot-key watcher."""
import argparse, os, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NDK_VERSION = '29.0.14206865'
TARGETS = {'arm64-v8a':'aarch64-linux-android26', 'armeabi-v7a':'armv7a-linux-androideabi26',
           'x86_64':'x86_64-linux-android26', 'x86':'i686-linux-android26'}

def compiler():
    sdk = Path(os.environ.get('C17_SDK') or os.environ.get('ANDROID_HOME') or ROOT/'.local-toolchain/sdk')
    ndk = Path(os.environ.get('C17_NDK') or os.environ.get('ANDROID_NDK_HOME') or sdk/'ndk'/NDK_VERSION)
    host = 'windows-x86_64' if os.name=='nt' else 'darwin-x86_64' if os.uname().sysname=='Darwin' else 'linux-x86_64'
    result = ndk/'toolchains/llvm/prebuilt'/host/'bin'/('clang++.exe' if os.name=='nt' else 'clang++')
    if not result.is_file(): raise SystemExit('Install ndk;'+NDK_VERSION+' or set C17_NDK')
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--abi',choices=TARGETS)
    args=p.parse_args(); cc=compiler()
    out=ROOT/'build/generated/c17-performance-assets/performance-module/bin';out.mkdir(parents=True,exist_ok=True)
    for abi,target in TARGETS.items():
        if args.abi and abi!=args.abi:continue
        dest=out/('c17bootrescue-'+abi)
        subprocess.run([str(cc),'--target='+target,'-std=c++17','-O2','-fPIE','-pie','-Wall','-Wextra','-Werror',
            '-static-libstdc++','-fstack-protector-strong','-D_FORTIFY_SOURCE=2',
            '-Wl,-z,relro,-z,now,-z,max-page-size=16384','-Wl,--build-id=sha1,--strip-all',
            str(ROOT/'tools/boot-rescue/c17bootrescue.cpp'),'-o',str(dest)],check=True)
        print('built',abi,dest.stat().st_size)

if __name__=='__main__':main()
