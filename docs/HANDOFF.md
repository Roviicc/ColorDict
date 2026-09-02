# ColorDict — orientation for whoever picks this up next

`DICTIONARY-PLAN.md` is the full record: every decision, every measurement, every
mistake, in the order they happened. It is long on purpose. **This file is the
entry point** — enough to understand what is being built, whether it is working,
and what to do next, without reading the whole history first.

Last updated after tick 7 / census 010, plus the two follow-ups in 11.81.

> **Resuming, or short on context?** Run `python tools/status.py` — it measures
> the state live (git, instrument drift, corpus, queue, censuses, lint) instead
> of trusting the numbers written here, which go stale. The `/orient` skill in
> `.claude/skills/orient/` wraps that plus the rules that are not negotiable.

---

## 1. What is being built

A dictionary that records **how a word lands**, not just what it means.

Ordinary dictionaries give six words the same definition. WordNet glosses
*lower-ranking*, *subaltern*, *secondary*, *junior-grade*, *lowly* and *petty*
all as "inferior in rank or status". They do not feel the same, and no dictionary
says so:

| word | charge | note |
| --- | --- | --- |
| lower-ranking | 0 | "Bare comparison, naming where someone sits relative to another with no colour of its own." |
| subaltern | 0 | "Formal and stiff, framing the inferiority as a fixed place in a chain of command." |
| lowly | −2 | "Diminishing — the position is painted as humble and small, so the low standing rubs off onto whoever holds it." |
| petty | −2 | "Belittling — the rank is not merely below others but slight enough to seem hardly worth counting." |

That table is the product. Each sense gets a **charge** (−3…+3) and a one-sentence
**tone note**, and every sense also renders as a row on its family's spectrum.

**Ships as** a StarDict file bundled into the Android app, the desktop build and
the web build. The app already works end to end and ships whatever has been
annotated so far — nothing is blocked on curation.

**Data**: Open English WordNet 2024 (CC BY 4.0) for senses and glosses;
SentiWordNet 3.0 as a *prior only*, never as a label (see §11.5 — it is wrong at
this resolution). The connotation layer is authored, then measured.

---

## 2. Is it working? Yes, with two honest caveats

**The quality problem is solved.** An audit of the first hand-written shards
found **44% of notes wrong** — confidently, plausibly wrong, things like *kindly*
"now used mostly of the elderly", which sounds authoritative and simply is not
true. That failure produced the rule the whole project now runs on (§11.65), and
the rate since:

| census | senses read | wrong | rate | note |
| --- | --- | --- | --- | --- |
| 001 | 767 | 198 | 25.8% | first full read |
| 002 | 927 | 35 | 3.8% | first blind read |
| 003 | 92 | 1 | 1.1% | typed rubric variant |
| 004 | 178 | 1 | 0.6% | typed rubric variant |
| 005 | 281 | 0 | **withdrawn** | rubric had drifted; adversarial re-read found 2.5% |
| 006 | 292 | 10 | 3.4% | first verbatim read |
| 007 | 298 | 4 | 1.3% | authoring prompt changed — confounded |
| 008 | 286 | 4 | 1.4% | both instruments verbatim; first clean comparison |
| 009 | 287 | 6 | 2.1% | comparable |
| 010 | 280 | 7 | **2.5%** | comparable; 2 of the 7 were an importer bug, not authoring |

Threshold is 5%. **Read 007–010 as the real number** — a little under 2% across
the four — and treat 003–005 as produced by rulers nobody had checked. At ~285
senses a single fault is 0.35%, so the spread between those four is four faults.

**A census rate is a floor, not the error rate.** It measures what a blind reader
catches. In tick 7 a new mechanical check found six more faults in the shard
census 010 had just scored at 2.5% — faults that live *between* senses in a
family, which a reader shown one card at a time structurally cannot see (11.80).

**Throughput is solved too.** Per tick: 92 → 178 → 281 → 292 → 286 → 287 → 280 senses, and
the jump from 178 came from diagnosing a bottleneck (the orchestrating session's
context) rather than working harder.

**Caveat one — about 40% of what gets annotated is arguably not a connotation
family.** A blind triage with controls (§11.75) found that five of eight
currently-eligible families were judged *taxonomy* rather than *connotation* by
two independent raters. The gate is still the best available — lowering it makes
things worse, and it selects at the same quality as the hand-picked corpus — but
the charge fraction is a weak proxy and no threshold fixes that.

