#!/usr/bin/env python3
"""Draw a random sample of reviewed senses for the B5 sampled audit.

Plan 5.5 asks for 50 random entries per 1,000 shipped, read properly, so the
error rate is measured rather than assumed. This picks the sample and writes it
as JSON for the audit sheet; it makes no judgement of its own.

The seed is fixed so the same sample can be redrawn and re-read later, and the
draw is stratified across the shard that produced each sense, so no one round of
authoring can dominate a sample by having been large.

Usage:
    python3 tools/audit_sample.py --n 50 --out data/policy/audit-001.json
"""

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load_shard_map():
    """sense id -> the source that gave it a charge.

    Inherited adverbs must be attributed to `adverbs-inherited`, not folded into
    the fallback. Audit 001 was first read with them defaulting to batch-0001,
    which produced a false finding: it looked as though the hand-written pilot
    scored as badly as the model-authored shards, when in fact the pilot was
    never sampled at all. An adverb's note is its adjective's note, so it tests
    the same authorship a second time and must be labelled that way.
    """
    owner = {}
    for path in sorted((ROOT / "data/families").glob("annotated-*.json")):
        shard = path.stem
        data = json.loads(path.read_text(encoding="utf-8"))
        for family in data["families"]:
            for member in family["members"]:
                word = member["word"].replace(" ", "_")
                owner[f"{word}.{member['synset']}"] = (shard, family["id"])

    adverbs = ROOT / "data/entries/overlays/adverbs-001.overlay.jsonl"
    if adverbs.exists():
        with open(adverbs, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                entry = json.loads(line)
                for sense_id in (entry.get("senses") or {}):
                    owner.setdefault(sense_id, ("adverbs-inherited", None))
    return owner


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--batch", type=Path, default=ROOT / "data/entries/batch-0001.jsonl")
    ap.add_argument("--n", type=int, default=50)
    ap.add_argument("--seed", type=int, default=20260830)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--exclude", type=Path, action="append", default=[],
                    help="an earlier audit-NNN.json whose senses to leave out, so a "
                         "second reading is fresh material rather than a re-mark")
    args = ap.parse_args()

    owner = load_shard_map()

    seen_before = set()
    for path in args.exclude:
        for entry in json.loads(path.read_text(encoding="utf-8"))["entries"]:
            seen_before.add(entry["id"])

    # Only senses that actually make a connotation claim can be wrong in the way
    # the audit is looking for. A neutral sense with no tone note asserts nothing.
    pool = []
    with open(args.batch, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            for sense in entry.get("senses", []):
                conn = sense.get("connotation") or {}
                if not conn.get("tone"):
                    continue
                if sense["id"] in seen_before:
                    continue
                shard, family = owner.get(sense["id"], ("batch-0001", None))
                pool.append({
                    "id": sense["id"],
                    "word": entry["word"],
                    "definition": sense.get("definition", ""),
                    "part_of_speech": sense.get("part_of_speech", ""),
                    "label": conn.get("label", ""),
                    "score": conn.get("score"),
                    "tone": conn["tone"],
                    "usage_labels": conn.get("usage_labels", []),
                    "examples": sense.get("examples", [])[:2],
                    "family": (sense.get("family") or {}).get("id") or family,
                    "charge": (sense.get("family") or {}).get("charge"),
                    "shard": shard,
                })

    by_shard = defaultdict(list)
    for item in pool:
        by_shard[item["shard"]].append(item)

    rng = random.Random(args.seed)
    shards = sorted(by_shard)
    # Proportional allocation, then top up the remainder from the largest shards
    # so the sample size is exact.
    picks = []
    for shard in shards:
        take = round(args.n * len(by_shard[shard]) / len(pool))
        picks.extend(rng.sample(by_shard[shard], min(take, len(by_shard[shard]))))
    picked_ids = {p["id"] for p in picks}
    rest = [x for x in pool if x["id"] not in picked_ids]
    rng.shuffle(rest)
    while len(picks) < args.n and rest:
        picks.append(rest.pop())
    picks = picks[:args.n]
    rng.shuffle(picks)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({
            "sample": args.out.stem,
            "seed": args.seed,
            "population": len(pool),
            "drawn": len(picks),
            "by_shard": {s: len(v) for s, v in sorted(by_shard.items())},
            "entries": picks,
        }, fh, ensure_ascii=False, indent=1)

    print(f"population: {len(pool)} senses carrying a connotation claim")
    for shard in shards:
        n = sum(1 for p in picks if p["shard"] == shard)
        print(f"  {shard:16s} {len(by_shard[shard]):5d} in pool -> {n:2d} sampled")
    print(f"drew {len(picks)} (seed {args.seed}) -> {args.out}")


if __name__ == "__main__":
    main()
