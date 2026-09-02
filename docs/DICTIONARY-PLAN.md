# Pop Up English Dictionary — execution plan

How we build our own bundled dictionary: what we generate, what we curate by
hand, **what we deliberately leave alone**, and in what order.

Companion to [DICTIONARY-DATA.md](DICTIONARY-DATA.md), which covers sourcing,
licensing and measured engine performance. This document is the execution side:
batches, gates, and definition of done.

---

## 1. Success vision

A user installs the app and, with no setup at all, looks up a word and gets a
card that shows — for each sense separately — the meaning, the part of speech,
whether the word carries positive, negative or neutral weight *and why*, two
real examples, sense-specific synonyms and antonyms, and how the word is built
from its prefix, root and suffix.

That is the thing no free offline dictionary currently does. Connotation is the
differentiator; everything else is table stakes.

Concretely, we are done when:

- The app ships with **~120,000 headwords offline**, zero network, first launch.
- The **top 1,000 words by frequency are human-curated** to the full quality bar.
- Lookup stays **under 1 ms** and the index holds **under 60 MB** (measured, not assumed).
- Every entry carries **provenance and a licence**, and the app displays both.
- Anyone can regenerate the whole dictionary from source with **one command**.
- Adding the next 1,000 curated words is a **routine batch**, not a project.

The failure mode we are explicitly designing against: a beautiful pipeline with
40 finished words in it. Shipping breadth is automated; depth accrues over time.

---

## 2. Settled decisions

| Question | Decision | Why |
| --- | --- | --- |
| Semantic baseline | **Open English WordNet** (CC BY 4.0) | Only large lexicon we may legally copy *and* modify with plain attribution |
| Connotation | **SentiWordNet 3.0** joined on synset id, + Wiktionary usage tags later | Per-sense, not per-word. "Cheap" differs by sense |
| Master format | **JSONL**, one entry per line | Diffable, appendable, streamable, batch-friendly |
| Ship format | **StarDict**, `sametypesequence=h` | `ArticleHtml.renderBlock` passes `h` through verbatim — zero engine changes |
| Storage | StarDict, **not** SQLite | We display structure, we do not query by field. Revisit only if filtering is needed |
| Hand-authoring | **Curation, not creation** | Reviewing a draft is 3–5 min; writing from scratch is 15–20. Same output |

**We do not scrape Oxford or Merriam-Webster**, and we do not systematically
copy them. They are occasional human cross-checks only.

---

## 3. Repository layout

```
data/
  worklist.tsv                 frequency-ranked headwords, our unit of work
  entries/
    batch-0001.jsonl           ranks     1-100
    batch-0002.jsonl           ranks   101-200
    ...
    derived-bulk.jsonl         everything else, machine-generated
  policy/
    editorial-policy.md        Phase 1 rules
    sensitive-terms.tsv        manual-only queue (see 5.3)
  build/                       generated, gitignored
tools/
  dict_schema.json             entry schema
  dict_validate.py             all mechanical checks
  wordnet_import.py            WordNet + SentiWordNet -> JSONL
  dict_build.py                JSONL -> TSV/HTML -> stardict_make.py
  stardict_make.py             EXISTS
  verify_stardict.py           EXISTS
```

---

## 4. Quality tiers

Every sense carries `editorial.status`. **All three tiers ship.** The licence
permits it, so review is a quality overlay, never a release gate.

| Tier | Meaning | Human time |
| --- | --- | --- |
| `derived` | Machine-generated from WordNet + SentiWordNet. Validated, unreviewed | 0 |
| `reviewed` | A human read it, fixed errors, accepted it | ~2 min |
| `curated` | Full bar: original connotation explanation, 2 checked examples, affix analysis | ~5 min |

The app labels the tier. A `derived` entry is honest about what it is.

---

## 5. Batch policy

### 5.1 The batch ladder — 6 batches, B0 to B5

Enrichment is a **model pass, not a human pass**. Humans calibrate it once and
audit samples of it; they do not read 5,000 entries. That turns the schedule
from months into days.

**One batch = one release.** Every rung leaves the app shippable, so we can
stop anywhere and still have a working product.

| Batch | Scope | Size | Who | Wall clock | Ships as |
| --- | --- | --- | --- | --- | --- |
| **B0** | Filtered WordNet + SentiWordNet join | ~120,000 | script | ~10 min | **v0.1** — first offline dictionary |
| **B1** | Calibration pilot (see 5.1.1) | 100 | human + model | 2–4 h | **v0.2** — pipeline and prompts proven |
| **B2** | Top 1,000 by frequency, enriched + verified | 1,000 | model | ~15 min | v0.5 |
| **B3** | Top 5,000, enriched + verified | 5,000 | model | ~1 h | **v1.0** — public release |
| **B4** | Top 20,000, enriched + verified | 20,000 | model | ~4 h | v1.5 |
| **B5** | Sensitive-terms queue + sampled audit | ~300 | human | ~4 h | **v2.0** — complete |

**Total: 1–2 days**, tooling included. Compute cost replaces labour cost —
budget for roughly 1–2 M output tokens at B3 scale, and prefer a cheap fast
model for enrichment with a stronger one reserved for verification.

**Stop-anywhere rungs.** B0 alone is a usable dictionary. **B3 (v1.0) is the
real target.** B4 is optional depth. B5 is not optional — see 5.5.

**B0 runs first, not last.** It is a script. Ship it in week one; enrichment
raises tiers underneath a dictionary that already works.

### 5.5 What automation does not fix

Speed is free. Correctness is not. A model will produce fluent, confident
connotation explanations at 5,000-per-hour — **including the wrong ones**, in
exactly the same tone as the right ones. Connotation is the entire reason this
app is worth building, so an unverified enrichment pass would automate away the
only thing that differentiates us.

Four controls, none of which require reading every entry:

1. **Ground, do not generate.** Connotation label comes from the SentiWordNet score, not from the model's opinion. The model writes the *explanation* for a label it was handed. It is not asked "is this positive?"
2. **The 5.4 fabrication rule, enforced in code.** Near-neutral score and no usage tag emits `neutral` with an empty explanation. The validator rejects any explanation on a neutral sense. This is a hard gate, not a prompt instruction.
3. **Independent verification pass.** A second model call sees only the finished entry and answers a narrow question — does the explanation match the label, does each example actually use the headword in that sense, is the affix analysis real. It returns agree/disagree, not prose. Disagreements drop to `derived` rather than blocking the batch.
4. **Sampled human audit — B5.** 50 random entries per 1,000, read properly, to *measure* the error rate rather than assume it. If B3's sample comes back above ~5% wrong, the fix is the prompt, and we re-run the hour.

