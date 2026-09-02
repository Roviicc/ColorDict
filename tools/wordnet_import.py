#!/usr/bin/env python3
"""Batch B0: derive Pop Up English Dictionary JSONL from Open English WordNet.

Reads the WN-LMF XML release of Open English WordNet (CC BY 4.0) and joins
SentiWordNet 3.0 (CC BY-SA 4.0) on the synset offset for per-sense connotation
scores. Emits one JSON entry per headword (docs/DICTIONARY-PLAN.md sections
5.1-5.4, schema in tools/dict_schema.json). Stdlib only.

Filters (plan 5.2): proper nouns and named entities (instance synsets and
Title-Case lemmas), headwords with characters outside letters/apostrophe/
hyphen/space, single letters other than a/i/I.

The fabrication rule (plan 5.4): the connotation label comes from the
SentiWordNet score or stays neutral; no explanation text is ever generated
here. Word formation is omitted entirely — absent means "not yet analysed".

Usage:
    python3 tools/wordnet_import.py \
        --oewn data/source/english-wordnet-2024.xml.gz \
        --swn data/source/SentiWordNet_3.0.0.txt \
        --out data/entries/derived-bulk.jsonl
"""

import argparse
import gzip
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

# Must match tools/dict_validate.py.
SCORE_POSITIVE = 0.25
SCORE_NEGATIVE = -0.25

POS_MAP = {
    "n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb",
    "c": "conjunction", "p": "preposition", "x": "particle", "u": "phrase",
}

BAD_CHARS = re.compile(r"[^A-Za-z' -]")
TITLE_TOKEN = re.compile(r"^[A-Z][a-z]")
SYNSET_OFFSET = re.compile(r"-(\d{8})-([nvasr])$")

stats = Counter()


def lemma_ok(lemma):
    if lemma in ("a", "i", "I"):
        return True
    if len(lemma) < 2:
        stats["skip_single_letter"] += 1
        return False
    if BAD_CHARS.search(lemma):
        stats["skip_bad_chars"] += 1
        return False
    for token in re.split(r"[ -]", lemma):
        if TITLE_TOKEN.match(token):
            stats["skip_proper_noun"] += 1
            return False
    return True


def slug(lemma):
    return lemma.lower().replace(" ", "_").replace("'", "")


def clean_text(text):
    text = " ".join((text or "").split())
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1].strip()
    return text


def load_sentiwordnet(path):
    """(pos, offset) -> pos_score - neg_score."""
    scores = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("#") or not line.strip():
                continue
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 4:
                continue
            pos, offset, pos_s, neg_s = cols[0], cols[1], cols[2], cols[3]
            try:
                scores[(pos, int(offset))] = round(float(pos_s) - float(neg_s), 4)
            except ValueError:
                continue
    return scores


def load_ili_map(path):
    """CILI ili id -> SentiWordNet key (pos, offset), e.g. i1 -> ('a', 1740)."""
    out = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            cols = line.split()
            if len(cols) != 2 or not cols[0].startswith("i"):
                continue
            ref = cols[1].rsplit(":", 1)[-1]
            if len(ref) < 10 or ref[8] != "-":
                continue
            pos = ref[9]
            if pos == "s":
                pos = "a"
            try:
                out[cols[0]] = (pos, int(ref[:8]))
            except ValueError:
                continue
    return out


def swn_score(scores, ili_map, synset):
    """OEWN 2024 renumbered most synsets away from PWN 3.0 offsets, so the
    reliable join to SentiWordNet is synset.ili -> CILI map -> PWN 3.0 key.
    The raw offset in the synset id still works for unrenumbered synsets and
    serves as a fallback when no ILI mapping exists."""
    key = ili_map.get(synset.get("ili") or "")
    if key is not None:
        return scores.get(key)
    m = SYNSET_OFFSET.search(synset["id"])
    if not m:
        return None
    pos = m.group(2)
    return scores.get(("a" if pos == "s" else pos, int(m.group(1))))


