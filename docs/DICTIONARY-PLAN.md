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

> **New here?** Read [`HANDOFF.md`](HANDOFF.md) first. It is the short orientation:
> what is being built, whether it is working, where the four lines stand, and what
> to do next. This file is the full record and is long on purpose.

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
| 2026-09-02 | **The instrument is version-controlled.** `.claude/agents/census-reader.md` pins the reader's model, effort and tool allowlist; `census2_aggregate.py` records all three in the results. A reader with only `Read`/`Write` cannot reach `data/policy/`, so blindness is enforced rather than requested. See 11.62 |
| 2026-09-02 | **The worklist gets a second gate.** Frequency alone queues neutral adjectives — *finished*, *whole*, *normal* — which a connotation dictionary has nothing to say about. Eligibility is now size ≥ 8 and charged ≥ 70%, then median Zipf. 340 eligible, 303 queued. See 11.62 |
| 2026-09-02 | **Shard 9 — the first drawn by the tool.** 9 families / 92 senses: ready, inaccurate, sound, accurate, fortunate, best, crucial, reliable, preserved. Median Zipf 4.45–5.06, a band above the hand-picked work. 0 notes flagged by `tone_lint.py`, the first shard to come in clean. Authored by Opus 5; the blind read is owed. See 11.62 |
| 2026-09-02 | **Census 003: 1.1%.** Shard 9 read blind, complete population, 91/92 right. One `restriction` fault repaired and re-read clean by a third reader. `census_packets.py` now builds and keeps the reader inputs, so a census is reproducible from its inputs rather than only from its results. See 11.72 |
| 2026-09-02 | **Tick 1: 17 families, 178 senses, 0.6% blind.** First shard authored by parallel `family-author` agents rather than one session writing serially. 0 lint flags, 1 `restriction` fault, repaired and re-read clean. The fan-out passes; the bottleneck it exposed is the orchestrator, not the authors. See 11.73 |
| 2026-09-02 | **Tick 2: 25 families, 281 senses — and the ruler moved.** Authors wrote to disk instead of returning through the orchestrator, so a full-size tick fit in one session. The read came back 0.0%, and an adversarial re-read of 40 senses under the previous rubric found a fault it had passed. The 0.0% is withdrawn: tick 2 is under 5%, not more precisely known. See 11.74 |
| 2026-09-02 | **The charge gate checked, and kept.** 30 families blind-triaged by two raters from different model families (27/30 agreement) against the hypothesis that the 0.70 threshold was too tight. It is not: the excluded 0.50-0.59 band scored 0.00. Gate unchanged; the hypothesis was wrong and the ~600 estimate in 11.6 is superseded. See 11.75 |
| 2026-09-02 | **Tick 3: 25 families, 292 senses, 3.4% blind — the first verbatim read.** Rubric taken from `census-reader.md` as written rather than typed into the prompt, which is what censuses 003-005 all did. 10 faults, 9 repaired and re-read clean; *awesome* took three attempts and failed a different class each time. See 11.76 |
| 2026-09-02 | **Tick 4: 24 families, 298 senses, 1.3% blind.** Reading instrument verbatim and comparable to census 006 — but the *authoring* prompts carried an extra warning that `family-author.md` did not, so the drop from 3.4% is confounded. The line is now in the file. `bad-example` promoted to a named fault class on its second instance. See 11.77 |
| 2026-09-02 | **Tick 5: 21 families, 286 senses, 1.4% blind.** First tick with BOTH instruments verbatim and prompts identical across every family, so it is the first cleanly comparable pair with tick 4 (1.3%). A second family withheld under 5.3, caught by reading rather than by the regex. New fault class `family-inconsistent`. See 11.78 |
| 2026-09-02 | **Tick 6: 23 families, 287 senses, 2.1% blind.** Third comparable tick (1.3 / 1.4 / 2.1). `family-inconsistent` built into `tone_lint.py` and backtested — found one historic fault in shard 12. `sensitive_screen.py` replaces the retyped regex after three misses in three ticks. Third family withheld under 5.3. See 11.79 |
| 2026-09-02 | **Tick 7: 20 families, 280 senses, 2.5% blind.** Fourth comparable tick (1.3 / 1.4 / 2.1 / 2.5). Two of the seven faults were an importer bug, not authoring: OEWN attaches examples to the synset, so *lucid* carried *pellucid*'s example - `bad-example` finally has a cause, fixed at import, and `example_mentions` tightened from substring to word boundary. New `superlative-collision` rule in `tone_lint.py` then found six MORE faults in the same shard the census scored at 2.5%, plus eight historic. Fourth family withheld under 5.3. See 11.80 |

**Current totals: 207 families, 2,506 annotated senses, 2,667 reviewed entries,
0 validation errors. Measured error rates 1.3% / 1.4% / 2.1% (censuses 007, 008,
009) — three consecutive ticks with both instruments verbatim and prompts
identical, so the three are comparable to each other and to nothing before
census 007. Earlier rates — 3.8% / 1.1% /
0.6% — were read under typed paraphrases of the rubric, and census 005's was
withdrawn for that reason (11.74). Every annotated sense has been read by a
model from a different family than the one that wrote it.** — including adverb senses inherited for free, and 11
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

## 11.62 The instrument, and what the worklist was actually ranking

Two fixes that belong together: both are cases where a number looked settled
because nobody had written down what produced it.

### The reader is now a file, not a memory

Census 002 recorded `reader_model: claude-fable-5-1` and nothing else. The
effort level the sixteen readers ran at was never recorded anywhere — it was
inherited from a session setting that had been rewritten four minutes after the
last packet landed. The reading could not be reproduced, and a rate compared
against it could not be attributed.

`.claude/agents/census-reader.md` now pins the instrument in version control:

| | |
| --- | --- |
| `model` | `fable` |
| `effort` | `xhigh` |
| `tools` | `Read, Write` — nothing else |

