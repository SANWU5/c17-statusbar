#!/usr/bin/env python3
"""Build the auditable companion daemon from source, using a pinned Android NDK."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
NDK_VERSION = "29.0.14206865"
TARGETS = {"arm64-v8a": "aarch64-linux-android26", "armeabi-v7a": "armv7a-linux-androideabi26",
           "x86_64": "x86_64-linux-android26", "x86": "i686-linux-android26"}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--abi", choices=TARGETS)
    args = parser.parse_args()
    sdk = Path(os.environ.get("C17_SDK") or os.environ.get("ANDROID_HOME") or ROOT / ".local-toolchain/sdk")
    ndk = Path(os.environ.get("C17_NDK") or os.environ.get("ANDROID_NDK_HOME") or sdk / "ndk" / NDK_VERSION)
    host = "windows-x86_64" if os.name == "nt" else "darwin-x86_64" if os.uname().sysname == "Darwin" else "linux-x86_64"
    compiler = ndk / "toolchains/llvm/prebuilt" / host / "bin" / ("clang++.exe" if os.name == "nt" else "clang++")
    if not compiler.is_file():
        raise SystemExit(f"Install ndk;{NDK_VERSION} or set C17_NDK")
    output = ROOT / "build/generated/c17-performance-assets/performance-module/bin"
    output.mkdir(parents=True, exist_ok=True)
    for abi, target in TARGETS.items():
        if args.abi and abi != args.abi:
            continue
        subprocess.run([str(compiler), "--target=" + target, "-std=c++17", "-O2", "-fPIE", "-pie",
                        "-Wall", "-Wextra", "-Werror", "-Wno-misleading-indentation", "-static-libstdc++",
                        "-fstack-protector-strong", "-D_FORTIFY_SOURCE=2", "-Wl,-z,relro,-z,now,-z,max-page-size=16384",
                        "-Wl,--build-id=sha1,--strip-all", str(ROOT / "tools/performance-cache/c17cached.cpp"),
                        "-landroid", "-o", str(output / ("c17cached-" + abi))], check=True)
        print("built", abi, (output / ("c17cached-" + abi)).stat().st_size)

if __name__ == "__main__":
    main()
