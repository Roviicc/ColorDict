#!/usr/bin/env python3
"""Flag tone notes a learner cannot easily read - the plain-words rule, stage 9b.

tone_lint.py asks whether a note leaves the word. This asks a different
question: whether the person the note is for can read it. A note can be true,
pass the census, and still open "Convicts the thing of mileage rather than
damage" and run to 38 words. The census reader measures truth; nothing measured
ease until this, which is how the notes drifted from a median of about 12 words
(shards 001-009) to about 20 (010-018) without anyone deciding they should.

tone_lint's own header records that length does not predict a WRONG note. That
finding stands: this is not a correctness check. Length and rare words predict
a HARD note, which is a separate failure with a separate cost.

The rule was chosen by the author on 2026-09-26: at most 24 words, no minimum.
A 16-24 band was considered and refused - 1,181 notes (34%) are under 16 words,
the short ones are among the clearest in the corpus, and a floor would pad
them, and padding adds claims.

  too-long    more than 24 words. The one hard limit: a tick checks its new
              shard with --max-long 0.
  hard-words  two or more words a learner is unlikely to know, not counting
              the headword or any *neighbour* the note names. "Unlikely to
              know" is a Zipf frequency under 3.0 - about once per million
              words - in wordfreq's English list, taken on the word's plainest
              base form, so *plainest* counts as *plain* and *weightier* as
              *weighty*. One rare word is often the exact word a note needs;
              two in one sentence is where a reader loses the thread.
  grammar     a grammar or linguistics term the author thinks in and the
              reader should never have to: participle, superlative, negation,
              register, gloss... Everyday grammar a learner is taught early
              (noun, verb, spelling) is left alone.

too-long is a gate. hard-words and grammar are smoke alarms, like everything
in tone_lint: read what they point at, do not obey them.

Usage:
    python3 tools/plain_lint.py --all                  # every shard + batch-0001's overlay
    python3 tools/plain_lint.py --all --quiet          # counts only
    python3 tools/plain_lint.py data/families/annotated-019.json --max-long 0
    python3 tools/plain_lint.py --all --json flagged.json

hard-words needs `pip install wordfreq`. Without it that rule is skipped and
the run says so; too-long and grammar still run.
"""

import argparse
import glob
import json
import re
import sys
from collections import Counter
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from family_apply import slug  # noqa: E402  the one rule that mints a sense id

try:
    from wordfreq import zipf_frequency
except ImportError:  # the rule degrades; the other two do not need it
    zipf_frequency = None

MAX_WORDS = 24
RARE_ZIPF = 3.0
RARE_LIMIT = 2

WORD = re.compile(r"[A-Za-z][A-Za-z'’-]*")
NEIGHBOUR = re.compile(r"\*([^*]+)\*")

# Counted on the corpus before the list was fixed: *spelling* (59 notes),
# *verb*, *noun*, *adjective*, *metaphor*, *literal* are school English and
# stay out. What is in is what a learner would have to look up.
GRAMMAR = ("register", "gloss", "superlative", "comparative", "participle",
           "participial", "negation", "evaluative", "euphemism", "euphemistic",
           "intensifier", "prefix", "suffix", "adjectival", "adverbial",
           "diminutive", "pejorative", "collocation", "lemma", "synset",
           "hypernym", "hyponym", "denotation")
GRAMMAR_RE = re.compile(r"\b(" + "|".join(GRAMMAR) + r")s?\b", re.I)

# Inflections a learner reads through: the base form is the word they know.
SUFFIXES = (("iness", "y"), ("iest", "y"), ("ier", "y"), ("ily", "y"), ("est", ""), ("er", ""),
            ("est", "e"), ("er", "e"), ("ly", ""), ("ness", ""), ("es", ""),
            ("s", ""), ("ed", ""), ("ed", "e"), ("ing", ""), ("ing", "e"))


def words(note):
    return WORD.findall(note or "")


def word_count(note):
    return len(words(note))


# Prefixes a learner reads through the same way: *unkind* is *kind*, *underfed*
# is *fed*. Only these four - "in-" would turn *insipid* into *sipid*.
PREFIXES = ("under", "over", "non", "un")


def _bases(w):
    stems = {w}
    for prefix in PREFIXES:
        if w.startswith(prefix) and len(w) - len(prefix) >= 3:
            stems.add(w[len(prefix):])
            break
    out = set(stems)
    for stem in stems:
        for suffix, repl in SUFFIXES:
            if stem.endswith(suffix) and len(stem) - len(suffix) >= 3:
                base = stem[:-len(suffix)] + repl
                out.add(base)
                if base[-1:] == base[-2:-1]:  # flattest -> flatt -> flat
                    out.add(base[:-1])
    return out


@lru_cache(maxsize=None)
def is_rare(w):
    parts = [p for p in w.split("-") if p]
    return bool(parts) and min(
        max(zipf_frequency(b, "en") for b in _bases(p)) for p in parts) < RARE_ZIPF


