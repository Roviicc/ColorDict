#!/usr/bin/env python3
"""Stage 0.2: reconcile every authored sense against a newer OEWN edition.

A tone note is a claim about one sense, and it is only as good as the gloss it
was measured against. When the lexicon moves under the corpus, three things can
go wrong, and only the first is visible without looking:

  vanished     the synset id is gone from the new edition
  moved        the id is gone but the same concept (same ILI) lives at a new id
  gloss changed the id survives and the definition beneath it is different

The third is the dangerous one. Nothing breaks, no validator complains, and the
note now sits under a definition no human ever read it against. Those senses are
queued for a blind re-read rather than shipped (BUILD-PLAN.md stage 0).

Usage:
    python3 tools/oewn_reconcile.py \
        --old data/source/english-wordnet-2024.xml.gz \
        --new data/source/english-wordnet-2025.xml.gz \
        --out data/policy/oewn-2025-reconciliation.json
"""

import argparse
import gzip
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def parse_edition(path):
    """-> ({synset_id: {...}}, {lexentry_id: written_form})

    Streamed, because the uncompressed XML is a few hundred megabytes and this
    has to run on the same laptop as everything else.
    """
    synsets = {}
    lemmas = {}
    with gzip.open(path, "rb") as fh:
        for event, el in ET.iterparse(fh, events=("end",)):
            tag = el.tag.rsplit("}", 1)[-1]
            if tag == "LexicalEntry":
                lem = el.find("./{*}Lemma")
                if lem is None:
                    lem = el.find("./Lemma")
                if lem is not None and el.get("id"):
                    lemmas[el.get("id")] = lem.get("writtenForm")
                el.clear()
            elif tag == "Synset":
                d = el.find("./{*}Definition")
                if d is None:
                    d = el.find("./Definition")
                synsets[el.get("id")] = {
                    "ili": el.get("ili") or "",
                    "definition": (d.text or "").strip() if d is not None else "",
                    "members": (el.get("members") or "").split(),
                    "pos": el.get("partOfSpeech") or "",
                }
                el.clear()
    return synsets, lemmas


