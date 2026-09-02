# ColorDict — orientation for whoever picks this up next

`DICTIONARY-PLAN.md` is the full record: every decision, every measurement, every
mistake, in the order they happened. It is long on purpose. **This file is the
entry point** — enough to understand what is being built, whether it is working,
and what to do next, without reading the whole history first.

Last updated 2026-09-02: tick 7 / census 010, the two follow-ups in 11.81,
and the shift to demand-driven selection in 11.83. Section 5 is a different
plan from the one that was here this morning - read it rather than assuming.

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
| 011 | 99 | 3 | **3.0%** | **first census outside adjectives** - verbs and nouns, book-selected |

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

**235 families / 2,885 annotated senses / 3,058 reviewed entries / 0 validation
errors.** Run `python tools/status.py` for the live numbers; the ones written
here go stale.

The shipped dictionary holds roughly **114,000 entries**, of which about 2,900
carry a connotation row. That gap used to read as a backlog. It is not one any
more - it is the product's input. See section 5.

| line | pool | done | queue | state |
| --- | --- | --- | --- | --- |
| **Adjective** | 5,911 candidate families | 227 families, 2,786 senses | 147 families / 2,434 members | **fallback filler** - drawn only when there is no demand to serve |
| **Adverb** | 5,571 senses, 2,505 pertainym links | 480 senses | n/a | **self-feeding** - inherited free from adjectives, keep it |
| **Verb** | 2,494 candidate families | 4 families, 162 senses | 68 book-ranked families / 903 members | **open** - first tick read at 2.2% |
| **Noun** | 11,484 families / 129,506 members | 4 families, 53 senses | 179 book-ranked families / 2,516 members | **open** - first tick read at 3.8%, Stage E closed by the book, not by a filter |

The adverb line stays the quiet win: **480 senses nobody authored**, inherited
through WordNet's pertainym links and growing with every adjective tick. Nine
adverbs sit on a deny list where their own gloss does not match the adjective
sense they point at.

---

## 5. The plan - ship, then let demand pick the words

**The decision this run kept deferring is made.** The old plan ended with Stages
C and D drained, roughly 6,000 senses, an empty queue and nothing left to draw -
a finish line nobody chose, and one that section 11.75 already proved could not
be moved by loosening the gate. The answer is not a better gate. It is to stop
guessing which words carry connotation and let the people using the app say so.

**The gate is a proxy; a reported miss is ground truth.** The queue is built
from size >= 8, charged >= 70%, ranked by wordfreq Zipf. Section 11.75 measured
what that proxy buys: two independent raters judged five of eight eligible
families to be taxonomy rather than connotation. A person who looked a word up
and found no connotation row is not a proxy for demand. They are the demand.

**What this changes, and what it does not.** It changes *selection* only. The
tick loop, both instruments, the blind read, the third-hand repair and the 5%
gate are untouched and not up for renegotiation - a reported word earns a
worksheet, an author, a census and a repair exactly like a drawn one. The
temptation with a demand queue is to hand-annotate the reported word quickly
because somebody is waiting. That is how a corpus gets back to 44% wrong.

**The honest caveat, stated first.** A demand queue only beats a Zipf queue once
there is demand, and today there is none - no release, no users, no report path.
So the MVP's deliverable is **not more senses. It is users.** Every stage below
is scoped to that.

**Decisions taken (2026-09-02).** Reports travel as a **local log the user
exports and sends** - nothing phones home, which keeps the app's offline promise
intact and needs no backend. The MVP ships as a **GitHub Releases APK plus the
web build**, both already wired.

### Stage 0 - standing, every tick

Push at the end of each tick rather than letting commits pile up. Carry any
outstanding repair into the next tick's repair round.

**Currently outstanding:** none. The *hard* repair in `family-01072500-a`
(annotated-015) is closed - the note claimed to be "the least damning word in
the family" while *day-old* sat beside it at charge 0. Repaired by a hand that
neither wrote it nor found it, re-read blind and passed, provenance in
`data/policy/repair-hard-annotated-015.json`.

### Stage M1 - make the empty state the report button

