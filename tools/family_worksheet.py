#!/usr/bin/env python3
"""Emit a pre-filled annotation skeleton for one or more families.

Most of the cost of annotating a family is not judgement, it is clerical:
finding the family, listing its members, resolving each member's sense id
against the corpus, and discovering afterwards that two of them do not exist.
This does all of that and leaves exactly the parts a person has to decide -
`charge`, `tone`, and the optional note and examples.

Members whose sense id is absent from the corpus are dropped here rather than
failing later in dict_enrich_apply.py, and the count is reported. Members whose
gloss is a usage restriction rather than a definition arrive pre-skipped: there
is nothing there for a note to agree with (tools/gloss_lint.py).

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
from gloss_lint import undefinable  # noqa: E402


def load_corpus_glosses(path):
    """sense id -> definition, for every sense in the corpus."""
    glosses = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    glosses[sense["id"]] = sense.get("definition", "")
    return glosses


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
    glosses = load_corpus_glosses(args.bulk)

    picked = [by_id[i] for i in args.id if i in by_id]
    missing_ids = [i for i in args.id if i not in by_id]
    for word in args.word:
        hits = [f for f in families
                if any(m["word"] == word for m in f["members"])]
        if hits:
            # Prefer a family the word actually heads. Size alone is the wrong
            # tiebreak: `lazy` appears as a satellite of *slow* ("moving slowly
            # and gently") and `angry` as one of *unhealthy* ("an angry
            # wound"), and both of those clusters are larger than the family
            # anyone means. A head word names its cluster; a satellite does not.
            picked.append(max(hits, key=lambda f: (word in f["head_words"],
                                                   f["size"])))
        else:
            missing_ids.append(f"(word {word})")

    out, dropped, unjudgeable = [], 0, 0
    for fam in picked:
        members, seen = [], set()
        for m in fam["members"]:
            if m["word"] in seen:
                continue
            sense_id = f"{slug(m['word'])}.{m['synset']}"
            if sense_id not in glosses:
                dropped += 1
                continue
            seen.add(m["word"])
            row = {
                "word": m["word"],
                "synset": m["synset"],
                "charge": 0,
                "tone": "",
                "_gloss": m["definition"][:90],
            }
            # A gloss that only restricts where the word applies gives the note
            # nothing to agree with, and audit 003 made that agreement the
            # test. Pre-skip it rather than invite a note that cannot be
            # checked - see tools/gloss_lint.py.
            reason = undefinable(glosses[sense_id])
            if reason:
                row["_skip"] = True
                row["_skip_reason"] = reason
                unjudgeable += 1
            members.append(row)
            if len(members) >= args.max_members:
                break
        if len([m for m in members if not m.get("_skip")]) < 2:
            continue
        out.append({
            "id": fam["id"].replace("oewn-", "family-"),
            "axis": "condemning → praising",
            "_head": fam["head_words"][:4],
            "_definition": fam["definition"][:100],
            "anchors": [m["word"] for m in members if not m.get("_skip")][:6],
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
    if unjudgeable:
        print(f"  {unjudgeable} members pre-skipped - the gloss is a usage "
              f"restriction, not a definition")
    for m in missing_ids:
        print(f"  not found: {m}")
    print(f"wrote {args.out}")
    print("Fill in charge (-3..3) and tone, then run family_apply.py directly - "
          "the _gloss/_head hints are ignored, and \"_skip\": true drops a member.")
    print("Every note must agree with the _gloss printed beside it; that is the "
          "first question any audit asks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
