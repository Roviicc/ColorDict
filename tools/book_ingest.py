#!/usr/bin/env python3
"""Lane A: a book becomes a ranked, WordNet-validated vocabulary. Code only.

This is the whole of Lane A in one script, and it deliberately spends nothing.
No model is called anywhere in this file. The run ends at a corpus report, which
is the gate between free work and paid work - the only place a person has to
make a decision (Graph Engineering, section 3).

What it fixes about the earlier `book_worklist.py`
--------------------------------------------------
That tool lemmatised every token under one assumed part of speech, so it could
not tell *saw* the past tense of *see* from *saw* the cutting tool, and it
counted both against whichever guess it made. Every downstream number inherited
that error. This script does contextual tagging: sentence segmentation, then
POS, then lemma given that POS, which is the pipeline the plan asks for in 5C.

    "She saw the boat"  -> saw / see / VERB
    "He bought a saw"   -> saw / saw  / NOUN

Surface forms are never destroyed
---------------------------------
Section 5A is a hard rule: lemmatisation groups vocabulary, it does not erase
it. Every occurrence keeps its surface form, and every aggregate keeps the
per-form counts underneath the lemma total, so *emerge* 32 still knows it was
emerge 3 / emerges 4 / emerged 18 / emerging 7.

Book id is the content hash
---------------------------
Re-ingesting the same file is free and idempotent, a book cannot be counted
twice because it was dragged in under two names, and a given hash always
produced a given occurrence set.

Outputs, all under data/build/books/<book_id>/ (gitignored - a book's extracted
text is not repo content, and for anything still in copyright it must not be):

    meta.json        provenance, hash, counts
    sentences.jsonl  sent_id -> text, kept so context can be re-fetched
    lemmas.jsonl     lemma+POS records: totals, per-form counts, sample sent_ids
    report.json      the corpus report - the decision screen

Usage:
    python tools/book_ingest.py --book path/to/book.txt --title "..." --license public-domain
    python tools/book_ingest.py --book x.epub --report-only
"""

import argparse
import hashlib
import json
import re
import sys
import unicodedata
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_ROOT = ROOT / "data/build/books"

# spaCy's coarse tags for words that carry lexical content. Everything else is
# function vocabulary, which section 6 keeps in its own bucket: it dominates any
# frequency count and tells you nothing about which content words to enrich.
CONTENT_POS = {"NOUN", "VERB", "ADJ", "ADV"}
WN_POS = {"NOUN": "n", "VERB": "v", "ADJ": "a", "ADV": "r"}

# The lexicon this dictionary actually ships (plan 0.3), not nltk's PWN 3.0.
BULK = ROOT / "data/entries/derived-bulk.jsonl"

# How many example sentences to remember per lemma+POS. The Sense Ranker wants a
# handful of real usages; it does not want the whole book, and storing every
# occurrence of *the* would be a gigabyte of nothing.
SENTENCES_PER_LEMMA = 6
COVERAGE_POINTS = (0.80, 0.90, 0.95, 0.98)


# --------------------------------------------------------------------------
# Extraction
# --------------------------------------------------------------------------

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def strip_tags(html):
    html = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", html)
    html = re.sub(r"(?is)<br\s*/?>|</p>|</div>|</h[1-6]>", "\n", html)
    html = re.sub(r"(?s)<[^>]+>", " ", html)
    html = re.sub(r"&nbsp;?", " ", html)
    html = re.sub(r"&#(\d+);", lambda m: chr(int(m.group(1))), html)
    html = re.sub(r"&amp;", "&", html)
    html = re.sub(r"&(l|g)t;", lambda m: "<" if m.group(1) == "l" else ">", html)
    html = re.sub(r"&quot;", '"', html)
    html = re.sub(r"&[a-z]+;", " ", html)
    return html


def extract_epub(path):
    """Read an EPUB's spine in order. Order matters: sentence ids should follow
    the book, so a later look at a sentence lands where the reader would."""
    out = []
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        opf = next((n for n in names if n.endswith(".opf")), None)
        order = []
        if opf:
            opf_dir = str(Path(opf).parent)
            manifest = z.read(opf).decode("utf-8", "ignore")
            ids = dict(re.findall(r'id="([^"]+)"[^>]*href="([^"]+)"', manifest))
            ids.update(dict(re.findall(r'href="([^"]+)"[^>]*id="([^"]+)"', manifest))
                       and {})  # tolerate attribute order without duplicating
            for idref in re.findall(r'<itemref[^>]*idref="([^"]+)"', manifest):
                href = ids.get(idref)
                if not href:
                    continue
                full = str(Path(opf_dir) / href) if opf_dir not in (".", "") else href
                full = full.replace("\\", "/")
                if full in names:
                    order.append(full)
        if not order:
            order = sorted(n for n in names if n.lower().endswith((".xhtml", ".html", ".htm")))
        for n in order:
            try:
                out.append(strip_tags(z.read(n).decode("utf-8", "ignore")))
            except KeyError:
                continue
    return "\n\n".join(out)


