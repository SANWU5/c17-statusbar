#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Put back example lists the program matches literally, where a translator localized them.

    python tools/i18n/fix_example_lists.py <language> [...]

The status icon priority field accepts the Chinese icon names or the slot names (bluetooth, location, …), so the help
text's example "蓝牙,定位,闹钟" must stay Chinese in every language. This rewrites the translation in the language's work
table (build/i18n-work/<language>.json) when the example list there is not the original; run merge.py afterwards.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare import WORK  # noqa: E402

EXAMPLE = "蓝牙,定位,闹钟"
LIST = re.compile(r"[^\s,，:：;；.。()（）]+(?:\s*[,，]\s*[^\s,，:：;；.。()（）]+){2}")
SLOTS = re.compile(r"^[a-z_]+(?:\s*,\s*[a-z_]+){2}$")


def restore(source, translated):
    """The translation with the program's example list put back, or unchanged."""
    if EXAMPLE not in source or not isinstance(translated, str) or EXAMPLE in translated:
        return translated
    match = next((m for m in LIST.finditer(translated) if not SLOTS.match(m.group())), None)
    return translated if match is None else translated[:match.start()] + EXAMPLE + translated[match.end():]


def fix(language):
    work_file = WORK / f"{language}.json"
    if not work_file.is_file():
        print(f"{language}: no work table")
        return
    work = json.loads(work_file.read_text(encoding="utf-8"))
    ids = json.loads((WORK / "ids.json").read_text(encoding="utf-8"))
    changed = 0
    for key, source in ids.items():
        if EXAMPLE not in source or key not in work or EXAMPLE in work[key]:
            continue
        translated = work[key]
        match = next((m for m in LIST.finditer(translated) if not SLOTS.match(m.group())), None)
        if not match:
            print(f"{language} {key}: list not found, fix by hand: {ascii(translated)}")
            continue
        work[key] = translated[:match.start()] + EXAMPLE + translated[match.end():]
        print(f"{language} {key}: {ascii(match.group())} -> {EXAMPLE.encode('unicode_escape').decode()}")
        changed += 1
    work_file.write_text(json.dumps(work, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    print(f"{language}: {changed} fixed")


if __name__ == "__main__":
    for name in sys.argv[1:]:
        fix(name)
