#!/usr/bin/env python3
"""Rank family candidates by how reachable their words are, and write the worklist.

Plan 3 calls for `data/worklist.tsv` — "frequency-ranked headwords, our unit of
work" — and 5.1 builds the whole batch ladder on it, but no frequency source was
ever wired in. Every shard to date was picked by hand and by family size, so
nothing has confirmed we annotate the words a reader actually meets.

The measure is Zipf frequency (wordfreq), not a rank in a top-N list. Two
reasons. A top-10,000 list cannot see the band that matters: *unctuous*,
*lugubrious* and *mawkish* are absent from it, and those are exactly the words
whose force a reader cannot guess. A subtitle-derived list (OpenSubtitles
en_50k) misses them too, because they belong to written English, not spoken.
wordfreq blends books with other corpora, so it scores the whole range: *the*
7.73, *good* 6.12, *asinine* 2.56, *snivel* 1.40.

A family is ranked by its most reachable member — the word most likely to be
looked up — with the median reported alongside, since a family whose head is
common but whose members are all obscure is a different proposition from one
that is uniformly mid-band.

Usage:
    python3 tools/worklist_build.py --pos a --out data/worklist.tsv
"""

import argparse
import json
import statistics
from pathlib import Path

from wordfreq import zipf_frequency

ROOT = Path(__file__).resolve().parent.parent
POS_FILE = {"a": "adjective-families.json", "v": "verb-families.json", "n": "noun-families.json"}
COLUMNS = ["family_id", "head", "size", "peak_zipf", "median_zipf", "peak_word", "covered", "synsets"]


def annotated_synsets():
    """Synsets already carrying hand-written notes.

    The join has to go through members, not family ids: the annotated shards key
    a family by the human name it was authored under (`thinness`, `self-regard`)
    while `family_extract.py` keys it by the head synset. Matching on the id
    fields silently reports every family as unannotated, which would send the
    loop back over work already done.
    """
    done = set()
    for path in sorted((ROOT / "data/families").glob("annotated-*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for family in data["families"]:
            for member in family["members"]:
                if member.get("tone"):
                    done.add(member["synset"])
    return done


def zipf(word):
    """Multi-word entries score as their rarest part: *clapped out* is only as
    reachable as *clapped*. wordfreq returns 0.0 for a word it has never seen,
    which means unknown rather than rare, so callers drop zeros."""
    parts = [p for p in word.replace("_", " ").split() if p]
    if not parts:
        return 0.0
    return min(zipf_frequency(p, "en") for p in parts)


def build(pos):
    families = json.loads((ROOT / "data/build" / POS_FILE[pos]).read_text(encoding="utf-8"))["families"]
    done = annotated_synsets()

    rows = []
    for family in families:
        words = sorted({m["word"] for m in family["members"]})
        synsets = {m["synset"] for m in family["members"]}
        scores = [zipf(w) for w in words]
        seen = [s for s in scores if s > 0]
        if not seen:
            continue
        peak = max(seen)
        rows.append({
            "family_id": family["id"],
            "head": family["head_words"][0] if family["head_words"] else words[0],
            "size": len(words),
            "peak_zipf": round(peak, 2),
            "median_zipf": round(statistics.median(seen), 2),
            "peak_word": words[scores.index(peak)],
            "covered": len(synsets & done),
            "synsets": len(synsets),
        })

    rows.sort(key=lambda r: -r["peak_zipf"])
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--pos", default="a", choices=sorted(POS_FILE))
    ap.add_argument("--out", type=Path, default=ROOT / "data/worklist.tsv")
    args = ap.parse_args()

    rows = build(args.pos)
    with open(args.out, "w", encoding="utf-8", newline="") as fh:
        fh.write("\t".join(COLUMNS) + "\n")
        for r in rows:
            fh.write("\t".join(str(r[c]) for c in COLUMNS) + "\n")

    touched = sum(1 for r in rows if r["covered"])
    print(f"{len(rows)} families -> {args.out}")
    print(f"{touched} families already touched by a shard")


if __name__ == "__main__":
    main()
