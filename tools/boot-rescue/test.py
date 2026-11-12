#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Execute the production Sequence unchanged as WASM. No Android or cleanup code runs."""
from pathlib import Path
import argparse,json,subprocess,sys
from build import compiler, ROOT

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',type=Path,default=ROOT/'build/c17-dev/boot-rescue-native-test')
a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
wasm=out/'sequence.wasm'
subprocess.run([str(compiler()),'--target=wasm32-unknown-unknown','-std=c++17','-O2','-ffreestanding',
    '-fno-exceptions','-fno-rtti','-nostdlib','-Wl,--no-entry','-Wl,--export=reset','-Wl,--export=input',
    '-Wl,--export=dropped','-Wl,--export=stage','-Wl,--export=probeReset','-Wl,--export=probeObserve',
    '-Wl,--export=probeReadable','-Wl,--export=probeUsable','-Wl,--export=probeCoverage',str(Path(__file__).parent/'state-test.cpp'),'-o',str(wasm)],check=True)
subprocess.run(['node',str(Path(__file__).parent/'test.cjs'),str(wasm)],check=True)
