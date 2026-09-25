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

  new      words never opened. Each is selected exactly as `enrich_packets.py
           select` would (same senses, same six spread sentences) and the
           Ranker's packets are cut; after the ranking, open.json makes the
           Enricher write every sense.

The draw keeps to words the Ranker could see (at least 3 usable sentences, the
`select` rule) and skips two kinds, counting each: function words (the plan:
*so*, *say* and *now* head the list, all correctly plain) and words with more
open senses than --max-open (*make* has 46; writing each as a learner entry is
the concordance the Enricher's cap exists to prevent). Both wait for the
author's word on how they are judged.

Usage:
    python3 tools/stage10_draw.py ranked --population data/policy/stage10/population.json \\
        --n 30 --strata 3 --max-open 8 --out data/policy/stage10-r1
    python3 tools/stage10_draw.py new --population data/policy/stage10/population.json \
        --book data/build/books/3f6bb9d6f78e0293 --n 30 --out data/policy/stage10-r2
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


def cmd_new(args):
    sys.path.insert(0, str(ROOT / "tools"))
    import enrich_packets as ep
    pop = json.loads(args.population.read_text(encoding="utf-8"))
    meta = json.loads((args.book / "meta.json").read_text(encoding="utf-8"))
    title = meta.get("title") or meta.get("source_filename")
    sentences = [json.loads(l) for l in (args.book / "sentences.jsonl").open(encoding="utf-8")
                 if l.strip()]
    forms = {}
    for line in (args.book / "lemmas.jsonl").open(encoding="utf-8"):
        if line.strip():
            r = json.loads(line)
            if r["part_of_speech"] in ep.UPOS_TO_POS:
                forms[(r["lemma"].lower(), ep.UPOS_TO_POS[r["part_of_speech"]])] = r
    pool, skipped = [], {"function word": 0, f"over {args.max_open} senses": 0,
                         f"under {ep.MIN_SENTENCES} usable sentences": 0}
    samples = {}
    for w in pop["words"]:
        if w["ranked"]:
            continue
        if w["stop"]:
            skipped["function word"] += 1
            continue
        if len(w["open"]) > args.max_open:
            skipped[f"over {args.max_open} senses"] += 1
            continue
        rec = forms[(w["word"].lower(), w["pos"])]
        sample = ep.sample_sentences(sentences, list(rec["corpus"]["forms"]))
        if len(sample) < ep.MIN_SENTENCES:
            skipped[f"under {ep.MIN_SENTENCES} usable sentences"] += 1
            continue
        samples[(w["word"].lower(), w["pos"])] = (rec, sample)
        pool.append(w)
    drawn = stratified(pool, args.n, args.strata, random.Random(args.seed))

    args.out.mkdir(parents=True, exist_ok=True)
    if (args.out / "selected.json").exists():
        sys.exit(f"{args.out} already holds a draw - a draw is kept, not redrawn")
    bulk = ep.load_bulk(w["word"] for w in drawn)
    selected, open_syns = [], {}
    for w in drawn:
        rec, sample = samples[(w["word"].lower(), w["pos"])]
        entry = bulk[w["word"].lower()]
        senses = [s for s in entry["senses"] if s["part_of_speech"] == w["pos"]]
        selected.append({
            "word": entry["word"], "pos": w["pos"], "book": title,
            "occurrences": rec["corpus"]["total_occurrences"], "forms": rec["corpus"]["forms"],
            "senses": [{"synset": s["source"]["synset"], "sense_id": s["id"],
                        "gloss": s["definition"],
                        "wordnet_examples": (s.get("examples") or [])[:2],
                        "synonyms": (s.get("synonyms") or [])[:6]} for s in senses],
            "sentences": sample})
        open_syns[f"{entry['word']}|{w['pos']}"] = [sid.rsplit(".", 1)[1] for sid in w["open"]]
    (args.out / "selected.json").write_text(json.dumps(selected, indent=1, ensure_ascii=False),
                                            encoding="utf-8")
    (args.out / "open.json").write_text(json.dumps(open_syns, indent=1, ensure_ascii=False),
                                        encoding="utf-8")
    ranker = [{k: e[k] for k in ("word", "pos", "book", "occurrences", "sentences")}
              | {"senses": [{k: s[k] for k in ("synset", "gloss", "wordnet_examples", "synonyms")}
                            for s in e["senses"]]} for e in selected]
    ep.write_packets(ranker, args.out / "ranker-packets", "ranker")
    (args.out / "draw.json").write_text(json.dumps({
        "kind": "new", "population": str(args.population), "counts": pop["counts"],
        "pool": len(pool), "skipped": skipped, "n": len(drawn), "strata": args.strata,
        "seed": args.seed, "max_open": args.max_open,
        "words": [{"word": w["word"], "pos": w["pos"], "occurrences": w["occurrences"],
                   "open": len(w["open"])} for w in drawn]}, indent=1, ensure_ascii=False),
        encoding="utf-8")
    print(f"pool {len(pool)} (skipped {skipped}); drew {len(drawn)} words, "
          f"{sum(len(v) for v in open_syns.values())} senses -> {args.out}")


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
    w = sub.add_parser("new")
    w.add_argument("--population", type=Path, required=True)
    w.add_argument("--book", type=Path, required=True)
    w.add_argument("--n", type=int, default=30)
    w.add_argument("--strata", type=int, default=3)
    w.add_argument("--max-open", type=int, default=8)
    w.add_argument("--seed", type=int, default=10)
    w.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    {"ranked": cmd_ranked, "new": cmd_new}[args.cmd](args)


if __name__ == "__main__":
    main()
