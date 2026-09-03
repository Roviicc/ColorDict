#!/usr/bin/env python3
"""Stage 6: the Resolver. A search term -> every lexical match, with how it matched.

    saw       -> saw NOUN exact; saw VERB exact; see VERB inflected (past tense of)
    emerging  -> emerging ADJ exact; emerge VERB inflected (present participle of)
    better    -> better ADJ/NOUN/VERB exact; good ADJ (comparative of); well (comparative of)
    left      -> left ADJ/NOUN/ADV exact; leave VERB (past tense and past participle of)

This is the reference implementation, run against the same `derived-bulk.jsonl`
the StarDict build ships, so that what it says is what the index can do. The
app does the same thing at lookup time in `engine/Morphology.java`: the .syn
index resolves the form to its headword, and Morphology names the relation
from the two strings. The two rule sets are kept in step by hand; `--check`
here and `MorphologyTest` there assert the same four words.

Each match carries:
    match_type   exact | inflected
    morphology   the relation label ("past tense of"), or null for an exact hit
    pos          the part of speech this branch belongs to

Usage:
    python3 tools/resolve.py saw emerging better left
    python3 tools/resolve.py --check          # the stage-6 done-check
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BULK = ROOT / "data/entries/derived-bulk.jsonl"
sys.path.insert(0, str(ROOT / "tools"))
from wordnet_import import regular_forms  # noqa: E402

POS_CODE = {"noun": "n", "verb": "v", "adjective": "a", "adverb": "r"}

# The irregular forms a reader actually meets. Mirrors Morphology.java; a form
# absent here and not reachable by rule is still a form (OEWN listed it) and is
# labelled "a form of", never guessed.
VERBS = """arise arose arisen|awake awoke awoken|be was been|bear bore borne|beat beat beaten|
become became become|begin began begun|bend bent|bet bet|bind bound|bite bit bitten|bleed bled|
blow blew blown|break broke broken|breed bred|bring brought|build built|burn burnt|burst burst|
buy bought|cast cast|catch caught|choose chose chosen|cling clung|come came come|cost cost|
creep crept|cut cut|deal dealt|dig dug|do did done|draw drew drawn|dream dreamt|
drink drank drunk|drive drove driven|eat ate eaten|fall fell fallen|feed fed|feel felt|
fight fought|find found|flee fled|fling flung|fly flew flown|forbid forbade forbidden|
forget forgot forgotten|forgive forgave forgiven|freeze froze frozen|get got gotten|
give gave given|go went gone|grind ground|grow grew grown|hang hung|have had|hear heard|
hide hid hidden|hit hit|hold held|hurt hurt|keep kept|kneel knelt|know knew known|lay laid|
lead led|lean leant|leap leapt|learn learnt|leave left|lend lent|let let|lie lay lain|
light lit|lose lost|make made|mean meant|meet met|pay paid|put put|quit quit|read read|
ride rode ridden|ring rang rung|rise rose risen|run ran run|say said|see saw seen|
seek sought|sell sold|send sent|set set|shake shook shaken|shed shed|shine shone|
shoot shot|show showed shown|shrink shrank shrunk|shut shut|sing sang sung|sink sank sunk|
sit sat|sleep slept|slide slid|smell smelt|speak spoke spoken|speed sped|spell spelt|
spend spent|spill spilt|spin spun|spit spat|split split|spoil spoilt|spread spread|
spring sprang sprung|stand stood|steal stole stolen|stick stuck|sting stung|
stink stank stunk|strike struck|strive strove striven|swear swore sworn|sweep swept|
swell swelled swollen|swim swam swum|swing swung|take took taken|teach taught|tear tore torn|
tell told|think thought|throw threw thrown|tread trod trodden|understand understood|
wake woke woken|wear wore worn|weave wove woven|weep wept|win won|wind wound|wring wrung|
write wrote written"""
COMPARATIVES = """good better best|well better best|bad worse worst|badly worse worst|
ill worse worst|many more most|much more most|little less least|far farther farthest|
far further furthest|old elder eldest"""
PLURALS = """child children|foot feet|tooth teeth|goose geese|mouse mice|louse lice|man men|
woman women|person people|ox oxen|die dice|penny pence|brother brethren"""

PAST = "past tense of"
PARTICIPLE = "past participle of"
PAST_AND_PARTICIPLE = "past tense and past participle of"
PAST_OR_PARTICIPLE = "past tense or past participle of"
PRESENT_PARTICIPLE = "present participle of"
THIRD_PERSON = "third-person singular of"
PLURAL = "plural of"
COMPARATIVE = "comparative of"
SUPERLATIVE = "superlative of"
FORM_OF = "a form of"


def irregular_table():
    """{(form, head): (pos, label)}"""
    table = {}

    def put(form, head, pos, label):
        table.setdefault((form, head), (pos, label))

    for line in VERBS.replace("\n", "").split("|"):
        p = line.split()
        if len(p) == 2:
            put(p[1], p[0], "verb", PAST_AND_PARTICIPLE)
        elif p[1] == p[2]:
            put(p[1], p[0], "verb", PAST_AND_PARTICIPLE)
        else:
            put(p[1], p[0], "verb", PAST)
            put(p[2], p[0], "verb", PARTICIPLE)
    put("am", "be", "verb", "first-person present of")
    put("is", "be", "verb", THIRD_PERSON)
    put("are", "be", "verb", "present tense of")
    put("were", "be", "verb", PAST)
    put("being", "be", "verb", PRESENT_PARTICIPLE)
    put("has", "have", "verb", THIRD_PERSON)
    put("does", "do", "verb", THIRD_PERSON)
    for line in COMPARATIVES.replace("\n", "").split("|"):
        p = line.split()
        put(p[1], p[0], "adjective", COMPARATIVE)
        put(p[2], p[0], "adjective", SUPERLATIVE)
    for line in PLURALS.replace("\n", "").split("|"):
        p = line.split()
        put(p[1], p[0], "noun", PLURAL)
    return table


IRREGULAR = irregular_table()


def regular_label(form, head, pos):
    """Which regular rule of tools/wordnet_import.py produces `form` from
    `head` in this POS, as a label; None when none does."""
    code = POS_CODE.get(pos)
    if not code or form not in regular_forms(head, code):
        return None
    if pos == "noun":
        return PLURAL
    if pos == "verb":
        if form.endswith("ing"):
            return PRESENT_PARTICIPLE
        if form.endswith("d"):
            return PAST_OR_PARTICIPLE
        return THIRD_PERSON
    if form.endswith("st"):
        return SUPERLATIVE
    return COMPARATIVE


class Resolver:
    def __init__(self, bulk=BULK):
        self.entries = {}          # headword -> {"pos": [..], "forms": [..]}
        self.by_form = {}          # form -> [headword]
        with Path(bulk).open(encoding="utf-8") as fh:
            for line in fh:
                if not line.strip():
                    continue
                e = json.loads(line)
                w = e["word"]
                pos = []
                for s in e["senses"]:
                    if s["part_of_speech"] not in pos:
                        pos.append(s["part_of_speech"])
                forms = list(e.get("inflections") or [])
                self.entries[w.lower()] = {"word": w, "pos": pos, "forms": forms}
                for f in forms:
                    self.by_form.setdefault(f.lower(), []).append(w)

    def resolve(self, term):
        q = term.strip().lower()
        out = []
        exact = self.entries.get(q)
        if exact:
            for pos in exact["pos"]:
                out.append({"headword": exact["word"], "pos": pos,
                            "match_type": "exact", "morphology": None})
        for head in self.by_form.get(q, []):
            entry = self.entries[head.lower()]
            branches = []
            for pos in entry["pos"]:
                hit = IRREGULAR.get((q, head.lower()))
                if hit and hit[0] == pos:
                    branches.append((pos, hit[1]))
                    continue
                label = regular_label(q, head.lower(), pos)
                if label:
                    branches.append((pos, label))
            if not branches:
                # OEWN listed the form; nothing here can say which POS or how.
                branches = [(pos, FORM_OF) for pos in entry["pos"]]
            for pos, label in branches:
                out.append({"headword": entry["word"], "pos": pos,
                            "match_type": "inflected", "morphology": label})
        return out


# The stage-6 done-check: each term must yield exactly these branches.
CHECK = {
    "saw": {("saw", "noun", "exact", None), ("saw", "verb", "exact", None),
            ("see", "verb", "inflected", PAST)},
    "emerging": {("emerging", "adjective", "exact", None),
                 ("emerge", "verb", "inflected", PRESENT_PARTICIPLE)},
    "better": {("better", "adjective", "exact", None), ("better", "noun", "exact", None),
               ("better", "verb", "exact", None), ("better", "adverb", "exact", None),
               ("good", "adjective", "inflected", COMPARATIVE),
               ("well", "adjective", "inflected", COMPARATIVE)},
    "left": {("left", "adjective", "exact", None), ("left", "noun", "exact", None),
             ("left", "adverb", "exact", None),
             ("leave", "verb", "inflected", PAST_AND_PARTICIPLE)},
    "emerged": {("emerge", "verb", "inflected", PAST_OR_PARTICIPLE)},
}


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("terms", nargs="*")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--bulk", default=str(BULK))
    args = ap.parse_args()
    r = Resolver(args.bulk)
    failed = 0
    terms = list(CHECK) if args.check else args.terms
    for t in terms:
        got = r.resolve(t)
        print(t)
        for m in got:
            print(f"   {m['headword']:<12} {m['pos']:<10} {m['match_type']:<10} "
                  f"{m['morphology'] or ''}")
        if args.check:
            have = {(m["headword"], m["pos"], m["match_type"], m["morphology"]) for m in got}
            want = CHECK[t]
            if have != want:
                failed += 1
                for x in sorted(want - have, key=str):
                    print(f"   MISSING  {x}")
                for x in sorted(have - want, key=str):
                    print(f"   EXTRA    {x}")
    if args.check:
        print(f"\n{len(CHECK) - failed}/{len(CHECK)} terms resolve as the plan requires")
        return 1 if failed else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
