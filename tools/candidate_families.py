#!/usr/bin/env python3
"""The join: turn Enricher candidates into book-restricted family worksheets.

The Enricher answers one question per sense - does this sense judge what it is
aimed at? - and returns either `null` or `{"candidate": true, "why": ...}`. A
candidate is a handoff: the Enricher never writes charge or tone, so the sense
is supposed to go to the family path where a family-author writes them against
its neighbours.

Nothing implemented that handoff. enrich_validate.py writes `label: "neutral"`
for a null and writes nothing at all for a candidate, and the `why` lands in the
run's results.json where no tool reads it. 121 senses across four runs have been
sitting in that gap since enrich-001, invisible to every instrument: a blind
reader shown a card with no tone note has nothing to mark wrong.

This closes it. But routing a candidate into its family naively is unaffordable,
because the two halves of the pipeline work at different grains. The Enricher
works per word-sense; the family path works per family; and OEWN noun families
are hypernym trees rather than connotation sets - `woman` carries 603 members,
`herb` 623. Sending 121 candidates to their smallest containing families whole
means authoring 3,958 member senses, more than the entire corpus built so far.

Two restrictions make it affordable, and they are the same restriction that
makes the product better:

  1. Keep only members the ingested books actually use. `woman` 603 -> 55,
     `man` 549 -> 83, `person` 105 -> 35. The rendered spectrum a reader sees
     is then the words in their book, not six hundred hyponyms.
  2. Cap what is left (default 20). A table longer than that is a taxonomy
     dump, not a spectrum, whoever is reading it.

Measured on the existing 121: 87 worksheets, 530 senses, 99 candidates routed -
against 3,958 senses naive. The candidates that do not fit are HELD and named in
the record file, never silently dropped: holding a sense is a decision someone
can revisit, and dropping one is the fault this script exists to fix.

The worksheet it writes is exactly the shape family_worksheet.py produces, so
family_merge.py, tone_lint.py and family_apply.py all run unchanged.

The Enricher's `why` deliberately does NOT go into the worksheet. family-author
is told the gloss is its entire evidence, and slipping a second source of
evidence beside it invites notes that agree with the why rather than the gloss -
which is the largest fault class in every census. The whys go to the record file
instead, for the person reading this run later.

Usage:
    python3 tools/candidate_families.py --out data/families/draft-018.json \\
        --record data/policy/stage9-candidates.json

    # tighter spectrum, fewer senses
    python3 tools/candidate_families.py --cap 12 --out draft.json --record rec.json
"""

import argparse
import collections
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402
from gloss_lint import undefinable  # noqa: E402

POS = {"ADJ": "adjective", "ADV": "adverb", "NOUN": "noun", "VERB": "verb"}
FAMILY_FILES = ("adjective-families.json", "verb-families.json",
                "noun-families.json")


def load_books(books_dir):
    """lemma -> total occurrences across every ingested book."""
    freq = collections.Counter()
    for path in sorted(books_dir.glob("*/lemmas.jsonl")):
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                rec = json.loads(line)
                if POS.get(rec["part_of_speech"]):
                    freq[rec["lemma"].lower()] += rec["corpus"]["total_occurrences"]
    return freq


def load_candidates(run_dirs):
    """Every accepted sense the Enricher marked as carrying connotation."""
    out = []
    for run in run_dirs:
        results = run / "results.json"
        if not results.is_file():
            continue
        data = json.loads(results.read_text(encoding="utf-8"))
        for entry in data.get("entries", []):
            if not entry.get("accepted"):
                continue
            for synset, sense in (entry.get("senses") or {}).items():
                conn = sense.get("connotation")
                if conn is not None:
                    out.append({"run": run.name, "word": entry.get("word"),
                                "pos": entry.get("pos"), "synset": synset,
                                "why": conn.get("why")})
    return out


def load_corpus_glosses(path):
    glosses = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                for sense in json.loads(line)["senses"]:
                    glosses[sense["id"]] = sense.get("definition", "")
    return glosses


