"""Authoring failures that used to reject real API 2 forms or pass broken references."""
import copy
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("pack_standard", ROOT / "tools/pack.py")
pack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pack)


class StandardTests(unittest.TestCase):
    def setUp(self):
        self.files, manifest = pack.read_source(ROOT / "templates/standard-runtime")
        self.metadata = {k: v for k, v in manifest.items() if k != "entry"}
        self.ui = pack.validate_source_form(self.files[manifest["entry"]].decode(), 2)

    def test_standard_template_and_resolved_descriptor(self):
        pack.validate_standard(self.metadata, self.ui)
        descriptor = dict(self.metadata, ui=self.ui, settings={}, hooks=[])
        manifest = dict(self.metadata, entry="main.js")
        pack.validate_descriptor(descriptor, manifest, True)
        with self.assertRaises(pack.Invalid):
            pack.validate_descriptor(descriptor, dict(manifest, name="wrong"))

    def test_management_requires_category_and_host_capability(self):
        for mutation in (lambda m, u: m.update(minimumHost=260),
                         lambda m, u: m["features"].remove("ui.management-groups"),
                         lambda m, u: m["features"].remove("runtime.actions"),
                         lambda m, u: u[-1].pop("groupPath"),
                         lambda m, u: m.update(usage="")):
            m, u = copy.deepcopy(self.metadata), copy.deepcopy(self.ui)
            mutation(m, u)
            with self.assertRaises(pack.Invalid): pack.validate_standard(m, u)

    def test_app_and_sort_values_and_action_key_collisions(self):
        form = [{"type":"app","key":"target","title":"App","default":"com.example.target"},
                {"type":"sort","key":"order","title":"Order","optionsAction":"catalog","default":'[]'}]
        pack.validate_form(form, 2)
        for changed in (dict(form[0], default="android"), dict(form[1], default='["x","x"]'),
                        dict(form[1], optionsAction="target"), dict(form[1], default='{"version":1,"order":[],"hidden":["x"]}')):
            bad = [changed, form[1]] if changed["key"] == "target" else [form[0], changed]
            with self.assertRaises(pack.Invalid): pack.validate_form(bad, 2)
        for extra in ({"type":"status","key":"catalog","title":"Collision"}, {"type":"text","key":"order","title":"Collision"}):
            with self.assertRaises(pack.Invalid): pack.validate_form(form + [extra], 2)
        pack.validate_form([dict(form[1], default=json.dumps(["x" * 4096, "y" * 4096]))], 2)
        with self.assertRaises(pack.Invalid): pack.validate_form(form, 1)

    def test_native_preference_ownership_and_external_scope(self):
        m = copy.deepcopy(self.metadata)
        m["runtime"]["preferences"] = ["example_owned_state"]
        pack.validate_metadata(m)
        for names in (["x"], ["../other"], ["example_owned_state"] * 2, [str(x) for x in range(33)]):
            bad=copy.deepcopy(m); bad["runtime"]["preferences"]=names
            with self.assertRaises(pack.Invalid): pack.validate_metadata(bad)
        m["scopes"].append("com.example.launcher"); m["features"].append("scope.apps")
        with self.assertRaises(pack.Invalid): pack.validate_standard(m, self.ui)
        del m["runtime"]["preferences"]
        pack.validate_standard(m, self.ui)
        m["scopes"].remove("com.android.systemui")
        with self.assertRaises(pack.Invalid): pack.validate_standard(m, self.ui)

    def test_confirmation_only_on_actions(self):
        for kind in ("status", "text", "switch"):
            with self.assertRaises(pack.Invalid):
                pack.validate_form(self.ui[:1] + [{"type":kind,"key":"another","title":"Invalid","confirmation":"Confirm"}], 2)
        for text in ("", " " * 2, "x" * 1025):
            with self.assertRaises(pack.Invalid): pack.validate_form(self.ui[:-1] + [dict(self.ui[-1], confirmation=text)], 2)

    def test_select_enable_reference_and_large_preset(self):
        form=[{"type":"switch","key":"gate","title":"Gate"},
              {"type":"select","key":"mode","title":"Mode","options":[{"value":"a","label":"A"}],"enableOnEdit":["gate"]}]
        pack.validate_form(form, 2)
        with self.assertRaises(pack.Invalid): pack.validate_form([form[0],dict(form[1],enableOnEdit=["missing"])],2)
        form=[{"type":"switch","key":"s"+str(i),"title":"Switch"} for i in range(512)]
        preset={"type":"preset","key":"preset","title":"Preset","values":{c["key"]:True for c in form}}
        pack.validate_form(form+[preset],2)
        form.append({"type":"switch","key":"last","title":"Last"}); preset["values"]["last"]=True
        with self.assertRaises(pack.Invalid): pack.validate_form(form+[preset],2)

    def test_schema_covers_same_api2_fields(self):
        try:
            from jsonschema import Draft202012Validator
        except ImportError:
            self.skipTest("Install jsonschema to check the published schemas")
        schema=json.loads((ROOT / "schema/descriptor.schema.json").read_text(encoding="utf-8"))
        validator=Draft202012Validator(schema)
        d=dict(self.metadata,ui=self.ui,settings={},hooks=[])
        validator.validate(d)
        d["runtime"]["preferences"]=["example_owned"]
        d["ui"] += [{"type":"app","key":"app","title":"App","default":""},
                    {"type":"sort","key":"order","title":"Order","optionsAction":"catalog","default":"[]"}]
        validator.validate(d)
        for index, field, value in ((-1,"optionsAction",None),(-1,"default",False),(-2,"default","invalid"),(0,"confirmation","Confirm")):
            bad=copy.deepcopy(d); bad["ui"][index][field]=value
            self.assertFalse(validator.is_valid(bad))
