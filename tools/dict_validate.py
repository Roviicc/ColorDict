#!/usr/bin/env python3
"""Validate Pop Up English Dictionary JSONL entry files (docs/DICTIONARY-PLAN.md section 8).

Stdlib only, like the rest of tools/. tools/dict_schema.json documents the
contract; this file is the enforced implementation.

Per entry: required fields and types, POS and connotation enums, label/score
agreement, the fabrication rule (neutral senses carry no explanation), example
hygiene, synonym/antonym sanity, affix formatting, sources for reviewed+,
sense-id format.

Per corpus: duplicate headwords, duplicate sense ids, duplicate/near-duplicate
definitions within an entry, near-duplicate definitions across reviewed+
entries, dangling synonym/antonym targets (reported as info — batch files
legitimately reference words that live in other files).

Severity: errors fail the run (exit 1); warnings pass unless --strict.

Usage:
    python3 tools/dict_validate.py data/entries/ [more files/dirs...] [--strict] [--max-report N]
"""

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

POS_VALUES = {
    "noun", "verb", "adjective", "adverb", "pronoun", "determiner",
    "preposition", "conjunction", "interjection", "numeral",
    "particle", "abbreviation", "phrase",
}
CONNOTATION_LABELS = {"positive", "negative", "neutral"}
USAGE_LABELS = {
    "informal", "formal", "slang", "vulgar", "derogatory", "offensive",
    "humorous", "archaic", "dated", "literary", "technical", "dialect",
    "euphemistic", "ironic", "clinical", "poetic", "regional",
}
STATUS_VALUES = {"derived", "reviewed", "curated"}
STATUS_RANK = {"derived": 0, "reviewed": 1, "curated": 2}

SENSE_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_'.\-]*$")
PREFIX_RE = re.compile(r"^[a-z]+-$")
SUFFIX_RE = re.compile(r"^-[a-z]+$")
WORD_RE = re.compile(r"^[A-Za-z][A-Za-z' -]*$|^[aI]$")
TOKEN_RE = re.compile(r"[a-z']+")

# Label thresholds must match tools/wordnet_import.py.
SCORE_POSITIVE = 0.25
SCORE_NEGATIVE = -0.25
NEAR_DUP_JACCARD = 0.8


class Report:
    def __init__(self, max_report):
        self.errors = []
        self.warnings = []
        self.infos = []
        self.max_report = max_report

    def error(self, where, msg):
        self.errors.append(f"{where}: {msg}")

    def warn(self, where, msg):
        self.warnings.append(f"{where}: {msg}")

    def info(self, msg):
        self.infos.append(msg)

    def dump(self):
        for label, items in (("ERROR", self.errors), ("WARN", self.warnings)):
            shown = items[: self.max_report]
            for line in shown:
                print(f"{label}  {line}")
            if len(items) > len(shown):
                print(f"{label}  ... and {len(items) - len(shown)} more")
        for line in self.infos:
            print(f"info   {line}")


def tokens(text):
    return set(TOKEN_RE.findall(text.lower()))


def jaccard(a, b):
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def headword_stem(word):
    w = word.lower()
    if len(w) > 3 and w[-1] in ("e", "y"):
        w = w[:-1]
    return w


# Bare substring containment was the first version and it passed the worst
# cases silently: "pellucid prose" counts as using *lucid*, "the burglar carried
# his loot in a pillowcase" as using *case*. Census 010 found two of these by
# reading - the reader could see that the example illustrated a sibling lemma
# and not the headword - after 4,610 example warnings had failed to name them.
# The match has to sit on a word boundary. Hyphens are part of a word here, so
# *broken-down* still matches "a broken-down fence" and *case* no longer matches
# "encased".
def _at_word_boundary(candidate, example):
    return re.search(r"(?<![A-Za-z])" + re.escape(candidate) + r"(?![A-Za-z])",
                     example) is not None


def example_mentions(word, inflections, example):
    """True when the example plausibly uses the headword or an inflection."""
    ex = example.lower()
    for cand in [word.lower()] + [i.lower() for i in inflections]:
        if cand and _at_word_boundary(cand, ex):
            return True
    wl = word.lower()
    if " " in wl:
        return False
    stem = headword_stem(wl)
    if len(stem) < 3:
        return False
    return any(t.startswith(stem) for t in re.split(r"[^A-Za-z-]+", ex))


def check_str(rep, where, obj, key, required=True, allow_null=False):
    if key not in obj or obj[key] is None:
        if required and not allow_null:
            rep.error(where, f"missing required field '{key}'")
        return None
    v = obj[key]
    if not isinstance(v, str) or not v.strip():
        rep.error(where, f"'{key}' must be a non-empty string")
        return None
    return v


