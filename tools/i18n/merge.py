#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Merge translated chunks into the tables the app and the plugins ship.

    python tools/i18n/merge.py <language> [<language> ...]

Reads build/i18n-work/<language>/out-*.json (ids from build/i18n-work/ids.json, written by prepare.py), checks every
entry and updates:
  build/i18n-work/<language>.json            id -> translation (all work so far; prepare.py --reference reads it)
  app/src/main/assets/i18n/<language>.json   source -> translation for the app's strings
  plugins/<plugin>/i18n/<language>.json      source -> translation for that plugin's strings
Entries already in a table stay unless a chunk replaces them; entries whose source text is gone are dropped.
Problems are listed in build/i18n-work/<language>-problems.txt and the entry is left out until it is fixed.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare import PLUGINS, ROOT, WORK  # noqa: E402
from i18n_common import CJK, LANGUAGES  # noqa: E402
from validate_chunk import problems  # noqa: E402
from fix_example_lists import restore  # noqa: E402


def source_set(path):
    return {entry["text"] for entry in json.loads(path.read_text(encoding="utf-8"))["strings"]}


def read(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def write_table(path, table):
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [json.dumps(key, ensure_ascii=False) + ": " + json.dumps(table[key], ensure_ascii=False) for key in sorted(table)]
    path.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8", newline="\n")


# Languages written with the same letters; other pairs are told apart by the script check of validate_chunk.
FAMILIES = [
    {"en", "es", "pt", "fr", "de", "it", "nl", "pl", "tr", "cs", "ro", "sv", "vi", "id", "ms", "sw"},
    {"ru", "uk"}, {"ar", "fa", "ur"}, {"zh-Hant", "ja"},
]
# Indonesian and Malay share much of their wording ("Status tidak diketahui" is both), so identical text is normal there.
CLOSE = [{"id", "ms"}]


def copied_from_other_language(language, key, value, source=""):
    """A long translation identical to another language's text for the same string was mixed up, not translated."""
    letters = sum(c.isalpha() for c in value)
    if letters < 20:
        return None
    # Mostly Latin sources (licenses, names, versions) read the same in every language.
    if source and len(CJK.findall(source)) * 4 < sum(c.isalpha() for c in source):
        return None
    family = next((f for f in FAMILIES if language in f), set())
    for other in LANGUAGES:
        if other == language or other not in family or {language, other} in CLOSE:
            continue
        theirs = read_other(other).get(key)
        if theirs is not None and theirs == value:
            return other
    return None


_OTHER = {}


def read_other(language):
    """Everything translated so far for [language]: merged work plus chunks not merged yet."""
    if language not in _OTHER:
        table = read(WORK / f"{language}.json")
        for chunk in sorted((WORK / language).glob("out-*.json")):
            try:
                table.update(json.loads(chunk.read_text(encoding="utf-8")))
            except ValueError:
                pass
        _OTHER[language] = table
    return _OTHER[language]


def merge(language):
    ids = read(WORK / "ids.json")
    targets = {ROOT / f"app/src/main/assets/i18n/{language}.json": source_set(ROOT / "tools/i18n/app-source.json")}
    for directory in PLUGINS:
        targets[ROOT / directory / f"i18n/{language}.json"] = source_set(ROOT / directory / "i18n-source.json")
    known = {}
    for path in targets:
        known.update(read(path))
    work = read(WORK / f"{language}.json")
    # The work table is the record of accepted translations; the shipped tables are rebuilt from it.
    for key, value in list(work.items()):
        source = ids.get(key)
        if source is not None:
            work[key] = value = restore(source, value)
            known[source] = value
    report, accepted = [], 0
    for chunk in sorted((WORK / language).glob("out-*.json")):
        try:
            data = json.loads(chunk.read_text(encoding="utf-8"))
        except ValueError as error:
            report.append(f"{chunk.name}: bad JSON {error}")
            continue
        # A mixed-up file repeats another language in bulk; a few identical lines are just close languages.
        twins = {}
        for key, value in data.items():
            if key in ids and isinstance(value, str):
                twin = copied_from_other_language(language, key, value, ids[key])
                if twin:
                    twins.setdefault(twin, []).append(key)
        mixed = {key: twin for twin, keys in twins.items() if len(keys) >= 10 for key in keys}
        for key, value in data.items():
            source = ids.get(key)
            if source is None:
                continue
            value = restore(source, value)
            found = problems(source, value, language)
            if key in mixed:
                found.append("identical to the " + mixed[key] + " translation (mixed-up file)")
            if found:
                report.append(f"{chunk.name} {key}: {'; '.join(found)} | {source!r} -> {value!r}")
                # An earlier merge may have taken this text before the problem was visible: take it out again.
                if known.get(source) == value:
                    del known[source]
                if work.get(key) == value:
                    del work[key]
                continue
            work[key] = value
            known[source] = value
            accepted += 1
    (WORK / f"{language}.json").write_text(json.dumps(work, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    sizes = {}
    for path, sources in targets.items():
        table = {source: known[source] for source in sources if source in known}
        if table:
            write_table(path, table)
        elif path.is_file():
            # Nothing translated yet: without a table the app and the plugins show English.
            path.unlink()
        sizes[path.relative_to(ROOT).as_posix()] = f"{len(table)}/{len(sources)}"
    (WORK / f"{language}-problems.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print(json.dumps({"language": language, "accepted": accepted, "problems": len(report), "tables": sizes}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    for name in sys.argv[1:]:
        merge(name)
