#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Split the text still missing from a language into chunks for translators.

    python tools/i18n/prepare.py <language> [--size 400] [--reference en]

Reads tools/i18n/app-source.json and plugins/*/i18n-source.json, removes what the language's tables already hold,
and writes build/i18n-work/<language>/in-NN.json: {"id": {"zh": source, "ctx": where, "en": reference?}}.
Ids are stable hashes of the source text, so a later run only lists new or changed strings.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from i18n_common import LANGUAGES  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "build/i18n-work"
PLUGINS = ["plugins/liquid-glass", "plugins/control-center-free-edit"]


def short_context(where):
    """A hint for the translator, without line numbers or setting keys."""
    plugin, _, where = where.rpartition(" · ")
    if where.startswith("catalog:"):
        parts = where.split(":")
        where = "settings page " + parts[1] + (" · " + parts[-1] if len(parts) > 2 else "")
    else:
        where = where.split(":")[0]
    return (plugin + " · " if plugin else "") + where


def text_id(text):
    return "t" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:9]


def sources():
    """Every source string with the first place it appears; plugins first so their context wins for shared text."""
    entries = {}
    for directory in PLUGINS + ["app"]:
        path = ROOT / (directory + "/i18n-source.json" if directory != "app" else "tools/i18n/app-source.json")
        label = "" if directory == "app" else Path(directory).name + " plugin · "
        for entry in json.loads(path.read_text(encoding="utf-8"))["strings"]:
            entries.setdefault(entry["text"], label + entry["refs"][0])
    return entries


def table_paths(language):
    yield ROOT / f"app/src/main/assets/i18n/{language}.json"
    for directory in PLUGINS:
        yield ROOT / directory / f"i18n/{language}.json"


def existing(language):
    done = {}
    for path in table_paths(language):
        if path.is_file():
            done.update(json.loads(path.read_text(encoding="utf-8")))
    return done


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("language", choices=sorted(LANGUAGES))
    parser.add_argument("--size", type=int, default=400)
    parser.add_argument("--reference", default=None, help="language whose merged work table is shown next to the source")
    parser.add_argument("--no-context", action="store_true", help="leave out the where-it-appears hint (smaller chunks)")
    args = parser.parse_args()
    all_sources = sources()
    ids = {}
    for text in all_sources:
        key = text_id(text)
        if key in ids and ids[key] != text:
            raise SystemExit("id collision: " + key)
        ids[key] = text
    done = existing(args.language)
    reference = {}
    if args.reference:
        ref_file = WORK / f"{args.reference}.json"
        if not ref_file.is_file():
            raise SystemExit(f"missing {ref_file}: merge the reference language first")
        reference = json.loads(ref_file.read_text(encoding="utf-8"))
    todo = [(key, text) for key, text in ids.items() if text not in done]
    out = WORK / args.language
    out.mkdir(parents=True, exist_ok=True)
    # A new round only lists what the tables still miss; the previous round's files are kept for reference.
    previous = sorted(out.glob("in-*.json")) + sorted(out.glob("out-*.json"))
    if previous:
        rounds = len([p for p in out.glob("round-*") if p.is_dir()])
        archive = out / f"round-{rounds + 1}"
        archive.mkdir()
        for path in previous:
            path.rename(archive / path.name)
    chunks = [todo[i:i + args.size] for i in range(0, len(todo), args.size)]
    for number, chunk in enumerate(chunks, 1):
        payload = {}
        for key, text in chunk:
            item = {"zh": text} if args.no_context else {"zh": text, "ctx": short_context(all_sources[text])}
            if key in reference:
                item["en"] = reference[key]
            payload[key] = item
        (out / f"in-{number:02d}.json").write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (WORK / "ids.json").write_text(json.dumps(ids, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    print(json.dumps({"language": args.language, "todo": len(todo), "chunks": len(chunks), "already": len(done)}))


if __name__ == "__main__":
    main()
