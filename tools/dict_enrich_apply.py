#!/usr/bin/env python3
"""Apply an enrichment overlay to derived entries, producing a batch file.

An overlay is JSONL keyed by headword and stable sense id — the format the
future model-enrichment pass emits, and the format a human editor writes by
hand. It never touches connotation labels or scores (the grounding rule,
docs/DICTIONARY-PLAN.md 5.5): it may add explanations, examples, usage labels,
word formation, and inflections. The validator remains the gate afterwards.

Overlay line shape:
    {"word": "cheap",
     "word_formation": {...},          # replaces entry word_formation
     "inflections": ["cheaper"],       # appended, deduped
     "senses": {"cheap.oewn-00937468-a": {
         "explanation": "...",         # only meaningful on non-neutral senses
         "examples": ["..."],          # appended, deduped
         "usage_labels": ["informal"]  # appended, deduped
     }}}

Usage:
    python3 tools/dict_enrich_apply.py \
        --bulk data/entries/derived-bulk.jsonl \
        --overlay data/entries/overlays/batch-0001.overlay.jsonl \
        --out data/entries/batch-0001.jsonl
"""

import argparse
import json
import re
import sys
from pathlib import Path

EDITORIAL_SOURCE = "popup-editorial"


def load_overlays(paths):
    """Merge several overlay files. Later files win field by field, so a
    family annotation can add a charge to a word an earlier batch already
    gave usage labels."""
    overlay = {}
    for path in paths:
        seen_here = set()
        with open(path, encoding="utf-8") as fh:
            for lineno, line in enumerate(fh, 1):
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                word = rec.get("word")
                if not word:
                    sys.exit(f"{path}:{lineno}: overlay line has no 'word'")
                if word in seen_here:
                    sys.exit(f"{path}:{lineno}: duplicate overlay for {word!r}")
                seen_here.add(word)
                existing = overlay.get(word)
                if existing is None:
                    overlay[word] = rec
                    continue
                senses = existing.setdefault("senses", {})
                for sid, patch in (rec.get("senses") or {}).items():
                    senses.setdefault(sid, {}).update(patch)
                for key, value in rec.items():
                    if key not in ("word", "senses"):
                        existing[key] = value
    return overlay


def extend_unique(target, extra):
    seen = {x.lower() for x in target}
    for x in extra:
        if x.lower() not in seen:
            seen.add(x.lower())
            target.append(x)
    return target


def mentions_headword(word, example, inflections=()):
    """True when the example actually uses the headword or an inflection.
    WordNet examples belong to the synset, so many illustrate a synonym
    instead - 'a long scrawny neck' sits under 'skinny'."""
    ex, wl = example.lower(), word.lower()
    if wl in ex or any(i.lower() in ex for i in inflections):
        return True
    if " " in wl:
        return False
    stem = wl[:-1] if len(wl) > 3 and wl[-1] in "ey" else wl
    tokens = re.findall(r"[a-z']+", ex)
    if len(stem) < 3:
        return wl in set(tokens)
    return any(t.startswith(stem) for t in tokens)


def prune_examples(entry):
    """Drop inherited examples that illustrate a synonym rather than the
    headword. Applied once an entry is authored: a wrong example is worse
    than none, and the article would not have shown them anyway."""
    word = entry["word"]
    inflections = entry.get("inflections") or []
    dropped = 0
    for sense in entry["senses"]:
        examples = sense.get("examples") or []
        if not examples:
            continue
        kept = [e for e in examples if mentions_headword(word, e, inflections)]
        dropped += len(examples) - len(kept)
        if kept:
            sense["examples"] = kept
        else:
            sense.pop("examples", None)
    return dropped


def is_authored(patch):
    """Did this patch add editorial content, as opposed to bookkeeping?"""
    return any(patch.get(k) for k in
               ("explanation", "tone", "family", "examples", "usage_labels", "label"))


def apply_overlay(entry, rec, problems):
    word = entry["word"]
    authored = False
    if "word_formation" in rec:
        entry["word_formation"] = rec["word_formation"]
    if rec.get("inflections"):
        entry["inflections"] = extend_unique(
            list(entry.get("inflections") or []), rec["inflections"])

    sense_by_id = {s["id"]: s for s in entry["senses"]}
    for sid, patch in (rec.get("senses") or {}).items():
        sense = sense_by_id.get(sid)
        if sense is None:
            problems.append(f"{word}: overlay names unknown sense id {sid}")
            continue
        unknown = set(patch) - {"explanation", "examples", "usage_labels", "tone",
                                "label", "family"}
        if unknown:
            problems.append(f"{word}/{sid}: overlay patch has unknown fields {sorted(unknown)}")
        authored = authored or is_authored(patch)
        conn = sense.setdefault("connotation", {"label": "neutral"})
        if patch.get("label"):
            # Editorial label override (a reviewed correction of the machine
            # label). The SentiWordNet score is the machine's claim, so it is
            # dropped: the entry no longer asserts what it now contradicts.
            if patch["label"] not in ("positive", "negative", "neutral"):
                problems.append(f"{word}/{sid}: bad label override {patch['label']!r}")
            else:
                conn["label"] = patch["label"]
                conn.pop("score", None)
                if patch["label"] == "neutral":
                    conn.pop("explanation", None)
        if patch.get("explanation"):
            if conn.get("label") == "neutral":
                # Fabrication rule: never attach an explanation to a neutral
                # label. Flag it instead of silently dropping.
                problems.append(f"{word}/{sid}: explanation given for a neutral sense")
            else:
                conn["explanation"] = patch["explanation"]
        if patch.get("family"):
            sense["family"] = patch["family"]
        if patch.get("tone"):
            # Register/association description — allowed on any label; unlike
            # 'explanation' it makes no positive/negative claim.
            conn["tone"] = patch["tone"]
        if patch.get("usage_labels"):
            conn["usage_labels"] = extend_unique(
                list(conn.get("usage_labels") or []), patch["usage_labels"])
        if patch.get("examples"):
            sense["examples"] = extend_unique(
                list(sense.get("examples") or []), patch["examples"])

    editorial = entry.setdefault(
        "editorial", {"status": "derived", "revision": 1, "sources": []})
    editorial["revision"] = int(editorial.get("revision", 1)) + 1
    if EDITORIAL_SOURCE not in editorial.get("sources", []):
        editorial.setdefault("sources", []).append(EDITORIAL_SOURCE)
    # A hand-authored entry is no longer "auto-derived": promote the tier so
    # the article footer stops calling it unreviewed.
    if authored and editorial.get("status") == "derived":
        editorial["status"] = "reviewed"
        entry["_pruned"] = prune_examples(entry)
    return entry


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--bulk", type=Path, required=True,
                    help="derived entries to enrich (read-only)")
    ap.add_argument("--overlay", type=Path, required=True, action="append",
                    help="overlay file; repeatable, later files win per field")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    overlay = load_overlays(args.overlay)
    problems = []
    out = []
    with open(args.bulk, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            entry = json.loads(line)
            rec = overlay.pop(entry["word"], None)
            if rec is not None:
                out.append(apply_overlay(entry, rec, problems))

    for word in overlay:
        problems.append(f"overlay word {word!r} not found in {args.bulk.name}")
    if problems:
        for p in problems:
            print(f"ERROR  {p}", file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    pruned = sum(entry.pop("_pruned", 0) for entry in out)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for entry in out:
            fh.write(json.dumps(entry, ensure_ascii=False,
                                separators=(",", ":")) + "\n")
    print(f"wrote {len(out)} enriched entries to {args.out} "
          f"({pruned} off-target inherited examples pruned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
