#!/usr/bin/env python3
"""Keep plugin/colordict/agents/ byte-identical to .claude/agents/.

The plugin exists so a session that is not opened on this folder can resolve
the instruments by name. That means a second copy of the measuring stick, and
two copies of a measuring stick drift. So there is exactly one source of truth,
.claude/agents/, and this script regenerates the plugin copy from it.

instrument_gate.py calls check() as well, so a run refuses to grade when the
two have diverged - the same protection the gate already gives against editing
.claude/agents/ directly.

    python tools/plugin_sync.py           # verify, exit 1 on divergence
    python tools/plugin_sync.py --write   # regenerate the plugin copy
"""
import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / ".claude/agents"
DST = ROOT / "plugin/colordict/agents"
# Only the four instruments ship. census-reader and family-author belong to the
# connotation lane and are not part of the stage 7 pipeline.
SHIPPED = ("sense-ranker.md", "enricher.md", "entry-reader.md", "null-auditor.md")


def check():
    bad = []
    for name in SHIPPED:
        s, d = SRC / name, DST / name
        if not s.is_file():
            bad.append(f"source missing: {s.relative_to(ROOT)}")
            continue
        if not d.is_file():
            bad.append(f"plugin copy missing: {d.relative_to(ROOT)}")
            continue
        if s.read_bytes() != d.read_bytes():
            bad.append(f"plugin copy differs from source: {name}")
    extra = sorted(p.name for p in DST.glob("*.md") if p.name not in SHIPPED) if DST.is_dir() else []
    if extra:
        bad.append("unexpected file(s) in the plugin: " + ", ".join(extra))
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--write", action="store_true",
                    help="regenerate the plugin copy from .claude/agents/")
    args = ap.parse_args()
    if args.write:
        DST.mkdir(parents=True, exist_ok=True)
        for name in SHIPPED:
            shutil.copyfile(SRC / name, DST / name)
            print("  synced " + name)
    bad = check()
    if bad:
        for b in bad:
            print("  - " + b)
        print("PLUGIN SYNC: FAIL - run `python tools/plugin_sync.py --write`")
        return 1
    print("PLUGIN SYNC: pass - the plugin ships the same four instruments")
    return 0


if __name__ == "__main__":
    sys.exit(main())
