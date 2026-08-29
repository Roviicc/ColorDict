#!/usr/bin/env python3
"""Step 03 of the Connotation Dictionary: turn annotated families into overlays.

Reads a family annotation file (data/families/annotated-*.json) and emits the
overlay JSONL that tools/dict_enrich_apply.py consumes. One authored judgement
per family member becomes a sense patch carrying the charge, the spectrum row,
and - where written - the tone note and register labels.

Annotation file shape:
    {"families": [{
       "id": "thinness",
       "axis": "condemning to praising",      # optional; defaults to that
       "members": [
         {"word": "skinny", "synset": "oewn-00993331-s", "charge": -2,
          "tone": "...", "usage": ["informal"], "examples": ["..."]},
         ...],
       "anchors": ["emaciated", "skinny", "thin", "slim", "svelte"]  # optional
    }]}

Charge maps to connotation.label and score: <=-1 negative, >=+1 positive,
0 neutral; score = charge/3 so the validator's label/score agreement holds.
The spectrum shown in the article is the anchors (or every member, deduped by
charge) - not all 58 members of a big family.

Usage:
    python3 tools/family_apply.py \
        --families data/families/annotated-001.json \
        --out data/entries/overlays/families-001.overlay.jsonl
"""

import argparse
import json
import sys
from collections import OrderedDict
from pathlib import Path

DEFAULT_AXIS = "condemning → praising"


def slug(word):
    return word.lower().replace(" ", "_").replace("'", "")


def label_for(charge):
    return "positive" if charge >= 1 else "negative" if charge <= -1 else "neutral"


def build_spectrum(family):
    """Anchors if given, else one representative word per distinct charge."""
    members = family["members"]
    by_word = {m["word"]: m for m in members}
    if family.get("anchors"):
        picked = [(w, by_word[w]["charge"]) for w in family["anchors"] if w in by_word]
    else:
        seen = {}
        for m in members:
            seen.setdefault(m["charge"], m["word"])
        picked = [(w, c) for c, w in sorted(seen.items())]
    return sorted(picked, key=lambda p: p[1])


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--families", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    data = json.loads(args.families.read_text(encoding="utf-8"))
    by_word = OrderedDict()
    members_out = 0

    for family in data["families"]:
        spectrum = build_spectrum(family)
        if len(spectrum) < 2:
            sys.exit(f"family {family['id']}: spectrum needs 2+ anchors")
        fam_common = {"id": family["id"], "spectrum": [list(p) for p in spectrum]}
        axis = family.get("axis")
        for m in family["members"]:
            charge = m["charge"]
            if not isinstance(charge, int) or not -3 <= charge <= 3:
                sys.exit(f"{family['id']}/{m['word']}: charge must be an int in [-3, 3]")
            sense_id = f"{slug(m['word'])}.{m['synset']}"
            patch = {
                "label": label_for(charge),
                "family": dict(fam_common, charge=charge,
                               **({"axis": axis} if axis else {})),
            }
            # The fabrication rule: prose only where a judgement exists.
            if m.get("tone"):
                patch["tone"] = m["tone"]
            if m.get("explanation") and charge != 0:
                patch["explanation"] = m["explanation"]
            if m.get("usage"):
                patch["usage_labels"] = m["usage"]
            if m.get("examples"):
                patch["examples"] = m["examples"]
            rec = by_word.setdefault(m["word"], {"word": m["word"], "senses": {}})
            rec["senses"][sense_id] = patch
            members_out += 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for rec in by_word.values():
            fh.write(json.dumps(rec, ensure_ascii=False, separators=(",", ":")) + "\n")

    print(f"{len(data['families'])} families, {members_out} annotated senses "
          f"-> {len(by_word)} overlay words")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