The tool allowlist is the part that does real work. A reader with no `Bash`,
`Grep` or `Glob` **cannot** open `data/policy/` and see how a sense was scored
before, so blindness is a property of the harness instead of a sentence in a
prompt that a reader may or may not honour. The rubric lives in the same file,
so the reading instructions are versioned alongside the model that follows them.

`census2_aggregate.py` reads that frontmatter and writes `reader_effort`,
`reader_agent` and `reader_tools` beside `reader_model`. It refuses to run
without the agent file: a census that cannot say what read it is not a
measurement. `--reader-model` takes the *resolved* ID actually served, because
an alias drifts under you — a mismatch against the agent file warns rather than
being silently recorded.

Verified by reconstructing all 927 census 002 verdicts from the published
results and re-running: 886/35/6, 3.8%, the 2.6/8.8 split and all six fault
counts reproduce exactly, and the diff against the published file is three added
keys. (The verdict packets themselves were not kept, so census 002 is
reproducible only from its own results. Keep the packets from here on.)

### Frequency was ranking the wrong thing, twice

The worklist sorted by **peak** Zipf. Zipf scores a word form, not a sense, so a
single common form dragged whole families to the top: the first three were
*fashionable*, *successful* and *cardinal*, lifted there by *in* and *i* —
function words 5.3 excludes from annotation anyway. *cardinal* is 259 members
and 134 synsets, which is not a ninth of a shard.

Sorting by the **median** — the honest signal, per 11.61 — fixed that and
exposed the real problem underneath. The new head of the list was *on*, *more*,
*out*, *like*, *first*: singleton families of function words. A family of one
has no spectrum to rank against.

Gating on size then showed the fault that had been there all along. Ranked by
frequency, the reachable adjective families are *finished*, *individual*,
*whole*, *normal*, *high* — neutral words. This is a connotation dictionary.
There is nothing for a tone note to say about *whole*, and a shard spent on it
measures nothing.

| | median size | charged fraction |
| --- | --- | --- |
| The 56 hand-picked families | 19 | **0.83** |
| Frequency-ranked head, size ≥ 8 | 10 | **0.36 – 0.62** |

Hand-picking had been quietly applying a filter the tool did not have. So
eligibility now takes two gates before frequency is consulted at all — size ≥ 8
(it has a spectrum) and charged ≥ 70% by SentiWordNet (it is a connotation
family) — and only then median Zipf. 340 families are eligible, 303 of them
untouched and queued. 37 of the 56 hand-picked families would themselves pass,
at a median charged fraction of 0.82 against a 0.70 gate.

Both gates are recorded per row rather than applied destructively. Every
candidate family stays in `worklist.tsv` with `charged`, `charged_pct` and
`eligible` columns; the sort puts eligible families on top. A later pass that
wants the neutral band can still find it.

**This is the same failure as the census's `--exclude` logic (11.70), one level
up.** There, a population was drawn to exclude the senses most likely to fail.
Here, a queue was ordered by a proxy that quietly excluded the families most
worth annotating. In both cases the tool looked principled and the hand-picked
alternative was better, which is the signal that the principle was not the one
being applied.

### Shard 9, drawn by the tool for the first time

The first shard the worklist chose rather than a person: 9 families, 93 members
after one pre-skipped gloss, 59 synsets (`data/families/draft-009.json`).

| Family | Members | Synsets | Charged | Median Zipf |
| --- | --- | --- | --- | --- |
| ready | 10 | 7 | 7/10 | 5.06 |
| inaccurate | 9 | 5 | 7/9 | 4.96 |
| sound | 8 | 5 | 7/8 | 4.77 |
| accurate | 12 | 8 | 9/12 | 4.70 |
| fortunate | 12 | 8 | 12/12 | 4.62 |
| best | 20 | 12 | 17/20 | 4.61 |
| crucial | 8 | 4 | 6/8 | 4.55 |
| reliable | 9 | 4 | 8/9 | 4.48 |
| preserved | 9 | 6 | 8/10 | 4.45 |

It sits a band above the hand-picked work (4.45–5.06 against a corpus centre of
3.04), which is the prioritisation the worklist was built for and never
delivered: these are commoner words than the ones annotated so far, and still
charged enough to be worth a note.

Annotated 2026-09-02: 92 senses, 90 overlay words, 0 validation errors, and
**0 notes flagged by `tone_lint.py`** — the first shard to come in clean, where
the eight before it run 1–9%. Two notes were rephrased before applying, both
tripping the hedge-claim rule on *no one* and *nobody*; neither was a claim
about speakers, which is the lint behaving as the smoke alarm 11.65 describes.

Three traps in this shard are worth recording, because they are the gloss-binding
rule doing real work rather than a rule quoted at a writer:

- The *sound* family is glossed entirely on money. *good*, *safe*, *secure* and
  *healthy* sit there as financial words, and a note about general goodness or
  bodily health would fail against the gloss printed above it.
- *happy* in the *fortunate* family is glossed "marked by good fortune", not
  gladness. The note has to describe luck landing well and say nothing about
  how anyone feels.
- *away* and *outside* are glossed as baseball calls. They carry no judgement,
  so they take charge 0 — the family's charged fraction is a gate on the family,
  never a quota the members have to fill.

The shard was authored by Opus 5, so the reader stays Fable 5.1 and the split
that made census 002 credible holds. **The blind read is owed before these 92
senses count toward a measured rate**; the 3.8% in the totals above was measured
on the corpus as it stood before them.

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

## 11.72 Census 003 — shard 9 read, and the loop closed at both ends

**92 senses, complete population, 91 right, 1 wrong, 0 unsure. 1.1% against a
5% threshold** (`data/policy/census-003-results.json`).

This is the first shard the worklist chose, the first authored against a written
instrument, and the lowest rate the corpus has measured. It does not prove the
method is four times better than census 002 said — 92 senses is a small
population and one fault is one fault — but it does clear the Stage 1 gate,
which is all it was asked to do.

### The one failure is the useful part

