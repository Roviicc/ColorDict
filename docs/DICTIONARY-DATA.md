# Where to get dictionary data — and how it performs

Notes on sourcing **meaning, part of speech, examples and connotation** for
ColorDict, plus measured numbers for building a dictionary of our own.

## The short version

Meaning, part of speech and examples are easy and free. **Connotation is the
hard one** — standard dictionaries do not encode it as data. You get it by
joining a second, separate lexicon onto the first. So the practical answer is
two layers merged at build time.

## Layer 1 — meaning, part of speech, examples

| Source | Gives you | Licence | Notes |
| --- | --- | --- | --- |
| [Open English WordNet](https://en-word.net/) | POS, glosses, examples, synonyms, antonyms, hypernyms | **CC BY 4.0** | ~120k synsets, 2025 edition. Cleanest licence of the lot |
| [Wiktionary via wiktextract / kaikki.org](https://kaikki.org/dictionary/rawdata.html) | Senses, glosses, examples, IPA, etymology, **usage tags** | CC BY-SA 4.0 | JSONL, one object per entry. ~2.6 GB gzipped, ~22.9 GB raw, refreshed weekly. Richest source available |
| GCIDE | Definitions, POS | GPL (Webster 1913 base) | Public-domain derived, reads archaic |
| Merriam-Webster / Wordnik / Oxford APIs | Curated, modern | Free tiers are non-commercial; Oxford is paid | **Online only** — conflicts with an offline-first app |

## Layer 2 — connotation

Ordered by how well they fit this app:

- **Wiktionary usage tags** — sense-level labels such as `derogatory`,
  `offensive`, `informal`, `vulgar`, `humorous`, `formal`. Already attached to
  the correct *sense* in the kaikki JSON, so no join is required. This is the
  most direct free connotation data that exists.
- **[SentiWordNet 3.0](https://github.com/aesuli/SentiWordNet)** —
  positivity / negativity / objectivity per WordNet synset, each in [0,1] and
  summing to 1. **CC BY-SA 4.0, commercial use permitted with attribution.**
  Keyed to WordNet synsets, so it joins cleanly onto Layer 1, and it is scored
  *per sense* rather than per word — which matters, because "cheap" is negative
  as a judgement of quality but neutral as a statement about price.
- **[NRC VAD](https://saifmohammad.com/WebPages/nrc-vad.html) and EmoLex** —
  20k words scored for valence/arousal/dominance, or eight discrete emotions.
  High quality, but **free for research only; commercial use requires a paid
  licence from NRC.** Check this before shipping.
- **VADER** (MIT, ~7.5k entries) and **AFINN** (ODbL, ~2.5k) — small,
  word-level, permissive. Reasonable fallbacks.

## Licence trap worth knowing

This app is MIT. Bundling CC BY-SA data does **not** relicense the app's code —
they are separate works — but the **dictionary file itself** remains CC BY-SA:
attribution is required and modifications must be shared alike.

If you want to avoid share-alike entirely, **Open English WordNet alone
(CC BY 4.0)** is the clean path. Add SentiWordNet only once you accept BY-SA
on the data file. Wiktionary is CC BY-SA 4.0 as well.

## Recommended pipeline

1. Take Open English WordNet as the spine: headword, POS, gloss, examples.
2. Join SentiWordNet on the synset id for sense-level connotation scores.
3. Optionally enrich from the kaikki Wiktionary dump for usage labels and
   extra examples.
4. Emit TSV and build a StarDict set with the tooling already in this repo:

   ```bash
   python3 tools/stardict_make.py merged.tsv out/ enriched \
       --bookname "Enriched English Dictionary" --dictzip
   python3 tools/verify_stardict.py out/enriched.ifo --dump 5
   ```

For something to test with today, [FreeDict](https://freedict.org/downloads/)
and [this StarDict collection](https://tuxor1337.frama.io/firedict/dictionaries.html)
publish ready-made files, WordNet conversions included.

## How our own database performs — measured

Measured with `tools/bench/`, against the engine in this repo, on a JVM
capped at `-Xmx512m` to approximate an Android heap. Reproduce with:

```bash
python3 tools/bench/gen_big.py 150000 /tmp/bench-150k dz
javac -cp <engine-classes> -d /tmp/bench-classes tools/bench/Bench.java
java -Xmx512m -cp "<engine-classes>:/tmp/bench-classes" Bench /tmp/bench-150k/big.ifo
```

| Entries | Index load | Heap held | Lookup + article read | .idx size |
| --- | --- | --- | --- | --- |
| 150,000 (WordNet scale) | **76 ms** | **8 MB** | **0.088 ms** average | 3.1 MB |

Reading of the numbers:

- **Lookup is not a concern.** 0.088 ms per lookup *including* inflating the
  dictzip chunk and rendering the article to HTML. Binary search over the
  in-memory index is ~17 comparisons at this size; the article read dominates,
  and it is still an order of magnitude faster than a frame at 60 fps.
- **Memory is the real budget.** 8 MB for 150k entries is roughly 56 bytes per
  entry, most of it the per-headword `byte[]` object overhead. Extrapolating
  linearly, a **1M-entry Wiktionary-scale index would hold ~55 MB** — workable
  on a modern phone, uncomfortable on a low-end one, and it is paid per
  enabled dictionary.
- **Load cost is per dictionary, once.** 76 ms happens on the background
  thread the first time a dictionary answers a query, so it never blocks the UI.

Caveat on the file sizes: the benchmark's synthetic articles are repetitive, so
dictzip squeezed 33 MB down to 1.4 MB. Real dictionary prose compresses closer
to 3–4×, not 24×, so size the `.dict.dz` from your own data.

### If the index outgrows memory

Two options, in order of effort:

1. **Memory-map the `.idx`** and binary-search it on disk instead of parsing it
   into `byte[][]`. Cuts the per-entry object overhead to zero at the cost of
   page faults during search. This is a contained change to `StarDictIndex`.
2. **Move to SQLite** for our own dictionary and keep StarDict only for
   imported third-party files.

### StarDict or SQLite for our own data?

This is the more important design question, and it is not about speed.

StarDict is a *key → blob* store: it answers "what is the article for this
headword" and nothing else. If all you want is to **display** connotation, POS
and examples, encode them as HTML inside the article and StarDict is fine —
no app changes needed at all, since the renderer already handles HTML.

If you want to **query by field** — "all adjectives with negative connotation",
"every sense whose example contains X", "sort by valence" — StarDict cannot do
it, and you want SQLite with a real schema plus an FTS5 index:

```sql
CREATE TABLE entry  (id INTEGER PRIMARY KEY, headword TEXT NOT NULL);
CREATE TABLE sense  (id INTEGER PRIMARY KEY, entry_id INTEGER NOT NULL,
                     pos TEXT, gloss TEXT NOT NULL,
                     connotation TEXT, valence REAL);
CREATE TABLE example(id INTEGER PRIMARY KEY, sense_id INTEGER NOT NULL, text TEXT);
CREATE INDEX idx_entry_headword ON entry(headword COLLATE NOCASE);
```

Android ships SQLite, and this project already uses `SQLiteOpenHelper` for
history and bookmarks, so the plumbing exists. The cost is a second dictionary
provider alongside the StarDict one, behind a shared interface.

**Recommendation:** if the goal is showing richer entries, stay on StarDict and
put the structure in the article HTML — zero new code. Move to SQLite only when
you actually need to filter or sort on these fields.
