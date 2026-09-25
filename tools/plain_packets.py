#!/usr/bin/env python3
"""Stage 9b, the plain-words pass: packets out, decisions checked, rewrites read.

Three steps, each a subcommand, so the pass can run unattended and every hand
still reads a file rather than a manager's retyping of one.

  draw    Every family with a note plain_lint.py flags becomes part of a packet
          for one simplifier agent. The family travels whole - a note that
          names *far* cannot be rewritten without *far*'s own note beside it -
          but only the flagged members are marked `rewrite`, with the rule and
          the detail that flagged them. The gloss is the full definition from
          batch-0001.jsonl, not the shard's `_gloss`, which worksheets cut to 90
          characters (family_packets.py records what that cost census 012).

  check   Each simplifier writes decisions-NN.json beside its packet-NN.json.
          This refuses the pass if any packet is unanswered, any decision is not
          `tone` or `keep`, any decision carries a `charge` (the simplifier has
          no licence to move one), any rewrite runs over 24 words, or any id was
          not asked for. It writes decisions.json in the shape census_apply.py
          applies.

  census  After census_apply -> family_apply -> dict_pipeline, the rewrites are
          read blind like any other note. The population is every `tone`
          decision, read from batch-0001.jsonl - what ships, not what the
          decisions file says should ship - and the command fails if the two
          disagree, because that means the apply did not land. Entries are
          sorted by family, as census 012 was, and census_packets.py slices
          them into reader packets.

Usage:
    python3 tools/plain_packets.py draw   --out data/policy/plain-001 [--per-packet 8]
    python3 tools/plain_packets.py check  --dir data/policy/plain-001
    python3 tools/plain_packets.py census --dir data/policy/plain-001 \
        --sample census-013 --out data/policy/census-013.json
"""

import argparse
import datetime
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402  the one rule that mints a sense id
import plain_lint  # noqa: E402

SHARDS = ROOT / "data/families"
BATCH = ROOT / "data/entries/batch-0001.jsonl"


def load_batch():
    senses = {}
    with BATCH.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            for sense in entry.get("senses") or []:
                senses[sense["id"]] = (entry["word"], sense)
    return senses


