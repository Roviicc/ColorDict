# Proposal: a verdict-only pass for the held words (stage 10, step 6)

Drafted 2026-10-01 for the author to approve, change or reject. Nothing here
has been spent on. The instrument is `verdict-writer.md` beside this file.

## What it is for

After r4, 13,606 senses were open on book one. The loop can draw about a
quarter of them (the ranked and never-opened pools). The rest are held by the
Enricher's cap, on purpose:

| held class | words | open senses |
| --- | --- | --- |
| function words (*so*, *say*, *now*) | 118 | 628 |
| more than 8 open senses (*make* 46, *give* 40) | 259 | 3,335 |
| under 3 usable sentences (now: attested in their own POS) | 2,372 + 289 | ~6,500 |

The Enricher writes a learner line, examples and labels for every sense it
judges, about 2,800 tokens a word. For these classes that is the concordance
the cap exists to prevent, and most of the answers will be null. The
verdict-writer answers only the connotation question, which is the one stage
10's done-check asks, and writes nothing a learner would read.

## Why a new instrument and not a flag on the Enricher

The rule: instruments are files, used verbatim, and a changed file is a new
baseline. Adding a "verdict only" mode to `enricher.md` would move the ruler
on the Enricher's own rate (734 senses at 0.8%). A separate file leaves that
rate comparable and gets its own.

## What changes in the tools (about half a day, no spend)

1. `book_verdicts.py`: a verdict counts when a hand made it. Today that is a
   tone note, an explanation or a learner line. Add: an overlay sense that
   carries `"verdict": {"connotation": null | candidate, "by": "verdict-writer",
   "run": "<run>"}`. The schema (`dict_schema.json`, `dict_validate.py`,
   `dict_enrich_apply.py`) declares the field; the app does not show it.
2. `stage10_draw.py held`: draws from the three held classes, stratified, and
   cuts verdict packets: word, POS, open senses with gloss and two WordNet
   examples, the attested sentences (0-6).
3. `enrich_validate.py verdicts`: accepts an entry only if it answers every
   open sense and nothing else, each null with a `why`, each candidate with a
   `why`; writes results.json and a parked overlay in the stage-10 shape.
4. `enrich_packets.py nulls` already cuts null packets from results.json; the
   null-auditor reads them unchanged. Candidates go to the same
   `candidates.json` the join reads.

## The pilot

- **30 words, 10 from each held class**, drawn by book frequency within the
  class, every open sense written: roughly 300 senses (the *make*/*give*
  class dominates; cap a word at its open senses, no sub-sampling).
- Three verdict-writers, one packet each.
- **Every null read blind by the null-auditor** (the existing instrument,
  unchanged, on Fable). There is no entry read, because there is no entry.
- **Gate: false-null rate under 5%**, from 50 read senses up; the same rule
  as the run. Below it the pass goes ahead at the loop's cap; over it the
  instrument is changed, not the batch.
- Two things to look at, not gates: the candidate rate against the Enricher's
  (45 of 734 = 6% in r1-r4; a verdict-only hand with nothing else to write
  may raise more, or fewer), and the nulls on senses with no book sentence.

## What is not decided here

- Whether a verdict with no learner line should ship at all, or only count.
  The proposal counts it and ships nothing visible (the app has no field for
  it), so the entry the learner sees does not change.
- Whether the under-3-sentences class should be judged from the gloss alone
  or wait for book two to supply sentences. The pilot's third stratum is the
  measurement.
