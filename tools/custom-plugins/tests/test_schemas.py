"""Optional JSON Schema parity checks; the packer itself remains standard-library only."""
import copy
import json
import unittest
from pathlib import Path

try:
    from jsonschema import Draft202012Validator
except ImportError:
    Draft202012Validator = None

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipIf(Draft202012Validator is None, "Install jsonschema to check the published schemas")
class SchemaParityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schemas = {name: json.loads((ROOT / "schema" / (name + ".schema.json")).read_text(encoding="utf-8"))
                       for name in ("manifest", "descriptor", "catalog")}
        cls.validators = {name: Draft202012Validator(schema) for name, schema in cls.schemas.items()}

    def test_edit_activation_shape(self):
        descriptor = self.descriptor()
        descriptor["ui"].append({"type": "slider", "key": "value", "title": "Value", "min": 0, "max": 100, "enableOnEdit": ["option_0"]})
        self.validators["descriptor"].validate(descriptor)
        choice = copy.deepcopy(descriptor)
        choice["ui"][1] = {"type": "select", "key": "value", "title": "Value", "options": [{"value": "a", "label": "A"}], "enableOnEdit": ["option_0"]}
        self.validators["descriptor"].validate(choice)
        for targets in ([], ["option_0", "option_0"], "option_0", [False]):
            bad = copy.deepcopy(descriptor); bad["ui"][1]["enableOnEdit"] = targets
            self.assertFalse(self.validators["descriptor"].is_valid(bad))
        bad = copy.deepcopy(descriptor); bad["ui"][1] = {"type": "status", "key": "value", "title": "Status", "enableOnEdit": ["option_0"]}
        self.assertFalse(self.validators["descriptor"].is_valid(bad))

    def test_unbounded_input_shape(self):
        descriptor = self.descriptor()
        descriptor["ui"].append({"type": "slider", "key": "value", "title": "Value", "min": 0, "max": 100, "unboundedInput": True})
        self.validators["descriptor"].validate(descriptor)
        for bad in ({"type": "slider", "key": "value", "title": "Value", "min": 0, "max": 100, "unboundedInput": "yes"},
                    {"type": "input", "key": "value", "title": "Value", "unboundedInput": True},
                    {"type": "preset", "key": "value", "title": "Value", "values": {"option_0": True}, "unboundedInput": True}):
            altered = copy.deepcopy(descriptor); altered["ui"][1] = bad
            self.assertFalse(self.validators["descriptor"].is_valid(altered))

    def metadata(self, api_version=2):
        value = {"id": "example.complete.plugin", "name": "Complete plugin", "version": "2.1.0_beta.002",
                 "versionCode": 7, "apiVersion": api_version, "capabilities": ["runtime"] if api_version == 2 else ["settings"]}
        if api_version == 2:
            value["runtime"] = {"entry": "runtime.js"}
        return value

    def descriptor(self, api_version=2, count=1):
        return dict(self.metadata(api_version), ui=[{"type": "switch", "key": f"option_{index}", "title": f"Option {index}"}
                                                   for index in range(count)], settings={}, hooks=[])

    def entry(self, api_version=2, **changed):
        value = {key: value for key, value in self.metadata(api_version).items() if key != "runtime"}
        value.update(file="packages/complete.zip", sha256="a" * 64, size=1048577 if api_version == 2 else 1024)
        value.update(changed)
        return value

    def test_all_schemas_are_valid_2020_12(self):
        for name, schema in self.schemas.items():
            with self.subTest(schema=name):
                Draft202012Validator.check_schema(schema)

    def test_configuration_aliases_have_bounded_shape_and_explicit_host_contract(self):
        for name, value in (("manifest", dict(self.metadata(), entry="main.js")), ("descriptor", self.descriptor())):
            valid = dict(value, minimumHost=265, features=["config.aliases"], configurationAliases={"old_option": "option_0"})
            self.validators[name].validate(valid)
            self.validators[name].validate(dict(valid, configurationAliases={}))
            self.validators[name].validate(dict(valid, configurationAliases={f"old_{n}": f"new_{n}" for n in range(512)}))
            invalid = [dict(valid, apiVersion=1), dict(valid, minimumHost=264), dict(valid, features=[]),
                       {key: item for key, item in valid.items() if key != "minimumHost"},
                       {key: item for key, item in valid.items() if key != "features"}]
            for aliases in (None, [], "old_option", {"old": False}, {"bad key": "option_0"}, {"old": "bad key"},
                            {"constructor": "option_0"}, {"old": "prototype"}, {"old\n": "option_0"}, {"old": "option_0\n"}, {"x" * 65: "option_0"},
                            {f"old_{n}": f"new_{n}" for n in range(513)}):
                invalid.append(dict(valid, configurationAliases=aliases))
            for altered in invalid:
                with self.subTest(schema=name, value=altered):
                    self.assertFalse(self.validators[name].is_valid(altered))
        catalog = {"schemaVersion": 1, "apiVersion": 2,
                   "plugins": [self.entry(configurationAliases={"old_option": "option_0"})]}
        self.assertFalse(self.validators["catalog"].is_valid(catalog))

    def test_api1_and_api2_are_valid_across_three_schemas(self):
        for api in (1, 2):
            cases = {"manifest": dict(self.metadata(api), entry="main.js"), "descriptor": self.descriptor(api),
                     "catalog": {"schemaVersion": 1, "apiVersion": api, "plugins": [self.entry(api)]}}
            for name, value in cases.items():
                with self.subTest(api=api, schema=name):
                    self.validators[name].validate(value)

    def test_api2_requires_runtime_and_declared_capability(self):
        for name, value in (("manifest", dict(self.metadata(), entry="main.js")), ("descriptor", self.descriptor())):
            for field in ("runtime", "capabilities"):
                changed = copy.deepcopy(value)
                del changed[field]
                with self.subTest(schema=name, missing=field):
                    self.assertFalse(self.validators[name].is_valid(changed))
            for runtime in ({"source": ""}, {"source": "   "}, {"entry": "../bad.js"}, {"entry": "x.js", "source": "code"}):
                with self.subTest(schema=name, runtime=runtime):
                    self.assertFalse(self.validators[name].is_valid(dict(value, runtime=runtime)))
            self.validators[name].validate(dict(value, runtime={"source": "C17.log('inline');"}))

    def test_api2_form_and_package_size_do_not_inherit_api1_quotas(self):
        self.validators["descriptor"].validate(self.descriptor(2, 80))
        self.assertFalse(self.validators["descriptor"].is_valid(self.descriptor(1, 80)))
        self.validators["manifest"].validate(dict(self.metadata(), entry="nested/" * 20 + "main.js"))
        self.assertFalse(self.validators["manifest"].is_valid(dict(self.metadata(1), entry="nested/" * 20 + "main.js")))
        for api in (1, 2):
            catalog = {"schemaVersion": 1, "apiVersion": api, "plugins": [self.entry(api, size=1048577)]}
            self.assertEqual(api == 2, self.validators["catalog"].is_valid(catalog))

    def test_catalog_rejects_private_script_and_downgraded_api(self):
        catalog = {"schemaVersion": 1, "apiVersion": 2, "plugins": [self.entry()]}
        self.assertFalse(self.validators["catalog"].is_valid(dict(catalog, apiVersion=1)))
        for field, value in (("runtime", {"source": "code"}), ("entry", "runtime.js"), ("ui", []), ("hooks", [])):
            with self.subTest(field=field):
                self.assertFalse(self.validators["catalog"].is_valid(dict(catalog, plugins=[self.entry(**{field: value})])))
        self.assertFalse(self.validators["catalog"].is_valid(dict(catalog, plugins=[self.entry(capabilities=[])])))

    def test_api1_cannot_declare_runtime_and_forms_must_be_editable(self):
        self.assertFalse(self.validators["manifest"].is_valid(dict(self.metadata(1), entry="main.js", runtime={"source": "code"})))
        for api in (1, 2):
            for ui in ([], [{"type": "text", "title": "Only description"}], [{"type": "button", "title": "Only button", "key": "action"}]):
                with self.subTest(api=api, ui=ui):
                    self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(api), ui=ui)))

    def test_detail_metadata_and_groups_are_optional_for_both_apis(self):
        for api in (1, 2):
            extra = {"minimumHost": 210, "features": ["ui.groups", "scope.systemui"],
                     "usage": "先启用，再修改功能设置。", "scopes": ["com.android.systemui", "android"]}
            manifest, descriptor = dict(self.metadata(api), entry="main.js", **extra), dict(self.descriptor(api), **extra)
            descriptor["ui"][0].update(section="features", group="显示")
            for name, value in (("manifest", manifest), ("descriptor", descriptor)):
                self.validators[name].validate(value)
                for field, invalid in (("minimumHost", True), ("minimumHost", 0), ("minimumHost", 2147483648),
                                       ("usage", "x" * 4097), ("features", ["runtime.dex"] * 2),
                                       ("features", ["bad feature"]), ("features", ["x" * 81]),
                                       ("features", ["f" + str(index) for index in range(65)]),
                                       ("scopes", ["systemui"]), ("scopes", ["com.android:remote"]),
                                       ("scopes", ["com." + "x" * 237]),
                                       ("scopes", ["com.android.systemui"] * 2)):
                    with self.subTest(api=api, schema=name, field=field):
                        self.assertFalse(self.validators[name].is_valid(dict(value, **{field: invalid})))
            for field, invalid in (("section", "overview"), ("group", "x" * 121), ("group", [])):
                with self.subTest(api=api, field=field):
                    controls = [dict(descriptor["ui"][0], **{field: invalid})]
                    self.assertFalse(self.validators["descriptor"].is_valid(dict(descriptor, ui=controls)))

    def test_native_settings_is_an_api2_setting_entry_and_not_a_catalog_field(self):
        native = {"dex": "settings/classes.dex", "className": "example.plugin.Settings$Entry"}
        for name, value in (("manifest", dict(self.metadata(), entry="main.js")), ("descriptor", self.descriptor())):
            self.validators[name].validate(dict(value, nativeSettings=native))
            legacy = dict(self.metadata(1), entry="main.js") if name == "manifest" else self.descriptor(1)
            self.assertFalse(self.validators[name].is_valid(dict(legacy, nativeSettings=native)))
            for invalid in ({"dex": "../bad.dex", "className": native["className"]},
                            {"dex": "settings.js", "className": native["className"]},
                            {"dex": "设置.dex", "className": native["className"]},
                            {"dex": "my settings.jar", "className": native["className"]},
                            {"dex": "settings.jar", "className": "Settings"},
                            {"dex": "settings.jar", "className": "example.Bad-Class"},
                            dict(native, method="other")):
                with self.subTest(schema=name, native=invalid):
                    self.assertFalse(self.validators[name].is_valid(dict(value, nativeSettings=invalid)))
        self.validators["descriptor"].validate(dict(self.descriptor(), nativeSettings=native, ui=[]))
        self.validators["descriptor"].validate(dict(self.descriptor(), nativeSettings=native, ui=[{"type": "text", "title": "原生设置入口"}]))
        for field, value in (("minimumHost", 210), ("features", ["ui.groups"]), ("usage", "说明"),
                             ("scopes", ["com.android.systemui"]), ("nativeSettings", native)):
            with self.subTest(field=field):
                catalog = {"schemaVersion": 1, "apiVersion": 2, "plugins": [self.entry(**{field: value})]}
                self.assertFalse(self.validators["catalog"].is_valid(catalog))

    def test_runtime_actions_and_status_are_not_saved_state(self):
        controls = [{"type": "switch", "title": "Enabled", "key": "enabled"},
                    {"type": "action", "title": "Capture", "key": "capture"},
                    {"type": "status", "title": "Saved time", "key": "saved_at"}]
        self.validators["descriptor"].validate(dict(self.descriptor(), ui=controls))
        grouped = copy.deepcopy(controls)
        grouped[1].update(section="management", group="数据")
        grouped[2].update(section="management", group="数据")
        self.validators["descriptor"].validate(dict(self.descriptor(), ui=grouped))
        self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(1), ui=controls)))
        self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=controls[1:])))
        for kind in ("action", "status"):
            for key, value in (("default", True), ("value", 1), ("options", []), ("min", 0),
                               ("max", 1), ("step", 1), ("unit", "%")):
                with self.subTest(kind=kind, field=key):
                    invalid = controls[:1] + [{"type": kind, "title": "Runtime", "key": "runtime", key: value}]
                    self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=invalid)))


    def test_nested_color_and_preset_controls(self):
        color = {"type": "color", "key": "tint", "title": "颜色", "default": "#80112233",
                 "groupPath": ["效果调节", "部件", "浅色遮罩"]}
        preset = {"type": "preset", "key": "clear", "title": "通透", "values": {"tint": "#FFFFFF"}}
        for api in (1, 2):
            self.validators["descriptor"].validate(dict(self.descriptor(api), ui=[color, preset]))
        for path in ([], ["a"] * 4, "a/b", [None], [" "], ["a\0b"], ["x" * 121]):
            with self.subTest(path=path):
                self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=[dict(color, groupPath=path)])))
        for value in ("red", "#ABC", "#FFFFFF ", "#00112233extra", 1, True, None):
            with self.subTest(value=value):
                self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=[dict(color, default=value)])))
        for values in ({}, {str(i): i for i in range(65)}, {"tint": []}, None):
            with self.subTest(values=values):
                self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=[color, dict(preset, values=values)])))
        for field, value in (("default", True), ("value", 1), ("options", []), ("min", 0), ("max", 1), ("step", 1)):
            self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=[color, dict(preset, **{field: value})])))
        self.assertFalse(self.validators["descriptor"].is_valid(dict(self.descriptor(), ui=[dict(color, values={"tint": "#FFFFFF"})])))


if __name__ == "__main__":
    unittest.main()