def check_connotation(rep, where, sense):
    conn = sense.get("connotation")
    if not isinstance(conn, dict):
        rep.error(where, "missing 'connotation' object")
        return
    unknown = set(conn) - {"label", "score", "explanation", "usage_labels", "tone"}
    if unknown:
        rep.error(where, f"connotation has unknown fields {sorted(unknown)}")
    label = conn.get("label")
    if label not in CONNOTATION_LABELS:
        rep.error(where, f"connotation label {label!r} not in {sorted(CONNOTATION_LABELS)}")
        return
    score = conn.get("score")
    if score is not None:
        if not isinstance(score, (int, float)) or not (-1 <= score <= 1):
            rep.error(where, f"connotation score {score!r} not a number in [-1, 1]")
        else:
            expect = ("positive" if score >= SCORE_POSITIVE
                      else "negative" if score <= SCORE_NEGATIVE else "neutral")
            if label != expect:
                rep.error(where, f"label {label!r} disagrees with score {score} (expect {expect!r})")
    explanation = conn.get("explanation")
    if explanation is not None and (not isinstance(explanation, str) or not explanation.strip()):
        rep.error(where, "connotation explanation must be null or a non-empty string")
    elif explanation and label == "neutral":
        rep.error(where, "fabrication rule: a neutral sense must not carry an explanation")
    tone = conn.get("tone")
    if tone is not None and (not isinstance(tone, str) or not tone.strip()):
        rep.error(where, "connotation tone must be null or a non-empty string")
    usage = conn.get("usage_labels", [])
    if not isinstance(usage, list):
        rep.error(where, "usage_labels must be an array")
    else:
        for u in usage:
            if u not in USAGE_LABELS:
                rep.error(where, f"unknown usage label {u!r}")


def check_word_list(rep, where, sense, key, word):
    values = sense.get(key, [])
    if not isinstance(values, list):
        rep.error(where, f"'{key}' must be an array")
        return []
    out = []
    seen = set()
    for v in values:
        if not isinstance(v, str) or not v.strip():
            rep.error(where, f"'{key}' contains an empty value")
            continue
        low = v.lower()
        if low == word.lower():
            rep.error(where, f"'{key}' references the headword itself")
        elif low in seen:
            rep.error(where, f"'{key}' repeats {v!r}")
        else:
            seen.add(low)
            out.append(v)
    return out


def check_word_formation(rep, where, wf):
    if wf is None:
        return
    if not isinstance(wf, dict):
        rep.error(where, "word_formation must be an object")
        return
    unknown = set(wf) - {"analysable", "prefixes", "root", "suffixes"}
    if unknown:
        rep.error(where, f"word_formation has unknown fields {sorted(unknown)}")
    analysable = wf.get("analysable")
    if not isinstance(analysable, bool):
        rep.error(where, "word_formation.analysable must be a boolean")
        return
    prefixes = wf.get("prefixes", [])
    suffixes = wf.get("suffixes", [])
    root = wf.get("root")
    if not analysable:
        if prefixes or suffixes or root:
            rep.error(where, "analysable:false but affix data is present")
        return
    if not prefixes and not suffixes and not root:
        rep.error(where, "analysable:true but no prefixes, suffixes, or root given")
    for lst, pat, what in ((prefixes, PREFIX_RE, "prefix"), (suffixes, SUFFIX_RE, "suffix")):
        if not isinstance(lst, list):
            rep.error(where, f"{what}es must be an array")
            continue
        for affix in lst:
            if not isinstance(affix, dict):
                rep.error(where, f"{what} entries must be objects")
                continue
            form = affix.get("form", "")
            if not isinstance(form, str) or not pat.match(form):
                rep.error(where, f"{what} form {form!r} must match {pat.pattern}")
            meaning = affix.get("meaning")
            if not isinstance(meaning, str) or not meaning.strip():
                rep.error(where, f"{what} {form!r} is missing its meaning")
    if root is not None and (not isinstance(root, dict)
                             or not isinstance(root.get("form"), str)
                             or not root["form"].strip()):
        rep.error(where, "root must be an object with a non-empty 'form'")


