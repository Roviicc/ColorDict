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
    seen = {}
    missing = []
    for i in range(1, 17):
        path = directory / f"verdicts-{i:02d}.json"
        if not path.exists():
            missing.append(i)
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for v in data["verdicts"]:
            seen[v["id"]] = v
    return seen, missing


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--dir", type=Path, required=True)
    ap.add_argument("--census", type=Path, default=ROOT / "data/policy/census-002.json")
    ap.add_argument("--prior", type=Path, default=ROOT / "data/policy/census-001.json")
    ap.add_argument("--out", type=Path, default=ROOT / "data/policy/census-002-results.json")
    ap.add_argument("--agent", type=Path, default=DEFAULT_AGENT,
                    help="reader agent definition whose frontmatter pins the instrument")
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
    prior = {e["id"] for e in json.loads(args.prior.read_text(encoding="utf-8"))["entries"]}

    verdicts, missing = load_verdicts(args.dir)
    unread = [i for i in entries if i not in verdicts]

    tally = Counter()
    faults = Counter()
    by_pos = defaultdict(Counter)
    by_history = defaultdict(Counter)
    by_synset = defaultdict(list)

    for sense_id, entry in entries.items():
        v = verdicts.get(sense_id)
        if not v:
            continue
        verdict = v["verdict"]
        history = "censused" if sense_id in prior else "never-censused"
        tally[verdict] += 1
        by_pos[entry.get("part_of_speech", "?")][verdict] += 1
        by_history[history][verdict] += 1
        if verdict != "right":
            faults[v.get("fault") or "unspecified"] += 1
            synset = sense_id.split(".", 1)[1] if "." in sense_id else sense_id
            by_synset[synset].append({
                "id": sense_id, "word": entry.get("word"), "verdict": verdict,
                "fault": v.get("fault"), "why": v.get("why"),
                "definition": entry.get("definition"), "tone": entry.get("tone"),
                "history": history,
            })

    read = sum(tally.values())
    rate = round(100 * tally["wrong"] / read, 1) if read else None

    def split(counter):
        n = sum(counter.values())
        return {"read": n, "right": counter["right"], "wrong": counter["wrong"],
                "unsure": counter["unsure"],
                "error_rate_pct": round(100 * counter["wrong"] / n, 1) if n else None}

    results = {
        "sample": "census-002",
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
        "faults": dict(faults.most_common()),
        "by_part_of_speech": {k: split(v) for k, v in sorted(by_pos.items())},
        "by_census_001_history": {k: split(v) for k, v in sorted(by_history.items())},
        "failures_by_synset": {k: v for k, v in sorted(by_synset.items())},
    }
    args.out.write_text(json.dumps(results, indent=1, ensure_ascii=False), encoding="utf-8")

    print(f"instrument: {reader_model} effort={instrument['effort']} "
          f"tools=[{instrument.get('tools')}]  ({args.agent})")
    print(f"read {read}/{census.get('population')}  right {tally['right']}  "
          f"wrong {tally['wrong']}  unsure {tally['unsure']}  -> {rate}% (threshold 5.0%)")
    if missing:
        print(f"MISSING packets: {missing}")
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
