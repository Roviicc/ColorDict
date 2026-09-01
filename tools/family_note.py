#!/usr/bin/env python3
"""Derive a sense's note from its place on its family spectrum.

Three sampled audits found the same thing three different ways: a hand-written
note is free prose, free prose asserts, and an assertion about English can be
wrong in more ways than a rule can enumerate. 44%, then 14%, then 22%.

The spectrum, over the same 150 cards, was never wrong once - because a charge
is checked by the validator and a family membership either exists or does not.

So the note is derived from the spectrum instead of written beside it. It says
where the word sits among its neighbours and nothing else. It cannot contradict
the gloss, because it makes no claim about the gloss; it cannot invent a
distribution or an etymology, because it has no vocabulary for either. What is
left to audit is the charge, which is a comparative judgement a reader can
check at a glance.

Usage:
    python3 tools/family_note.py --families data/families/annotated-006.json
    python3 tools/family_note.py --families data/families/annotated-006.json --apply
"""

import argparse
import json
from pathlib import Path


def _pick(members, charge, direction, prefer):
    """The nearest member on one side, preferring a spectrum anchor."""
    side = [m for m in members
            if (m["charge"] < charge if direction < 0 else m["charge"] > charge)]
    if not side:
        return None
    best = max(m["charge"] for m in side) if direction < 0 \
        else min(m["charge"] for m in side)
    nearest = [m for m in side if m["charge"] == best]
    for word in prefer:
        for m in nearest:
            if m["word"] == word:
                return m
    return nearest[0]


def note_for(member, family):
    """One sentence placing this word among its neighbours."""
    members = [m for m in family["members"] if not m.get("_skip")]
    if len(members) < 2:
        return None
    anchors = family.get("anchors") or []
    charge = member["charge"]
    charges = [m["charge"] for m in members]

    harsher = _pick(members, charge, -1, anchors)
    milder = _pick(members, charge, +1, anchors)

    # An all-positive family reads as degrees of praise, not degrees of blame,
    # so the comparison words change with the company the word keeps.
    warm = min(charges) >= 0

    if harsher and milder:
        if warm:
            return f"Warmer than *{harsher['word']}*, short of *{milder['word']}*."
        if charge >= 1:
            return f"Warmer than *{harsher['word']}*, short of *{milder['word']}*."
        return f"Milder than *{harsher['word']}*, harsher than *{milder['word']}*."

    if harsher and not milder:
        if warm or charge >= 1:
            return f"The warmest its family has - beyond *{harsher['word']}*."
        return f"The mildest of its family; *{harsher['word']}* is the step up."

    if milder and not harsher:
        if warm or charge >= 1:
            return f"The plainest of its family; *{milder['word']}* goes further."
        return f"The harshest its family has - past *{milder['word']}*."

    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--families", type=Path, required=True)
    ap.add_argument("--apply", action="store_true",
                    help="write the derived notes into the shard file")
    ap.add_argument("--limit", type=int, default=0,
                    help="show only the first N, for a quick look")
    args = ap.parse_args()

    data = json.loads(args.families.read_text(encoding="utf-8"))
    shown = changed = skipped = 0

    for family in data["families"]:
        for member in family["members"]:
            if member.get("_skip"):
                continue
            note = note_for(member, family)
            if not note:
                skipped += 1
                continue
            if not args.apply and (not args.limit or shown < args.limit):
                print(f"{member['word']:18s} {member['charge']:+d}")
                print(f"   was  {member.get('tone', '')}")
                print(f"   now  {note}")
                shown += 1
            if args.apply:
                member["tone"] = note
            changed += 1

    if args.apply:
        with open(args.families, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
        print(f"rewrote {changed} notes in {args.families}")
    else:
        print(f"\n{changed} notes derivable, {skipped} without a neighbour to compare")


if __name__ == "__main__":
    main()
