#!/usr/bin/env python3
"""Split a family worksheet into one input file per family, and keep them.

Authoring is fanned out one agent per family, and until now the family reached
the agent by being retyped into its prompt. That is the same failure that cost
two censuses on the rubric side: what the agent actually read was a manager's
transcription of a file, and nothing afterwards could tell the two apart. A
packet is the file, written by a tool, read verbatim.

What an author is allowed to see is an allowlist, not a subtraction. Two fields
in the worksheet are deliberately withheld:

  axis     - every draft family carries the placeholder "condemning -> praising".
             The rubric asks the author to write the axis that these members
             actually run along, and handing them a generic one first is an
             anchor, not a help.
  anchors  - which members a rendered spectrum is built around is a display
             decision made later. It is also present on gate-drawn worksheets
             and absent on book-drawn ones, so passing it through would make the
             two arms of a census differ in what the author saw.

Skipped members travel with their reason so the author can see the family whole,
but they are marked, and the rubric tells the author to leave them out.

The gloss is restored to its full length. Worksheets store `_gloss` cut to 90
characters - a display cap in `family_worksheet.py` that `candidate_families.py`
inherited - and the rubric calls that gloss the author's entire evidence. A
definition cut mid-word ("sufficient b") is not the evidence the sense was
printed under, and the blind reader who judges the note afterwards has always
been shown the whole definition, so the cap put author and reader in front of
different texts. In draft-018 it hits 28 of 483 candidate senses and 0 of 86
control senses, which would have loaded the difference between the two arms of
census 012 onto the very thing that census exists to measure.

Usage:
    python3 tools/family_packets.py --draft data/families/draft-018.json \
        --out <dir> [--only family-04699505-n ...] [--limit N]
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from family_apply import slug  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent

# What an author may see. A field added to the worksheet later cannot leak into
# a packet by accident.
FAMILY_VISIBLE = ("id", "_head", "_definition")
MEMBER_VISIBLE = ("word", "synset", "_gloss", "_skip", "_skip_reason", "_note_5_3")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--draft", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--only", nargs="*", default=None,
                    help="family ids to write; default is all of them")
    ap.add_argument("--limit", type=int, default=0,
                    help="stop after N families (largest first), for a first batch")
    ap.add_argument("--bulk", type=Path, default=ROOT / "data/entries/derived-bulk.jsonl",
                    help="corpus entries, to restore glosses the worksheet cut to 90 chars")
    args = ap.parse_args()

    draft = json.loads(args.draft.read_text(encoding="utf-8"))
    families = draft["families"]

    if args.only:
        wanted = list(args.only)
        by_id = {f["id"]: f for f in families}
        missing = [i for i in wanted if i not in by_id]
        if missing:
            raise SystemExit(f"not in {args.draft}: {', '.join(missing)}")
        families = [by_id[i] for i in wanted]
    if args.limit:
        families = sorted(
            families,
            key=lambda f: -sum(1 for m in f["members"] if not m.get("_skip")),
        )[:args.limit]

    # sense id -> the definition the sense is actually printed under
    glosses = {}
    with args.bulk.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    glosses[sense["id"]] = sense.get("definition", "")

    args.out.mkdir(parents=True, exist_ok=True)
    written, restored, unrestored = [], 0, []
    for fam in families:
        packet = {k: fam[k] for k in FAMILY_VISIBLE if k in fam}
        packet["batch"] = draft.get("batch")
        packet["members"] = []
        for m in fam["members"]:
            row = {k: m[k] for k in MEMBER_VISIBLE if k in m}
            full = glosses.get(f"{slug(m['word'])}.{m['synset']}")
            if full and full != row.get("_gloss"):
                if not full.startswith(row.get("_gloss", "")):
                    # The worksheet's copy is not a prefix of the corpus's, so
                    # these are two different glosses, not one cut short. Keep
                    # the worksheet's - it is what the family was drawn on.
                    unrestored.append(f"{m['word']}.{m['synset']}")
                    packet["members"].append(row)
                    continue
                row["_gloss"] = full
                restored += 1
            packet["members"].append(row)
        path = args.out / f"{fam['id']}.json"
        path.write_text(json.dumps(packet, indent=1, ensure_ascii=False),
                        encoding="utf-8")
        written.append((path.name, sum(1 for m in fam["members"] if not m.get("_skip"))))

    withheld = sorted(({k for f in draft["families"] for k in f} - set(FAMILY_VISIBLE))
                      | ({k for f in draft["families"] for m in f["members"] for k in m}
                         - set(MEMBER_VISIBLE)))
    total = sum(n for _, n in written)
    print(f"{len(written)} families / {total} annotatable senses -> {args.out}")
    for name, n in written:
        print(f"  {name}  {n}")
    print(f"glosses restored to full length: {restored}")
    if unrestored:
        print(f"  ! left as the worksheet had them ({len(unrestored)}): {', '.join(unrestored[:8])}")
    if withheld:
        print(f"withheld from authors: {', '.join(withheld)}")


if __name__ == "__main__":
    main()
