#!/usr/bin/env python3
"""Stage 10: ship a round's overlay once its blind reads pass.

A round's overlay waits in its run directory (parked.overlay.jsonl) while it is
unread, so nothing unread reaches the batch. After the entry read and the null
audit, this copies it into data/entries/overlays/ with the one change the audit
forces: a sense the null auditor found NOT connotation-free loses the Enricher's
"label": "neutral", so it no longer ships claiming to be plain. It keeps the
learner line and examples the entry reader passed.

Those senses and the Enricher's own candidates are listed in candidates.json in
the run directory: the join (stage 10 step 5) routes them to the family path,
where a family author writes the connotation and a census reader reads it.

Usage:
    python3 tools/stage10_ship.py --run data/policy/stage10-r1
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402


def verdicts(folder):
    for p in sorted(folder.glob("verdicts-*.json")):
        v = json.loads(p.read_text(encoding="utf-8"))
        items = v.get("verdicts", v)
        yield from (items.values() if isinstance(items, dict) else items)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--run", type=Path, required=True)
    args = ap.parse_args()
    run = args.run

    false_nulls = {f"{slug(x['word'])}.{x['synset']}": x for x in verdicts(run / "null-reads")
                   if x.get("verdict") == "null-wrong"}
    results = json.loads((run / "results.json").read_text(encoding="utf-8"))
    candidates = []
    for e in results["entries"]:
        if not e.get("accepted"):
            continue
        for syn, w in e["senses"].items():
            if isinstance(w.get("connotation"), dict):
                candidates.append({"sense_id": f"{slug(e['word'])}.{syn}", "word": e["word"],
                                   "pos": e["pos"], "synset": syn, "source": "enricher",
                                   "why": w["connotation"].get("why")})
    for sid, x in false_nulls.items():
        candidates.append({"sense_id": sid, "word": x["word"], "pos": x.get("pos"),
                           "synset": x["synset"], "source": "null audit", "why": x.get("why")})

    out_lines, relabelled = [], 0
    for line in (run / "parked.overlay.jsonl").open(encoding="utf-8"):
        if not line.strip():
            continue
        rec = json.loads(line)
        for sid, patch in rec["senses"].items():
            if sid in false_nulls and patch.get("label") == "neutral":
                del patch["label"]
                relabelled += 1
        out_lines.append(json.dumps(rec, ensure_ascii=False, separators=(",", ":")))
    if relabelled != len(false_nulls):
        sys.exit(f"{len(false_nulls)} false nulls but {relabelled} neutral labels found")
    dest = ROOT / "data/entries/overlays" / f"{run.name}.overlay.jsonl"
    dest.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    (run / "candidates.json").write_text(json.dumps(candidates, indent=1, ensure_ascii=False)
                                         + "\n", encoding="utf-8")
    print(f"shipped {len(out_lines)} entries -> {dest.relative_to(ROOT)}; "
          f"{relabelled} false nulls unlabelled; {len(candidates)} candidates -> "
          f"{run / 'candidates.json'}")


if __name__ == "__main__":
    main()
