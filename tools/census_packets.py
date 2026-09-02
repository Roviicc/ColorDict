#!/usr/bin/env python3
"""Split a census population into numbered reader packets, and keep them.

This is the half of the reading loop that did not exist. `census2_aggregate.py`
consumes `verdicts-NN.json`, but nothing produced the inputs those verdicts
answer: census 001's packets survive as data with no tool behind them, and
census 002's were never written down at all. The consequence is that census 002
- the measurement the whole method rests on - is reproducible only from its own
results file, never from its inputs.

A packet carries exactly what a blind reader may see: the sense id, the word,
the gloss it is printed under, its part of speech, the charge and label, the
tone note, and the usage labels and examples that ride with it. It carries
nothing about how the sense was scored before, which shard authored it, or what
any previous pass decided - that is the whole point, and it is enforced here by
what gets written rather than by asking the reader not to look.

Usage:
    python3 tools/census_packets.py --census data/policy/census-003.json \
        --packets 2 --out data/policy/census-003-reads
"""

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# What a reader is allowed to see. Anything not on this list is withheld, so a
# field added to the population later cannot leak into a packet by accident.
VISIBLE = ("id", "word", "definition", "part_of_speech", "label", "score",
           "tone", "usage_labels", "examples")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--census", type=Path, required=True,
                    help="population file with an 'entries' list")
    ap.add_argument("--packets", type=int, default=16,
                    help="how many readers to split across")
    ap.add_argument("--out", type=Path, required=True,
                    help="directory to write input-NN.json into (kept, not scratch)")
    args = ap.parse_args()

    census = json.loads(args.census.read_text(encoding="utf-8"))
    entries = census["entries"]
    if not entries:
        raise SystemExit(f"{args.census}: no entries to packet")

    packets = max(1, min(args.packets, len(entries)))
    args.out.mkdir(parents=True, exist_ok=True)

    # Contiguous slices, not round-robin: a reader who gets one family's senses
    # together can weigh them against each other, which is the comparison the
    # family stage exists to support.
    per = -(-len(entries) // packets)
    written = []
    for i in range(packets):
        chunk = entries[i * per:(i + 1) * per]
        if not chunk:
            continue
        path = args.out / f"input-{i + 1:02d}.json"
        path.write_text(json.dumps({
            "packet": i + 1,
            "count": len(chunk),
            "entries": [{k: e[k] for k in VISIBLE if k in e} for e in chunk],
        }, indent=1, ensure_ascii=False), encoding="utf-8")
        written.append((path.name, len(chunk)))

    withheld = sorted({k for e in entries for k in e} - set(VISIBLE))
    print(f"{len(entries)} senses -> {len(written)} packets in {args.out}")
    for name, n in written:
        print(f"  {name}  {n} senses")
    if withheld:
        print(f"withheld from readers: {', '.join(withheld)}")


if __name__ == "__main__":
    main()
