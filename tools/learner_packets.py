#!/usr/bin/env python3
"""Stage 9b: can a learner read the rewrites? Packets for learner-reader.md.

The census reader asks whether a note is true; this asks whether a learner can
read it. Two subcommands:

  draw       Every `tone` decision in a plain-words pass, read from what ships
             (batch-0001.jsonl), plus unlabelled controls the reader cannot
             tell apart from the rewrites: notes plain_lint flags for
             hard-words or grammar, which should read hard, and short notes it
             never flagged, which should read easy. The packets carry only id,
             word, gloss and note - learner-reader.md is told nothing else, and
             the key saying which is which stays beside the packets in key.json.

  aggregate  The easy rate over the rewrites alone, and the controls scored
             apart. A pass counts only if the reader got its controls right: a
             reader that calls everything easy would pass any rewrite.

Usage:
    python3 tools/learner_packets.py draw --dir data/policy/plain-001 \
        --out data/policy/learner-001 [--packets 4] [--controls 4]
    python3 tools/learner_packets.py aggregate --dir data/policy/learner-001
"""

import argparse
import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import plain_lint  # noqa: E402
from plain_packets import load_batch  # noqa: E402

PASS_EASY = 0.9
EVERYDAY_ZIPF = 4.0  # an easy control uses only words met every week
# School words learner-reader.md counts as everyday. plain_lint's zipf test
# flags some of them (*adverb*), so a hard control needs two rare words
# besides these - learner-001's first draw used three that did not (the
# reader rightly read them easy).
SCHOOL = {"adverb", "adverbs", "noun", "nouns", "verb", "verbs", "adjective",
          "adjectives", "spelling", "metaphor", "literal", "formal", "informal",
          "slang", "british"}


def hard_enough(hits):
    if "grammar" in hits:
        return True
    rare = [w.strip() for w in hits.get("hard-words", "").split(",")]
    return len([w for w in rare if w and w.lower() not in SCHOOL]) >= 2


def everyday(note, word):
    """True when every word but the headword is common - a control meant to
    read easy must not be one a fair reader could call hard."""
    if plain_lint.zipf_frequency is None:
        return False
    stem = word.lower()[:5]
    for w in re.findall(r"[a-z]+", note.lower().replace("*", "")):
        if w.startswith(stem) or len(w) <= 2:
            continue
        if plain_lint.zipf_frequency(w, "en") < EVERYDAY_ZIPF:
            return False
    return True