The cheapest piece of the whole plan, because the affordance and the empty state
are the same UI element.

`dict_build.py:sense_html` emits the `Connotations:` row **only** when a label,
usage note, explanation or tone exists, so an unannotated sense currently renders
as a definition with the row silently absent - indistinguishable from a sense
nobody has looked at. Emit the row always. When there is no tone, render
`Connotation not recorded - report this word` as a link to a
`colordict:report?...` URL carrying the sense id, lemma and gloss.

Per section 11.7, `sametypesequence=h` means a new article row is a builder
change plus a CSS class **with no app code at all**, so this lands on Android,
desktop and web at once from `tools/dict_build.py`.

> **Stop:** if intercepting a custom scheme in `DefinitionWebView` turns out to
> need more than a URL handler and an append, fall back to a long-press action
> on the headword. Do not let the report affordance grow into a feature.

### Stage M2 - the local log, and a way to send it

The link handler appends one JSON line to a local log: sense id, lemma, gloss,
timestamp, and a reason code distinguishing **`unannotated`** (the entry exists,
this sense has no connotation row) from **`not-found`** (no entry at all, logged
from the search screen). Those are different problems and must not arrive in the
same bucket.

A Settings row - `Reported words (N)` - opens the list, lets the user delete
anything they would rather not send, and exports the file through the standard
share sheet. The web build does the same through `localStorage` and a download.

> **Stop:** the log is append-only, local, and never sent without an explicit
> action. If a change would make it leave the device on its own, it does not
> ship - that is the promise the README makes.

### Stage M3 - `reports_ingest.py`

A received export merges into a demand worklist: dedupe by sense id, count
reports per sense, and rank by count rather than Zipf. Two things it must do
that the Zipf worklist never had to:

- **Honour a by-design-neutral deny list.** *table*, *hydrogen*, *plant* will be
  reported, and they are correctly unannotated. An annotation pass that finds a
  sense genuinely carries no charge writes it to `data/families/neutral-*.json`,
  and ingest marks those ineligible forever - the same mechanism
  `worklist_build.py` already uses for `held-*.json`.
- **Group reports into families before queueing.** The tick loop's unit is a
  family, not a word. A reported sense pulls in its family from
  `data/build/adjective-families.json`, so one report buys a whole spectrum.

> **Stop:** if fewer than ~20 distinct senses have been reported, do not run a
> demand tick - the queue is noise at that size. Draw from Stage F instead.

### Stage M4 - cut the release

`release.yml` builds the APK and `vercel.json` deploys the web build. Nothing
here is blocked on curation - B0 was designed that way. Cut the release with the
2,786 senses that exist, then put it in front of enough people that the log has
signal.

> **Stop:** a release with no report path is a wasted release. M1 and M2 ship
> with it or it waits.

### Stage M5 - the first demand tick

Run the section 3 loop unchanged against the demand worklist. This is the first
tick whose draw nobody at this end chose, so treat its rate as the real test of
whether the method survives contact with words it did not select.

> **Stop:** the 5% gate, exactly as before. A demand tick over 5% is a method
> problem, not a queue problem - the words are not harder, and if the rate says
> otherwise that finding is worth more than the shard.

### Stage F - fallback filler

147 adjective families / 2,434 members, ranked and gated, ready to draw. Run a
tick from here whenever there is no demand to serve, and prefer the largest
families - they buy the most spectrum per tick.

This is what the old Stage C was, demoted. It is no longer the default use of a
tick, and **emptying it is not a goal.**

### Cancelled

- **Verb screening (old Stage B).** It existed to find out whether the gate's 70
  eligible verb families carry connotation. A reported verb answers that for
  itself, one word at a time, for free.
- **The noun filter (old Stage E).** Nouns were closed because the filter cannot
  tell a bad thing (*pneumonia*) from a loaded word. Demand-driven selection has
  a human doing the selecting, so the filter is not on the critical path.

Neither is *wrong*; both are now work that buys nothing the report loop does not
buy more cheaply. The queues stay on disk.

---

## 5b. What success looks like

The targets changed with the goal. Quality is unchanged and non-negotiable;
coverage stopped being a number to grow and became a response time.

