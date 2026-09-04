#!/usr/bin/env python3
"""Stage 4: the gates between the Ranker, the Enricher, the overlay and the read.

Three checks, each run on files that an agent wrote to disk:

  ranker    a ranking is accepted only if it is a permutation of the synset ids
            the packet offered - nothing added, nothing dropped, nothing twice -
            and every id it says the book attests is in that order. A bad
            ranking is rejected HERE, before the Enricher spends on it.
  enricher  a written sense is accepted only if the learner line is a short
            sentence that is not the gloss, every example uses the headword (the
            same test dict_validate.py applies to reviewed entries), the usage
            labels come from the fixed list, and the connotation verdict is
            either null or a candidate with a reason. An entry with one bad
            sense is rejected whole - partial enrichment would ship a mix that
            nobody read.
  reads     the blind reader's verdicts -> the measured defect rate, by fault
            class, for the stage record.
  nulls     (stage 5) the null auditor's verdicts -> the false-null rate.

Accepted entries become an overlay for dict_enrich_apply.py. The overlay
carries rank, learner, examples and usage labels, and sets the label to
neutral on senses the Enricher judged connotation-free, so the machine
SentiWordNet sign is dropped where a hand said there is nothing to sign.
It never carries charge or tone: that is the family path's.

Usage:
    python3 tools/enrich_validate.py ranker   --out data/policy/enrich-001
    python3 tools/enrich_validate.py enricher --out data/policy/enrich-001 \
        --overlay data/entries/overlays/enrich-001.overlay.jsonl
    python3 tools/enrich_validate.py reads    --out data/policy/enrich-001
"""

import argparse
import json
import sys

import instrument_gate
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import dict_validate as dv  # noqa: E402  - the example test and the label list

LEARNER_MIN_WORDS = 3
LEARNER_MAX_WORDS = 45
EXAMPLE_MAX_CHARS = 220
EXAMPLES_MAX = 2


def load_dir(path, prefix):
    out = {}
    for p in sorted(Path(path).glob(f"{prefix}-*.json")):
        try:
            out[p.name] = json.loads(p.read_text(encoding="utf-8"))
        except ValueError as exc:
            sys.exit(f"{p}: not JSON ({exc})")
    if not out:
        sys.exit(f"no {prefix}-*.json in {path}")
    return out


def pair(packets, outputs):
    """(packet, output) by packet number; a missing output is a missing read."""
    by_n = {}
    for data in outputs.values():
        by_n[data.get("packet")] = data
    for name, packet in packets.items():
        yield name, packet, by_n.get(packet["packet"])


# --------------------------------------------------------------------------
# ranker
# --------------------------------------------------------------------------

def cmd_ranker(args):
    out = Path(args.out)
    packets = load_dir(out / "ranker-packets", "input")
    outputs = load_dir(out / "ranker-reads", "verdicts")
    accepted, rejected = [], []
    for name, packet, result in pair(packets, outputs):
        got = {(e.get("word"), e.get("pos")): e for e in (result or {}).get("entries", [])}
        for entry in packet["entries"]:
            key = (entry["word"], entry["pos"])
            offered = [s["synset"] for s in entry["senses"]]
            r = got.get(key)
            why = None
            if r is None:
                why = "no ranking returned"
            else:
                order = r.get("order") or []
                met = r.get("met") or []
                if sorted(order) != sorted(offered):
                    extra = sorted(set(order) - set(offered))
                    missing = sorted(set(offered) - set(order))
                    dup = [s for s, n in Counter(order).items() if n > 1]
                    why = f"order is not a permutation: extra={extra} missing={missing} dup={dup}"
                elif not set(met) <= set(order):
                    why = f"met names ids outside the order: {sorted(set(met) - set(order))}"
            if why:
                rejected.append({"word": key[0], "pos": key[1], "why": why})
            else:
                accepted.append({"word": key[0], "pos": key[1],
                                 "order": list(order), "met": list(dict.fromkeys(met))})
    ranking = {"accepted": len(accepted), "rejected": rejected, "entries": accepted}
    (out / "ranking.json").write_text(json.dumps(ranking, ensure_ascii=False, indent=1),
                                      encoding="utf-8")
    print(f"ranker: {len(accepted)} accepted, {len(rejected)} rejected")
    for r in rejected:
        print(f"  REJECT {r['word']}/{r['pos']}: {r['why']}")
    return 0 if accepted else 1


