#!/usr/bin/env python3
"""Refuse to annotate a sense whose gloss is not a definition.

Audit 003 found the same fault seven times: the note annotates the right
synset and then describes a different sense of the word. The rule that came
out of it is that the gloss printed above the note is binding - the note must
agree with *that* definition, not with the lemma in general.

That rule has a hole. Some OEWN synsets have no definition at all: the field
holds a bare usage restriction - `renunciant` is glossed "used especially of
behavior", `stouthearted` "used especially of persons". There is nothing for a
note to agree with, so the binding check cannot be applied and any note written
against such a gloss is unfalsifiable. Those senses stay `derived`; we cannot
correct a gloss, so we decline to judge it. Mark them `"_skip": true`.

A restriction is only a defect when it stands alone. "used of a knife or other
blade; not sharp" restricts *and* defines, and is fine.

Usage:
    python3 tools/gloss_lint.py --all                 # every annotated shard
    python3 tools/gloss_lint.py data/families/annotated-008.json
    python3 tools/gloss_lint.py --corpus              # count the defect in OEWN
"""

import argparse
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BULK = ROOT / "data/entries/derived-bulk.jsonl"

# "used [especially] of/in/for/... X" - a restriction on where the word applies,
# with no predicate saying what it means. Function words ("used to express
# negation") are left alone: for them, the function *is* the definition.
RESTRICTION = re.compile(
    r"^\s*\(?\s*(?:usually\s+|often\s+|especially\s+|chiefly\s+|sometimes\s+)?"
    r"used\s+(?:especially\s+|chiefly\s+|only\s+|mainly\s+|informally\s+|"
    r"colloquially\s+|primarily\s+)*"
    r"(?:of|in|for|with|by|on|as|among|about)\b",
    re.I)

# The restriction may be parenthesised with the definition following it -
# "(used of sums of money) so small in amount as to deserve contempt". Strip a
# leading parenthetical and see whether a definition is left behind.
PARENTHETICAL = re.compile(r"^\s*\([^)]*\)\s*")

# A clause after ; or : or a dash carries the definition the opening lacked.
CONTENT = re.compile(r"[;:\u2013\u2014]\s*\S+\s+\S+")


def undefinable(gloss):
    """Return a reason if this gloss cannot carry a judgement, else None."""
    if not gloss or not gloss.strip():
        return "empty gloss"
    rest = PARENTHETICAL.sub("", gloss)
    if rest != gloss and len(rest.split()) >= 2:
        return None          # parenthesised restriction, real definition after
    if RESTRICTION.match(gloss) and not CONTENT.search(gloss):
        return "gloss is a usage restriction, not a definition"
    return None


def load_glosses(bulk):
    glosses = {}
    with open(bulk, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    glosses[sense["id"]] = sense.get("definition", "")
    return glosses


def slug(word):
    return word.replace(" ", "_")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="*", help="annotated-*.json files")
    ap.add_argument("--all", action="store_true", help="every annotated shard")
    ap.add_argument("--corpus", action="store_true",
                    help="also count the defect across the whole build")
    ap.add_argument("--bulk", type=Path, default=BULK)
    args = ap.parse_args()

    paths = list(args.paths)
    if args.all:
        paths += sorted(glob.glob(str(ROOT / "data/families/annotated-*.json")))
    if not paths and not args.corpus:
        sys.exit("nothing to check - pass files, --all, or --corpus")

    glosses = load_glosses(args.bulk)

    checked = flagged = skipped = 0
    for path in paths:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        for fam in data["families"]:
            for m in fam["members"]:
                sense_id = f"{slug(m['word'])}.{m['synset']}"
                reason = undefinable(glosses.get(sense_id, ""))
                if m.get("_skip"):
                    skipped += 1
                    continue
                checked += 1
                if reason:
                    flagged += 1
                    print(f"\n{Path(path).name}  {fam['id']}/{m['word']}")
                    print(f"  gloss: {glosses.get(sense_id, '')!r}")
                    print(f"  note:  {m.get('tone', '')!r}")
                    print(f"  -> {reason}; add \"_skip\": true")

    if paths:
        print(f"\n{checked} annotated senses checked, {flagged} flagged, "
              f"{skipped} already skipped")

    if args.corpus:
        by_synset = {}
        for sense_id, gloss in glosses.items():
            by_synset.setdefault(sense_id.rsplit(".", 1)[-1], gloss)
        bad = {s: g for s, g in by_synset.items() if undefinable(g)}
        senses = sum(1 for sid in glosses
                     if sid.rsplit(".", 1)[-1] in bad)
        print(f"\ncorpus: {len(bad)} synsets ({senses} senses) have no "
              f"definition to annotate against")

    return 1 if flagged else 0


if __name__ == "__main__":
    sys.exit(main())