def draw(args):
    batch = load_batch()
    families, rewrite, by_rule, short_gloss = [], 0, Counter(), 0
    for path in sorted(SHARDS.glob("annotated-*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for fam in data["families"]:
            members, marked = [], 0
            for m in fam["members"]:
                if m.get("_skip") or not m.get("tone"):
                    continue
                sid = f"{slug(m['word'])}.{m['synset']}"
                found = batch.get(sid)
                gloss = found[1].get("definition") if found else None
                if not gloss:
                    gloss = m.get("_gloss", "")
                    short_gloss += len(gloss) >= 90
                hits = plain_lint.check(m["tone"], m["word"])
                row = {"id": sid, "word": m["word"], "gloss": gloss,
                       "charge": m.get("charge"), "tone": m["tone"],
                       "rewrite": bool(hits)}
                if hits:
                    row["flagged_for"] = dict(hits)
                    marked += 1
                    by_rule.update(rule for rule, _ in hits)
                members.append(row)
            if marked:
                # No shard name: simplifier.md forbids looking up where a note
                # came from, and a packet should not hand over the address.
                families.append({"family": fam["id"], "axis": fam.get("axis", ""),
                                 "members": members})
                rewrite += marked
    if args.limit:
        families = families[:args.limit]
        rewrite = sum(r["rewrite"] for f in families for r in f["members"])

    args.out.mkdir(parents=True, exist_ok=True)
    if any(args.out.glob("packet-*.json")):
        sys.exit(f"{args.out} already holds packets - a draw is kept, not redrawn")
    packets = [families[i:i + args.per_packet]
               for i in range(0, len(families), args.per_packet)]
    rule = {"max_words": plain_lint.MAX_WORDS, "rare_zipf": plain_lint.RARE_ZIPF,
            "rare_limit": plain_lint.RARE_LIMIT, "grammar": list(plain_lint.GRAMMAR)}
    index = []
    for n, fams in enumerate(packets, 1):
        body = {"packet": n, "families": fams}
        (args.out / f"packet-{n:02d}.json").write_text(
            json.dumps(body, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        index.append({"packet": n, "families": [f["family"] for f in fams],
                      "rewrite": [r["id"] for f in fams for r in f["members"]
                                  if r["rewrite"]]})
    manifest = {"pass": args.out.name, "drawn": str(datetime.date.today()),
                "instrument": ".claude/agents/simplifier.md", "rule": rule,
                "families": len(families), "rewrite": rewrite,
                "by_rule": dict(by_rule), "packets": index,
                "wordfreq": plain_lint.zipf_frequency is not None,
                "gloss_from_shard_possibly_cut": short_gloss}
    (args.out / "manifest.json").write_text(
        json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(families)} families, {rewrite} notes to rewrite, "
          f"{len(packets)} packets of <= {args.per_packet} families -> {args.out}")
    print(f"flagged by: {dict(by_rule)}")
    if short_gloss:
        print(f"WARNING: {short_gloss} gloss(es) fell back to a shard _gloss of 90+ "
              "characters and may be cut")
    if plain_lint.zipf_frequency is None:
        print("WARNING: wordfreq missing - hard-words did not run, so this draw is short")


def check(args):
    manifest = json.loads((args.dir / "manifest.json").read_text(encoding="utf-8"))
    errors, warnings, merged = [], [], {}
    actions = Counter()
    for p in manifest["packets"]:
        n = p["packet"]
        packet = json.loads((args.dir / f"packet-{n:02d}.json").read_text(encoding="utf-8"))
        word_of = {r["id"]: r["word"] for f in packet["families"] for r in f["members"]}
        out = args.dir / f"decisions-{n:02d}.json"
        if not out.is_file():
            errors.append(f"packet {n:02d}: no {out.name}")
            continue
        try:
            got = json.loads(out.read_text(encoding="utf-8"))
        except ValueError as e:
            errors.append(f"packet {n:02d}: {out.name} is not JSON ({e})")
            continue
        asked = p["rewrite"]
        for sid in asked:
            if sid not in got:
                errors.append(f"packet {n:02d}: {sid} was asked for and not answered")
        for sid, d in got.items():
            if sid not in asked:
                errors.append(f"packet {n:02d}: {sid} was not asked for")
                continue
            action = (d or {}).get("action")
            actions[action] += 1
            if "charge" in d:
                errors.append(f"{sid}: carries a charge - the simplifier may not move one")
            if action == "keep":
                if not d.get("reasoning"):
                    errors.append(f"{sid}: keep without a reason")
            elif action == "tone":
                tone = (d.get("tone") or "").strip()
                if not tone or "\n" in tone:
                    errors.append(f"{sid}: empty or multi-line tone")
                    continue
                for rule, detail in plain_lint.check(tone, word_of.get(sid, "")):
                    (errors if rule == "too-long" else warnings).append(
                        f"{sid}: {rule} ({detail}): {tone}")
            else:
                errors.append(f"{sid}: action {action!r} is not tone or keep")
                continue
            merged[sid] = d
    print(f"decisions: {dict(actions)} across {len(manifest['packets'])} packets")
    for w in warnings:
        print(f"  warn  {w}")
    for e in errors:
        print(f"  ERROR {e}")
    if errors:
        sys.exit(f"{len(errors)} error(s) - decisions.json not written")
    target = args.dir / "decisions.json"
    target.write_text(json.dumps(merged, indent=1, ensure_ascii=False) + "\n",
                      encoding="utf-8")
    keeps = actions["keep"]
    print(f"{len(merged)} decisions -> {target} ({keeps} keep"
          f"{'' if keeps == 1 else 's'}, {len(warnings)} lint warning(s))")
    if manifest["rewrite"] and keeps > manifest["rewrite"] // 3:
        print("NOTE: the simplifier kept over a third of its packet - read the "
              "reasons before applying; simplifier.md says that is a claim about the rule")


def census(args):
    manifest = json.loads((args.dir / "manifest.json").read_text(encoding="utf-8"))
    decisions = json.loads((args.dir / "decisions.json").read_text(encoding="utf-8"))
    batch = load_batch()
    family_of = {}
    for p in manifest["packets"]:
        packet = json.loads((args.dir / f"packet-{p['packet']:02d}.json").read_text(
            encoding="utf-8"))
        for f in packet["families"]:
            for r in f["members"]:
                family_of[r["id"]] = f["family"]
    entries, missing, stale = [], [], []
    for sid, d in decisions.items():
        if d.get("action") != "tone":
            continue
        found = batch.get(sid)
        if not found:
            missing.append(sid)
            continue
        word, sense = found
        conn = sense.get("connotation") or {}
        if (conn.get("tone") or "").strip() != d["tone"].strip():
            stale.append(sid)
            continue
        entries.append({"id": sid, "word": word, "definition": sense.get("definition"),
                        "part_of_speech": sense.get("part_of_speech"),
                        "label": conn.get("label"), "score": None,
                        "tone": conn.get("tone"),
                        "usage_labels": conn.get("usage_labels") or [],
                        "examples": sense.get("examples") or [],
                        "_family": family_of.get(sid)})
    if missing or stale:
        for sid in missing:
            print(f"  ERROR {sid}: not in {BATCH.name}")
        for sid in stale:
            print(f"  ERROR {sid}: ships a different tone than its decision - "
                  "run census_apply, family_apply and dict_pipeline --no-build first")
        sys.exit(f"{len(missing) + len(stale)} rewrite(s) not in the shipped batch")
    entries.sort(key=lambda e: (e["_family"] or "", e["id"]))
    body = {
        "sample": args.sample,
        "pre_registered": str(datetime.date.today()),
        "status": "population drawn; read not started",
        "why": ("Stage 9b rewrote notes for plain words. A rewrite is a new note "
                "and carries the same risk as the one it replaced, so every one is "
                "read blind, as repairs are."),
        "instruments": {"author": ".claude/agents/simplifier.md",
                        "reader": ".claude/agents/census-reader.md",
                        "repairer": ".claude/agents/repairer.md",
                        "rule": ("census-reader.md unchanged from censuses 007-012: "
                                 "the reader measures truth, not ease, so this rate "
                                 "compares with theirs; the instrument gate must "
                                 "pass before grading")},
        "stop_condition": ("over 5% stops the pass: it means simplifying is "
                           "changing meaning, a method problem before a batch one"),
        "read_design": ("complete read of every rewrite; census_packets.py slices "
                        "contiguously; reader sees gloss, charge label and note, "
                        "never the old note or that it was rewritten"),
        "scope": f"every tone decision in {args.dir.name}/decisions.json",
        "population": len(entries),
        "drawn": len(entries),
        "entries": entries,
        "read_order": "sorted by family so a packet carries a family's rewrites together",
    }
    args.out.write_text(json.dumps(body, indent=1, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    print(f"{len(entries)} rewrites -> {args.out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("draw")
    d.add_argument("--out", type=Path, required=True)
    d.add_argument("--per-packet", type=int, default=8, help="families per packet")
    d.add_argument("--limit", type=int, default=0, help="first N families only (pilot)")
    c = sub.add_parser("check")
    c.add_argument("--dir", type=Path, required=True)
    s = sub.add_parser("census")
    s.add_argument("--dir", type=Path, required=True)
    s.add_argument("--sample", required=True)
    s.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    {"draw": draw, "check": check, "census": census}[args.cmd](args)


if __name__ == "__main__":
    main()