**Caveat two — the adjective line is one of four, and the other three are barely
started.** See §4.

---

## 3. How the method works

Three ideas, each of which was learned the expensive way.

**1. The gloss is binding.** A note must agree with the definition printed above
it, not with the word's famous sense. *voracious* glossed on food cannot be noted
on reading. This is still the largest fault class every single census.

**2. Stay inside the word.** Say what the word does — force, register, how it
differs from its neighbour. Never who uses it, how often, or where it came from.
There is no corpus and no etymological source here, so those claims are guesses
wearing the clothes of facts. This does *not* mean duller notes: the notes that
pass are the vivid ones.

**3. The hand that writes is never the hand that reads.** Notes are authored by
Opus and read blind by Fable — a different model family — and the reader sees the
gloss, the charge and the note, and nothing about how the sense was scored before.
Repairs go to a *third* reader who neither wrote nor found the fault.

### The instruments are files, not habits

This is the lesson that cost the most. Both halves of the loop live in version
control and are used **verbatim**:

- `.claude/agents/census-reader.md` — the reading rubric, model, effort, tool allowlist
- `.claude/agents/family-author.md` — the authoring rubric, same

Twice, a rubric was retyped into a prompt instead of read from its file, and both
times the measurement went wrong in a way nobody noticed until an adversarial
check (§11.74, §11.77). If you change a rubric: change the **file**, say so in the
plan, and treat the next tick as a new baseline.

### The tick

One unit of work, ~25 families / ~290 senses:

1. `worklist_build.py` ranks and gates the queue → draw the top families
2. run `sensitive_screen.py` on the draw, then **read the draw yourself** for
   §5.3 sensitive families — the screen is a smoke alarm that missed the real
   family three ticks running before it was rewritten
3. `family_worksheet.py` builds the annotation skeleton
4. one author agent per family, each reading rubric + worksheet from disk and
   **writing its own JSON to disk** (never returning it through the orchestrator —
   that is the bottleneck)
5. `family_merge.py` collects them, strictly: only `charge` and `tone`, only for
   members the worksheet lists, and a family that does not match is left
   unannotated rather than partly annotated
6. `tone_lint.py`, `family_apply.py`, `dict_pipeline.py --no-build`
7. `census_packets.py` → blind read → `census2_aggregate.py`
8. repair, re-read the repairs blind, commit

**Stop conditions:** a tick over 5% stops the run. Two consecutive over 5% is a
method problem, not a batch problem (§5.5).

---

## 4. Where the work actually stands

**227 families · 2,786 annotated senses · 2,963 reviewed entries · 0 validation
errors.**

| line | pool | done | queue | state |
| --- | --- | --- | --- | --- |
| **Adjective** | 5,911 candidate families | 227 families, 2,786 senses | **147 families / 2,434 members** | running, ~8 ticks left |
| **Adverb** | 5,571 senses, 2,505 pertainym links | 489 senses | n/a | **self-feeding** — inherited free from adjectives |
| **Verb** | 2,494 candidate families | 116 senses | **70 eligible / 937 members** | queue built; screening pending, ~3 ticks |
| **Noun** | 11,484 families / 129,506 members | 0 | 1,318 candidates, filter known bad | **deliberately closed** |

The adverb line is the quiet win: **489 senses that nobody authored**, inherited
through WordNet's pertainym links, growing automatically with every adjective
tick. Nine adverbs sit on a deny list where their own gloss does not match the
adjective sense they point at.

---

## 5. The plan, in stages

Every stage names its own stop condition. Stages A and B are independent and can
run in parallel; C depends on A, D depends on B.

### Stage 0 — standing, every tick

Push at the end of each tick rather than letting commits pile up. Carry any
outstanding repair into the next tick's repair round rather than opening a cycle
for it. **Currently outstanding:** *hard* in `family-01072500-a`
(annotated-015) claims "the least damning word in the family" while *day-old*
sits beside it at charge **0**. It was found by the pass that wrote the rule, so
§11.65's discipline requires a hand that neither wrote the note nor found the
fault.

### Stage A — tick 8, deliberately oversized