def check_entry(rep, where, entry, corpus):
    if not isinstance(entry, dict):
        rep.error(where, "line is not a JSON object")
        return
    unknown = set(entry) - {"word", "rank", "pronunciation", "senses",
                            "word_formation", "inflections", "editorial"}
    if unknown:
        rep.error(where, f"entry has unknown fields {sorted(unknown)}")

    word = check_str(rep, where, entry, "word")
    if word is None:
        return
    if unicodedata.normalize("NFC", word) != word or word != word.strip():
        rep.error(where, f"word {word!r} is not NFC-normalized and trimmed")
    if not WORD_RE.match(word):
        rep.warn(where, f"word {word!r} has characters outside the headword policy")

    # A headword may legitimately appear in several files: batch files override
    # derived-bulk, which is how enrichment ships (dict_build.py merges by
    # tier). Only a repeat *within one file* is an error.
    prior = corpus["words"].get(word)
    if prior is not None:
        prior_file = prior.rsplit(":", 1)[0]
        if prior_file == where.rsplit(":", 1)[0]:
            rep.error(where, f"duplicate headword {word!r} (also at {prior})")
        else:
            corpus["overrides"].add(word)
    corpus["words"][word] = where

    rank = entry.get("rank")
    if rank is not None and (not isinstance(rank, int) or rank < 1):
        rep.error(where, f"rank {rank!r} must be a positive integer")

    inflections = entry.get("inflections", [])
    if not isinstance(inflections, list) or any(
            not isinstance(i, str) or not i.strip() for i in inflections):
        rep.error(where, "inflections must be an array of non-empty strings")
        inflections = []

    editorial = entry.get("editorial")
    status = "derived"
    if not isinstance(editorial, dict):
        rep.error(where, "missing 'editorial' object")
    else:
        status = editorial.get("status")
        if status not in STATUS_VALUES:
            rep.error(where, f"editorial.status {status!r} not in {sorted(STATUS_VALUES)}")
            status = "derived"
        revision = editorial.get("revision")
        if not isinstance(revision, int) or revision < 1:
            rep.error(where, "editorial.revision must be an integer >= 1")
        sources = editorial.get("sources")
        if not isinstance(sources, list):
            rep.error(where, "editorial.sources must be an array")
        elif STATUS_RANK[status] >= 1 and not sources:
            rep.error(where, f"a {status} entry must name its sources")

    check_word_formation(rep, where, entry.get("word_formation"))

    senses = entry.get("senses")
    if not isinstance(senses, list) or not senses:
        rep.error(where, "'senses' must be a non-empty array")
        return

    defs_seen = {}
    def_token_sets = []
    for i, sense in enumerate(senses):
        swhere = f"{where} sense[{i}]"
        if not isinstance(sense, dict):
            rep.error(swhere, "sense is not an object")
            continue
        unknown = set(sense) - {"id", "definition", "part_of_speech", "connotation",
                                "examples", "synonyms", "antonyms", "source",
                                "family", "rank"}
        if unknown:
            rep.error(swhere, f"sense has unknown fields {sorted(unknown)}")

        sid = check_str(rep, swhere, sense, "id")
        if sid is not None:
            if not SENSE_ID_RE.match(sid):
                rep.error(swhere, f"sense id {sid!r} does not match {SENSE_ID_RE.pattern}")
            prior_sid = corpus["sense_ids"].get(sid)
            if prior_sid is not None and \
                    prior_sid.rsplit(":", 1)[0] == where.rsplit(":", 1)[0]:
                rep.error(swhere, f"sense id {sid!r} already used at {prior_sid}")
            corpus["sense_ids"][sid] = where

        definition = check_str(rep, swhere, sense, "definition")
        if definition is not None:
            key = " ".join(definition.lower().split())
            if key in defs_seen:
                rep.error(swhere, f"definition duplicates sense[{defs_seen[key]}]")
            else:
                defs_seen[key] = i
            toks = tokens(definition)
            for j, other in def_token_sets:
                if jaccard(toks, other) > NEAR_DUP_JACCARD:
                    rep.warn(swhere, f"definition is near-duplicate of sense[{j}]")
            def_token_sets.append((i, toks))
            if STATUS_RANK[status] >= 1:
                corpus["reviewed_defs"].append((where, i, toks))

        pos = sense.get("part_of_speech")
        if pos not in POS_VALUES:
            rep.error(swhere, f"part_of_speech {pos!r} not in the allowed set")

        check_connotation(rep, swhere, sense)

        examples = sense.get("examples", [])
        if not isinstance(examples, list):
            rep.error(swhere, "'examples' must be an array")
            examples = []
        seen_ex = set()
        for ex in examples:
            if not isinstance(ex, str) or not ex.strip():
                rep.error(swhere, "empty example")
                continue
            low = " ".join(ex.lower().split())
            if low in seen_ex:
                rep.error(swhere, f"repeated example {ex!r}")
            seen_ex.add(low)
            if not example_mentions(word, inflections, ex):
                if STATUS_RANK[status] >= 1:
                    rep.error(swhere, f"example does not use the headword: {ex!r}")
                else:
                    rep.warn(swhere, f"example does not use the headword: {ex!r}")
        if STATUS_RANK[status] >= 2 and len(examples) < 2:
            rep.error(swhere, "a curated sense needs at least two examples")

        syns = check_word_list(rep, swhere, sense, "synonyms", word)
        ants = check_word_list(rep, swhere, sense, "antonyms", word)
        overlap = {s.lower() for s in syns} & {a.lower() for a in ants}
        if overlap:
            rep.error(swhere, f"synonyms and antonyms overlap: {sorted(overlap)}")
        for w in syns + ants:
            corpus["referenced"].add(w)

        source = sense.get("source")
        if source is not None and not isinstance(source, dict):
            rep.error(swhere, "'source' must be an object")

        rank = sense.get("rank")
        if rank is not None and (not isinstance(rank, int) or not 1 <= rank <= 99):
            rep.error(swhere, f"sense rank {rank!r} must be an integer in [1, 99]")

        family = sense.get("family")
        if family is not None:
            if not isinstance(family, dict) or not isinstance(family.get("id"), str) \
                    or not family.get("id"):
                rep.error(swhere, "family must be an object with a non-empty 'id'")
            else:
                unknown = set(family) - {"id", "charge", "spectrum", "axis"}
                if unknown:
                    rep.error(swhere, f"family has unknown fields {sorted(unknown)}")
                axis = family.get("axis")
                if axis is not None and (not isinstance(axis, str) or not axis.strip()):
                    rep.error(swhere, "family axis must be a non-empty string")
                charge = family.get("charge")
                if not isinstance(charge, int) or not -3 <= charge <= 3:
                    rep.error(swhere, f"family charge {charge!r} must be an integer in [-3, 3]")
                spectrum = family.get("spectrum")
                if (not isinstance(spectrum, list) or len(spectrum) < 2 or any(
                        not isinstance(p, list) or len(p) != 2
                        or not isinstance(p[0], str) or not p[0]
                        or not isinstance(p[1], int) or not -3 <= p[1] <= 3
                        for p in spectrum)):
                    rep.error(swhere, "family spectrum must be 2+ [word, charge] pairs")


