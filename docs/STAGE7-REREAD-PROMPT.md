# Step 5 — the prompt for the re-reading session

Hand the block below to Shawn's session. It is stage 7 step 5's second half:
the blind re-read of the pilot's third-hand repairs. Small — one packet, six
entries, seventeen senses.

Shared rules are in `docs/READER-BRIEF.md`; state is in
`data/policy/build-stages.json`. This file is the launch prompt, nothing more.

---

```
Open on folder: C:\Users\villa\OneDrive\Documents\GitHub\ColorDict
Branch: staging. Pull first — the packet landed in b2627fe.

You are the READING hand for stage 7 step 5. Read docs/READER-BRIEF.md
for the shared rules. Do not take state from this prompt.

1. PROBE. Spawn entry-reader with "Reply READY and nothing else."
   READY                -> use it by name.
   Agent type not found -> expected; it dies on Fable's
                           [reasoning_extraction] safeguard. Use the
                           sanctioned fallback in READER-BRIEF: a
                           general-purpose agent on model fable, told to
                           read .claude/agents/entry-reader.md in full
                           and follow it verbatim. Record which one you
                           used as reader_agent.

2. GATE. python tools/instrument_gate.py must print "pass".

3. READ.
   packet: data/policy/enrich-003-pilot/repair-read-packets/input-01.json
   output: data/policy/enrich-003-pilot/repair-reads/verdicts-01.json

   Six whole entries, every sense shown, nothing marked. Some of these
   senses were rewritten and some were never touched; you are not told
   which, and that is the point. Read them all as ordinary entries.

4. REPORT the rate to Shawn: senses read, senses wrong, the fault on
   each, and reader_model / reader_agent. Do not repair anything and do
   not commit — the manager owns git and owns the repairs.
```

---

## What this read is measuring

The pilot's first blind read scored 3.14% on 255 senses, and the null audit
2.05% on 244 nulls. Nine senses across six entries were repaired by a third
hand — seven false nulls promoted to candidates, one false candidate dropped,
one example moved off a neighbouring sense.

The two readers agreed on only four of the seven senses one or the other
called a false null, so the class is real and its boundary is not. A re-read
that splits on the same class again is the signal that `enricher.md` §4 needs
the argument, not that this batch needs another pass.
