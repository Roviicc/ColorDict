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

A family is ranked by its **median** member, with the peak reported alongside.
Ranking by the peak was the first instinct and it is wrong: Zipf scores a word
form, not a sense, so one common form drags a whole family to the top of the
list. Under a peak sort the first three families were *fashionable*, *successful*
and *cardinal*, all lifted there by *in* and *i* — function words that 5.3
excludes from annotation anyway, so the ranking was driven by words the shard
would never touch. The median is the honest signal (11.61), and it also keeps
shards shard-sized: peak put *cardinal* (259 members, 134 synsets) third.

The peak stays in the file. A family that is uniformly mid-band is a different
proposition from one whose head is common and whose members are all obscure,
and that difference is worth seeing — it just is not what orders the work.

Frequency alone is still the wrong queue, by either key. This is a *connotation*
dictionary, and the most reachable adjective families are largely neutral:
*finished*, *individual*, *whole*, *normal*, *high*. There is nothing for a tone
note to say about them, and a shard spent on them measures nothing. The 56
hand-picked families are 83% charged by SentiWordNet; the frequency-ranked head
of the list runs 36–62%. So eligibility takes two gates before frequency is
consulted at all:

  size    >= MIN_SIZE          a family of one has no spectrum to rank against
  charged >= MIN_CHARGED_PCT   it has to be a connotation family to be worth a note

Both gates are recorded per row rather than applied destructively — every
candidate family stays in the file, `eligible` says whether it passed, and the
sort puts the eligible ones on top. A later shard that wants the neutral band
can still find it.

Usage:
    python3 tools/worklist_build.py --pos a \
        --bulk data/entries/derived-bulk.jsonl --out data/worklist.tsv
"""

import argparse
import json
import statistics
from pathlib import Path

from wordfreq import zipf_frequency

ROOT = Path(__file__).resolve().parent.parent
POS_FILE = {"a": "adjective-families.json", "v": "verb-families.json", "n": "noun-families.json"}
COLUMNS = ["family_id", "head", "size", "peak_zipf", "median_zipf", "peak_word",
           "charged", "labelled", "charged_pct", "eligible", "covered", "synsets"]

# A family smaller than this has no spectrum; one less charged than this is not
# a connotation family. Both are gates on eligibility, not filters on the file.
MIN_SIZE = 8
MIN_CHARGED_PCT = 0.7


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


def load_labels(path):
    """synset -> connotation label, from the same SentiWordNet join the corpus uses.

    Read from the derived bulk rather than SentiWordNet directly so the worklist
    agrees with what the corpus actually shipped: the label a sense carries here
    is the label a reader will see under it.
    """
    labels = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            for sense in json.loads(line)["senses"]:
                synset = sense.get("source", {}).get("synset")
                if synset:
                    labels[synset] = sense.get("connotation", {}).get("label")
    return labels


def zipf(word):
    """Multi-word entries score as their rarest part: *clapped out* is only as
    reachable as *clapped*. wordfreq returns 0.0 for a word it has never seen,
    which means unknown rather than rare, so callers drop zeros."""
    parts = [p for p in word.replace("_", " ").split() if p]
    if not parts:
        return 0.0
    return min(zipf_frequency(p, "en") for p in parts)


def build(pos, labels):
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

        # Charge is counted per member sense, not per unique word: a family is a
        # spectrum of senses, and it is the senses a note gets written against.
        member_labels = [labels.get(m["synset"]) for m in family["members"]]
        labelled = sum(1 for l in member_labels if l)
        charged = sum(1 for l in member_labels if l in ("positive", "negative"))
        size = len(words)

        rows.append({
            "family_id": family["id"],
            "head": family["head_words"][0] if family["head_words"] else words[0],
            "size": size,
            "peak_zipf": round(peak, 2),
            "median_zipf": round(statistics.median(seen), 2),
            "peak_word": words[scores.index(peak)],
            "charged": charged,
            "labelled": labelled,
            "charged_pct": round(charged / labelled, 2) if labelled else 0.0,
            "eligible": int(size >= MIN_SIZE and labelled > 0
                            and charged / labelled >= MIN_CHARGED_PCT),
            "covered": len(synsets & done),
            "synsets": len(synsets),
        })

    rows.sort(key=lambda r: (-r["eligible"], -r["median_zipf"], -r["peak_zipf"], r["head"]))
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--pos", default="a", choices=sorted(POS_FILE))
    ap.add_argument("--bulk", type=Path, default=ROOT / "data/entries/derived-bulk.jsonl",
                    help="corpus JSONL supplying each sense's connotation label")
    ap.add_argument("--out", type=Path, default=ROOT / "data/worklist.tsv")
    args = ap.parse_args()

    labels = load_labels(args.bulk)
    rows = build(args.pos, labels)
    with open(args.out, "w", encoding="utf-8", newline="") as fh:
        fh.write("\t".join(COLUMNS) + "\n")
        for r in rows:
            fh.write("\t".join(str(r[c]) for c in COLUMNS) + "\n")

    touched = sum(1 for r in rows if r["covered"])
    eligible = sum(1 for r in rows if r["eligible"])
    queue = sum(1 for r in rows if r["eligible"] and not r["covered"])
    print(f"{len(rows)} families -> {args.out}")
    print(f"{touched} families already touched by a shard")
    print(f"{eligible} eligible (size >= {MIN_SIZE}, charged >= {MIN_CHARGED_PCT:.0%}); "
          f"{queue} of those untouched and queued")


if __name__ == "__main__":
    main()
