# The four instrument files, and who may run them

If you are a model that has just arrived in this repo, read this before you
touch `.claude/agents/`.

## What they are

`.claude/agents/` holds six worker definitions. Four of them are the instrument
for stage 7:

| file | model | effort | job |
|---|---|---|---|
| `sense-ranker.md` | opus | high | orders a word's senses by what a reader actually meets |
| `enricher.md` | opus | high | writes the learner line, examples, labels; never charge or tone |
| `entry-reader.md` | fable | xhigh | reads finished entries blind and returns a verdict |
| `null-auditor.md` | fable | xhigh | re-checks the senses the enricher called connotation-free |

They are the measuring stick. A defect rate only means something if the
instrument that produced it did not move. **Do not edit them to make a run
pass.** That mistake has already cost this project two censuses.

## How to run them

Open a session whose project folder is this repo:

```
C:\Users\villa\OneDrive\Documents\GitHub\ColorDict
```

Then spawn them **by name** — `sense-ranker`, `enricher` — one agent per
packet, in parallel. Pass packet file paths. The model, the effort and the tool
allowlist load from the frontmatter automatically. This is the only proven path
and it is free.

## What does not work, and why

Copying these files somewhere else does not make them runnable somewhere else.

A session's worker list is fixed when the session starts, and it is built from
that session's own project folder. A session opened anywhere else returns
`Agent type 'sense-ranker' not found`. Copying the markdown changes nothing
about that.

The obvious substitute — a general-purpose agent with the model forced and the
rubric read verbatim from file — was tested against a known answer on
2026-09-04 and **rejected**. See
[`data/policy/enrich-003-control/control-summary.json`](../data/policy/enrich-003-control/control-summary.json).

It failed on two counts:

- **Quality.** 45 of 50 first senses matched, but three of the five
  disagreements were the exact failure class the rubric exists to prevent:
  `time/noun` put "the continuum of experience" first, the one sense census 002
  records as used by no sentence; `make/verb` and `take/verb` both chose the
  broad light-verb reading over the plain one. These land on the
  high-sense-count words, where the missing `effort: high` would be expected to
  bite.
- **Cost.** 8,094 tokens per entry for the ranking pass alone, against 5,500
  for ranking *and* enriching together when spawned by name. Extrapolated to
  878 entries that is roughly 16M tokens against a stage-7 cap of 8M.

Note what the control controlled for: the rubric **was** read verbatim from
file, not paraphrased. The deviation that remained was `effort: UNSET`. Effort
is frontmatter, not prose — it cannot travel in a copy of the text. That is why
a portable duplicate of these files does not exist and cannot be made to work.

## One real exception: the fable readers

The ban above is on substituting for the **authors** — `sense-ranker` and
`enricher`, both opus. That is what the control measured and rejected.

The **readers** are the other way round. On 2026-09-03 every by-name
`entry-reader` spawn died instantly with "Fable 5.1's safeguards flagged this
message ... [reasoning_extraction]" — the rubric, used as a system prompt,
trips a classifier. The same rubric read from disk by a `general-purpose` agent
on model fable ran clean, twice.

So for `entry-reader` and `null-auditor`: try by name first, and fall back to
general-purpose + read-the-file if it trips. Record
`reader_agent: general-purpose+<file>` in the results so the run stays
comparable. Neither reader runs in the stage 7 pilot.

## If you cannot spawn the authors by name

Stop and say so. Do not substitute a generic worker for `sense-ranker` or
`enricher`; that path is measured and rejected above. The two valid options
are:

1. **Someone opens a session on this folder** — free, exact fidelity.
2. **An API key** — sets model *and* effort exactly, works from anywhere, and
   is the cheapest per entry. Bills to an API account, separate from a Claude
   subscription. There is no key in this repo as of 2026-09-04.

## The pilot

Stage 7's pilot is specified in [`STAGE7-PILOT-BRIEF.md`](STAGE7-PILOT-BRIEF.md).
