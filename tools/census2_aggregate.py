#!/usr/bin/env python3
"""Merge census 002 reader verdicts and split the rate by repair history.

`census_aggregate.py` collects census 001, whose readers wrote a different
verdict shape; this reads the census 002 packets ({"packet", "verdicts":
[{id, verdict, fault, why}]}) and adds the dimension census 001 could not have:
**whether census 001 ever read the sense at all.**

Census 001 drew its population with the `--exclude` logic that keeps a second
reading on fresh material, so it skipped the ~171 senses audits 001-004 had
already sampled — the population already proven to contain failures. It then
repaired 198 faults everywhere except there. Splitting the census 002 rate on
that boundary says whether the corpus has a method problem or an unfinished
repair.

The reading instrument is not inferred from memory. `.claude/agents/census-reader.md`
pins the reader's model, effort and tool allowlist in version control, and this
script copies those into the results so every tick records what read it. Pass
`--reader-model` with the resolved model ID actually served (the agent file can
only name an alias, and an alias drifts); a mismatch between the two is warned
about rather than silently written.

Usage:
    python3 tools/census2_aggregate.py --dir <verdict dir> \
        --census data/policy/census-002.json \
        --prior data/policy/census-001.json \
        --reader-model claude-fable-5-1 \
        --out data/policy/census-002-results.json
"""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


DEFAULT_AGENT = ROOT / ".claude/agents/census-reader.md"


def read_instrument(path):
    """Pull model / effort / tools out of the reader agent's YAML frontmatter.

    Deliberately a five-line parser rather than a PyYAML dependency: the fields
    that define the instrument are flat strings, and a census must not fail to
    record what read it because a library is missing.
    """
    if not path.exists():
        raise SystemExit(
            f"no reader agent definition at {path}\n"
            "The instrument has to be version-controlled, not remembered. "
            "Write the agent file before aggregating."
        )
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        raise SystemExit(f"{path}: expected YAML frontmatter opening with ---")
    fields = {}
    for line in lines[1:]:
        if line.strip() == "---":
            break
        if ":" in line and not line.startswith((" ", "\t", "#")):
            key, _, value = line.partition(":")
            fields[key.strip()] = value.strip()
    missing = [k for k in ("model", "effort") if k not in fields]
    if missing:
        raise SystemExit(f"{path}: frontmatter is missing {', '.join(missing)}")
    return fields


