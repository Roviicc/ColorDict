#!/usr/bin/env python3
"""Stage 10: which senses of a book's words still lack a verdict anyone made.

Stage 10 is done when no sense on a book-one word lacks a connotation verdict
someone made - null is a claim, not a default. This measures what is left and
writes the population the stage-10 draw works from.

A sense has a verdict when a hand wrote one, as it ships in batch-0001.jsonl:
  - a tone note or an explanation (the family path, stage 1's hand notes, or an
    adverb carrying the note of the adjective it inherits from)
  - a learner line: the Enricher writes one for every sense it answers the
    connotation question for, null or candidate, and for no other

A SentiWordNet label is not a verdict (plan 11.5). It is a prior.

Words are the book's lemma+POS records the dictionary can serve - the same index
enrich_packets.py selects from. A word is "ranked" when any run's ranking.json
holds it, so its open senses need the Enricher only; the rest were never opened
and need the Ranker first.

Function words are marked, not dropped: *so*, *say* and *now* head the list with
ten senses each, all correctly plain, so the draw takes them last.

Usage:
    python3 tools/book_verdicts.py --book data/build/books/3f6bb9d6f78e0293 \
        --out data/policy/stage10/population.json
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import book_ingest as bi  # noqa: E402
from enrich_packets import UPOS_TO_POS, load_bulk  # noqa: E402
from plain_packets import load_batch  # noqa: E402


def judged(sense):
    conn = sense.get("connotation") or {}
    return bool(conn.get("tone") or conn.get("explanation") or sense.get("learner"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--book", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    from spacy.lang.en.stop_words import STOP_WORDS

    meta = json.loads((args.book / "meta.json").read_text(encoding="utf-8"))
    records = [json.loads(l) for l in (args.book / "lemmas.jsonl").open(encoding="utf-8")
               if l.strip()]
    lookup = bi.wordnet_index()
    records = [r for r in records
               if r["part_of_speech"] in UPOS_TO_POS and lookup(r["lemma"], r["part_of_speech"])]
    bulk = load_bulk(r["lemma"] for r in records)
    batch = load_batch()
    ranked = set()
    for path in sorted((ROOT / "data/policy").glob("*/ranking.json")):
        for e in json.loads(path.read_text(encoding="utf-8")).get("entries", []):
            ranked.add((e["word"].lower(), e["pos"]))

    words, unserved = [], 0
    for r in records:
        pos = UPOS_TO_POS[r["part_of_speech"]]
        entry = bulk.get(r["lemma"].lower())
        if entry is None:
            unserved += 1
            continue
        ids = [s["id"] for s in entry["senses"] if s["part_of_speech"] == pos]
        if not ids:
            unserved += 1
            continue
        open_ids = [sid for sid in ids if not (sid in batch and judged(batch[sid][1]))]
        words.append({"word": entry["word"], "pos": pos,
                      "occurrences": r["corpus"]["total_occurrences"],
                      "senses": len(ids), "open": open_ids,
                      "ranked": (entry["word"].lower(), pos) in ranked,
                      "stop": r["lemma"].lower() in STOP_WORDS})
    words.sort(key=lambda w: (-w["occurrences"], w["word"], w["pos"]))

    left = [w for w in words if w["open"]]
    def count(ws):
        return {"words": len(ws), "senses": sum(len(w["open"]) for w in ws)}
    counts = {
        "book_words": len(words),
        "book_senses": sum(w["senses"] for w in words),
        "open": count(left),
        "open_ranked": count([w for w in left if w["ranked"]]),
        "open_never_opened": count([w for w in left if not w["ranked"]]),
        "open_stop_words": count([w for w in left if w["stop"]]),
        "unserved_records": unserved,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps({
        "book_id": meta["book_id"], "book": meta.get("title"),
        "rule": ("a sense has a verdict when it ships with a tone note, an explanation or "
                 "an Enricher's learner line; a SentiWordNet label is not one"),
        "counts": counts, "words": left}, indent=1, ensure_ascii=False) + "\n",
        encoding="utf-8")
    print(json.dumps(counts, indent=1))
    print(f"-> {args.out}")


if __name__ == "__main__":
    main()