*better off*, glossed "in a more fortunate or prosperous condition", was noted
"it means **only** against the state someone was in before". The gloss is a bare
comparative; the note quietly excluded *better off than his neighbours*. Fault
class `restriction`, and a fair catch.

Two things follow from it. First, `tone_lint.py` passed this note: its narrowing
rule matches `only in`, `only of`, `only ever` — and not `only against`. The
lint is a smoke alarm, exactly as 11.65 says, and this is what that costs.
Second, the failure is a **hedge word doing damage**, not a false claim: every
content word in the note was defensible and the sentence was still wrong,
because *only* asserted a boundary the gloss does not draw.

Repaired to "Comparative by construction - it always implies something being
compared against, and never says what", and handed to a third reader that had
neither authored it nor found the fault: **1/1 right**.

### The packet builder, and what its absence had cost

`tools/census_packets.py` did not exist until now. Census 001's input packets
survive as data with no tool behind them; census 002's were never written down
at all, which is why **the measurement the whole method rests on is reproducible
only from its own results file.** Census 003 keeps its inputs
(`data/policy/census-003-reads/`), so it can be re-read by a different model, at
a different effort, or a year from now, and the two runs compared.

The builder decides what a reader may see by an explicit allowlist rather than
by which fields happen to be in the population file, and prints what it withheld.
For census 003 that was the family id — knowing which family a sense belongs to
would let a reader infer the spectrum instead of reading the gloss.

### Three bugs the run exposed

Worth recording because all three were latent and none would have announced
itself:

- `--prior` was required, so a census with no earlier population to split against
  could not be aggregated at all. Now optional; the split is omitted rather than
  reported against an empty set.
- The packet count was hardcoded to 16, so any census not using sixteen readers
  reported the difference as missing packets — a false alarm that trains an
  operator to ignore the one line that matters when a reader really does drop a
  packet. Now discovered from the inputs that were handed out.
- `"sample"` was hardcoded to `"census-002"`. Census 003's results were written
  out labelled as census 002 before it was caught.

Each edit touched the script that certifies census 002, so the 927-verdict
reconstruction was re-run after every one of them: **886/35/6 and 3.8%
reproduce, with no key removed and no value changed.**

### Blindness, honestly stated

The readers for this run held no copy of the repo — it lives on the authoring
machine, not in the session that ran them — and used no tools at all, so they
could not have reached `data/policy/` had they tried. That is a stronger
guarantee than the tool allowlist, but it is a property of *where this run
happened*, not of the instrument. The allowlist in
`.claude/agents/census-reader.md` is what enforces the same thing for a run made
against the repo itself, and the results file records which of the two applied.

## 11.73 Tick 1 — the fan-out works, and the bottleneck moved

**17 families, 178 senses, 177 right, 1 wrong, 0 unsure. 0.6% against a 5%
threshold** (`data/policy/census-004-results.json`), and 0 notes flagged by
`tone_lint.py`.

This is the first shard authored by **parallel `family-author` agents — one per
family, each seeing only its own family's glosses** — rather than one session
writing every note serially. The design mirrors the reader fan-out and exists
for the same reason: an author holding five hundred glosses starts producing
plausible notes instead of grounded ones.

### What the calibration actually measured

The rate is the headline and it is not the finding. Three censuses now:

| | Read | Wrong | Rate | Authored by |
| --- | --- | --- | --- | --- |
| Census 002 | 927 | 35 | 3.8% | mixed, serial, unversioned |
| Census 003 | 92 | 1 | 1.1% | one session, serial |
| Census 004 | 178 | 1 | 0.6% | parallel agents, one per family |

The trend is real but the populations are small and the later two were authored
against a written instrument the earlier corpus never had. **Do not read 0.6% as
proof the fan-out is six times better than census 002's method.** Read it as: the
fan-out did not degrade quality, which is the only question tick 1 was asked.

### The bottleneck is the orchestrator

The tick was planned at ~500 senses across 43 families and closed at 178 across
17. Not because the authors struggled — every one returned clean, usable JSON —
but because **each author returns its work through the orchestrating session's
context**, and that context fills long before the authors run out of families.

That is a fixable defect and the fix is already in the agent definition:
`family-author.md` grants `Write` precisely so an author can put its JSON on
disk itself, where `family_merge.py` collects it and the orchestrator never sees
the content. Run against the repo through Claude Code, one tick can then be as
large as the queue allows. Run the way tick 1 was — from a session that holds no
copy of the repo, with authors returning JSON in their replies — a tick is
capped near twenty families, and pretending otherwise would just produce a tick
that dies half-finished.

### `family_merge.py`, and why the merge is strict

Fanning authoring out moves the risk to the merge: an author can return a member
that was never asked for, drop one that was, or attach a field nobody defined.
Tick 1 produced exactly the third — a stray `"chargeInvalid": null` riding
beside a valid charge — which would have gone into the corpus unnoticed.

So only `charge` and `tone` are taken, only for members the worksheet lists, and
a family that does not match is left **unannotated rather than partly
annotated**: a half-filled family is much harder to spot than an empty one.

### The one failure, and the one deliberate exclusion

*major*, glossed "of the elder of two boys with the same family name", was noted
as a tag between "two brothers of one name". The gloss does not say brothers.
`restriction` — the same fault class as census 003's, and again a single word
narrowing a gloss that every other word in the sentence respected. Repaired and
re-read clean by a reader that neither wrote it nor found it.

Worth noting that *minor*, its opposite number in the same family and authored
by the same agent, said "two boys sharing a family name" and passed. The fault
was not a misunderstanding of the gloss; it was one careless word in one
sentence, which is the failure mode that survives every rule and is exactly what
the blind read is for.

Separately, one member was withheld from authoring under 5.3: *white-bread*,
glossed "of or belonging to or representative of the white middle class". The
gloss is a claim about a racial and class group rather than about the word's
force, so it goes to the manual queue and was never auto-drafted. Its author was
told a member had been withheld and not to speculate about it.