def load_verdicts(directory):
    """Collect every verdict file, and report packets that were never answered.

    The packet count is discovered rather than assumed. It was hardcoded to 16
    because census 002 happened to use sixteen readers, which meant a census run
    with any other number reported the difference as missing packets - a false
    alarm that would teach an operator to ignore the one line that matters when
    a reader really does drop a packet.
    """
    seen = {}
    answered = set()
    for path in sorted(directory.glob("verdicts-*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        answered.add(int(path.stem.split("-")[1]))
        for v in data["verdicts"]:
            seen[v["id"]] = v
    # A packet that was handed out and never came back is the failure worth
    # naming; if no inputs were kept, fall back to the numbering we did receive.
    handed_out = {int(p.stem.split("-")[1]) for p in directory.glob("input-*.json")}
    missing = sorted((handed_out or answered) - answered)
    return seen, missing


def reconcile_ids(verdicts, entries):
    """Rescue verdicts whose sense id was mistyped when the reader transcribed it.

    A sense id is "<word>.<synset>", and the synset half is the fragile one: it
    is eight digits the reader copies by hand, and it has now been mistyped in
    three censuses (005, 009, 010). Census 005 is what this costs when nobody
    notices - the verdict vanished into `verdicts_for_unknown_ids` and the sense
    it belonged to was counted unread, so a sense that WAS read correctly still
    came off the numerator.

    The word half of the id is not fragile, and it is enough: when a stray
    verdict's word matches exactly one unread sense, that pairing is the
    mistype. Ambiguity is refused rather than guessed - two unread senses of the
    same word leave the stray stray.

    This never happens silently. Every remap is printed and written into the
    results under `reconciled_ids`, because a reconciliation that cannot be
    audited is just a different way to lose the verdict.
    """
    def word_of(sense_id):
        return sense_id.split(".", 1)[0] if "." in sense_id else None

    unread_by_word = {}
    for sense_id in entries:
        if sense_id in verdicts:
            continue
        unread_by_word.setdefault(word_of(sense_id), []).append(sense_id)

    reconciled = []
    for bad_id in sorted(i for i in verdicts if i not in entries):
        candidates = unread_by_word.get(word_of(bad_id)) or []
        if len(candidates) != 1:
            continue
        good_id = candidates[0]
        v = dict(verdicts.pop(bad_id))
        v["id"] = good_id
        verdicts[good_id] = v
        unread_by_word[word_of(bad_id)] = []
        reconciled.append({
            "verdict_id": bad_id, "matched_to": good_id,
            "basis": "same word, and the only unread sense carrying it",
        })
    return reconciled


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--dir", type=Path, required=True)
    ap.add_argument("--census", type=Path, default=ROOT / "data/policy/census-002.json")
    ap.add_argument("--prior", type=Path, default=None,
                    help="earlier census to split the rate on; omit when there is "
                         "no prior population to compare against")
    ap.add_argument("--out", type=Path, default=ROOT / "data/policy/census-002-results.json")
    ap.add_argument("--agent", type=Path, default=DEFAULT_AGENT,
                    help="reader agent definition whose frontmatter pins the instrument")
    ap.add_argument("--strict-ids", action="store_true",
                    help="do not pair a stray verdict with an unread sense of the "
                         "same word; report both as-is, the pre-tick-8 behaviour")
    ap.add_argument("--arm", action="append", default=[], metavar="NAME=WORKSHEET",
                    help="a census arm: NAME=path to the family worksheet whose members "
                         "belong to it. Repeatable. Census 012 has two - candidate and "
                         "control - because the candidate draw is mostly noun and every "
                         "comparable census is adjective; the 5%% gate is judged per arm "
                         "and per arm/POS, never on the blend.")
    ap.add_argument("--reader-model", default=None,
                    help="resolved model ID actually served (e.g. claude-fable-5-1); "
                         "defaults to the alias named in the agent file")
    args = ap.parse_args()

    instrument = read_instrument(args.agent)
    reader_model = args.reader_model or instrument["model"]
    if args.reader_model and instrument["model"] not in args.reader_model:
        print(f"WARNING: agent file says model {instrument['model']!r} but "
              f"--reader-model is {args.reader_model!r}; recording the latter.")

    census = json.loads(args.census.read_text(encoding="utf-8"))
    entries = {e["id"]: e for e in census["entries"]}
    # No prior is a real state, not a missing argument: census 003 reads one
    # shard nothing has seen before, and a split against an empty set would
    # report every sense as never-censused, which says nothing.
    prior = None
    if args.prior:
        prior = {e["id"] for e in json.loads(args.prior.read_text(encoding="utf-8"))["entries"]}

    verdicts, missing = load_verdicts(args.dir)
    # Repair mistyped ids BEFORE counting, so a sense that was read does not
    # come off the numerator for a transcription slip. See reconcile_ids.
    reconciled = [] if args.strict_ids else reconcile_ids(verdicts, entries)
    unread = [i for i in entries if i not in verdicts]
    # A verdict whose id is not in the population was silently dropped before.
    # Census 005 produced one: a reader transcribed a synset id wrong, so a
    # sense read correctly still counted as unread while the verdict vanished.
    # One unread sense and one stray verdict is a mistyped id; report both so
    # the pair can be recognised instead of being written off as a short read.
    stray = sorted(i for i in verdicts if i not in entries)

    # sense id -> arm, from the worksheets. A sense in no arm is reported as
    # such rather than guessed: the strata were pre-registered, so a sense the
    # registration did not cover is a finding about the packet, not noise.
    arm_of = {}
    for spec in args.arm:
        name, _, path = spec.partition("=")
        if not path:
            raise SystemExit(f"--arm needs NAME=WORKSHEET, got {spec!r}")
        sheet = json.loads(Path(path).read_text(encoding="utf-8"))
        for fam in sheet.get("families", []):
            for m in fam.get("members", []):
                arm_of[f"{m['word'].replace(' ', '_')}.{m['synset']}"] = name

    tally = Counter()
    faults = Counter()
    by_pos = defaultdict(Counter)
    by_arm = defaultdict(Counter)
    by_arm_pos = defaultdict(Counter)
    by_history = defaultdict(Counter)
    by_synset = defaultdict(list)

    for sense_id, entry in entries.items():
        v = verdicts.get(sense_id)
        if not v:
            continue
        verdict = v["verdict"]
        history = None if prior is None else (
            "censused" if sense_id in prior else "never-censused")
        tally[verdict] += 1
        by_pos[entry.get("part_of_speech", "?")][verdict] += 1
        if args.arm:
            arm = arm_of.get(sense_id, "no-arm")
            by_arm[arm][verdict] += 1
            by_arm_pos[f"{arm}/{entry.get('part_of_speech', '?')}"][verdict] += 1
        if history:
            by_history[history][verdict] += 1
        if verdict != "right":
            faults[v.get("fault") or "unspecified"] += 1
            synset = sense_id.split(".", 1)[1] if "." in sense_id else sense_id
            by_synset[synset].append({
                "id": sense_id, "word": entry.get("word"), "verdict": verdict,
                "fault": v.get("fault"), "why": v.get("why"),
                "definition": entry.get("definition"), "tone": entry.get("tone"),
                **({"history": history} if history else {}),
            })

    read = sum(tally.values())
    rate = round(100 * tally["wrong"] / read, 1) if read else None

    def split(counter):
        n = sum(counter.values())
        return {"read": n, "right": counter["right"], "wrong": counter["wrong"],
                "unsure": counter["unsure"],
                "error_rate_pct": round(100 * counter["wrong"] / n, 1) if n else None}

    results = {
        # Taken from the population file, not hardcoded: census 003 was written
        # out labelled "census-002" before this was caught, and a results file
        # that misnames its own sample is worse than one that omits it.
        "sample": census.get("sample", args.census.stem),
        "seed": census.get("seed"),
        "population": census.get("population"),
        "read": read,
        "right": tally["right"],
        "wrong": tally["wrong"],
        "unsure": tally["unsure"],
        "error_rate_pct": rate,
        "threshold_pct": 5.0,
        "reader_model": reader_model,
        "reader_effort": instrument["effort"],
        "reader_agent": str(args.agent.relative_to(ROOT)) if args.agent.is_absolute() else str(args.agent),
        "reader_tools": instrument.get("tools"),
        "note": "blind read - readers saw only gloss, charge and note, and did not author or repair the corpus",
        "missing_packets": missing,
        "unread": unread,
        "verdicts_for_unknown_ids": stray,
        "reconciled_ids": reconciled,
        "faults": dict(faults.most_common()),
        "by_part_of_speech": {k: split(v) for k, v in sorted(by_pos.items())},
        **({"by_arm": {k: split(v) for k, v in sorted(by_arm.items())},
            "by_arm_and_pos": {k: split(v) for k, v in sorted(by_arm_pos.items())},
            "strata_over_threshold": sorted(
                k for k, v in by_arm_pos.items()
                if sum(v.values()) and 100 * v["wrong"] / sum(v.values()) > 5.0),
            "note_strata": "the stop condition is judged per stratum in by_arm_and_pos; "
                           "the blended error_rate_pct above decides nothing"}
           if args.arm else {}),
        # Key name kept verbatim so census 002's published results still
        # reproduce byte-for-byte; census 003 has no prior and omits it.
        **({"by_census_001_history": {k: split(v) for k, v in sorted(by_history.items())}}
           if prior is not None else {}),
        "failures_by_synset": {k: v for k, v in sorted(by_synset.items())},
    }
    args.out.write_text(json.dumps(results, indent=1, ensure_ascii=False), encoding="utf-8")

    print(f"instrument: {reader_model} effort={instrument['effort']} "
          f"tools=[{instrument.get('tools')}]  ({args.agent})")
    print(f"read {read}/{census.get('population')}  right {tally['right']}  "
          f"wrong {tally['wrong']}  unsure {tally['unsure']}  -> {rate}% (threshold 5.0%)")
    if missing:
        print(f"MISSING packets: {missing}")
    if args.arm:
        for k, v in sorted(by_arm_pos.items()):
            n = sum(v.values())
            flag = "  <-- OVER 5%" if n and 100 * v["wrong"] / n > 5.0 else ""
            print(f"  {k:24} read {n:4}  wrong {v['wrong']:3}  "
                  f"{(100 * v['wrong'] / n) if n else 0:5.1f}%{flag}")
        if "no-arm" in by_arm:
            print(f"  {by_arm['no-arm'].total()} read sense(s) belong to no registered arm")
    if reconciled:
        print(f"RECONCILED {len(reconciled)} mistyped id(s) - counted as read:")
        for r in reconciled:
            print(f"  {r['verdict_id']}  ->  {r['matched_to']}")
    if stray:
        print(f"VERDICTS for ids not in the population: {stray}")
    if unread and stray:
        print("  ^ one unread sense alongside one stray verdict usually means a "
              "mistyped id, not a missed read - check before accepting the count.")
    print()
    for name, counter in sorted(by_history.items()):
        s = split(counter)
        print(f"  {name:16} read {s['read']:>4}  wrong {s['wrong']:>3}  -> {s['error_rate_pct']}%")
    print()
    for name, counter in sorted(by_pos.items()):
        s = split(counter)
        print(f"  {name:16} read {s['read']:>4}  wrong {s['wrong']:>3}  -> {s['error_rate_pct']}%")
    print()
    print("faults:", dict(faults.most_common()))
    print(f"failing synsets: {len(by_synset)} -> {args.out}")


if __name__ == "__main__":
    main()
