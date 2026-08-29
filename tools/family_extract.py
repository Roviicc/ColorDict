#!/usr/bin/env python3
"""Step 01 of the Connotation Dictionary: extract candidate word families.

A family is a WordNet adjective cluster - one head synset (pos 'a') plus its
satellites (pos 's', linked by 'similar') - i.e. words that share a meaning
but may disagree about how to feel about it (miserly ... thrifty). Sense-level
antonym links between heads pair each family with its opposite, when one exists.

This step is purely mechanical: zero authored content. Screening (evaluative
vs taxonomic) and charge annotation happen in later, authored steps.

Usage:
    python3 tools/family_extract.py \
        --oewn data/source/english-wordnet-2024.xml.gz \
        --out data/build/adjective-families.json
"""

import argparse
import gzip
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from wordnet_import import clean_text, lemma_ok  # noqa: E402


def parse(path):
    entries = {}       # entry id -> lemma
    sense_info = {}    # sense id -> (lemma, synset id)
    antonyms = []      # (sense id, target sense id)
    synsets = {}       # id -> record

    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as fh:
        for _, elem in ET.iterparse(fh, events=("end",)):
            tag = elem.tag.rsplit("}", 1)[-1]
            if tag == "LexicalEntry":
                lemma_el = elem.find("Lemma")
                if lemma_el is None:
                    elem.clear()
                    continue
                lemma = (lemma_el.get("writtenForm") or "").strip()
                eid = elem.get("id")
                if eid:
                    entries[eid] = lemma
                for s in elem.findall("Sense"):
                    sid, synset = s.get("id"), s.get("synset")
                    if sid and synset:
                        sense_info[sid] = (lemma, synset)
                        for r in s.findall("SenseRelation"):
                            if r.get("relType") == "antonym" and r.get("target"):
                                antonyms.append((sid, r.get("target")))
                elem.clear()
            elif tag == "Synset":
                pos = elem.get("partOfSpeech") or ""
                if pos not in ("a", "s", "v", "n"):
                    elem.clear()
                    continue
                sid = elem.get("id")
                defs = [d.text for d in elem.findall("Definition") if d.text]
                synsets[sid] = {
                    "pos": pos,
                    "members": (elem.get("members") or "").split(),
                    "definition": clean_text(defs[0]) if defs else "",
                    "examples": [clean_text(e.text) for e in elem.findall("Example")
                                 if e.text and clean_text(e.text)],
                    "similar": [r.get("target") for r in elem.findall("SynsetRelation")
                                if r.get("relType") == "similar" and r.get("target")],
                    "hypernym": [r.get("target") for r in elem.findall("SynsetRelation")
                                 if r.get("relType") == "hypernym" and r.get("target")],
                }
                elem.clear()
    return entries, sense_info, antonyms, synsets


def member_words(synset, entries, sense_info):
    words = []
    for token in synset["members"]:
        lemma = entries.get(token) or (sense_info.get(token) or (None,))[0]
        if lemma and lemma_ok(lemma) and lemma not in words:
            words.append(lemma)
    return words


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--oewn", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--pos", default="a", choices=("a", "v", "n"),
                    help="a: adjective clusters via 'similar'; "
                         "v/n: hypernym siblings (troponyms)")
    ap.add_argument("--min-size", type=int, default=3,
                    help="skip families smaller than this (verbs/nouns only)")
    args = ap.parse_args()

    entries, sense_info, antonyms, synsets = parse(args.oewn)

    # Head-to-head antonym pairs (via any member sense of each synset).
    synset_antonyms = {}
    for sid, target in antonyms:
        a = sense_info.get(sid)
        b = sense_info.get(target)
        if a and b and a[1] in synsets and b[1] in synsets:
            synset_antonyms.setdefault(a[1], set()).add(b[1])

    if args.pos in ("v", "n"):
        families, words_seen = troponym_families(
            args.pos, synsets, synset_antonyms, entries, sense_info, args.min_size)
    else:
        families, words_seen = similar_families(
            synsets, synset_antonyms, entries, sense_info)

    families.sort(key=lambda f: (-f["size"], f["id"]))
    linked = sum(1 for f in families if f["opposite"])
    multi = sum(1 for f in families if f["size"] > 1)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"pos": args.pos, "families": families}, fh,
                  ensure_ascii=False, indent=1)

    print(f"candidate families: {len(families)}")
    print(f"  multi-member:     {multi}")
    print(f"  antonym-linked:   {linked}")
    print(f"  words covered:    {len(words_seen)}")
    print(f"wrote {args.out}")
    return 0


def troponym_families(pos, synsets, synset_antonyms, entries, sense_info, min_size):
    """Verbs and nouns have no 'similar' clusters - that relation is
    adjective-only. Their families are hypernym siblings instead: every
    synset sharing a parent is one way of doing (or being) the same thing,
    which is exactly where the connotation lives - die / pass away / croak
    are all troponyms of the same parent."""
    children = {}
    for sid, syn in synsets.items():
        if syn["pos"] != pos:
            continue
        for parent in syn["hypernym"]:
            children.setdefault(parent, []).append(sid)

    families = []
    words_seen = set()
    for parent_id, kids in children.items():
        parent = synsets.get(parent_id)
        if parent is None or parent["pos"] != pos:
            continue
        members = []
        for cid in [parent_id] + sorted(kids):
            syn = synsets[cid]
            for w in member_words(syn, entries, sense_info):
                members.append({"word": w, "synset": cid,
                                "definition": syn["definition"],
                                "examples": syn["examples"]})
        if len(members) < min_size:
            continue
        opposite = sorted(synset_antonyms.get(parent_id, ()))
        words_seen.update(m["word"] for m in members)
        families.append({
            "id": parent_id,
            "head_words": member_words(parent, entries, sense_info),
            "definition": parent["definition"],
            "size": len(members),
            "opposite": opposite[0] if opposite else None,
            "members": members,
        })
    return families, words_seen


def similar_families(synsets, synset_antonyms, entries, sense_info):
    families = []
    words_seen = set()
    for head_id, head in synsets.items():
        if head["pos"] != "a":
            continue
        cluster_ids = [head_id] + [t for t in head["similar"] if t in synsets]
        members = []
        for cid in cluster_ids:
            syn = synsets[cid]
            for w in member_words(syn, entries, sense_info):
                members.append({"word": w, "synset": cid,
                                "definition": syn["definition"],
                                "examples": syn["examples"]})
        if not members:
            continue
        opposite = sorted(synset_antonyms.get(head_id, ()))
        family_words = [m["word"] for m in members]
        words_seen.update(family_words)
        families.append({
            "id": head_id,
            "head_words": member_words(head, entries, sense_info),
            "definition": head["definition"],
            "size": len(members),
            "opposite": opposite[0] if opposite else None,
            "members": members,
        })
    return families, words_seen


if __name__ == "__main__":
    sys.exit(main())
