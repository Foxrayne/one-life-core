"""Verify an extracted staging package using only Python's standard library."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parent
payload = root / "One Life Core"
manifest = json.loads((root / "SHA256-MANIFEST.json").read_text(encoding="utf-8"))
failures = []
for name, expected in manifest.items():
    path = (payload / name).resolve()
    if not path.is_relative_to(payload.resolve()):
        failures.append(f"Invalid manifest path: {name}")
        continue
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        failures.append(f"Missing or mismatched file: {name}")
actual = {p.relative_to(payload).as_posix() for p in payload.rglob("*") if p.is_file()}
failures.extend(f"Unexpected payload file: {name}" for name in sorted(actual - manifest.keys()))
jars = list(payload.rglob("storm-42.21_*.jar"))
if len(jars) != 1:
    failures.append(f"Expected one Core JAR, found {len(jars)}")
else:
    with zipfile.ZipFile(jars[0]) as jar:
        names = jar.namelist()
        forbidden = ("io/pzstorm/storm/client/visuals/", "shaders/", "zombie/")
        failures.extend(f"Unexpected JAR entry: {name}" for name in names if name.startswith(forbidden))
        if jar.read("storm-version.properties").decode().strip() != "version=42.21_2.11.7-rc1":
            failures.append("Incorrect Core version")
        if any("RequestSaveCellSuppress" in name for name in names):
            failures.append("Obsolete population-save suppression is present")
        for required in (
            "io/pzstorm/storm/patch/performance/ServerLosPause.class",
            "io/pzstorm/storm/patch/fixes/ItemContainerChainGuardPatch.class",
            "io/pzstorm/storm/patch/fixes/InventoryItemGetOutermostContainerPatch.class",
            "io/pzstorm/storm/transfer/ContainerInsertion.class",
            "io/pzstorm/storm/patch/client/IsoHutchNullAnimalSlotGuardPatch.class",
        ):
            if required not in names:
                failures.append(f"Missing fix: {required}")
if list(payload.rglob("OneLifeVisualOptions.lua")):
    failures.append("Unfinished visual options are present")
if failures:
    raise SystemExit("Verification failed:\n" + "\n".join(failures))
print(f"Verified {len(manifest)} payload files: One Life Core 42.21_2.11.7-rc1 (stability only).")
