#!/usr/bin/env python3
"""Verify and export exactly the companion resources shipped by the selected APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def elf(data, abi):
    assert data[:4] == b"\x7fELF" and data[5] == 1, "invalid native ELF"
    machine = struct.unpack_from("<H", data, 18)[0]
    assert machine == {"arm64-v8a": 183, "armeabi-v7a": 40, "x86_64": 62, "x86": 3}[abi]
    wide = data[4] == 2
    offset = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)[0]
    step, count = struct.unpack_from("<HH", data, 54 if wide else 42)
    loads = 0
    for index in range(count):
        entry = offset + step * index
        if struct.unpack_from("<I", data, entry)[0] != 1:
            continue
        loads += 1
        alignment = struct.unpack_from("<Q" if wide else "<I", data, entry + (48 if wide else 28))[0]
        assert alignment >= 16384, "native segment lacks 16 KB alignment"
    assert loads, "no native load segments"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = (ROOT / "app/src/main/java/dev/puitheme/RootPerformanceModule.java").read_text(encoding="utf-8")
    names = re.findall(r'"([a-zA-Z0-9_./-]+)"', source.split("String[] ASSETS =", 1)[1].split("};", 1)[0])
    assert len(names) == len(set(names)) == 22, names
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.apk) as apk, zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as out:
        assert apk.testzip() is None, "APK CRC"
        packaged = {name.removeprefix("assets/performance-module/") for name in apk.namelist()
                    if name.startswith("assets/performance-module/") and not name.endswith("/")}
        assert packaged == set(names), "APK/module exporter asset mismatch"
        for name in names:
            data = apk.read("assets/performance-module/" + name)
            generated = name.startswith("bin/")
            expected = (ROOT / ("build/generated/c17-performance-assets/performance-module" if generated
                                else "app/src/main/assets/performance-module") / name).read_bytes()
            assert data == expected, "APK contains stale source: " + name
            if generated:
                abi = re.sub(r'^bin/c17(?:cached|bootrescue)-', '', name)
                elf(data, abi)
                assert len(data) <= 4 * 1024 * 1024
            else:
                assert len(data) <= 262144
                if name.endswith(".sh"):
                    assert b"\r" not in data, "shell requires LF: " + name
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = (0o100755 if generated or name.endswith(".sh") else 0o100644) << 16
            out.writestr(entry, data)
    print(json.dumps({"module": str(args.output.resolve()), "assets": len(names), "abi": 4,
                      "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
                      "bytes": args.output.stat().st_size}, ensure_ascii=False))


if __name__ == "__main__":
    main()
