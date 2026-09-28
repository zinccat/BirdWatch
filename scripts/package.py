#!/usr/bin/env python3
"""Package a built debug APK, portable project source, and SHA-256 checksums."""
from pathlib import Path
import hashlib
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUTS = ROOT / "outputs"
PROJECT = ROOT
APK = PROJECT / "app/build/outputs/apk/debug/app-debug.apk"
EXCLUDED_DIRS = {".git", ".gradle", ".idea", "build", "__pycache__", "outputs"}
EXCLUDED_FILES = {"local.properties", ".DS_Store"}


def main():
    if not APK.is_file():
        raise SystemExit("Build :app:assembleDebug before packaging.")
    OUTPUTS.mkdir(exist_ok=True)
    shutil.copy2(APK, OUTPUTS / "BirdWatch-debug.apk")
    with zipfile.ZipFile(OUTPUTS / "BirdWatch-source.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(PROJECT.rglob("*")):
            relative = path.relative_to(PROJECT)
            if (path.is_file() and not EXCLUDED_DIRS.intersection(relative.parts)
                    and path.name not in EXCLUDED_FILES and path.suffix not in {".jks", ".keystore"}):
                archive.write(path, Path("BirdWatch") / relative)
    artifacts = ("BirdWatch-debug.apk", "BirdWatch-source.zip")
    (OUTPUTS / "SHA256SUMS.txt").write_text("".join(
        f"{hashlib.sha256((OUTPUTS / name).read_bytes()).hexdigest()}  {name}\n"
        for name in artifacts), encoding="utf-8")
    print("Packaged APK, source archive and SHA-256 checksums in outputs/.")


if __name__ == "__main__":
    main()
