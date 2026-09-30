"""Build an isolated manual staging kit using the published rc2 payload as its base."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
base = root.parent / "One Life Core Staging/build/staging-kit"
version = "42.21_2.11.7-rc1"
kit = root / "build/staging-kit-2.11.7-rc1"
payload = kit / "One Life Core"
manifest = json.loads((base / "SHA256-MANIFEST.json").read_text(encoding="utf-8"))
for name, expected in manifest.items():
    assert hashlib.sha256((base / "One Life Core" / name).read_bytes()).hexdigest() == expected, name
shutil.copytree(base / "One Life Core", payload, dirs_exist_ok=True,
                ignore=shutil.ignore_patterns("storm-42.21_*.jar"))
mod = payload / "Contents/mods/storm/42"
jar = root / "build/libs" / ("storm-" + version + ".jar")
assert jar.is_file()
shutil.copy2(jar, mod / "lib" / jar.name)
p = mod / "mod.info"
p.write_text(p.read_text(encoding="utf-8").replace("42.21_2.11.6-rc2", version),encoding="utf-8")
shutil.copy2(root / "CHANGELOG.md", mod / "CHANGELOG.md")
for name in ("STAGING.md", "CHANGELOG.md", "verify-package.py"):
    shutil.copy2(root / name, kit / name)
shutil.copy2(root / "docs/population-container-integration.md", kit / "INTEGRATION.md")
counts = {}
for task in ("test", "clientPatchTest"):
    totals = {k:0 for k in ("tests", "failures", "errors", "skipped")}
    files = list((root / "build/test-results" / task).glob("TEST-*.xml"))
    assert files
    for p in files:
        r = ET.parse(p).getroot()
        for k in totals: totals[k] += int(r.attrib.get(k,0))
    assert totals["failures"] == totals["errors"] == 0, totals
    counts[task] = totals
validation = {
    "version":version,
    "sourceCommit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=root,text=True).strip(),
    "upstreamCommit":"3659e9f5875e1511ec88f1b935390a6ed49aea90",
    "tests":counts,
    "initialImplementationFullOfflineTests":json.loads((root / "build/validation/full-offline/counts.json").read_text()),
    "initialFullSuiteNote":"Run before final save-scope, diagnostics and floor-processing refinements; final targeted tests and transformer checks are above.",
    "gameJarSha256":"e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33",
    "linuxPopulationSha256":"31d83c5d9e281a40ed040278f567f02b36a85b757b105266a7a4146eff85a100",
    "coreJarSha256":hashlib.sha256(jar.read_bytes()).hexdigest(),
    "publishedBaselineJarSha256":"5bd64213ce9c763d4229052fe835a8c1e906a63f318ebb3a36580a589f150654",
    "liveMultiplayerTested":False,"published":False,"installed":False,
    "scope":["population-saving","container-protection"],
    "modId":"one-life-core-b42"
}
(kit / "VALIDATION.json").write_text(json.dumps(validation,indent=2),encoding="utf-8")
new = {p.relative_to(payload).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
       for p in sorted(payload.rglob("*")) if p.is_file()}
(kit / "SHA256-MANIFEST.json").write_text(json.dumps(new,indent=2),encoding="utf-8")
# Only Core code, changelog and mod version may differ in the release payload.
allowed = {"Contents/mods/storm/42/CHANGELOG.md", "Contents/mods/storm/42/mod.info"}
for name, sha in new.items():
    if name in allowed or name.endswith("/"+jar.name): continue
    assert manifest.get(name) == sha, "Unexpected payload change: " + name
subprocess.run(["py", str(kit / "verify-package.py")],check=True,cwd=kit)
archive = root / "build/One-Life-Core-2.11.7-rc1-staging.zip"
with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED) as z:
    for p in sorted(kit.rglob("*")):
        if p.is_file(): z.write(p,p.relative_to(kit).as_posix())
print(json.dumps({"archive":str(archive),"sha256":hashlib.sha256(archive.read_bytes()).hexdigest(),"validation":validation},indent=2))
