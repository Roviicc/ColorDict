#!/usr/bin/env python3
"""Stage 9's done-check, re-derived from the corpus rather than from its notes.

Stage 9 closes when "every candidate in data/policy/*/results.json is either
authored or held with a reason, and the blind census on the authored senses is
under 5%". Both halves are checkable, and neither is checked by reading
stage9-candidates.json - that file is the routing tool's own account of what it
did, and a done-check that trusts it is asking the accused for a character
reference. The candidate list here is rebuilt from the Enricher results the way
candidate_families.py builds it, and matched against what is actually on disk.

A candidate is accounted for in exactly one of three ways:

  authored   a member of an annotated-018 family carrying a tone note
  withdrawn  a member marked _skip with a _skip_reason - pre-skipped as a usage
             restriction, or charged and then withdrawn by the collision audit
  held       in stage9-candidates.json's held list with a held_because

Anything in none of those is the failure this check exists to find: a candidate
that was routed and then quietly lost.

Usage:
    python3 tools/stage9_donecheck.py
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402  the one rule that mints a sense id

POLICY = ROOT / "data/policy"
FAMILIES = ROOT / "data/families"
BATCH = ROOT / "data/entries/batch-0001.jsonl"
THRESHOLD = 5.0
TICK_SHARDS = ("annotated-018.json", "annotated-018c.json")


def load_candidates():
    """Every accepted sense the Enricher marked as carrying connotation.

    The same walk candidate_families.load_candidates does, plus the hand
    candidates an audit added, so this check sees the population the router saw.
    """
    out = []
    for results in sorted(POLICY.glob("*/results.json")):
        data = json.loads(results.read_text(encoding="utf-8"))
        for entry in data.get("entries", []):
            if not entry.get("accepted"):
                continue
            for synset, sense in (entry.get("senses") or {}).items():
                if sense.get("connotation") is not None:
                    out.append({"run": results.parent.name, "word": entry.get("word"),
                                "synset": synset})
    hand = POLICY / "hand-candidates.json"
    if hand.is_file():
        for h in json.loads(hand.read_text(encoding="utf-8")).get("candidates", []):
            out.append({"run": "hand:" + h.get("source", "?"), "word": h["word"],
                        "synset": h["synset"]})
    return out


def load_members():
    """(word, synset) -> member, across the shards this tick wrote."""
    members = {}
    for name in TICK_SHARDS:
        data = json.loads((FAMILIES / name).read_text(encoding="utf-8"))
        for family in data["families"]:
            for member in family["members"]:
                members[(member["word"], member["synset"])] = member
    return members


def load_shipped():
    shipped = {}
    with BATCH.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    shipped[sense["id"]] = sense
    return shipped


def check_candidates(rep):
    candidates = load_candidates()
    members = load_members()
    ledger = json.loads((POLICY / "stage9-candidates.json").read_text(encoding="utf-8"))
    held = {(h["word"], h["synset"]): h for h in ledger.get("held_candidates", [])}

    seen, buckets, lost = set(), {"authored": [], "withdrawn": [], "held": []}, []
    for cand in candidates:
        key = (cand["word"], cand["synset"])
        if key in seen:
            continue
        seen.add(key)
        member = members.get(key)
        if member and member.get("_skip"):
            if member.get("_skip_reason"):
                buckets["withdrawn"].append(key)
            else:
                lost.append((key, "skipped with no reason"))
        elif member and member.get("tone"):
            buckets["authored"].append(key)
        elif key in held:
            if held[key].get("held_because"):
                buckets["held"].append(key)
            else:
                lost.append((key, "held with no reason"))
        elif member:
            lost.append((key, "routed into a family and never given a note"))
        else:
            lost.append((key, "in no family and in no held list"))

    # The router's own count is raw records, not distinct senses: a word the
    # Enricher marked a candidate in two runs is two records and one sense. The
    # stage notes carried the raw figures, so say both and check they agree
    # where they should.
    routed = {(c["word"], c["synset"])
              for lst in ledger.get("routing", {}).values() for c in lst}
    print(f"A. candidate ledger - {len(candidates)} candidate records from "
          f"{len(set(c['run'] for c in candidates))} runs, {len(seen)} distinct senses")
    print(f"     stage9-candidates.json reports {ledger.get('candidates')} candidates / "
          f"{ledger.get('routed')} routed / {ledger.get('held')} held, which are record "
          f"counts; distinct they are {len(routed | set(held))} / {len(routed)} / "
          f"{len(held)}")
    for name in ("authored", "withdrawn", "held"):
        print(f"     {name:10s} {len(buckets[name]):4d}")
    unknown = seen - (routed | set(held))
    orphan = (routed | set(held)) - seen
    if unknown:
        rep.append(f"{len(unknown)} candidate(s) the router never saw: "
                   f"{', '.join(f'{w}.{s}' for w, s in sorted(unknown)[:5])}")
    if orphan:
        rep.append(f"{len(orphan)} routed or held sense(s) that are not candidates "
                   f"in any results.json: "
                   f"{', '.join(f'{w}.{s}' for w, s in sorted(orphan)[:5])}")
    if lost:
        print(f"     LOST       {len(lost):4d}")
        for (word, synset), why in lost:
            print(f"       {word}.{synset}: {why}")
        rep.append(f"{len(lost)} candidate(s) accounted for nowhere")
    else:
        print("     every candidate is authored, withdrawn with a reason, or held "
              "with a reason")
    return buckets


def check_census(rep):
    results = json.loads((POLICY / "census-012-results.json").read_text(encoding="utf-8"))
    strata = results.get("by_arm_and_pos") or {}
    print(f"\nB. census gate - threshold {THRESHOLD}% per stratum, "
          f"judged per stratum and never blended")
    over = []
    for name in sorted(strata):
        s = strata[name]
        read, wrong = s.get("read", 0), s.get("wrong", 0)
        rate = 100.0 * wrong / read if read else 0.0
        flag = "  OVER" if rate > THRESHOLD else ""
        print(f"     {name:22s} {read:5d} read  {wrong:3d} wrong  {rate:5.1f}%{flag}")
        if rate > THRESHOLD:
            over.append(name)
    declared = results.get("strata_over_threshold")
    if over:
        rep.append(f"stratum over the gate: {', '.join(over)}")
    if declared != over and not (not declared and not over):
        rep.append(f"strata_over_threshold in the results file says {declared!r}, "
                   f"this check computes {over!r}")
    if results.get("caveats"):
        print(f"     {len(results['caveats'])} caveats recorded on this rate - "
              f"see census-012-results.json before quoting it")
    else:
        rep.append("census-012-results.json carries no caveats")
    return results


def check_stop_condition(rep, results):
    """Stage 9 stops if the book-restricted draw reads worse than 007-011."""
    print("\nC. stop condition - the candidate arm against the ticks before it")
    priors = []
    for n in ("007", "008", "009", "010", "011"):
        path = POLICY / f"census-{n}-results.json"
        if not path.is_file():
            continue
        prior = json.loads(path.read_text(encoding="utf-8"))
        read, wrong = prior.get("read", 0), prior.get("wrong", 0)
        priors.append((n, read, wrong))
        print(f"     census {n}           {read:5d} read  {wrong:3d} wrong  "
              f"{100.0 * wrong / read if read else 0:5.1f}%")
    pooled_read = sum(r for _, r, _ in priors)
    pooled_wrong = sum(w for _, _, w in priors)
    pooled = 100.0 * pooled_wrong / pooled_read if pooled_read else 0.0
    arm = (results.get("by_arm") or {}).get("candidate") or {}
    arm_rate = 100.0 * arm.get("wrong", 0) / arm["read"] if arm.get("read") else 0.0
    print(f"     pooled 007-011      {pooled_read:5d} read  {pooled_wrong:3d} wrong  "
          f"{pooled:5.1f}%")
    print(f"     census 012 candidate{arm.get('read', 0):5d} read  "
          f"{arm.get('wrong', 0):3d} wrong  {arm_rate:5.1f}%")
    if arm_rate > pooled:
        rep.append(f"candidate arm {arm_rate:.1f}% reads worse than the pooled "
                   f"{pooled:.1f}% - the cap is the suspect, per the stage's stop rule")
    else:
        print("     the book-restricted draw does not read worse; the cap stands")
    return pooled, arm_rate


def check_corpus(rep, buckets):
    """Every authored sense must reach the shipped file still carrying its note."""
    shipped = load_shipped()
    members = load_members()
    print("\nD. corpus - what the authored senses look like where a reader meets them")
    missing, noteless, uncharged = [], [], 0
    for key in buckets["authored"]:
        sense_id = f"{slug(key[0])}.{key[1]}"
        sense = shipped.get(sense_id)
        if sense is None:
            missing.append(sense_id)
            continue
        conn = sense.get("connotation") or {}
        if not conn.get("tone"):
            noteless.append(sense_id)
        if conn.get("label") == "neutral":
            uncharged += 1
    print(f"     {len(buckets['authored'])} authored candidate senses, "
          f"{len(buckets['authored']) - len(missing)} present in the shipped file")
    print(f"     {uncharged} of them ship on a neutral label, note and all "
          f"(normal: a note is force and register, not only approval)")
    if missing:
        rep.append(f"{len(missing)} authored sense(s) never reached the shipped file: "
                   f"{', '.join(missing[:5])}")
    if noteless:
        rep.append(f"{len(noteless)} authored sense(s) ship with no tone note: "
                   f"{', '.join(noteless[:5])}")

    withdrawn_live = []
    for key in buckets["withdrawn"]:
        sense = shipped.get(f"{slug(key[0])}.{key[1]}")
        if sense and ((sense.get("connotation") or {}).get("tone")):
            withdrawn_live.append(f"{key[0]}.{key[1]}")
    print(f"     {len(buckets['withdrawn'])} withdrawn senses, "
          f"{len(withdrawn_live)} still shipping a note")
    if withdrawn_live:
        rep.append(f"{len(withdrawn_live)} withdrawn sense(s) still ship a note: "
                   f"{', '.join(withdrawn_live[:5])}")

    tick = 0
    for name in TICK_SHARDS:
        data = json.loads((FAMILIES / name).read_text(encoding="utf-8"))
        tick += sum(1 for f in data["families"] for m in f["members"]
                    if not m.get("_skip") and m.get("tone"))
    flat = {s["family"]["id"] for s in shipped.values()
            if s.get("family") and "spectrum" not in s["family"]}
    print(f"     {tick} notes on disk in this tick's shards; "
          f"{len(flat)} families ship with no spectrum (every member at one charge)")

    # Not a stage 9 condition - the stage closes on authoring and the census -
    # but the stage 7 done-check found a bundled dictionary two days behind the
    # corpus and nothing had said so, which is how a whole tick reaches nobody.
    asset = ROOT / "app/src/main/assets/dicts/popup-en/popup-en.dict.dz"
    if asset.is_file():
        behind = BATCH.stat().st_mtime - asset.stat().st_mtime
        state = ("STALE" if behind > 0 else "current")
        print(f"     bundled dictionary is {state} against the corpus"
              + (f" by {behind / 3600:.1f}h - no reader has any of this tick"
                 if behind > 0 else ""))
    else:
        print("     no bundled dictionary in app assets")
    return len(shipped), tick, len(flat)


def main():
    print("STAGE 9 DONE-CHECK - the reading loop, proven on the backlog")
    print("=" * 66)
    rep = []
    buckets = check_candidates(rep)
    results = check_census(rep)
    pooled, arm_rate = check_stop_condition(rep, results)
    senses, notes, flat = check_corpus(rep, buckets)

    print("\n" + "=" * 66)
    if rep:
        print(f"FAIL - {len(rep)} finding(s)")
        for line in rep:
            print(f"  * {line}")
        return 1
    print("PASS - every candidate accounted for, no stratum over the gate, "
          "the draw does not read worse")
    print(f"       {len(buckets['authored'])} authored, {len(buckets['withdrawn'])} "
          f"withdrawn, {len(buckets['held'])} held; {results['read']} senses read blind, "
          f"{results['wrong']} wrong")
    return 0


if __name__ == "__main__":
    sys.exit(main())