We are trading a zero-error-rate assumption we were never going to achieve for
a **measured** error rate we can publish. `derived` entries are already labelled
as unreviewed in the app, so this is honest either way.

Sensitive terms stay human-only regardless of throughput. That list is a few
hundred words, not five thousand.

### 5.1.1 What is in B1

B1 is picked for *pipeline coverage*, not frequency alone. It is the only batch
chosen by hand, because it has to exercise every code path before we commit to
19 more. Required composition:

| Must include | Count | Examples |
| --- | --- | --- |
| Nouns | 25 | *time, hope, failure, weight* |
| Verbs | 25 | *run, hold, dismiss, achieve* |
| Adjectives | 25 | *cheap, slender, stubborn, generous* |
| Adverbs | 15 | *barely, frankly, hardly* |
| Function words | 10 | *the, of, and* — proves the validator does not demand connotation |
| Multi-sense words | ≥ 20 | *cheap, run, light, fine* |
| Real prefix or suffix | ≥ 20 | *unhelpful, reorganization, careless* |
| No valid affix analysis | ≥ 10 | *dog, understand, table* — proves `analysable: false` works |
| Clear positive connotation | ≥ 15 | *generous, elegant, thrive* |
| Clear negative connotation | ≥ 15 | *stubborn, reckless, squander* |
| Sensitive usage | ≥ 5 | routed to the manual queue per 5.3 |

If B1 passes validation and looks right on the emulator, B2–B18 are the same
work with the word list chosen by frequency rank instead of by hand.

### 5.1.2 Cadence

B0–B3 can all land inside two days. The only reason to slow down is B1: the
calibration pilot exists so that the prompts are wrong on 100 entries instead
of on 5,000. **Do not skip it to save two hours** — a bad prompt discovered at
B3 costs the whole B3 run.

Ship a release at the end of every batch. Even at this speed, users getting
v0.1 on day one and v1.0 on day two is strictly better than both arriving
together, because B0 feedback tells us what B3 should prioritise.

### 5.2 Excluded from the build entirely

WordNet contains a great deal that is not an English dictionary. Filter at
import:

- **Proper nouns and named entities** — `Abraham_Lincoln`, `Chicago`. Encyclopedia content; belongs in a separate optional dictionary if ever.
- **Taxonomic binomials** — `Felis_catus`, `genus_Quercus`. Thousands of them, useless in a pop-up.
- **Headwords containing digits or non-letter characters**, except hyphen and apostrophe.
- **Single-letter headwords** other than *a* and *I*.

Expected removal: roughly 25–30% of raw WordNet synsets.

### 5.3 In the build, but never auto-curated

These ship as `derived` and are **excluded from curation batches** unless
deliberately queued:

- **Slurs and sensitive terms.** Never let a model draft a connotation explanation for these unreviewed. They route to `data/policy/sensitive-terms.tsv` and get human handling under the Phase 1 policy, or they ship with the usage label only and no explanation. This is the single most important exclusion in the plan.
- **Function words** — *the*, *of*, *and*. Gloss and POS are fine; connotation and affix analysis are meaningless. The validator must not demand those fields, and curation time is better spent elsewhere.
- **Archaic and obsolete terms** — real headwords, low value per minute of review.
- **Multi-word expressions** — keep them (normalise `hot_dog` to `hot dog`), but `derived` only.
- **Rank > 50,000** — long tail. `derived` forever unless a user reports a problem.

### 5.4 The fabrication rule

**Never invent a connotation.** If SentiWordNet is near-neutral and there is no
Wiktionary usage tag, emit `"label": "neutral"` with **no explanation string**.
An empty field is honest; a confidently wrong one poisons the differentiator.

Same for affixes: if there is no productive analysis, set
`word_formation.analysable: false` and stop. `understand` has no prefix *un-*.

---

## 6. Schema changes from the original draft

- `word_formation.prefixes` / `.suffixes` are **arrays** and optional, plus an `analysable` boolean. `reorganization` has two suffixes; `dog` has none.
- Every sense gets a **stable `id`**, so review state survives revisions.
- Add **`inflections`** — the engine reads `.syn` files, so feeding inflected forms there is what makes "running" find "run". Highest-value lookup win available, and it was missing.
- `connotation` keeps `label`, gains optional `score` (SentiWordNet) and a separate `usage_labels` array (`informal`, `derogatory`, `vulgar`).
- `editorial.status` uses the three tiers above, not a six-stage workflow.

---

## 7. Editorial workflow

Three states, not six. The validator carries every mechanical check; humans
only do what humans can do.

```
derived  --review-->  reviewed  --full bar-->  curated
```

The review step is one sitting with a checklist — correctness, originality,
bias, duplicate senses — recorded as flags in `editorial`, not as separate
states. Six sequential named gates across 20,000 senses will stall; this will
not.

---

## 8. Validator checks (`dict_validate.py`)

Per batch, fast; plus a corpus pass for anything cross-batch.

**Per entry:** required fields; valid POS; valid connotation label; label agrees
with score sign; examples present, distinct, and actually containing the
headword or an inflection; synonyms and antonyms disjoint; no self-reference;
affix arrays well-formed and consistent with `analysable`; sources present for
`reviewed` and above; JSON conforms to schema.

**Per corpus:** duplicate headwords; duplicate senses within a headword;
near-duplicate definitions across entries (normalised token Jaccard, flag above
0.8); dangling synonym or antonym targets; sense ids unique.

**Reported per batch:** pass rate, reject reasons ranked. A drop in batch
pass-rate is our early warning that a prompt or import step regressed — catch it
at entry 400, not entry 4,000.

---

## 9. Build pipeline (as implemented)

```bash
# one-time source downloads (all gitignored under data/source/)
curl -Lo data/source/english-wordnet-2024.xml.gz https://en-word.net/static/english-wordnet-2024.xml.gz
curl -Lo data/source/ili-map-pwn30.tab https://cdn.jsdelivr.net/gh/globalwordnet/cili@master/ili-map-pwn30.tab
# SentiWordNet_3.0.0.txt from https://github.com/aesuli/SentiWordNet (data/)

python3 tools/wordnet_import.py \
    --oewn data/source/english-wordnet-2024.xml.gz \
    --swn data/source/SentiWordNet_3.0.0.txt \
    --ili-map data/source/ili-map-pwn30.tab \
    --out data/entries/derived-bulk.jsonl
python3 tools/dict_validate.py data/entries/
python3 tools/dict_build.py data/entries/ --out data/build   # runs stardict_make + verify itself
cp data/build/popup-en.{ifo,idx,dict.dz,syn} app/src/main/assets/dicts/popup-en/
```