def parse_oewn(path):
    """One streaming pass; returns (entries, synsets, sense_lemma, entry_lemma).

    entries: list of dicts in document order:
        {lemma, pos, pronunciation, forms[], senses[(sense_id, synset_id, [antonym sense ids])]}
    synsets: id -> {pos, definition, examples[], members[], instance}
    """
    entries = []
    synsets = {}
    sense_lemma = {}
    entry_lemma = {}

    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as fh:
        context = ET.iterparse(fh, events=("end",))
        for _, elem in context:
            tag = elem.tag.rsplit("}", 1)[-1]
            if tag == "LexicalEntry":
                lemma_el = elem.find("Lemma")
                if lemma_el is None:
                    elem.clear()
                    continue
                lemma = (lemma_el.get("writtenForm") or "").strip()
                pos = lemma_el.get("partOfSpeech") or ""
                pron = None
                for p in lemma_el.findall("Pronunciation"):
                    if p.text and not p.get("variety"):
                        pron = p.text.strip()
                        break
                    if pron is None and p.text:
                        pron = p.text.strip()
                forms = [f.get("writtenForm", "").strip()
                         for f in elem.findall("Form") if f.get("writtenForm")]
                senses = []
                for s in elem.findall("Sense"):
                    sid, synset = s.get("id"), s.get("synset")
                    if not sid or not synset:
                        continue
                    antonyms = [r.get("target") for r in s.findall("SenseRelation")
                                if r.get("relType") == "antonym" and r.get("target")]
                    senses.append((sid, synset, antonyms))
                    sense_lemma[sid] = lemma
                eid = elem.get("id")
                if eid:
                    entry_lemma[eid] = lemma
                entries.append({"lemma": lemma, "pos": pos, "pron": pron,
                                "forms": forms, "senses": senses})
                elem.clear()
            elif tag == "Synset":
                sid = elem.get("id")
                if not sid:
                    elem.clear()
                    continue
                defs = [d.text for d in elem.findall("Definition") if d.text]
                examples = []
                for e in elem.findall("Example"):
                    text = clean_text(e.text)
                    if text and text.lower() not in (x.lower() for x in examples):
                        examples.append(text)
                instance = any(r.get("relType") == "instance_hypernym"
                               for r in elem.findall("SynsetRelation"))
                synsets[sid] = {
                    "id": sid,
                    "ili": elem.get("ili") or "",
                    "pos": elem.get("partOfSpeech") or "",
                    "definition": clean_text(defs[0]) if defs else "",
                    "examples": examples,
                    "members": (elem.get("members") or "").split(),
                    "instance": instance,
                }
                elem.clear()
    return entries, synsets, sense_lemma, entry_lemma


def member_lemmas(synset, sense_lemma, entry_lemma):
    out = []
    for token in synset["members"]:
        lemma = sense_lemma.get(token) or entry_lemma.get(token)
        if lemma and lemma not in out:
            out.append(lemma)
    return out


def build_entries(entries, synsets, sense_lemma, entry_lemma, scores, ili_map):
    by_word = {}
    used_sense_ids = set()

    for entry in entries:
        lemma = entry["lemma"]
        if not lemma or not lemma_ok(lemma):
            stats["senses_dropped_with_entry"] += len(entry["senses"])
            continue
        record = by_word.setdefault(lemma, {"word": lemma, "pron": None,
                                            "senses": [], "forms": [],
                                            "defs": set(), "scored": False})
        if record["pron"] is None and entry["pron"]:
            record["pron"] = entry["pron"]
        for form in entry["forms"]:
            if form and form != lemma and lemma_ok(form) and form not in record["forms"]:
                record["forms"].append(form)

        for _sid, synset_id, antonym_sids in entry["senses"]:
            synset = synsets.get(synset_id)
            if synset is None:
                stats["skip_missing_synset"] += 1
                continue
            if synset["instance"]:
                stats["skip_instance_sense"] += 1
                continue
            pos = POS_MAP.get(synset["pos"] or entry["pos"])
            if pos is None:
                stats["skip_unmapped_pos"] += 1
                continue
            if not synset["definition"]:
                stats["skip_no_definition"] += 1
                continue
            # OEWN occasionally carries the same gloss in two synsets that
            # share a member (e.g. "barleycorn"); one copy is enough.
            def_key = synset["definition"].lower()
            if def_key in record["defs"]:
                stats["skip_duplicate_gloss"] += 1
                continue
            record["defs"].add(def_key)

            sense_id = f"{slug(lemma)}.{synset_id}"
            if sense_id in used_sense_ids:
                sense_id += "-2"
            used_sense_ids.add(sense_id)

            score = swn_score(scores, ili_map, synset)
            if score is not None and score >= SCORE_POSITIVE:
                label = "positive"
            elif score is not None and score <= SCORE_NEGATIVE:
                label = "negative"
            else:
                label = "neutral"
            connotation = {"label": label}
            if score is not None:
                connotation["score"] = score
                record["scored"] = True
                stats[f"label_{label}"] += 1
            else:
                stats["label_unscored"] += 1

            seen = {lemma.lower()}
            synonyms = []
            for m in member_lemmas(synset, sense_lemma, entry_lemma):
                if m.lower() not in seen and lemma_ok(m):
                    seen.add(m.lower())
                    synonyms.append(m)
            # Seed with the synonyms so an auto-antonym like ravel/unravel
            # (upstream lists it as both) lands only on the synonym side.
            seen = {lemma.lower()} | {s.lower() for s in synonyms}
            antonyms = []
            for target in antonym_sids:
                t_lemma = sense_lemma.get(target)
                if t_lemma and t_lemma.lower() not in seen and lemma_ok(t_lemma):
                    seen.add(t_lemma.lower())
                    antonyms.append(t_lemma)

            sense = {
                "id": sense_id,
                "definition": synset["definition"],
                "part_of_speech": pos,
                "connotation": connotation,
            }
            own_examples = examples_for(lemma, entry["forms"], synset["examples"])
            if own_examples:
                sense["examples"] = own_examples
            if len(own_examples) < len(synset["examples"]):
                stats["examples_dropped"] += len(synset["examples"]) - len(own_examples)
            if synonyms:
                sense["synonyms"] = synonyms
            if antonyms:
                sense["antonyms"] = antonyms
            sense["source"] = {"synset": synset_id}
            record["senses"].append(sense)
            stats["senses_out"] += 1

    out = []
    for lemma in sorted(by_word, key=lambda w: (w.casefold(), w)):
        record = by_word[lemma]
        if not record["senses"]:
            stats["entries_left_empty"] += 1
            continue
        entry = {"word": lemma}
        if record["pron"]:
            entry["pronunciation"] = record["pron"]
        entry["senses"] = record["senses"]
        if record["forms"]:
            entry["inflections"] = record["forms"]
        sources = ["oewn-2024"]
        if record["scored"]:
            sources.append("sentiwordnet-3.0")
        entry["editorial"] = {"status": "derived", "revision": 1, "sources": sources}
        out.append(entry)
    return out