def book_members(family, freq):
    """Members whose lemma appears in a book, deduplicated by word, most
    frequent first. Frequency order matters because the cap truncates: the
    words a reader meets most often are the ones the spectrum should show."""
    seen, rows = set(), []
    for m in family["members"]:
        word = m["word"].lower()
        if word not in freq or word in seen:
            continue
        seen.add(word)
        rows.append(m)
    rows.sort(key=lambda m: -freq[m["word"].lower()])
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--policy", type=Path, default=ROOT / "data/policy",
                    help="scanned for */results.json")
    ap.add_argument("--run", action="append", default=[],
                    help="limit to these run directory names; repeatable")
    ap.add_argument("--books", type=Path, default=ROOT / "data/build/books")
    ap.add_argument("--build", type=Path, default=ROOT / "data/build")
    ap.add_argument("--bulk", type=Path, default=ROOT / "data/entries/derived-bulk.jsonl")
    ap.add_argument("--cap", type=int, default=20,
                    help="maximum members per worksheet (default 20)")
    ap.add_argument("--min-members", type=int, default=2,
                    help="a family with fewer annotatable members has no contrast to write")
    ap.add_argument("--out", type=Path, required=True, help="the worksheet")
    ap.add_argument("--record", type=Path, required=True,
                    help="routing record: where every candidate went, and why the held ones did not")
    args = ap.parse_args()

    runs = sorted(p for p in args.policy.iterdir() if p.is_dir())
    if args.run:
        runs = [p for p in runs if p.name in set(args.run)]
    candidates = load_candidates(runs)
    if not candidates:
        print("no candidates found - nothing to route")
        return 1

    freq = load_books(args.books)
    families, index = {}, collections.defaultdict(list)
    for name in FAMILY_FILES:
        for fam in json.loads((args.build / name).read_text(encoding="utf-8"))["families"]:
            families[fam["id"]] = fam
            for m in fam["members"]:
                index[m["synset"]].append(fam["id"])

    # Cache the book-restricted view: the same huge families are consulted over
    # and over, and rebuilding `woman` per candidate is the whole runtime.
    restricted = {}

    def restrict(fid):
        if fid not in restricted:
            restricted[fid] = book_members(families[fid], freq)
        return restricted[fid]

    # Route each candidate to the SMALLEST book-restricted family that contains
    # it and fits under the cap. Smallest wins because a tight family is where
    # contrast is real: `lady` inside a 55-member `woman` is a spectrum, inside
    # a 603-member one it is a filing system.
    routed, held = collections.defaultdict(list), []
    for cand in candidates:
        options = []
        for fid in index.get(cand["synset"], []):
            members = restrict(fid)
            if not (args.min_members <= len(members) <= args.cap):
                continue
            if any(m["synset"] == cand["synset"] for m in members):
                options.append((len(members), fid))
        if not options:
            sizes = sorted(len(restrict(f)) for f in index.get(cand["synset"], []))
            held.append({**cand, "held_because":
                         "no family in the books" if not sizes else
                         f"smallest book-restricted family is {sizes[-1] if sizes[0] > args.cap else sizes[0]} members"
                         f" (cap {args.cap})"})
            continue
        options.sort()
        routed[options[0][1]].append(cand)

    glosses = load_corpus_glosses(args.bulk)
    sheets, dropped, unjudgeable, skipped_families = [], 0, 0, []

    for fid, cands in sorted(routed.items(), key=lambda kv: -len(kv[1])):
        family = families[fid]
        wanted = {c["synset"] for c in cands}
        pool = restrict(fid)
        # The candidate senses are why this worksheet exists, so they lead and
        # the cap can never truncate them away.
        pool = ([m for m in pool if m["synset"] in wanted]
                + [m for m in pool if m["synset"] not in wanted])

        members, seen = [], set()
        for m in pool:
            if m["word"] in seen:
                continue
            sense_id = f"{slug(m['word'])}.{m['synset']}"
            if sense_id not in glosses:
                dropped += 1
                continue
            seen.add(m["word"])
            row = {"word": m["word"], "synset": m["synset"], "charge": 0,
                   "tone": "", "_gloss": m["definition"][:90]}
            reason = undefinable(glosses[sense_id])
            if reason:
                row["_skip"] = True
                row["_skip_reason"] = reason
                unjudgeable += 1
            members.append(row)
            if len(members) >= args.cap:
                break

        annotatable = [m for m in members if not m.get("_skip")]
        if len(annotatable) < args.min_members:
            skipped_families.append(fid)
            for c in cands:
                held.append({**c, "held_because":
                             f"family {fid} has {len(annotatable)} annotatable members"})
            continue

        sheets.append({
            "id": fid.replace("oewn-", "family-"),
            "axis": "condemning → praising",
            "_head": family["head_words"][:4],
            "_definition": family["definition"][:100],
            "anchors": [m["word"] for m in annotatable][:6],
            "members": members,
        })

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump({"batch": args.out.stem, "families": sheets}, fh,
                  ensure_ascii=False, indent=1)

    args.record.parent.mkdir(parents=True, exist_ok=True)
    with args.record.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump({
            "cap": args.cap,
            "candidates": len(candidates),
            "routed": sum(len(v) for v in routed.values()) - len(
                [h for h in held if h.get("held_because", "").startswith("family ")]),
            "held": len(held),
            "worksheets": len(sheets),
            "senses_to_author": sum(len(f["members"]) for f in sheets),
            "runs": [r.name for r in runs],
            "routing": {fid: [{"word": c["word"], "pos": c["pos"],
                               "synset": c["synset"], "why": c["why"],
                               "run": c["run"]} for c in cands]
                        for fid, cands in routed.items() if fid not in skipped_families},
            "held_candidates": held,
        }, fh, ensure_ascii=False, indent=1)

    total = sum(len(f["members"]) for f in sheets)
    print(f"{len(candidates)} candidates from {len(runs)} run(s)")
    print(f"  {len(sheets)} worksheets, {total} senses to author "
          f"(~{total / 290:.1f} ticks)")
    print(f"  {len(held)} held - see {args.record}")
    if dropped:
        print(f"  {dropped} members dropped - sense id absent from the corpus")
    if unjudgeable:
        print(f"  {unjudgeable} members pre-skipped - the gloss is a usage restriction")
    print(f"wrote {args.out}")
    print("Next: one family-author per family reading rubric + worksheet from "
          "disk, then family_merge.py -> tone_lint.py -> family_apply.py.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