# --------------------------------------------------------------------------
# enricher
# --------------------------------------------------------------------------

def bulk_inflections(words):
    wanted = {w.lower() for w in words}
    out = {}
    with (ROOT / "data/entries/derived-bulk.jsonl").open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            entry = json.loads(line)
            w = (entry.get("word") or "").lower()
            if w in wanted:
                out[w] = list(entry.get("inflections") or [])
    return out


def check_sense(word, inflections, gloss, s):
    """Return a fault string, or None when the written sense passes."""
    if not isinstance(s, dict):
        return "sense is not an object"
    learner = s.get("learner")
    if not isinstance(learner, str) or not learner.strip():
        return "learner missing"
    n = len(learner.split())
    if not (LEARNER_MIN_WORDS <= n <= LEARNER_MAX_WORDS):
        return f"learner is {n} words (want {LEARNER_MIN_WORDS}-{LEARNER_MAX_WORDS})"
    if " ".join(learner.lower().split()).rstrip(".") == " ".join(gloss.lower().split()).rstrip("."):
        return "learner is the gloss verbatim"
    examples = s.get("examples")
    if not isinstance(examples, list) or not examples:
        return "examples missing"
    if len(examples) > EXAMPLES_MAX:
        return f"{len(examples)} examples (max {EXAMPLES_MAX})"
    seen = set()
    for ex in examples:
        if not isinstance(ex, str) or not ex.strip():
            return "empty example"
        if len(ex) > EXAMPLE_MAX_CHARS:
            return f"example over {EXAMPLE_MAX_CHARS} chars"
        low = " ".join(ex.lower().split())
        if low in seen:
            return "repeated example"
        seen.add(low)
        if not dv.example_mentions(word, inflections, ex):
            return f"example does not use the headword: {ex!r}"
    labels = s.get("usage_labels", [])
    if labels is None:
        labels = []
    if not isinstance(labels, list):
        return "usage_labels is not a list"
    bad = [u for u in labels if u not in dv.USAGE_LABELS]
    if bad:
        return f"usage labels outside the fixed list: {bad}"
    conn = s.get("connotation", "MISSING")
    if conn == "MISSING":
        return "connotation verdict missing"
    if conn is not None:
        if not (isinstance(conn, dict) and conn.get("candidate") is True
                and isinstance(conn.get("why"), str) and conn["why"].strip()):
            return "connotation must be null or {candidate: true, why: ...}"
        if len(conn) != 2:
            return "connotation carries fields other than candidate and why"
    return None


