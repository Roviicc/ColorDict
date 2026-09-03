#!/usr/bin/env python3
"""One-shot state of the run, for a session that has lost its context.

The plan is 108KB and the handoff is 12KB. Neither answers "what is true right
now" - they answer "what was true when someone last wrote them down", and this
project has already been bitten by exactly that: the queue count and the tick
estimate in HANDOFF section 4 were both stale within a day of being written.

So this reads the files instead of the prose. Everything printed here is
measured at the moment it runs.

Usage:
    python3 tools/status.py            # full, includes validation (~10s)
    python3 tools/status.py --quick    # skip validation
"""

import argparse
import csv
import glob
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SENSES_PER_TICK = 285  # observed across ticks 2-7: 281, 292, 286, 287, 280


def sh(*args):
    """Run a git command and return stdout, or '' when it fails."""
    try:
        out = subprocess.run(args, cwd=ROOT, capture_output=True,
                             text=True, timeout=60)
        return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def rule(title):
    print("\n" + "=" * 66)
    print(title)
    print("=" * 66)


LOCKS = (".git/index.lock", ".git/HEAD.lock", ".git/config.lock",
         ".git/objects/maintenance.lock", ".git/packed-refs.lock")
STALE_AFTER = 15 * 60  # a real git operation does not hold a lock this long


