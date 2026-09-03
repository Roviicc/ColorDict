#!/usr/bin/env python3
"""Map each adverb sense to the adjective sense it is the adverb of.

Adverb inheritance (tools/adverb_inherit.py) works by morphology: *angrily* is
the adverb of *angry*, so it takes *angry*'s judgement. Morphology names the
lemma, though, not the sense, and audit 004 caught the gap: *vulgarly* is
glossed "in a smutty manner" and inherited the note written for *vulgar*
"lacking refinement or cultivation or taste" - the right lemma, the wrong
sense.

WordNet already knows the answer. Every derived adverb carries a `pertainym`
SenseRelation pointing at one adjective *sense*, and that sense resolves to one
synset. This extracts that mapping so inheritance can require the adjective
synset a note was written against to be the synset the adverb actually points
at.

Output: {"<adverb-synset>": {"<adverb word>": "<adjective synset>"}}

Usage:
    python3 tools/pertainym_extract.py \
        --wordnet data/source/english-wordnet-2025.xml.gz \
        --out data/build/pertainyms.json
"""

import argparse
import gzip
import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--wordnet", type=Path,
                    default=ROOT / "data/source/english-wordnet-2025.xml.gz")
    ap.add_argument("--out", type=Path, default=ROOT / "data/build/pertainyms.json")
    args = ap.parse_args()

    if not args.wordnet.exists():
        sys.exit(f"{args.wordnet} is missing - it is a gitignored source download")

    opener = gzip.open if args.wordnet.suffix == ".gz" else open
    with opener(args.wordnet, "rb") as fh:
        tree = ET.parse(fh)

    # First pass: every sense id -> its synset, so a pertainym target resolves.
    sense_synset = {}
    lemma_of = {}
    for entry in tree.iter("LexicalEntry"):
        lemma = entry.find("Lemma")
        word = lemma.get("writtenForm") if lemma is not None else None
        pos = lemma.get("partOfSpeech") if lemma is not None else None
        for sense in entry.findall("Sense"):
            sense_synset[sense.get("id")] = sense.get("synset")
            lemma_of[sense.get("id")] = (word, pos)

    out = {}
    pairs = 0
    for entry in tree.iter("LexicalEntry"):
        lemma = entry.find("Lemma")
        if lemma is None or lemma.get("partOfSpeech") != "r":
            continue
        word = lemma.get("writtenForm")
        for sense in entry.findall("Sense"):
            for rel in sense.findall("SenseRelation"):
                if rel.get("relType") != "pertainym":
                    continue
                target = sense_synset.get(rel.get("target"))
                if not target:
                    continue
                out.setdefault(sense.get("synset"), {})[word] = target
                pairs += 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=0, sort_keys=True)
    print(f"{pairs} adverb senses point at an adjective synset "
          f"({len(out)} adverb synsets)")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
