#!/usr/bin/env python3
"""Replay the device's Binder rejection of private-file stdout through real scripts."""
import importlib.util
import os
from pathlib import Path
import shutil
import tempfile

path = Path(__file__).with_name("performance-module-check.py")
spec = importlib.util.spec_from_file_location("performance_module_checks", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
bash = shutil.which("bash")
if os.name == "nt":
    bash = next((str(p) for p in [Path("C:/Program Files/Git/bin/bash.exe"),
                                Path("C:/Program Files/Git/usr/bin/bash.exe")] if p.exists()), bash)
if not bash:
    raise SystemExit("bash is required")
with tempfile.TemporaryDirectory(prefix="c17-art-transport-") as base:
    fixture = module.Fixture(Path(base), bash, "binder-private-stdout")
    result = fixture.run(mode="run", C17_FIXTURE_REJECT_PRIVATE_STDOUT="1")
    module.equal(0, result.returncode, "compile must not pass private-file FDs to package service")
    module.equal("ok", fixture.status()["state"], "actual compiler completion")
    module.equal("2", fixture.status()["verified"], "both outputs verified")
    module.equal("none", fixture.status()["error"], "no masked transport failure")
    profiled = module.Fixture(Path(base), bash, "oem-profile")
    result = profiled.run(mode="run", C17_FIXTURE_ART="speed-profile")
    module.equal(0, result.returncode, "valid OEM profile is reusable")
    module.equal("2", profiled.status()["verified"], "profile-guided code verified")
    module.equal([], module.compiles(profiled.commands()), "no unnecessary full recompile")
    stale = module.Fixture(Path(base), bash, "dependency-mismatch")
    result = stale.run(mode="run", C17_FIXTURE_ART="vdex")
    module.equal(False, result.returncode == 0, "stale dependency is never verified")
    module.equal("0", stale.status()["verified"], "mismatched code stays unverified")
    failed = module.Fixture(Path(base), bash, "compiler-failed")
    result = failed.run(mode="run", C17_FIXTURE_COMPILE="success_exit_failure")
    module.equal(False, result.returncode == 0, "pipe reader cannot hide compiler exit failure")
    module.equal("failed", failed.status()["state"], "compiler exit retained")
    cancelled = module.Fixture(Path(base), bash, "compiler-timeout")
    result = cancelled.run(mode="run", C17_FIXTURE_COMPILE="hang")
    module.equal(False, result.returncode == 0, "timeout remains bounded")
    module.equal(True, any("package art cancel" in line for line in cancelled.commands()),
                 "cancel service-side operation before releasing lock")
print("ART transport checks:", module.CHECKS)
