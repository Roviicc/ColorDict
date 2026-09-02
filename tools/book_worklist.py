#!/usr/bin/env python3
"""Rank family candidates by how often a reader actually meets them in prose.

`worklist_build.py` ranks by wordfreq Zipf, which is a corpus-wide prior mixing
web, subtitles and books. That was already an improvement on a top-10k list
(11.61), but it still answers "how common is this word in English" rather than
"will a reader meet this word and want to know how it lands".

A book answers the second question directly, and until the report loop has real
users (11.83) it is the closest available stand-in for demand: prose is where a
reader meets a word whose force the gloss will not give them.

What this does NOT change is the gate. Frequency alone queues neutral words -
11.62 measured that for adjectives (*finished*, *whole*, *normal*), and it is
worse for nouns, where the most frequent words in any book are *man*, *time*,
*hand*, *eye*. So the size and charged-fraction gates are inherited from
`worklist_build.py` unchanged; only the ranking source is different.

Nouns get one extra gate. The noun line was closed because the filter cannot
separate a bad thing from a loaded word: *pneumonia* and *tranquilizer* score
high because the referent is unpleasant, not because the word does anything to
what it is aimed at (11.71 Stage E). A family whose members are uniformly
negative is describing a bad thing; a family that carries connotation has
members pulling in different directions - *hovel* against *mansion*. So a noun
family must contain at least one positive and one negative member.

That test uses SentiWordNet, which 11.5 rejected as a label because it ordered
*skinny* above *slender*. Using it for sign spread rather than for ordering is a
much weaker claim on the same data: we are not asking it which member is kinder,
only whether the family disagrees with itself at all.

The corpus is not in the repo - it is ten public-domain books, ~1.2M words,
fetched from Project Gutenberg as https://www.gutenberg.org/cache/epub/<id>/pg<id>.txt

    1342  Pride and Prejudice        84  Frankenstein
    2701  Moby-Dick                1661  The Adventures of Sherlock Holmes
      98  A Tale of Two Cities       74  The Adventures of Tom Sawyer
     345  Dracula                    76  Huckleberry Finn
     158  Emma                     2542  A Doll's House

Its known bias is period: Gutenberg is overwhelmingly pre-1929, so the ranking
leans literary and slightly archaic. That is the right direction for this
dictionary - a reader meets *sagacity* and *composure* in books, not in
subtitles (11.61 rejected OpenSubtitles for exactly this) - but it is a bias,
not a neutral sample, and it should be replaced by the real report log as soon
as one exists (11.83).

Usage:
    python tools/book_worklist.py --pos v --books ~/books --out data/worklist-verbs-book.tsv
"""

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import worklist_build as wb  # noqa: E402  - gates and joins are inherited, not reimplemented

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "data/build/book-counts.json"
WORD_RE = re.compile(r"[a-z][a-z'\-]+")
POS_TAG = {"a": "a", "n": "n", "v": "v"}

# A family has to actually appear in the prose, not merely be eligible. One
# member showing up once is a coincidence; two is the family being used.
MIN_BOOK_MEMBERS = 2


def polysemy(families):
    """word -> how many synsets of this part of speech contain it.

    Raw book counts belong to a lemma, not to a sense, and a lemma's count is
    earned almost entirely by its commonest sense. Left uncorrected this is the
    gloss-binding fault (11.65) one layer up in selection: the first run of this
    tool put *sleep together* on top of the verb queue with 24,739 hits, all of
    them earned by *have*, *know* and *take* doing their ordinary work elsewhere
    in the sentence.

    Dividing a member's count by how many synsets its lemma belongs to is a
    blunt correction, and a deliberately blunt one - it does not claim to know
    which sense was meant, only that a word spread across forty senses cannot
    lend its whole frequency to any one of them.
    """
    from collections import defaultdict
    seen = defaultdict(set)
    for family in families:
        for m in family.get("members", []):
            w = (m.get("word") or "").lower()
            if w:
                seen[w].add(m.get("synset"))
    return {w: len(s) for w, s in seen.items()}


def strip_gutenberg(text):
    """Drop the licence header and footer so boilerplate does not enter counts."""
    start = re.search(r"\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG.*?\*\*\*", text, re.S)
    if start:
        text = text[start.end():]
    end = re.search(r"\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG", text)
    if end:
        text = text[:end.start()]
    return text


def corpus_counts(books_dir, pos):
    """lemma -> occurrences, for one part of speech, over every .txt in books_dir."""
    from nltk.stem import WordNetLemmatizer

    lem = WordNetLemmatizer()
    raw = Counter()
    joined = []
    for path in sorted(Path(books_dir).glob("*.txt")):
        text = strip_gutenberg(path.read_text(encoding="utf-8", errors="ignore")).lower()
        joined.append(text)
        raw.update(WORD_RE.findall(text))

    counts = Counter()
    for token, n in raw.items():
        counts[lem.lemmatize(token, POS_TAG[pos])] += n
    return counts, "\n".join(joined)


def load_cache(pos):
    if not CACHE.exists():
        return None
    blob = json.loads(CACHE.read_text(encoding="utf-8"))
    entry = blob.get(pos)
    return Counter(entry["counts"]) if entry else None


