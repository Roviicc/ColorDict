# ColorDict

A connotation dictionary for learners of English. The plan is
docs/BUILD-PLAN.md; `python tools/status.py` measures where it stands.

## At the start of a session, and after every compaction

1. Run `/orient`, or at least `python tools/status.py --quick`, before you say
   anything about state. A summary is memory, not state: when it disagrees with
   status.py, status.py is right.
2. During an overnight run, read tonight's log, `docs/overnight/<YYYY-MM-DD>.md`
   (dated in UTC+8), before you spawn anything. An agent that is still running,
   or already wrote its file, must not be spawned again.

## Rules that do not bend

- The hand that writes never reads. One agent writes, a blind reader reads, and
  a third agent repairs.
- Instruments are the files in `.claude/agents/`, spawned by name and used
  verbatim. Never retype a rubric into a prompt.
- Agents write their output to disk and return one line. Do not paste their
  results or big files into the conversation; that is what fills the context.
- Never `git add -A`; stage explicit paths. Commit and push after every tick.
- A census over 5% stops the run.
- A decision the author makes goes into `data/policy/build-stages.json` the same
  hour. The next session cannot see this conversation.
