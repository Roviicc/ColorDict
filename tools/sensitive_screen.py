#!/usr/bin/env python3
"""Flag families that plan 5.3 reserves for a person, before authoring starts.

5.3 keeps sensitive terms in a manual-only queue and says they are never
auto-drafted. Enforcing that needs someone to notice the family in the draw,
and for three ticks running that noticing was done by an ad-hoc regex typed
fresh each time. It missed every family it mattered for:

- tick 3, `deaf` - caught by reading, not by the screen
- tick 5, `illegitimate` - missed; the screen fired two false positives on the
  word "offensive" in `improper` instead
- tick 6, `noncivilized` - missed; savage, barbarian, barbarous, primitive

Three misses is enough. The term list belongs in a file that accumulates what
each miss taught, the same way the two rubrics do.

**This is a smoke alarm, not a referee.** It has the same standing as
tone_lint.py: it points at families worth a second look, and the draw still has
to be read by someone before authoring starts. Every family it has ever
mattered for was found by that reading first. What the file buys is that a term
which caught us once cannot slip past silently again.

Usage:
    python3 tools/sensitive_screen.py data/families/draft-015.json
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Grouped by what the words are aimed at, because that is what makes the
# judgement sensitive: a spectrum of contempt directed at people for something
# they did not choose.
CATEGORIES = {
    "disability": r"deaf|blind|mute|dumb\b|lame|crippl|handicap|disab|retard|"
                  r"imbecil|feeble-?minded|hearing loss|impair",
    "birth status": r"illegitimat|born out of wedlock|bastard|baseborn|"
                    r"misbegot|conceived in adultery|fatherless|parentage",
    "peoples and culture": r"civiliz|civilis|savage|barbar|primitive|tribal|"
                           r"aborigin|native peoples|heathen|pagan|preliterate|"
                           r"nonliterate",
    "race and ethnicity": r"\brace\b|racial|ethnic|negro|\bwhite\b|\bblack\b|"
                          r"caucasian|oriental|coloured|colored people",
    "religion": r"religio|muslim|moslem|jewish|\bjew\b|christian|hindu|"
                r"infidel|idolat",
    "sexuality and gender": r"homosexual|\bgay\b|lesbian|effeminate|"
                            r"transsexual|transgender|hermaphrodit|sodom",
    "mental health": r"insane|lunatic|madness|mentally ill|deranged|"
                     r"psychopath|moron\b|idiot\b",
    "slur marker": r"offensive term|derogatory term|disparaging|term of abuse|"
                   r"ethnic slur|racial slur",
}
PATTERNS = {name: re.compile(p, re.I) for name, p in CATEGORIES.items()}


def screen(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    flagged = []
    for fam in data.get("families", []):
        hits = {}
        for m in fam.get("members", []):
            text = f"{m.get('word','')} {m.get('_gloss','') or ''}"
            for name, pat in PATTERNS.items():
                found = pat.search(text)
                if found:
                    hits.setdefault(name, []).append((m.get("word"), found.group(0)))
        if hits:
            flagged.append((fam, hits))
    return data, flagged


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("draft", type=Path)
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    data, flagged = screen(args.draft)
    total = len(data.get("families", []))
    for fam, hits in flagged:
        head = ", ".join(fam.get("_head") or []) or fam["id"]
        print(f"\n{fam['id']}  ({head})")
        for name, found in hits.items():
            words = ", ".join(sorted({w for w, _ in found})[:6])
            print(f"  {name}: {words}")

    print(f"\n{len(flagged)} of {total} families flagged for a human look")
    if flagged:
        print("A flag is not a verdict. Read the glosses and decide: withhold to "
              "data/families/held-5.3-<name>.json, or clear it and author.")
    print("Absence of flags is not clearance either - read the draw regardless.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
