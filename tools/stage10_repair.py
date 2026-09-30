#!/usr/bin/env python3
"""Stage 10: the third-hand repair of an entry read's faults, as files.

The entry reader names faulted senses; a fresh enricher rewrites exactly
those; a fresh reader re-reads exactly those. Three steps, each writing the
files the stage-7 tools already expect, so nothing here is a new gate:

  cut     reader-reads/*.json -> repair-packets/input-01.json: each faulted
          entry as the Enricher saw it (enricher-packets), with only the
          faulted senses marked write: true. One packet, one fresh enricher.
  land    repair-out/output-01.json -> repair-out/repairs.json (the flat shape
          enrich_repair_apply.py lands), then enrich_repair_apply.py.
          Re-run enrich_validate.py enricher after this.
  reread  a reader packet for the repaired entries only, cut from a scratch
          batch, into repair-read-packets/input-01.json. The original reader
          packets are kept as they were read.

Usage:
    python3 tools/stage10_repair.py cut    --run data/policy/stage10-r5
    python3 tools/stage10_repair.py land   --run data/policy/stage10-r5
    python3 tools/stage10_repair.py reread --run data/policy/stage10-r5 --batch <scratch batch>
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def save(p, data):
    Path(p).parent.mkdir(parents=True, exist_ok=True)
    Path(p).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def faults(run):
    """(word, pos) -> [synset] the entry read marked wrong."""
    out = {}
    for p in sorted((run / "reader-reads").glob("verdicts-*.json")):
        for e in load(p).get("verdicts", []):
            for s in e.get("senses") or []:
                if s.get("verdict") == "wrong":
                    out.setdefault((e["word"], e["pos"]), []).append(s["synset"])
    return out


def cmd_cut(run):
    wrong = faults(run)
    entries = []
    for p in sorted((run / "enricher-packets").glob("input-*.json")):
        for e in load(p)["entries"]:
            syns = wrong.get((e["word"], e["pos"]))
            if not syns:
                continue
            e = dict(e)
            e["senses"] = [dict(s, write=s["synset"] in syns) for s in e["senses"]]
            entries.append(e)
    if not entries:
        sys.exit("no faulted senses to repair")
    save(run / "repair-packets" / "input-01.json", {"packet": 1, "entries": entries})
    n = sum(len(v) for v in wrong.values())
    print(f"repair packet: {len(entries)} entries, {n} senses -> {run / 'repair-packets/input-01.json'}")


def cmd_land(run):
    out = load(run / "repair-out" / "output-01.json")
    senses = []
    for e in out["entries"]:
        for syn, body in e["senses"].items():
            senses.append({"word": e["word"], "pos": e["pos"], "synset": syn,
                           **{k: body[k] for k in ("learner", "examples", "usage_labels",
                                                   "connotation")}})
    save(run / "repair-out" / "repairs.json", {"senses": senses})
    subprocess.run([sys.executable, str(ROOT / "tools/enrich_repair_apply.py"), "--out", str(run),
                    "--file", str(run / "repair-out" / "repairs.json")], check=True)


def cmd_reread(run, batch):
    applied = load(run / "repair-applied.json")
    words = {(s["word"], s["pos"]) for s in applied["senses"]}
    words |= {(r["word"], r["pos"]) for r in applied.get("rankings", [])}
    scratch = run / "repair-read-scratch"
    subprocess.run([sys.executable, str(ROOT / "tools/enrich_packets.py"), "reader",
                    "--out", str(run), "--batch", str(batch)], check=True, capture_output=True)
    # enrich_packets writes reader-packets in place; move the fresh set aside
    # and restore the packets the readers actually read from git.
    entries = []
    for p in sorted((run / "reader-packets").glob("input-*.json")):
        entries += [e for e in load(p)["entries"] if (e["word"], e["pos"]) in words]
    subprocess.run(["git", "checkout", "--", str(run / "reader-packets")], cwd=ROOT, check=True)
    save(run / "repair-read-packets" / "input-01.json", {"packet": 1, "entries": entries})
    print(f"re-read packet: {len(entries)} entries -> {run / 'repair-read-packets/input-01.json'}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("cut", "land", "reread"):
        s = sub.add_parser(name)
        s.add_argument("--run", type=Path, required=True)
        if name == "reread":
            s.add_argument("--batch", type=Path, required=True)
    args = ap.parse_args()
    if args.cmd == "cut":
        cmd_cut(args.run)
    elif args.cmd == "land":
        cmd_land(args.run)
    else:
        cmd_reread(args.run, args.batch)


if __name__ == "__main__":
    main()
