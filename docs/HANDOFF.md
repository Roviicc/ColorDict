# ColorDict — orientation for whoever picks this up next

`DICTIONARY-PLAN.md` is the full record: every decision, every measurement, every
mistake, in the order they happened. It is long on purpose. **This file is the
entry point** — enough to understand what is being built, whether it is working,
and what to do next, without reading the whole history first.

Last updated after tick 7 / census 010, plus the two follow-ups in 11.81.

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
| **Adjective** | 6,826 families / 29,039 members | 227 families, 2,786 senses | **135 families / 2,116 members** | running, ~5 ticks left |
| **Adverb** | 5,571 senses, 2,505 pertainym links | 489 senses | n/a | **self-feeding** — inherited free from adjectives |
| **Verb** | 2,495 families / 31,811 members | 116 senses | **none built** | stalled |
| **Noun** | 11,484 families / 129,506 members | 0 | 1,318 candidates, filter known bad | **deliberately closed** |

The adverb line is the quiet win: **489 senses that nobody authored**, inherited
through WordNet's pertainym links, growing automatically with every adjective
tick. Nine adverbs sit on a deny list where their own gloss does not match the
adjective sense they point at.

---

## 5. Next steps, in order

**1. Run tick 8.** `staging` is pushed and current through 11.81, and the two
instruments are unchanged by that work, so **tick 8 is still comparable to
007–010** — it is not a new baseline. Push at the end of each tick rather than
letting commits pile up; that is routine, not a step.

**2. Finish the adjective line — about five more ticks.** Nothing blocks this.
Both instruments are verbatim, the gate is checked, the queue is ranked. Just run
the tick loop above and stop if a tick goes over 5%.

**3. Two things 11.81 opened while closing tick 7's follow-ups.**

- **A real fault is sitting unrepaired.** *hard* in `family-01072500-a`
  (annotated-015) calls itself "the least damning word in the family" while
  *day-old* sits beside it at charge **0**. Found by the pass that wrote the
  rule, so by §11.65's own discipline the repair has to come from a hand that
  neither wrote the note nor found the fault.
- **The intensity axis is two axes wearing one name.** `MILD_END` conflates
  *bareness* ("plainest", "flattest") with *low intensity* ("mildest",
  "gentlest"), which is why the collision it reported paired the wrong two
  notes. The fix is to split a `bare` end out **and** check extreme-intensity
  claims against sibling charges — both together, because splitting alone drops
  the *hard* fault rather than keeping it. It is a rule change, so it needs its
  own backtest before it is kept.

**4. Then verbs.** The verb line has 116 senses that rode along inside adjective
shards and **no queue at all** — `worklist_build.py --pos v` has never been run.
Verbs are not adjectives with different words: 12.8 members per family against
the adjective 4.3, so §11.62's size and charge gates are **not transferable** and
must be recalibrated against verb data. A screening pass comes before any
authoring.

**5. Nouns stay closed until the screening filter is fixed.** `pneumonia` and
`tranquilizer` score high because the **thing** is bad, not because the **word**
carries force. That is `world-not-word` operating at family-selection level — the
same fault class the notes kept failing on, one layer up. Annotating before it is
fixed would build shards out of words with no connotation to describe.

**Not a step:** rebuilding assets and cutting a release. The build runs green on
every pipeline pass and the app ships whatever the corpus holds. A release is a
timing decision, not a prerequisite.

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