def authored_senses(families_dir):
    """Every sense carrying a hand-written tone note, with where it came from."""
    out = []
    for path in sorted(Path(families_dir).glob("annotated-*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            print(f"  ! unreadable {path.name}: {exc}", file=sys.stderr)
            continue
        for fam in data.get("families", []):
            for m in fam.get("members", []):
                if not m.get("tone"):
                    continue
                out.append({
                    "shard": path.name,
                    "family": fam.get("id"),
                    "word": m.get("word"),
                    "synset": m.get("synset"),
                    "charge": m.get("charge"),
                })
    return out


def member_lemmas(entry, lemmas):
    return {lemmas.get(mid, mid) for mid in entry["members"]}


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--old", default="data/source/english-wordnet-2024.xml.gz")
    ap.add_argument("--new", default="data/source/english-wordnet-2025.xml.gz")
    ap.add_argument("--families", default="data/families")
    ap.add_argument("--out", default="data/policy/oewn-2025-reconciliation.json")
    args = ap.parse_args()

    print("reading old edition ...", file=sys.stderr)
    old, old_lem = parse_edition(ROOT / args.old)
    print(f"  {len(old):,} synsets", file=sys.stderr)
    print("reading new edition ...", file=sys.stderr)
    new, new_lem = parse_edition(ROOT / args.new)
    print(f"  {len(new):,} synsets", file=sys.stderr)

    # ILI is the stable cross-edition handle; an id that vanished may simply
    # have been renumbered, and that is a very different problem from a deletion.
    by_ili = defaultdict(list)
    for sid, s in new.items():
        if s["ili"] and s["ili"] != "in":
            by_ili[s["ili"]].append(sid)

    # Built-in control. A reconciler that parses no definitions reports "nothing
    # changed" for every sense, which looks like the best possible news. That is
    # exactly the failure this project has hit three times - a join returning a
    # cleaner number than the truth - so the tool proves it can see change
    # before it is allowed to report an absence of it.
    empty_old = sum(1 for v in old.values() if not v["definition"])
    empty_new = sum(1 for v in new.values() if not v["definition"])
    if empty_old > len(old) // 100 or empty_new > len(new) // 100:
        sys.exit(f"ABORT: definitions did not parse ({empty_old:,}/{len(old):,} old, "
                 f"{empty_new:,}/{len(new):,} new are empty). Every gloss comparison "
                 f"would be '' == '' and every sense would pass. Check element clear().")
    lex_gone = sum(1 for k in old if k not in new)
    lex_changed = sum(1 for k, v in old.items()
                      if k in new and v["definition"] != new[k]["definition"])
    print(f"  control: {lex_gone:,} ids gone, {lex_changed:,} glosses changed "
          f"lexicon-wide", file=sys.stderr)

    senses = authored_senses(ROOT / args.families)
    print(f"{len(senses):,} authored senses", file=sys.stderr)

    verdicts = Counter()
    findings = {"vanished": [], "moved": [], "gloss_changed": [],
                "lemma_dropped": [], "unknown_in_old": []}

    for s in senses:
        sid = s["synset"]
        o = old.get(sid)
        n = new.get(sid)

        if o is None:
            verdicts["unknown_in_old"] += 1
            findings["unknown_in_old"].append(s)
            continue

        if n is None:
            targets = [t for t in by_ili.get(o["ili"], []) if t != sid]
            if targets:
                verdicts["moved"] += 1
                findings["moved"].append(dict(s, ili=o["ili"], new_synset=targets[0],
                                              old_gloss=o["definition"],
                                              new_gloss=new[targets[0]]["definition"]))
            else:
                verdicts["vanished"] += 1
                findings["vanished"].append(dict(s, ili=o["ili"],
                                                 old_gloss=o["definition"]))
            continue

        if o["definition"] != n["definition"]:
            verdicts["gloss_changed"] += 1
            findings["gloss_changed"].append(dict(s, old_gloss=o["definition"],
                                                  new_gloss=n["definition"]))
            continue

        if s["word"] and s["word"] not in member_lemmas(n, new_lem):
            verdicts["lemma_dropped"] += 1
            findings["lemma_dropped"].append(dict(s, gloss=n["definition"],
                                                  members=sorted(member_lemmas(n, new_lem))))
            continue

        verdicts["ok"] += 1

    report = {
        "control": {
            "lexicon_ids_gone": lex_gone,
            "lexicon_glosses_changed": lex_changed,
            "note": "non-zero proves the comparison can see change at all",
        },
        "old_edition": args.old,
        "new_edition": args.new,
        "old_synsets": len(old),
        "new_synsets": len(new),
        "authored_senses": len(senses),
        "verdicts": dict(verdicts),
        "needs_blind_reread": (verdicts["gloss_changed"] + verdicts["moved"]
                               + verdicts["lemma_dropped"]),
        "findings": findings,
    }
    out = ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=1, ensure_ascii=False), encoding="utf-8")

    print("")
    print(f"synsets   : {len(old):,} ({args.old.split('/')[-1]}) -> {len(new):,} ({args.new.split('/')[-1]})")
    print(f"authored  : {len(senses):,} senses carrying a tone note")
    print(f"control   : {lex_gone:,} ids gone and {lex_changed:,} glosses changed "
          f"lexicon-wide, so a clean result below is a measurement")
    for k in ("ok", "gloss_changed", "moved", "vanished", "lemma_dropped",
              "unknown_in_old"):
        if verdicts.get(k):
            print(f"  {k:<15} {verdicts[k]:>6,}")
    print("")
    print(f"NEEDS BLIND RE-READ: {report['needs_blind_reread']:,}")
    print(f"report -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
