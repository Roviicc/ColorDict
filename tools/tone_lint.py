#!/usr/bin/env python3
"""Flag tone notes that leave the word, per the rule in plan 11.65.

A smoke alarm, not a referee. The rule is "say what the word does; do not say
who uses it or where it came from", and the three shapes that break it leave a
lexical trace. Some flagged notes are fine - *puritanical*'s "now used almost
exclusively as an accusation" trips the distribution rule and was marked right
in audit 001, because it is true. Read what it points at; do not obey it.

Audit 001 measured 44% of tone notes wrong, and the failures fell into a small
number of shapes. Every one of those shapes leaves a lexical trace, so the rule
can be enforced mechanically instead of remembered:

  1. distribution - "usually", "now mostly", "the commonest"
  2. provenance   - "from the Latin", "named for", "originally meant"
  3. restriction  - "only ever", "describes a X, not a Y", "confined to"

Usage:
    python3 tools/tone_lint.py data/families/annotated-*.json
    python3 tools/tone_lint.py --overlay data/entries/overlays/batch-0001.overlay.jsonl
    python3 tools/tone_lint.py --all --quiet      # counts only
"""

import argparse
import glob
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Each rule is (name, compiled pattern, what to do instead).
RULES = [
    ("distribution", re.compile(
        r"\b("
        r"usually|mostly|most often|more often than not|commonest|most common|"
        r"nowadays|these days|now (?:used|said|aimed|mostly|almost|largely|"
        r"chiefly|entirely)|frequently|typically|generally|as a rule|"
        r"rarely used|hardly ever|almost always|almost never|"
        r"far more often|much more often|equally likely|tends to be"
        r")\b", re.I),
     "state what the word does, not how often people do it"),

    ("narrowing", re.compile(
        r"\b("
        r"only ever|only in|only of|never of|never said|not of people|"
        r"describes? (?:a|the) \w+,? not|"
        r"about \w+ rather than \w+,? not|"
        r"exclusively|confined to|restricted to|nothing but"
        r")\b", re.I),
     "do not restrict the sense further than the gloss does"),

    ("etymology", re.compile(
        r"\b("
        r"from the (?:latin|greek|french|old english|german|norse)|"
        r"named (?:for|after)|comes from|derives? from|"
        r"originally (?:meant|a|an|the)|in origin|the root is|"
        r"latin for|greek for|french for"
        r")\b", re.I),
     "drop the origin story unless it has been checked against a source"),

    ("hedge-claim", re.compile(
        r"\b(everyone|nobody|no one|anybody) \w+", re.I),
     "a claim about all speakers is a distribution claim in disguise"),

    ("speaker", re.compile(
        r"\b(?:said|used|aimed|applied|addressed)\s+(?:of|to|at|by)\s+"
        r"(?:the\s+)?(?:elderly|old|young|children|women|men|girls|boys|"
        r"americans|the british|teenagers|adults)\b", re.I),
     "who says a word is not something we can check - describe the word instead"),
]

# Length was tried as a proxy and does not predict failure: the *grudgingly*
# note is long and correct, the *hoggish* note is short and wrong. What predicts
# failure is leaving the word, which the rules above catch directly.



# --- family-inconsistent -----------------------------------------------------
# Census 008 found two notes that were right about their gloss and wrong about
# their family: *ageless* called itself "the one admiring member here" while
# *everlasting* sat beside it at +1. A reader shown one sense at a time cannot
# see that, and packets deliberately withhold the family id so readers cannot
# infer the spectrum instead of reading the gloss. But it needs no reader at
# all - a claim to be the only approving member of a family is checkable
# against the siblings' charges.

UNIQUENESS = re.compile(
    r"\b(?:the\s+(?:one|only|sole)|alone\s+in|uniquely)\b[^.;]{0,60}", re.I)

# The claim has to be ABOUT THE FAMILY. Without this the rule fires on "the one
# stated" and "the one singled out for favor", where "the one" points at the
# referent rather than at a position in the spectrum. Backtesting on every shard
# turned up exactly that: two false positives and one real fault, and requiring
# a family word keeps the fault and drops both.
FAMILY_REF = re.compile(
    r"\b(?:here|member|members|word here|of these|among (?:them|these)|"
    r"in (?:this|the) (?:set|family|group)|of the (?:set|family|group))\b", re.I)

