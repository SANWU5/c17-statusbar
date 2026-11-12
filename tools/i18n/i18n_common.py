# SPDX-License-Identifier: GPL-3.0-only
"""Rules shared by the i18n tools: what in a source string must survive translation unchanged."""
import re
import unicodedata

# The 30 interface languages (C17Language.OPTIONS); zh-Hans is the source.
LANGUAGES = {
    "zh-Hant": ("Traditional Chinese (Taiwan usage, 「」 quotes)", "values-b+zh+Hant"),
    "en": ("English (US spelling, sentence case)", "values-en"),
    "ja": ("Japanese", "values-ja"),
    "ko": ("Korean", "values-ko"),
    "es": ("Spanish", "values-es"),
    "pt": ("Portuguese (Brazil)", "values-pt"),
    "fr": ("French", "values-fr"),
    "de": ("German", "values-de"),
    "it": ("Italian", "values-it"),
    "nl": ("Dutch", "values-nl"),
    "pl": ("Polish", "values-pl"),
    "ru": ("Russian", "values-ru"),
    "uk": ("Ukrainian", "values-uk"),
    "tr": ("Turkish", "values-tr"),
    "el": ("Greek", "values-el"),
    "cs": ("Czech", "values-cs"),
    "ro": ("Romanian", "values-ro"),
    "sv": ("Swedish", "values-sv"),
    "ar": ("Arabic", "values-ar"),
    "fa": ("Persian (Farsi)", "values-fa"),
    "he": ("Hebrew", "values-iw"),
    "ur": ("Urdu", "values-ur"),
    "hi": ("Hindi", "values-hi"),
    "bn": ("Bengali", "values-bn"),
    "th": ("Thai", "values-th"),
    "vi": ("Vietnamese", "values-vi"),
    "id": ("Indonesian", "values-in"),
    "ms": ("Malay", "values-ms"),
    "sw": ("Swahili", "values-sw"),
}
CJK_SCRIPT = {"zh-Hant", "ja"}  # Han characters are expected in these translations

CJK = re.compile(r"[㐀-鿿豈-﫿]")
FORMAT_WORDS = ("天气|日期|周|年月日|星期|农历|农历日期|干支|时间|时段|上午下午|12小时|电池|信号|蜂窝信号|网络|网络状态|格式|"
                "日期英文|星期英文简|星期英文|天气原色|天气中文|天气英文|年|月|日")
# Pieces the reader types or the program substitutes; they must appear unchanged in every translation.
VERBATIM = re.compile(
    r"%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[sdfxXc%](?![A-Za-zÀ-ɏ])"  # String.format placeholders (not "% de")
    r"|\{[^{}\n]{0,40}\}"                                       # {周}, {text}, {0}
    r"|（(?:" + FORMAT_WORDS + r")(?:[:：][^（）\n]*)?）"            # （天气）, （电池:{电量}%）
    r"|(?<![^\W\d_])(?:y{2,4}|M{1,2}|d{1,2})(?=[年月日/\-])[年月日/\-]?(?:d{1,2}日?)?"  # M月d日, yyyy年
    r"|\b[Hh]{1,2}:mm(?::ss)?\b"                                # HH:mm
)
TOKEN = re.compile(r"\{[^{}\n]{0,24}\}|（(?:" + FORMAT_WORDS + r")(?:[:：][^（）\n]*)?）"
                   r"|(?<![^\W\d_])(?:y{2,4}|M{1,2}|d{1,2})(?=[年月日/\-])|(?<=[年月日])\s*d{1,2}\b|\b[Hh]{1,2}:mm(?::ss)?\b|\bE{3,4}\b|'[^'\n]*'")
TEXT_PUNCT = re.compile(r"[，。；：、！？]")


def verbatim_pieces(text):
    return VERBATIM.findall(text)


def is_data(text):
    """A template the user types or saves: mostly tokens, with little Chinese prose of its own."""
    rest = TOKEN.sub("", text)
    if not TOKEN.search(text):
        return False
    return len(CJK.findall(rest)) < 4 and not TEXT_PUNCT.search(rest)


# Chinese a translation may keep: what the source puts inside brackets or quotes (tokens the user types, names the
# program matches, examples) and names written next to Latin letters ("天伞桜&PanL").
_QUOTED = re.compile(r"（[^（）\n]*）|\{[^{}\n]*\}|“[^”\n]*”|「[^」\n]*」|\"[^\"\n]*\"|'[^'\n]*'|\([^()\n]*\)")
_RUN = re.compile(r"[\u3400-\u9fff\uf900-\ufaff]+")
_NAME = re.compile(r"[\u3400-\u9fff\uf900-\ufaff]+(?=&|[A-Za-z])|(?<=[A-Za-z&])[\u3400-\u9fff\uf900-\ufaff]+")


def kept_chinese(source):
    runs = set()
    for piece in _QUOTED.findall(source):
        runs.update(_RUN.findall(piece))
    runs.update(_NAME.findall(source))
    # Comma or slash separated lists of names the program matches ("蓝牙,定位,闹钟").
    for piece in re.findall(r"[\u3400-\u9fff]+(?:[,/][\u3400-\u9fff]+)+", source):
        runs.update(_RUN.findall(piece))
    return runs


def leftover_chinese(source, translated):
    """Chinese in the translation that the source does not mark as something to keep."""
    rest = VERBATIM.sub("", translated)
    allowed = kept_chinese(source)
    return [run for run in _RUN.findall(rest) if run not in allowed and not any(run in a for a in allowed)]


# Letters of the scripts our languages use. A translation must not carry letters of a script its language does not
# use (it would be another language's text, e.g. a mixed-up file).
SCRIPTS = {
    "latin": r"A-Za-z\u00c0-\u024f\u1e00-\u1eff",
    "han": r"\u3400-\u9fff\uf900-\ufaff",
    "kana": r"\u3040-\u30ff",
    "hangul": r"\uac00-\ud7af\u1100-\u11ff",
    "cyrillic": r"\u0400-\u04ff",
    "greek": r"\u0370-\u03ff",
    "arabic": r"\u0600-\u06ff\u0750-\u077f\ufb50-\ufdff\ufe70-\ufeff",
    "hebrew": r"\u0590-\u05ff",
    "devanagari": r"\u0900-\u097f",
    "bengali": r"\u0980-\u09ff",
    "thai": r"\u0e00-\u0e7f",
}
LANGUAGE_SCRIPTS = {
    "zh-Hant": {"han"}, "ja": {"han", "kana"}, "ko": {"hangul", "han"},
    "ru": {"cyrillic"}, "uk": {"cyrillic"}, "el": {"greek"},
    "ar": {"arabic"}, "fa": {"arabic"}, "ur": {"arabic"}, "he": {"hebrew"},
    "hi": {"devanagari"}, "bn": {"bengali"}, "th": {"thai"},
}
# Latin letters are always allowed (names, units, tokens); Han only where the source keeps Chinese tokens.
_SCRIPT_RE = {name: re.compile("[" + chars + "]") for name, chars in SCRIPTS.items()}


def foreign_scripts(language, translated):
    expected = LANGUAGE_SCRIPTS.get(language, set()) | {"latin", "han"}
    # Only letters and marks tell a script apart: punctuation is shared, e.g. Bengali ends sentences with the
    # danda U+0964, which Unicode files under Devanagari.
    letters = "".join(c for c in translated if unicodedata.category(c)[0] in "LM")
    return sorted(name for name, pattern in _SCRIPT_RE.items() if name not in expected and pattern.search(letters))
