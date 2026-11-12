#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check one translated chunk against its input.

    python tools/i18n/validate_chunk.py <in-NN.json> <out-NN.json> <language>

Prints "OK <n>" or one line per problem: missing or extra ids, empty text, Chinese left in a language that does not use
Han characters, placeholders, tokens and line breaks that do not match the source. Exit code 1 when there are problems.
"""
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from i18n_common import CJK, CJK_SCRIPT, foreign_scripts, leftover_chinese, verbatim_pieces  # noqa: E402


def problems(source, translated, language):
    found = []
    if not isinstance(translated, str) or not translated.strip():
        return ["empty translation"]
    want = Counter(verbatim_pieces(source))
    have = Counter(verbatim_pieces(translated))
    missing = want - have
    if missing:
        found.append("keep unchanged: " + " ".join(sorted(missing)))
    extra = have - want
    if extra:
        found.append("not in the source, remove: " + " ".join(sorted(extra)))
    if source.count("\n") != translated.count("\n"):
        found.append(f"line breaks: source has {source.count(chr(10))}, translation has {translated.count(chr(10))}")
    if language not in CJK_SCRIPT and language != "zh-Hans":
        left = leftover_chinese(source, translated)
        if left:
            found.append("Chinese characters left outside the tokens: " + "".join(left)[:20])
    wrong = foreign_scripts(language, translated)
    if wrong:
        found.append("letters of another language (" + ", ".join(wrong) + "): this is not " + language + " text")
    if language == "zh-Hant" and translated == source and CJK.search(source) and any(c in source for c in "这们说时来过还进对样为"):
        found.append("still Simplified Chinese")
    return found


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    source = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    try:
        result = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print("BAD JSON:", error)
        sys.exit(1)
    language = sys.argv[3]
    lines = []
    for key in source:
        if key not in result:
            lines.append(f"{key}: missing")
            continue
        for problem in problems(source[key]["zh"], result[key], language):
            lines.append(f"{key}: {problem}")
    for key in result:
        if key not in source:
            lines.append(f"{key}: id not in the input, remove it")
    if lines:
        sys.stdout.reconfigure(encoding="utf-8")
        print("\n".join(lines))
        print(f"PROBLEMS {len(lines)}")
        sys.exit(1)
    print(f"OK {len(source)}")


if __name__ == "__main__":
    main()
