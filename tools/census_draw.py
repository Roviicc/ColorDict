#!/usr/bin/env python3
"""Draw a complete census population from one tick's annotated shard(s).

Censuses 007-012 read every sense a tick authored, drawn complete rather than
sampled, and each population was assembled by hand. That is fine with a person
at the keyboard and not at 3 a.m.: this does the same draw as a tool, so an
unattended tick cannot drift in what it hands the reader.

The entries are read from batch-0001.jsonl - what ships, not what the shard says
should ship - and the draw fails if a note in the shard has not reached the
batch, because that means family_apply or dict_pipeline did not run. Entries are
sorted by family, as census 012 was, so census_packets.py (which slices
contiguously) keeps a family's siblings in one packet where it can.

Usage:
    python3 tools/census_draw.py --shard data/families/annotated-019.json \
        --sample census-014 --out data/policy/census-014.json
"""

import argparse
import datetime
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402  the one rule that mints a sense id

BATCH = ROOT / "data/entries/batch-0001.jsonl"


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--shard", type=Path, action="append", required=True)
    ap.add_argument("--sample", required=True, help="e.g. census-014")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    shipped = {}
    with BATCH.open(encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                entry = json.loads(line)
                for sense in entry.get("senses") or []:
                    shipped[sense["id"]] = (entry["word"], sense)

    entries, errors = [], []
    for shard in args.shard:
        for fam in json.loads(shard.read_text(encoding="utf-8"))["families"]:
            for m in fam["members"]:
                if m.get("_skip") or not m.get("tone"):
                    continue
                sid = f"{slug(m['word'])}.{m['synset']}"
                found = shipped.get(sid)
                if not found:
                    errors.append(f"{sid}: not in {BATCH.name}")
                    continue
                word, sense = found
                conn = sense.get("connotation") or {}
                if (conn.get("tone") or "").strip() != m["tone"].strip():
                    errors.append(f"{sid}: ships a different note than {shard.name}")
                    continue
                entries.append({"id": sid, "word": word,
                                "definition": sense.get("definition"),
                                "part_of_speech": sense.get("part_of_speech"),
                                "label": conn.get("label"), "score": None,
                                "tone": conn.get("tone"),
                                "usage_labels": conn.get("usage_labels") or [],
                                "examples": sense.get("examples") or [],
                                "_family": fam["id"], "_shard": shard.name})
    for e in errors:
        print(f"  ERROR {e}")
    if errors:
        sys.exit(f"{len(errors)} note(s) have not reached the batch - run family_apply "
                 "and dict_pipeline --no-build first")

    entries.sort(key=lambda e: (e["_family"], e["id"]))
    body = {"sample": args.sample, "pre_registered": str(datetime.date.today()),
            "status": "population drawn; read not started",
            "scope": ("every sense carrying a note in "
                      + ", ".join(s.name for s in args.shard)
                      + ", drawn complete rather than sampled, as 007-012 were"),
            "reading": "rubric verbatim from census-reader.md",
            "stop_condition": "over 5% stops the run",
            "population": len(entries), "drawn": len(entries), "entries": entries,
            "read_order": "sorted by family"}
    args.out.write_text(json.dumps(body, indent=1, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    print(f"{len(entries)} senses -> {args.out}")


if __name__ == "__main__":
    main()