Notes learned building B0:

- **The SentiWordNet join must go through ILI.** OEWN 2024 renumbered most
  synsets away from Princeton 3.0 offsets; joining on the raw offset matches
  only 0.6% of senses. Joining `synset@ili` through CILI's
  `ili-map-pwn30.tab` matches **97.7%**.
- `--sts h`: the bundled sample glossary uses `m` (plain text); ours needs `h`
  for structured articles. `ArticleHtml` passes type-h through untouched.
- 32-bit offsets suffice (the .dict is ~57 MB raw); `dict_build.py` invokes
  `stardict_make.py` with its defaults.
- Test without an emulator: `./run-desktop.sh --dict data/build --lookup cheap`.

## 9.1 B0 status — DONE (2026-08-29)

| Measure | Result |
| --- | --- |
| Entries | **111,466 headwords**, 166,986 senses |
| Validation | 0 errors (53k warnings, expected derived-tier example flags) |
| SentiWordNet coverage | 97.7% of senses; 13,453 positive / 17,090 negative labels |
| Filtered out | 55,069 proper nouns, 2,176 bad-char, 383 single-letter, 215 instance senses |
| Ship size | 8.9 MB .dict.dz + 2.2 MB .idx + 58 KB .syn |
| Wall clock | import 10 s · validate 3 s · build+verify 8 s |
| App | bundled under assets/dicts/popup-en/, installer generalized, CSS added, APK builds |

---

## 10. Phase order and estimates

| # | Work | Owner | Estimate |
| --- | --- | --- | --- |
| 0 | Editorial policy doc | human | 2–3 h |
| 1 | `dict_schema.json` + `dict_validate.py` | code | 2–3 h |
| 2 | `wordnet_import.py` incl. 5.2 filters and 5.4 rule | code | 3–4 h |
| 3 | `dict_build.py` + CSS classes in `defaultCss` | code | 2–3 h |
| 4 | B0 run, bundle as default, emulator test | code | 2–3 h |
| 5 | `dict_enrich.py` — batched model pass, resumable | code | 3–4 h |
| 6 | `dict_verify_llm.py` — independent check pass | code | 2 h |
| 7 | B1 calibration pilot | human + model | 2–4 h |
| 8 | B2/B3/B4 runs | compute | ~5 h wall clock |
| 9 | B5 sensitive queue + sampled audit | human | ~4 h |

**Total: 1-2 working days for the tooling.** Authoring is the remaining clock - see 11.6. The long pole is now compute and prompt
calibration, not review hours. The tooling cost is paid once and is independent
of whether the dictionary ends at 1,000 enriched entries or 20,000.

---

## 11. Definition of done, per batch

1. `dict_validate.py` passes with zero errors.
2. Pass rate recorded and compared against the previous batch.
3. Build regenerates and `verify_stardict.py` passes.
4. Spot-check 10 random entries in the running app on the emulator.
5. Batch committed as its own `.jsonl` file, one commit.

---

## 11.5 The Connotation Dictionary — family stage (adopted)

Connotation is authored per **family** — a WordNet adjective cluster (head +
satellites) paired with its opposite via antonym links — not per isolated
word: *miserly −3 … prudent +3*, one behaviour, seven verdicts. This replaces
SentiWordNet as the connotation engine for evaluative words; our own pilot
confirmed SWN is wrong at this resolution (it scored skinny's stingy sense
positive). Charge (−3..+3) maps to `connotation.score` (÷3), register to
`usage_labels`, the note to `tone`. Families also render as a spectrum row
inside each member's article — no app changes needed.

Steps: **01 extract** (`tools/family_extract.py` — done: 6,826 candidates,
20,566 words, 3,845 antonym-linked on OEWN 2024) → **02 screen** evaluative vs
taxonomic (~600 real families expected) → **03 rank + annotate** (charge,
register, note; overlay files, 200 families per batch, resumable) →
**04 fill missing examples** (same overlays) → **05 assemble** through the
existing validate/build gates. Order: adjectives, verbs, nouns, adverbs; stop
after any stage. Settled: −3..+3 resolution; slurs kept with warnings per 5.3
(worst tier omitted); SentiWordNet demoted to unlabeled-score prior only.

## 11.6 Progress log

| Date | Milestone |
| --- | --- |
| 2026-08-29 | **B0 shipped.** 111,466 headwords derived, validated, bundled, APK builds |
| 2026-08-29 | **Tier-2 probe passed.** 4 families, 12/12 golden pairs, ~836 authored tokens per family |
| 2026-08-30 | **Stage 1 shard 2.** 10 adjective families (good/bad, clever/stupid, beauty/ugliness, rich/poor, brave/cowardly) |
| 2026-08-30 | **Tier + example fixes.** Authored entries promote `derived → reviewed`; 1,120 off-target inherited examples pruned |
| 2026-08-30 | **Stage 2 opened.** Verb families via hypernym siblings: 2,495 families / 11,257 words; dying, weeping, laughing, stealing annotated |
| 2026-08-30 | **Feasibility shards.** Nouns work unchanged (9,359 families / 75,017 words). Adverbs cannot be grouped at all — solved by morphological inheritance instead (`adverb_inherit.py`) |
| 2026-08-30 | **Worksheet tool.** `family_worksheet.py` does the clerical half: finds the family, resolves every sense id, leaves only charge and tone blank |
| 2026-08-30 | **Shard 4.** dishonest, genuine, friendly (46 members); `*word*` now renders as italics in tone notes |
| 2026-08-30 | **Reviewed entries stop carrying machine labels.** An unjudged sense on an authored entry no longer shows its SentiWordNet guess (318 dropped) |
| 2026-08-30 | **Shard 5.** 12 families / 148 members: refined, angry, generous, stubborn, careless, idle, polite; praise, criticize, boast, look, complain |
| 2026-08-30 | **`dict_pipeline.py`.** One command for the whole round; it discovers overlays instead of being handed them |
| 2026-08-30 | **Shard 6.** 10 families / 127 members: cruel, kind, greedy, stingy, selfish, humble, rude, dirty, cheerful, loyal |
| 2026-08-30 | **Shard 7.** 11 families / 142 members: sly, merciless, contented, unrefined, naive, wise, domineering, elegant, untidy, sincere, patient. First use of the 5.3 exclusion in anger — one sensitive synset left `derived` |

| 2026-08-30 | **Shard 8.** 9 families / 117 members: inferior, ill-natured, distrustful, strict, curious, noisy, worn, eager, grateful. **Reviewed entries cross 1,000** |