Run the §3 loop, but draw **35–40 families instead of ~23**. Ticks have sat at
~285 senses for five ticks running, and §11.73's orchestrator cap was already
fixed — `family-author.md` grants `Write` so authors put JSON on disk and the
orchestrator never holds it. The plateau is probably habit, not a ceiling, and
this is the cheapest way to find out.

Both instruments are unchanged through 11.82, so **tick 8 is comparable to
007–010 and is not a new baseline.**

> **Stop:** a tick over 5% stops the run. Two consecutive over 5% is a method
> problem, not a batch problem. If the oversized draw degrades the rate, the
> cause is the size, and the next tick goes back to ~23.

### Stage B — verb screening pass

The queue exists (`data/worklist-verbs.tsv`, 70 eligible / 937 members). What it
does **not** yet have is evidence that those 70 are connotation families rather
than taxonomy. Run a §11.75-style blind triage **with controls** — the same
design that measured the adjective gate — before any verb authoring.

> **Stop:** if the triage judges verbs materially worse than the adjective
> baseline, the verb line does not open on these gates. Do not lower the gates
> to compensate; §11.75 measured that lowering makes selection worse.

### Stage C — drain the adjective queue

147 families / 2,434 members, ~8 ticks. Nothing blocks it: both instruments are
verbatim, the gate is checked, the queue is ranked.

> **Stop:** the queue empties, or a tick breaches 5%.

### Stage D — verb authoring

Only after Stage B passes. ~69 families / 884 members, ~3 ticks. One practical
note for the worksheet stage: five eligible verb families are larger than any
adjective family ever authored (*knock* at 53, *fail* at 38), and 53 members is
a lot to hand one author agent — consider splitting the largest.

### Stage E — nouns, blocked

`pneumonia` and `tranquilizer` score high because the **thing** is bad, not
because the **word** carries force. That is `world-not-word` at family-selection
level — the same fault class the notes kept failing, one layer up. Annotating
before the filter is fixed would build shards out of words with no connotation
to describe.

> **Stop:** stays closed until the screening filter distinguishes a bad thing
> from a loaded word.

### Then: the decision this run has not yet faced

Stages C and D together are **~12 ticks and end with roughly 6,000 annotated
senses**, at which point *the queue is empty and the method has nothing left to
draw*. That is not a finish line anyone chose — it is what a 5.7% gate implies.
§11.75 already measured that lowering the gate makes selection worse, so this
ceiling is real rather than a tuning problem.

**Decide before Stage C ends, not after:** is ~6,000 senses the product, or does
the method need a way to reach the other 94%? Nothing in the current plan
answers this.

**Not a step:** rebuilding assets and cutting a release. The build runs green on
every pipeline pass and the app ships whatever the corpus holds. A release is a
timing decision, not a prerequisite.

---

## 5b. What success looks like

Concrete, so a session can tell whether it is winning:

| | target | now |
| --- | --- | --- |
| Census error rate | **< 5%**, the hard gate | ~2% across 007–010 |
| Validation errors | **0**, always | 0 (2,963 entries) |
| Instruments unchanged within a comparison window | required for a rate to mean anything | unchanged since 11.81 |
| Repairs re-read blind | every repair, no exceptions | held |
| Adjective queue | empty | 147 families left |
| Verb line | screened, then drained | queue built, screening pending |

**A tick has succeeded when:** the draw was screened for §5.3 by hand as well as
by tool, every family merged cleanly, the census came in under 5%, every fault
was repaired by a third hand, every repair was re-read blind, and the shard is
committed and pushed.

**The project has succeeded when** the annotated corpus answers "how does this
word land, in this sense, against its neighbours" for the words people actually
look up — and the measured error rate on that claim is published rather than
assumed. The rate mattering more than the size is the whole thesis: 44% wrong at
any scale is worth nothing.

**What does *not* count as success:** a lower census number produced by a ruler
nobody checked (§11.74, §11.77 — two censuses lost exactly this way), or a clean
lint run, which is a smoke alarm rather than a referee.

---

## 6. Things that will bite you

- **The working tree always looks fully modified.** CRLF/LF mismatch from the
  OneDrive + Windows checkout. `git diff --ignore-all-space --ignore-cr-at-eol`
  shows the real changes. Never `git add -A` — stage explicit paths.
- **A rate is only comparable if the ruler did not move.** Two censuses were lost
  to this. Check the `reading` and `authoring` fields in each results file before
  comparing rates.
