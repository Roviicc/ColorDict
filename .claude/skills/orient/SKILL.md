---
name: orient
description: Get back up to speed on the ColorDict connotation dictionary run. Use when resuming work, when context is running low or has just been compacted, when starting a fresh session on this repo, or when asked "where are we", "what's next", or "what's the state". Establishes measured state in one command, then points at the right doc.
---

# Orient

This repo's history is 108KB of plan. **Do not read it to find out where things
stand.** It records what was true when someone wrote it down, and its numbers go
stale within a day — the queue count and tick estimate in HANDOFF §4 both did.

## 1. Measure the state (always first)

```bash
python tools/status.py            # ~10s, includes validation
python tools/status.py --quick    # skip validation
```

That prints, all measured live: branch and unpushed commits, the real
(CRLF-insensitive) working tree, **whether either instrument has drifted**,
corpus counts, validation errors, the queue per part of speech with a tick
estimate, the last six censuses, and the tone-lint totals.

Read its output before saying anything about state. If it disagrees with a doc,
**the script is right and the doc is stale** — fix the doc.

## 2. Read the entry point, not the record

- [docs/HANDOFF.md](../../../docs/HANDOFF.md) — §4 where things stand, **§5 the
  ordered plan**, §6 what will bite you. ~12KB. This is the file to read.
- [docs/DICTIONARY-PLAN.md](../../../docs/DICTIONARY-PLAN.md) — the full record.
  Open it **only** to read a specific numbered section HANDOFF cites (e.g.
  §11.75, §11.80). Never front to back.

## 3. Then work

The tick loop is HANDOFF §3. One tick is ~25 families / ~290 senses:

1. `worklist_build.py` → draw the top families
2. `sensitive_screen.py` on the draw, **then read the draw yourself** for §5.3
   sensitive families — the screen missed the real family three ticks running
3. `family_worksheet.py` → annotation skeleton
4. one `family-author` agent per family, each **writing its own JSON to disk**
   (returning it through the orchestrator is the known bottleneck)
5. `family_merge.py` → `tone_lint.py` → `family_apply.py` → `dict_pipeline.py --no-build`
6. `census_packets.py` → blind read → `census2_aggregate.py`
7. repair, re-read the repairs blind, commit, push

**Stop condition: a tick over 5% stops the run. Two consecutive over 5% is a
method problem, not a batch problem.**

## Rules that are not negotiable

- **The hand that writes is never the hand that reads.** Opus authors, Fable
  reads blind, and a *third* agent repairs. If you found a fault, you do not
  write its repair.
- **Instruments are files, used verbatim.** `.claude/agents/census-reader.md`
  and `family-author.md`. Never retype a rubric into a prompt — that cost two
  censuses (§11.74, §11.77). To change one: change the file, say so in the plan,
  and treat the next tick as a new baseline. `status.py` warns when they drift.
- **Never `git add -A`.** CRLF from the OneDrive/Windows checkout makes the tree
  look fully modified. Stage explicit paths; diff with
  `--ignore-all-space --ignore-cr-at-eol`.
- **Keep the packets.** Census 002 is reproducible only from its own results
  file because its reader inputs were never written down.
- **A rate is only comparable if the ruler did not move.** Check `reader_model`
  and `reader_agent` in the results file before comparing any two censuses.

## What the numbers mean

- **The census rate is a floor, not the error rate.** It measures what a blind
  reader catches. Faults living *between* senses in a family are near-invisible
  to a reader shown one card at a time — a linter found six such faults in a
  shard census 010 had just scored at 2.5%.
- **`tone_lint.py` and `sensitive_screen.py` are smoke alarms, not referees.**
  Both produce false positives and both have missed real faults. A flag is not a
  verdict; absence of flags is not clearance.
- **Emptying the queue is not "finishing adjectives."** The gate admits ~5.7% of
  families by design, and §11.75 measured that lowering it makes selection
  worse. The queue is the ceiling of the current method, not of the pool.

## If you are about to add another instrument

Check the last four census rates first. They have been flat around 2% since
census 007, well under the 5% gate. **The quality loop has converged**, and each
new linter generates its own follow-up work. Prefer spending a tick on coverage
unless a census actually breaches 5%.
