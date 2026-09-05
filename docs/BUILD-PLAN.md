# ColorDict — the staged build plan

**Status: stages 0-6 done on 2026-09-03; stage 7 waits for approval.**
**Stage state is measured by `python tools/status.py`, not by this line.**

This replaces the forward-looking half of `HANDOFF.md` and supersedes both
planning documents (*Vocabulary and Word Generation Plan*, *Graph Engineering*)
and plan sections 11.83 and 11.84. It combines them with what this repo already
has and already measures.

`DICTIONARY-PLAN.md` stays as the archive — the record of how the method was
arrived at. Nothing should read it front to back. `HANDOFF.md` stays as the
orientation doc: what is being built and what will bite you. **This file is the
plan.**

---

## 1. What we are building, in one paragraph

A learner's dictionary where **books decide which words matter**, **Open English
WordNet decides what the senses are**, and **AI makes them readable** — plus a
connotation layer, on the words that have one, that tells you how a word lands
against its neighbours. It ships as a file inside an offline app. It is not a
service.

---

## 2. The four decisions this plan rests on

**Books select, not a gate.** The charge gate was a proxy for "does this word
carry connotation," and 11.75 measured it choosing connotation five times in
eight and taxonomy three — as good as the hand-picked corpus, and no better. *(This
line read "taxonomy five times in eight" until 2026-09-05; the recount is in
§11.75's results file.)* The case for books does not rest on the gate being bad.
A word a reader actually meets in prose is not a proxy. Until real users generate
a report log, a book is the closest stand-in for demand there is.

**Not every word needs connotation.** `connotation: null` is a valid, common and
correct answer. *table*, *emerge*, *chapter* get a good entry without one.
Absence renders as absence, never as "neutral".

**Connotation keeps its own path, and its own measurement.** The Enricher writes
definitions and examples. It does **not** decide charge and tone for words that
belong to a family, because contrast cannot be written one sense at a time — the
author has to see *subaltern* to write *lowly*. The family pipeline, the blind
read, the third hand and the 5% gate all stay exactly as they are. The 44% audit
is why.

**Bundle, do not serve.** The planning document assumed lazy generation because
it assumed covering all ~114,000 WordNet entries. Books make the scope a few
thousand, which fits in a file. So entries are pre-generated and shipped inside
the app. No backend, no search-time model call, no telemetry, still works on a
plane. This removes nine of the thirteen nodes in the graph document.

---

## 3. What already exists

| | state |
| --- | --- |
| Book → ranked vocabulary with contextual POS | **built** (`book_ingest.py`, 17s per novel) |
| WordNet senses, glosses, relations | **built** (OEWN 2024) |
| Connotation corpus | **built** — 235 families, 2,885 senses, ~2% measured |
| Authoring + blind reading instruments | **built** — 11 censuses |
| Validation, StarDict build, ship path | **built** — 0 errors on 3,058 entries |
| Android / desktop / web app | **built** — 5,860 lines, ships today |

Roughly 70% of the target system. The stages below are the other 30%.

### Two things measured on 2026-09-03 that reorder the plan

**~~The app ships 3,058 entries, not 114,000.~~ Corrected 2026-09-03: it ships
111,466.** 3,058 was the *reviewed* count that `status.py` validates.
`dict_pipeline.py:80` passes `derived-bulk.jsonl` straight to `dict_build.py`,
`popup-en.ifo` read `wordcount=111466`, and *emerge*, *market* and *book* all
resolved before stage 1 began. The draft read a validation figure as a coverage
figure - the exact mistake section 7 warns about. Stage 1.2 below was already
shipped; 1.1 and 1.3 were the real work.

**`dict_build.py` prints a connotation row for a bare SentiWordNet label.** The
render condition fires on `label in ("positive","negative")` with no authored
note behind it. So shipping the bulk layer as-is would publish 111,466 entries
labelled by the exact source 11.5 rejected for scoring *skinny* above *slender*.
One condition has to change before any coverage ships.

---

## 4. The stages

Each stage names what ships, when it is done, and what stops it. **A stage is not
finished until its done-check passes.**

The order is deliberate and it is not the order these documents were written in.
Everything free comes first, shipping comes before generating, and the single
expensive stage sits behind three cheap stages that tell you what to spend on.

### Stage 0 — Foundations · *no model spend*

- **0.1** Adopt **OEWN 2025 standard edition** — common nouns, verbs, adjectives,
  adverbs, with proper nouns moved upstream to Namenet. That is the split this
  plan wanted anyway.
- **0.2** Reconcile all 2,885 authored senses against 2025 IDs. Print what moved,
  merged, vanished, or changed gloss.
- **0.3** Repoint `book_ingest.py` at OEWN. It currently validates against
  nltk's **Princeton WordNet 3.0** — a different lexicon from the corpus, which
  makes every coverage figure it has produced wrong.
- **0.4** Entry schema + `entry_validate.py`: required fields, synset
  traceability, POS match, sense-count cap, duplicate detection, and
  **source gloss unchanged**.
- **0.5** `status.py` reads `build-stages.json` and prints stage state.

> **Done when:** the reconciliation report shows every authored note still bound
> to a live sense; `status.py` clean; 0 validation errors.
> **Stop:** any note whose gloss changed in 2025 is queued for a blind re-read
> before it ships. A note is only as good as the gloss it was measured against.

### Stage 1 — Coverage, and honest connotation · *no model spend*

- **1.1** Change the render condition in `dict_build.py` so a connotation row
  appears **only** when there is an authored note. A bare SentiWordNet label is
  not a connotation; it is a prior, and 11.5 measured it getting the flagship
  pair backwards.
- **1.2** ~~Ship the plain WordNet layer beneath the annotated one.~~ Already
  shipping when this was written (see the correction above). No work done, none
  needed.
- **1.3** Rebuild `.syn` over the full entry set so inflected forms resolve.

> **Done when:** *emerge*, *market* and *book* return a real entry; *skinny*
> returns a spectrum; nothing returns a SentiWordNet label.
> **Stop:** if a bare label survives anywhere in the build, this stage is not
> done — that is the one thing this dictionary must never assert.

### Stage 2 — The report loop · *no model spend*

The empty state **is** the report button. When a sense has no authored note,
render *"Connotation not recorded — report this word"* instead of nothing. Per
11.7 that is a builder change plus a CSS class, so it reaches Android, desktop
and web with no app code.

Reports append to a **local, append-only log** the user exports and sends. Never
telemetry. Reason codes separate *unannotated* from *not-found*.

> **Done when:** you can report a word on a device and export the log.
> **Stop:** if the log would ever leave the device on its own, it does not ship.

### Stage 3 — Ship it to ten people · *no model spend*

APK on GitHub Releases, web build on Vercel. Both already wired.

This is the stage the old strategy never had. After eleven censuses and 2,885
measured senses, nobody except the author has looked a word up in this
dictionary, and every argument about what to build next is a guess about a user
who does not exist.

> **Done when:** someone who is not you has looked a word up in it.
> **Stop:** a release without Stage 2 is a wasted release — you learn nothing
> you can act on.

### Stage 4 — Ranker and Enricher, proven on 50 entries · *first spend, tiny*

- `sense-ranker.md` and `enricher.md` as instrument files in `.claude/agents/`,
  versioned like the two that already exist.
- Run both on **50 entries**. The Ranker emits synset IDs only, so the Validator
  rejects a bad ranking before the Enricher spends anything.
- Blind-read those 50 — different model family, rubric read from disk.

> **Done when:** you have a **measured** cost per entry and a defect rate on 50
> real entries, not an estimate of either.
> **Stop:** if the sample reads badly, fix the rubric file and re-run 50. Never
> scale a rubric nobody has read.

### Stage 5 — The null audit · *small spend*

Sample the entries where the Enricher returned `connotation: null` and blind-read
them with one question: **is this word really connotation-free?**

Null is always the safe answer, so it will be over-used, and the field this app
is built around would quietly empty out.

> **Done when:** the false-null rate is known.
> **Stop:** if it is high, the Enricher loses the connotation field entirely and
> every candidate goes to the family path instead.

### Stage 6 — The search side · *no model spend*

Resolver: search term → all valid lexical matches, with `match_type` and
`morphology`. `emerged` → *emerge*+VERB+inflected. `saw` → three branches.
`emerging` → two.

> **Done when:** `saw`, `emerging`, `better` and `left` each return the correct
> multiple matches, and an inflected form shows the "past tense of…" line without
> the user knowing the headword.

### Stage 7 — Generate book one, and bundle it · *the real spend*

Pick a coverage target from the corpus report — the curve decides, not a word
count. Pre-generate, validate, write into the StarDict file the app already
ships. No backend, no search-time call.

> **Done when:** the app opens offline and a book-selected word returns a
> readable entry.
> **Stop:** a spend cap is set **before** the run starts. A validation failure
> rate above 10% halts it — that is a rubric problem, not a batch problem.

### Stage 8 — The first demand tick

`reports_ingest.py`: dedupe by sense, rank by report count, honour a
neutral-by-design deny list so *table* is not rediscovered forever, and group
reported senses into families before queueing.

> **Done when:** a word somebody reported appears in an update.
> **Stop:** below ~20 distinct reported senses the queue is noise — draw from the
> book instead.

**Deferred 2026-09-05 — the mechanism does not exist.** Stage 8 *is* the demand
tick: `reports_ingest` ranks by report count, which needs reports from people who
are not the author. There are none. The author is the only user, and the three
reports that exist are all from their own device and all singletons — ranking
three 1s produces no order, which is exactly the noise the stop condition names.
Stage 3 closed on the shippable artefact and said so; its real done-check,
*"someone who is not the author has looked a word up in it"*, still has not
happened.

So **book-driven selection is the standing method**, and stage 7 measured it:
take a real book, take the coverage band, rank each sense by what a reader of
*that* book meets — 3.09% defect, `rank_wrong` 0. The gate it displaces was
measured in §11.75 at three of eight eligible families being taxonomy and five
connotation *(corrected 2026-09-05 from "five of eight taxonomy")*. The book is a
demand signal that needs no users; the gate is a decent proxy that the book does
not need to beat, only to supplement.

What this gives up, stated once: every census measures whether the dictionary is
**correct**. Nobody has measured whether it is **useful** to someone reading a
novel, and book-driven work does not substitute for that. Accepted deliberately.

Nothing is deleted. If readers appear, stage 8 resumes unchanged. `pg74` and
`pg2701` are already ingested under `data/build/books/`.

---

### Stage 9 — The reading loop, proven on the backlog · *small spend*

Lane A and Lane B were two pipelines that shared a corpus and a doctrine and had
no working connection. The Enricher's `candidate` verdict was a handoff to the
family path that nothing implemented: 121 senses across four runs — *lady*,
*civility*, *deceive*, *agreeable* — shipped with a verdict that they judge and
no note saying how. Invisible by construction: a blind reader shown a card with
no tone note has nothing to mark wrong. The author found it by using the app.

This stage builds the join and proves it on that backlog. `candidate_families.py`
routes each candidate to its smallest family, restricted to members the books
actually use and capped at 20 — 3,958 member senses naive, 516 restricted, 86
worksheets. One `family-author` per worksheet, blind read, then the **first
repair pass ever made by a third hand**: `repairer.md` is the instrument, and it
joins the drift gate alongside `family-author.md` and `census-reader.md`, which
had been outside it since the gate was written.

The 25 candidates the cap holds back are named in the record file, not dropped.
They are the uncomfortable part — *man*, *lady*, *woman*, *gentleman*, *child*,
*servant* — the words carrying the most connotation, held because WordNet offers
only a hypernym tree for them. That is stage 12's problem, written down here so
it is not rediscovered.

> **Done when:** every candidate in `data/policy/*/results.json` is either
> authored or held with a reason, and the blind census on the authored senses is
> under 5%.
> **Stop:** if the census on book-restricted families reads worse than 007–011,
> the cap is the problem — the median worksheet is 5 members, and 55% of existing
> notes name a sibling. Change the cap in the tool and treat the next run as a
> new baseline; do not author more under a cap that reads badly.

### Stage 10 — Close book one · *the loop at full width*

Book one's vocabulary still has 6,914 senses on 1,785 words with no connotation
verdict anyone made: 2,817 ranked but never judged, 4,097 never opened. Run the
loop over them — Enricher only where a ranking already exists — and let the join
route whatever candidates fall out.

Do not draw by raw frequency alone. *so*, *say* and *now* head the list with ten
senses each, all correctly plain; a tick spent confirming that function words do
not judge is a tick wasted.

> **Done when:** no sense on a book-one word lacks a verdict someone made — null
> is a claim, not a default.
> **Stop:** spend cap set before the run. Two consecutive ticks over 5% is a
> method problem, not a batch problem.

### Stage 11 — Book two · *breadth, after depth*

`pg74` or `pg2701`, both already ingested under `data/build/books/`. The loop
unchanged. Tone coverage will fall when the book lands, because the denominator
grows first and the candidates follow; that is expected and should be watched,
not corrected.

> **Done when:** a second book's vocabulary ships in the StarDict file and the
> app resolves its words offline.

From here the reading loop is not a project. It is the standing method, and
adding a book is the unit of work — see §5.

### Stage 12 — The held set · *research, conditional*

A connotation family for *lady* that is not the 603 hyponyms of `woman`. The
family path works because an author sees siblings and writes contrast; the held
words have no siblings WordNet can offer at a size anyone could read. Candidate
mechanisms: adjective satellite links, antonym pairs, pertainyms, co-occurrence
in the book sentences. None is proven.

This stage exists only if a mechanism is found. Until then the held set stays
held and visible, and no sense-level author is written to absorb it — a one-word
author with no neighbour to name reintroduces the 44% fault pattern into a
corpus whose census cannot see it.

> **Done when:** *lady*, *gentleman*, *woman* and *servant* render on a spectrum
> a reader of *Pride and Prejudice* would recognise, and it reads under 5%.
> **Stop:** if no in-repo signal groups these words at ≤20 members, say so in
> §6 and stop looking.

---

## 4b. Why this order and not the other one

Stages 0 and 1 are genuine foundation: the lexicon and the validator. Everything
downstream is wrong or unverifiable without them, and both cost nothing, so they
go first without argument.

**Stages 4 through 7 feel like foundation and are not.** The Enricher's rubric,
how many senses to show, what a learner definition should sound like — those are
product decisions wearing engineering clothes. They do not get better with more
care up front. They get better with someone telling you the definitions read like
a textbook.

This is the failure mode this project has already had once. Eleven censuses is
excellent foundation work, and it is also eight months without a user. Foundation
work is comfortable because it succeeds on its own terms; shipping is
uncomfortable because it can fail. Ordering Stage 3 before Stage 7 is the
correction.

## 5. The lane that never stops

There was one, and now there is a different one.

Until 2026-09-05 the **connotation path** ran on its own selection: family draw
→ §5.3 hand screen → author per family → blind read → third-hand repair → 5%
gate. It built 235 families and 2,885 senses at ~2%, and HANDOFF §2 recorded its
weakness honestly: about 40% of what the gate admitted was taxonomy, not
connotation, and no threshold fixes that.

From stage 9 the standing method is the **reading loop**, and the connotation
path runs *inside* it rather than beside it. A book names the words; the Ranker
picks the senses a reader meets; the Enricher writes the entry and asks whether
the sense judges; a candidate summons its family, restricted to the book and
capped; the author writes the spectrum; a blind reader marks faults; a third
hand repairs; a fresh reader checks. Same instruments, same three rules, same 5%
gate. What changed is who chooses the work: a reader, not a charge fraction.

It still does not have stages, for the same reason as before: it is not a
project, it is the thing that makes this dictionary different from
Merriam-Webster. Stages 9–11 are the work of standing it up; after that, adding
a book is the unit of work.

The gate-driven draw is not deleted. `worklist_build.py` and the queues stay on
disk as the second draw — when a book summons nothing, and as the pre-registered
control arm every candidate census carries (stage 9). §6 records how "filler"
came to be said and why it was withdrawn.

Current: 235 families, 2,885 senses, ~2% across censuses 007–011, no outstanding
repairs. 121 candidates awaiting the join (stage 9).

---

## 6. Cancelled

| | why |
| --- | --- |
| Verb screening pass | census 011 answered it: verbs read 2.2% |
| Noun selection filter | the book keeps *pneumonia* out; no filter needed |
| Draining the adjective queue as the default | demoted to filler on 2026-09-02 on the sentence "the gate picks taxonomy five times in eight" — which a 2026-09-05 recount of §11.75's raw rater files inverted: five of eight are connotation, 0.62 against the hand-picked 0.58. Kept as the second draw and the census control arm; still not the default, because a book is demand and the gate is a proxy |
| Lazy generation, the backend, endpoints | bundling removes the need |
| 9 of the 13 graph-document nodes | speculative before a single user |

Nothing is deleted. The queues stay on disk.

---

## 7. Tracking

Stage state lives in `data/policy/build-stages.json` and `status.py` prints it,
so a cold session sees where the build is without reading this file. One line per
stage: `not_started | in_progress | blocked | deferred | done`, with the date and the commit
that closed it.

**The rule that makes tracking honest:** a stage is marked done by its own
done-check passing, never by someone deciding it looks finished. Three times in
one day a join or a diff flag reported a cleaner number than the truth. Measured
state beats remembered state.