# OEWN attaches examples to the SYNSET, not to the lemma, so copying them onto
# every member gives *lucid* the example "pellucid prose" and *distinct* the
# example "trenchant distinctions between right and wrong" - each illustrating a
# sibling of the word whose card it lands on. Census 010's blind reader found
# two of these and named the mechanism; the shape had been on the books as
# `bad-example` since tick 4 without a cause.
#
# A member keeps only the synset examples that use its own lemma or one of its
# forms, on a word boundary. That drops 11% of examples and leaves 7% of senses
# with none at all - a card with no example is better than a card whose example
# is about a different word.
def examples_for(lemma, forms, examples):
    cands = [lemma.lower()] + [f.lower() for f in forms if f]
    kept = []
    for ex in examples:
        low = ex.lower()
        if any(re.search(r"(?<![A-Za-z])" + re.escape(c) + r"(?![A-Za-z])", low)
               for c in cands if c):
            kept.append(ex)
    return kept


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--oewn", type=Path, required=True,
                    help="english-wordnet-*.xml or .xml.gz (WN-LMF)")
    ap.add_argument("--swn", type=Path, default=None,
                    help="SentiWordNet_3.0.0.txt (omit to skip sentiment)")
    ap.add_argument("--ili-map", type=Path, default=None,
                    help="CILI ili-map-pwn30.tab; joins SWN to OEWN's renumbered synsets")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--limit", type=int, default=0,
                    help="emit only the first N entries (smoke tests)")
    args = ap.parse_args()

    scores = {}
    if args.swn:
        scores = load_sentiwordnet(args.swn)
        print(f"sentiwordnet: {len(scores)} scored synsets")
    ili_map = {}
    if args.ili_map:
        ili_map = load_ili_map(args.ili_map)
        print(f"ili map: {len(ili_map)} pwn30 mappings")

    print(f"parsing {args.oewn} ...")
    entries, synsets, sense_lemma, entry_lemma = parse_oewn(args.oewn)
    print(f"oewn: {len(entries)} lexical entries, {len(synsets)} synsets")

    out = build_entries(entries, synsets, sense_lemma, entry_lemma, scores, ili_map)
    if args.limit:
        out = out[: args.limit]

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for entry in out:
            fh.write(json.dumps(entry, ensure_ascii=False,
                                separators=(",", ":")) + "\n")

    print(f"\nwrote {len(out)} entries to {args.out}")
    for key in sorted(stats):
        print(f"  {key}: {stats[key]}")
    scored = stats["label_positive"] + stats["label_negative"] + stats["label_neutral"]
    total_senses = scored + stats["label_unscored"]
    if total_senses:
        print(f"  swn coverage: {scored}/{total_senses} senses "
              f"({100.0 * scored / total_senses:.1f}%)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
