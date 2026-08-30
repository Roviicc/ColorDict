#!/usr/bin/env python3
"""Flag tone notes that leave the word, per the rule in plan 11.65.

A smoke alarm, not a referee. The rule is "say what the word does; do not say
who uses it or where it came from", and the three shapes that break it leave a
lexical trace. Some flagged notes are fine - *puritanical*'s "now used almost
exclusively as an accusation" trips the distribution rule and was marked right
in audit 001, because it is true. Read what it points at; do not obey it.

Audit 001 measured 44% of tone notes wrong, and the failures fell into a small
number of shapes. Every one of those shapes leaves a lexical trace, so the rule
can be enforced mechanically instead of remembered:

  1. distribution - "usually", "now mostly", "the commonest"
  2. provenance   - "from the Latin", "named for", "originally meant"
  3. restriction  - "only ever", "describes a X, not a Y", "confined to"

Usage:
    python3 tools/tone_lint.py data/families/annotated-*.json
    python3 tools/tone_lint.py --overlay data/entries/overlays/batch-0001.overlay.jsonl
    python3 tools/tone_lint.py --all --quiet      # counts only
"""

import argparse
import glob
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Each rule is (name, compiled pattern, what to do instead).
RULES = [
    ("distribution", re.compile(
        r"\b("
        r"usually|mostly|most often|more often than not|commonest|most common|"
        r"nowadays|these days|now (?:used|said|aimed|mostly|almost|largely|"
        r"chiefly|entirely)|frequently|typically|generally|as a rule|"
        r"rarely used|hardly ever|almost always|almost never|"
        r"far more often|much more often|equally likely|tends to be"
        r")\b", re.I),
     "state what the word does, not how often people do it"),

    ("narrowing", re.compile(
        r"\b("
        r"only ever|only in|only of|never of|never said|not of people|"
        r"describes? (?:a|the) \w+,? not|"
        r"about \w+ rather than \w+,? not|"
        r"exclusively|confined to|restricted to|nothing but"
        r")\b", re.I),
     "do not restrict the sense further than the gloss does"),

    ("etymology", re.compile(
        r"\b("
        r"from the (?:latin|greek|french|old english|german|norse)|"
        r"named (?:for|after)|comes from|derives? from|"
        r"originally (?:meant|a|an|the)|in origin|the root is|"
        r"latin for|greek for|french for"
        r")\b", re.I),
     "drop the origin story unless it has been checked against a source"),

    ("hedge-claim", re.compile(
        r"\b(everyone|nobody|no one|anybody) \w+", re.I),
     "a claim about all speakers is a distribution claim in disguise"),

    ("speaker", re.compile(
        r"\b(?:said|used|aimed|applied|addressed)\s+(?:of|to|at|by)\s+"
        r"(?:the\s+)?(?:elderly|old|young|children|women|men|girls|boys|"
        r"americans|the british|teenagers|adults)\b", re.I),
     "who says a word is not something we can check - describe the word instead"),
]

# Length was tried as a proxy and does not predict failure: the *grudgingly*
# note is long and correct, the *hoggish* note is short and wrong. What predicts
# failure is leaving the word, which the rules above catch directly.


def check(note):
    """Return a list of (rule, matched text, advice) for one note."""
    hits = []
    for name, pattern, advice in RULES:
        m = pattern.search(note or "")
        if m:
            hits.append((name, m.group(0), advice))
    return hits


def notes_from_family_file(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    for fam in data["families"]:
        for m in fam["members"]:
            if m.get("tone"):
                yield f"{fam['id']}/{m['word']}", m["tone"]


def notes_from_overlay(path):
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            for sense_id, patch in (entry.get("senses") or {}).items():
                tone = (patch or {}).get("tone")
                if tone:
                    yield sense_id, tone


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="*", help="annotated-*.json files")
    ap.add_argument("--overlay", action="append", default=[],
                    help="an overlay .jsonl carrying tone notes")
    ap.add_argument("--all", action="store_true",
                    help="every annotated shard plus batch-0001's overlay")
    ap.add_argument("--quiet", action="store_true", help="counts only")
    ap.add_argument("--max-fail", type=int, default=None,
                    help="exit non-zero if more than this many notes are flagged")
    args = ap.parse_args()

    paths, overlays = list(args.paths), list(args.overlay)
    if args.all:
        paths += sorted(glob.glob(str(ROOT / "data/families/annotated-*.json")))
        overlays.append(str(ROOT / "data/entries/overlays/batch-0001.overlay.jsonl"))

    sources = [(p, notes_from_family_file) for p in paths] + \
              [(p, notes_from_overlay) for p in overlays]
    if not sources:
        sys.exit("nothing to check - pass files, --overlay, or --all")

    total = flagged = 0
    per_rule = Counter()
    per_source = {}

    for path, reader in sources:
        n = bad = 0
        for key, note in reader(path):
            n += 1
            hits = check(note)
            if hits:
                bad += 1
                for rule, _, _ in hits:
                    per_rule[rule] += 1
                if not args.quiet:
                    print(f"\n{Path(path).name}  {key}")
                    print(f"  {note}")
                    for rule, text, advice in hits:
                        print(f"  -> {rule}: {text!r} - {advice}")
        total += n
        flagged += bad
        per_source[Path(path).name] = (n, bad)

    print()
    print(f"{'source':34s} {'notes':>6s} {'flagged':>8s} {'rate':>6s}")
    for name, (n, bad) in per_source.items():
        print(f"{name:34s} {n:6d} {bad:8d} {100*bad/n if n else 0:5.0f}%")
    print(f"{'TOTAL':34s} {total:6d} {flagged:8d} {100*flagged/total if total else 0:5.0f}%")
    print()
    for rule, k in per_rule.most_common():
        print(f"  {rule:14s} {k}")

    if args.max_fail is not None and flagged > args.max_fail:
        sys.exit(f"\n{flagged} notes flagged, limit is {args.max_fail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