| 2026-08-30 | **Audit 002: 14%, down from 44%.** The rewrite worked; the remainder is sense misalignment, not note scope. See 11.66 |
| 2026-08-30 | **Audit 001 run — and failed.** 50 sampled senses, 44% wrong against a 5% threshold. Authoring paused; see 11.65 for the diagnosis and the tone-note rule it produced |
| 2026-08-30 | **Audit 003: 22%, a regression.** Seven of eleven failures were one fault — the note describing a different sense from the one its gloss names. The gloss is now binding; see 11.67 |
| 2026-08-30 | **Undefinable glosses swept.** 8 reviewed senses whose OEWN gloss is a usage restriction, not a definition, dropped back to `derived`; `gloss_lint.py` added and the worksheet pre-skips the other 233 corpus-wide |
| 2026-08-30 | **Audit 004: 20%.** The gloss-binding fix held where it was applied; two failures were the same synset fixed for one word only. Repairs are now synset-wide; see 11.68 |
| 2026-08-30 | **Adverbs gated on WordNet's pertainym.** An adverb takes the note for the adjective *sense* it points at, or none at all: 244 inherited senses → 154, 90 declined |
| 2026-09-02 | **Census 001: every unaudited sense read.** 767 senses, sixteen parallel readers, 25.8% wrong; 198 failures repaired synset-wide plus a 32-note sweep of their neighbours; see 11.69 |
| 2026-09-02 | **Adverb deny list.** Nine adverbs whose own gloss does not match the adjective sense they point at — the fifth fault class; inherited senses 154 → 145 |
| 2026-09-02 | **Audit 005: 0%, with a caveat.** 49 right, 1 unsure, drawn from the repaired corpus and read by the session that repaired it; a blind read is still owed before 5.5 unpauses. See 11.69 |
| 2026-09-02 | **Worklist built.** `worklist_build.py` ranks 5,911 adjective families by wordfreq Zipf; the eight hand-picked shards sit on the corpus centre (3.04 vs 2.90), unbiased but unprioritised. See 11.61 |
| 2026-09-02 | **Census 002: 3.8%, blind, and the gate passes.** All 927 senses read by sixteen Fable 5.1 readers with no sight of the corpus's history. The split is the finding: 2.6% where census 001 swept, 8.8% in the 171 senses its `--exclude` logic skipped. 41 senses repaired. See 11.70 |

**Current totals: 63 families, 781 annotated senses, 922 reviewed entries,
0 validation errors, measured error rate 3.8% (census 002, blind)** — including 145 adverb senses inherited for free, and 11
senses deliberately left `derived` because their gloss cannot carry a
judgement. **The validator's zero errors mean well-formed, not correct: the
audit is the only thing that measures correct. Every claim-carrying sense has
now been read once (census 001, 25.8% wrong, all repaired) and the sample drawn
afterwards reads 0% — by a reader who made the repairs (audit 005).**

Stage 1 (adjectives) is 54 of ~1,100 families; stage 2 (verbs) is 9 of ~2,495.
The machinery is complete; what remains is authoring.

### Grouping strategy differs by part of speech

`similar` is an **adjective-only** relation in WordNet, so only adjectives form
satellite clusters. Verbs and nouns are grouped by **hypernym siblings**
instead — every troponym of a parent is one way of doing the same thing, which
is where the connotation lives (`family_extract.py --pos v --min-size 4`).

**Adverbs cannot be grouped by the graph at all.** They have no hypernyms, no
`similar` clusters, and only nine adverb senses in OEWN 2024 carry a derivation
link. Morphology solves them instead: English adverbs are overwhelmingly an
adjective plus *-ly*, and 91.4% of the `-ly` adverbs in the corpus have their
base adjective present, so `harshly` inherits whatever `harsh` was given
(`adverb_inherit.py`). This turns adverbs from the hardest stage into a
by-product — re-run it after every adjective shard and the count grows for
free. Nothing is invented: an adverb whose adjective was never annotated is
skipped.

### Lessons the shards have taught

- **A single-polarity family renders most-extreme-first**, because the spectrum
  always sorts ascending by charge. Its axis label must therefore read
  harshest → mildest. This was written backwards twice, so `family_apply.py`
  now refuses to build such a family; all-positive families are exempt, as they
  ascend mild → strong the way their labels already read.
- **A family may need an axis other than condemning → praising.** Dying runs
  bluntest → gentlest, stealing gravest → lightest, weeping most contemptuous →
  most sympathetic.
- **WordNet splits families that belong together.** stingy/thrifty, and
  `excellent` sitting in the *superior* cluster rather than *good*. Annotation
  repairs these merges by hand; it is one of the things the authored layer adds.
- **Sense ids must be verified against the corpus, not assumed.** OEWN 2024 has
  no "steal" sense of *nick*, and *frugal* is not in the cluster its definition
  suggests. `dict_enrich_apply.py` fails loudly on an unknown id rather than
  silently dropping the annotation.
- **One word can belong to two families in the same shard.** *creaky* is worn
  (`oewn-02591968-s`) and it is noisy (`oewn-01927734-s`); *questioning* is both
  doubting and curious. The overlay is keyed by headword, so the two sense
  patches merge under one word and both spectra render — which is why shard 8
  reports 117 annotated senses but 115 overlay words. Nothing is lost; the
  count simply stops matching.
- **The sensitive exclusion lands on a synset, not a word.** The *untidy*
  family contains `oewn-02433489-s`, glossed "befitting a slut or slattern;
  used especially of women". All four of its members were left `derived` and
  unlabeled, not just the two most obvious ones — 5.3 applies to the sense, so
  it takes the whole synset with it. The rest of the family annotates normally.
- **A headword picks a family, not a meaning.** Asking the worksheet for
  *harsh* returns the grainy-texture cluster, *gloomy* returns literal
  darkness, and *gentle* returns the aristocracy. Reading the glosses before
  annotating is the screening step, and it discarded 4 of 14 candidates in
  shard 6.
- **Authored entries must not keep inherited examples.** WordNet attaches
  examples to the synset, so many illustrate a synonym ("a long scrawny neck"
  under *skinny*). Pruning happens to the data, not just the render, so the
  validator's rule stays honest.

## 11.61 The worklist — frequency finally wired in

Section 3 has always listed `data/worklist.tsv` as "frequency-ranked headwords,
our unit of work", and 5.1 builds the batch ladder on it — B2 is the top 1,000
by frequency, B3 the top 5,000 and the real target. **No frequency source was
ever wired in.** All eight shards were picked by hand and by family size.
`tools/worklist_build.py` closes that gap.

### The source matters more than expected

Two obvious lists both fail, and in the same direction:

| Source | *asinine* | *unctuous* | *lugubrious* | *mawkish* | *snivel* |
| --- | --- | --- | --- | --- | --- |
| google-10000-english | — | — | — | — | — |
| OpenSubtitles en_50k | rank 36,454 | — | — | — | — |
| wordfreq (Zipf) | 2.56 | 2.03 | 2.02 | 1.93 | 1.40 |

A top-10,000 list cannot see the band at all, and OpenSubtitles is spoken
English, so it misses words that live in books — which is exactly where a reader
meets a word whose force the gloss will not give them. **wordfreq's Zipf scale
is the source**: *the* 7.73, *good* 6.12, *asinine* 2.56, *snivel* 1.40.
Installed with `pip install wordfreq`; nothing lands in `data/source/`.

### Rank on the median member, not the peak

Ranking a family by its most frequent member puts *in*, *out*, *like* and *more*
at the top — function words carrying a rare adjective sense (*in* as
"fashionable"). Zipf scores a **word form**, not a sense, so a rare sense of a
common word inherits the common word's frequency. The median member is the
honest signal, and 5.3 excludes function words anyway.

### Where the hand-picked shards actually landed

| | median-of-medians |
| --- | --- |
| The 56 annotated adjective families | **3.04** |
| All 5,911 candidate families | **2.90** |

The done families spread from *superior* (4.45) to *wise* (2.10) and sit almost
exactly on the corpus centre. Hand-picking was not biased toward easy common
words — but it was not prioritised either: coverage runs at roughly 1% in every
band, which is what picking by hand looks like. The worklist does not correct
past shards; it orders the next six hundred.

### The band

Adjective families by median member:

| Band | Families | Done |
| --- | --- | --- |
| 6.0+ everyday | 8 | 0 |
| 5.0–6.0 common | 156 | 0 |
| 4.0–5.0 familiar | 792 | 7 |
| 3.0–4.0 educated | 1,771 | 21 |
| 2.0–3.0 literary | 2,010 | 28 |
| <2.0 rare/obscure | 1,174 | 0 |

The top two bands are words whose connotation every reader already knows; the
bottom is 5.3's long tail. The work is the middle.

**The band is a selection filter and must never reach the note-writing prompt.**
A writer told a word is "literary" or "rare" will put that in the note, and
distribution claims are the largest fault class in the corpus — 58% of every
failure in census 001 (11.69), and still the largest in census 002 (11.70). The
worklist decides *whether* a family is queued; the writer sees only the gloss
and the spectrum.

## 11.65 Audit 001 — the sampled audit, and what it found

Run 2026-08-30 at the moment reviewed entries crossed 1,000, per 5.5. Fifty
senses drawn at random from the 1,064 carrying a connotation claim, stratified
by shard, seed 20260830, read against Merriam-Webster and Cambridge.

**Result: 18 right, 22 wrong, 10 unsure. Error rate 44% against a 5%
threshold. The sample fails, and by 5.5 that means the method is wrong, not
the batch.**

The rate is flat across all eight family shards — 29% to 67%, with no trend
from the earliest to the latest. This is not a drift in care between rounds;
**it is the format.**

One correction on first reading. The sampler attributed inherited adverbs to
`batch-0001` by fallback, which made it look as though the hand-written pilot
scored the same 46% as the model-authored shards. It did not: **no batch-0001
sense was drawn at all.** The thirteen rows in question are inherited adverbs,
whose notes are their adjectives' notes, so they re-test the same authorship
rather than a different one. The sample therefore says nothing about hand
authorship, and the "human and model fail alike" reading is not supported by
it. `audit_sample.py` now labels inherited adverbs as their own source.

### What actually failed

| Category | Count | What it means |
| --- | --- | --- |
| Over-narrow | 16 | The note restricts the word to a context it does not own |
| Unverifiable | 9 | A claim about frequency or distribution with no corpus behind it |
| Mislabel | 5 | The connotation label or charge itself is wrong |
| Bad etymology | 2 | A decorative origin story that is misleading or false |

**Only 10% are genuine mislabels.** The charge layer — the spectrum, which is
the differentiator — is right about 90% of the time. What fails is the prose
written around it:

- *lifelessly* "describes a body, not a person" — it describes speech, music and movement too.
- *dingily* "of rooms and fabric" and "depressing" — neither restriction holds.
- *kindly* "now used mostly of the elderly" — a distribution claim that is simply untrue.
- *thankful* "carries a trace of prayer" and *laud* "Church Latin behind it" — invented provenance.
- *hoggish* "about an eater" — it means greedy, coarse or selfish generally.

The pattern is exact: **the note was written to be interesting, and interest
required specificity, and the specificity is where the falsehood entered.** A
vivid claim about a word is the most readable thing on the card and the most
likely thing on it to be wrong.

### The tone-note rule, adopted: stay inside the word

The first reading of this audit suggested the notes had to become duller. They
do not. Sorting the sample by verdict shows something better: the eighteen
notes that passed are among the most vivid in the corpus.

> *snivel* — "Contemptuous - crying treated as whining, weakness rather than sorrow."
> *at rest* — "Gravestone language - comfort offered to the living."
> *asinine* — "Withering - stupidity so complete it deserves contempt."
> *clapped out* — "British, and it works equally on a car, a machine or a person - which is the joke."

And the ones that failed share a different shape entirely:

> *kindly* — "now used mostly of **the elderly**"
> *dingily* — "of **rooms and fabric**"
> *laud* — "**Church Latin** behind it"
> *hoggish* — "about **an eater**"

**The notes that passed describe the word — its force, its register, how it
differs from the word beside it on the spectrum. The notes that failed describe
the world around the word — who says it, how often, where it came from, what it
is limited to.**

That is the rule, and it is a single line: **say what the word does; do not say
who uses it or where it came from.** We have the gloss and we have the
spectrum, so claims about the word are grounded. We have no corpus and no
etymological source, so claims about its distribution and origin are guesses
wearing the clothes of facts.

Three shapes are therefore out:

1. **Distribution** — "usually", "now mostly", "the commonest", "more often than not".
2. **Provenance** — any origin story not checked against a source.
3. **Restriction** — narrowing the sense past what the gloss says it covers.

This is 5.4's fabrication rule applied one level up: we already refused to
invent a connotation, and now we refuse to invent the reasoning about it. What
we keep is the part that was always ours to write — the comparison between a
word and its neighbours, which is exactly what the family stage exists to know.

`tone_lint.py` checks the three shapes, but it is **a smoke alarm, not a
referee**: *puritanical*'s "now used almost exclusively as an accusation" trips
the distribution rule and was still marked right, because it happens to be
true. The tool points at notes worth a second look; the test stays human.

