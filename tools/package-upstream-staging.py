from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
kit = root / "build/staging-kit-2.13.1-rc2"
source = root / "build/local-update/One Life Core"
kit.mkdir(exist_ok=True)
assert source.is_dir()
# Fresh version-specific output; repeat runs must not retain stale payload files.
if (kit / "One Life Core").exists():
    target = (kit / "One Life Core").resolve()
    assert target.is_relative_to((root / "build").resolve())
    shutil.rmtree(target)
shutil.copytree(source, kit / "One Life Core")
payload = kit / "One Life Core"
shutil.copy2(root / "CHANGELOG.md", payload / "Contents/mods/storm/42/CHANGELOG.md")
for name in ["STAGING.md", "CHANGELOG.md", "verify-package.py"]: shutil.copy2(root / name, kit / name)
shutil.copy2(root / "docs/upstream-2.13.1-integration.md", kit / "INTEGRATION.md")
shutil.copy2(root / "docs/population-container-integration.md", kit / "POPULATION-CONTAINER-BRUSH.md")
counts = {}
for task in ["build/test-results/test", "build/test-results/clientPatchTest", "launcher/build/test-results/test"]:
    totals = dict.fromkeys(["tests", "failures", "errors", "skipped"], 0)
    files = list((root / task).glob("TEST-*.xml")); assert files
    for file in files:
        node = ET.parse(file).getroot()
        for key in totals: totals[key] += int(node.get(key,0))
    assert totals["failures"] == totals["errors"] == 0
    counts[task] = totals
for mode in ["combined", "reverse"]:
    log = (root / "build/sdac-integration-2.13.1-rc2" / (mode+".log")).read_text(errors="replace")
    assert "errors=0" in log and "FAIL TRANSFORM" not in log and "FAIL VERIFY" not in log
    shutil.copy2(root / "build/sdac-integration-2.13.1-rc2" / (mode+".log"), kit / ("sdac-"+mode+".log"))
manifest = {p.relative_to(payload).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(payload.rglob("*")) if p.is_file()}
(kit / "SHA256-MANIFEST.json").write_text(json.dumps(manifest,indent=2))
validation = {"version":"42.21_2.13.1-rc2", "launcher":"1.2.8-rc1", "sourceCommit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=root,text=True).strip(), "upstream":"dcc9b065e08b047f76a9122313f4e5220743b3f8", "tests":counts, "liveLinuxTested":False,"liveGraphicsTested":False,"published":False,"installed":False}
(kit / "VALIDATION.json").write_text(json.dumps(validation,indent=2))
subprocess.run(["py","-3.13",str(kit/"verify-package.py")],check=True)
archive=root/"build/One-Life-Core-2.13.1-rc2-staging.zip"
with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED) as z:
    for p in sorted(kit.rglob("*")):
        if p.is_file(): z.write(p,p.relative_to(kit).as_posix())
with zipfile.ZipFile(archive) as z: assert z.testzip() is None
print(json.dumps({"archive":str(archive),"sha256":hashlib.sha256(archive.read_bytes()).hexdigest(),"validation":validation},indent=2))
