"""Stage 7: carve a working set out of a `select` run.

Two jobs `enrich_packets.py select` does not do.

**Exclude what is already written.** `select` skips words already in the family
corpus, but not words a previous enrichment run already wrote. Stage 4's fifty
Pride and Prejudice entries came back at positions 0-49 of the stage 7 draw --
the whole head of the list -- which would have paid for them twice and, worse,
measured the pilot on the fifty entries the rubric was tuned against.

**Stratify.** The head of a book's lemma list is not the run. Occurrences fall
from 597 to 9 across the draw, and sentence evidence thins with them. A pilot
taken off the top reports the rate on the easiest tenth. `--strata` draws evenly
across frequency bands so the pilot measures where the run actually lives.

The draw is seeded and the seed is written into selection.json: same seed, same
hundred entries.
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

import enrich_packets as ep


def load_selected(d: Path):
    return json.loads((d / "selected.json").read_text(encoding="utf-8"))


def key(e):
    return (e["word"].lower(), e["pos"])


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--src", required=True, help="a directory written by `select`")
    p.add_argument("--out", required=True)
    p.add_argument("--exclude", action="append", default=[],
                   help="a selected.json whose entries are already written; repeatable")
    p.add_argument("--strata", type=int, default=0,
                   help="number of frequency bands to draw evenly from")
    p.add_argument("--n", type=int, default=0, help="0 keeps everything that survives --exclude")
    p.add_argument("--seed", type=int, default=7)
    args = p.parse_args()

    src, out = Path(args.src), Path(args.out)
    entries = load_selected(src)

    done = set()
    for path in args.exclude:
        for e in json.loads(Path(path).read_text(encoding="utf-8")):
            done.add(key(e))
    kept = [e for e in entries if key(e) not in done]
    dropped = len(entries) - len(kept)

    picked, bands = kept, []
    if args.n and args.n < len(kept):
        rng = random.Random(args.seed)
        if args.strata > 1:
            size = len(kept) / args.strata
            per = args.n // args.strata
            picked = []
            for i in range(args.strata):
                lo, hi = int(i * size), int((i + 1) * size)
                band = kept[lo:hi]
                take = per if i < args.strata - 1 else args.n - len(picked)
                draw = rng.sample(band, min(take, len(band)))
                draw.sort(key=lambda e: kept.index(e))
                picked.extend(draw)
                bands.append({"band": i + 1, "range": [lo, hi], "drawn": len(draw),
                              "occurrences": [band[0]["occurrences"], band[-1]["occurrences"]]})
        else:
            picked = rng.sample(kept, args.n)
            picked.sort(key=lambda e: kept.index(e))

    out.mkdir(parents=True, exist_ok=True)
    (out / "selected.json").write_text(json.dumps(picked, ensure_ascii=False, indent=1),
                                       encoding="utf-8")
    src_sel = json.loads((src / "selection.json").read_text(encoding="utf-8"))
    (out / "selection.json").write_text(json.dumps({
        "book_id": src_sel.get("book_id"), "book": src_sel.get("book"), "n": len(picked),
        "derived_from": str(src), "excluded_already_written": dropped,
        "exclude_files": args.exclude, "seed": args.seed, "strata": args.strata or None,
        "bands": bands or None,
        "rule": src_sel.get("rule"),
        "entries": [{"word": e["word"], "pos": e["pos"], "occurrences": e["occurrences"],
                     "senses": len(e["senses"]), "sentences": len(e["sentences"])}
                    for e in picked],
    }, ensure_ascii=False, indent=1), encoding="utf-8")

    ranker = [{k: e[k] for k in ("word", "pos", "book", "occurrences", "sentences")}
              | {"senses": [{k: s[k] for k in ("synset", "gloss", "wordnet_examples",
                                               "synonyms")} for s in e["senses"]]}
              for e in picked]
    ep.write_packets(ranker, out / "ranker-packets", "ranker")
    print(f"source {len(entries)}, already written {dropped}, eligible {len(kept)}, "
          f"picked {len(picked)}")
    for b in bands:
        print(f"  band {b['band']}: {b['drawn']} drawn from entries {b['range'][0]}-"
              f"{b['range'][1]}, occurrences {b['occurrences'][0]}->{b['occurrences'][1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
