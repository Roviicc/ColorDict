#!/usr/bin/env python3
"""Collect census reader verdicts into one results file and a repair worksheet.

The census (plan 11.69) reads every unaudited sense carrying a connotation
claim. Sixteen readers each judge one slice against the rubric; this merges
their output files, tallies verdicts per shard and fault class, and groups
every failure by synset - because audit 004's rule is that a repair made for
one word is made for every word in its synset, the repair pass works
synset-by-synset, not read-by-read.

Usage:
    python3 tools/census_aggregate.py --dir data/policy/census-001-reads \
        --census data/policy/census-001.json \
        --out data/policy/census-001-results.json
"""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--dir", type=Path, required=True)
    ap.add_argument("--census", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    census = json.loads(args.census.read_text(encoding="utf-8"))
    meta = {e["id"]: e for e in census["entries"]}

    reads = {}
    missing_inputs = []
    for inp in sorted(args.dir.glob("input-*.json")):
        out = args.dir / inp.name.replace("input-", "output-")
        if not out.exists():
            missing_inputs.append(inp.name)
            continue
        data = json.loads(out.read_text(encoding="utf-8"))
        for r in data["reads"]:
            reads[r["id"]] = r

    unread = [i for i in meta if i not in reads]
    verdicts = Counter(r["verdict"] for r in reads.values())
    faults = Counter(r.get("fault", "?") for r in reads.values()
                     if r["verdict"] == "no")
    by_shard = defaultdict(Counter)
    for sid, r in reads.items():
        shard = meta.get(sid, {}).get("shard", "?")
        by_shard[shard][{"ok": "ok", "no": "no", "hm": "hm"}[r["verdict"]]] += 1

    # Failures grouped by synset so repairs land synset-wide.
    by_synset = defaultdict(list)
    for sid, r in reads.items():
        if r["verdict"] == "ok":
            continue
        synset = sid.rsplit(".", 1)[-1]
        m = meta.get(sid, {})
        by_synset[synset].append({
            "id": sid, "word": m.get("word"), "shard": m.get("shard"),
            "definition": m.get("definition"), "tone": m.get("tone"),
            "charge": m.get("charge"), "verdict": r["verdict"],
            "fault": r.get("fault"), "why": r.get("why"),
            "repair": r.get("repair"),
        })

    read_n = len(reads)
    wrong = verdicts.get("no", 0)
    results = {
        "sample": "census-001",
        "read": read_n,
        "right": verdicts.get("ok", 0),
        "wrong": wrong,
        "unsure": verdicts.get("hm", 0),
        "error_rate_pct": round(100 * wrong / read_n, 1) if read_n else None,
        "threshold_pct": 5.0,
        "note": "census repair pass, not a measurement - audit 005 measures",
        "unread": unread,
        "missing_outputs": missing_inputs,
        "faults": dict(faults.most_common()),
        "by_shard": {s: dict(c) for s, c in sorted(by_shard.items())},
    }
    args.out.write_text(json.dumps(results, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8", newline="\n")

    worksheet = args.out.with_name("census-001-repairs.json")
    worksheet.write_text(json.dumps(
        {"synsets": dict(sorted(by_synset.items()))},
        ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")

    print(f"read {read_n}/{len(meta)}  ok {results['right']}  "
          f"no {wrong}  hm {results['unsure']}  "
          f"rate {results['error_rate_pct']}%")
    for name in missing_inputs:
        print(f"  MISSING output for {name}")
    for fault, n in faults.most_common():
        print(f"  {fault:14s} {n}")
    print(f"failures span {len(by_synset)} synsets -> {worksheet.name}")


if __name__ == "__main__":
    main()