def cmd_enricher(args):
    out = Path(args.out)
    packets = load_dir(out / "enricher-packets", "input")
    outputs = load_dir(out / "enricher-out", "output")
    selected = {(e["word"], e["pos"]): e
                for e in json.loads((out / "selected.json").read_text(encoding="utf-8"))}
    inflections = bulk_inflections(w for w, _ in selected)

    results, overlay_lines = [], []
    counts = Counter()
    for name, packet, result in pair(packets, outputs):
        got = {(e.get("word"), e.get("pos")): e for e in (result or {}).get("entries", [])}
        for entry in packet["entries"]:
            key = (entry["word"], entry["pos"])
            word = entry["word"]
            gloss_of = {s["synset"]: s["gloss"] for s in entry["senses"]}
            want = [s["synset"] for s in entry["senses"] if s["write"]]
            r = got.get(key)
            rec = {"word": word, "pos": entry["pos"], "accepted": False, "why": None,
                   "senses": {}}
            if r is None:
                rec["why"] = "no output returned"
            else:
                senses = r.get("senses") or {}
                if set(senses) != set(want):
                    rec["why"] = (f"wrote for {sorted(set(senses) - set(want))} not asked, "
                                  f"missed {sorted(set(want) - set(senses))}")
                else:
                    faults = []
                    for syn in want:
                        fault = check_sense(word, inflections.get(word.lower(), []),
                                            gloss_of[syn], senses[syn])
                        if fault:
                            faults.append(f"{syn}: {fault}")
                    if faults:
                        rec["why"] = "; ".join(faults)
                    else:
                        rec["accepted"] = True
                        for syn in want:
                            s = senses[syn]
                            rec["senses"][syn] = {
                                "learner": s["learner"].strip(),
                                "examples": [e.strip() for e in s["examples"]],
                                "usage_labels": list(s.get("usage_labels") or []),
                                "connotation": s["connotation"],
                            }
            results.append(rec)
            counts["accepted" if rec["accepted"] else "rejected"] += 1
            if not rec["accepted"]:
                print(f"  REJECT {word}/{entry['pos']}: {rec['why']}")
                continue

            # The overlay: rank for every sense of this POS, content for the
            # written ones. sense ids come from the selection, which read them
            # off the bulk, so the overlay can only name senses that exist.
            sel = selected[key]
            sid_of = {s["synset"]: s["sense_id"] for s in sel["senses"]}
            order = [s["synset"] for s in entry["senses"]]
            patch = {}
            for i, syn in enumerate(order, 1):
                p = {"rank": i}
                w = rec["senses"].get(syn)
                if w:
                    p["learner"] = w["learner"]
                    p["examples"] = w["examples"]
                    if w["usage_labels"]:
                        p["usage_labels"] = w["usage_labels"]
                    if w["connotation"] is None:
                        p["label"] = "neutral"
                    counts["senses_written"] += 1
                    counts["null" if w["connotation"] is None else "candidate"] += 1
                patch[sid_of[syn]] = p
            # One overlay line per headword: *more* was selected as an adverb
            # and as an adjective, and their sense patches never overlap.
            for line in overlay_lines:
                if line["word"] == word:
                    line["senses"].update(patch)
                    break
            else:
                overlay_lines.append({"word": word, "senses": patch})

    summary = {"entries": results, "counts": dict(counts)}
    (out / "results.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1),
                                      encoding="utf-8")
    overlay = Path(args.overlay)
    overlay.parent.mkdir(parents=True, exist_ok=True)
    with overlay.open("w", encoding="utf-8", newline="\n") as fh:
        for line in overlay_lines:
            fh.write(json.dumps(line, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"enricher: {counts['accepted']} accepted, {counts['rejected']} rejected; "
          f"{counts['senses_written']} senses written "
          f"({counts['null']} null, {counts['candidate']} candidate) -> {overlay}")
    return 0 if counts["accepted"] else 1


# --------------------------------------------------------------------------
# reads
# --------------------------------------------------------------------------

def cmd_reads(args):
    out = Path(args.out)
    packets = load_dir(out / "reader-packets", "input")
    outputs = load_dir(out / "reader-reads", "verdicts")
    entries = senses = 0
    entries_wrong = senses_wrong = rank_wrong = unsure = 0
    faults = Counter()
    wrong_list = []
    for name, packet, result in pair(packets, outputs):
        got = {(e.get("word"), e.get("pos")): e for e in (result or {}).get("verdicts", [])}
        for entry in packet["entries"]:
            key = (entry["word"], entry["pos"])
            v = got.get(key)
            if v is None:
                sys.exit(f"{name}: no verdict for {key}")
            entries += 1
            bad = False
            if v.get("rank") == "wrong":
                rank_wrong += 1
                faults["rank-wrong"] += 1
                bad = True
                wrong_list.append(f"{key[0]}/{key[1]} rank: {v.get('why')}")
            elif v.get("rank") == "unsure":
                unsure += 1
            written = {s["synset"] for s in entry["senses"] if "learner" in s}
            seen = set()
            for sv in v.get("senses") or []:
                syn = sv.get("synset")
                if syn not in written or syn in seen:
                    continue
                seen.add(syn)
                senses += 1
                if sv.get("verdict") == "wrong":
                    senses_wrong += 1
                    faults[sv.get("fault") or "other"] += 1
                    bad = True
                    wrong_list.append(f"{key[0]}/{syn} {sv.get('fault')}: {sv.get('why')}")
                elif sv.get("verdict") == "unsure":
                    unsure += 1
            missing = written - seen
            if missing:
                sys.exit(f"{name}: {key} verdict misses senses {sorted(missing)}")
            if bad:
                entries_wrong += 1
    summary = {
        "entries_read": entries, "entries_with_a_fault": entries_wrong,
        "entry_defect_rate": round(entries_wrong / entries, 4) if entries else None,
        "senses_read": senses, "senses_wrong": senses_wrong,
        "sense_defect_rate": round(senses_wrong / senses, 4) if senses else None,
        "rank_wrong": rank_wrong, "unsure": unsure,
        "faults": dict(faults.most_common()), "wrong": wrong_list,
    }
    (out / "reads-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1),
                                            encoding="utf-8")
    print(f"reads: {entries} entries, {entries_wrong} with a fault "
          f"({summary['entry_defect_rate']}); {senses} senses, {senses_wrong} wrong "
          f"({summary['sense_defect_rate']}); rank wrong {rank_wrong}; unsure {unsure}")
    for k, n in faults.most_common():
        print(f"  {k:<22}{n:>4}")
    for w in wrong_list:
        print("  - " + w)
    return 0


# --------------------------------------------------------------------------
# nulls (stage 5)
# --------------------------------------------------------------------------

def cmd_nulls(args):
    out = Path(args.out)
    packets = load_dir(out / "null-packets", "input")
    outputs = load_dir(out / "null-reads", "verdicts")
    read = wrong = unsure = 0
    wrong_list = []
    for name, packet, result in pair(packets, outputs):
        got = {v.get("synset"): v for v in (result or {}).get("verdicts", [])}
        for s in packet["senses"]:
            v = got.get(s["synset"])
            if v is None:
                sys.exit(f"{name}: no verdict for {s['word']} / {s['synset']}")
            read += 1
            if v.get("verdict") == "null-wrong":
                wrong += 1
                wrong_list.append(f"{s['word']}/{s['pos']} {s['synset']}: {v.get('why')}")
            elif v.get("verdict") == "unsure":
                unsure += 1
    summary = {"nulls_read": read, "false_null": wrong, "unsure": unsure,
               "false_null_rate": round(wrong / read, 4) if read else None,
               "wrong": wrong_list}
    (out / "nulls-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1),
                                            encoding="utf-8")
    print(f"nulls: {read} read, {wrong} false ({summary['false_null_rate']}), {unsure} unsure")
    for w in wrong_list:
        print("  - " + w)
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("ranker", "enricher", "reads", "nulls"):
        s = sub.add_parser(name)
        s.add_argument("--out", required=True)
        if name == "enricher":
            s.add_argument("--overlay",
                           default=str(ROOT / "data/entries/overlays/enrich-001.overlay.jsonl"))
    args = ap.parse_args()
    # The gate, not a warning: a rate produced by a moved instrument is not
    # comparable to an older one, and the cheapest moment to refuse is before
    # the number exists. See tools/instrument_gate.py.
    instrument_gate.enforce("validating the " + args.cmd + " pass")
    return {"ranker": cmd_ranker, "enricher": cmd_enricher, "reads": cmd_reads,
            "nulls": cmd_nulls}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
