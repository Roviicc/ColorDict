# Step 6 — the prompt for the authoring session

Hand the block below to a session opened on this repo. It is stage 7 step 6:
the 778-entry generation run, the longest job in the build.

Shared rules are in `docs/READER-BRIEF.md`; state is in
`data/policy/build-stages.json`. This file is the launch prompt, nothing more.

---

```
Open on folder: C:\Users\villa\OneDrive\Documents\GitHub\ColorDict
Branch: staging. Pull first.

You are the AUTHORING hand for stage 7 step 6: the 778-entry generation
run. This is a long session. Read docs/READER-BRIEF.md for the shared
rules and data/policy/build-stages.json for state. Do not take state
from this prompt.

--- BEFORE YOU SPEND ANYTHING ---

1. PROBE. Spawn sense-ranker with "Reply READY and nothing else."
   READY                -> continue.
   Agent type not found -> STOP, tell Shawn, spend nothing. Do not clone
                           the repo and retry (tested 3x, it does not
                           work). Do not substitute a general-purpose
                           agent - measured against a known answer on
                           2026-09-04 and rejected on quality AND cost.
                           See data/policy/enrich-003-control/.

2. GATE. Run: python tools/instrument_gate.py
   It must print "pass". If it fails, STOP - the measuring stick moved
   and nothing you generate will be comparable.

3. ONE PACKET FIRST. Rank packet 01 only, then stop and report the
   output to Shawn before going further. ~25k tokens, ten minutes. This
   session has never run these agents; four million tokens is not the
   place to find that out.

--- THE RUN ---

Set: data/policy/stage7-book1-run/  - 778 entries, 78 packets.
DO NOT use stage7-book1-remaining/ - that still holds all 878 including
the pilot's 100, and you would pay for them twice.

4. sense-ranker BY NAME, one agent per packet, in parallel batches.
   Pass file paths only. Never retype or paraphrase a rubric into a
   prompt. Verdicts -> ranker-reads/verdicts-NN.json

   python tools/enrich_validate.py ranker --out data/policy/stage7-book1-run

5. python tools/enrich_packets.py enricher --out data/policy/stage7-book1-run
   then enricher BY NAME over enricher-packets/ -> enricher-out/output-NN.json

   python tools/enrich_validate.py enricher \
       --out data/policy/stage7-book1-run \
       --overlay data/entries/overlays/stage7-book1-run.overlay.jsonl

   The --overlay path is required. Without it the default overwrites
   enrich-001's overlay.

6. STOP THERE. Do NOT run entry-reader or null-auditor. You authored
   this; you cannot read it. The manager session reads the 778.

--- RULES THAT DO NOT BEND ---

- Never edit anything in .claude/agents/. Two censuses lost to that.
  Edit/Write are denied and instrument_gate.py catches any other route.
- A rejected packet is RE-RUN, never hand-fixed.
- Validation failure above 10% HALTS the run. That is a rubric problem,
  not a batch problem. Report it and stop; it is Shawn's call.
- Spend cap: 8M tokens for all of stage 7. ~1.6M is already spent on the
  pilot. This run should land near 4M. Stop at the cap and report.
- Adverbs defer to inheritance this run.
- You do NOT commit or push. The manager session owns git. Leave the
  working tree dirty and hand it back.

--- WHEN YOU FINISH, REPORT TO SHAWN ---

  entries and senses written
  validator rejects, with reasons
  tokens actually spent, and per entry
  wall clock
  anything that made you want to change an instrument file (do not -
  just say what and why)

Benchmarks from the pilot's 100, same instruments, for comparison:
  257 senses / 100 entries    2.6 senses per entry
  4,711 tokens per entry      rank + enrich
  2 validator rejects         2%
```

---

## Why the prompt is shaped this way

**The one-packet checkpoint.** The pilot proved this path in the manager's
session, not in his. If his session resolves the agents differently, or at a
different effort, one packet costs ten minutes to discover it and 78 packets
costs four million tokens.

**No git.** He finishes with a large dirty tree and that is correct. The
manager commits it. Two writers on this tree already produced a stuck
`index.lock`.

**It stops at the enricher validator.** He cannot read his own work, so step 7
returns to the manager. That is the rule the whole two-session split exists to
enforce.

**The baseline this run is measured against** is the pilot's, with
`enricher.md` unchanged: Shawn cleared the 878 on 2026-09-04 without a rubric
edit, on the argument that the null audit is the working net for evaluative
abstract nouns. That clearance carries one condition - the null audit runs at
100% coverage, never sampled.
