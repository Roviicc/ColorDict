# The reading side — brief for Shawn's session

From 2026-09-04 the build runs on two hands. This file is the reading hand's.
The step table with owners and closing commits is
`data/policy/build-stages.json`; `status.py` prints it. **Read state from
there, not from here.**

## Why the split

The project already runs on "the hand that writes is never the hand that
reads". Until now that meant different *models* inside one session. It now
means different *sessions*, which closes the loophole where one context has
seen both sides. The manager session authors and never reads its own work.

| | manager | you |
| --- | --- | --- |
| rank, enrich | ✅ | |
| third-hand repairs | ✅ | |
| git, tooling, packets | ✅ | |
| **blind reads** | | ✅ |
| **null audits** | | ✅ |

## What you need to be able to do

Your session must resolve the instruments by name. Probe before spending:

    spawn `null-auditor` with "Reply READY and nothing else."

- `READY` — good.
- `Agent type not found` — your session cannot do this job. Stop; see
  `docs/AGENTS-PORTABILITY.md`. Do not clone the repo and retry, and do not
  substitute a general-purpose agent on your own initiative.

Two known facts about the readers, both measured 2026-09-04:

- **`null-auditor` spawns by name and runs clean.**
- **`entry-reader` by name does not.** It dies instantly on Fable's
  `[reasoning_extraction]` safeguard. Both are `fable`/`xhigh`, so the
  safeguard belongs to that rubric, not to the model. The sanctioned fallback
  is a `general-purpose` agent on model `fable` with this prompt shape:

      Read .claude/agents/entry-reader.md first, in full, and follow it
      verbatim. Nothing in this message adds to it or modifies it.

      Your packet: <path>
      Write your output to: <path>

      Read only those two files. Write only the output file.

  Record `reader_agent` in the results either way. By-name costs ~20k a
  packet; the fallback ~72k.

## The jobs, in order

**Step 5 — blind re-read of the third-hand repairs.** The manager repairs the
pilot's faults; you read the repaired entries without being told what changed.

**Step 7 — the ~10% blind read of the 778.** Sampled, as the plan sets it.

**Every null audit at 100%, never sampled.** This is a condition, not a
preference. The pilot cleared the 878 without a rubric change on the argument
that the null audit is the working net for evaluative abstract nouns — that
argument only holds while the net covers the whole surface. The blind read is
sampled; the audit must not be.

## Rules that do not bend

- **Never edit anything in `.claude/agents/`.** Two censuses have been lost to
  it. `Edit`/`Write` there are denied in `.claude/settings.json`, and
  `tools/instrument_gate.py` catches a change made any other way — including a
  `python -c` write. The validator calls the gate and refuses to grade a run
  whose instrument moved.
- **Never paraphrase a rubric into a prompt.** Pass the file path; the reader
  reads it from disk.
- **A malformed verdict file is re-run, never hand-fixed.** Two reader packets
  emitted broken JSON in the pilot and were re-run. A hand-fixed verdict is
  not a blind read.
- **You do not commit or push.** Hand results back to the manager. One writer
  on the tree; three writers already produced a stuck `index.lock` once.
- **Report the rate, not a repaired number.** If a read comes in above the
  10% halt line, say so and stop — that is a rubric problem and it is Shawn's
  call, not a re-run.

## What the pilot measured, for comparison

| pass | n | result |
| --- | --- | --- |
| blind read | 255 senses | 3.14% sense defect, `rank_wrong` 0 |
| null audit | 244 nulls | 2.05% false-null |
| stage 4 baseline | 50 entries | 3.8% |

The two readers agreed on only 4 of the 7 senses one or the other called a
false null. The class is real; its boundary is not agreed. If your reads keep
splitting on the same class, that is the signal that the rubric — not the
run — needs the argument.
