#!/usr/bin/env python3
"""The join: turn Enricher candidates into book-restricted family worksheets.

The Enricher answers one question per sense - does this sense judge what it is
aimed at? - and returns either `null` or `{"candidate": true, "why": ...}`. A
candidate is a handoff: the Enricher never writes charge or tone, so the sense
is supposed to go to the family path where a family-author writes them against
its neighbours.

Nothing implemented that handoff. enrich_validate.py writes `label: "neutral"`
for a null and writes nothing at all for a candidate, and the `why` lands in the
run's results.json where no tool reads it. 121 senses across four runs sat in
that gap from enrich-001, invisible to every instrument: a blind reader shown a
card with no tone note has nothing to mark wrong.

This closes it. Routing a candidate into its family naively is unaffordable,
because the two halves of the pipeline work at different grains. The Enricher
works per word-sense; the family path works per family; and OEWN noun families
are hypernym trees rather than connotation sets - `woman` carries 603 members.
Sending the 121 to their smallest containing families whole means authoring
3,958 member senses, more than the entire corpus built so far.

Two restrictions make it affordable. Keep only members the ingested books use,
keyed on (lemma, part of speech) so *bad* the noun does not borrow *bad* the
adjective's frequency; then cap what is left (default 20). Candidates that fit
no family inside that window are HELD and named in the record file with the
sizes that excluded them, never dropped - holding is a decision someone can
revisit, dropping is the fault this script exists to fix.

Four guards, each added because a blind audit of the first version found the
hole (2026-09-05):

  - a family already authored in data/families/annotated-*.json is not drawn
    again; its candidates are held and say so
  - a member that already carries a censused tone note is left out
  - a sense that lands in two worksheets is kept in one - the one it is a
    candidate for, else the first - because family_apply.py writes the last
    author's patch over the first with no warning
  - when a word appears in a family under two synsets, the candidate's synset
    is the row that survives the per-word dedup, so a candidate cannot be
    counted as routed while its own sense is missing from the sheet

No `anchors` key is written. family_worksheet.py emits the six most prominent
words as the spectrum; here that would be the six words three novels use most,
frozen per sense into the rendered table - the product - and a different six
for every book set. Without the key, family_apply.build_spectrum() shows one
word per distinct charge, which spans the axis instead, and refuses a family
whose charges are all equal, which is the right refusal.

The Enricher's `why` deliberately does NOT go into the worksheet. family-author
is told the gloss is its entire evidence; a second source of evidence beside it
invites notes that agree with the why rather than the gloss, the largest fault
class in every census. The whys go to the record file, which doubles as the
demand log stage 8 said did not exist.

Numbers are not repeated in this docstring. The first version's were stale the
same day, which is what the record file is for.

Usage:
    python3 tools/candidate_families.py --out data/families/draft-018.json \\
        --record data/policy/stage9-candidates.json
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

BOOK_POS = {"ADJ": "adjective", "ADV": "adverb", "NOUN": "noun", "VERB": "verb"}
SYNSET_POS = {"a": "adjective", "s": "adjective", "n": "noun", "v": "verb",
              "r": "adverb"}
FAMILY_FILES = ("adjective-families.json", "verb-families.json",
                "noun-families.json")


def pos_of(synset):
    return SYNSET_POS.get(synset.rsplit("-", 1)[-1])


def load_books(books_dir):
    """(lemma, pos) -> total occurrences across every ingested book."""
    freq = collections.Counter()
    for path in sorted(books_dir.glob("*/lemmas.jsonl")):
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                rec = json.loads(line)
                pos = BOOK_POS.get(rec["part_of_speech"])
                if pos:
                    freq[(rec["lemma"].lower(), pos)] += rec["corpus"]["total_occurrences"]
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


def load_annotated(families_dir):
    """What the connotation lane has already written: toned (word, synset)
    pairs, and the family ids that own a shard."""
    toned, owned = set(), {}
    for path in sorted(families_dir.glob("annotated-*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for fam in data.get("families", []):
            owned[fam["id"]] = path.name
            for m in fam["members"]:
                if m.get("tone"):
                    toned.add((m["word"].lower(), m["synset"]))
    return toned, owned


def book_view(family, freq, prefer=frozenset()):
    """The family as the books see it: members whose (word, pos) occurs in a
    book, one row per word, most frequent first.

    When one word sits in the family under two synsets the row kept is the
    one in `prefer` if any, else the first. Frequency order matters because
    the cap truncates: the words a reader meets most are the ones to keep."""
    rows = {}
    for m in family["members"]:
        key = (m["word"].lower(), pos_of(m["synset"]))
        if key not in freq:
            continue
        word = m["word"].lower()
        if word not in rows or (m["synset"] in prefer and rows[word]["synset"] not in prefer):
            rows[word] = m
    return sorted(rows.values(), key=lambda m: -freq[(m["word"].lower(), pos_of(m["synset"]))])


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--policy", type=Path, default=ROOT / "data/policy",
                    help="scanned for */results.json")
    ap.add_argument("--run", action="append", default=[],
                    help="limit to these run directory names; repeatable")
    ap.add_argument("--books", type=Path, default=ROOT / "data/build/books")
    ap.add_argument("--build", type=Path, default=ROOT / "data/build")
    ap.add_argument("--bulk", type=Path, default=ROOT / "data/entries/derived-bulk.jsonl")
    ap.add_argument("--families", type=Path, default=ROOT / "data/families",
                    help="annotated-*.json shards; already-authored work is not redrawn")
    ap.add_argument("--cap", type=int, default=20,
                    help="maximum members per worksheet (default 20)")
    ap.add_argument("--min-members", type=int, default=2,
                    help="fewer annotatable members than this is no contrast to write")
    ap.add_argument("--out", type=Path, required=True, help="the worksheet")
    ap.add_argument("--record", type=Path, required=True,
                    help="routing record and demand log: where every candidate went, "
                         "and the sizes that held the ones that did not")
    args = ap.parse_args()

    runs = sorted(p for p in args.policy.iterdir() if p.is_dir())
    if args.run:
        runs = [p for p in runs if p.name in set(args.run)]
    candidates = load_candidates(runs)
    if not candidates:
        print("no candidates found - nothing to route")
        return 1

    freq = load_books(args.books)
    toned, owned = load_annotated(args.families)
    families, index = {}, collections.defaultdict(list)
    for name in FAMILY_FILES:
        for fam in json.loads((args.build / name).read_text(encoding="utf-8"))["families"]:
            families[fam["id"]] = fam
            for m in fam["members"]:
                index[m["synset"]].append(fam["id"])

    # The size of a family, as the books see it, is its distinct in-book words.
    # Cached: `woman` is consulted once per candidate it contains, and the
    # book view of a 603-member family is the whole runtime.
    size_cache = {}

    def book_size(fid):
        if fid not in size_cache:
            size_cache[fid] = len(book_view(families[fid], freq))
        return size_cache[fid]

    def in_books(cand):
        return (cand["word"].lower(), cand["pos"]) in freq

    # Route each candidate to the SMALLEST book-restricted family that contains
    # its synset and fits the window. Smallest wins because a tight family is
    # where contrast is real: `lady` among 44 in-book members of `woman` is a
    # spectrum; among 603 it is a filing system. A family the connotation lane
    # has already authored is never redrawn - two authors on one family id is
    # the between-sense fault class the corpus has paid for twice.
    routed, held = collections.defaultdict(list), []

    def hold(cand, why):
        held.append({**cand, "held_because": why})

    for cand in candidates:
        fids = index.get(cand["synset"], [])
        if not fids:
            hold(cand, "synset is in no family")
            continue
        if not in_books(cand):
            hold(cand, f"{cand['word']}/{cand['pos']} does not occur in any ingested book")
            continue
        sizes = {fid: book_size(fid) for fid in fids}
        fits = sorted((n, fid) for fid, n in sizes.items()
                      if args.min_members <= n <= args.cap)
        if not fits:
            lo, hi = min(sizes.values()), max(sizes.values())
            if hi < args.min_members:
                hold(cand, f"largest book-restricted family has {hi} member(s) "
                           f"(min {args.min_members})")
            elif lo > args.cap:
                hold(cand, f"smallest book-restricted family is {lo} members (cap {args.cap})")
            else:
                hold(cand, f"book-restricted families are {sorted(sizes.values())} members - "
                           f"none within {args.min_members}-{args.cap}")
            continue
        already = [fid for _, fid in fits if fid.replace("oewn-", "family-") in owned]
        if len(already) == len(fits):
            hold(cand, f"every fitting family is already authored: "
                       + ", ".join(f"{fid} in {owned[fid.replace('oewn-', 'family-')]}"
                                   for fid in already))
            continue
        fid = next(fid for _, fid in fits if fid not in already)
        routed[fid].append(cand)

    glosses = load_corpus_glosses(args.bulk)
    sheets, dropped, unjudgeable, already_toned = [], 0, 0, []
    owner_of = {}   # (word, synset) -> family id that keeps the row

    for fid, cands in sorted(routed.items(), key=lambda kv: -len(kv[1])):
        family = families[fid]
        wanted = {c["synset"] for c in cands}
        # Candidate senses lead: they are why the sheet exists, and the cap
        # must never truncate them away.
        pool = book_view(family, freq, prefer=frozenset(wanted))
        pool = ([m for m in pool if m["synset"] in wanted]
                + [m for m in pool if m["synset"] not in wanted])

        members = []
        for m in pool:
            key = (m["word"].lower(), m["synset"])
            sense_id = f"{slug(m['word'])}.{m['synset']}"
            if sense_id not in glosses:
                dropped += 1
                continue
            if key in toned:
                already_toned.append(f"{m['word']}.{m['synset']}")
                continue
            if key in owner_of and m["synset"] not in wanted:
                continue    # another sheet already carries this sense
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
            for c in cands:
                hold(c, f"family {fid} has {len(annotatable)} annotatable member(s) "
                        f"after guards (min {args.min_members})")
            continue
        # A candidate whose own sense fell out (gloss absent, already toned)
        # is not routed just because its family shipped.
        present = {m["synset"] for m in members}
        for c in cands:
            if c["synset"] in present:
                continue
            rival = next((m for m in members if m["word"].lower() == c["word"].lower()
                          and m["synset"] in wanted), None)
            if rival:
                # One row per word is the worksheet's contract - family_apply
                # keys the spectrum on word - so a word that is a candidate
                # under two synsets of one family can carry only one of them.
                hold(c, f"{c['word']} is also a candidate under {rival['synset']} in "
                        f"{fid}, and a worksheet carries one row per word; that sense "
                        f"took the row")
            else:
                hold(c, f"own sense absent from the {fid} worksheet after guards")
        cands[:] = [c for c in cands if c["synset"] in present]
        if not cands:
            continue
        for m in members:
            owner_of[(m["word"].lower(), m["synset"])] = fid
        sheets.append({
            "id": fid.replace("oewn-", "family-"),
            "axis": "condemning → praising",
            "_head": family["head_words"][:4],
            "_definition": family["definition"][:100],
            "members": members,
        })

    # A sense in two sheets: the second pass above skipped non-candidate
    # repeats, but a sense can be a candidate in one sheet and a plain member
    # of an earlier one. Resolve in favour of the candidate's sheet.
    seen = collections.defaultdict(list)
    for sh in sheets:
        for m in sh["members"]:
            seen[(m["word"].lower(), m["synset"])].append(sh["id"])
    dup_resolved = 0
    for key, ids in seen.items():
        if len(ids) < 2:
            continue
        fid_cands = {fid.replace("oewn-", "family-") for fid, cs in routed.items()
                     for c in cs if c["synset"] == key[1]}
        keep = next((i for i in ids if i in fid_cands), ids[0])
        for sh in sheets:
            if sh["id"] != keep:
                before = len(sh["members"])
                sh["members"] = [m for m in sh["members"]
                                 if (m["word"].lower(), m["synset"]) != key]
                dup_resolved += before - len(sh["members"])
    sheets = [sh for sh in sheets
              if len([m for m in sh["members"] if not m.get("_skip")]) >= args.min_members]
    kept_ids = {sh["id"] for sh in sheets}
    routed = {fid: cs for fid, cs in routed.items()
              if fid.replace("oewn-", "family-") in kept_ids and cs}

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump({"batch": args.out.stem, "families": sheets}, fh,
                  ensure_ascii=False, indent=1)

    total = sum(len(sh["members"]) for sh in sheets)
    by_pos = collections.Counter(pos_of(m["synset"]) for sh in sheets for m in sh["members"])
    routed_n = sum(len(v) for v in routed.values())
    args.record.parent.mkdir(parents=True, exist_ok=True)
    with args.record.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump({
            "cap": args.cap, "min_members": args.min_members,
            "runs": [r.name for r in runs],
            "candidates": len(candidates), "routed": routed_n, "held": len(held),
            "worksheets": len(sheets), "senses_to_author": total,
            "senses_by_pos": dict(by_pos),
            "guards": {"members_already_toned_left_out": sorted(set(already_toned)),
                       "duplicate_rows_resolved": dup_resolved,
                       "members_dropped_no_gloss": dropped,
                       "members_pre_skipped_undefinable": unjudgeable},
            "routing": {fid: [{"word": c["word"], "pos": c["pos"], "synset": c["synset"],
                               "why": c["why"], "run": c["run"]} for c in cs]
                        for fid, cs in routed.items()},
            "held_candidates": held,
        }, fh, ensure_ascii=False, indent=1)

    print(f"{len(candidates)} candidates from {len(runs)} run(s)")
    print(f"  {routed_n} routed into {len(sheets)} worksheets, {total} senses to author")
    print(f"     ~{total / 290:.1f} ticks of senses, ~{len(sheets) / 25:.1f} ticks of dispatch "
          f"(a tick is ~25 families / ~290 senses)")
    print(f"     by part of speech: " + ", ".join(f"{p} {n}" for p, n in by_pos.most_common()))
    print(f"  {len(held)} held - every one named with its reason in {args.record}")
    if already_toned:
        print(f"  {len(set(already_toned))} members left out - already carry a censused tone note")
    if dup_resolved:
        print(f"  {dup_resolved} duplicate row(s) removed - a sense was in two worksheets")
    if dropped:
        print(f"  {dropped} members dropped - sense id absent from the corpus")
    if unjudgeable:
        print(f"  {unjudgeable} members pre-skipped - the gloss is a usage restriction")
    print(f"wrote {args.out}")
    print("Next: one family-author per family reading rubric + worksheet from disk, "
          "then family_merge.py -> tone_lint.py -> family_apply.py. Census the result "
          "stratified by part of speech - the baseline is adjectives, this draw is not.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
