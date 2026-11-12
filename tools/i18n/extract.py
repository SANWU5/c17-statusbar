#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the list of interface text to translate.

    python tools/i18n/extract.py app                 -> tools/i18n/app-source.json
    python tools/i18n/extract.py plugin <plugin dir> -> <plugin dir>/i18n-source.json

The source language is Simplified Chinese. A string is translated by exact match at display time
(C17Text.tr in the app, the plugin's i18n/<language>.json in the host), so the list holds whole strings:

* string literals of the sources that contain Chinese, unescaped as the program sees them;
* run-time text that only exists after code composes it: build/c17-dev/i18n/catalog.json (settings catalog) and
  build/c17-dev/i18n/plugin-<name>.json (plugin forms), written by tools/i18n/I18nDump.java.

Text that is data must keep its exact spelling and is left out: format templates the user types or saves
(（天气:{温度}）, M月d日 {周}), tokens, weather and time-of-day words drawn on the status bar, log and diagnostic
lines meant for the author, and the old, unused MainActivity.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from i18n_common import CJK, is_data  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DUMP = ROOT / "build/c17-dev/i18n"
LITERAL = re.compile(r'(?<![A-Za-z0-9_])"((?:[^"\\\n]|\\.)*)"')
RAW = re.compile(r'"""(.*?)"""', re.S)

# Whole files whose Chinese text is data, SystemUI-only output or author-facing logs.
APP_EXCLUDED_FILES = {
    "MainActivity.java",            # the old View interface; ModernMainActivity replaced it
    "TimeFormat.java",              # date tokens and the time-of-day words drawn on the status bar
    "WeatherLabels.java",           # weather words drawn on the status bar
    "BigClockTextContent.java",     # tokens of the big clock text formats
    "ComponentFormat.java",         # tokens of the component formats
    "ModuleDiagnostics.java",       # diagnostic events and redaction patterns
    "LunarCalendar.java", "LunarDate.java", "ChineseCalendar.java",
}
# Calls whose string arguments are logs, keys or patterns rather than text a reader sees.
SKIP_CALLS = re.compile(r"(?:\bLog\.[a-z]|\bXposedBridge\.log|\bModuleDiagnostics\.[a-z]+|\breport|\bdiag|\btrace|\bevent|"
                        r"\bmatches|\breplaceAll|\bsplit|\bcontains|\bstartsWith|\bendsWith|\bindexOf|\bequals|"
                        r"\bPattern\.compile|\bRegex)\s*\(\s*$")
ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\", "$": "$", "b": "\b"}


def unescape(body):
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "\\" and i + 1 < len(body):
            n = body[i + 1]
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", body[i + 2:i + 6] or ""):
                out.append(chr(int(body[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(ESCAPES.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def kotlin_template(body):
    return bool(re.search(r"(?<!\\)\$(?:\{|[A-Za-z_])", body))


def scan(path, found, skipped, rel):
    text = path.read_text(encoding="utf-8", errors="replace")
    in_values = None
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith(("//", "*", "/*", "import ", "package ")):
            continue
        decl = re.search(r"\b([A-Z][A-Z0-9_]*_VALUES|[A-Z][A-Z0-9_]*_PATTERNS|[A-Z][A-Z0-9_]*_TOKENS)\b\s*=", line)
        if decl:
            in_values = decl.group(1)
        for match in LITERAL.finditer(line):
            body = match.group(1)
            if not CJK.search(body):
                continue
            where = f"{rel}:{number}"
            before = line[:match.start()]
            if in_values:
                skipped.setdefault("values-array", []).append(where)
                continue
            if path.suffix == ".kt" and kotlin_template(body):
                skipped.setdefault("kotlin-template", []).append(f"{where} {body}")
                continue
            if SKIP_CALLS.search(before):
                skipped.setdefault("call", []).append(where)
                continue
            value = unescape(body)
            if value.startswith("}") or "${" in value:
                skipped.setdefault("template-piece", []).append(where)
                continue
            if is_data(value):
                skipped.setdefault("data", []).append(f"{where} {value}")
                continue
            found.setdefault(value, []).append(where)
        if in_values and ("};" in line or ")" in line and line.rstrip().endswith(";")):
            in_values = None


def merge_dump(found, dump_file, label):
    if not dump_file.is_file():
        raise SystemExit(f"missing {dump_file}: run tools/i18n/dump.ps1 first")
    for text, places in json.loads(dump_file.read_text(encoding="utf-8")).items():
        if is_data(text):
            continue
        found.setdefault(text, []).extend(p if p.startswith(label) else f"{label}:{p}" for p in places[:3])


def write(found, skipped, out):
    entries = [{"text": text, "refs": sorted(set(refs))[:6]} for text, refs in found.items()]
    entries.sort(key=lambda e: e["refs"][0])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"source": "zh-Hans", "count": len(entries), "strings": entries},
                              ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    report = {k: len(v) for k, v in skipped.items()}
    print(json.dumps({"out": str(out.relative_to(ROOT)), "strings": len(entries),
                      "chars": sum(len(e["text"]) for e in entries), "skipped": report}, ensure_ascii=False))
    (DUMP / (out.stem + "-skipped.json")).write_text(json.dumps(skipped, ensure_ascii=False, indent=1), encoding="utf-8")


def app():
    found, skipped = {}, {}
    base = ROOT / "app/src/main/java"
    for path in sorted(base.rglob("*")):
        if path.suffix not in (".kt", ".java") or path.name in APP_EXCLUDED_FILES:
            continue
        scan(path, found, skipped, path.relative_to(base).as_posix().replace("dev/puitheme/", ""))
    merge_dump(found, DUMP / "catalog.json", "catalog")
    write(found, skipped, ROOT / "tools/i18n/app-source.json")


def plugin(directory):
    directory = (ROOT / directory).resolve()
    found, skipped = {}, {}
    for name in ("main.js",):
        scan(directory / name, found, skipped, name)
    merge_dump(found, DUMP / f"plugin-{directory.name}.json", "form")
    manifest = json.loads((directory / "plugin.json").read_text(encoding="utf-8"))
    for field in ("name", "description", "usage"):
        if CJK.search(manifest.get(field, "")):
            found.setdefault(manifest[field], []).append(f"plugin.json:{field}")
    write(found, skipped, directory / "i18n-source.json")


if __name__ == "__main__":
    if sys.argv[1:] == ["app"]:
        app()
    elif len(sys.argv) == 3 and sys.argv[1] == "plugin":
        plugin(sys.argv[2])
    else:
        raise SystemExit(__doc__)