def validate_file(path, rep, corpus):
    count = 0
    with open(path, encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            where = f"{path.name}:{lineno}"
            try:
                entry = json.loads(line)
            except json.JSONDecodeError as e:
                rep.error(where, f"invalid JSON: {e}")
                continue
            count += 1
            check_entry(rep, where, entry, corpus)
    return count


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="+", type=Path, help="JSONL files or directories")
    ap.add_argument("--strict", action="store_true", help="warnings also fail the run")
    ap.add_argument("--max-report", type=int, default=40, help="cap lines shown per severity")
    args = ap.parse_args()

    files = []
    for p in args.paths:
        if p.is_dir():
            files.extend(sorted(p.glob("*.jsonl")))
        elif p.is_file():
            files.append(p)
        else:
            print(f"not found: {p}", file=sys.stderr)
            return 2
    if not files:
        print("no .jsonl files found", file=sys.stderr)
        return 2

    rep = Report(args.max_report)
    corpus = {"words": {}, "sense_ids": {}, "referenced": set(),
              "reviewed_defs": [], "overrides": set()}
    total = 0
    for f in files:
        n = validate_file(f, rep, corpus)
        total += n
        print(f"{f}: {n} entries")

    # Cross-entry near-duplicate definitions, reviewed tier and above only —
    # derived entries legitimately share glosses across a synset's members.
    rdefs = corpus["reviewed_defs"]
    for i in range(len(rdefs)):
        for j in range(i + 1, len(rdefs)):
            wa, ia, ta = rdefs[i]
            wb, ib, tb = rdefs[j]
            if wa == wb:
                continue
            if jaccard(ta, tb) > NEAR_DUP_JACCARD:
                rep.warn(f"{wa} sense[{ia}]", f"near-duplicate of {wb} sense[{ib}]")

    if corpus["overrides"]:
        rep.info(f"{len(corpus['overrides'])} headwords appear in more than one file "
                 f"(batch entries overriding derived ones - the build keeps the "
                 f"highest tier)")
    dangling = {w for w in corpus["referenced"] if w not in corpus["words"]}
    if dangling:
        rep.info(f"{len(dangling)} synonym/antonym targets have no entry in the "
                 f"validated set (links will fall back to similar-words)")

    rep.dump()
    print(f"\n{total} entries in {len(files)} file(s): "
          f"{len(rep.errors)} error(s), {len(rep.warnings)} warning(s)")
    if rep.errors:
        return 1
    if args.strict and rep.warnings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