## 11.74 Tick 2 — the bottleneck fixed, and the ruler caught moving

**25 families, 281 senses.** The read came back 281 right, 0 wrong, 0 unsure.
**That 0.0% is withdrawn.** What tick 2 actually established is two things, one
good and one uncomfortable.

### The fix works: 25 families in one session, not 17

Census 004 found the bottleneck was the orchestrator's context, not the authors,
and named the fix: let authors write their own JSON to disk. Tick 2 did exactly
that — each agent read the rubric and the worksheet from disk, authored its
family, and wrote its own output file. **No family's glosses or notes passed
through the orchestrating session at all.**

The result: 25 families and 281 senses in one session against tick 1's 17 and
178, at a fraction of the orchestrator cost, with the tick sized by the queue
rather than by the session. `family_merge.py` collected the files and reported
25/25 merged with no shape problems.

### The uncomfortable part: the ruler moved, and nobody noticed

The read returned **0.0% on 281 senses**. Censuses 002, 003 and 004 had returned
3.8%, 1.1% and 0.6%. A fourth point at zero is the kind of result that should be
distrusted before it is enjoyed.

The reason it should be: **the reading rubric was re-typed for this run and
gained a section the earlier runs did not have** — a "What is NOT a fault" list,
added in good faith to stop readers flagging register labels and neighbour
comparisons. It plausibly also made them more permissive, and nothing in the
process caught the change, because the rubric was being retyped into each run's
prompts rather than read from `.claude/agents/census-reader.md`.

**This is 11.62's own lesson, broken one tick after it was written.** The
instrument was put under version control and then not used.

### The adversarial re-read

Forty senses drawn at random from census 005 (seed 20260902), re-read under the
census-003/004 rubric, by a reader told the notes had passed a read that found
nothing and asked to establish whether that was soundness or leniency.

**39 right, 1 wrong — 2.5%.**

The fault it found is a fair one. *intellectual*, glossed "of or associated with
or requiring the use of the mind", was given a positive charge and noted as
"faintly flattering... credits it with demanding real thought". The gloss is
relational and carries no judgement; the note singled out the word *requiring*
and inflated it into praise. `wrong-charge`. Repaired to charge 0 with the note
naming the domain rather than praising the work, and re-read clean by a further
reader.

So tick 2's honest position: **under the 5% gate on either reading, but its rate
is not known more precisely than that.** One fault in forty is a wide interval;
zero in 281 is an instrument artefact. The corpus is fine; the measurement is
not, and the results file says so rather than carrying a number that flatters.

### What changed as a result

`census-reader.md` now opens with an instruction to use it verbatim — not
paraphrase it, not re-type it, not extend it for a run — with the reason stated,
so the next person to improve a rubric mid-run has to do it in the file and
declare a new baseline.

`census2_aggregate.py` gained a second guard. Tick 2's read came back 280/281
with one sense unread, which looks like a dropped read and was not: a reader had
transcribed a synset id wrong, so a sense that *was* read counted as unread while
its verdict vanished into a silent drop. The aggregator now reports verdicts for
ids that are not in the population, and points out that one unread sense
alongside one stray verdict is usually a mistyped id. The id was corrected, the
verdict and its reasoning left untouched, and the correction recorded in the
verdict file itself.

Both of those were invisible until the packets were kept on disk (11.72). A
census that throws its inputs away cannot notice either.

### One family withheld

*deaf* (11 senses) was pulled from automated authoring under 5.3 and written to
`data/families/held-5.3-deaf.json`. The family includes *deaf-and-dumb* and
*deaf-mute*, and deciding which of those words demeans and which does not **is**
the sensitive judgement — exactly the kind 5.3 reserves for a person. It was
never auto-drafted.

## 11.75 The charge gate, checked before spending fourteen ticks on it

11.62 set eligibility at size >= 8 and charged >= 70%, and noted the gate passed
340 families where 11.6 had estimated "~600 real families". With roughly
fourteen ticks left, that gap was worth an hour before it was worth 4,000 senses.

**The hypothesis was that the gate was too tight, and it was wrong.**

The suspicion had a good starting point. Thirteen already-annotated families fail
the gate on charge alone, and they are not marginal cases — *stupid* (0.55),
*ugly* (0.62), *angry* (0.59), *fat* (0.23), *gluttonous* (0.21). Those are as
close to archetypal connotation families as this corpus has. And 11.5 already
records that **SentiWordNet is wrong at this resolution** and was demoted to a
prior; building an eligibility gate on its labels looked like using a signal the
project had already rejected.

### The test

Thirty families, size >= 8, drawn across the charge range: eight currently
eligible, eight from 0.50-0.69, eight below 0.50, and six already-annotated
families as controls. Head words, charge values and group labels withheld —
raters saw only the member glosses and answered one question: **do these words
differ mainly in force, or mainly in denotation?**

Two raters from different model families, Fable 5.1 and Opus 5. They agreed on
**27 of 30**, which is what makes the numbers below worth reading at all.

| Band | n | mean connotation score |
| --- | --- | --- |
| charged >= 0.70 — eligible today | 8 | **0.62** |
| charged 0.50-0.69 — excluded | 8 | 0.22 |
| charged < 0.50 — excluded | 8 | 0.09 |
| already annotated by hand — control | 6 | 0.58 |

Correlation between the charge fraction and the triage verdict: **r = +0.51.**

### What it says

**The gate stays at 0.70.** Three things follow from the table:

- The charge fraction is a real predictor, not noise. The bands separate
  cleanly and monotonically.
- Lowering the threshold would import taxonomy, not recover missed connotation.
  The 0.50-0.59 band scored **0.00** — five sampled families, every one judged
  taxonomy by both raters. Dropping to 0.50 would add 188 families of mostly the
  wrong kind.
- The gate selects families of the same quality as the hand-picked corpus, 0.62
  against the controls' 0.58. It is doing what it was built to do.

