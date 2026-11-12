#!/usr/bin/env python3
"""C17 API 1/2 deterministic packaging. Standard library only; does not execute JS."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import re
import stat
import struct
import sys
import zipfile
from pathlib import Path, PurePosixPath

MAX_FILE = 256 * 1024
MAX_TOTAL = 1024 * 1024
MAX_FILES = 32
ROOT = Path(__file__).resolve().parents[1]
META_FIELDS = {"id", "name", "version", "versionCode", "apiVersion", "description", "author", "capabilities"}
PLUGIN_FIELDS = META_FIELDS | {"runtime", "minimumHost", "features", "usage", "scopes", "nativeSettings", "configurationAliases"}
MANIFEST_FIELDS = PLUGIN_FIELDS | {"entry"}
ID_RE = re.compile(r"[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\Z")
SCOPE_RE = re.compile(r"(?:android|[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+)\Z")
CLASS_RE = re.compile(r"[a-zA-Z_$][a-zA-Z0-9_$]*(?:\.[a-zA-Z_$][a-zA-Z0-9_$]*)+\Z")
STATE_KEY_RE = re.compile(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}\Z")
HEADER_RE = re.compile(r"^\s*var\s+plugin\s*=\s*", re.MULTILINE)
UI_RE = re.compile(r"^\s*plugin\s*\.\s*ui\s*=\s*", re.MULTILINE)
ALLOWED_EXT = {".js", ".md", ".txt"}
# Interface translation tables (host code291, ui.i18n): i18n/<language>.json, display text only.
I18N_RE = re.compile(r"i18n/[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|[0-9]{3}))?\.json\Z")
MAX_I18N_FILES = 64
MAX_I18N_BYTES = 2 * 1024 * 1024
MAX_I18N_ENTRIES = 20000
MAX_I18N_TEXT = 8192


class Invalid(ValueError):
    pass


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Invalid("JSON 含重复字段：" + key)
        result[key] = value
    return result


def decode_json(data):
    try:
        return json.loads(data.decode("utf-8-sig"), object_pairs_hook=_pairs,
                          parse_constant=lambda value: (_ for _ in ()).throw(Invalid("JSON 数值无效：" + value)))
    except (UnicodeError, json.JSONDecodeError) as error:
        raise Invalid("不是有效 UTF-8 JSON：" + str(error)) from error


def _text(obj, key, maximum, required=True):
    if not required and key not in obj:
        return
    value = obj.get(key)
    if not isinstance(value, str) or len(value) > maximum or "\0" in value or required and not value.strip():
        raise Invalid("元数据文字字段无效：" + key)


def validate_metadata(value, manifest=False, catalog=False):
    if not isinstance(value, dict) or set(value) - (MANIFEST_FIELDS if manifest else PLUGIN_FIELDS if not catalog else META_FIELDS):
        raise Invalid("元数据必须为对象且不能有未知字段")
    for key, maximum in (("id", 80), ("name", 80), ("version", 32)):
        _text(value, key, maximum)
    if len(value["id"]) < 3 or not ID_RE.fullmatch(value["id"]):
        raise Invalid("插件 ID 格式无效")
    _text(value, "description", 1024, False)
    _text(value, "author", 80, False)
    _text(value, "usage", 4096, False)
    if "minimumHost" in value and (type(value["minimumHost"]) is not int or not 1 <= value["minimumHost"] <= 2147483647):
        raise Invalid("minimumHost 必须为有效的宿主 versionCode 整数")
    for key, pattern, maximum in (("features", ID_RE, 80), ("scopes", SCOPE_RE, 240)):
        if key not in value:
            continue
        entries = value[key]
        if not isinstance(entries, list) or len(entries) > 64 or any(not isinstance(item, str) or len(item) > maximum or not pattern.fullmatch(item) for item in entries) or len(set(entries)) != len(entries):
            raise Invalid(key + " 必须为至多 64 个唯一且有效的声明")
    for key, low, high in (("versionCode", 1, 2147483647), ("apiVersion", 1, 2)):
        if type(value.get(key)) is not int or not low <= value[key] <= high:
            raise Invalid("元数据整数字段无效：" + key)
    runtime_api = value["apiVersion"] == 2
    validate_configuration_aliases(value)
    if "nativeSettings" in value:
        native = value["nativeSettings"]
        if not runtime_api or not isinstance(native, dict) or set(native) != {"dex", "className"}:
            raise Invalid("nativeSettings 仅用于 API 2，且必须声明 dex 和 className")
        _text(native, "dex", 4096)
        _text(native, "className", 240)
        validate_path(native["dex"], 2)
        if not re.fullmatch(r"[a-zA-Z0-9_./-]+\.(?:dex|jar)", native["dex"]) or not CLASS_RE.fullmatch(native["className"]):
            raise Invalid("原生设置需要安全的 .dex/.jar 路径与完整 Java 类名")
    caps = value.get("capabilities", [])
    allowed = ("settings", "systemui", "runtime") if runtime_api else ("settings", "systemui")
    if not isinstance(caps, list) or len(caps) > (len(allowed) if runtime_api else 2) or any(not isinstance(cap, str) or cap not in allowed for cap in caps) or len(set(caps)) != len(caps):
        raise Invalid("capabilities 不受支持或重复")
    if runtime_api and "runtime" not in caps:
        raise Invalid("API 2 插件必须声明 runtime 能力")
    if runtime_api and not catalog:
        runtime = value.get("runtime")
        if not isinstance(runtime, dict) or set(runtime) - {"entry", "source", "preferences"} or ("entry" in runtime) == ("source" in runtime):
            raise Invalid("API 2 必须声明 runtime.entry 或 runtime.source")
        if "preferences" in runtime:
            names = runtime["preferences"]
            if (not isinstance(names, list) or len(names) > 32
                    or any(not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{2,119}", name) for name in names)
                    or len(set(names)) != len(names)):
                raise Invalid("runtime.preferences 必须为至多 32 个唯一的自有偏好文件名")
        if "entry" in runtime:
            _text(runtime, "entry", 4096)
            validate_path(runtime["entry"], 2)
            if PurePosixPath(runtime["entry"]).suffix != ".js":
                raise Invalid("runtime 入口必须为 .js")
        else:
            _text(runtime, "source", 2147483647)
    elif not runtime_api and ("runtime" in value or "runtime" in caps):
        raise Invalid("runtime 需要 API 2")
    if manifest:
        _text(value, "entry", 4096 if runtime_api else 128)
        validate_path(value["entry"], value["apiVersion"])
        if PurePosixPath(value["entry"]).suffix != ".js":
            raise Invalid("入口必须为 .js")


def validate_configuration_aliases(metadata, controls=None):
    """Retired import keys are explicit, one-step mappings; live state has only current keys."""
    if "configurationAliases" not in metadata:
        return
    aliases = metadata["configurationAliases"]
    if metadata.get("apiVersion") != 2 or "config.aliases" not in metadata.get("features", []):
        raise Invalid("配置别名需要 API 2 和 config.aliases 能力声明")
    if type(metadata.get("minimumHost")) is not int or metadata["minimumHost"] < 265:
        raise Invalid("配置别名需要 minimumHost 至少 265")
    if not isinstance(aliases, dict) or len(aliases) > 512:
        raise Invalid("配置别名必须为最多 512 项的对象")
    for old, current in aliases.items():
        if any(not isinstance(key, str) or not STATE_KEY_RE.fullmatch(key)
               or key in ("constructor", "prototype") for key in (old, current)):
            raise Invalid("配置别名必须使用合法的状态键")
        if current in aliases:
            raise Invalid("配置别名不能形成链或循环")
    if len(set(aliases.values())) != len(aliases):
        raise Invalid("配置别名不能重复引用同一目标")
    if controls is None:
        return
    current_keys = {control["key"] for control in controls if "key" in control}
    persistent = {control["key"] for control in controls
                  if control["type"] in ("switch", "slider", "input", "color", "app", "select", "sort", "button")}
    if aliases.keys() & current_keys:
        raise Invalid("配置别名不能覆盖当前控件键")
    if set(aliases.values()) - persistent:
        raise Invalid("配置别名目标必须是当前持久控件")


def validate_sort(value):
    if not isinstance(value, str) or len(value) > 65536:
        raise Invalid("排序值必须为不超过 65536 字符的 JSON 字符串")
    data = decode_json(value.encode("utf-8"))
    lists = [data]
    if isinstance(data, dict):
        if set(data) != {"version", "order", "hidden"} or type(data["version"]) is not int or data["version"] != 1:
            raise Invalid("排序对象需要 version:1、order 和 hidden")
        lists = [data["order"], data["hidden"]]
    for items in lists:
        if (not isinstance(items, list) or any(not isinstance(item, str) or not item.strip() or len(item) > 4096 or "\0" in item for item in items)
                or len(set(items)) != len(items)):
            raise Invalid("排序标识必须为唯一的非空字符串")
    if isinstance(data, dict) and set(data["hidden"]) - set(data["order"]):
        raise Invalid("隐藏项目必须在排序列表中")


def validate_form(controls, api_version=1, native_settings=False):
    """Validate the builder's literal form; the host still checks actual JS output."""
    if native_settings and api_version != 2:
        raise Invalid("原生设置入口需要 API 2")
    if not isinstance(controls, list) or not controls and not native_settings or api_version == 1 and len(controls) > 64:
        raise Invalid("插件必须提供完整设置；API 1 的 ui 需包含 1–64 项控件，API 2 不设总项数配额")
    keys, editable = set(), 0
    def number(value):
        try:
            return type(value) in (int, float) and math.isfinite(value)
        except OverflowError:
            return False
    def primitive(value):
        return type(value) is bool or isinstance(value, str) and len(value) <= 4096 and "\0" not in value or number(value)
    for control in controls:
        if not isinstance(control, dict) or set(control) - {"type", "title", "description", "key", "default", "min", "max", "step", "unit", "unboundedInput", "options", "value", "values", "section", "group", "groupPath", "enableOnEdit", "optionsAction", "confirmation"}:
            raise Invalid("插件控件必须为对象且不能包含未知字段")
        kind = control.get("type")
        if "enableOnEdit" in control and kind not in ("switch", "slider", "color", "select"):
            raise Invalid("仅开关、数值、颜色和选择项支持编辑联动")
        if kind not in ("text", "switch", "slider", "input", "color", "app", "select", "sort", "button", "preset", "action", "status"):
            raise Invalid("不支持的插件控件类型")
        if kind in ("action", "status") and (api_version != 2 or set(control) - {"type", "title", "description", "key", "section", "group", "groupPath", "confirmation"}):
            raise Invalid("一次性操作与只读状态仅用于 API 2，不能包含持久配置字段")
        if "confirmation" in control:
            if kind != "action":
                raise Invalid("只有一次性操作支持确认说明")
            _text(control, "confirmation", 1024)
        if "optionsAction" in control and kind != "sort":
            raise Invalid("只有排序控件支持 optionsAction")
        if kind == "sort" and (api_version != 2 or set(control) - {"type", "title", "description", "key", "default", "optionsAction", "section", "group", "groupPath"}):
            raise Invalid("动态排序仅用于 API 2，不能包含其他控件的配置字段")
        if "values" in control and kind != "preset" or kind == "preset" and set(control) - {"type", "title", "description", "key", "values", "section", "group", "groupPath"}:
            raise Invalid("预设仅声明组合数值和展示字段，不拥有独立配置值")
        _text(control, "title", 120)
        _text(control, "description", 512, False)
        _text(control, "group", 120, False)
        if "groupPath" in control:
            path = control["groupPath"]
            if not isinstance(path, list) or not 1 <= len(path) <= 3:
                raise Invalid("插件菜单路径需要 1 到 3 层")
            for segment in path:
                _text({"name": segment}, "name", 120)
        if "section" in control and control["section"] not in ("features", "management"):
            raise Invalid("控件 section 只能为 features 或 management")
        if "unit" in control:
            if kind != "slider":
                raise Invalid("只有滑块可以声明显示单位")
            _text(control, "unit", 16, False)
        if "unboundedInput" in control and (kind != "slider" or type(control["unboundedInput"]) is not bool):
            raise Invalid("只有滑块可以声明不限范围的手动输入，且取值为布尔值")
        if kind == "text" and "key" not in control:
            continue
        key = control.get("key")
        if not isinstance(key, str) or not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}", key) or key in ("constructor", "prototype") or key in keys:
            raise Invalid("插件配置 key 无效或重复")
        keys.add(key)
        if kind == "sort":
            action = control.get("optionsAction")
            if not isinstance(action, str) or not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,63}", action) or action in ("constructor", "prototype") or action in keys:
                raise Invalid("排序 optionsAction 无效或与其他 key 冲突")
            keys.add(action)
            validate_sort(control.get("default", "[]"))
            editable += 1
            continue
        if kind in ("text", "action", "status", "preset"):
            continue
        if kind != "button":
            editable += 1
        default = control.get("default", False if kind == "switch" else control.get("min") if kind == "slider" else control.get("value", True) if kind == "button" else "#FFFFFF" if kind == "color" else "")
        if kind == "select":
            options = control.get("options")
            if not isinstance(options, list) or not 1 <= len(options) <= 64:
                raise Invalid("选择控件必须声明 1–64 个选项")
            values = set()
            for option in options:
                if not isinstance(option, dict) or set(option) != {"value", "label"}:
                    raise Invalid("选择项必须包含 value 和 label")
                _text(option, "value", 128)
                _text(option, "label", 120)
                if option["value"] in values:
                    raise Invalid("选择项 value 重复")
                values.add(option["value"])
            default = control.get("default", options[0]["value"])
            if not isinstance(default, str) or default not in values:
                raise Invalid("默认配置不在选项中")
        if not primitive(default) or kind == "switch" and type(default) is not bool or kind == "input" and not isinstance(default, str):
            raise Invalid("插件配置默认值类型或内容无效")
        if kind == "color" and (not isinstance(default, str) or not re.fullmatch(r"#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", default)):
            raise Invalid("颜色应为 #RRGGBB 或 #AARRGGBB")
        if kind == "app" and (not isinstance(default, str) or default and not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+", default)):
            raise Invalid("应用默认值必须为空或完整包名")
        if kind == "slider":
            low, high, step = control.get("min"), control.get("max"), control.get("step", 1)
            if not all(number(value) for value in (low, high, step, default)) or not -100000 <= low < high <= 100000 or not 0 < step <= high-low or not low <= default <= high:
                raise Invalid("滑块范围、步长或默认值无效")
        if kind == "button" and not primitive(control.get("value", True)):
            raise Invalid("按钮值必须为文字、有限数字或布尔值")
    if editable == 0 and not native_settings:
        raise Invalid("插件必须提供完整设置，至少声明一个开关、滑块、输入框或选择项；只有说明或按钮不符合规范")
    persistent = {control["key"]: control for control in controls if control["type"] in ("switch", "slider", "input", "color", "app", "select", "sort", "button")}
    for control in controls:
        if "enableOnEdit" not in control:
            continue
        gates = control["enableOnEdit"]
        if (not isinstance(gates, list) or not 1 <= len(gates) <= 4
                or any(not isinstance(key, str) or key == control.get("key") or persistent.get(key, {}).get("type") != "switch" for key in gates)
                or len(set(gates)) != len(gates)):
            raise Invalid("编辑联动只能引用其他已声明的唯一开关")
    for preset in (control for control in controls if control["type"] == "preset"):
        values = preset.get("values")
        if not isinstance(values, dict) or not 1 <= len(values) <= 512 or set(values) - persistent.keys():
            raise Invalid("预设需要引用 1 到 512 个已声明设置")
        # Reuse the same value contract, with presets removed so validation cannot recurse.
        applied = []
        for control in controls:
            if control["type"] == "preset":
                continue
            if control.get("key") in values:
                # Like typed values, a preset may leave an unbounded slider's drag range.
                if control["type"] == "slider" and control.get("unboundedInput") is True:
                    if not number(values[control["key"]]) or abs(values[control["key"]]) > 3.4028234663852886e38:
                        raise Invalid("滑块预设值必须为有限数字")
                else:
                    control = dict(control, default=values[control["key"]])
            applied.append(control)
        validate_form(applied, api_version, native_settings)


def validate_source_form(source, api_version=1, native_settings=False):
    matches = list(UI_RE.finditer(source))
    if len(matches) != 1:
        raise Invalid("标准构建器要求唯一的 plugin.ui = [严格 JSON 控件声明];，插件必须提供完整设置")
    try:
        controls, end = json.JSONDecoder(object_pairs_hook=_pairs, parse_constant=lambda value: (_ for _ in ()).throw(Invalid("JSON 数值无效：" + value))).raw_decode(source, matches[0].end())
    except json.JSONDecodeError as error:
        raise Invalid("标准构建器要求 plugin.ui 使用严格 JSON 数组；动态脚本请直接交宿主验证导入") from error
    if not source[end:].lstrip().startswith(";"):
        raise Invalid("plugin.ui 严格 JSON 数组后需要分号")
    validate_form(controls, api_version, native_settings)
    return controls


def validate_standard(metadata, controls):
    """Opt-in authoring convention; legacy packages keep their original admission rules."""
    validate_metadata(metadata)
    validate_form(controls, metadata["apiVersion"], "nativeSettings" in metadata)
    validate_configuration_aliases(metadata, controls)
    if metadata["apiVersion"] != 2 or metadata.get("minimumHost", 0) < 261:
        raise Invalid("统一详情页模板需要 API 2，并注明 minimumHost 至少 261")
    if not metadata.get("usage", "").strip() or not metadata.get("scopes"):
        raise Invalid("统一详情页需要使用说明和实际应用作用域")
    features = set(metadata.get("features", []))
    if not {"ui.group-paths", "ui.management-groups"} <= features:
        raise Invalid("统一分类需要 ui.group-paths 与 ui.management-groups 能力声明")
    for control in controls:
        if control.get("section") not in ("features", "management") or not control.get("groupPath"):
            raise Invalid("统一详情页控件需明确 section 与 groupPath")
        needed = {"color": "ui.color", "app": "ui.app", "sort": "ui.sort", "preset": "ui.presets",
                  "action": "runtime.actions", "status": "runtime.status"}.get(control["type"])
        dependencies = [needed] if needed else []
        for field, feature in (("enableOnEdit", "ui.edit-enables"), ("confirmation", "ui.action-confirmation"),
                               ("unboundedInput", "ui.unbounded-input")):
            if field in control:
                dependencies.append(feature)
        if set(dependencies) - features:
            raise Invalid("控件缺少能力声明：" + ", ".join(sorted(set(dependencies) - features)))
    external = set(metadata["scopes"]) - {"com.android.systemui"}
    if "android" in external or "dev.puitheme.iosstatusbar" in external:
        raise Invalid("运行作用域不支持系统服务或 C17 自身")
    if external and ("scope.apps" not in features or metadata["runtime"].get("preferences")):
        raise Invalid("多应用需要 scope.apps，且不能声明原生偏好文件")
    if "com.android.systemui" not in metadata["scopes"] and any(c["type"] in ("action", "sort") for c in controls):
        raise Invalid("表单操作与动态排序需要 SystemUI 作用域")


def validate_descriptor(descriptor, manifest=None, standard=False):
    if not isinstance(descriptor, dict) or set(descriptor) - (PLUGIN_FIELDS | {"ui", "settings", "hooks"}):
        raise Invalid("描述包含未知字段")
    metadata = {key: value for key, value in descriptor.items() if key in PLUGIN_FIELDS}
    validate_metadata(metadata)
    if manifest is not None:
        validate_metadata(manifest, True)
        if metadata != {key: value for key, value in manifest.items() if key != "entry"}:
            raise Invalid("manifest 与导出描述元数据不一致")
    validate_form(descriptor.get("ui"), metadata["apiVersion"], "nativeSettings" in metadata)
    validate_configuration_aliases(metadata, descriptor["ui"])
    if not isinstance(descriptor.get("settings", {}), dict) or not isinstance(descriptor.get("hooks", []), list):
        raise Invalid("settings 必须为对象，hooks 必须为数组")
    if standard:
        validate_standard(metadata, descriptor["ui"])
    return metadata


def validate_path(name, api_version=1):
    maximum = 4096 if api_version == 2 else 128
    if not isinstance(name, str) or not name or len(name) > maximum or "\\" in name or "\0" in name:
        raise Invalid("非法文件路径")
    path = PurePosixPath(name)
    if path.is_absolute() or ":" in name or any(part in ("", ".", "..") or part.endswith((".", " ")) for part in name.split("/")):
        raise Invalid("文件路径必须是无穿越的相对路径：" + name)
    if api_version == 1 and name not in ("LICENSE", "plugin.json") and path.suffix not in ALLOWED_EXT:
        raise Invalid("不支持的包内文件类型：" + name)


def validate_i18n(files, api_version):
    """Every file under i18n/ is a translation table the host can read; anything else there is refused."""
    tables = {name: data for name, data in files.items() if name.startswith("i18n/")}
    if not tables:
        return
    if api_version != 2:
        raise Invalid("界面翻译表需要 API 2")
    if len(tables) > MAX_I18N_FILES:
        raise Invalid("翻译文件超过 64 个")
    for name, data in tables.items():
        if not I18N_RE.match(name):
            raise Invalid("翻译文件应为 i18n/<语言>.json，例如 i18n/en.json、i18n/zh-Hant.json：" + name)
        if len(data) > MAX_I18N_BYTES:
            raise Invalid("翻译文件超过 2MiB：" + name)
        try:
            table = json.loads(data.decode("utf-8-sig"), object_pairs_hook=_pairs)
        except (UnicodeError, ValueError, Invalid) as error:
            raise Invalid("翻译文件必须是 UTF-8 JSON 对象且键不重复：" + name) from error
        if not isinstance(table, dict):
            raise Invalid("翻译文件必须是 JSON 对象：" + name)
        if len(table) > MAX_I18N_ENTRIES:
            raise Invalid("翻译条目超过 20000 条：" + name)
        for key, value in table.items():
            if (not isinstance(value, str) or not key or not value.strip() or len(key) > MAX_I18N_TEXT
                    or len(value) > MAX_I18N_TEXT or "\0" in key or "\0" in value):
                raise Invalid("翻译条目必须把非空原文映射到非空文字：" + name)


def validate_files(files):
    if not files or "plugin.json" not in files:
        raise Invalid("ZIP 根目录缺少 plugin.json")
    manifest = decode_json(files["plugin.json"])
    validate_metadata(manifest, True)
    api_version = manifest["apiVersion"]
    if api_version == 1 and (len(files) > MAX_FILES or sum(len(data) for data in files.values()) > MAX_TOTAL):
        raise Invalid("API 1 插件超过 32 项或 1MiB 总容量")
    normalized = set()
    for name, data in files.items():
        validate_path(name, api_version)
        canonical = name.lower()
        if canonical in normalized:
            raise Invalid("包内路径忽略大小写后重复：" + name)
        normalized.add(canonical)
        if api_version == 1 and len(data) > MAX_FILE:
            raise Invalid("单项文件超过 256KiB：" + name)
        if api_version == 1 or name == "plugin.json" or name == manifest["entry"] or name.endswith(".js"):
            try:
                text = data.decode("utf-8-sig")
            except UnicodeError as error:
                raise Invalid("插件脚本或说明必须为 UTF-8：" + name) from error
            if "\0" in text:
                raise Invalid("插件文本不能包含 NUL：" + name)
    validate_i18n(files, api_version)
    if manifest["entry"] not in files:
        raise Invalid("入口文件不存在：" + manifest["entry"])
    runtime = manifest.get("runtime", {})
    if "entry" in runtime and runtime["entry"] not in files:
        raise Invalid("API 2 runtime 入口文件不存在：" + runtime["entry"])
    native = manifest.get("nativeSettings")
    if native is not None and native["dex"] not in files:
        raise Invalid("原生设置 DEX/JAR 文件不存在：" + native["dex"])
    try:
        source = files[manifest["entry"]].decode("utf-8-sig")
    except UnicodeError as error:
        raise Invalid("入口脚本必须为 UTF-8") from error
    match = HEADER_RE.search(source)
    if not match:
        raise Invalid("标准构建器要求 var plugin = {严格 JSON 元数据}; 源码头；独立脚本可直接交应用导入")
    try:
        metadata, end = json.JSONDecoder(object_pairs_hook=_pairs).raw_decode(source, match.end())
    except json.JSONDecodeError as error:
        raise Invalid("plugin 元数据头必须是严格 JSON") from error
    if not source[end:].lstrip().startswith(";"):
        raise Invalid("plugin 元数据头后需要分号")
    validate_metadata(metadata)
    expected = {key: value for key, value in manifest.items() if key != "entry"}
    if metadata != expected:
        raise Invalid("manifest 与 JavaScript 元数据头不一致")
    if not re.search(r"\bC17\s*\.\s*define\s*\(", source):
        raise Invalid("脚本没有 C17.define 声明")
    controls = validate_source_form(source, api_version, native is not None)
    validate_configuration_aliases(metadata, controls)
    return manifest


def read_source(directory):
    directory = Path(directory)
    if directory.is_symlink() or not directory.is_dir():
        raise Invalid("源码目录不存在或为链接")
    manifest_path = directory / "plugin.json"
    if not manifest_path.is_file():
        raise Invalid("包根目录缺少 plugin.json")
    manifest = decode_json(manifest_path.read_bytes())
    validate_metadata(manifest, True)
    api_version = manifest["apiVersion"]
    files = {}
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise Invalid("不接受符号链接：" + str(path))
        if path.is_file():
            name = path.relative_to(directory).as_posix()
            validate_path(name, api_version)
            if api_version == 1 and path.stat().st_size > MAX_FILE:
                raise Invalid("单项文件超过 256KiB：" + name)
            if api_version == 1 and len(files) >= MAX_FILES:
                raise Invalid("文件超过 32 项")
            files[name] = path.read_bytes()
    manifest = validate_files(files)
    files["plugin.json"] = json_bytes(manifest)
    return files, manifest


def archive_bytes(files):
    if len(files) >= 65535:
        raise Invalid("当前宿主支持经典 ZIP；文件数需能由非 ZIP64 档案表示")
    buffer = io.BytesIO()
    try:
        with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, allowZip64=False) as archive:
            for name, data in sorted(files.items()):
                item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                item.create_system = 3
                item.external_attr = (stat.S_IFREG | 0o644) << 16
                item.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(item, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    except zipfile.LargeZipFile as error:
        raise Invalid("当前宿主支持经典 ZIP，包超出档案格式范围") from error
    data = buffer.getvalue()
    inspect_archive(data)
    return data


def validate_zip_structure(data):
    """Match the host's classic ZIP format, including local/central header identity."""
    def field(offset, kind):
        size = struct.calcsize(kind)
        if offset < 0 or offset + size > len(data):
            raise Invalid("ZIP 文件头截断")
        return struct.unpack_from(kind, data, offset)[0]
    end = -1
    for position in range(len(data) - 22, max(-1, len(data) - 65558), -1):
        if field(position, "<I") == 0x06054b50 and position + 22 + field(position + 20, "<H") == len(data):
            end = position
            break
    if end < 0 or field(end + 4, "<H") or field(end + 6, "<H"):
        raise Invalid("ZIP 不完整或不支持分卷")
    count, size, offset = field(end + 10, "<H"), field(end + 12, "<I"), field(end + 16, "<I")
    if count == 65535 or size == 0xffffffff or offset == 0xffffffff:
        raise Invalid("当前宿主不支持 ZIP64，请重新打包为经典 ZIP")
    if not count or field(end + 8, "<H") != count or offset + size != end:
        raise Invalid("ZIP 目录无效")
    cursor, used_offsets = offset, set()
    for _ in range(count):
        if cursor + 46 > end or field(cursor, "<I") != 0x02014b50:
            raise Invalid("ZIP 中央目录无效")
        flags, method = field(cursor + 8, "<H"), field(cursor + 10, "<H")
        compressed, plain, local = field(cursor + 20, "<I"), field(cursor + 24, "<I"), field(cursor + 42, "<I")
        name_size, extra_size, comment_size = field(cursor + 28, "<H"), field(cursor + 30, "<H"), field(cursor + 32, "<H")
        if compressed == 0xffffffff or plain == 0xffffffff or local == 0xffffffff:
            raise Invalid("当前宿主不支持 ZIP64，请重新打包为经典 ZIP")
        if (flags & 1 or method not in (0, 8) or field(cursor + 34, "<H") or local + 30 > offset
                or local in used_offsets or cursor + 46 + name_size + extra_size + comment_size > end):
            raise Invalid("ZIP 不支持加密、分卷、重复偏移或无效压缩目录")
        used_offsets.add(local)
        if (field(local, "<I") != 0x04034b50 or field(local + 6, "<H") != flags
                or field(local + 8, "<H") != method or field(local + 26, "<H") != name_size):
            raise Invalid("ZIP 本地文件头与目录不一致")
        if local + 30 + name_size + field(local + 28, "<H") + compressed > offset:
            raise Invalid("ZIP 条目边界无效")
        if data[local + 30:local + 30 + name_size] != data[cursor + 46:cursor + 46 + name_size]:
            raise Invalid("ZIP 本地文件名与目录不一致")
        cursor += 46 + name_size + extra_size + comment_size
    if cursor != end:
        raise Invalid("ZIP 目录长度无效")


def inspect_archive(data):
    validate_zip_structure(data)
    files = {}
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            try:
                manifest = decode_json(archive.read("plugin.json"))
            except KeyError as error:
                raise Invalid("ZIP 根目录缺少 plugin.json") from error
            validate_metadata(manifest, True)
            api_version = manifest["apiVersion"]
            if api_version == 1 and (len(data) > MAX_TOTAL or len(archive.infolist()) > MAX_FILES):
                raise Invalid("API 1 ZIP 超过 1MiB 或 32 项")
            total = 0
            for item in archive.infolist():
                validate_path(item.filename, api_version)
                mode = item.external_attr >> 16
                if item.filename in files or item.is_dir() or stat.S_ISLNK(mode) or stat.S_IFMT(mode) not in (0, stat.S_IFREG) or item.flag_bits & 1:
                    raise Invalid("ZIP 含重复条目、目录、链接或加密条目")
                if item.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                    raise Invalid("ZIP 仅支持 stored/deflate 压缩")
                total += item.file_size
                if api_version == 1 and (item.file_size > MAX_FILE or total > MAX_TOTAL):
                    raise Invalid("ZIP 解压容量超限")
                if api_version == 1 and item.file_size > 65536 and item.file_size > max(1, item.compress_size) * 200:
                    raise Invalid("ZIP 条目压缩比超过 200")
                with archive.open(item) as stream:
                    content = stream.read() if api_version == 2 else stream.read(MAX_FILE + 1)
                if api_version == 1 and len(content) > MAX_FILE or len(content) != item.file_size:
                    raise Invalid("ZIP 条目大小无效")
                files[item.filename] = content
    except (zipfile.BadZipFile, NotImplementedError, RuntimeError) as error:
        raise Invalid("无效 ZIP：" + str(error)) from error
    return validate_files(files)


def build(directory, out):
    files, manifest = read_source(directory)
    data = archive_bytes(files)
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    filename = f'{manifest["id"]}-{manifest["versionCode"]}.zip'
    checksum = hashlib.sha256(data).hexdigest()
    (out / filename).write_bytes(data)
    (out / (filename + ".sha256")).write_text(f"{checksum}  {filename}\n", encoding="utf-8", newline="\n")
    entry = {key: value for key, value in manifest.items() if key in META_FIELDS}
    entry.update(file=filename, sha256=checksum, size=len(data))
    return entry


def validate_catalog(catalog):
    allowed = {"schemaVersion", "apiVersion", "plugins", "name", "description", "updatedAt", "readmeUrl", "changesUrl"}
    if not isinstance(catalog, dict) or set(catalog) - allowed or type(catalog.get("schemaVersion")) is not int or catalog["schemaVersion"] != 1 or type(catalog.get("apiVersion")) is not int or catalog["apiVersion"] not in (1, 2):
        raise Invalid("目录格式或 API 版本无效")
    entries = catalog.get("plugins")
    if not isinstance(entries, list) or len(entries) > 128:
        raise Invalid("目录最多包含 128 个插件")
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) - (META_FIELDS | {"file", "sha256", "size"}):
            raise Invalid("目录条目有未知字段")
        validate_metadata({key: value for key, value in entry.items() if key in META_FIELDS}, catalog=True)
        if entry["apiVersion"] > catalog["apiVersion"]:
            raise Invalid("目录 API 版本不能低于插件 API 版本")
        if entry["id"] in seen:
            raise Invalid("索引包含重复插件 ID")
        seen.add(entry["id"])
        filename = entry.get("file")
        if not isinstance(filename, str) or not re.fullmatch(r"[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*\.zip", filename) or any(part in (".", "..") for part in filename.split("/")):
            raise Invalid("目录 file 必须为安全相对 ZIP 路径")
        if not isinstance(entry.get("sha256"), str) or not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]):
            raise Invalid("目录 SHA-256 无效")
        maximum = MAX_TOTAL if entry["apiVersion"] == 1 else 2147483647
        if type(entry.get("size")) is not int or not 1 <= entry["size"] <= maximum:
            raise Invalid("目录 ZIP 大小无效")
    for key in allowed - {"schemaVersion", "apiVersion", "plugins"}:
        if key in catalog and (not isinstance(catalog[key], str) or len(catalog[key]) > 4096 or "\0" in catalog[key]):
            raise Invalid("目录说明字段无效：" + key)
    return catalog