def extract_txt(path):
    text = Path(path).read_text(encoding="utf-8", errors="ignore")
    # Project Gutenberg boilerplate is not the author's vocabulary.
    start = re.search(r"\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG.*?\*\*\*", text, re.S)
    if start:
        text = text[start.end():]
    end = re.search(r"\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG", text)
    if end:
        text = text[:end.start()]
    return text


def extract(path):
    suffix = Path(path).suffix.lower()
    if suffix == ".epub":
        return extract_epub(path)
    if suffix in (".txt", ".md"):
        return extract_txt(path)
    raise SystemExit(f"unsupported format {suffix!r} - convert to .txt or .epub first")


def clean(text):
    text = unicodedata.normalize("NFKC", text)
    text = text.replace("’", "'").replace("‘", "'")
    text = text.replace("“", '"').replace("”", '"')
    text = re.sub(r"-\n(?=[a-z])", "", text)       # de-hyphenate across a line break
    text = re.sub(r"[ \t ]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


# --------------------------------------------------------------------------
# Tagging
# --------------------------------------------------------------------------

def load_nlp():
    import spacy
    # The parser and NER are the expensive components and neither is needed:
    # sentence boundaries come from the rule-based sentencizer, and proper nouns
    # are already visible in the POS tag.
    nlp = spacy.load("en_core_web_sm", exclude=["parser", "ner"])
    nlp.add_pipe("sentencizer", first=True)
    nlp.max_length = 10_000_000
    return nlp


def paragraphs(text):
    for block in re.split(r"\n\s*\n", text):
        block = block.strip()
        if block:
            yield block


def tag(text, nlp, progress_every=200_000):
    """Yield (sentence_text, [(surface, lemma, upos) ...]) for every sentence."""
    seen = 0
    for doc in nlp.pipe(paragraphs(text), batch_size=64):
        for sent in doc.sents:
            toks = []
            for t in sent:
                if t.is_space or t.is_punct or t.like_num:
                    continue
                toks.append((t.text, t.lemma_.lower(), t.pos_))
            if toks:
                yield sent.text.strip(), toks
        seen += len(doc)
        if seen // progress_every != (seen - len(doc)) // progress_every:
            print(f"    ... {seen:,} tokens tagged", file=sys.stderr)


# --------------------------------------------------------------------------
# WordNet + the existing corpus
# --------------------------------------------------------------------------

def wordnet_index(bulk=None):
    """lemma+POS -> the synset ids this dictionary can actually serve.

    This used to call nltk, which ships **Princeton WordNet 3.0** - a different
    lexicon from the one the corpus is built on. Every coverage figure produced
    that way answered "does Princeton know this word", when the only question
    that matters here is "can the dictionary we ship show the reader an entry".
    The two disagree in both directions, so the numbers were wrong, not merely
    approximate.

    So the index is built from `derived-bulk.jsonl` - the OEWN import itself.
    It carries the same filters the shipped dictionary has (no proper nouns, no
    odd characters), and it moves to OEWN 2025 the moment the import does, with
    nothing here to update. Synset ids come back in corpus form (oewn-...-n),
    which is traceable; `dog.n.01` never was.
    """
    path = Path(bulk) if bulk else BULK
    if not path.exists():
        sys.exit(f"{path} is missing - regenerate it with tools/wordnet_import.py")

    pos_of = {"noun": "NOUN", "verb": "VERB", "adjective": "ADJ", "adverb": "ADV"}
    index = defaultdict(list)
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            word = (entry.get("word") or "").lower()
            if not word:
                continue
            for sense in entry.get("senses", []):
                upos = pos_of.get(sense.get("part_of_speech"))
                if not upos:
                    continue
                syn = (sense.get("source") or {}).get("synset") or sense.get("id")
                if syn and syn not in index[(word, upos)]:
                    index[(word, upos)].append(syn)

    def lookup(lemma, upos):
        if upos not in WN_POS:
            return []
        lemma = lemma.lower()
        # spaCy hands back multiword lemmas space-separated; the import stores
        # them the same way. Underscores are tried only as a courtesy.
        return (index.get((lemma, upos))
                or index.get((lemma.replace("_", " "), upos))
                or [])
    return lookup


def already_annotated():
    """lemma+POS pairs that already carry a hand-written tone note.

    On book two and later this is the whole story - it is the difference between
    a large generation run and a cheap one."""
    done = set()
    pos_of = {"-a": "ADJ", "-s": "ADJ", "-v": "VERB", "-n": "NOUN", "-r": "ADV"}
    for path in sorted((ROOT / "data/families").glob("annotated-*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        for family in data.get("families", []):
            for m in family.get("members", []):
                if not m.get("tone"):
                    continue
                syn = m.get("synset", "")
                upos = pos_of.get(syn[-2:], None)
                if upos:
                    done.add((m["word"].lower(), upos))
    return done


def anomaly_bucket(lemma, upos, count):
    """Why a content word has no WordNet entry. The buckets are the plan's
    section 9 list; they tell you whether the tagger is misfiring or the book is
    simply unusual, and they are the input to a later Anomaly Review."""
    if upos == "PROPN":
        return "proper-noun"
    if any(ch.isdigit() for ch in lemma):
        return "contains-digit"
    if len(lemma) <= 2:
        return "too-short"
    if not re.fullmatch(r"[a-z][a-z'\-]*", lemma):
        return "non-word-characters"
    if "-" in lemma:
        return "hyphenated-compound"
    if count == 1:
        return "hapax-possible-typo"
    return "unknown-word"


# --------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------

def coverage_curve(sorted_totals, total_tokens):
    """How many entries you need to cover N% of the content words a reader meets.

    This replaces 'we need 5,000 words'. The curve, not a target number, decides
    how many entries are worth generating (plan section 8)."""
    curve = []
    cum = 0
    idx = 0
    for point in COVERAGE_POINTS:
        need = point * total_tokens
        while cum < need and idx < len(sorted_totals):
            cum += sorted_totals[idx]
            idx += 1
        curve.append({"coverage": point, "entries": idx,
                      "tokens_covered": cum,
                      "share": round(cum / total_tokens, 4) if total_tokens else 0})
    return curve


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--book", required=True)
    ap.add_argument("--title", default=None)
    ap.add_argument("--author", default=None)
    ap.add_argument("--license", default="unspecified",
                    help="provenance, recorded verbatim; the pipeline does not police it")
    ap.add_argument("--out", default=None, help="override output directory")
    ap.add_argument("--max-sentences", type=int, default=0, help="0 = whole book")
    args = ap.parse_args()

    book_path = Path(args.book).expanduser()
    if not book_path.exists():
        raise SystemExit(f"no such file: {book_path}")

    book_id = sha256(book_path)[:16]
    out_dir = Path(args.out) if args.out else (OUT_ROOT / book_id)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"book_id  : {book_id}")
    print(f"source   : {book_path.name}")
    print(f"license  : {args.license}")
    print("extracting ...", file=sys.stderr)
    text = clean(extract(book_path))
    print(f"clean text: {len(text):,} characters", file=sys.stderr)

    nlp = load_nlp()
    lookup = wordnet_index()

    sentences = []
    forms = defaultdict(Counter)          # (lemma, pos) -> {surface: n}
    totals = Counter()                    # (lemma, pos) -> n
    samples = defaultdict(list)           # (lemma, pos) -> [sent_id]
    function_tokens = 0
    content_tokens = 0
    other_tokens = 0

    print("tagging ...", file=sys.stderr)
    for sent_text, toks in tag(text, nlp):
        if args.max_sentences and len(sentences) >= args.max_sentences:
            break
        sid = f"{book_id}_s{len(sentences):06d}"
        sentences.append({"sent_id": sid, "text": sent_text})
        for surface, lemma, upos in toks:
            if upos in CONTENT_POS:
                key = (lemma, upos)
                forms[key][surface] += 1
                totals[key] += 1
                content_tokens += 1
                if len(samples[key]) < SENTENCES_PER_LEMMA:
                    samples[key].append(sid)
            elif upos in ("PROPN",):
                other_tokens += 1
            else:
                function_tokens += 1

    print(f"sentences: {len(sentences):,}", file=sys.stderr)

    done = already_annotated()
    records = []
    anomalies = Counter()
    unmatched = []
    for (lemma, upos), n in totals.most_common():
        syns = lookup(lemma, upos)
        if not syns:
            bucket = anomaly_bucket(lemma, upos, n)
            anomalies[bucket] += 1
            unmatched.append({"lemma": lemma, "pos": upos, "count": n, "bucket": bucket})
        records.append({
            "lemma": lemma,
            "part_of_speech": upos,
            "corpus": {"total_occurrences": n, "book_count": 1,
                       "forms": dict(forms[(lemma, upos)])},
            "wordnet": {"found": bool(syns), "synsets": syns[:12],
                        "sense_count": len(syns)},
            "already_annotated": (lemma, upos) in done,
            "sentences": samples[(lemma, upos)],
        })

    sorted_totals = [r["corpus"]["total_occurrences"] for r in records]
    curve = coverage_curve(sorted_totals, content_tokens)

    # What a coverage point actually costs: entries that still need generating,
    # after subtracting what the corpus already covers and what WordNet cannot
    # support. This is the number that turns the curve into a spend decision.
    for point in curve:
        head = records[:point["entries"]]
        point["needs_generation"] = sum(
            1 for r in head if r["wordnet"]["found"] and not r["already_annotated"])
        point["already_have"] = sum(1 for r in head if r["already_annotated"])
        point["no_wordnet"] = sum(1 for r in head if not r["wordnet"]["found"])

    meta = {
        "book_id": book_id,
        "source_filename": book_path.name,
        "title": args.title,
        "author": args.author,
        "license_status": args.license,
        "sha256": sha256(book_path),
        "characters": len(text),
        "sentences": len(sentences),
        "content_tokens": content_tokens,
        "function_tokens": function_tokens,
        "proper_noun_tokens": other_tokens,
        "unique_lemma_pos": len(records),
        "pipeline": "extract -> clean -> sentencize -> tag -> contextual lemma -> aggregate",
        "tagger": "spacy en_core_web_sm, parser and NER excluded, rule sentencizer",
    }
    report = {
        "meta": meta,
        "coverage_curve": curve,
        "already_annotated_in_corpus": sum(1 for r in records if r["already_annotated"]),
        "wordnet_missing": len(unmatched),
        "anomaly_buckets": dict(anomalies.most_common()),
        "top_unmatched": unmatched[:40],
        "by_pos": dict(Counter(r["part_of_speech"] for r in records).most_common()),
    }

    (out_dir / "meta.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
    (out_dir / "report.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    with (out_dir / "sentences.jsonl").open("w", encoding="utf-8", newline="\n") as fh:
        for s in sentences:
            fh.write(json.dumps(s, ensure_ascii=False) + "\n")
    with (out_dir / "lemmas.jsonl").open("w", encoding="utf-8", newline="\n") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    # ---- the corpus report: the gate between free work and paid work ----
    print()
    print("=" * 66)
    print(f"CORPUS REPORT - {args.title or book_path.name}")
    print("=" * 66)
    print(f"  sentences            {len(sentences):>10,}")
    print(f"  content tokens       {content_tokens:>10,}   (noun/verb/adj/adv)")
    print(f"  function tokens      {function_tokens:>10,}   (ranked separately, section 6)")
    print(f"  proper-noun tokens   {other_tokens:>10,}")
    print(f"  unique lemma+POS     {len(records):>10,}")
    print(f"  already annotated    {report['already_annotated_in_corpus']:>10,}   from your existing corpus")
    print()
    print("  COVERAGE CURVE - the curve decides how many entries, not a target number")
    print(f"    {'cover':<8}{'entries':>9}{'have':>8}{'no-wn':>8}{'to generate':>13}")
    for p in curve:
        print(f"    {int(p['coverage']*100):>3}%    {p['entries']:>9,}{p['already_have']:>8,}"
              f"{p['no_wordnet']:>8,}{p['needs_generation']:>13,}")
    print()
    print(f"  WORDNET MISSES       {len(unmatched):,} lemma+POS with no synset")
    for bucket, n in anomalies.most_common():
        print(f"    {bucket:<24}{n:>7,}")
    print()
    print(f"  wrote {out_dir}")
    print("  STOP. Nothing here spent a token. Pick a coverage target and a spend")
    print("  cap before Lane C runs.")


if __name__ == "__main__":
    main()