| | target | now |
| --- | --- | --- |
| Census error rate | **< 5%**, the hard gate | ~2% across 007-010; 3.0% on the first verb/noun tick |
| Validation errors | **0**, always | 0 (2,963 entries) |
| Instruments unchanged within a comparison window | required for a rate to mean anything | unchanged since 11.81 |
| Repairs re-read blind | every repair, no exceptions | held - 3/3 right on census 011 |
| **A report path exists** | shipped in the release | **not built** |
| **Release cut** | APK on GitHub Releases + web deployed | **not cut** |
| **Reports received** | >= 20 distinct senses before the first demand tick | 0 |
| **Report to shipped annotation** | one tick, and the reporter can see it | n/a |

**A tick has succeeded when:** the draw was screened for section 5.3 by hand as
well as by tool, every family merged cleanly, the census came in under 5%, every
fault was repaired by a third hand, every repair was re-read blind, and the shard
is committed and pushed.

**The MVP has succeeded when** somebody who is not Shawn looks up a word, finds
no connotation, reports it, and sees it annotated in the next update.

**The project has succeeded when** the corpus answers "how does this word land,
in this sense, against its neighbours" for the words people actually look up -
and the measured error rate on that claim is published rather than assumed. That
sentence has been the goal since 5b was first written. Until now nothing in the
plan connected "the words people actually look up" to how words were chosen.

**What does *not* count as success:** a lower census number produced by a ruler
nobody checked (11.74, 11.77 - two censuses lost exactly this way); a clean lint
run, which is a smoke alarm rather than a referee; or a bigger corpus nobody
asked for.

---

## 6. Things that will bite you

- **A dead session leaves git locks behind, and every later git command fails.**
  On 2 Sep a session was terminated one second after its final commit, during
  git's automatic post-commit maintenance, leaving `index.lock`, `HEAD.lock` and
  `objects/maintenance.lock` orphaned within three seconds of each other. The
  repo was perfectly consistent — only the janitorial step was skipped — but a
  plain `git checkout` then died with *"Another git process seems to be
  running"*. `status.py` now reports lock files with their age and whether a
  `git` process actually exists; when none does and the lock is over 15 minutes
  old it is orphaned, and `python tools/status.py --clear-stale-locks` removes
  it. **Never delete a lock while a git process is alive** — that is how an
  index gets corrupted, and the tool refuses to do it.
- **Two sessions, two clocks.** Commits from the sandbox session are stamped in
  UTC and this machine's are `+08:00`, so foreign commits can look six hours
  *older* while actually being newer. Anything sorting by commit date will order
  them wrongly; `git reflog` is the only reliable record of what landed when.
- **Line endings are now declared in `.gitattributes`, and that fixed real
  damage.** `core.autocrlf=true` is set at the *system* level on the Windows
  machine, while the sandbox session commits with it unset — so the stored form
  of a file depended on who committed it. Two things this had actually broken,
  both found by measuring rather than by the symptom:
  **(a)** `sample-glossary.dict` had a carriage return injected into it on
  checkout — 2,494 bytes on disk against 2,493 committed, with one stray `0x0D`.
  A StarDict payload with an injected CR has corrupt offsets and the failure is
  invisible in a diff. The committed copy was clean and the working copy was
  restored from it; the other 37 tracked binaries were checked and were fine.
  **(b)** `gradlew` and `run-desktop.sh` were CRLF on disk, which fails under
  bash with `bad interpreter: /bin/sh^M`.
  `.gitattributes` pins `text=auto` (index stays LF, checkout stays native),
  `eol=crlf` for `.bat`/`.cmd`, `eol=lf` for `gradlew` and `.sh`, and `binary`
  for every dictionary payload and image. It deliberately does **not** set a
  global `eol=lf`, which would rewrite the whole working tree.
  Still stage explicit paths — never `git add -A`.
- **The "whole tree looks modified" symptom is not currently true.** It is in
  the history because it was, but `git diff --stat` on a clean checkout now
  returns nothing. If it returns again, check `git ls-files --eol` before
  believing it — 469 of 508 tracked files are stored LF and correct.
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
