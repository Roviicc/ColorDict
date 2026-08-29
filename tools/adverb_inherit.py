#!/usr/bin/env python3
"""Derive adverb connotation from the adjectives it is built from.

Adverbs cannot be grouped the way the other parts of speech are. WordNet gives
them no hypernyms and no 'similar' clusters, and only nine adverb senses in the
2024 release carry a derivation link - so neither the adjective strategy
(clusters) nor the verb strategy (troponym siblings) applies.

Morphology does the job instead: English adverbs are overwhelmingly an
adjective plus -ly, and 91% of the -ly adverbs in our corpus have their base
adjective present. `harshly` should carry what `harsh` carries.

So an annotated adjective lends its charge, tone and register to its adverb,
with the tone reworded from "X is ..." to "the adverb of X". Nothing is
invented: an adverb whose adjective was never annotated is simply skipped.

Usage:
    python3 tools/adverb_inherit.py \
        --bulk data/entries/derived-bulk.jsonl \
        --overlay data/entries/overlays/families-001.overlay.jsonl \
        --overlay data/entries/overlays/families-002.overlay.jsonl \
        --out data/entries/overlays/adverbs-001.overlay.jsonl
"""

import argparse
import json
import sys
from pathlib import Path


def base_forms(adverb):
    """Candidate adjectives an -ly adverb could be built from."""
    if not adverb.endswith("ly") or len(adverb) < 5:
        return []
    stem = adverb[:-2]
    out = [stem, stem + "e"]                       # harshly->harsh, freely->free
    if stem.endswith("i"):
        out.append(stem[:-1] + "y")                # happily->happy
    if stem.endswith("l"):
        out.append(stem[:-1])                      # fully->full
    if stem.endswith(("ab", "ib")):
        out.append(stem + "le")                    # ably->able
    return out


def adverbise(tone, adjective):
    """Reword an adjective's note so it reads correctly of the adverb."""
    if not tone:
        return None
    first = tone[0].lower() + tone[1:]
    return f"The adverb of *{adjective}*: {first}"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--bulk", type=Path, required=True)
    ap.add_argument("--overlay", type=Path, required=True, action="append",
                    help="annotated adjective overlays to inherit from")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    # What has been said about each adjective, keyed by headword.
    annotated = {}
    for path in args.overlay:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                for sid, patch in (rec.get("senses") or {}).items():
                    if patch.get("family") or patch.get("tone"):
                        annotated.setdefault(rec["word"], patch)

    adverb_senses = {}
    for line in open(args.bulk, encoding="utf-8"):
        entry = json.loads(line)
        senses = [s for s in entry["senses"] if s["part_of_speech"] == "adverb"]
        if senses:
            adverb_senses[entry["word"]] = senses

    out, inherited = [], 0
    for adverb, senses in sorted(adverb_senses.items()):
        source = next((b for b in base_forms(adverb) if b in annotated), None)
        if source is None:
            continue
        patch_from = annotated[source]
        family = patch_from.get("family")
        patch = {}
        if family:
            # Same family, same charge - the adverb sits where its adjective does.
            patch["family"] = family
            patch["label"] = ("positive" if family["charge"] >= 1
                              else "negative" if family["charge"] <= -1 else "neutral")
        tone = adverbise(patch_from.get("tone"), source)
        if tone:
            patch["tone"] = tone
        if patch_from.get("usage_labels"):
            patch["usage_labels"] = patch_from["usage_labels"]
        if not patch:
            continue
        # Apply to every adverb sense; an adverb rarely has more than one.
        out.append({"word": adverb,
                    "senses": {s["id"]: dict(patch) for s in senses}})
        inherited += len(senses)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for rec in out:
            fh.write(json.dumps(rec, ensure_ascii=False,
                                separators=(",", ":")) + "\n")
    print(f"{len(annotated)} annotated adjectives -> "
          f"{len(out)} adverbs, {inherited} senses")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