def draw(args):
    decisions = json.loads((args.dir / "decisions.json").read_text(encoding="utf-8"))
    batch = load_batch()
    rows, key = [], {}
    for sid, d in ({} if args.controls_only else decisions).items():
        if d.get("action") != "tone":
            continue
        word, sense = batch[sid]
        tone = ((sense.get("connotation") or {}).get("tone") or "").strip()
        if tone != d["tone"].strip():
            sys.exit(f"{sid}: ships a different tone than its decision - apply first")
        rows.append({"id": sid, "word": word, "gloss": sense.get("definition"), "note": tone})
        key[sid] = "rewrite"

    # Controls come from notes this pass did not touch, drawn with a fixed seed
    # so the draw can be repeated.
    rng = random.Random(args.seed)
    hard, easy = [], []
    if args.hard_from:
        # After a pass the shipped corpus has almost no hard notes left, so
        # hard controls come from the notes as they stood at an earlier commit.
        import subprocess
        raw = subprocess.run(["git", "show", f"{args.hard_from}:data/entries/batch-0001.jsonl"],
                             cwd=str(ROOT), capture_output=True, text=True, check=True).stdout
        old = {}
        for line in raw.splitlines():
            if line.strip():
                e = json.loads(line)
                for sn in e.get("senses") or []:
                    old[sn["id"]] = (e["word"], sn)
        for sid, (word, sense) in sorted(old.items()):
            tone = ((sense.get("connotation") or {}).get("tone") or "").strip()
            hits = dict(plain_lint.check(tone, word)) if tone else {}
            if tone and hard_enough(hits) and "too-long" not in hits:
                hard.append({"id": sid, "word": word, "gloss": sense.get("definition"), "note": tone})
    for sid, (word, sense) in sorted(batch.items()):
        if sid in key or sid in decisions:
            continue
        tone = ((sense.get("connotation") or {}).get("tone") or "").strip()
        if not tone:
            continue
        hits = dict(plain_lint.check(tone, word))
        row = {"id": sid, "word": word, "gloss": sense.get("definition"), "note": tone}
        if hard_enough(hits) and "too-long" not in hits:
            if not args.hard_from:
                hard.append(row)
        elif not hits and len(tone.split()) <= 14 and everyday(tone, word):
            easy.append(row)
    n = args.controls // 2
    for row in rng.sample(hard, n):
        rows.append(row)
        key[row["id"]] = "control-hard"
    for row in rng.sample(easy, args.controls - n):
        rows.append(row)
        key[row["id"]] = "control-easy"

    rng.shuffle(rows)
    args.out.mkdir(parents=True, exist_ok=True)
    if any(args.out.glob("input-*.json")):
        sys.exit(f"{args.out} already holds packets - a draw is kept, not redrawn")
    size = -(-len(rows) // args.packets)
    for i in range(args.packets):
        chunk = rows[i * size:(i + 1) * size]
        if chunk:
            (args.out / f"input-{i + 1:02d}.json").write_text(
                json.dumps({"packet": i + 1, "notes": chunk}, indent=1,
                           ensure_ascii=False) + "\n", encoding="utf-8")
    (args.out / "key.json").write_text(json.dumps(
        {"pass": args.dir.name, "instrument": ".claude/agents/learner-reader.md",
         "seed": args.seed, "key": key}, indent=1, ensure_ascii=False) + "\n",
        encoding="utf-8")
    counts = {k: list(key.values()).count(k) for k in ("rewrite", "control-hard", "control-easy")}
    print(f"{len(rows)} notes {counts} in {args.packets} packet(s) -> {args.out}")


def aggregate(args):
    key = json.loads((args.dir / "key.json").read_text(encoding="utf-8"))["key"]
    verdicts, errors = {}, []
    for path in sorted(args.dir.glob("input-*.json")):
        n = path.stem.split("-")[1]
        asked = [r["id"] for r in json.loads(path.read_text(encoding="utf-8"))["notes"]]
        out = args.dir / f"verdicts-{n}.json"
        if not out.is_file():
            errors.append(f"no {out.name}")
            continue
        got = json.loads(out.read_text(encoding="utf-8")).get("verdicts", {})
        for sid in asked:
            v = (got.get(sid) or {}).get("verdict")
            if v not in ("easy", "hard"):
                errors.append(f"{sid}: verdict {v!r}")
            else:
                verdicts[sid] = got[sid]
    rewrites = [s for s, k in key.items() if k == "rewrite" and s in verdicts]
    easy = sum(verdicts[s]["verdict"] == "easy" for s in rewrites)
    controls = {s: (k, verdicts[s]["verdict"]) for s, k in key.items()
                if k != "rewrite" and s in verdicts}
    controls_right = all(v == k.split("-")[1] for k, v in controls.values())
    rate = easy / len(rewrites) if rewrites else 0.0
    hard = {s: verdicts[s] for s in rewrites if verdicts[s]["verdict"] == "hard"}
    result = {"rewrites_read": len(rewrites), "easy": easy, "easy_rate": round(rate, 3),
              "threshold": PASS_EASY, "controls": controls,
              "controls_right": controls_right,
              "passes": rate >= PASS_EASY and controls_right and not errors,
              "hard": hard, "errors": errors}
    (args.dir / "results.json").write_text(
        json.dumps(result, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"rewrites easy {easy}/{len(rewrites)} = {rate:.1%} (pass at {PASS_EASY:.0%})")
    for s, (k, v) in controls.items():
        print(f"  {k:13s} {v:5s} {'ok' if v == k.split('-')[1] else 'WRONG'}  {s}")
    for e in errors:
        print(f"  ERROR {e}")
    print("PASS" if result["passes"] else "FAIL")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("draw")
    d.add_argument("--dir", type=Path, required=True, help="plain-words pass directory")
    d.add_argument("--out", type=Path, required=True)
    d.add_argument("--packets", type=int, default=1)
    d.add_argument("--controls", type=int, default=4)
    d.add_argument("--seed", type=int, default=2026)
    d.add_argument("--hard-from", help="git revision to draw hard controls from")
    d.add_argument("--controls-only", action="store_true",
                   help="controls alone: re-check the reader's calibration")
    a = sub.add_parser("aggregate")
    a.add_argument("--dir", type=Path, required=True)
    args = ap.parse_args()
    {"draw": draw, "aggregate": aggregate}[args.cmd](args)


if __name__ == "__main__":
    main()