APPROVING = ("admir", "approv", "prais", "warm", "fond", "affection", "flatter",
             "complimentary", "celebrat", "esteem")
DISPARAGING = ("damn", "disparag", "contempt", "scorn", "mock", "deris", "sneer",
               "belittl", "condemn", "withering", "dismissive")


def claimed_polarity(note):
    """+1 / -1 if the note claims to be the family's ONLY approving or
    disparaging member, else None. The claim and the evaluative word have to sit
    in the same clause, so a note that merely uses the word 'admiring' elsewhere
    is not caught."""
    m = UNIQUENESS.search(note or "")
    if not m:
        return None
    span = m.group(0)
    if not FAMILY_REF.search(span):
        return None
    span = span.lower()
    if any(t in span for t in APPROVING):
        return 1
    if any(t in span for t in DISPARAGING):
        return -1
    return None


def family_inconsistent(family):
    """Notes whose uniqueness claim their own siblings contradict."""
    members = [m for m in family.get("members", []) if m.get("tone")]
    pos = sum(1 for m in members if (m.get("charge") or 0) > 0)
    neg = sum(1 for m in members if (m.get("charge") or 0) < 0)
    out = []
    for m in members:
        want = claimed_polarity(m["tone"])
        if want is None:
            continue
        n = pos if want > 0 else neg
        if n > 1:
            side = "approving" if want > 0 else "disparaging"
            others = [x["word"] for x in members
                      if x is not m and ((x.get("charge") or 0) > 0) == (want > 0)
                      and (x.get("charge") or 0) != 0]
            out.append((m["word"], m["tone"],
                        f"claims to be the family's only {side} member, but "
                        f"{n - 1} sibling(s) share that side: {', '.join(others[:4])}"))
    return out


# --- superlative-collision ---------------------------------------------------
# Census 010 found three notes in one family each claiming the mild end of its
# spectrum: *commonplace* "the mildest reproach in the set", *stock* "flattest
# word here", *timeworn* "softer than the rest". At most one can be true. The
# blind reader caught it only because census_packets slices contiguously, so all
# three landed in one packet - the family id is withheld, but a family that fits
# inside a packet is visible anyway. That is luck, not instrument, and a family
# split across a packet boundary would have passed.
#
# This is a contradiction check, not a judgement: it never decides which note is
# right, only that two notes cannot both hold the same end.

SUPERLATIVE = re.compile(r"\b(?:(\w{3,})est|most\s+(\w{3,})|least\s+(\w{3,}))\b", re.I)
COMPARATIVE_ALL = re.compile(
    r"\b(\w{3,})er\b\s+than\s+(?:the\s+(?:rest|others)|any(?:\s+other)?\b|"
    r"all\s+(?:the\s+)?others|its\s+neighbou?rs)", re.I)

# Same discipline as FAMILY_REF above: the claim has to point at this family and
# not at the thing the word is aimed at. "the harshest winter here" is about a
# winter; "the harshest word here" is about the spectrum.
#
# Two axes, not one. Tick 7 shipped this rule knowing only intensity, and
# *perturbing* failed a repair by claiming to be its family's FORMAL member -
# a positional claim the rule was structurally unable to see. Register is the
# other axis notes actually reach for. The axes are kept apart because a
# family's mildest member and its most formal member are different claims, and
# one note may hold either without contradicting the other.
MILD_END = ("mild", "soft", "gent", "faint", "flat", "weak", "light", "plain",
            "quiet", "tame", "neutral", "restrain", "understated", "cool")
STRONG_END = ("strong", "harsh", "sharp", "fierc", "cruel", "bitter", "extreme",
              "contempt", "worst", "sever", "brutal", "savage", "vehement",
              "damning", "ugli", "nasti", "violent")
FORMAL_END = ("formal", "stately", "ceremon", "elevated", "literary", "learned",
              "bookish", "lofty", "dignified", "stiff", "starch", "clinical")
EVERYDAY_END = ("informal", "colloquial", "casual", "slang", "everyday",
                "homely", "chatty", "conversational", "offhand")

END_ROOTS = (("mild", MILD_END), ("strong", STRONG_END),
             ("formal", FORMAL_END), ("everyday", EVERYDAY_END))

# "the least harsh word here" claims the MILD end, not the strong one. The rule
# read `least` as naming the position it modifies and so recorded the exact
# opposite of what the note said - harmless while it only ever compared a claim
# against another claim, and wrong the moment the axis has more than two ends.
OPPOSITE = {"mild": "strong", "strong": "mild",
            "formal": "everyday", "everyday": "formal"}


