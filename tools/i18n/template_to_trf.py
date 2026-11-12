#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Turn Kotlin string templates that hold Chinese into translatable trf(...) calls.

    python tools/i18n/template_to_trf.py <file.kt> [--apply] [--lines 12,40]

"已应用${name}" becomes trf("已应用%s", name); with several values the placeholders are numbered (%1$s, %2$s) so a
translation may reorder them. Chinese literals inside the inserted expressions are wrapped in tr(...). A literal "%"
becomes "%%". trf translates while the string is built, so only convert text that is shown, never text the program
compares, stores or uses as a key. Without --apply the planned changes are printed for review.
"""
import argparse
import re
import sys
from pathlib import Path

CJK = re.compile(r"[㐀-鿿豈-﫿]")


def parse_string(src, i):
    """src[i] == '"'. Returns (end index after the closing quote, parts) with parts = [('text', raw) | ('expr', code)]."""
    assert src[i] == '"'
    i += 1
    parts, text = [], []
    while i < len(src):
        c = src[i]
        if c == "\\":
            text.append(src[i:i + 2])
            i += 2
            continue
        if c == '"':
            if text:
                parts.append(("text", "".join(text)))
            return i + 1, parts
        if c == "\n":
            raise ValueError("unterminated")
        if c == "$" and i + 1 < len(src) and src[i + 1] == "{":
            if text:
                parts.append(("text", "".join(text)))
                text = []
            j, depth = i + 2, 1
            while depth:
                ch = src[j]
                if ch == '"':
                    j, _ = parse_string(src, j)
                    continue
                if ch == "'":
                    j = src.index("'", j + 2 if src[j + 1] == "\\" else j + 1) + 1
                    continue
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                j += 1
            parts.append(("expr", src[i + 2:j - 1]))
            i = j
            continue
        if c == "$" and i + 1 < len(src) and (src[i + 1].isalpha() or src[i + 1] == "_"):
            if text:
                parts.append(("text", "".join(text)))
                text = []
            j = i + 1
            while j < len(src) and (src[j].isalnum() or src[j] == "_"):
                j += 1
            parts.append(("expr", src[i + 1:j]))
            i = j
            continue
        text.append(c)
        i += 1
    raise ValueError("unterminated")


def wrap_inner(code):
    """tr(...) around plain Chinese literals inside an inserted expression."""
    out, i = [], 0
    while i < len(code):
        if code[i] == '"' and not code.startswith('"""', i):
            end, parts = parse_string(code, i)
            literal = code[i:end]
            plain = all(kind == "text" for kind, _ in parts)
            if plain and CJK.search(literal) and not code[:i].rstrip().endswith("tr("):
                out.append("tr(" + literal + ")")
            else:
                out.append(literal)
            i = end
            continue
        out.append(code[i])
        i += 1
    return "".join(out)


def convert(parts):
    exprs = [code for kind, code in parts if kind == "expr"]
    numbered = len(exprs) > 1
    fmt, n = [], 0
    for kind, raw in parts:
        if kind == "text":
            fmt.append(raw.replace("%", "%%"))
        else:
            n += 1
            fmt.append(f"%{n}\\$s" if numbered else "%s")
    args = ", ".join(wrap_inner(code).strip() for code in exprs)
    return 'trf("' + "".join(fmt) + '", ' + args + ")"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("file")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--lines", default="")
    args = parser.parse_args()
    path = Path(args.file)
    src = path.read_text(encoding="utf-8")
    only = {int(x) for x in args.lines.split(",") if x}
    out, i, changes = [], 0, []
    while i < len(src):
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = len(src) if j < 0 else j
            out.append(src[i:j])
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = len(src) if j < 0 else j + 2
            out.append(src[i:j])
            i = j
            continue
        if src.startswith('"""', i):
            j = src.index('"""', i + 3) + 3
            out.append(src[i:j])
            i = j
            continue
        if c == "'":
            j = src.index("'", i + 2 if src[i + 1] == "\\" else i + 1) + 1
            out.append(src[i:j])
            i = j
            continue
        if c == '"':
            try:
                end, parts = parse_string(src, i)
            except (ValueError, IndexError):
                out.append(c)
                i += 1
                continue
            literal = src[i:end]
            line = src.count("\n", 0, i) + 1
            has_expr = any(kind == "expr" for kind, _ in parts)
            has_cjk = any(kind == "text" and CJK.search(raw) for kind, raw in parts)
            if has_expr and has_cjk and (not only or line in only):
                replacement = convert(parts)
                changes.append((line, literal, replacement))
                out.append(replacement)
            else:
                out.append(literal)
            i = end
            continue
        out.append(c)
        i += 1
    sys.stdout.reconfigure(encoding="utf-8")
    for line, before, after in changes:
        print(f"{path.name}:{line}\n  - {before}\n  + {after}")
    if args.apply and changes:
        path.write_text("".join(out), encoding="utf-8", newline="\n")
    print(f"{len(changes)} {'applied' if args.apply else 'planned'}")


if __name__ == "__main__":
    main()