def git_running():
    """True / False / None when we cannot tell."""
    try:
        out = subprocess.run(["tasklist"], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    if out.returncode != 0:
        return None
    return any(line.lower().startswith("git") for line in out.stdout.splitlines())


def find_locks():
    """Every stale-able lock in the repo, with its age in seconds."""
    found = []
    for rel in LOCKS:
        f = ROOT / rel
        if f.exists():
            found.append((rel, time.time() - f.stat().st_mtime))
    for f in (ROOT / ".git/refs").rglob("*.lock"):
        found.append((str(f.relative_to(ROOT)).replace("\\", "/"),
                      time.time() - f.stat().st_mtime))
    return sorted(found)


def lock_state(clear):
    """Orphaned lock files, which is how this repo fails.

    A session was terminated one second after its final commit, during git's
    automatic post-commit maintenance, and left index.lock, HEAD.lock and
    objects/maintenance.lock behind within three seconds of each other. The
    repo was entirely consistent - only the janitorial step was skipped - but
    every later git operation that touches the index dies with "Another git
    process seems to be running", including a plain checkout.

    A lock with no git process behind it is by definition orphaned. Reporting
    is the default; clearing is opt-in, because deleting a lock out from under
    a LIVE git process is how an index actually gets corrupted.
    """
    locks = find_locks()
    if not locks:
        print("locks             : none")
        return
    running = git_running()
    label = {True: "YES", False: "no", None: "unknown"}[running]
    print("locks             : %d present, git process running: %s" % (len(locks), label))
    for rel, age in locks:
        mins = age / 60.0
        state = "stale" if (age > STALE_AFTER and running is False) else "recent/live"
        print("                    %-34s %6.1f min  %s" % (rel, mins, state))
    stale = [rel for rel, age in locks if age > STALE_AFTER]
    if not stale:
        print("                    a lock this fresh may be a live operation - wait, do not delete")
        return
    if running:
        print("                    ** git IS running - do NOT delete these, wait for it **")
        return
    if clear:
        for rel in stale:
            try:
                (ROOT / rel).unlink()
                print("                    cleared %s" % rel)
            except OSError as exc:
                print("                    could not clear %s: %s" % (rel, exc))
    else:
        print("                    ** ORPHANED - no git process. Clear with: **")
        print("                    python tools/status.py --clear-stale-locks")


def git_state():
    rule("GIT")
    print("branch            : " + sh("git", "rev-parse", "--abbrev-ref", "HEAD"))
    print("HEAD              : " + sh("git", "log", "--oneline", "-1"))

    unpushed = sh("git", "log", "--oneline", "@{u}..HEAD")
    lines = unpushed.splitlines() if unpushed else []
    print("unpushed commits  : %d" % len(lines))
    for line in lines:
        print("                    " + line)

    # CRLF from the OneDrive/Windows checkout makes plain `git status` claim the
    # whole tree is modified. Only the whitespace-insensitive diff means
    # anything here, and staging is always explicit paths - never `git add -A`.
    real = sh("git", "diff", "--ignore-all-space", "--ignore-cr-at-eol", "--stat")
    print("uncommitted (real): " + ("none" if not real else ""))
    for line in real.splitlines():
        print("                    " + line)

    # `git diff` alone sees neither the index nor new files. On 3 Sep that blind
    # spot hid a whole draft plan - BUILD-PLAN.md and build-stages.json - from a
    # session that had just read this output and reported the tree clean. Staged
    # work and untracked files ARE uncommitted work; without them the line above
    # is a half-truth, and this script's whole promise is that it is not.
    staged = sh("git", "diff", "--staged", "--ignore-all-space",
                "--ignore-cr-at-eol", "--stat")
    if staged:
        print("staged            :")
        for line in staged.splitlines():
            print("                    " + line)
    untracked = sh("git", "ls-files", "--others", "--exclude-standard")
    if untracked:
        names = untracked.splitlines()
        print("untracked         : %d file(s)" % len(names))
        for name in names:
            print("                    " + name)

    # The instruments ARE the measurement. If either has moved since the last
    # commit, the next tick is a new baseline and is not comparable to 007-010.
    drift = sh("git", "diff", "--ignore-all-space", "--ignore-cr-at-eol",
               "--stat", "HEAD", "--", ".claude/agents/")
    if drift:
        print("")
        print("  ** INSTRUMENT DRIFT - the next tick is a NEW BASELINE **")
        for line in drift.splitlines():
            print("    " + line)
    else:
        print("instruments       : unchanged vs HEAD (tick stays comparable)")


def corpus_state(quick):
    rule("CORPUS")
    fams = 0
    senses = 0
    shards = []
    for f in sorted(glob.glob(str(ROOT / "data/families/annotated-*.json"))):
        match = re.search(r"annotated-(\d+)", f)
        if match:
            shards.append(int(match.group(1)))
        data = json.loads(Path(f).read_text(encoding="utf-8"))
        fams += len(data["families"])
        senses += sum(1 for fam in data["families"]
                      for m in fam["members"] if m.get("tone"))
    print("annotated         : %d families, %d senses" % (fams, senses))
    if shards:
        print("shards            : %d (latest annotated-%03d)"
              % (len(shards), max(shards)))

    adverbs = 0
    for f in glob.glob(str(ROOT / "data/entries/overlays/adverbs-*.overlay.jsonl")):
        adverbs += sum(1 for line in open(f, encoding="utf-8") if line.strip())
    print("adverbs inherited : %d" % adverbs)

    held = sorted(Path(p).stem.replace("held-5.3-", "")
                  for p in glob.glob(str(ROOT / "data/families/held-*.json")))
    print("held under 5.3    : %d (%s)" % (len(held), ", ".join(held) or "none"))

    if quick:
        print("validation        : skipped (--quick)")
        return
    batch = ROOT / "data/entries/batch-0001.jsonl"
    if not batch.exists():
        print("validation        : batch-0001.jsonl missing")
        return
    out = subprocess.run([sys.executable, str(ROOT / "tools/dict_validate.py"),
                          str(batch)], capture_output=True, text=True)
    tail = [l for l in out.stdout.splitlines() if "error(s)" in l]
    print("validation        : " + (tail[-1] if tail else "no summary line"))


def queue_state():
    rule("QUEUE - eligible means size >= 8 and charged >= 70%")
    done = set()
    for f in glob.glob(str(ROOT / "data/families/annotated-*.json")):
        for fam in json.loads(Path(f).read_text(encoding="utf-8"))["families"]:
            done.add(fam["id"].replace("family-", "").replace("oewn-", ""))

    lines = (("adjective", ROOT / "data/worklist.tsv", "a"),
             ("verb", ROOT / "data/worklist-verbs.tsv", "v"),
             ("noun", ROOT / "data/worklist-nouns.tsv", "n"))
    for label, path, pos in lines:
        if not path.exists():
            print("%-10s: no worklist built" % label)
            print("            build: python tools/worklist_build.py --pos %s --out data/%s"
                  % (pos, path.name))
            continue
        rows = list(csv.DictReader(open(path, encoding="utf-8"), delimiter="\t"))
        if not rows:
            print("%-10s: worklist empty" % label)
            continue
        elig = [r for r in rows if r.get("eligible") == "1"]
        todo = [r for r in elig
                if r["family_id"].replace("family-", "").replace("oewn-", "") not in done]
        members = sum(int(r["size"]) for r in todo)
        print("%-10s: %d families, %d eligible (%.1f%%), QUEUE %d families / %d members ~ %.1f ticks"
              % (label, len(rows), len(elig), 100.0 * len(elig) / len(rows),
                 len(todo), members, members / float(SENSES_PER_TICK)))
        for r in todo[:5]:
            print("            next: %-16s size=%-4s charged=%s"
                  % (r["head"], r["size"], r["charged_pct"]))

    print("")
    print("  The gate admits a small fraction on purpose. Emptying the queue is")
    print("  NOT 'finishing adjectives' - it is exhausting what the gate allows.")
    print("  11.75 measured that lowering the gate makes selection worse.")


def census_state():
    rule("CENSUS - gate is 5%; a tick over it stops the run")
    files = sorted(glob.glob(str(ROOT / "data/policy/census-*-results.json")))
    for f in files[-6:]:
        d = json.loads(Path(f).read_text(encoding="utf-8"))
        rate = d.get("error_rate_pct")
        if not isinstance(rate, (int, float)):
            # census 005 has no rate on purpose: its rubric had drifted and the
            # tick was withdrawn rather than published. A blank is the honest
            # rendering; a 0 would read as a perfect score.
            shown, flag = "  n/a", "  <-- withdrawn (11.77)"
        else:
            shown = "%5s" % rate
            flag = "  <-- OVER GATE" if rate > 5 else ""
        recon = d.get("reconciled_ids") or []
        note = "  reconciled=%d" % len(recon) if recon else ""
        # An alias rather than a resolved id means the aggregator was run
        # without --reader-model, so the file records what the agent asked for
        # and not what actually served. Aliases drift; that is why it matters.
        model = d.get("reader_model") or "?"
        if "-" not in model:
            note += "  (alias, not a resolved model id)"
        print("  %-14s read %4s  wrong %3s  %s%%  reader=%s%s%s"
              % (Path(f).stem.replace("-results", ""), d.get("read"),
                 d.get("wrong"), shown, model, note, flag))
    print("")
    print("  Read 007-010 as the real number (~2%). A rate is only comparable if")
    print("  the ruler did not move - check reader_model and reader_agent first.")
    print("  The rate is a FLOOR: it is what a blind reader catches, and faults")
    print("  living BETWEEN senses are near-invisible to a one-card-at-a-time read.")


def lint_state():
    rule("TONE LINT - a smoke alarm, not a referee")
    out = subprocess.run([sys.executable, str(ROOT / "tools/tone_lint.py"),
                          "--all", "--quiet"], capture_output=True, text=True)
    lines = [l.rstrip() for l in out.stdout.splitlines() if l.strip()]
    tail = [l for l in lines if l.startswith("TOTAL")]
    counts = [l for l in lines if l.startswith("  ")]
    for line in tail + counts:
        print("  " + line.strip())
    print("")
    print("  A flag is not a verdict, and absence of flags is not clearance.")


STAGES = ROOT / "data/policy/build-stages.json"

MARK = {"done": "[x]", "in_progress": "[~]", "blocked": "[!]",
        "not_started": "[ ]"}


def stage_state():
    """The build plan's stage table, read from disk rather than remembered.

    BUILD-PLAN.md section 7: a stage is marked done by its own done-check
    passing, never by someone deciding it looks finished. Three times in one day
    a join or a diff flag reported a cleaner number than the truth.
    """
    if not STAGES.exists():
        return
    rule("BUILD STAGES - data/policy/build-stages.json")
    try:
        data = json.loads(STAGES.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print("  UNREADABLE: %s" % exc)
        return

    if not data.get("approved"):
        print("  ** DRAFT - 'approved' is false. No stage is authorised to start. **")
    if data.get("note"):
        print("  " + data["note"])
    print("")

    for st in data.get("stages", []):
        state = st.get("state", "not_started")
        print("  %s %-2s %-52s %s" % (MARK.get(state, "[?]"), st.get("stage", "?"),
                                      (st.get("title") or "")[:52],
                                      st.get("spend") or ""))
        if st.get("closed_on") or st.get("closed_by_commit"):
            print("         closed %s  %s" % (st.get("closed_on") or "?",
                                              st.get("closed_by_commit") or ""))

    lane = data.get("always_running") or {}
    if lane:
        print("")
        print("  always running: %s" % lane.get("lane", ""))
        print("    %s families / %s senses / last census %s / %s outstanding repair(s)"
              % (lane.get("families", "?"), lane.get("senses", "?"),
                 lane.get("last_census", "?"), lane.get("outstanding_repairs", "?")))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--quick", action="store_true", help="skip validation")
    ap.add_argument("--clear-stale-locks", action="store_true",
                    help="delete lock files older than 15 min when no git process is "
                         "running; never touches a lock a live process may own")
    args = ap.parse_args()

    print("ColorDict - measured state, not remembered state")
    git_state()
    lock_state(args.clear_stale_locks)
    stage_state()
    corpus_state(args.quick)
    queue_state()
    census_state()
    lint_state()

    rule("NEXT")
    print("  docs/BUILD-PLAN.md is the plan; the BUILD STAGES block above is its")
    print("  measured state. docs/HANDOFF.md section 6 is what will bite you.")
    print("  Do not read the 108KB DICTIONARY-PLAN.md front to back - it is the")
    print("  record, not the entry point.")


if __name__ == "__main__":
    main()