The thirteen annotated families that fail it are real misses, but they are
exceptions, not evidence about their bands. A rule is not wrong because it has
exceptions; it is wrong when the population it excludes looks like the
population it admits, and here it plainly does not.

**11.6's "~600 real families" was an estimate made before any measurement
existed.** It is superseded by this one, and the gap it seemed to open was never
real.

### The finding that is not about the gate

Five of the eight currently-eligible sampled families were judged taxonomy by
both raters — *unfavorable*, *irrelevant*, *inaccurate*, *competitive*,
*genuine*. That is a ~40% taxonomy rate **inside** the eligible pool, and the
controls show the hand-picked corpus carries it too.

This is not a threshold problem and no threshold fixes it: the charge fraction
alone is simply a weak proxy for "these words differ in judgement". Antonym
linkage cannot sharpen it either — all thirty sampled families carry an
antonym-linked opposite, so the signal does not discriminate at size >= 8.

Recorded and not scheduled. It costs roughly 40% of tick effort on families
where there is little force to describe, which is a real price but a knowable
one, and the alternative is a signal the corpus does not currently hold.

### Why this is in the plan at all

The check cost one round of triage and changed nothing. That is the point worth
recording: **an hour spent confirming an instrument is not wasted when the
alternative was fourteen ticks run against an unchecked one** — and the result
that the hypothesis was wrong is exactly as useful as the result that it was
right would have been.

## 11.76 Tick 3 — the first read where the instrument was used as written

**25 families, 292 senses. 282 right, 10 wrong, 0 unsure — 3.4%.** Under the 5%
gate, and the most trustworthy number the project has produced, because it is
the first one measured with the rubric taken **verbatim** from
`.claude/agents/census-reader.md` rather than typed into the prompt.

That distinction matters more than the rate. Censuses 003, 004 and 005 were all
read under close paraphrases — 005's had to be withdrawn when an adversarial
re-read showed the paraphrase had gone soft (11.74). **Census 006 is the first
properly comparable baseline.** The earlier 1.1% and 0.6% should be read as
having been produced by rulers nobody had checked, which is exactly what 3.4%
against 002's 3.8% suggests: the corpus quality has been roughly flat, and the
dip in the middle was instrument drift, not improvement.

### What the ten faults were

| fault | n |
| --- | --- |
| gloss-mismatch | 5 |
| restriction | 3 |
| distribution | 1 |
| other | 1 |

`gloss-mismatch` dominating is the expected shape: five of the ten were an
author writing the lemma's famous sense rather than the sense printed above it.
*awesome* glossed "inspiring awe or admiration or wonder" was noted on the
colloquial approval sense; *discomfited* glossed "disappointingly unsuccessful"
was noted on embarrassment; *solid* glossed "providing abundant nourishment" was
noted on dependability. Every one is a writer who knew the word and stopped
reading the gloss — the failure 11.65 named and the one that keeps coming back.

### Repair needs the same discipline as authoring

Nine of the ten were repaired and re-read blind by readers that had neither
authored them nor found the original faults: **9/9 right**. But *awesome* took
three attempts and failed a **different fault class each time**:

1. Original: `gloss-mismatch` — the colloquial approval sense.
2. First repair: `world-not-word` — "large enough to stop someone in front of
   it" describes the thing, not the word, and quietly narrowed awe to physical
   vastness.
3. Second repair: `gloss-mismatch` again — "pitched above admiration" when the
   gloss lists admiration *inside* the sense, alongside awe and wonder.
4. Third: passed.

The third reader was told the note had failed twice **and** warned that repeated
failure is not itself evidence of a fault — otherwise a reader told "this has
been wrong twice" will find a third. It passed it, with reasoning that engages
the disjunction in the gloss rather than deferring.

**A repair is a new note and carries the same risk as the note it replaces.**
Nothing in the process had said so before this tick.

### The tenth fault, left standing

*constructive* was faulted `other`, and the reader's own reasoning says the note
is sound: the fault is that OEWN's example for this sense — "constructive
permission" — belongs to the legal *inferential* sense. **That is an upstream
example problem and no rewrite of the note fixes it.** It is left as authored
and counted against the rate rather than argued away, since a rate that excludes
the faults it finds inconvenient is not a rate.

It also names a fault class the corpus has not had before: the note and the
gloss agree, and the *example* is from a different sense. Recorded, not yet
promoted to a class of its own on one instance.

### The 5.3 queue stopped re-offering itself

*deaf* was withheld under 5.3 in tick 2 and came back at the head of the queue
for tick 3, because a withheld family has no tone notes and `annotated_synsets`
therefore cannot see it. A family is withheld precisely because a person has to
make the judgement, and a queue that keeps re-offering it to an automated pass
will eventually get one.

`worklist_build.py` now reads `data/families/held-*.json` and marks those
families held and ineligible. Ids are normalised to their numeric core, because
a worksheet renames `oewn-00685207-a` to `family-00685207-a` on the way out and
matching the literal string finds nothing — a check that silently passes is
worse than no check.

## 11.77 Tick 4 — a better number, and the confound that comes with it

**24 families, 298 senses. 294 right, 4 wrong, 0 unsure — 1.3%**, against census
006's 3.4%. Three faults repaired and re-read blind, 3/3 right.

### The number is confounded, and it is my confound

Both censuses used `census-reader.md` verbatim, so the **reading** instrument
held. But tick 4's author prompts carried a line tick 3's did not:

> The single largest fault class in this corpus is writing the lemma's famous
> sense instead of the sense printed above it. Read each `_gloss` before you
> write, not after.

That line was in the prompt and **not** in `family-author.md`. Which means the
drop from 3.4% to 1.3% cannot be attributed: it may be the warning working, it
may be an easier draw, and there is no way to separate them after the fact.

This is 11.74's failure repeated on the other side of the loop, one tick after
being fixed on the reading side. The reading instrument was locked down and the
authoring instrument was left to whatever the orchestrator typed — which is the
same mistake wearing different clothes.

