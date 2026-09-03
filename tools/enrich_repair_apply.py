#!/usr/bin/env python3
"""Stage 4: land a third hand's repairs into a run's agent outputs.

The reader names faults; a third agent - not the writer, not the reader -
rewrites the faulted senses and re-ranks the faulted entries. Its output is
one file. This lands it where the gates expect to find agent output, so the
same validators run again unchanged:

  senses     -> the matching enricher-out/output-NN.json sense is replaced
  rankings   -> the matching ranker-reads/verdicts-NN.json entry is replaced,
                and any newly written senses are added to enricher-out

After this, re-run in order: enrich_validate ranker, enrich_packets enricher,
then this script's --trim (drops written senses that fell out of the write
set when the order changed), then enrich_validate enricher and the pipeline.
The repaired items are listed in repair-applied.json so a blind re-read can
be cut for exactly them.

Usage:
    python3 tools/enrich_repair_apply.py --out data/policy/enrich-002
    python3 tools/enrich_repair_apply.py --out data/policy/enrich-002 --trim
    python3 tools/enrich_repair_apply.py --out data/policy/enrich-002 --file data/policy/enrich-002/repair-out/output-02.json
"""

import argparse
import json
import sys
from pathlib import Path


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def save(p, data):
    Path(p).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def find_entry(files, word, pos, key="entries"):
    for path, data in files.items():
        for e in data.get(key, []):
            if e.get("word") == word and e.get("pos") == pos:
                return path, data, e
    return None, None, None


def cmd_apply(out, repair_file):
    repairs = load(repair_file)
    enricher = {p: load(p) for p in sorted((out / "enricher-out").glob("output-*.json"))}
    ranker = {p: load(p) for p in sorted((out / "ranker-reads").glob("verdicts-*.json"))}
    applied = {"senses": [], "rankings": []}

    for s in repairs.get("senses", []):
        path, data, e = find_entry(enricher, s["word"], s["pos"])
        if e is None or s["synset"] not in e.get("senses", {}):
            sys.exit(f"repair names {s['word']}/{s['synset']} which no enricher output has")
        e["senses"][s["synset"]] = {k: s[k] for k in ("learner", "examples", "usage_labels", "connotation")}
        applied["senses"].append({"word": s["word"], "pos": s["pos"], "synset": s["synset"]})

    for r in repairs.get("rankings", []):
        path, data, e = find_entry(ranker, r["word"], r["pos"])
        if e is None:
            sys.exit(f"repair re-ranks {r['word']}/{r['pos']} which no ranker output has")
        if sorted(e["order"]) != sorted(r["order"]):
            sys.exit(f"repair order for {r['word']} is not a permutation of the original")
        e["order"], e["met"] = list(r["order"]), list(r["met"])
        epath, edata, ee = find_entry(enricher, r["word"], r["pos"])
        for syn, body in (r.get("new_senses") or {}).items():
            ee["senses"][syn] = {k: body[k] for k in ("learner", "examples", "usage_labels", "connotation")}
            applied["senses"].append({"word": r["word"], "pos": r["pos"], "synset": syn})
        applied["rankings"].append({"word": r["word"], "pos": r["pos"], "order": r["order"]})

    for p, d in enricher.items():
        save(p, d)
    for p, d in ranker.items():
        save(p, d)
    log = out / "repair-applied.json"
    if log.exists():
        prior = load(log)
        applied = {"senses": prior["senses"] + applied["senses"],
                   "rankings": prior["rankings"] + applied["rankings"]}
    save(log, applied)
    print(f"applied {len(applied['senses'])} sense repairs and {len(applied['rankings'])} re-rankings")


def cmd_trim(out):
    """Drop written senses no longer in the write set after a re-ranking."""
    packets = {p: load(p) for p in sorted((out / "enricher-packets").glob("input-*.json"))}
    want = {}
    for d in packets.values():
        for e in d["entries"]:
            want[(e["word"], e["pos"])] = {s["synset"] for s in e["senses"] if s["write"]}
    dropped = 0
    for p in sorted((out / "enricher-out").glob("output-*.json")):
        d = load(p)
        for e in d["entries"]:
            w = want.get((e["word"], e["pos"]), set())
            extra = set(e["senses"]) - w
            for syn in extra:
                del e["senses"][syn]
                dropped += 1
            missing = w - set(e["senses"])
            if missing:
                sys.exit(f"{e['word']}: write set needs {sorted(missing)} which nobody wrote")
        save(p, d)
    print(f"trimmed {dropped} senses that left the write set")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", required=True)
    ap.add_argument("--trim", action="store_true")
    ap.add_argument("--file", default=None, help="repair output to land (default repair-out/output-01.json)")
    args = ap.parse_args()
    out = Path(args.out)
    if args.trim:
        return cmd_trim(out)
    return cmd_apply(out, Path(args.file) if args.file else out / "repair-out" / "output-01.json")


if __name__ == "__main__":
    sys.exit(main())
