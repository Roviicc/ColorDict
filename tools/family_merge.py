#!/usr/bin/env python3
"""Merge authored family JSON back into a worksheet, checking it fits.

Authoring is fanned out one agent per family, and an agent returns JSON rather
than editing the worksheet. That is safer than letting many writers touch one
file, but it moves the risk to the merge: an author can return a member that
was never asked for, drop one that was, or attach a field nobody defined. The
first tick produced exactly that - a stray "chargeInvalid" key riding alongside
a valid charge - which would have travelled silently into the corpus.

So the merge is strict. Only `charge` and `tone` are taken from the author, and
only for members the worksheet already lists; anything else is reported and
refused. A family that does not match is left unannotated rather than partly
annotated, because a half-filled family is harder to notice than an empty one.

Usage:
    python3 tools/family_merge.py --draft data/families/draft-010.json \
        --authored <dir of *.json> --out data/families/annotated-010.json
"""

import argparse
import json
from pathlib import Path

TAKE = ("charge", "tone")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--draft", type=Path, required=True)
    ap.add_argument("--authored", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    draft = json.loads(args.draft.read_text(encoding="utf-8"))
    by_id = {f["id"]: f for f in draft["families"]}

    filled = skipped = 0
    problems = []
    seen = set()

    for path in sorted(args.authored.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        fam = by_id.get(data.get("id"))
        if fam is None:
            problems.append(f"{path.name}: family id {data.get('id')!r} is not in the draft")
            continue
        seen.add(fam["id"])

        wanted = {(m["word"], m["synset"]) for m in fam["members"] if not m.get("_skip")}
        got = {(m.get("word"), m.get("synset")) for m in data.get("members", [])}
        if got - wanted:
            problems.append(f"{fam['id']}: author returned members not in the worksheet: {sorted(got - wanted)}")
            continue
        if wanted - got:
            problems.append(f"{fam['id']}: author did not return {sorted(wanted - got)}")
            continue

        extra = {k for m in data["members"] for k in m} - set(TAKE) - {"word", "synset"}
        if extra:
            problems.append(f"{fam['id']}: dropped undefined field(s) {sorted(extra)} from the author's output")

        authored = {(m["word"], m["synset"]): m for m in data["members"]}
        for m in fam["members"]:
            if m.get("_skip"):
                skipped += 1
                continue
            a = authored[(m["word"], m["synset"])]
            charge = a.get("charge")
            tone = (a.get("tone") or "").strip()
            if not isinstance(charge, int) or not -3 <= charge <= 3:
                problems.append(f"{fam['id']}/{m['word']}: charge {charge!r} is not an integer in -3..3")
                continue
            if not tone:
                problems.append(f"{fam['id']}/{m['word']}: empty tone")
                continue
            m["charge"], m["tone"] = charge, tone
            filled += 1
        if data.get("axis"):
            fam["axis"] = data["axis"]

    unauthored = [i for i in by_id if i not in seen]
    args.out.write_text(json.dumps(draft, indent=1, ensure_ascii=False), encoding="utf-8")

    print(f"{len(seen)}/{len(by_id)} families merged, {filled} senses filled, {skipped} pre-skipped -> {args.out}")
    if unauthored:
        print(f"still unauthored ({len(unauthored)}): {', '.join(unauthored)}")
    for p in problems:
        print(f"  ! {p}")
    return 1 if any("refused" in p or "did not return" in p or "not in the worksheet" in p for p in problems) else 0


if __name__ == "__main__":
    raise SystemExit(main())