The line is now **in** `family-author.md`, along with a second clause the tick's
faults argued for: where a gloss offers alternatives joined by "or", a note must
not quietly drop one. Two of tick 4's four faults were exactly that — *stray*
glossed "having no home **or** having wandered away from home" noted only on
homelessness, *mordacious* glossed "biting **or** given to biting" noted only on
the habit.

**Tick 5 onward is comparable to tick 4. Tick 4 is not cleanly comparable to
tick 3.** Recorded rather than smoothed over, because a rate whose provenance is
unclear is worth less than a smaller rate whose provenance is not.

### `bad-example` earns a name

*no-hit* was faulted `other`: the note is fine, but two of OEWN's three examples
use *no-hit* as a transitive verb rather than the adjective sense glossed. That
is the second instance of the shape — census 006's *constructive* carried an
example belonging to the legal *inferential* sense — and two instances is enough
to stop calling it "other".

**`bad-example`: the note agrees with the gloss, and the example does not.**

It is an upstream OEWN problem and no rewrite of a note fixes it. Both instances
are counted against their rates rather than argued away, because a rate that
excludes the faults it finds inconvenient is not a rate. What the class buys is
the ability to *count* them separately later and decide whether the examples are
worth filtering at import.

### Where the run stands

Queue down to **202 families**. At tick-4 size that is roughly eight more ticks
on the adjective line.

## 11.78 Tick 5 — the first uncaveated comparison, and a fault only visible in company

**21 families, 286 senses. 282 right, 4 wrong, 0 unsure — 1.4%.** All four
repaired and re-read blind, 4/4 right.

### What makes this tick different

Every previous rate came with a caveat. Census 005's rubric had drifted (11.74).
Census 006 was the first verbatim *read*, but census 007 then changed the
*authoring* prompt (11.77). Tick 5 is the first where **both instruments came
verbatim from their files and every author prompt was identical** — no
per-family hints, no extra warnings, nothing in the prompt that is not in
`family-author.md`.

So 1.4% against census 007's 1.3% is the first comparison in this project that
needs no asterisk. Two ticks, ~580 senses, holding at a little over 1%.

### `family-inconsistent` — a fault a solo reader cannot see

Two of the four faults were a shape the corpus has not named: **a note makes a
comparative claim about its family that the family's own notes contradict.**

- *ageless* called itself "the one admiring member here" — while *everlasting*
  sits beside it in the same synset at +1, its note calling it "warmer than
  *eternal*".
- *unending* said endlessness "sounds wearing" against *everlasting*'s
  "treasured", when the sense's own example is *the unending bliss of heaven*.
  Its charge was wrong too, and went −1 → 0.

Neither is a gloss fault. Both notes agree with "continuing forever or
indefinitely". They are wrong about **the family**, and that is only visible when
the sibling notes are in front of you — which no census has done, because packets
deliberately withhold the family id to stop readers inferring the spectrum
instead of reading the gloss.

The repair re-read therefore showed each note **with its siblings**, and the
reader checked every comparative claim against the neighbour's actual note. That
is the shape future repair rounds should use.

It is also **mechanically checkable**: a note claiming to be the only approving
or only disparaging member of its family can be tested against its siblings'
charges without a model in the loop. Recorded as a candidate for `tone_lint.py`,
not yet built.

### The 5.3 screen is a smoke alarm too

*illegitimate* was drawn for this tick — *bastardly*, *baseborn*, *misbegotten*,
*base*, *spurious*, *fatherless*: words that demean a person for the
circumstances of their birth, which they had no part in. Deciding how much
contempt each carries **is** the sensitive judgement, the same shape as
`held-5.3-deaf`. Withheld to `data/families/held-5.3-illegitimate.json`, 12
senses, never auto-drafted.

**The regex screen did not catch it.** It flagged two false positives in
*improper* on the word "offensive" and missed a family whose every gloss is about
birth status. The automated 5.3 screen has the same standing as `tone_lint.py` —
it points at things worth a second look, and the tick's families still have to be
read by someone before authoring starts. That reading is what caught this one.

### Where the run stands

Queue down to **181 families**, roughly seven more ticks on the adjective line.

## 11.79 Tick 6 — two checks moved from a reader's head into a file

**23 families, 287 senses. 280 right, 6 wrong, 1 unsure — 2.1%.** Seven repairs,
re-read blind, 7/7 after a second pass on two of them.

Three comparable ticks now: **1.3% / 1.4% / 2.1%**, all with both instruments
verbatim. Call it a little under 2% and stop reading the individual figures as a
trend — 287 senses puts a single fault at 0.35%, so the spread between these
three is four faults.

### `family-inconsistent`, now in the linter

Census 008 named the class: a note that is right about its gloss and wrong about
its family. It needs no reader at all — a claim to be the family's only approving
member is checkable against the siblings' charges.

`tone_lint.py` gained the rule. **The first version was too loose**, and the
backtest is what showed it: across every shard it produced one real fault and two
false positives — *"insisting the thing is exactly the one stated"* and *"marks
the one singled out for favor"*, where "the one" points at the referent rather
than at a position in the spectrum. Requiring the claim to reference the family
at all (*here*, *member*, *of these*, *among them*) keeps both real faults and
drops both false positives.

The backtest then earned its keep immediately: a genuine historic fault in shard
12 — *singular*, "the one word here that flatters", with *curious* beside it at
+1. Repaired. Every other shard clean, and tick 6 clean on the rule.

**The division of labour is worth stating.** A linter cannot discover a fault
class; it can only stop one recurring after a reader has named it. That is why
`tone_lint.py` stays a smoke alarm rather than becoming the referee.

### `sensitive_screen.py`, after three misses in three ticks