## 11.66 Audit 002 — the rewrite tested

Read 2026-08-30 against fifty fresh senses, none of them seen in audit 001.

**42 right, 7 wrong, 1 unsure. Error rate 14%, down from 44%.** Still over the
5% threshold, so 5.5 still says method rather than batch — but the option-D
rewrite did most of what it was supposed to do, and what remains is a different
fault with a different fix.

**Only one of the seven is a note-scope failure** of the kind audit 001 was
about (*beguiling*, "the one near-flattering word here" — a claim about the
sample, not the word). **Six are sense misalignment: the note annotates a
different sense from the one the definition names.**

| Word | What went wrong |
| --- | --- |
| *slightly* | a bare degree adverb given the body sense of *slight* |
| *thinly* | glossed "without force or sincere effort", noted as if it meant shape |
| *furiously* | the sense chosen is wind, the note is about human anger |
| *gaze* | called literary and positive; *gazed blankly*, *gazed in horror* |
| *dingy* | WordNet's synset glosses it as *grimy*; a dingy room can be clean |
| *unhurried* | WordNet's synset glosses it as *patient*; the word is about pace |

Three of those are inherited adverbs, and they share one cause:
`adverb_inherit.py` stamped the adjective's charge on **every** sense of the
adverb. *furiously* covers wind as well as anger; *thinly* covers viscosity.
Fixed by inheriting onto a multi-sense adverb only where the gloss is visibly
about the adjective — 34 adverbs are now skipped outright rather than guessed
at, and the inherited count falls from 365 senses to 244.

*dingy* and *unhurried* are the same fault one level up: WordNet placed the word
in a synset whose gloss does not fit it. We cannot correct a gloss, so we
decline to add a judgement to it and the sense stays `derived`.

**The check this produces:** does the note's claim fit the definition printed
directly above it on the card? That is mechanical enough for a person to do at a
glance and is now the first question of any audit.

### What surfaced underneath

Removing a wrong inherited note does not leave a blank — it exposes whatever
SentiWordNet said. *slightly* and *furiously* dropped out of the reviewed set
entirely and are `derived` again, labelled unreviewed in the app, which is
honest. But it is a reminder that **30,542 non-neutral labels still rest on
SentiWordNet alone**, a source 11.5 demoted for being wrong at this resolution,
and no audit has ever sampled them.

## 11.67 Audit 003 — the gloss is binding

Read 2026-08-30 against fifty senses drawn with seed 20260901, excluding every
sense seen in audits 001 and 002.

**39 right, 11 wrong. Error rate 22% — a regression on 002's 14.** The sheet
asked two questions of each card for the first time: does the note fit the
definition printed above it, and is the claim true.

Seven of the eleven are one fault, and it is the first question failing: the
note annotates the right synset and then describes a different sense of the
word. *fallacious* is glossed "involving deception" and noted "an argument can
be fallacious with nobody lying". *vociferous* is glossed "conspicuously and
offensively loud" and noted "which is why it can be praise". *voracious* is
glossed on food and noted on reading.

**The notes are stored per sense; they were being written per lemma.** The
worksheet has always printed the gloss — it was simply not treated as binding.
Fixed for *fearful*, *fallacious*, *voracious*, *cordial*, *disciplinal* and
*vociferous*, with charges corrected where the gloss was stronger than the
charge admitted.

### Some glosses are not definitions

Holding a note to its gloss only works when the gloss says something. OEWN 2024
gives some synsets a bare usage restriction where the definition should be:
`renunciant` is glossed "used especially of behavior", `stouthearted` "used
especially of persons", `self-disciplined` "used of nonindulgent persons".
There is nothing there for a note to agree with, so any note written against
one is unfalsifiable — it cannot fail the check, which means the check cannot
pass it either.

**The rule, adopted:** a sense whose gloss is a usage restriction rather than a
definition does not get a judgement. It stays `derived` and is marked
`"_skip": true`, the same treatment 11.66 gave *dingy* and *unhurried*, and for
the same reason — we cannot correct a gloss, so we decline to annotate it.

Enforced mechanically rather than remembered:

- **`tools/gloss_lint.py`** flags any annotated sense whose gloss carries no
  definition. A restriction is only a defect when it stands alone: "used of a
  knife or other blade; not sharp" and "(used of sums of money) so small in
  amount as to deserve contempt" both restrict *and* define, and pass.
- **`tools/family_worksheet.py`** pre-marks such members `"_skip": true` and
  keeps them out of the anchors, so a future shard is never invited to write
  the note in the first place.

The sweep of the reviewed set found **eight** senses with the defect: four in
`oewn-01303991-s` and *dingy* and *unhurried*, all skipped when 11.66 was
written, plus *stouthearted* and *self-disciplined*, skipped now. Losing
*self-disciplined* cost family-01302836-a its positive pole, and its axis was
rewritten from "joyless → self-disciplined" to "moralising severity → plain
strictness" to match what is left.

Corpus-wide the defect is larger than the reviewed set has met so far:
**137 synsets, 233 senses**, spread across every part of speech. Those are now
unreachable by the worksheet, so the cost is paid once rather than per shard.

## 11.68 Audit 004 — the fixes measured

Read 2026-08-30 against fifty senses drawn with seed 20260904, excluding every
sense seen in audits 001, 002 and 003. It is the first reading after both the
gloss-binding fix and the undefinable-gloss sweep.

**37 right, 10 wrong, 3 unsure. Error rate 20%, against 22% for audit 003.**
Still four times the threshold. The fixes held where they were applied; the
finding is *where they were applied*.

| Category | Count | What it is |
| --- | --- | --- |
| wrong-sense | 4 | the note describes a different sense from the gloss above it |
| unverifiable | 3 | a claim about speakers, frequency or origin that we cannot check |
| note-scope | 2 | the note leaves the word — for the sample, or for a restriction the gloss does not make |
| wrong-gloss | 1 | OEWN put the word in a synset that does not fit it |

### The fix was applied per word, not per synset

*blatant* is glossed "conspicuously and offensively loud" and was noted "has
largely left sound behind" — the same fault, in the same synset, as
*vociferous*, which 11.67 fixed. The synset was corrected for the audited word
and left standing for its neighbour.

**The rule that follows:** a note repaired for one word is repaired for every
word in that synset. Two of audit 004's ten failures would not have been
written down if 11.67's fixes had been made synset-wide.

The same shape appears one level out. *bush-league* failed on "From
minor-league baseball", and three of its family — *cheapjack*, *shoddy*,
*tawdry* — carried the same origin story to a gloss that says only "made of
inferior workmanship and materials". All four are rewritten here. Etymology is
not banned because it is uninteresting; it is banned because 11.65 could not
check it, and an unchecked origin is what an audit counts as wrong.

