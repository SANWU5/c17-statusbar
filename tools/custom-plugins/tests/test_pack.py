import copy
import hashlib
import importlib.util
import io
import json
import os
import stat
import struct
import tempfile
import shutil
import unittest
import warnings
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("c17_pack", ROOT / "tools" / "pack.py")
pack = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pack)


class PackagingTests(unittest.TestCase):
    def test_configuration_alias_metadata_requires_explicit_supported_contract(self):
        _, manifest = self.api2_files()
        metadata = {key: value for key, value in manifest.items() if key != "entry"}
        metadata.update(minimumHost=265, features=["config.aliases"], configurationAliases={"old_enabled": "enabled"})
        pack.validate_metadata(metadata)
        pack.validate_metadata(dict(metadata, configurationAliases={}))
        pack.validate_metadata(dict(metadata, configurationAliases={f"old_{n}": f"new_{n}" for n in range(512)}))
        invalid = [dict(metadata, apiVersion=1), dict(metadata, features=[]), dict(metadata, minimumHost=264),
                   {key: value for key, value in metadata.items() if key != "features"},
                   {key: value for key, value in metadata.items() if key != "minimumHost"}]
        for aliases in (None, [], "old_enabled", {"old": False}, {"bad key": "enabled"}, {"old": "bad key"},
                        {"constructor": "enabled"}, {"old": "prototype"}, {"old\n": "enabled"}, {"old": "enabled\n"}, {"old": "old"},
                        {"first": "second", "second": "enabled"}, {"first": "enabled", "second": "enabled"},
                        {f"old_{n}": f"new_{n}" for n in range(513)}):
            invalid.append(dict(metadata, configurationAliases=aliases))
        for value in invalid:
            with self.subTest(metadata=value), self.assertRaises(pack.Invalid):
                pack.validate_metadata(value)
        with self.assertRaises(pack.Invalid):
            pack.validate_metadata(metadata, catalog=True)

    def test_configuration_alias_targets_are_current_persistent_controls(self):
        _, manifest = self.api2_files()
        metadata = {key: value for key, value in manifest.items() if key != "entry"}
        metadata.update(minimumHost=265, features=["config.aliases"], configurationAliases={"old_enabled": "enabled"})
        controls = [{"type": "switch", "key": "enabled", "title": "Enable"}]
        descriptor = dict(metadata, ui=controls)
        pack.validate_descriptor(descriptor)
        for aliases in ({"enabled": "enabled"}, {"enabled": "missing"}, {"old_enabled": "missing"}):
            with self.subTest(aliases=aliases), self.assertRaises(pack.Invalid):
                pack.validate_descriptor(dict(descriptor, configurationAliases=aliases))
        for extra in ({"type": "text", "key": "old_enabled", "title": "Old text"},
                      {"type": "action", "key": "old_enabled", "title": "Old action"},
                      {"type": "status", "key": "old_enabled", "title": "Old status"}):
            with self.subTest(source=extra), self.assertRaises(pack.Invalid):
                pack.validate_descriptor(dict(descriptor, ui=controls + [extra]))
        for extra in ({"type": "text", "key": "target", "title": "Text"},
                      {"type": "action", "key": "target", "title": "Action"},
                      {"type": "status", "key": "target", "title": "Status"},
                      {"type": "preset", "key": "target", "title": "Preset", "values": {"enabled": True}}):
            with self.subTest(target=extra), self.assertRaises(pack.Invalid):
                pack.validate_descriptor(dict(descriptor, ui=controls + [extra], configurationAliases={"old": "target"}))
        many = dict(descriptor, configurationAliases={f"old_{n}": f"new_{n}" for n in range(512)},
                    ui=[{"type": "switch", "key": f"new_{n}", "title": f"Current {n}"} for n in range(512)])
        pack.validate_descriptor(many)

    def test_configuration_aliases_match_manifest_and_validate_packaged_form(self):
        files, original = self.api2_files()
        manifest = dict(original, minimumHost=265, features=["config.aliases"], configurationAliases={"old_enabled": "enabled"})
        metadata = {key: value for key, value in manifest.items() if key != "entry"}
        controls = [{"type": "switch", "key": "enabled", "title": "Enable"}]
        files["plugin.json"] = pack.json_bytes(manifest)
        files["main.js"] = ("var plugin = " + json.dumps(metadata) + ";\nplugin.ui = " + json.dumps(controls)
                            + ";\nC17.define(plugin);").encode()
        self.assertEqual(manifest, pack.validate_files(files))
        self.assertEqual(manifest, pack.inspect_archive(pack.archive_bytes(files)))
        mismatched = dict(files)
        mismatched["plugin.json"] = pack.json_bytes(dict(manifest, configurationAliases={"another": "enabled"}))
        with self.assertRaisesRegex(pack.Invalid, "不一致"):
            pack.validate_files(mismatched)
        with self.assertRaisesRegex(pack.Invalid, "不一致"):
            pack.validate_descriptor(dict(metadata, ui=controls), dict(manifest, configurationAliases={"another": "enabled"}))
        invalid = dict(files)
        invalid["main.js"] = ("var plugin = " + json.dumps(metadata) + ";\nplugin.ui = "
                              + json.dumps(controls + [{"type": "status", "key": "old_enabled", "title": "Existing"}])
                              + ";\nC17.define(plugin);").encode()
        with self.assertRaises(pack.Invalid):
            pack.validate_files(invalid)

    def test_edit_activation_targets_are_declared_switches(self):
        controls = [{"type": "switch", "key": "gate", "title": "Gate"},
                    {"type": "slider", "key": "value", "title": "Value", "min": 0, "max": 100, "enableOnEdit": ["gate"]}]
        pack.validate_form(controls)
        for targets in ([], ["missing"], ["value"], ["gate", "gate"], "gate", [False]):
            altered = copy.deepcopy(controls); altered[1]["enableOnEdit"] = targets
            with self.subTest(targets=targets), self.assertRaises(pack.Invalid): pack.validate_form(altered)

    def test_unbounded_input_is_slider_only_and_frees_presets(self):
        slider = {"type": "slider", "key": "value", "title": "Value", "min": 0, "max": 100, "default": 50, "unboundedInput": True}
        preset = {"type": "preset", "key": "far", "title": "Far", "values": {"value": 900}}
        pack.validate_form([slider, preset])
        for bad in (dict(slider, unboundedInput=1), dict(slider, default=150),
                    {"type": "input", "key": "value", "title": "Value", "unboundedInput": True}):
            with self.subTest(control=bad), self.assertRaises(pack.Invalid): pack.validate_form([bad])
        with self.assertRaises(pack.Invalid): pack.validate_form([dict(slider, unboundedInput=False), preset])
        with self.assertRaises(pack.Invalid): pack.validate_form([slider, dict(preset, values={"value": "far"})])

    def files(self):
        return pack.read_source(ROOT / "templates" / "clock-seconds")[0]

    def api2_files(self):
        metadata = {
            "id": "example.runtime.plugin", "name": "Runtime plugin", "version": "1.0.0",
            "versionCode": 1, "apiVersion": 2, "description": "API 2 package fixture",
            "author": "C17 test", "capabilities": ["runtime"],
            "runtime": {"entry": "runtime.js"},
        }
        ui = [{"type": "switch", "key": "enabled", "title": "Enable", "default": False}]
        source = ("var plugin = " + json.dumps(metadata, ensure_ascii=False) + ";\n"
                  + "plugin.ui = " + json.dumps(ui) + ";\n"
                  + "plugin.settings = {}; plugin.hooks = []; C17.define(plugin);\n")
        manifest = dict(metadata, entry="main.js")
        files = {
            "plugin.json": pack.json_bytes(manifest), "main.js": source.encode("utf-8"),
            "runtime.js": b"C17.log('API 2 runtime');", "classes.dex": b"dex\\n\\0" + os.urandom(pack.MAX_TOTAL + 1),
            "README.md": b"API 2 fixture", "LICENSE": b"test license",
        }
        files.update({f"resources/data-{index}.bin": os.urandom(1) for index in range(40)})
        return files, manifest

    def mutate_manifest(self, files, **values):
        manifest = pack.decode_json(files["plugin.json"])
        manifest.update(values)
        files["plugin.json"] = pack.json_bytes(manifest)
        return files

    def zip_bytes(self, entries):
        buffer = io.BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
                for name, value in entries:
                    archive.writestr(name, value)
        return buffer.getvalue()

    def test_all_developer_templates_build_and_inspect(self):
        for source in sorted((ROOT / "templates").iterdir()):
            with self.subTest(example=source.name):
                files, manifest = pack.read_source(source)
                self.assertEqual(manifest, pack.inspect_archive(pack.archive_bytes(files)))

    def test_identical_files_in_different_order_make_identical_zip(self):
        files = self.files()
        self.assertEqual(pack.archive_bytes(files), pack.archive_bytes(dict(reversed(list(files.items())))))
        with zipfile.ZipFile(io.BytesIO(pack.archive_bytes(files))) as archive:
            self.assertEqual(sorted(files), archive.namelist())
            self.assertTrue(all(item.date_time == (1980, 1, 1, 0, 0, 0) for item in archive.infolist()))

    def test_catalog_hashes_match_actual_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            entries = [pack.build(source, out) for source in sorted((ROOT / "templates").iterdir())]
            pack.write_index(entries, out)
            catalog = pack.decode_json((out / "index.json").read_bytes())
            self.assertEqual(len(entries), len(catalog["plugins"]))
            for item in catalog["plugins"]:
                data = (out / item["file"]).read_bytes()
                self.assertEqual(item["sha256"], hashlib.sha256(data).hexdigest())
                self.assertEqual(item["size"], len(data))
            for line in (out / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
                digest, name = line.split("  ", 1)
                self.assertEqual(digest, hashlib.sha256((out / name).read_bytes()).hexdigest())

    def test_manifest_source_identity_mismatch_is_rejected(self):
        with self.assertRaisesRegex(pack.Invalid, "不一致"):
            pack.validate_files(self.mutate_manifest(self.files(), name="不匹配"))

    def test_unknown_manifest_field_is_rejected(self):
        with self.assertRaises(pack.Invalid):
            pack.validate_files(self.mutate_manifest(self.files(), network=True))

    def test_optional_detail_metadata_is_strict_and_legacy_metadata_still_works(self):
        for api in (1, 2):
            original = pack.decode_json(self.files()["plugin.json"]) if api == 1 else self.api2_files()[1]
            pack.validate_metadata(original, manifest=True)
            metadata = dict(original, minimumHost=210, usage="先启用，再调整设置；停用恢复原样。",
                            features=["ui.groups", "scope.systemui"], scopes=["com.android.systemui", "android"])
            pack.validate_metadata(metadata, manifest=True)
            for field, value in (("minimumHost", True), ("minimumHost", 0), ("minimumHost", 2147483648),
                                 ("usage", "x" * 4097), ("usage", 1), ("features", ["runtime.dex"] * 2),
                                 ("features", ["bad feature"]), ("features", ["x" * 81]),
                                 ("features", ["feature" + str(index) for index in range(65)]),
                                 ("scopes", ["com.android.systemui"] * 2), ("scopes", ["systemui"]),
                                 ("scopes", ["com..android"]), ("scopes", ["com.android:remote"]),
                                 ("scopes", ["com." + "x" * 237]),
                                 ("scopes", [None]), ("features", [[]])):
                with self.subTest(api=api, field=field, value=value), self.assertRaises(pack.Invalid):
                    pack.validate_metadata(dict(metadata, **{field: value}), manifest=True)

    def test_grouped_controls_keep_state_and_action_contracts(self):
        controls = [{"type": "switch", "key": "enabled", "title": "启用", "section": "features", "group": "外观"},
                    {"type": "action", "key": "capture", "title": "保存", "section": "management", "group": "数据"},
                    {"type": "status", "key": "saved_at", "title": "保存时间", "group": "数据"}]
        pack.validate_form(controls, 2)
        pack.validate_form(controls[:1], 1)
        for field, value in (("section", "overview"), ("section", 1), ("group", "x" * 121), ("group", [])):
            with self.subTest(field=field), self.assertRaises(pack.Invalid):
                pack.validate_form([dict(controls[0], **{field: value})], 2)
        with self.assertRaises(pack.Invalid):
            pack.validate_form(controls[:1] + [dict(controls[1], value=True)], 2)

    def test_nested_color_and_preset_form_contract(self):
        color = {"type": "color", "key": "tint", "title": "颜色", "default": "#80112233",
                 "groupPath": ["效果调节", "部件", "浅色遮罩"]}
        amount = {"type": "slider", "key": "amount", "title": "强度", "min": 0, "max": 100, "default": 40}
        preset = {"type": "preset", "key": "clear", "title": "通透", "values": {"tint": "#FFFFFF", "amount": 20}}
        for api in (1, 2):
            pack.validate_form([color, amount, preset], api)
        for path in ([], ["a"] * 4, "a/b", [None], [" "], ["a\0b"], ["x" * 121]):
            with self.subTest(path=path), self.assertRaises(pack.Invalid):
                pack.validate_form([dict(color, groupPath=path)], 2)
        for value in ("red", "#ABC", "#FFFFFF ", "#00112233extra", 1, True, None):
            with self.subTest(value=value), self.assertRaises(pack.Invalid):
                pack.validate_form([dict(color, default=value)], 2)
        for values in ({}, {"missing": 1}, {"clear": True}, {"amount": 101}, {"tint": "red"}, {str(i): i for i in range(65)}):
            with self.subTest(values=values), self.assertRaises(pack.Invalid):
                pack.validate_form([color, amount, dict(preset, values=values)], 2)
        with self.assertRaises(pack.Invalid):
            pack.validate_form([color, dict(preset, default=True)], 2)
        with self.assertRaises(pack.Invalid):
            pack.validate_form([dict(color, values={"tint": "#FFFFFF"})], 2)

    def test_native_settings_requires_api2_valid_resource_and_complete_abi_metadata(self):
        files, original = self.api2_files()
        native = {"dex": "settings/classes.dex", "className": "example.plugin.SettingsView"}
        metadata = dict(original, nativeSettings=native, minimumHost=210)
        files["plugin.json"] = pack.json_bytes(metadata)
        files[native["dex"]] = b"dex\n\0native-settings-fixture"
        source_metadata = {key: value for key, value in metadata.items() if key != "entry"}
        files["main.js"] = ("var plugin = " + json.dumps(source_metadata) + ";\nplugin.ui = [];\nC17.define(plugin);").encode()
        self.assertEqual(metadata, pack.validate_files(files))
        self.assertEqual(metadata, pack.inspect_archive(pack.archive_bytes(files)))
        missing = dict(files)
        del missing[native["dex"]]
        with self.assertRaisesRegex(pack.Invalid, "DEX/JAR"):
            pack.validate_files(missing)
        for value in ({"dex": "../settings.dex", "className": native["className"]},
                      {"dex": "settings.js", "className": native["className"]},
                      {"dex": "/settings.jar", "className": native["className"]},
                      {"dex": "设置.dex", "className": native["className"]},
                      {"dex": "my settings.jar", "className": native["className"]},
                      {"dex": "settings.jar", "className": "SettingsView"},
                      {"dex": "settings.jar", "className": "example.Bad-Class"},
                      {"dex": "settings.jar", "className": native["className"], "method": "other"}):
            with self.subTest(native=value), self.assertRaises(pack.Invalid):
                pack.validate_metadata(dict(metadata, nativeSettings=value), manifest=True)
        pack.validate_metadata(dict(metadata, nativeSettings={"dex": "settings.jar", "className": "example.Settings$Entry"}), manifest=True)
        legacy = dict(pack.decode_json(self.files()["plugin.json"]), nativeSettings=native)
        with self.assertRaises(pack.Invalid):
            pack.validate_metadata(legacy, manifest=True)
        with self.assertRaises(pack.Invalid):
            pack.validate_form([], 1, native_settings=True)

    def test_complete_detail_reorganization_uses_the_same_host_contract(self):
        files, original = self.api2_files()
        metadata = {key: value for key, value in original.items() if key != "entry"}
        metadata.update(minimumHost=210, features=["ui.groups", "runtime.actions", "runtime.status"],
                        usage="按说明启用并调整；停用恢复原样。", scopes=["com.android.systemui"])
        controls = [
            {"type": "switch", "key": "enabled", "title": "启用功能", "default": False, "group": "基本设置"},
            {"type": "slider", "key": "intensity", "title": "强度", "min": 0, "max": 100, "default": 50},
            {"type": "select", "key": "mode", "title": "模式", "options": [{"value": "system", "label": "系统"},
             {"value": "custom", "label": "自定义"}], "default": "system"},
            {"type": "input", "key": "note", "title": "备注", "default": "", "section": "management"},
            {"type": "action", "key": "capture", "title": "保存数据", "section": "management"},
            {"type": "status", "key": "saved_at", "title": "保存时间", "section": "management"},
        ]
        original_controls = copy.deepcopy(controls)
        for version in (1, 2):
            metadata.update(version=f"1.0.{version}", versionCode=version)
            if version == 2:
                controls = list(reversed(controls))
                for item in controls:
                    item.update(section="management" if item["key"] == "intensity" else "features", group="新分组")
            manifest = dict(metadata, entry="main.js")
            files["plugin.json"] = pack.json_bytes(manifest)
            files["main.js"] = ("var plugin = " + json.dumps(metadata, ensure_ascii=False) + ";\nplugin.ui = "
                                + json.dumps(controls, ensure_ascii=False)
                                + ";\nplugin.settings = {}; plugin.hooks = []; C17.define(plugin);").encode("utf-8")
            self.assertEqual(manifest, pack.validate_files(files))
            self.assertEqual(manifest, pack.inspect_archive(pack.archive_bytes(files)))
            self.assertEqual(210, manifest["minimumHost"])
            persisted = {item["key"]: {key: value for key, value in item.items() if key not in ("section", "group")}
                         for item in controls}
            self.assertEqual({item["key"]: {key: value for key, value in item.items() if key not in ("section", "group")}
                              for item in original_controls}, persisted)
        for field, value in (("selectedScopes", ["com.android.systemui"]), ("scopeSelected", True)):
            with self.subTest(field=field), self.assertRaises(pack.Invalid):
                pack.validate_metadata(dict(manifest, **{field: value}), manifest=True)

    def test_new_detail_metadata_must_match_source_and_manifest(self):
        for field, value in (("minimumHost", 210), ("features", ["runtime.java"]), ("usage", "使用说明"),
                             ("scopes", ["com.android.systemui"])):
            files, manifest = self.api2_files()
            files["plugin.json"] = pack.json_bytes(dict(manifest, **{field: value}))
            with self.subTest(field=field), self.assertRaisesRegex(pack.Invalid, "不一致"):
                pack.validate_files(files)

    def test_detail_metadata_stays_in_package_and_does_not_change_catalog_shape(self):
        files, original = self.api2_files()
        manifest = dict(original, minimumHost=210, usage="使用说明", features=["runtime.dex"], scopes=["com.android.systemui"])
        files["plugin.json"] = pack.json_bytes(manifest)
        metadata = {key: value for key, value in manifest.items() if key != "entry"}
        files["main.js"] = ("var plugin = " + json.dumps(metadata) + ";\nplugin.ui = "
                            + json.dumps([{"type": "switch", "key": "enabled", "title": "Enable"}])
                            + ";\nC17.define(plugin);").encode()
        with tempfile.TemporaryDirectory() as temporary:
            source, out = Path(temporary) / "source", Path(temporary) / "out"
            source.mkdir()
            for name, content in files.items():
                target = source / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            entry = pack.build(source, out)
            self.assertTrue(set(entry).issubset(pack.META_FIELDS | {"file", "sha256", "size"}))
            pack.write_index([entry], out)
            catalog = pack.decode_json((out / "index.json").read_bytes())
            pack.validate_catalog(catalog)
            self.assertEqual(manifest, pack.inspect_archive((out / entry["file"]).read_bytes()))
            for field in ("minimumHost", "features", "usage", "scopes", "nativeSettings"):
                with self.subTest(field=field), self.assertRaises(pack.Invalid):
                    pack.validate_catalog(dict(catalog, plugins=[dict(entry, **{field: manifest.get(field, {})})]))

    def test_boolean_is_not_a_version_integer(self):
        with self.assertRaises(pack.Invalid):
            pack.validate_files(self.mutate_manifest(self.files(), versionCode=True))

    def test_invalid_api_version_is_rejected(self):
        with self.assertRaises(pack.Invalid):
            pack.validate_files(self.mutate_manifest(self.files(), apiVersion=2))

    def test_api2_accepts_unrestricted_binary_package_resources(self):
        files, manifest = self.api2_files()
        self.assertEqual(manifest, pack.validate_files(files))
        data = pack.archive_bytes(files)
        self.assertEqual(manifest, pack.inspect_archive(data))
        self.assertGreater(len(data), pack.MAX_TOTAL)
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "runtime-plugin"
            source.mkdir()
            for name, content in files.items():
                target = source / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            reread, read_manifest = pack.read_source(source)
            self.assertEqual(manifest, read_manifest)
            self.assertEqual(files["classes.dex"], reread["classes.dex"])

    def test_api2_translation_tables_are_packed_as_display_data(self):
        files, manifest = self.api2_files()
        files["i18n/en.json"] = json.dumps({"启用": "Enable", "保存当前记录": "Save the current record"}, ensure_ascii=False).encode("utf-8")
        files["i18n/zh-Hant.json"] = json.dumps({"启用": "啟用"}, ensure_ascii=False).encode("utf-8")
        files["i18n/pt-BR.json"] = b"{}"
        self.assertEqual(manifest, pack.validate_files(files))
        data = pack.archive_bytes(files)
        self.assertEqual(manifest, pack.inspect_archive(data))
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            self.assertEqual(files["i18n/en.json"], archive.read("i18n/en.json"))

    def test_translation_tables_are_strict_language_tables(self):
        bad = {
            "i18n/EN.json": b"{}", "i18n/en_US.json": b"{}", "i18n/source.json": b"{}",
            "i18n/readme.md": b"notes", "i18n/sub/en.json": b"{}", "i18n/english.json": b"{}",
        }
        for name, data in bad.items():
            files, _ = self.api2_files()
            files[name] = data
            with self.subTest(name=name), self.assertRaises(pack.Invalid):
                pack.validate_files(files)
        contents = {
            "array": b"[]", "number value": b'{"a": 1}', "blank value": b'{"a": "  "}', "empty key": b'{"": "x"}',
            "nul": b'{"a": "b\\u0000"}', "duplicate key": b'{"a": "x", "a": "y"}', "not json": b"{",
            "too many": json.dumps({f"k{i}": "v" for i in range(pack.MAX_I18N_ENTRIES + 1)}).encode("utf-8"),
            "too large": b'{"k": "' + b"x" * pack.MAX_I18N_BYTES + b'"}',
        }
        for label, data in contents.items():
            files, _ = self.api2_files()
            files["i18n/en.json"] = data
            with self.subTest(content=label), self.assertRaises(pack.Invalid):
                pack.validate_files(files)
        files, _ = self.api2_files()
        files.update({f"i18n/l{index:02d}.json" if index < 26 else f"i18n/x{chr(97 + index % 26)}{chr(97 + index // 26)}.json": b"{}"
                      for index in range(pack.MAX_I18N_FILES + 1)})
        with self.assertRaises(pack.Invalid):
            pack.validate_files(files)

    def test_api1_packages_do_not_carry_translation_tables(self):
        files = self.files()
        files["i18n/en.json"] = b'{"a": "b"}'
        with self.assertRaises(pack.Invalid):
            pack.validate_files(files)

    def test_api2_catalog_allows_java_array_sized_packages(self):
        files, manifest = self.api2_files()
        entry = {key: value for key, value in manifest.items() if key not in ("entry", "runtime")}
        entry.update(file="packages/runtime.zip", sha256="a" * 64, size=pack.MAX_TOTAL + 1)
        catalog = {"schemaVersion": 1, "apiVersion": 2, "plugins": [entry]}
        self.assertEqual(catalog, pack.validate_catalog(catalog))

    def test_api2_complete_form_is_not_limited_to_64_items(self):
        controls = [{"type": "switch", "key": f"setting_{index}", "title": f"Setting {index}"} for index in range(80)]
        pack.validate_source_form("plugin.ui = " + json.dumps(controls) + ";", api_version=2)
        with self.assertRaisesRegex(pack.Invalid, "API 1"):
            pack.validate_form(controls)

    def test_api2_long_metadata_entry_path_matches_host(self):
        files, manifest = self.api2_files()
        entry = "resources/" + "nested/" * 20 + "main.js"
        manifest["entry"] = entry
        files[entry] = files.pop("main.js")
        files["plugin.json"] = pack.json_bytes(manifest)
        self.assertGreater(len(entry), 128)
        self.assertEqual(manifest, pack.validate_files(files))

    def test_api2_runtime_and_capability_are_both_required(self):
        _, manifest = self.api2_files()
        for changed in (dict(manifest, capabilities=[]), {key: value for key, value in manifest.items() if key != "runtime"},
                        dict(manifest, runtime={"source": "  "}), dict(manifest, runtime={"entry": "runtime.js", "source": "code"})):
            with self.subTest(manifest=changed), self.assertRaises(pack.Invalid):
                pack.validate_metadata(changed, manifest=True)

    def test_api2_build_catalog_strips_private_runtime_source(self):
        files, manifest = self.api2_files()
        manifest["runtime"] = {"source": "C17.log('must stay inside package');"}
        files["plugin.json"] = pack.json_bytes(manifest)
        meta = {key: value for key, value in manifest.items() if key != "entry"}
        files["main.js"] = ("var plugin = " + json.dumps(meta) + ";\nplugin.ui = "
                            + json.dumps([{"type": "switch", "key": "enabled", "title": "Enable"}])
                            + ";\nC17.define(plugin);").encode()
        with tempfile.TemporaryDirectory() as temporary:
            source, out = Path(temporary) / "source", Path(temporary) / "out"
            source.mkdir()
            for name, content in files.items():
                target = source / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            entry = pack.build(source, out)
            self.assertNotIn("runtime", entry)
            self.assertNotIn("entry", entry)
            pack.write_index([entry], out)
            catalog = pack.decode_json((out / "index.json").read_bytes())
            self.assertEqual(2, catalog["apiVersion"])
            self.assertNotIn("must stay inside package", json.dumps(catalog))
            for field, value in (("runtime", {"source": "code"}), ("entry", "runtime.js")):
                with self.subTest(field=field), self.assertRaises(pack.Invalid):
                    pack.validate_catalog(dict(catalog, plugins=[dict(entry, **{field: value})]))
            with self.assertRaisesRegex(pack.Invalid, "不能低于"):
                pack.validate_catalog(dict(catalog, apiVersion=1))

    def test_duplicate_capabilities_are_rejected(self):
        with self.assertRaises(pack.Invalid):
            pack.validate_files(self.mutate_manifest(self.files(), capabilities=["settings", "settings"]))

    def test_duplicate_json_fields_are_rejected(self):
        with self.assertRaisesRegex(pack.Invalid, "重复"):
            pack.decode_json(b'{"id":"one","id":"two"}')

    def test_missing_entry_is_rejected(self):
        files = self.files()
        del files["main.js"]
        with self.assertRaisesRegex(pack.Invalid, "不存在"):
            pack.validate_files(files)

    def test_unsafe_paths_and_extensions_are_rejected(self):
        for name in ("../main.js", "/main.js", "a/../b.js", "a\\b.js", "C:/main.js", "a//b.js", "a/./b.js", "payload.dex", chr(0) + ".js"):
            with self.subTest(name=name), self.assertRaises(pack.Invalid):
                pack.validate_path(name)

    def test_oversized_source_is_rejected_before_packaging(self):
        files = self.files()
        files["main.js"] = b"x" * (pack.MAX_FILE + 1)
        with self.assertRaises(pack.Invalid):
            pack.validate_files(files)

    def test_too_many_files_are_rejected(self):
        files = self.files()
        files.update({f"extra-{number}.txt": b"x" for number in range(33)})
        with self.assertRaises(pack.Invalid):
            pack.validate_files(files)

    def test_duplicate_zip_entry_is_rejected(self):
        files = self.files()
        data = self.zip_bytes(list(files.items()) + [("main.js", files["main.js"])])
        with self.assertRaisesRegex(pack.Invalid, "重复"):
            pack.inspect_archive(data)

    def test_zip_traversal_is_rejected(self):
        with self.assertRaises(pack.Invalid):
            pack.inspect_archive(self.zip_bytes(list(self.files().items()) + [("../bad.js", b"bad")]))

    def test_zip_format_boundary_and_header_identity_match_host(self):
        data = bytearray(self.zip_bytes(list(self.files().items())))
        end = data.rfind(b"PK\x05\x06")
        malformed = []
        split = bytearray(data)
        struct.pack_into("<H", split, end + 4, 1)
        malformed.append(split)
        zip64 = bytearray(data)
        struct.pack_into("<H", zip64, end + 10, 65535)
        malformed.append(zip64)
        renamed = bytearray(data)
        renamed[30] ^= 1
        malformed.append(renamed)
        for changed in malformed:
            with self.subTest(header=changed[:32]), self.assertRaises(pack.Invalid):
                pack.inspect_archive(changed)

    def test_case_insensitive_duplicate_paths_are_rejected(self):
        with self.assertRaisesRegex(pack.Invalid, "重复"):
            pack.inspect_archive(self.zip_bytes(list(self.files().items()) + [("MAIN.js", b"bad")]))

    def test_only_root_plugin_json_is_accepted(self):
        for name in ("extra.json", "nested/plugin.json", "main.JS", "README.MD", "trailing./good.js", "trailing /good.js"):
            with self.subTest(name=name), self.assertRaises(pack.Invalid):
                pack.validate_path(name)

    def test_every_packaged_text_file_must_be_utf8_without_nul(self):
        for data in (bytes([255]), b"text" + bytes([0])):
            with self.subTest(data=data), self.assertRaises(pack.Invalid):
                pack.inspect_archive(self.zip_bytes(list(self.files().items()) + [("bad.txt", data)]))

    def test_unsupported_zip_compression_is_rejected(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w", zipfile.ZIP_BZIP2) as archive:
            for name, data in self.files().items():
                archive.writestr(name, data)
        with self.assertRaisesRegex(pack.Invalid, "压缩"):
            pack.inspect_archive(buffer.getvalue())

    def test_zip_symlink_is_rejected(self):
        link = zipfile.ZipInfo("link.js")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        data = self.zip_bytes(list(self.files().items()) + [(link, b"main.js")])
        with self.assertRaisesRegex(pack.Invalid, "链接"):
            pack.inspect_archive(data)

    def test_zip_directory_entry_is_rejected(self):
        with self.assertRaises(pack.Invalid):
            pack.inspect_archive(self.zip_bytes(list(self.files().items()) + [("folder/", b"")]))

    def test_zip_high_compression_ratio_is_rejected(self):
        with self.assertRaisesRegex(pack.Invalid, "压缩比"):
            pack.inspect_archive(self.zip_bytes(list(self.files().items()) + [("large.txt", b"0" * 100000)]))

    def test_duplicate_catalog_ids_are_rejected(self):
        with tempfile.TemporaryDirectory() as out:
            entry = pack.build(ROOT / "templates" / "clock-seconds", out)
            with self.assertRaises(pack.Invalid):
                pack.write_index([entry, copy.deepcopy(entry)], out)

    def test_standard_source_header_is_required_without_executing_code(self):
        files = self.files()
        files["main.js"] = b"throw new Error('must never execute'); C17.define({});"
        with self.assertRaisesRegex(pack.Invalid, "源码头"):
            pack.validate_files(files)

    def test_complete_configuration_is_required_by_standard_builder(self):
        files = self.files()
        source = files["main.js"].decode("utf-8")
        start = source.index("plugin.ui = ")
        end = source.index("\n];", start) + 3
        for form in ([], [{"type": "text", "title": "说明"}], [{"type": "button", "title": "执行", "key": "action"}]):
            with self.subTest(form=form), self.assertRaisesRegex(pack.Invalid, "完整设置"):
                changed = dict(files)
                changed["main.js"] = (source[:start] + "plugin.ui = " + json.dumps(form, ensure_ascii=False) + ";" + source[end:]).encode("utf-8")
                pack.validate_files(changed)

    def test_complete_configuration_types_defaults_and_units(self):
        for control in ({"type": "switch", "title": "开关", "key": "enabled"},
                        {"type": "slider", "title": "透明度", "key": "opacity", "min": 0, "max": 100, "unit": "%"},
                        {"type": "input", "title": "文字", "key": "label"},
                        {"type": "select", "title": "模式", "key": "mode", "options": [{"value": "a", "label": "A"}]}):
            pack.validate_form([control])
        for control in ({"type": "switch", "title": "开关", "key": "enabled", "unit": "%"},
                        {"type": "switch", "title": "开关", "key": "enabled", "default": "yes"},
                        {"type": "slider", "title": "透明度", "key": "opacity", "min": 0, "max": 100, "unit": "x" * 17},
                        {"type": "slider", "title": "透明度", "key": "opacity", "min": 0, "max": 100, "unit": 1},
                        {"type": "slider", "title": "透明度", "key": "opacity", "min": 0, "max": 100, "default": 101},
                        {"type": "select", "title": "模式", "key": "mode", "options": [{"value": "a", "label": "A"}], "default": "b"}):
            with self.subTest(control=control), self.assertRaises(pack.Invalid):
                pack.validate_form([control])

    def test_runtime_form_controls_do_not_define_persisted_state(self):
        controls = [{"type": "switch", "title": "配置", "key": "enabled"},
                    {"type": "action", "title": "执行", "key": "capture"},
                    {"type": "status", "title": "保存时间", "key": "saved_at"}]
        pack.validate_form(controls, 2)
        with self.assertRaises(pack.Invalid):
            pack.validate_form(controls, 1)
        with self.assertRaisesRegex(pack.Invalid, "完整设置"):
            pack.validate_form(controls[1:], 2)
        for kind in ("action", "status"):
            for field, value in (("default", True), ("value", 1), ("options", []), ("min", 0),
                                 ("max", 1), ("step", 1), ("unit", "%")):
                with self.subTest(kind=kind, field=field), self.assertRaises(pack.Invalid):
                    pack.validate_form(controls[:1] + [{"type": kind, "title": "运行", "key": "runtime", field: value}], 2)
        with self.assertRaises(pack.Invalid):
            pack.validate_form(controls[:2] + [{"type": "status", "title": "重复", "key": "capture"}], 2)

    def test_api2_runtime_form_package_roundtrips_without_executing_action(self):
        files, manifest = self.api2_files()
        controls = [{"type": "switch", "title": "Enable", "key": "enabled"},
                    {"type": "action", "title": "Capture", "key": "capture"},
                    {"type": "status", "title": "Saved time", "key": "saved_at"}]
        source = files["main.js"].decode("utf-8")
        start = source.index("plugin.ui = ")
        end = source.index(";\n", start) + 1
        files["main.js"] = (source[:start] + "plugin.ui = " + json.dumps(controls) + ";" + source[end:]).encode("utf-8")
        files["runtime.js"] = b"C17.onAction('capture', function () { throw new Error('must not execute at build'); });"
        self.assertEqual(manifest, pack.validate_files(files))
        self.assertEqual(manifest, pack.inspect_archive(pack.archive_bytes(files)))

    def test_builder_requires_literal_ui_without_running_script(self):
        for source in ("plugin.ui = makeUntrustedForm();", "throw new Error('must not run');", "plugin.ui = []; plugin.ui = [];", 'plugin.ui = [{"type":"input","title":"文字","key":"label","default":NaN}];'):
            with self.subTest(source=source), self.assertRaises(pack.Invalid):
                pack.validate_source_form(source)

    def test_public_catalog_is_deliberately_empty(self):
        catalog = pack.validate_catalog(pack.decode_json((ROOT / "index.json").read_bytes()))
        self.assertEqual([], catalog["plugins"])
        self.assertEqual("C17 扩展插件", catalog["name"])
        self.assertEqual("https://github.com/SANWU5/c17-plugins/blob/main/README.md", catalog["readmeUrl"])
        self.assertEqual("https://github.com/SANWU5/c17-plugins/blob/main/CHANGELOG.md", catalog["changesUrl"])
        self.assertEqual([], list((ROOT / "packages").glob("*.zip")))

    def test_default_developer_build_does_not_publish_catalog(self):
        with tempfile.TemporaryDirectory() as temporary:
            out = Path(temporary) / "dist"
            before = (ROOT / "index.json").read_bytes()
            self.assertEqual(0, pack.main(["build-all", "--out", str(out)]))
            self.assertEqual(len(list((ROOT / "templates").iterdir())), len(list(out.glob("*.zip"))))
            self.assertFalse((out / "index.json").exists())
            self.assertEqual(before, (ROOT / "index.json").read_bytes())

    def test_direct_template_publish_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, self.assertRaisesRegex(pack.Invalid, "教学模板"):
            pack.publish(ROOT / "templates" / "clock-seconds", Path(temporary))

    def test_explicit_publish_requires_license_and_uses_package_relative_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            source = base / "development"
            shutil.copytree(ROOT / "templates" / "clock-seconds", source)
            repository = base / "repository"
            with self.assertRaisesRegex(pack.Invalid, "LICENSE"):
                pack.publish(source, repository)
            shutil.copyfile(ROOT / "LICENSE", source / "LICENSE")
            entry = pack.publish(source, repository)
            self.assertTrue(entry["file"].startswith("packages/"))
            data = (repository / entry["file"]).read_bytes()
            self.assertEqual(entry["sha256"], hashlib.sha256(data).hexdigest())
            self.assertEqual([entry], pack.validate_catalog(pack.decode_json((repository / "index.json").read_bytes()))["plugins"])
            # Same immutable content is safe to prepare again; changed same-version content is not.
            self.assertEqual(entry, pack.publish(source, repository))
            with (source / "README.md").open("a", encoding="utf-8") as output:
                output.write("\nChanged publication content\n")
            with self.assertRaisesRegex(pack.Invalid, "同版本"):
                pack.publish(source, repository)

    def test_catalog_rejects_absolute_traversal_query_and_fragment_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            item = pack.build(ROOT / "templates" / "clock-seconds", Path(temporary))
            for name in ("https://bad.example/a.zip", "../a.zip", "/a.zip", "packages/a.zip?q=x", "packages/a.zip#x", "packages\\a.zip"):
                with self.subTest(name=name), self.assertRaises(pack.Invalid):
                    pack.validate_catalog({"schemaVersion": 1, "apiVersion": 1, "plugins": [dict(item, file=name)]})


if __name__ == "__main__":
    unittest.main()
