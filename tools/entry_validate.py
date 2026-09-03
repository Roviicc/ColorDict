#!/usr/bin/env python3
"""Stage 0.4: validate entries against the LEXICON, not just against a schema.

`dict_validate.py` checks an entry's shape - required fields, enums, duplicate
ids, the fabrication rule. It is deliberately lexicon-free, so there is one
question it structurally cannot ask: is this entry still true of the WordNet
sense it claims to describe?

That is the question that matters when the lexicon moves under the corpus. Four
checks, each with a specific way of going wrong silently:

  traceable       the synset id resolves in the shipped lexicon. An untraceable
                  sense cannot be re-read, re-measured, or repaired.
  pos-match       the entry's part of speech agrees with the synset's.
  gloss-unchanged the definition is identical to the source gloss. This is the
                  load-bearing one: every tone note in this project was measured
                  against a gloss, and a note under a rewritten gloss is an
                  unmeasured claim wearing a measured one's clothes.
  member          the headword is actually a lemma of that synset.

Plus a sense-count cap, which exists for generated entries: 75 senses (break) is
real WordNet, 200 is a runaway rubric.

Usage:
    python3 tools/entry_validate.py data/entries/derived-bulk.jsonl
    python3 tools/entry_validate.py data/entries/ --lexicon data/source/english-wordnet-2025.xml.gz
"""

import argparse
import gzip
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LEXICON = ROOT / "data/source/english-wordnet-2025.xml.gz"
DEFAULT_CACHE = ROOT / "data/build/lexicon-index.json"

# OEWN part-of-speech codes -> the entry vocabulary. Codes a and s are both
# adjectives (head and satellite); the corpus does not distinguish them.
POS_MAP = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective",
           "r": "adverb"}

# 75 is the observed maximum in the shipped bulk (break). Anything past 80 is a
# generator that has lost the plot, not a word with many meanings.
SENSE_CAP = 80

ERRORS = ("unparsable", "no-synset", "untraceable", "pos-mismatch",
          "gloss-changed", "duplicate-sense-id", "sense-cap")
WARNINGS = ("not-a-member",)


def build_index(lexicon):
    """{synset_id: {definition, pos, members}} from a WN-LMF release.

    clear() is called ONLY on elements we have finished reading whole. Clearing
    a child - <Definition> in particular - wipes its text before the parent
    <Synset> end event fires, and every gloss then compares equal to every other
    because they are all the empty string. That bug produced a perfect
    reconciliation report before it was caught.
    """
    synsets, lemmas = {}, {}
    with gzip.open(lexicon, "rb") as fh:
        for _, el in ET.iterparse(fh, events=("end",)):
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
                    "definition": (d.text or "").strip() if d is not None else "",
                    "pos": el.get("partOfSpeech") or "",
                    "members": (el.get("members") or "").split(),
                }
                el.clear()

    empty = sum(1 for v in synsets.values() if not v["definition"])
    if empty > len(synsets) // 100:
        sys.exit("ABORT: %d/%d definitions parsed empty - every gloss check would "
                 "pass vacuously. Check element clear()." % (empty, len(synsets)))

    for s in synsets.values():
        s["members"] = sorted({lemmas.get(m, m) for m in s["members"]})
    return synsets


def load_index(lexicon, cache, refresh):
    if cache and Path(cache).exists() and not refresh:
        try:
            data = json.loads(Path(cache).read_text(encoding="utf-8"))
            if data.get("lexicon") == str(lexicon):
                return data["synsets"]
        except (OSError, ValueError):
            pass
    print("indexing %s ..." % Path(lexicon).name, file=sys.stderr)
    synsets = build_index(lexicon)
    if cache:
        Path(cache).parent.mkdir(parents=True, exist_ok=True)
        Path(cache).write_text(
            json.dumps({"lexicon": str(lexicon), "synsets": synsets},
                       ensure_ascii=False), encoding="utf-8")
    return synsets


def entry_files(paths):
    for p in paths:
        p = Path(p)
        if p.is_dir():
            yield from sorted(p.rglob("*.jsonl"))
        elif p.exists():
            yield p


