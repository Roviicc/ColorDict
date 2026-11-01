#!/usr/bin/env python3
"""Build the app's built-in dictionary (app/src/main/assets/dictionary.db.gz).

Sources (downloaded by --fetch, or pass local paths):
  * Open English WordNet 2024, CC BY 4.0
    https://en-word.net/downloads/english-wordnet-2024.xml.gz
  * CMU Pronouncing Dictionary, BSD 2-clause, used for IPA where WordNet has none
    https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict

Output: a gzipped SQLite database with
  entries(key TEXT PRIMARY KEY, word TEXT, rank INTEGER, json TEXT)
  forms(form TEXT PRIMARY KEY, key TEXT)                            -- irregular forms ("geese" -> "goose")
  meta(name TEXT PRIMARY KEY, value TEXT)

Each json value is a compact array expanded by Entry.fromDictionaryRow (see the comment
at the INSERT below).

Usage:
  python3 tools/build_dictionary.py --fetch            # download sources to build/dict-src and build
  python3 tools/build_dictionary.py --wordnet X.xml.gz --cmudict cmudict.dict
"""

import argparse
import gzip
import json
import os
import re
import sqlite3
import sys
import urllib.request
import xml.etree.ElementTree as ET

WORDNET_URL = "https://en-word.net/downloads/english-wordnet-2024.xml.gz"
CMUDICT_URL = "https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict"
DB_VERSION = "oewn-2024.1"

POS_NAMES = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
# Index into this list is what the compact rows store; keep in sync with Entry.POS.
POS_INDEX = {"": 0, "noun": 1, "verb": 2, "adjective": 3, "adverb": 4}
MAX_SENSES_PER_POS = 10
MAX_EXAMPLES = 2
MAX_SYNONYMS = 6
MAX_ANTONYMS = 4
MAX_RELATED = 6

# ARPAbet -> IPA (General American)
ARPA_VOWELS = {
    "AA": "ɑ", "AE": "æ", "AH": "ʌ", "AO": "ɔ", "AW": "aʊ", "AY": "aɪ", "EH": "ɛ",
    "ER": "ɝ", "EY": "eɪ", "IH": "ɪ", "IY": "i", "OW": "oʊ", "OY": "ɔɪ", "UH": "ʊ", "UW": "u",
}
ARPA_CONSONANTS = {
    "B": "b", "CH": "tʃ", "D": "d", "DH": "ð", "F": "f", "G": "ɡ", "HH": "h", "JH": "dʒ",
    "K": "k", "L": "l", "M": "m", "N": "n", "NG": "ŋ", "P": "p", "R": "ɹ", "S": "s", "SH": "ʃ",
    "T": "t", "TH": "θ", "V": "v", "W": "w", "Y": "j", "Z": "z", "ZH": "ʒ",
}
# Consonant pairs that can start an English syllable, for placing the stress mark.
ONSETS = {"pl", "pɹ", "bl", "bɹ", "tɹ", "dɹ", "kl", "kɹ", "kw", "ɡl", "ɡɹ", "fl", "fɹ", "θɹ",
          "ʃɹ", "sl", "sm", "sn", "sp", "st", "sk", "sw", "tw", "dw", "pj", "bj", "kj", "fj",
          "mj", "hj", "vj", "spl", "spɹ", "stɹ", "skɹ", "skw"}


def fetch(url, path):
    if os.path.exists(path):
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print(f"downloading {url}", file=sys.stderr)
    urllib.request.urlretrieve(url, path)
    return path


def arpabet_to_ipa(phones):
    """Converts CMUdict phones to IPA with a primary-stress mark before the stressed syllable."""
    segs = []  # (ipa, is_vowel, stress)
    for p in phones:
        m = re.match(r"([A-Z]+)([012])?$", p)
        if not m:
            return None
        base, stress = m.group(1), m.group(2)
        if base in ARPA_VOWELS:
            ipa = ARPA_VOWELS[base]
            if base == "AH" and stress == "0":
                ipa = "ə"
            elif base == "ER" and stress == "0":
                ipa = "ɚ"
            elif base == "IH" and stress == "0":
                ipa = "ɪ"
            segs.append((ipa, True, stress))
        elif base in ARPA_CONSONANTS:
            segs.append((ARPA_CONSONANTS[base], False, None))
        else:
            return None
    vowels = [i for i, s in enumerate(segs) if s[1]]
    out = [s[0] for s in segs]
    stressed = [i for i in vowels if segs[i][2] == "1"]
    if len(vowels) > 1 and stressed:
        v = stressed[0]
        prev_vowels = [i for i in vowels if i < v]
        if not prev_vowels:
            start = 0  # first syllable: the mark goes at the very start
        else:
            # Consonants between the previous vowel and this one; the longest
            # legal onset at the end of that cluster belongs to this syllable.
            first = prev_vowels[-1] + 1
            start = v
            for cut in range(first, v):
                onset = "".join(s[0] for s in segs[cut:v])
                if onset in ONSETS or (v - cut == 1 and onset != "ŋ"):
                    start = cut
                    break
        out.insert(start, "ˈ")
    return "/" + "".join(out) + "/"