- **Withheld families come back.** `worklist_build.py` reads
  `data/families/held-*.json` and marks those ineligible; ids are normalised
  because worksheets rename `oewn-…` to `family-…` on the way out.
- **Keep the packets.** Census 002 is reproducible only from its own results file
  because its reader inputs were never written down. Two later bugs — a mistyped
  synset id and a false "missing packet" alarm — were only visible because the
  packets were on disk.
- **A repair is a new note.** It carries the same risk as the note it replaces.
  One repair in census 006 needed three attempts and failed a *different* fault
  class each time. Always re-read repairs blind, and warn the reader that repeated
  failure is not itself evidence of a fault.
- **`tone_lint.py` is a smoke alarm, not a referee.** So is `sensitive_screen.py`.
  Both produce false positives and both have missed real faults. `superlative-collision`
  currently reports a pair in which only one half is the fault (11.81) — read
  what a flag points at, never just the pair it names.
- **A reconciled id is a guess, and the results file says so.** `census2_aggregate.py`
  now pairs a stray verdict with an unread sense of the same word and counts it
  as read, because census 005 lost a correct reading to a mistyped synset id.
  Every remap is printed and listed under `reconciled_ids` — check them rather
  than skimming past. `--strict-ids` turns it off.
- **The census rate is a floor.** It measures what a blind reader catches. Faults
  that live between senses in a family are close to invisible to a reader shown
  one card at a time — tick 7's linter found six such faults in a shard the
  census had just scored at 2.5%.
- **Examples belong to the synset, not the word.** The importer now keeps only
  the examples that use a member's own lemma. If you touch `wordnet_import.py`,
  do not undo that, and do not weaken `example_mentions` back to a substring
  test — "pellucid prose" passed as an example of *lucid* for the whole project
  until census 010 read it (11.80).

---

## 7. Skills, agents and tooling

### Recover first, read second

```bash
python tools/status.py            # ~10s, includes validation
python tools/status.py --quick    # skip validation
```

Measures live: branch and unpushed commits, the CRLF-insensitive working tree,
**whether either instrument has drifted from HEAD**, corpus counts, validation,
the queue per part of speech with a tick estimate, the last six censuses, lint
totals. **If it disagrees with this file, it is right and this file is stale** —
that has already happened twice.

`/orient` (`.claude/skills/orient/SKILL.md`) wraps that plus the rules that do
not bend. Deliberately short: it loads when context is already tight.

### The two instruments — these ARE the measurement

| file | role |
| --- | --- |
| `.claude/agents/family-author.md` | authoring rubric, model, effort, tool allowlist |
| `.claude/agents/census-reader.md` | reading rubric, same |

Used **verbatim**, never retyped into a prompt. To change one: change the file,
say so in the plan, and treat the next tick as a new baseline. `status.py` warns
when they drift. Two censuses were lost to ignoring this (§11.74, §11.77).

### Pipeline, in tick order

| tool | does |
| --- | --- |
| `worklist_build.py --pos a/v/n` | ranks and gates the queue |
| `sensitive_screen.py` | §5.3 smoke alarm — **then read the draw yourself** |
| `family_worksheet.py` | builds the annotation skeleton |
| `family_merge.py` | collects author JSON, strictly |
| `tone_lint.py` | per-note and per-family checks |
| `family_apply.py` → `dict_pipeline.py --no-build` | applies and rebuilds |
| `census_packets.py` | blind reader packets — **keep them** |
| `census2_aggregate.py` | verdicts → results; reconciles mistyped ids |
| `dict_validate.py` | schema and cross-reference validation |

### Division of labour that must not collapse

Opus authors → **Fable reads blind** → a *third* agent repairs. The hand that
writes is never the hand that reads, and whoever finds a fault does not write its
repair. `tone_lint.py` and `sensitive_screen.py` are smoke alarms in that
system, never referees: a census names a fault class, and only then can a linter
stop it recurring.

### Before adding another instrument — read this first

The last four census rates are flat near 2%, well under the 5% gate. **The
quality loop has converged**, and each new checker generates its own follow-up
work — tick 6, tick 7 and 11.81 each shipped a checker and each found new work
while doing it. Prefer spending a tick on coverage unless a census actually
breaches 5%.