def _end_of(root):
    root = (root or "").lower()
    for end, roots in END_ROOTS:
        if any(root.startswith(t) for t in roots):
            return end
    return None


def claimed_end(note):
    """The end of its family's spectrum this note claims, or None.

    One of 'mild', 'strong', 'formal', 'everyday' - a position on one of the
    two axes above, not a judgement about whether the claim is true.
    """
    note = note or ""
    for m in COMPARATIVE_ALL.finditer(note):
        end = _end_of(m.group(1))
        if end:
            return end
    for m in SUPERLATIVE.finditer(note):
        span = note[m.start():m.start() + 70]
        if not FAMILY_REF.search(span):
            continue
        end = _end_of(next(g for g in m.groups() if g))
        if end:
            return OPPOSITE[end] if m.group(3) else end
    return None


def superlative_collision(family):
    """Two notes in one family claiming the same end of its spectrum."""
    members = [m for m in family.get("members", []) if m.get("tone")]
    claims = {}
    for m in members:
        end = claimed_end(m["tone"])
        if end:
            claims.setdefault(end, []).append(m)
    out = []
    for end, group in claims.items():
        if len(group) < 2:
            continue
        words = [m["word"] for m in group]
        for m in group:
            others = [w for w in words if w != m["word"]]
            out.append((m["word"], m["tone"],
                        f"claims the {end} end of this family, and so do "
                        f"{len(others)} sibling(s): {', '.join(others[:4])} - "
                        f"at most one of these can hold"))
    return out


def check(note):
    """Return a list of (rule, matched text, advice) for one note."""
    hits = []
    for name, pattern, advice in RULES:
        m = pattern.search(note or "")
        if m:
            hits.append((name, m.group(0), advice))
    return hits


def notes_from_family_file(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    for fam in data["families"]:
        for m in fam["members"]:
            if m.get("tone"):
                yield f"{fam['id']}/{m['word']}", m["tone"]


def family_checks(path):
    """Per-family checks, which the per-note reader cannot see. Only annotated
    family files carry the charges these need; overlays do not."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return
    for fam in data.get("families", []):
        for word, note, advice in family_inconsistent(fam):
            yield f"{fam['id']}/{word}", note, advice, "family-inconsistent"
        for word, note, advice in superlative_collision(fam):
            yield f"{fam['id']}/{word}", note, advice, "superlative-collision"


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
                    yield sense_id, tone


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="*", help="annotated-*.json files")
    ap.add_argument("--overlay", action="append", default=[],
                    help="an overlay .jsonl carrying tone notes")
    ap.add_argument("--all", action="store_true",
                    help="every annotated shard plus batch-0001's overlay")
    ap.add_argument("--quiet", action="store_true", help="counts only")
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

    for path, reader in sources:
        n = bad = 0
        for key, note in reader(path):
            n += 1
            hits = check(note)
            if hits:
                bad += 1
                for rule, _, _ in hits:
                    per_rule[rule] += 1
                if not args.quiet:
                    print(f"\n{Path(path).name}  {key}")
                    print(f"  {note}")
                    for rule, text, advice in hits:
                        print(f"  -> {rule}: {text!r} - {advice}")
        if reader is notes_from_family_file:
            for key, note, advice, rule in family_checks(path):
                bad += 1
                per_rule[rule] += 1
                if not args.quiet:
                    print(f"\n{Path(path).name}  {key}")
                    print(f"  {note}")
                    print(f"  -> {rule}: {advice}")
        total += n
        flagged += bad
        per_source[Path(path).name] = (n, bad)

    print()
    print(f"{'source':34s} {'notes':>6s} {'flagged':>8s} {'rate':>6s}")
    for name, (n, bad) in per_source.items():
        print(f"{name:34s} {n:6d} {bad:8d} {100*bad/n if n else 0:5.0f}%")
    print(f"{'TOTAL':34s} {total:6d} {flagged:8d} {100*flagged/total if total else 0:5.0f}%")
    print()
    for rule, k in per_rule.most_common():
        print(f"  {rule:14s} {k}")

    if args.max_fail is not None and flagged > args.max_fail:
        sys.exit(f"\n{flagged} notes flagged, limit is {args.max_fail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
