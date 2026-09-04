#!/usr/bin/env python3
"""The instrument gate: refuse to grade a run whose measuring stick moved.

status.py *warns* when the four agent files differ from HEAD. A warning is
advice; by the time anyone reads it the tokens are spent and the number is
already incomparable. This is the same check as a gate, and enrich_validate.py
calls it before it will grade anything.

Deny rules in .claude/settings.json stop `Edit` and `Write` on
.claude/agents/**. They cannot stop `python -c "open(...,'w')"`, a `sed -i`,
or an editor. This catches drift however it arrived, which is why it is the
real protection and the deny rules are only the cheap first layer.

Exit 0 clean, 1 on drift. --require-clean also fails on uncommitted or
untracked instrument files.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENTS = ".claude/agents/"
INSTRUMENTS = ("sense-ranker.md", "enricher.md", "entry-reader.md",
               "null-auditor.md")


def git(*args):
    p = subprocess.run(("git",) + args, cwd=str(ROOT), capture_output=True,
                       text=True)
    return p.returncode, p.stdout.strip(), p.stderr.strip()


def check():
    """Return a list of complaints; empty means the instrument has not moved."""
    bad = []

    missing = [n for n in INSTRUMENTS if not (ROOT / AGENTS / n).is_file()]
    if missing:
        bad.append("instrument file(s) missing: " + ", ".join(missing))

    # Same comparison status.py makes, so the gate and the warning never
    # disagree: whitespace and CRLF are not drift, content is.
    rc, out, err = git("diff", "--ignore-all-space", "--ignore-cr-at-eol",
                       "--stat", "HEAD", "--", AGENTS)
    if rc != 0:
        bad.append("cannot diff instruments against HEAD: " + (err or "git failed"))
    elif out:
        bad.append("instruments differ from HEAD:\n    "
                   + "\n    ".join(out.splitlines()))

    rc, out, _ = git("ls-files", "--others", "--exclude-standard", "--", AGENTS)
    if rc == 0 and out:
        bad.append("untracked file(s) in " + AGENTS + ":\n    "
                   + "\n    ".join(out.splitlines()))
    return bad


def enforce(context=""):
    """Called by enrich_validate.py. Exits non-zero rather than grading."""
    bad = check()
    if not bad:
        return
    where = (" before " + context) if context else ""
    print("INSTRUMENT GATE FAILED" + where, file=sys.stderr)
    for b in bad:
        print("  - " + b, file=sys.stderr)
    print("", file=sys.stderr)
    print("The four agent files are the measuring stick. A defect rate is only",
          file=sys.stderr)
    print("comparable to an older one if they did not move. Restore them with",
          file=sys.stderr)
    print("    git checkout -- " + AGENTS, file=sys.stderr)
    print("or, if the change was deliberate, commit it and say so in the plan:",
          file=sys.stderr)
    print("the next run is then a NEW BASELINE, not a comparison.",
          file=sys.stderr)
    sys.exit(1)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--require-clean", action="store_true",
                    help="also fail when instruments are uncommitted")
    ap.parse_args()
    bad = check()
    if bad:
        for b in bad:
            print("  - " + b)
        print("INSTRUMENT GATE: FAIL")
        return 1
    print("INSTRUMENT GATE: pass - instruments unchanged vs HEAD")
    return 0


if __name__ == "__main__":
    sys.exit(main())
