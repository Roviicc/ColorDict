#!/usr/bin/env python3
"""Run the whole authoring pipeline: overlays -> batch -> validate -> build.

Every annotation round repeats the same five commands with the same growing
list of overlay files, which is easy to get wrong by hand - one forgotten
`--overlay` silently drops a shard from the build. This discovers the overlays
instead, and re-derives the adverbs first so a new adjective family's free
adverbs are never left behind.

Overlay order matters and is alphabetical by design:
`batch-0001` (the hand-written first batch) is overridden by `families-*`
(charges and spectra), and `ranks-*` sorts last because it only sets rank.
`adverbs-*` is regenerated here, so its position is irrelevant - it patches
adverbs, which no family overlay touches.

Usage:
    python3 tools/dict_pipeline.py                # overlays, validate, build
    python3 tools/dict_pipeline.py --no-build     # stop after validating
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OVERLAYS = ROOT / "data/entries/overlays"
BULK = ROOT / "data/entries/derived-bulk.jsonl"
BATCH = ROOT / "data/entries/batch-0001.jsonl"
BUILD = ROOT / "data/build"
ASSETS = ROOT / "app/src/main/assets/dicts/popup-en"
ASSET_FILES = ("popup-en.dict.dz", "popup-en.idx", "popup-en.ifo", "popup-en.syn")


def run(*args):
    print(f"\n$ {' '.join(str(a) for a in args[1:])}")
    result = subprocess.run([sys.executable, *[str(a) for a in args]], cwd=ROOT)
    if result.returncode:
        sys.exit(f"failed: {args[0].name}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--no-build", action="store_true",
                    help="stop after validation; skip stardict and assets")
    ap.add_argument("--no-assets", action="store_true",
                    help="build, but do not copy into the app")
    args = ap.parse_args()

    if not BULK.exists():
        sys.exit(f"{BULK} is missing - regenerate it with tools/wordnet_import.py")

    families = sorted(OVERLAYS.glob("families-*.overlay.jsonl"))
    if not families:
        sys.exit(f"no family overlays in {OVERLAYS}")

    # Adverbs are derived, not authored: rebuild them from every family so the
    # ones a new adjective shard unlocked are picked up automatically.
    adverbs = OVERLAYS / "adverbs-001.overlay.jsonl"
    run(ROOT / "tools/adverb_inherit.py", "--bulk", BULK,
        *[a for f in families for a in ("--overlay", f)], "--out", adverbs)

    overlays = sorted(OVERLAYS.glob("*.overlay.jsonl"))
    run(ROOT / "tools/dict_enrich_apply.py", "--bulk", BULK,
        *[a for o in overlays for a in ("--overlay", o)], "--out", BATCH)
    run(ROOT / "tools/dict_validate.py", BATCH)
    if args.no_build:
        return 0

    run(ROOT / "tools/dict_build.py", BATCH, BULK, "--out", BUILD)
    if not args.no_assets:
        for name in ASSET_FILES:
            shutil.copyfile(BUILD / name, ASSETS / name)
        print(f"\ncopied {len(ASSET_FILES)} files into {ASSETS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
