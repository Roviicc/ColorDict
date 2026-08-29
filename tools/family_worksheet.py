#!/usr/bin/env python3
"""Emit a pre-filled annotation skeleton for one or more families.

Most of the cost of annotating a family is not judgement, it is clerical:
finding the family, listing its members, resolving each member's sense id
against the corpus, and discovering afterwards that two of them do not exist.
This does all of that and leaves exactly the parts a person has to decide -
`charge`, `tone`, and the optional note and examples.

Members whose sense id is absent from the corpus are dropped here rather than
failing later in dict_enrich_apply.py, and the count is reported.

Usage:
    python3 tools/family_worksheet.py --families data/build/verb-families.json \
        --bulk data/entries/derived-bulk.jsonl \
        --id oewn-00944022-v --id oewn-01908923-v \
        --out data/families/draft-004.json

    # or pick by a headword the family contains
    python3 tools/family_worksheet.py --families data/build/adjective-families.json \
        --bulk data/entries/derived-bulk.jsonl --word honest --out draft.json
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from family_apply import slug  # noqa: E402


def load_corpus_sense_ids(path):
    ids = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    ids.add(sense["id"])
    return ids


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--families", type=Path, required=True,
                    help="output of family_extract.py")
    ap.add_argument("--bulk", type=Path, required=True)
    ap.add_argument("--id", action="append", default=[], help="family id; repeatable")
    ap.add_argument("--word", action="append", default=[],
                    help="pick the largest family containing this headword")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-members", type=int, default=16,
                    help="cap members per family; the long tail is rarely worth annotating")
    args = ap.parse_args()

    data = json.loads(args.families.read_text(encoding="utf-8"))
    families = data["families"]
    by_id = {f["id"]: f for f in families}
    valid = load_corpus_sense_ids(args.bulk)

    picked = [by_id[i] for i in args.id if i in by_id]
    missing_ids = [i for i in args.id if i not in by_id]
    for word in args.word:
        hits = [f for f in families
                if any(m["word"] == word for m in f["members"])]
        if hits:
            picked.append(max(hits, key=lambda f: f["size"]))
        else:
            missing_ids.append(f"(word {word})")

    out, dropped = [], 0
    for fam in picked:
        members, seen = [], set()
        for m in fam["members"]:
            if m["word"] in seen:
                continue
            sense_id = f"{slug(m['word'])}.{m['synset']}"
            if sense_id not in valid:
                dropped += 1
                continue
            seen.add(m["word"])
            members.append({
                "word": m["word"],
                "synset": m["synset"],
                "charge": 0,
                "tone": "",
                "_gloss": m["definition"][:90],
            })
            if len(members) >= args.max_members:
                break
        if len(members) < 2:
            continue
        out.append({
            "id": fam["id"].replace("oewn-", "family-"),
            "axis": "condemning → praising",
            "_head": fam["head_words"][:4],
            "_definition": fam["definition"][:100],
            "anchors": [m["word"] for m in members[:6]],
            "members": members,
        })

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"batch": args.out.stem, "families": out}, fh,
                  ensure_ascii=False, indent=1)

    print(f"{len(out)} families, "
          f"{sum(len(f['members']) for f in out)} members ready to annotate")
    if dropped:
        print(f"  {dropped} members dropped - sense id absent from the corpus")
    for m in missing_ids:
        print(f"  not found: {m}")
    print(f"wrote {args.out}")
    print("Fill in charge (-3..3) and tone, then run family_apply.py directly - "
          "the _gloss/_head hints are ignored, and \"_skip\": true drops a member.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
