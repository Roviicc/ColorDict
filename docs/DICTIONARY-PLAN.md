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

**Current totals: 54 families, 915 reviewed entries, 0 validation errors** —
including 235 adverbs inherited for free.

Stage 1 (adjectives) is 45 of ~1,100 families; stage 2 (verbs) is 9 of ~2,495.
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