### Adverbs: morphology names the lemma, WordNet names the sense

The one inherited-adverb failure was *vulgarly*, glossed "in a smutty manner"
and carrying the note written for *vulgar* "lacking refinement or cultivation
or taste". 11.66 had already gated multi-sense adverbs on their glosses;
*vulgarly* has one sense, and a single sense was assumed safe. It is not:
morphology says which lemma an adverb belongs to and says nothing about which
of that lemma's senses.

WordNet does say. Every derived adverb carries a `pertainym` relation pointing
at one adjective **sense**. `tools/pertainym_extract.py` pulls those 3,252
pointers out of the source; `adverb_inherit.py` now uses them as the gate:

- the adverb takes the note written for the sense it points at, when we have
  one — *benignly* points at *benign* "pleasant and beneficial", not at the
  "kindness of disposition" sense that happened to be annotated first;
- it takes nothing when we have no note for that sense;
- senses with no pertainym fall back to the gloss rule from 11.66.

Inherited senses fall from 244 to 154 and 90 are declined outright. That is a
third of the adverb shard given up to remove one measured failure, and it is
the right trade only because the loss is recoverable: annotating the adjective
sense an adverb points at restores it for free, with no new prose.

### Where the error rate actually sits

Two rounds of fixes have moved it 44 → 14 → 22 → 20. The first drop was real;
the plateau since is three different faults arriving in turn, each smaller than
the last, and each mechanically checkable only after an audit has named it.
**5.5 still says method, not batch.** The next audit is the one that decides
whether the per-synset rule and the pertainym gate did what 11.67's fix did for
one word only.

## 11.69 Census 001 — every unaudited sense read, then audit 005

Four sampled audits had moved the rate 44 → 14 → 22 → 20 and could no longer
tell one round from the next: a 50-sense draw at 20% carries a 95% interval of
roughly ±11 points, so 003 and 004 were the same number. And at 20% wrong on a
corpus of ~1,000 claims there were ~200 defective notes, of which a sample
finds ten a round. The arithmetic said census, not sample.

**Read 2026-09-02: 767 senses — every claim-carrying sense not seen by audits
001–004 — by sixteen parallel readers, 48 each, against a written rubric
distilled from 11.65–11.68 (`data/policy/census-001-rubric.md`). 565 right,
198 wrong, 4 unsure. Error rate 25.8%.** Higher than the samples, and the
samples were within their interval of it.

| Fault | Count | Shape |
| --- | --- | --- |
| unverifiable | 114 | frequency ("commoner", "rarer", "often", "equally common"), dating ("older", "now", "since the nineteenth century"), origin stories ("From Gascony", "the fourth humour", "back-formation from *uncouth*") |
| wrong-sense | 47 | the note describes a different sense of the lemma from the gloss above it |
| note-scope | 31 | the note leaves the word — "the one word here", "the same euphemism", "the least loaded word here" |
| wrong-gloss | 2 | *wail* filed under "cry weakly or softly"; *loot* under "take illegally; of intellectual property" |
| charge-sign | 2 | *aged* and *eroded*: neutral technical glosses carrying −1 |
| other | 2 | *fat*, *luxe*: the note is right and an example belongs to another sense |

A census is a repair pass, not a measurement — once every failure it finds is
fixed, the rate it reports is 0% by construction. So the repairs were made
first and a fresh sample drawn afterwards, below.

### The repairs, made synset-wide

Every failure was refereed centrally (`data/policy/census-001-decisions.json`,
202 entries) so the per-synset rule from 11.68 held by construction: 159 notes
rewritten, 4 charges changed (*shoplift* −2 → −1, *blithe* 0 → +1, *aged* and
*eroded* −1 → 0), *wail* and *loot* declined for their glosses, one authored
example dropped, and a further 32 notes rewritten in the unflagged members of
repaired synsets (`census-001-decisions-sweep.json`) — among them *laud*
"Church Latin behind it", the exhibit from 11.65 that had never actually been
rewritten. `tools/census_apply.py` lands decisions on the shards;
`tools/census_aggregate.py` merges the readers.

The sixteen readers disagreed at one boundary and it is now settled: a bare
register label (*British*, *formal*, *dated*, *literary*) and a register
picture ("gravestone language", "the word on a chart, not at a graveside") are
inside the word and stay, as *at rest* did in audit 001. A quantifier, a date
or an origin is out, however plausible.

### The fifth fault class: the adverb's own gloss

Thirty-five inherited adverbs failed. Nineteen were the adjective's fault and
re-derived correctly once the adjective was fixed; seven had the fault in an
adjective note the census happened not to sample, fixed at source. The other
nine are new: the pertainym gate passes — the note *was* written for the sense
the adverb points at — and the adverb's own gloss still disagrees. *preciously*
points at the affectation sense of *precious* and is glossed "very". *lately*
carried the note for *late* "deceased" onto "in the recent past", in the
*death* family at +1. Nothing mechanical can see this; they are listed by hand
in `data/families/adverb-deny.json` and `adverb_inherit.py` refuses them.
Inherited senses 154 → 145. As in 11.68 the loss is recoverable: annotating
the adverb itself, or the sense that fits, lifts the denial.

Two smaller findings. `adverbise()` lowercased the first word of every
inherited note, so *chirpily* read "british and small-scale"; proper
adjectives now keep their capital. And OEWN attaches examples to the synset,
not the lemma, so "a deluxe car" prints under *luxe* and *de luxe* — the
validator's 1,561 "example does not use the headword" warnings are this one
artifact, and it is a builder decision, not an annotation one.

### Audit 005 — the measurement, with a caveat

Fifty senses, seed 20260905, drawn from the whole repaired population of 927
with no exclusions, since every sense has now been read once and "fresh
material" no longer exists — the freshness is in the reader. **49 right, 0
wrong, 1 unsure. Error rate 0%.** The one *hm* is *noisy*, "noise is expected
in most places", a claim about the world rather than the word.

**The caveat is real: the reader was the session that made the repairs.** Twelve
of the fifty senses were rewritten hours earlier by the same hand; on those the
audit is self-review. The other thirty-eight were passed by a census reader and
now by a second one, which is a genuine, if weaker, check. The rate is therefore
"0% as read by a non-independent reader", not "0%". 5.5's threshold is met on
the number and not yet on the method. The next audit should be drawn with a new
seed and read blind — a fresh session, or the owner — before authoring resumes.

### Where the error rate actually sits, revisited

