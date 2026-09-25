#!/usr/bin/env python3
"""Stage 10: draw a round from the open population and set up its run directory.

`book_verdicts.py` says which senses of book one still lack a verdict. A round
takes a stratified draw of those words and writes a run directory the stage-7
tools work in unchanged.

  ranked   words a ranking already exists for. Their selected.json and
           ranking.json entries are copied from the run that ranked them, and
           open.json names the senses to write, so the Enricher runs alone
           (`enrich_packets.py enricher --write-open`) on the same sentences
           the ranking was judged on.

The draw keeps to words the Ranker could see (at least 3 usable sentences, the
`select` rule) and skips two kinds, counting each: function words (the plan:
*so*, *say* and *now* head the list, all correctly plain) and words with more
open senses than --max-open (*make* has 46; writing each as a learner entry is
the concordance the Enricher's cap exists to prevent). Both wait for the
author's word on how they are judged.

Usage:
    python3 tools/stage10_draw.py ranked --population data/policy/stage10/population.json \\
        --n 30 --strata 3 --max-open 8 --out data/policy/stage10-r1
"""

import argparse
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def runs_holding():
    """(word, pos) -> (run name, selected entry, ranking entry), earliest run last
    so the stage 7 run wins where two runs ranked the same word."""
    held = {}
    runs = sorted((ROOT / "data/policy").glob("*/ranking.json"),
                  key=lambda p: (p.parent.name != "stage7-book1-run", p.parent.name))
    for ranking_path in reversed(runs):
        run = ranking_path.parent
        sel_path = run / "selected.json"
        if not sel_path.is_file():
            continue
        selected = {(e["word"].lower(), e["pos"]): e
                    for e in json.loads(sel_path.read_text(encoding="utf-8"))}
        for r in json.loads(ranking_path.read_text(encoding="utf-8")).get("entries", []):
            k = (r["word"].lower(), r["pos"])
            if k in selected:
                held[k] = (run.name, selected[k], r)
    return held


def stratified(words, n, strata, rng):
    size = len(words) / strata
    per, picked = n // strata, []
    for i in range(strata):
        band = words[int(i * size):int((i + 1) * size)]
        take = per if i < strata - 1 else n - len(picked)
        picked.extend(sorted(rng.sample(band, min(take, len(band))), key=words.index))
    return picked


def cmd_ranked(args):
    pop = json.loads(args.population.read_text(encoding="utf-8"))
    held = runs_holding()
    pool, skipped = [], {"function word": 0, f"over {args.max_open} open senses": 0,
                         "no earlier run holds it": 0}
    for w in pop["words"]:
        if not w["ranked"]:
            continue
        if w["stop"]:
            skipped["function word"] += 1
        elif len(w["open"]) > args.max_open:
            skipped[f"over {args.max_open} open senses"] += 1
        elif (w["word"].lower(), w["pos"]) not in held:
            skipped["no earlier run holds it"] += 1
        else:
            pool.append(w)
    rng = random.Random(args.seed)
    drawn = stratified(pool, args.n, args.strata, rng)

    args.out.mkdir(parents=True, exist_ok=True)
    if (args.out / "selected.json").exists():
        sys.exit(f"{args.out} already holds a draw - a draw is kept, not redrawn")
    selected, ranking, open_syns, source = [], [], {}, {}
    for w in drawn:
        run, sel, rank = held[(w["word"].lower(), w["pos"])]
        syns = [sid.rsplit(".", 1)[1] for sid in w["open"]]
        missing = set(syns) - set(rank["order"])
        if missing:
            sys.exit(f"{w['word']}: open senses {sorted(missing)} are not in {run}'s ranking")
        selected.append(sel)
        ranking.append(rank)
        open_syns[f"{sel['word']}|{sel['pos']}"] = syns
        source[f"{sel['word']}|{sel['pos']}"] = run
    (args.out / "selected.json").write_text(json.dumps(selected, indent=1, ensure_ascii=False),
                                            encoding="utf-8")
    (args.out / "ranking.json").write_text(json.dumps(
        {"accepted": len(ranking), "rejected": 0, "entries": ranking, "ranked_in": source},
        indent=1, ensure_ascii=False), encoding="utf-8")
    (args.out / "open.json").write_text(json.dumps(open_syns, indent=1, ensure_ascii=False),
                                        encoding="utf-8")
    (args.out / "draw.json").write_text(json.dumps({
        "kind": "ranked", "population": str(args.population), "counts": pop["counts"],
        "pool": len(pool), "skipped": skipped, "n": len(drawn), "strata": args.strata,
        "seed": args.seed, "max_open": args.max_open,
        "words": [{"word": w["word"], "pos": w["pos"], "occurrences": w["occurrences"],
                   "open": len(w["open"])} for w in drawn]}, indent=1, ensure_ascii=False),
        encoding="utf-8")
    print(f"pool {len(pool)} (skipped {skipped}); drew {len(drawn)} words, "
          f"{sum(len(v) for v in open_syns.values())} open senses -> {args.out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("ranked")
    r.add_argument("--population", type=Path, required=True)
    r.add_argument("--n", type=int, default=30)
    r.add_argument("--strata", type=int, default=3)
    r.add_argument("--max-open", type=int, default=8)
    r.add_argument("--seed", type=int, default=10)
    r.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    {"ranked": cmd_ranked}[args.cmd](args)


if __name__ == "__main__":
    main()