def check_sense(word, sense, synsets, counts, samples, seen_ids):
    counts["senses"] += 1
    sid = (sense.get("source") or {}).get("synset")
    if not sid:
        counts["no-synset"] += 1
        samples.setdefault("no-synset", []).append("%s / %s" % (word, sense.get("id")))
        return
    if sense.get("id") in seen_ids:
        counts["duplicate-sense-id"] += 1
        samples.setdefault("duplicate-sense-id", []).append(
            "%s / %s" % (word, sense.get("id")))
    seen_ids.add(sense.get("id"))

    syn = synsets.get(sid)
    if syn is None:
        counts["untraceable"] += 1
        samples.setdefault("untraceable", []).append("%s / %s" % (word, sid))
        return
    if POS_MAP.get(syn["pos"]) != sense.get("part_of_speech"):
        counts["pos-mismatch"] += 1
        samples.setdefault("pos-mismatch", []).append(
            "%s / %s: entry %s vs lexicon %s"
            % (word, sid, sense.get("part_of_speech"), POS_MAP.get(syn["pos"])))
    if (sense.get("definition") or "").strip() != syn["definition"]:
        counts["gloss-changed"] += 1
        samples.setdefault("gloss-changed", []).append(
            "%s / %s\n      entry  : %s\n      lexicon: %s"
            % (word, sid, sense.get("definition"), syn["definition"]))
    if word and word not in syn["members"]:
        counts["not-a-member"] += 1
        samples.setdefault("not-a-member", []).append(
            "%s / %s: members %s" % (word, sid, syn["members"][:6]))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--lexicon", default=str(DEFAULT_LEXICON))
    ap.add_argument("--cache", default=str(DEFAULT_CACHE))
    ap.add_argument("--refresh", action="store_true", help="rebuild the index cache")
    ap.add_argument("--max-senses", type=int, default=SENSE_CAP)
    ap.add_argument("--max-report", type=int, default=10)
    ap.add_argument("--strict", action="store_true", help="warnings fail too")
    args = ap.parse_args()

    synsets = load_index(args.lexicon, args.cache, args.refresh)
    print("lexicon: %d synsets from %s" % (len(synsets), Path(args.lexicon).name))

    counts = Counter()
    samples = {}
    files = list(entry_files(args.paths))
    if not files:
        sys.exit("no .jsonl entry files found")

    for path in files:
        with path.open(encoding="utf-8") as fh:
            for lineno, line in enumerate(fh, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                except ValueError as exc:
                    counts["unparsable"] += 1
                    samples.setdefault("unparsable", []).append(
                        "%s:%d %s" % (path.name, lineno, exc))
                    continue
                counts["entries"] += 1
                word = entry.get("word") or ""
                senses = entry.get("senses") or []
                if len(senses) > args.max_senses:
                    counts["sense-cap"] += 1
                    samples.setdefault("sense-cap", []).append(
                        "%s: %d senses > %d" % (word, len(senses), args.max_senses))
                seen_ids = set()
                for sense in senses:
                    check_sense(word, sense, synsets, counts, samples, seen_ids)

    print("files  : %d" % len(files))
    print("entries: %d   senses: %d" % (counts["entries"], counts["senses"]))
    print("")

    errs = sum(counts[k] for k in ERRORS)
    warns = sum(counts[k] for k in WARNINGS)
    for k in ERRORS + WARNINGS:
        if not counts[k]:
            continue
        kind = "ERROR " if k in ERRORS else "warn  "
        print("  %s %-20s %8d" % (kind, k, counts[k]))
        for line in samples.get(k, [])[:args.max_report]:
            print("      " + line)
        extra = counts[k] - args.max_report
        if extra > 0:
            print("      ... and %d more" % extra)
    if not errs and not warns:
        print("  clean - every sense traceable, POS agrees, gloss unchanged")
    print("")
    print("%d error(s), %d warning(s)" % (errs, warns))
    return 1 if errs or (args.strict and warns) else 0


if __name__ == "__main__":
    sys.exit(main())