The 5.3 screen had been an ad-hoc regex retyped each tick, and it missed every
family it mattered for: *deaf* (tick 3), *illegitimate* (tick 5, where it fired
two false positives on the word "offensive" instead), and now *noncivilized* —
*savage*, *barbarian*, *barbarous*, *primitive*, *preliterate*, words applied to
whole peoples and carrying the history of what was done to them. Grading how much
contempt each carries **is** the sensitive judgement. Withheld to
`data/families/held-5.3-noncivilized.json`; third family in the manual queue.

All three were caught by reading the draw, not by the screen. So the term list
now lives in a file that accumulates what each miss taught, grouped by what the
words are aimed at: disability, birth status, peoples and culture, race, religion,
sexuality and gender, mental health, slur markers.

It flags 5 of 24 families in this draw and only one of them is real — it also
fires on *wild/untamed* for "savage" (glossed "wild and menacing", about animals)
and on three families for the word "black" (*black-and-blue*, *a black day*).
That is the intended shape: **a flag is not a verdict, and absence of flags is
not clearance.** The draw still gets read.

### Repairing a gloss-mismatch is the hardest repair

Seven repairs went out; five passed. *pathetic* and *invasive* both failed a
second time, **and both failed on the same class as the original** —
gloss-mismatch. *pathetic*'s repair still smuggled in the scornful-pity sense;
*invasive*'s called the sense an "incursion", denying the *gradually* the gloss
makes definitional.

That is not bad luck. The pull that produced the fault — the writer knows the
word, and knows its famous sense — is still acting on whoever writes the
replacement. Two of the three repair rounds in this project have needed a second
pass and both were gloss-mismatch.

The second-round reader was told both notes had been edited twice **and** warned
against two opposite failures: drifting a third time, and flattening the note
into something safe that says nothing. Both passed with reasoning that engaged
the gloss rather than deferring.

## 11.80 Tick 7 — the census measured 2.5%, and then a linter found six more

**20 families, 280 senses. 273 right, 7 wrong, 0 unsure — 2.5%.** One family
withheld under 5.3. Five repairs, all passing on the first re-read.

Four comparable ticks: **1.3% / 1.4% / 2.1% / 2.5%**. Still a little under 2%
across the four, and still four faults' worth of spread.

But the headline number is not the finding this tick. Two other things happened.

### Two of the seven faults were not writing mistakes at all

The reader marked *distinct* and *lucid* wrong, and in both cases said the note
was right and the **example** was not:

> *lucid*, "(of language) transparently clear" — example: **"pellucid prose"**
> *distinct*, "clearly or sharply defined to the mind" — example: **"trenchant distinctions between right and wrong"**

Both examples illustrate a *sibling lemma in the same synset*. The mechanism is
in the importer: **OEWN attaches examples to the synset, not to the lemma**, and
`wordnet_import.py` copied every synset example onto every member. So *lucid*
inherited *pellucid*'s example and *distinct* inherited *trenchant*'s.

`bad-example` has been a named fault class since tick 4 and had never had a
cause. It has one now, and the fix is mechanical: a member keeps only the synset
examples that use its own lemma or one of its forms. **11% of examples dropped,
7% of senses left with none** (2% among annotated senses). A card with no
example beats a card whose example is about a different word.

A second bug was hiding behind the first. The validator already checks that an
example uses its headword — 4,610 warnings' worth — and it passed both of these,
because `example_mentions` tested **bare substring containment**: "lucid" *is* in
"pellucid", "case" *is* in "pillowcase", "distinct" *is* in "distinctions". The
check now matches on a word boundary, treating hyphens as part of a word so
*broken-down* still matches "a broken-down fence". Warnings fell 4,610 → 4,284.

The re-import was verified field-by-field against the old bulk file: 111,466
entries, zero differences outside `examples`.

**The lesson is about which instrument found it.** A checker that had been
running on every pipeline pass for the whole project could not see this, because
its match was too loose; a reader looking at one card saw it immediately. That is
the reverse of 11.79's lesson and belongs beside it: a linter cannot discover a
fault class, and a reader cannot check 8,438 examples.

### `superlative-collision`, and what it says about the blind read

The census found three notes in the *unoriginal* family each claiming the mild
end of the spectrum — *commonplace* "the mildest reproach in the set", *stock*
"flattest word here", *timeworn* "softer than the rest". At most one can hold.

The reader could only see that **by luck**. Packets withhold the family id by
design, but `census_packets.py` slices contiguously so a family's senses stay
together — so a family small enough to fit inside one packet is visible anyway,
and a family split across a boundary is not. That is not an instrument.