def hard_words(note, head):
    """Rare words in the note, minus the headword and named neighbours."""
    if zipf_frequency is None:
        return []
    exempt = {w.lower() for w in words(head)}
    for span in NEIGHBOUR.findall(note or ""):
        exempt |= {w.lower() for w in words(span)}
    # A form of the word being looked up is not a new word to the person
    # looking it up: *froth* in the note on *frothy*.
    stems = {w[:5] for w in exempt if len(w) >= 5}
    out = []
    for w in words(NEIGHBOUR.sub(" ", note or "")):
        lw = w.lower().strip("'’-")
        if (lw and lw not in exempt and lw[:5] not in stems and lw not in out
                and is_rare(lw)):
            out.append(lw)
    return out


def check(note, head=""):
    """Return (rule, detail) pairs for one note; empty means it reads plainly."""
    hits = []
    n = word_count(note)
    if n > MAX_WORDS:
        hits.append(("too-long", f"{n} words"))
    rare = hard_words(note, head)
    if len(rare) >= RARE_LIMIT:
        hits.append(("hard-words", ", ".join(rare)))
    terms = sorted({m.group(1).lower() for m in GRAMMAR_RE.finditer(note or "")})
    if terms:
        hits.append(("grammar", ", ".join(terms)))
    return hits


def notes_from_family_file(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    for fam in data["families"]:
        for m in fam["members"]:
            if m.get("tone") and not m.get("_skip"):
                yield {"family": fam["id"], "word": m["word"],
                       "synset": m["synset"],
                       "sense_id": f"{slug(m['word'])}.{m['synset']}",
                       "head": m["word"], "tone": m["tone"]}


def notes_from_overlay(path):
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            for sense_id, patch in (entry.get("senses") or {}).items():
                tone = (patch or {}).get("tone")
                if tone:
                    head = sense_id.split(".")[0].replace("_", " ")
                    yield {"family": None, "word": head, "synset": None,
                           "sense_id": sense_id, "head": head, "tone": tone}


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="*", help="annotated-*.json files")
    ap.add_argument("--overlay", action="append", default=[],
                    help="an overlay .jsonl carrying tone notes")
    ap.add_argument("--all", action="store_true",
                    help="every annotated shard plus batch-0001's overlay")
    ap.add_argument("--quiet", action="store_true", help="counts only")
    ap.add_argument("--json", type=Path, default=None,
                    help="write every flagged note here, for plain_packets.py")
    ap.add_argument("--max-long", type=int, default=None,
                    help="exit non-zero if more than this many notes are too long")
    ap.add_argument("--max-fail", type=int, default=None,
                    help="exit non-zero if more than this many notes are flagged")
    args = ap.parse_args()

    paths, overlays = list(args.paths), list(args.overlay)
    if args.all:
        paths += sorted(glob.glob(str(ROOT / "data/families/annotated-*.json")))
        overlays.append(str(ROOT / "data/entries/overlays/batch-0001.overlay.jsonl"))
    sources = [(p, notes_from_family_file) for p in paths] + \
              [(p, notes_from_overlay) for p in overlays]
    if not sources:
        sys.exit("nothing to check - pass files, --overlay, or --all")

    total = flagged = 0
    per_rule = Counter()
    per_source = {}
    lengths = []
    records = []

    for path, reader in sources:
        n = bad = 0
        for note in reader(path):
            n += 1
            lengths.append(word_count(note["tone"]))
            hits = check(note["tone"], note["head"])
            if not hits:
                continue
            bad += 1
            for rule, _ in hits:
                per_rule[rule] += 1
            records.append({"source": Path(path).name, **{k: note[k] for k in (
                "family", "word", "synset", "sense_id", "tone")},
                "words": word_count(note["tone"]),
                "rules": {rule: detail for rule, detail in hits}})
            if not args.quiet:
                print(f"\n{Path(path).name}  {note['family'] or ''}/{note['word']}")
                print(f"  {note['tone']}")
                for rule, detail in hits:
                    print(f"  -> {rule}: {detail}")
        total += n
        flagged += bad
        per_source[Path(path).name] = (n, bad)

    print()
    print(f"{'source':34s} {'notes':>6s} {'flagged':>8s} {'rate':>6s}")
    for name, (n, bad) in per_source.items():
        print(f"{name:34s} {n:6d} {bad:8d} {100*bad/n if n else 0:5.0f}%")
    print(f"{'TOTAL':34s} {total:6d} {flagged:8d} {100*flagged/total if total else 0:5.0f}%")
    print()
    for rule in ("too-long", "hard-words", "grammar"):
        print(f"  {rule:14s} {per_rule[rule]}")
    if lengths:
        ordered = sorted(lengths)
        print(f"  {'words':14s} median {ordered[len(ordered) // 2]}, "
              f"longest {ordered[-1]}, limit {MAX_WORDS}")
    if zipf_frequency is None:
        print("  hard-words     SKIPPED - pip install wordfreq")

    if args.json:
        args.json.write_text(json.dumps(records, indent=1, ensure_ascii=False) + "\n",
                             encoding="utf-8")
        print(f"\n{len(records)} flagged notes -> {args.json}")

    if args.max_long is not None and per_rule["too-long"] > args.max_long:
        sys.exit(f"\n{per_rule['too-long']} notes over {MAX_WORDS} words, "
                 f"limit is {args.max_long}")
    if args.max_fail is not None and flagged > args.max_fail:
        sys.exit(f"\n{flagged} notes flagged, limit is {args.max_fail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
