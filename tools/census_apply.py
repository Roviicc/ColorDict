#!/usr/bin/env python3
"""Apply the census repair decisions to the annotated family shards.

The census (plan 11.69) produced one decision per flagged sense in
data/policy/census-001-decisions.json. Each is applied to the member that owns
the note - located by word and synset across data/families/annotated-*.json -
so the shard files stay the single source of truth and family_apply.py
regenerates the overlays from them as usual.

Actions:
  tone         replace the tone note (optionally the charge; optionally clear examples)
  charge       change the charge only, the note stands
  skip         decline judgement: "_skip": true with a reason (wrong gloss)
  drop-example remove one example that illustrates a different sense, from the
               worksheet member if it holds one, else from the enrichment overlay
  fix-source   an inherited adverb whose fault lives in the adjective note:
               resolve the adjective sense through the pertainym map and fix it there
  keep / auto-inherit / deny-adverb
               nothing to write here (denials live in adverb-deny.json)

Usage:
    python3 tools/census_apply.py --decisions data/policy/census-001-decisions.json
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OVERLAYS = ROOT / "data/entries/overlays"
sys.path.insert(0, str(ROOT / "tools"))
from adverb_inherit import base_forms  # noqa: E402
from family_apply import slug  # noqa: E402  the one rule that mints a sense id


def load_shards():
    shards = {}
    index = {}
    for path in sorted((ROOT / "data/families").glob("annotated-*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        shards[path] = data
        for family in data["families"]:
            for member in family["members"]:
                # Decisions are keyed by sense id, and slug() is the rule that
                # mints one: it lowercases and drops apostrophes as well as
                # spacing words. A local near-copy that only spaced words could
                # not resolve *ma'am* or any capitalised member, and reported it
                # as unresolved rather than as the bug it was.
                key = (slug(member["word"]), member["synset"])
                index.setdefault(key, (path, member))
    return shards, index


def split_lines(path):
    """Raw lines and the file's newline, so untouched lines pass through whole."""
    raw = path.read_text(encoding="utf-8")
    nl = "\r\n" if "\r\n" in raw else "\n"
    return nl, raw.split(nl)


def load_overlay_examples():
    """Sense id -> (overlay path, line index) for every overlay line with examples.

    A book-drawn sense keeps its examples in the enrichment overlay rather than
    in the family worksheet: the family author writes charge and tone, and the
    Enricher writes the examples. drop-example used to look only at the
    worksheet, where just the 66 gate-drawn members of annotated-001..003 carry
    an examples key, so the action warned and changed nothing on any sense the
    book path produced - and a census re-reading that sense would find the same
    example still sitting under the same gloss.
    """
    index = {}
    for path in sorted(OVERLAYS.glob("*.overlay.jsonl")):
        _, lines = split_lines(path)
        for n, line in enumerate(lines):
            if not line.strip():
                continue
            for sid, sense in (json.loads(line).get("senses") or {}).items():
                if sense.get("examples"):
                    index.setdefault(sid, (path, n))
    return index


def drop_from_overlay(overlays, sense_id, example, dry_run):
    """Remove one example from the overlay that holds it. True if it was there."""
    hit = overlays.get(sense_id)
    if not hit:
        return False
    path, n = hit
    nl, lines = split_lines(path)
    record = json.loads(lines[n])
    sense = record["senses"][sense_id]
    kept = [e for e in sense["examples"] if e != example]
    if len(kept) == len(sense["examples"]):
        return False
    if kept:
        sense["examples"] = kept
    else:
        sense.pop("examples")
    # Only the changed line is re-serialised; every other line is passed through
    # as it was read. The diff is the one sense that moved rather than a reformat
    # of an overlay holding hundreds of entries.
    lines[n] = json.dumps(record, ensure_ascii=False)
    if not dry_run:
        path.write_text(nl.join(lines), encoding="utf-8", newline="")
    print(f"  drop-example {sense_id} -> {path.name}"
          f"{' (dry run)' if dry_run else ''}, {len(kept)} example(s) left")
    return True


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--decisions", type=Path, required=True)
    ap.add_argument("--pertainyms", type=Path,
                    default=ROOT / "data/build/pertainyms.json")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    decisions = json.loads(args.decisions.read_text(encoding="utf-8"))
    pertainyms = json.loads(args.pertainyms.read_text(encoding="utf-8"))
    shards, index = load_shards()
    overlays = load_overlay_examples()
    by_synset = {}
    for (word, synset), (path, member) in index.items():
        by_synset.setdefault(synset, []).append((word, path, member))

    touched = set()
    unresolved = []
    counts = {}

    def find(sense_id):
        word, synset = sense_id.rsplit(".", 1)
        return index.get((word, synset))

    for sense_id, d in decisions.items():
        action = d["action"]
        counts[action] = counts.get(action, 0) + 1
        if action in ("keep", "auto-inherit", "deny-adverb"):
            continue
        if action == "fix-source":
            adverb, adv_synset = sense_id.rsplit(".", 1)
            target = pertainyms.get(adv_synset, {}).get(adverb)
            hit = None
            if target:
                cands = by_synset.get(target, [])
                bases = base_forms(adverb)
                hit = next(((p, m) for w, p, m in cands if w in bases), None) \
                    or (cands[0][1:] if cands else None)
            if not hit:
                for base in base_forms(adverb):
                    hit = next(((p, m) for (w, s), (p, m) in index.items()
                                if w == base and m.get("tone")), None)
                    if hit:
                        break
            if not hit:
                unresolved.append(sense_id)
                continue
            path, member = hit
            member["tone"] = d["tone"]
            touched.add(path)
            print(f"  fix-source {sense_id} -> {member['word']}.{member['synset']}")
            continue

        hit = find(sense_id)
        if not hit:
            if action == "drop-example" and drop_from_overlay(
                    overlays, sense_id, d["example"], args.dry_run):
                continue
            unresolved.append(sense_id)
            continue
        path, member = hit
        if action == "tone":
            member["tone"] = d["tone"]
            if "charge" in d:
                member["charge"] = d["charge"]
            if d.get("clear_examples"):
                member.pop("examples", None)
        elif action == "charge":
            member["charge"] = d["charge"]
        elif action == "skip":
            member["_skip"] = True
            member["_skip_reason"] = d["reason"]
        elif action == "drop-example":
            ex = member.get("examples") or []
            kept = [e for e in ex if e != d["example"]]
            if len(kept) != len(ex):
                member["examples"] = kept
                if not member["examples"]:
                    member.pop("examples")
            else:
                # Not on the worksheet member. Either the Enricher wrote it, or
                # it is not in the corpus at all - and those are different
                # outcomes, so say which.
                if not drop_from_overlay(overlays, sense_id, d["example"],
                                         args.dry_run):
                    print(f"  warning: example not found on {sense_id}: "
                          f"{d['example']!r}")
                continue
        else:
            sys.exit(f"unknown action {action} for {sense_id}")
        touched.add(path)

    for path in sorted(touched):
        if args.dry_run:
            print(f"would write {path.name}")
            continue
        path.write_text(json.dumps(shards[path], ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8", newline="\n")
        print(f"wrote {path.name}")

    print("actions:", ", ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    if unresolved:
        print(f"UNRESOLVED ({len(unresolved)}):")
        for sid in unresolved:
            print("  ", sid)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
