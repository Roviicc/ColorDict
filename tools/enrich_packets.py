#!/usr/bin/env python3
"""Stage 4: the packets for the Sense Ranker, the Enricher and the blind reader.

Three packet kinds, one per instrument, each carrying exactly what that hand may
see and nothing about what the other hands decided:

  select    a book's lemma records -> the entries to spend on, and the Ranker's
            packets: every OEWN sense of the word (id + gloss) and up to six
            real sentences from the book. The Ranker returns synset ids only.
  enricher  a validated ranking -> the Enricher's packets: the same senses in
            ranked order, marked which ones to write for. The Enricher never
            sees a charge or a tone note and never writes one.
  reader    the applied overlay -> the reader's packets: the entry as the app
            will show it, plus the sentences the ranking was judged on. The
            reader sees no Ranker or Enricher rationale, only the product.
  nulls     (stage 5) every sense the Enricher called connotation-free, as a
            blind packet for the null auditor: word, POS, gloss, sentences.

Why the sentences are re-sampled here rather than taken from lemmas.jsonl: the
ingest keeps the FIRST six sentence ids per lemma, and in a Gutenberg text the
first six are the editor's preface. Six sentences spread across the book are a
sample of the book; six from the preface are a sample of the preface.

Usage:
    python3 tools/enrich_packets.py select  --book data/build/books/<id> --n 50 --out data/policy/enrich-001
    python3 tools/enrich_packets.py enricher --out data/policy/enrich-001
    python3 tools/enrich_packets.py reader   --out data/policy/enrich-001 --batch data/entries/batch-0001.jsonl
"""

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BULK = ROOT / "data/entries/derived-bulk.jsonl"
sys.path.insert(0, str(ROOT / "tools"))
import book_ingest as bi  # noqa: E402  - the OEWN join and the annotated set live there

UPOS_TO_POS = {"NOUN": "noun", "VERB": "verb", "ADJ": "adjective", "ADV": "adverb"}
PER_PACKET = 10
SENTENCES = 6
MIN_SENTENCES = 3
MAX_SENTENCE_CHARS = 240
MIN_SENTENCE_CHARS = 25

# How many senses the Enricher writes for. Everything the book attests, plus the
# top of the ranking, capped: *make* has 49 senses and a learner wants the ones
# they will meet, not a concordance.
ENRICH_MIN = 3
ENRICH_MAX = 6


def load_bulk(words):
    """Entries for the given headwords, keyed by lower-cased word."""
    wanted = {w.lower() for w in words}
    out = {}
    with BULK.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            w = (entry.get("word") or "").lower()
            if w in wanted:
                out[w] = entry
    return out


def clean_sentence(text):
    text = text.replace("\n", " ")
    text = re.sub(r"\s+", " ", text)
    text = text.replace("_", "")
    return text.strip()


def form_pattern(forms):
    alts = sorted({f for f in forms if f}, key=len, reverse=True)
    if not alts:
        return None
    return re.compile(r"(?<![A-Za-z])(" + "|".join(re.escape(a) for a in alts)
                      + r")(?![A-Za-z])")


def spread(items, n):
    if len(items) <= n:
        return items
    step = len(items) / n
    return [items[int(i * step)] for i in range(n)]


def sample_sentences(sentences, forms):
    pat = form_pattern(forms)
    if pat is None:
        return []
    hits = []
    for s in sentences:
        text = s["text"]
        if "[" in text or "]" in text:
            continue
        text = clean_sentence(text)
        if not (MIN_SENTENCE_CHARS <= len(text) <= MAX_SENTENCE_CHARS):
            continue
        if pat.search(text):
            hits.append(text)
    return spread(hits, SENTENCES)


