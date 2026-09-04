# Stage 7, pilot run — brief for a session opened on this repo

Written 2026-09-04 by a cloud session that could do the free half and not this
half. Everything below is prepared and on disk. **Read `docs/BUILD-PLAN.md` §
Stage 7 and `.claude/skills/orient/SKILL.md` first; this file does not replace
them.**

## Before you spend anything: probe this session

Spawn a `sense-ranker` agent with the prompt `Reply READY and nothing else.`

- **`READY`** — this session can do the job. Continue.
- **`Agent type 'sense-ranker' not found`** — it cannot, and nothing you do
  from inside it will change that. **Stop and tell Shawn.** Do not clone the
  repo and retry: a session's worker list is fixed from its project folder at
  the moment it opens, and a clone lands somewhere the registry never reads.
  That was tested on 2026-09-04, twice, once with the full repo cloned locally.
  Do not substitute a general-purpose agent for the authors either; see
  `docs/AGENTS-PORTABILITY.md`.

The probe costs about 4k tokens and takes a second. Run it first.

Attaching or connecting this folder to an already-running session does not
help — the folder has to be the one the session opened on. Native Claude Code
opened on the repo is the setup known to pass.

## Why this file exists

The cloud session cannot spawn `sense-ranker`, `enricher`, `entry-reader` or
`null-auditor`: those resolve only for a session opened on this folder. Running
them as general-purpose agents instead costs ~20,600 tokens per entry against
~12,000 by name, cannot set the `effort` the rubrics specify, and produces a
rate that is a new baseline rather than a comparison with stage 4. So the model
passes wait for you.

## State

Stages 0–6 closed 2026-09-03. Stage 7 is **in progress** — steps 1–3 of 13 are
done (select and packetise, the pilot's rank+enrich, the pilot's blind read).

**Do not read state from this paragraph.** The step table with its owners and
closing commits lives in `data/policy/build-stages.json` and `status.py` prints
it; that is the copy that gets updated. This file describes how to run the
pilot, not where the build has got to.

Book one is Pride and Prejudice, 80% coverage band, approved by Shawn
2026-09-04, blind read sampled at ~10%.

## What the prep found, and it matters

`enrich_packets.py select` skips words already in the **family** corpus but not
words a previous **enrichment** run wrote. All 50 stage-4 entries came back at
positions 0–49 of the 928 — the literal head of the list. Left alone that would
have paid for them twice and measured the pilot on the fifty entries the rubric
was tuned against.

`tools/stage7_subset.py` (new) excludes them and stratifies the pilot draw.

| path | what |
| --- | --- |
| `data/policy/stage7-book1/` | full 928 selection, 93 packets, as `select` cut it |
| `data/policy/stage7-book1-remaining/` | **878 entries, 88 packets** — the real generation set |
| `data/policy/enrich-003-pilot/` | **100 entries, 10 packets** — the pilot, seed 7 |

Pilot bands, 25 drawn from each quarter of the 878:
occurrences 135→35, 34→19, 19→12, 12→9. Stage 4 measured 3.8% on words
averaging ~135 occurrences; two thirds of this run is under 25, on thinner
sentence evidence. **The pilot exists to find out whether the rate holds there.**

## The run — pilot only, then stop

Adverbs defer to inheritance for this run; the Enricher does not write fresh
connotation for the 113 adverbs in the 928.

1. Give each of the 10 packets in `data/policy/enrich-003-pilot/ranker-packets/`
   to a **`sense-ranker` agent spawned by name**, one agent per packet, in
   parallel. Each writes `ranker-reads/verdicts-NN.json` beside it, NN matching
   its `input-NN.json`. Pass paths, not contents — never retype a rubric into a
   prompt, and never paraphrase or extend an instrument file for a run.

   ```
   python tools/enrich_validate.py ranker --out data/policy/enrich-003-pilot
   ```
   Rejects any order that is not a permutation of the offered ids, before the
   Enricher spends anything. Re-run a rejected packet; do not hand-fix a verdict.

2. ```
   python tools/enrich_packets.py enricher --out data/policy/enrich-003-pilot
   ```
   then the same by-name fan-out with **`enricher`** over `enricher-packets/`,
   writing `enricher-out/output-NN.json`.

   ```
   python tools/enrich_validate.py enricher --out data/policy/enrich-003-pilot
   ```

3. **STOP.** Shawn asked to be shown the pilot before any reading pass. Report:
   entries and senses written, the validator's reject count and reasons, wall
   clock per packet, and tokens actually spent. Do **not** run `entry-reader` or
   `null-auditor` until he says go.

## The rules that do not bend here

- **Spend cap 8M tokens** for the whole of stage 7, set before the run as the
  plan requires. The pilot should land near 0.6M. Stop at the cap and report.
- **Validation failure above 10% halts the run.** That is a rubric problem, not
  a batch problem: fix `.claude/agents/enricher.md`, say so in the plan, and the
  next run is a new baseline.
- **Do not edit the four instrument files to make a run pass.** `status.py`
  warns when they drift from HEAD and the warning is the point.
- **The hand that writes is never the hand that reads.** Opus ranks and enriches,
  Fable reads, a third agent repairs. Check the `reading` and `authoring` fields
  in any results file before comparing a rate to an older one.
- **Stage explicit paths. Never `git add -A`.** See HANDOFF §6 on CRLF and the
  binary payloads.

## Then hand back

The cloud session keeps the deterministic half: `dict_enrich_apply.py`,
`dict_pipeline.py --no-build`, `entry_validate.py`, `stardict_make.py`,
`verify_stardict.py`, and the stage-7 close-out in `data/policy/build-stages.json`.
Keeping the entry text out of that session's context is also what the blind read
needs, so the split is deliberate.
