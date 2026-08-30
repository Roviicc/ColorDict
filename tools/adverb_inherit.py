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

Morphology names the lemma, not the sense. WordNet's `pertainym` relation names
the sense, so where it exists it decides: an adverb inherits only if the
adjective synset the note was written against is the one the adverb actually
points at. *vulgarly* is glossed "in a smutty manner" and points at *vulgar*
"indecent", while the note we hold belongs to *vulgar* "lacking refinement", so
it is not inherited (tools/pertainym_extract.py, audit 004).

Usage:
    python3 tools/adverb_inherit.py \
        --bulk data/entries/derived-bulk.jsonl \
        --overlay data/entries/overlays/families-001.overlay.jsonl \
        --overlay data/entries/overlays/families-002.overlay.jsonl \
        --out data/entries/overlays/adverbs-001.overlay.jsonl
"""

import argparse
import json
import re
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


def _common_prefix(a, b):
    n = 0
    for x, y in zip(a, b):
        if x != y:
            break
        n += 1
    return n


def relates(base, definition):
    """Does this adverb sense look like it is about the base adjective?

    Audit 002 found the real failure in adverb inheritance: a multi-sense
    adverb had its adjective's charge stamped on every sense, including the
    ones that mean something else. *furiously* covers wind as well as anger,
    *thinly* covers viscosity and insincerity, *slightly* is a bare degree
    adverb - and all three carried a judgement belonging to a sense they do
    not have.

    A single-sense adverb is safe: morphology says whose adverb it is and
    there is nowhere else for the charge to land. For a multi-sense adverb we
    keep only the senses whose gloss is visibly about the adjective, matching
    on a stem so that *angrily* "with anger" and *anxiously* "with anxiety"
    still qualify. Where nothing matches, the adverb is skipped - the same
    refusal to invent that governs the rest of this file.
    """
    need = max(4, len(base) - 2)
    return any(_common_prefix(base, w) >= need
               for w in re.findall(r"[a-z]+", definition.lower()))


def lend(patch_from, source):
    """The part of an adjective's judgement an adverb can carry."""
    patch = {}
    family = patch_from.get("family")
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
    return patch


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
    ap.add_argument("--pertainyms", type=Path,
                    default=Path(__file__).resolve().parent.parent
                    / "data/build/pertainyms.json",
                    help="output of pertainym_extract.py; the sense-level check "
                         "is skipped if it is absent")
    args = ap.parse_args()

    pertainyms = {}
    if args.pertainyms and args.pertainyms.exists():
        pertainyms = json.loads(args.pertainyms.read_text(encoding="utf-8"))
    else:
        print(f"note: {args.pertainyms} absent - inheriting on morphology alone",
              file=sys.stderr)

    # What has been said about each adjective, keyed by headword, and then by
    # the sense the judgement was written against - an adverb points at one
    # sense of its adjective, and only that sense's note belongs to it.
    annotated, source_synset = {}, {}
    by_sense = {}
    for path in args.overlay:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                for sid, patch in (rec.get("senses") or {}).items():
                    if patch.get("family") or patch.get("tone"):
                        synset = sid.rsplit(".", 1)[-1]
                        by_sense.setdefault(rec["word"], {})[synset] = patch
                        if rec["word"] not in annotated:
                            annotated[rec["word"]] = patch
                            # Which sense of the adjective the note was written
                            # against - the pertainym check needs it.
                            source_synset[rec["word"]] = synset

    adverb_senses = {}
    for line in open(args.bulk, encoding="utf-8"):
        entry = json.loads(line)
        senses = [s for s in entry["senses"] if s["part_of_speech"] == "adverb"]
        if senses:
            adverb_senses[entry["word"]] = senses

    out, inherited, skipped_ambiguous, wrong_sense = [], 0, 0, 0
    for adverb, senses in sorted(adverb_senses.items()):
        source = next((b for b in base_forms(adverb) if b in annotated), None)
        if source is None:
            continue
        patch_from = annotated[source]
        sense_note = {}
        patch = lend(patch_from, source)
        if not patch:
            continue
        # A single-sense adverb takes the charge outright. A multi-sense one
        # takes it only on the senses that are actually about the adjective -
        # see relates().
        eligible = (senses if len(senses) == 1
                    else [s for s in senses if relates(source, s["definition"])])
        # Where WordNet names the adjective sense, it overrules morphology.
        # *benignly* points at *benign* "pleasant and beneficial", not at the
        # "kindness of disposition" sense we happened to annotate first, so it
        # takes the note written for the sense it points at - and if we have no
        # note for that sense, it takes none. Senses with no pertainym fall
        # back to the morphological rule above.
        want = source_synset.get(source)
        notes = by_sense.get(source, {})
        kept = []
        for sense in eligible:
            synset = (sense.get("source") or {}).get("synset", "")
            target = pertainyms.get(synset, {}).get(adverb)
            if target and target != want:
                if target not in notes:
                    wrong_sense += 1
                    continue
                sense_note[synset] = notes[target]
            kept.append(sense)
        eligible = kept
        if not eligible:
            skipped_ambiguous += 1
            continue
        # The tone note goes on the first eligible sense only - it reads
        # identically on each, and `genuinely` printed the same sentence twice.
        # The later senses still carry the spectrum row, which shows the
        # judgement without repeating the prose.
        sense_patches = {}
        for position, sense in enumerate(eligible):
            synset = (sense.get("source") or {}).get("synset", "")
            # A sense with its own pertainym note takes that one, not the
            # lemma's first.
            one = (lend(sense_note[synset], source) if synset in sense_note
                   else dict(patch))
            if position:
                one.pop("tone", None)
            if one:
                sense_patches[sense["id"]] = one
        if not sense_patches:
            continue
        out.append({"word": adverb, "senses": sense_patches})
        inherited += len(sense_patches)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for rec in out:
            fh.write(json.dumps(rec, ensure_ascii=False,
                                separators=(",", ":")) + "\n")
    print(f"{len(annotated)} annotated adjectives -> "
          f"{len(out)} adverbs, {inherited} senses")
    if skipped_ambiguous:
        print(f"{skipped_ambiguous} adverbs skipped - no sense clearly about the adjective")
    if wrong_sense:
        print(f"{wrong_sense} adverb senses declined - WordNet points them at a "
              f"different sense of the adjective")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