def write_packets(entries, out_dir, name):
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("input-*.json"):
        old.unlink()
    n = 0
    for i in range(0, len(entries), PER_PACKET):
        n += 1
        packet = {"packet": n, "entries": entries[i:i + PER_PACKET]}
        (out_dir / f"input-{n:02d}.json").write_text(
            json.dumps(packet, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{name}: {len(entries)} entries in {n} packets -> {out_dir}")
    return n


# --------------------------------------------------------------------------
# select
# --------------------------------------------------------------------------

def cmd_select(args):
    book = Path(args.book)
    meta = json.loads((book / "meta.json").read_text(encoding="utf-8"))
    title = meta.get("title") or meta.get("source_filename")
    sentences = [json.loads(l) for l in (book / "sentences.jsonl").open(encoding="utf-8")
                 if l.strip()]
    lookup = bi.wordnet_index()
    done = bi.already_annotated()

    records = []
    with (book / "lemmas.jsonl").open(encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                records.append(json.loads(line))

    candidates = []
    for r in records:
        lemma, upos = r["lemma"], r["part_of_speech"]
        if upos not in UPOS_TO_POS:
            continue
        if (lemma, upos) in done:
            continue
        if not lookup(lemma, upos):
            continue
        candidates.append(r)
        if len(candidates) >= args.n * 2:
            break

    bulk = load_bulk(r["lemma"] for r in candidates)
    selected, skipped = [], []
    for r in candidates:
        if len(selected) >= args.n:
            break
        lemma, upos = r["lemma"], r["part_of_speech"]
        pos = UPOS_TO_POS[upos]
        entry = bulk.get(lemma.lower())
        if entry is None:
            skipped.append((lemma, upos, "not in bulk"))
            continue
        senses = [s for s in entry["senses"] if s["part_of_speech"] == pos]
        if not senses:
            skipped.append((lemma, upos, "no sense of that POS"))
            continue
        sample = sample_sentences(sentences, list(r["corpus"]["forms"]))
        if len(sample) < MIN_SENTENCES:
            skipped.append((lemma, upos, f"only {len(sample)} usable sentences"))
            continue
        selected.append({
            "word": entry["word"],
            "pos": pos,
            "book": title,
            "occurrences": r["corpus"]["total_occurrences"],
            "forms": r["corpus"]["forms"],
            "senses": [{
                "synset": s["source"]["synset"],
                "sense_id": s["id"],
                "gloss": s["definition"],
                "wordnet_examples": (s.get("examples") or [])[:2],
                "synonyms": (s.get("synonyms") or [])[:6],
            } for s in senses],
            "sentences": sample,
        })

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    selection = {
        "book_id": meta["book_id"], "book": title, "n": len(selected),
        "rule": "top of the book's lemma+POS list by occurrences, joined to OEWN "
                "2025 through derived-bulk.jsonl, not already in a family, with "
                f"at least {MIN_SENTENCES} usable sentences",
        "skipped": [{"lemma": l, "pos": p, "why": w} for l, p, w in skipped],
        "entries": [{"word": e["word"], "pos": e["pos"], "occurrences": e["occurrences"],
                     "senses": len(e["senses"]), "sentences": len(e["sentences"])}
                    for e in selected],
    }
    (out / "selection.json").write_text(json.dumps(selection, ensure_ascii=False, indent=1),
                                        encoding="utf-8")
    ranker_entries = [{k: e[k] for k in ("word", "pos", "book", "occurrences", "sentences")}
                      | {"senses": [{k: s[k] for k in ("synset", "gloss", "wordnet_examples",
                                                       "synonyms")}
                                    for s in e["senses"]]}
                      for e in selected]
    (out / "selected.json").write_text(json.dumps(selected, ensure_ascii=False, indent=1),
                                       encoding="utf-8")
    write_packets(ranker_entries, out / "ranker-packets", "ranker")
    print(f"selected {len(selected)}, skipped {len(skipped)}: "
          + ", ".join(f"{l}/{p} ({w})" for l, p, w in skipped[:8]))
    return 0


# --------------------------------------------------------------------------
# enricher
# --------------------------------------------------------------------------

def cmd_enricher(args):
    out = Path(args.out)
    selected = {(e["word"], e["pos"]): e
                for e in json.loads((out / "selected.json").read_text(encoding="utf-8"))}
    ranking = json.loads((out / "ranking.json").read_text(encoding="utf-8"))
    entries = []
    for r in ranking["entries"]:
        e = selected[(r["word"], r["pos"])]
        by_syn = {s["synset"]: s for s in e["senses"]}
        met = set(r["met"])
        enrich = []
        for syn in r["order"]:
            if syn in met or len(enrich) < ENRICH_MIN:
                if len(enrich) < ENRICH_MAX:
                    enrich.append(syn)
        entries.append({
            "word": e["word"], "pos": e["pos"], "book": e["book"],
            "sentences": e["sentences"],
            "senses": [{
                "synset": syn,
                "gloss": by_syn[syn]["gloss"],
                "wordnet_examples": by_syn[syn]["wordnet_examples"],
                "synonyms": by_syn[syn]["synonyms"],
                "write": syn in enrich,
            } for syn in r["order"]],
        })
    write_packets(entries, out / "enricher-packets", "enricher")
    n_write = sum(1 for e in entries for s in e["senses"] if s["write"])
    print(f"senses to write: {n_write} across {len(entries)} entries")
    return 0


# --------------------------------------------------------------------------
# reader
# --------------------------------------------------------------------------

def cmd_reader(args):
    out = Path(args.out)
    selected = {(e["word"], e["pos"]): e
                for e in json.loads((out / "selected.json").read_text(encoding="utf-8"))}
    results = json.loads((out / "results.json").read_text(encoding="utf-8"))
    verdicts = {(r["word"], r["pos"]): r for r in results["entries"]}
    batch = {}
    with Path(args.batch).open(encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                entry = json.loads(line)
                batch[entry["word"].lower()] = entry

    entries = []
    for (word, pos), sel in selected.items():
        r = verdicts.get((word, pos))
        if r is None or not r.get("accepted"):
            continue
        entry = batch.get(word.lower())
        if entry is None:
            sys.exit(f"{word} is not in {args.batch} - run the pipeline first")
        senses = [s for s in entry["senses"] if s["part_of_speech"] == pos]
        senses.sort(key=lambda s: (0 if s.get("rank") else 1, s.get("rank") or 0))
        written = r["senses"]
        shown = []
        for s in senses:
            syn = s["source"]["synset"]
            w = written.get(syn)
            card = {"synset": syn, "gloss": s["definition"]}
            if w is not None:
                card["learner"] = s.get("learner")
                card["examples"] = w["examples"]
                card["usage_labels"] = w.get("usage_labels") or []
                card["connotation"] = w["connotation"]
            shown.append(card)
        entries.append({"word": word, "pos": pos, "book": sel["book"],
                        "sentences": sel["sentences"], "senses": shown})
    write_packets(entries, out / "reader-packets", "reader")
    return 0


# --------------------------------------------------------------------------
# nulls (stage 5)
# --------------------------------------------------------------------------

def cmd_nulls(args):
    """Every sense the Enricher marked connotation-free, as a blind packet: the
    word, its POS, the gloss and the book sentences - not the learner line or
    anything else the Enricher wrote, which would tell the auditor what the
    other hand thought."""
    out = Path(args.out)
    selected = {(e["word"], e["pos"]): e
                for e in json.loads((out / "selected.json").read_text(encoding="utf-8"))}
    results = json.loads((out / "results.json").read_text(encoding="utf-8"))
    senses = []
    for r in results["entries"]:
        if not r.get("accepted"):
            continue
        sel = selected[(r["word"], r["pos"])]
        gloss_of = {s["synset"]: s["gloss"] for s in sel["senses"]}
        for syn, w in r["senses"].items():
            if w["connotation"] is None:
                senses.append({"synset": syn, "word": r["word"], "pos": r["pos"],
                               "gloss": gloss_of[syn], "sentences": sel["sentences"]})
    n = 0
    per = args.per_packet
    (out / "null-packets").mkdir(parents=True, exist_ok=True)
    for old in (out / "null-packets").glob("input-*.json"):
        old.unlink()
    for i in range(0, len(senses), per):
        n += 1
        (out / "null-packets" / f"input-{n:02d}.json").write_text(
            json.dumps({"packet": n, "senses": senses[i:i + per]}, ensure_ascii=False,
                       indent=1), encoding="utf-8")
    print(f"nulls: {len(senses)} senses in {n} packets -> {out / 'null-packets'}")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("select")
    s.add_argument("--book", required=True, help="data/build/books/<id>")
    s.add_argument("--n", type=int, default=50)
    s.add_argument("--out", required=True)
    e = sub.add_parser("enricher")
    e.add_argument("--out", required=True)
    r = sub.add_parser("reader")
    r.add_argument("--out", required=True)
    r.add_argument("--batch", default=str(ROOT / "data/entries/batch-0001.jsonl"))
    n = sub.add_parser("nulls")
    n.add_argument("--out", required=True)
    n.add_argument("--per-packet", type=int, default=25)
    args = ap.parse_args()
    return {"select": cmd_select, "enricher": cmd_enricher, "reader": cmd_reader,
            "nulls": cmd_nulls}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
