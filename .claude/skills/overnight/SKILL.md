---
name: overnight
description: Run ColorDict unattended while the author sleeps - stage 9b's plain-words pass, the carried faults, then queue ticks - with hard stops, a commit and push after every tick, and a morning report. Use ONLY when the author says go ("goodnight, go", "/overnight"); never start it on your own initiative.
---

# Overnight run

The author approved this shape on 2026-09-26. The numbers below are theirs and
change only when they say so:

- **Cap: 4 ticks a night.** A tick is one writing round (authors, repairer or
  simplifier) plus the blind read of what it wrote and the repairs that read
  finds.
- **Stop by 08:00 UTC+8** (the author's commits are +08:00). Do not start a tick
  after 06:30 UTC+8; one takes 60-90 minutes.
- **Nothing reaches `main`.** Commit and push to the session's own branch after
  every tick. No merge, no tag, no release, no APK. The author reviews in the
  morning.

`/orient` rules all apply. The two that matter most at night: the hand that
writes never reads, and instruments are spawned **by name** and used verbatim.

## 0. Before the first tick

1. `python tools/status.py` must show: nothing uncommitted, instruments
   unchanged vs HEAD, no locks, 0 validation errors.
2. **A cloud checkout is missing gitignored data.** If `data/source/`,
   `data/build/` or `data/entries/derived-bulk.jsonl` is absent, rebuild it -
   proven 2026-09-26 to reproduce the committed dictionary byte for byte:
   ```
   pip install wordfreq
   mkdir -p data/source && cd data/source
   curl -sSLO https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz
   curl -sSLO https://raw.githubusercontent.com/aesuli/SentiWordNet/master/data/SentiWordNet_3.0.0.txt
   curl -sSLO https://raw.githubusercontent.com/globalwordnet/cili/master/ili-map-pwn30.tab
   cd ../..
   python tools/wordnet_import.py --oewn data/source/english-wordnet-2025.xml.gz \
       --swn data/source/SentiWordNet_3.0.0.txt --ili-map data/source/ili-map-pwn30.tab \
       --out data/entries/derived-bulk.jsonl
   python tools/family_extract.py --oewn data/source/english-wordnet-2025.xml.gz \
       --out data/build/adjective-families.json
   python tools/family_extract.py --oewn data/source/english-wordnet-2025.xml.gz \
       --pos v --min-size 4 --out data/build/verb-families.json
   python tools/dict_pipeline.py --no-build     # 0 errors, and git status clean
   ```
   sha256 of the three sources as first fetched: OEWN `9ca6d1dc`, SentiWordNet
   `4fc9b253`, ili-map `13b67413`. www.gutenberg.org and cdn.jsdelivr.net
   answer from the cloud since 2026-09-26; both were refused before.
3. `python tools/instrument_gate.py` passes - eight instruments, unchanged.
4. Open the log: `docs/overnight/<YYYY-MM-DD>.md`, dated in UTC+8. Write the
   plan for the night into it before anything is spent.
5. Arm the watchdog: `send_later` in 90 minutes - "Overnight check-in: read
   docs/overnight/<date>.md; if no tick is in flight and no stop has fired,
   resume at the next step." Re-arm it at the end of every tick.

## 1. The order of work

Read `status.py`'s stage 9b block for what is done; start at the first open
step.

| tick | work | stage 9b step |
| --- | --- | --- |
| 1 | the six carried faults | 4 |
| 2 | the plain-words pass | 5-9 |
| 3-4 | adjective queue ticks | - |

When 9b is closed, every tick is a queue tick until stage 10 is approved.
Book one can be fetched now (HANDOFF §6 has the URL and the hash), but that
approves nothing: stage 10 still needs its own approval and a spend cap, and a
"go" for the night is neither.

### Tick 1 - the carried faults

- The 4 superlative collisions (`tone_lint.py --all` lists them) go to one
  `repairer` packet, in the shape of `data/policy/census-012-repairs-input.json`,
  with `fault: "other"`, the lint message as `reader_said`, and the whole family
  attached. repairer.md names this repair ("name one neighbour instead of
  ranking all of them") and may `keep`: superlative-collision flags a pair of
  which only one half may be wrong.
- `census_apply.py` the decisions, `family_apply.py` every shard,
  `dict_pipeline.py --no-build`, then a blind re-read of every changed note by
  a fresh `census-reader` that found none of them.
- **The 2 stale axes are re-authored from scratch** (the author's decision,
  2026-09-26). They cannot be patched in place: the repairer may not touch a
  family, census_apply has no axis action, and re-merging annotated-018 would
  undo the stage 9 repairs applied to it. So the two families move to a shard
  of their own:
  1. Copy `family-10008828-n` (lady, ma'am, madam, dame, gentlewoman) and
     `family-05911139-n` (scheme, play, system, policy) from
     `data/families/draft-018.json` into a new `draft-019r.json`, same shape.
  2. Remove both from `annotated-018.json` with a script, and prove the rest
     untouched: 87 families become 85, every other family byte-identical.
  3. `family_packets.py --draft data/families/draft-019r.json --out
     data/families/packets-019r`, one `family-author` each, `family_merge.py`
     into `annotated-019r.json`, then tone_lint and `plain_lint --max-long 0`.
  4. `family_apply.py` on 018 and 019r, `dict_pipeline.py --no-build`,
     `census_draw.py --shard data/families/annotated-019r.json`, and a blind
     read of every note in it. They are gendered forms of address, so read
     the draw for §5.3 first, as for any tick.

### Tick 2 - the plain-words pass

**Offer the author a pilot first** if they are awake: one simplifier packet
(~15 notes), shown to them as before/after. The author is the reader these
notes are for and asked to be the test reader; their "easy" or "still hard"
is the one check no agent can give. If they are asleep, run the full pass - it
is one commit, and it can be reverted.

```
python tools/plain_packets.py draw --out data/policy/plain-001
# one `simplifier` per packet, in parallel: read packet-NN.json, write decisions-NN.json
python tools/plain_packets.py check --dir data/policy/plain-001
python tools/census_apply.py --decisions data/policy/plain-001/decisions.json
for f in data/families/annotated-*.json; do b=$(basename $f .json); \
  python tools/family_apply.py --families $f \
  --out data/entries/overlays/families-${b#annotated-}.overlay.jsonl; done
python tools/dict_pipeline.py --no-build
python tools/plain_lint.py --all --quiet        # too-long must fall to the keeps
python tools/plain_packets.py census --dir data/policy/plain-001 \
    --sample census-013 --out data/policy/census-013.json
python tools/census_packets.py --census data/policy/census-013.json \
    --packets 18 --out data/policy/census-013-reads
# one `census-reader` per packet: read input-NN.json, write verdicts-NN.json
python tools/census2_aggregate.py --dir data/policy/census-013-reads \
    --census data/policy/census-013.json --reader-model <served model> \
    --out data/policy/census-013-results.json
```

If `check` reports that the simplifier kept over a third of its packet, stop
and write why into the log: that is a claim about the rule, and the author
decides it.

### Ticks 3-4 - queue ticks

HANDOFF §3, with the tools:

```
python tools/worklist_build.py --pos a --bulk data/entries/derived-bulk.jsonl --out data/worklist.tsv
# draw the top ~25 eligible, untouched families
python tools/family_worksheet.py --families data/build/adjective-families.json \
    --bulk data/entries/derived-bulk.jsonl --id <id> ... --out data/families/draft-NNN.json
python tools/sensitive_screen.py data/families/draft-NNN.json
```

**Then read the draw yourself** for §5.3 sensitive families. Hold any you are
unsure of in `data/families/held-*.json` - a held family costs one tick of
coverage, a wrong one ships.

```
python tools/family_packets.py --draft data/families/draft-NNN.json --out data/families/packets-NNN
# one `family-author` per family, in parallel:
#   packet data/families/packets-NNN/<id>.json -> output data/families/authored-NNN/<id>.json
python tools/family_merge.py --draft data/families/draft-NNN.json \
    --authored data/families/authored-NNN --out data/families/annotated-NNN.json
python tools/tone_lint.py data/families/annotated-NNN.json
python tools/plain_lint.py data/families/annotated-NNN.json --max-long 0
python tools/family_apply.py --families data/families/annotated-NNN.json \
    --out data/entries/overlays/families-NNN.overlay.jsonl
python tools/dict_pipeline.py --no-build
python tools/census_draw.py --shard data/families/annotated-NNN.json \
    --sample census-0NN --out data/policy/census-0NN.json
```

Then census_packets, one `census-reader` per packet, census2_aggregate, a
`repairer` on the faults, and a blind re-read of the repairs.

The first `worklist_build` in a cloud checkout rewrites the tracked worklists
against OEWN 2025. Expect a diff - shards committed since the last build now
cover families, and a few 2024 head synsets no longer exist. It is correct;
commit it with the tick.

## 2. Spawning agents

- `Agent` with `subagent_type` set to the instrument name (`family-author`,
  `simplifier`, `census-reader`, `repairer`), `run_in_background: true`, all of
  one round in a single message.
- The prompt names two paths and nothing else: "Your packet is <path>. Write
  your output to <path>. Reply with one line: the path you wrote, or what
  stopped you." Never paste or paraphrase a rubric into a prompt.
- Every agent writes its own file. Nothing comes back through the orchestrator
  but that one line.
- The Workflow tool runs 2 agents at a time on a 4-CPU container; use `Agent`.
- Record each round's agent count and `subagent_tokens` in the log. The first
  queue tick is the measurement the next night's cap is set from.

## 3. Hard stops

Stop the run - finish the step in hand safely, commit, push, report - when:

- any census stratum reads over 5%
- the instrument gate fails, or a tool fails twice on the same input
- `plain_lint --max-long 0` fails on a new shard after one re-run of the
  offending author
- the tick cap is reached, or it is past 06:30 UTC+8 with no tick in flight
- agents fail on usage or rate limits twice, ten minutes apart
- a git lock is held by a live process for over 15 minutes (a stale lock with
  no process: `status.py --clear-stale-locks`)

## 4. Commits

Stage explicit paths only - never `git add -A`. One commit per tick, message in
the repo's style: what was measured, what changed, what was left standing.
`git push -u origin <session branch>` after every commit, retrying on network
failure with 2s/4s/8s/16s backoff. Update build-stages.json's step notes with
measured numbers in the same commit.

## 5. The morning report

`docs/overnight/<date>.md`, committed last:

- one section per tick: work done, agents, tokens, census by stratum, repairs,
  lint before and after, commit hash
- why the run stopped
- **what needs the author**: keeps to review, families held under §5.3, anything
  the stop rules fired on, the next night's suggested cap

Then send the report with `SendUserFile` (status `proactive`) so it reaches the
author's phone.