def load_cmudict(path):
    pron = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.split("#")[0].strip()
            if not line:
                continue
            word, *phones = line.split()
            if "(" in word:  # alternate pronunciations: keep the first
                continue
            ipa = arpabet_to_ipa(phones)
            if ipa:
                pron[word.lower()] = ipa
    return pron


def tidy_sentence(text):
    text = text.strip()
    if not text:
        return text
    text = text[0].upper() + text[1:]
    if text[-1] not in ".!?\"'”’)":
        text += "."
    return text


def tidy_example(text):
    text = text.strip().strip('"').strip()
    return tidy_sentence(text) if text else text


def display_lemma(written):
    return written.replace("_", " ")


def key_of(word):
    return re.sub(r"\s+", " ", word.strip().lower())


def build(wordnet_path, cmudict_path, out_path):
    cmu = load_cmudict(cmudict_path)

    entries = {}      # entry id -> dict(lemma, pos, prons, senses=[(sense_id, synset_id)], forms)
    sense_owner = {}  # sense id -> entry id
    sense_rels = {}   # sense id -> list of (relType, target sense id)
    synsets = {}      # synset id -> dict(defs, examples, members)

    opener = gzip.open if wordnet_path.endswith(".gz") else open
    with opener(wordnet_path, "rb") as f:
        for _, el in ET.iterparse(f, events=("end",)):
            tag = el.tag
            if tag == "LexicalEntry":
                lemma = el.find("Lemma")
                e = {
                    "lemma": display_lemma(lemma.get("writtenForm")),
                    "pos": lemma.get("partOfSpeech"),
                    "prons": [(p.get("variety"), (p.text or "").strip()) for p in lemma.findall("Pronunciation")],
                    "senses": [],
                    "forms": [display_lemma(f.get("writtenForm")) for f in el.findall("Form")],
                }
                for s in el.findall("Sense"):
                    sid = s.get("id")
                    e["senses"].append((sid, s.get("synset")))
                    sense_owner[sid] = el.get("id")
                    sense_rels[sid] = [(r.get("relType"), r.get("target")) for r in s.findall("SenseRelation")]
                entries[el.get("id")] = e
                el.clear()
            elif tag == "Synset":
                synsets[el.get("id")] = {
                    "defs": [(d.text or "").strip() for d in el.findall("Definition")],
                    "examples": [(x.text or "").strip() for x in el.findall("Example")],
                    "members": (el.get("members") or "").split(),
                }
                el.clear()

    # Group entries by headword key (case-insensitive), keeping their order.
    by_key = {}
    for eid, e in entries.items():
        by_key.setdefault(key_of(e["lemma"]), []).append(eid)

    def lemma_of(entry_id):
        return entries[entry_id]["lemma"] if entry_id in entries else None

    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.execute("CREATE TABLE entries(key TEXT PRIMARY KEY, word TEXT NOT NULL, rank INTEGER NOT NULL,"
               " json TEXT NOT NULL) WITHOUT ROWID")
    db.execute("CREATE TABLE forms(form TEXT PRIMARY KEY, key TEXT NOT NULL) WITHOUT ROWID")
    db.execute("CREATE TABLE meta(name TEXT PRIMARY KEY, value TEXT)")

    rows = []
    forms = {}
    with_ipa = 0
    for key, eids in by_key.items():
        # Prefer the lowercase spelling for display ("turkey" over "Turkey").
        eids.sort(key=lambda i: (entries[i]["lemma"] != entries[i]["lemma"].lower(), 0))
        word = entries[eids[0]]["lemma"]

        phonetic = ""
        for eid in eids:
            prons = entries[eid]["prons"]
            pick = [t for v, t in prons if v == "US"] or [t for v, t in prons if not v] or [t for v, t in prons]
            if pick and pick[0]:
                phonetic = "/" + pick[0].strip("/") + "/"
                break
        if not phonetic:
            phonetic = cmu.get(key, "")
        if phonetic:
            with_ipa += 1

        # Senses grouped by part of speech; the POS with most senses first.
        groups = {}
        for eid in eids:
            pos = POS_NAMES.get(entries[eid]["pos"], "")
            groups.setdefault(pos, []).append(eid)
        ordered = sorted(groups.items(), key=lambda kv: -sum(len(entries[i]["senses"]) for i in kv[1]))

        senses = []
        related = []
        for pos, pos_eids in ordered:
            count = 0
            for eid in pos_eids:
                for sid, ssid in entries[eid]["senses"]:
                    if count >= MAX_SENSES_PER_POS:
                        break
                    syn = synsets.get(ssid)
                    if not syn or not syn["defs"] or not syn["defs"][0]:
                        continue
                    synonyms = []
                    for m in syn["members"]:
                        lm = lemma_of(m)
                        if lm and key_of(lm) != key and lm not in synonyms:
                            synonyms.append(lm)
                    antonyms = []
                    for rel, target in sense_rels.get(sid, []):
                        owner = sense_owner.get(target)
                        lm = lemma_of(owner) if owner else None
                        if not lm:
                            continue
                        if rel == "antonym" and lm not in antonyms:
                            antonyms.append(lm)
                        elif rel == "derivation" and key_of(lm) != key and lm not in related:
                            related.append(lm)
                    # Prefer examples that actually contain the word.
                    examples = sorted(syn["examples"], key=lambda x: key.split(" ")[0][:4] not in x.lower())
                    senses.append({
                        "part_of_speech": pos,
                        "definition": tidy_sentence(syn["defs"][0]),
                        "examples": [tidy_example(x) for x in examples[:MAX_EXAMPLES] if x],
                        "synonyms": synonyms[:MAX_SYNONYMS],
                        "antonyms": antonyms[:MAX_ANTONYMS],
                        "register": "",
                    })
                    count += 1
            for eid in pos_eids:
                for form in entries[eid]["forms"]:
                    fk = key_of(form)
                    if fk != key and fk not in by_key:
                        forms.setdefault(fk, key)
        if not senses:
            continue

        # Compact row format (expanded by Entry.fromDictionaryRow on the device):
        #   [phonetic, [related...], [[pos_index, definition, [examples], [synonyms], [antonyms]], ...]]
        compact = [phonetic, related[:MAX_RELATED],
                   [[POS_INDEX[s["part_of_speech"]], s["definition"], s["examples"], s["synonyms"], s["antonyms"]]
                    for s in senses]]
        blob = json.dumps(compact, ensure_ascii=False, separators=(",", ":"))
        rank = sum(len(entries[i]["senses"]) for i in eids)
        rows.append((key, word, rank, blob))

    db.executemany("INSERT INTO entries VALUES (?,?,?,?)", rows)
    db.executemany("INSERT INTO forms VALUES (?,?)", sorted(forms.items()))
    db.executemany("INSERT INTO meta VALUES (?,?)", [
        ("version", DB_VERSION),
        ("source", "Open English WordNet 2024 (CC BY 4.0); CMU Pronouncing Dictionary (BSD)"),
        ("entries", str(len(rows))),
    ])
    db.commit()
    db.execute("VACUUM")
    db.close()

    gz_path = out_path + ".gz"
    with open(out_path, "rb") as src, gzip.open(gz_path, "wb", compresslevel=9) as dst:
        dst.write(src.read())
    os.remove(out_path)
    print(f"{len(rows)} entries ({with_ipa} with IPA), {len(forms)} irregular forms -> "
          f"{gz_path} ({os.path.getsize(gz_path) / 1e6:.1f} MB)", file=sys.stderr)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fetch", action="store_true", help="download the sources into build/dict-src")
    ap.add_argument("--wordnet", help="path to english-wordnet-2024.xml(.gz)")
    ap.add_argument("--cmudict", help="path to cmudict.dict")
    ap.add_argument("--out", default="app/src/main/assets/dictionary.db",
                    help="output path; '.gz' is appended")
    args = ap.parse_args()
    if args.fetch:
        args.wordnet = args.wordnet or fetch(WORDNET_URL, "build/dict-src/english-wordnet-2024.xml.gz")
        args.cmudict = args.cmudict or fetch(CMUDICT_URL, "build/dict-src/cmudict.dict")
    if not args.wordnet or not args.cmudict:
        ap.error("pass --fetch, or both --wordnet and --cmudict")
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    build(args.wordnet, args.cmudict, args.out)


if __name__ == "__main__":
    main()