So the check moved into `tone_lint.py`, in the same spirit as 11.79's
`family-inconsistent`: a positional superlative (or a comparative against "the
rest") plus a family reference, flagged when two members claim the same end. It
is a **contradiction check, not a judgement** — it never says which note is
right, only that two notes cannot both be the mildest.

The backtest across every shard found **14 notes in 7 collisions**: three pairs
in this tick's own shard, four in shards 12, 14 and 15. Two were contradicted by
their own charges — *empty-headed* at −2 calling itself "the flattest insult in
the set" beside *dizzy* at −1, and *laughable* at −2 calling itself "the lightest
verdict here" beside *silly* at −1.

**This is the uncomfortable part.** Census 010 read shard 16 blind and scored it
2.5%. A mechanical check then found **six more faults in the same shard**. The
census rate is not the note-level error rate; it is the rate *a blind reader
catches*, and for faults that live between senses rather than inside one, the
reader is structurally near-blind. Every census number in this document should be
read that way.

Seven repairs went out (one per collision, keeping the better-founded claim).
Five passed, two failed — and again on opposite faults:

- *well-worn* **drifted**, into the comfortable worn-in sense (a path, a pair of
  shoes) where the wear is a virtue, when its gloss makes overuse the fault. The
  third gloss-mismatch repair in this project to need a second pass.
- *perturbing* **traded one exclusive claim for another**: it stopped claiming
  the mildest position and started claiming to be the formal member of a family
  where *distressful* is already noted as the stiffer, more formal shape. The new
  rule does not catch that — it only knows mild and strong ends.

Both passed a second pass under a prompt that named both failures and warned
against flattening as much as against drifting.

### Also this tick

- `insane` withheld under 5.3 — fifteen of sixteen members glossed "informal or
  slang terms for mentally irregular". Fourth family in the manual queue, and the
  **first the screen flagged that was also real**: it fired on three families,
  the other two being *weak* (on *lame*, glossed "pathetically lacking in force",
  not the disability sense) and *guilty* (on *blameworthy* and five like it,
  culpability words aimed at no group).
- A reader mistyped a synset id again — `composed.oewn-01828067-s` for
  `oewn-00531471-a`, carrying over the id of the sense above it. Caught by the
  aggregator's stray-verdict guard, which 11.79 added after the same thing
  happened in census 009. Twice in two ticks is a pattern, not an accident.
- `odorous` was drawn and kept: fifteen of sixteen members are "smelling of X"
  compounds. It is the caveat-two taxonomy problem in its purest form, and it was
  kept rather than quietly dropped because hand-removing a family the gate chose
  is exactly the undocumented selection this project keeps paying for. Worth
  knowing that ~16 near-vacuous senses in a 280-sense tick **dilute the census
  rate downward**, which is a second reason the number is a floor.

## 11.71 The run plan — stages, and what stops each one

Written down because the last three things that went wrong went wrong by being
remembered instead of recorded. Each stage below has an exit condition that can
be checked rather than felt, and a stop condition that says when to abandon the
stage instead of pushing through it.

**The unit is a tick, not a shard.** A tick is roughly 500 senses authored and
then read blind before the next one starts. Measuring at the end of a long run
is the census 001 mistake stretched over time: a population, or a stretch of
work, that is only inspected after it is finished. A tick that comes in over
the 5% threshold costs one tick of rework; a run measured only at the end costs
all of it.

### Where the line stands

| | Pool | Annotated | Queued | Instrument |
| --- | --- | --- | --- | --- |
| Adjective | 6,826 families / 29,039 members | 63 families, 766 senses | **303 families, 4,543 members** | ready |
| Verb | 2,495 families / 31,811 members | 9 families, 118 senses | none — worklist never built | absent |
| Adverb | 5,571 senses, 2,505 pertainym links | 156 inherited | n/a — arrives free | automatic |
| Noun | 11,484 families / 129,506 members | 0 | 1,318 candidates, filter known bad | blocked |

### Stage 1 — close the debt, and make authoring an instrument too

Shard 9's 92 senses are authored and unread, so the corpus currently has a
measured rate (3.8%) that does not cover all of it. Nothing else should start
while that is true.

1. `.claude/agents/family-author.md` — model `opus`, effort `xhigh`, tools
   restricted the way the reader's are. Authoring is the half of the loop still
   depending on whoever happens to be running the session, which is the exact
   condition 11.62 removed from reading.
2. Blind-read shard 9 through `census-reader.md` (two packets).
3. Push.

**Exit:** shard 9 has a rate, and both halves of the loop are files.
**Stop:** if the shard 9 read comes in over 5%, Stage 2 does not start — the
notes authored today are the template for the next 4,500, and a bad template
should not be copied 45 times.

### Stage 2 — tick 1, the calibration tick

The first tick authored by parallel `family-author` subagents rather than by one
session writing serially: one agent per family, each seeing only its own family's
glosses. This is the same design as the reader fan-out, and it exists for the
same reason — context stays fresh per family, and a writer holding 500 glosses
starts producing plausible notes instead of grounded ones.

~500 senses, then a blind read of the whole tick.

**Exit:** tick rate under 5%.
**Stop:** over 5% means the parallel-author design is worse than serial
authoring, and the honest response is to say so and go back, not to tune the
prompt until the number moves. B1 played this role for batches; this plays it
for the fan-out.

### Stage 3 — ticks 2 onward, the adjective run

Repeat until the queue is empty: ~500 senses authored, read blind, decide.
4,543 members is roughly **nine ticks**.

Two things to revisit with evidence rather than argument:

- **The 70% charge gate.** It passes 340 of 5,911 families where 11.6 expected
  "~600 real families". That gap is either the gate being too tight or the
  estimate being optimistic, and two ticks of data will say which. Do not touch
  it before then.
- **The adverb dividend.** 156 senses inherited today against 2,505 pertainym
  links available. Every adjective tick pays some of that gap automatically, so
  the adverb line needs no stage of its own — it needs adjectives.

**Exit:** adjective queue empty.
**Stop:** two consecutive ticks over threshold. That is a method problem, not a
batch problem, and 5.5 already says what to do about it.

### Stage 4 — verb

The verb line has 118 annotated senses that rode along inside adjective shards
and no queue at all. `worklist_build.py --pos v` has never been run.

Verbs are not adjectives with different words: 31,811 members across 2,495
families means they cluster far denser (12.8 members per family against the
adjective 4.3), so the size and charge gates calibrated in 11.62 are **not**
transferable and must be recalibrated against what the verb pool actually looks
like. A screening pass comes before any authoring.

**Exit:** a verb worklist with gates justified by verb data, then ticks as
Stage 3.

### Stage 5 — noun, and the filter that has to be fixed first

The largest pool and the least ready. 1,318 families have four or more
non-neutral members, but `screening-nouns-001.json` records why that number
cannot be trusted: *pneumonia* and *tranquilizer* score high because the
**thing** is bad, not because the **word** carries force.

That is the `world-not-word` fault — the same class the tone notes kept failing
on — operating one level up, at family selection. Annotating nouns before it is
fixed would build a shard out of words that have no connotation to describe,
and the read would find it the expensive way.

**Exit:** a screening rule that separates a word's force from its referent's
desirability. Until then the noun line stays closed.

### What is deliberately not in this plan

Rebuilding assets and cutting a release is not a stage. The build already runs
green on every pipeline pass, and the app ships whatever the corpus currently
holds — B0 was designed so nothing is blocked on curation. A release is a
decision about timing, not a step that has to be completed in order.

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