def write_index(entries, out, metadata=None):
    ids = [entry["id"] for entry in entries]
    if len(set(ids)) != len(ids):
        raise Invalid("索引包含重复插件 ID")
    entries = sorted(entries, key=lambda value: value["id"])
    out = Path(out)
    catalog = dict(metadata or {})
    catalog.update(schemaVersion=1, apiVersion=max((entry["apiVersion"] for entry in entries), default=1), plugins=entries)
    validate_catalog(catalog)
    data = json_bytes(catalog)
    (out / "index.json").write_bytes(data)
    sums = [f'{entry["sha256"]}  {entry["file"]}' for entry in entries]
    sums.append(hashlib.sha256(data).hexdigest() + "  index.json")
    (out / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8", newline="\n")


def publish(directory, repository):
    """Prepare a reviewed package and local catalog; never commit, push or publish remotely."""
    directory, repository = Path(directory).resolve(), Path(repository).resolve()
    for templates in (ROOT / "templates", repository / "templates"):
        try:
            directory.relative_to(templates.resolve())
        except ValueError:
            continue
        raise Invalid("教学模板不能直接发布；请复制到自己的开发目录、修改身份并完成审查")
    files, manifest = read_source(directory)
    if "LICENSE" not in files or "README.md" not in files or not manifest.get("author", "").strip():
        raise Invalid("正式分发需要作者、包内 LICENSE 与 README.md 来源/适配说明")
    index = repository / "index.json"
    catalog = validate_catalog(decode_json(index.read_bytes())) if index.exists() else {"schemaVersion": 1, "apiVersion": 1, "plugins": []}
    data = archive_bytes(files)
    checksum = hashlib.sha256(data).hexdigest()
    previous = next((item for item in catalog["plugins"] if item["id"] == manifest["id"]), None)
    if previous is not None and (manifest["versionCode"] < previous["versionCode"] or manifest["versionCode"] == previous["versionCode"] and checksum != previous["sha256"]):
        raise Invalid("禁止降级或替换同版本的不同内容，请增加 versionCode")
    if previous is None and len(catalog["plugins"]) >= 128:
        raise Invalid("目录已达 128 个插件上限")
    entry = build(directory, repository / "packages")
    entry["file"] = "packages/" + entry["file"]
    entries = [item for item in catalog["plugins"] if item["id"] != entry["id"]] + [entry]
    write_index(entries, repository, {key: value for key, value in catalog.items() if key != "plugins"})
    return entry


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    for name in ("lint", "standard", "descriptor", "build", "build-all", "inspect", "catalog", "publish"):
        sub = subs.add_parser(name)
        if name == "build-all":
            sub.add_argument("source", type=Path, nargs="?", default=ROOT / "templates")
        else:
            sub.add_argument("source", type=Path)
        if name in ("build", "build-all"):
            sub.add_argument("--out", type=Path, default=Path("dist"))
        if name == "publish":
            sub.add_argument("--repository", type=Path, default=ROOT)
        if name == "descriptor":
            sub.add_argument("--manifest", type=Path)
            sub.add_argument("--standard", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "descriptor":
            manifest = decode_json(args.manifest.read_bytes()) if args.manifest else None
            metadata = validate_descriptor(decode_json(args.source.read_bytes()), manifest, args.standard)
            print("描述元数据与表单通过：" + metadata["id"] + "；未执行脚本，settings/hooks 语义与运行效果仍由宿主核验")
        elif args.command in ("lint", "standard"):
            files, manifest = read_source(args.source)
            archive_bytes(files)
            if args.command == "standard":
                controls = validate_source_form(files[manifest["entry"]].decode("utf-8-sig"), manifest["apiVersion"], "nativeSettings" in manifest)
                validate_standard({key: value for key, value in manifest.items() if key != "entry"}, controls)
            print("结构通过：" + manifest["id"] + "；JavaScript 语义仍由 C17 校验")
        elif args.command == "inspect":
            manifest = inspect_archive(args.source.read_bytes())
            print("ZIP 结构通过：" + manifest["id"])
        elif args.command == "catalog":
            catalog = validate_catalog(decode_json(args.source.read_bytes()))
            print("目录通过：" + str(len(catalog["plugins"])) + " 个插件")
        elif args.command == "publish":
            entry = publish(args.source, args.repository)
            print("已准备本地发行包与目录：" + entry["file"] + "；尚未提交或推送")
        else:
            sources = [args.source] if args.command == "build" else sorted(path for path in args.source.iterdir() if path.is_dir())
            if not sources:
                raise Invalid("没有插件源码")
            # Validate every package and duplicate ID before producing a partial index.
            seen = set()
            for source in sources:
                files, manifest = read_source(source)
                archive_bytes(files)
                if manifest["id"] in seen:
                    raise Invalid("重复插件 ID：" + manifest["id"])
                seen.add(manifest["id"])
            entries = [build(source, args.out) for source in sources]
            print(f"已生成 {len(entries)} 个本地开发包与校验和；未更新公开目录")
        return 0
    except (Invalid, OSError, ValueError) as error:
        print("失败：" + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