def save_cache(pos, counts, books_dir):
    blob = json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else {}
    blob[pos] = {
        "books": sorted(p.name for p in Path(books_dir).glob("*.txt")),
        "distinct_lemmas": len(counts),
        "counts": dict(counts),
    }
    CACHE.write_text(json.dumps(blob), encoding="utf-8")


def member_hits(word, counts, text):
    """Occurrences of one member. Multi-word members are counted as a phrase."""
    w = word.lower()
    if " " in w:
        return text.count(w)
    return counts.get(w, 0)


def build(pos, labels, counts, text):
    families = json.loads((ROOT / f"data/build/{pos_name(pos)}-families.json").read_text(encoding="utf-8"))
    if isinstance(families, dict):
        families = families.get("families", list(families.values()))

    done = wb.annotated_synsets()
    held = wb.held_families()
    poly = polysemy(families)

    rows = []
    for family in families:
        members = family.get("members", [])
        member_labels = [labels.get(m.get("synset")) for m in members]
        labelled = sum(1 for l in member_labels if l)
        charged = sum(1 for l in member_labels if l in ("positive", "negative"))
        positives = sum(1 for l in member_labels if l == "positive")
        negatives = sum(1 for l in member_labels if l == "negative")

        hits = [member_hits(m.get("word", ""), counts, text) for m in members]
        raw_hits = sum(hits)
        book_hits = round(sum(
            h / max(1, poly.get((m.get("word") or "").lower(), 1))
            for h, m in zip(hits, members)
        ))
        book_members = sum(1 for h in hits if h > 0)
        peak_word = ""
        if members and max(hits) > 0:
            peak_word = members[hits.index(max(hits))].get("word", "")

        size = len(members)
        charged_pct = round(charged / labelled, 2) if labelled else 0.0
        is_held = wb.core(family["id"]) in held
        is_done = any(m.get("synset") in done for m in members)

        eligible = (
            size >= wb.MIN_SIZE
            and charged_pct >= wb.MIN_CHARGED_PCT
            and book_members >= MIN_BOOK_MEMBERS
            and not is_held
            and not is_done
        )
        # `contrast` was written as the noun gate - the idea being that a family
        # which only disagrees with the world, never with itself, is describing a
        # thing rather than carrying a charge. It was tested against the two
        # families Stage E was closed over and it failed: SentiWordNet hands the
        # *pneumonia* family a stray positive, so sign spread passes it. What
        # actually keeps *pneumonia* out is that nobody writes it in prose -
        # book_hits is 0 across the whole corpus, while *sagacity*, *composure*
        # and *misery* are everywhere.
        #
        # So the book is doing the work the filter could not, and the filter is
        # kept as a reported column rather than promoted to a gate it does not
        # earn. Left as a gate it also rejects *sadness*, *anxiety* and
        # *liveliness*, whose members contrast in degree rather than in sign.
        contrast = positives >= 1 and negatives >= 1

        rows.append({
            "family_id": family["id"],
            "head": (family.get("head_words") or [""])[0],
            "size": size,
            "book_hits": book_hits,
            "raw_hits": raw_hits,
            "book_members": book_members,
            "peak_word": peak_word,
            "charged": charged,
            "labelled": labelled,
            "charged_pct": charged_pct,
            "pos_ct": positives,
            "neg_ct": negatives,
            "contrast": int(contrast),
            "held": int(is_held),
            "done": int(is_done),
            "eligible": int(eligible),
        })

    rows.sort(key=lambda r: (-r["eligible"], -r["book_hits"], -r["size"]))
    return rows


def pos_name(pos):
    return {"a": "adjective", "n": "noun", "v": "verb"}[pos]


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--pos", choices=["a", "n", "v"], required=True)
    ap.add_argument("--books", required=True, help="directory of plain-text books")
    ap.add_argument("--bulk", default="data/entries/derived-bulk.jsonl")
    ap.add_argument("--out", required=True)
    ap.add_argument("--refresh", action="store_true", help="recount the corpus")
    args = ap.parse_args()

    counts = None if args.refresh else load_cache(args.pos)
    if counts is None:
        counts, text = corpus_counts(args.books, args.pos)
        save_cache(args.pos, counts, args.books)
    else:
        _, text = corpus_counts(args.books, args.pos) if False else (None, "")
        text = "\n".join(
            strip_gutenberg(p.read_text(encoding="utf-8", errors="ignore")).lower()
            for p in sorted(Path(args.books).glob("*.txt"))
        )

    labels = wb.load_labels(ROOT / args.bulk)
    rows = build(args.pos, labels, counts, text)

    out = ROOT / args.out
    cols = list(rows[0].keys())
    with out.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write("\t".join(cols) + "\n")
        for r in rows:
            fh.write("\t".join(str(r[c]) for c in cols) + "\n")

    eligible = [r for r in rows if r["eligible"]]
    print(f"{pos_name(args.pos)}: {len(rows)} families, {len(eligible)} eligible "
          f"({len(eligible)/len(rows):.1%}), {sum(r['size'] for r in eligible)} members")
    print(f"wrote {out}")
    for r in eligible[:12]:
        print(f"    {r['head']:<18} size={r['size']:<4} book_hits={r['book_hits']:<6} "
              f"members_seen={r['book_members']:<3} charged={r['charged_pct']}")


if __name__ == "__main__":
    main()