The plateau in 11.68 was never three faults arriving in turn; it was one large
class — unverifiable claims about frequency, date and origin, 58% of every
failure — that the sampled audits kept finding ten at a time and the rewrite in
11.65 had addressed by rule but not by sweep. The census cleared it in one pass
at roughly the cost of three sampled rounds. What the samples were good for was
naming the fault classes; once named, a census is the cheaper instrument.

## 11.70 Census 002 — the blind read, and the boundary it found

Run 2026-09-02, the reading 11.69 said was owed. Every one of the 927 senses
carrying a connotation claim, read by sixteen **Claude Fable 5.1** readers that
saw only the word, the gloss, the charge, the family and the note — no plan, no
prior audit, no repair list, and no part of this repository. A different model
family from the one that authored the corpus, so a shared blind spot between
author and reader is ruled out rather than hoped about.

**927 read. 886 right, 35 wrong, 6 unsure. Error rate 3.8%, against 5.5's 5%
threshold. The gate passes.**

### The number that matters is the split

| Population | Read | Wrong | Rate |
| --- | --- | --- | --- |
| Read and repaired by census 001 | 756 | 20 | **2.6%** |
| Excluded from census 001 | 171 | 15 | **8.8%** |

`audit_sample.py --exclude` exists so a second reading gets fresh material
rather than a re-mark. Census 001 inherited that logic and therefore skipped the
171 senses audits 001–004 had already sampled — **the one population already
proven to contain failures.** It repaired 198 faults everywhere else and never
returned to them. Of the 32 senses audit 001 flagged on 2026-08-30, 26 still
carried a note five days later and several were word-for-word unchanged:
*hoggish* still narrowed to "the eater", *cannibalic* still "rare enough to
sound like an accusation of myth", *loutish* still "young, male and physical".

Audit 005's 0% is now fully explained. It drew 45 of its 50 senses from the
repaired side of a boundary nobody had noticed, so it measured the clean half of
the corpus and reported it as the whole.

**The method was never the problem. The bookkeeping was.** 11.68 concluded the
plateau was one large fault class arriving in waves; 11.69 concluded the census
had cleared it. Both were right about the corpus they looked at. Neither looked
at the 171.

### The fault classes, unchanged in rank

| Fault | Count |
| --- | --- |
| distribution | 20 |
| restriction | 7 |
| gloss-mismatch | 6 |
| world-not-word | 5 |
| provenance | 2 |
| wrong-charge | 1 |

Distribution is 57% of failures, against 58% in census 001. The class was never
beaten — it was swept out of 756 senses and left standing in 171.

By part of speech: adverbs 0.7% (1 of 145), adjectives 4.1% (27 of 666), verbs
6.0% (7 of 116). The inherited adverbs are the cleanest population in the
corpus, which is what 11.68's pertainym gate was for.

### The repairs

35 failing synsets, 41 senses. 34 synsets were repaired by hand in the shard
files (`data/policy/census-002-repairs.json`, applied through the existing
`census_apply.py`); the 35th, *noisily*, re-derived itself from the repaired
*noisy* through the pertainym inheritance, which is the machinery working as
designed.

Two repairs are judgement calls worth recording. *couth*'s gloss is literally
"(used facetiously)", so the reader's objection to "almost always a joke" caught
a frequency word sitting on top of a claim the gloss does licenses; the note
keeps the joke and drops the "almost always". *pillory*'s "names an actual
punishment" was read as provenance, and the rewrite keeps only what the word
does to its target now.

### What this changes about auditing

A sample tells you which fault classes exist. A census tells you the rate. **But
neither is worth anything if the population is drawn to exclude the senses most
likely to fail** — and "fresh material" is exactly the instinct that produces
that exclusion. The rule from here: *a repair pass reads everything; only a
measurement pass may exclude.* Census 001 was a repair pass wearing a
measurement's exclusion logic.

### The repairs, re-read blind

The 41 repaired senses were rewritten by the session that read this census's
results, which is the same defect that cost audit 005 its credibility — so they
were handed straight back out to a fresh Fable 5.1 reader that had seen neither
the originals nor the repair list, and was told plainly that these notes had
been through an editing pass and to look for what an editor had talked
themselves into keeping.

**40 of 40 right, 0 wrong, 0 unsure** (`data/policy/census-002-reread.json`).
The 35 failing synsets are closed, verified by someone other than the hand that
fixed them. Nothing is owed on census 002.

The cost of that discipline is one packet, about three minutes and a few cents.
The cost of skipping it was five days and three audits.

## 11.7 Possible later work — not scheduled

Recorded so the options are not lost. None of these is committed to, and none
requires a rewrite: the JSONL master takes new fields additively, and
`sametypesequence=h` means a new article row is a builder change plus a CSS
class, with no app code at all.

| Idea | What it needs | Notes |
| --- | --- | --- |
| **Thesaurus view** | UI only | The data already beats a thesaurus — synonyms carry a charge. Needs a browse-by-family screen, not new data |
| **Etymology / origin** | one schema field, one overlay pass | Wiktionary via kaikki.org has it (CC BY-SA — see DICTIONARY-DATA.md). `word_formation` is already a shallow version |
| **Word trees** | turn on relations already parsed | WordNet ships 93,446 hypernym pairs and 74,646 derivations; `family_extract.py` currently discards both |
| **Query by field** | SQLite migration | The only idea here that is *not* cheap. Display-only stays on StarDict; "all Latin-derived negative adjectives" does not |
| **iOS port** | Swift engine + SwiftUI | Engine is 1,451 lines with **zero** Android imports (proven by the desktop harness); UI is 2,379 lines and needs a rewrite. ~3–4 weeks. Caveat: iOS has no equivalent of the Android lookup intent, so the popup becomes a share extension |

## 12. Risks

| Risk | Mitigation |
| --- | --- |
| Curation stalls at ~200 words | B0 ships first, so the app is never blocked on it |
| Model-drafted connotation is subtly wrong | 5.4 fabrication rule enforced in code, plus the four controls in 5.5 |
| Fluent-but-wrong entries ship at scale | Label comes from SentiWordNet, not the model; independent verify pass; B5 sampled audit measures the real rate |
| Bad prompt burns a whole batch run | B1 calibration pilot; batches are resumable and re-runnable per file |
| Sensitive terms handled badly | 5.3 manual-only queue; never auto-drafted |
| WordNet glosses read stiff | Accept for `derived`; rewriting is exactly what curation is for |
| Index memory at full scale | Measured 8 MB per 150k entries, so ~120k fits. Memory-map `.idx` if it grows |
| CC BY-SA on SentiWordNet | Data file only; app stays MIT. Attribution shipped in `.ifo` and the About screen |
