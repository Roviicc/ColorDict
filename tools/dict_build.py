#!/usr/bin/env python3
"""Build the Pop Up English Dictionary StarDict set from JSONL entry files.

Renders each entry to a compact HTML article (sametypesequence=h — the engine's
ArticleHtml passes type-h blocks straight to the WebView), writes the TSV that
tools/stardict_make.py consumes, invokes it, then verifies the result with
tools/verify_stardict.py. Stdlib only.

Merging: every *.jsonl under the given paths is loaded; when two files define
the same headword the higher editorial tier wins, ties going to the
later-processed file (derived-bulk.jsonl is always processed first, so batch
files override it).

Inflections and variant forms are emitted as the StarDict .syn column, which is
what makes a search for "running" land on "run".

Usage:
    python3 tools/dict_build.py data/entries/ --out data/build
    python3 tools/dict_build.py data/entries/ --out data/build --min-tier reviewed
"""

import argparse
import json
import re
import subprocess
import sys
import urllib.parse
from pathlib import Path

TOOLS = Path(__file__).resolve().parent

STATUS_RANK = {"derived": 0, "reviewed": 1, "curated": 2}
TIER_NOTE = {
    "derived": "auto-derived · unreviewed",
    "reviewed": "human-reviewed",
    "curated": "curated",
}

BOOKNAME = "Pop Up English Dictionary"
DESCRIPTION = (
    "Original open dictionary for the Pop Up Dictionary project.<br>"
    "Derived from Open English WordNet (CC BY 4.0) and "
    "SentiWordNet 3.0 (CC BY-SA 4.0); see the app's About screen for "
    "attribution details."
)


def escape(text):
    """HTML-escape plus the characters the TSV pipeline treats specially."""
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "&#92;").replace("\t", " "))


def bword(word):
    return ('<a href="bword://' + urllib.parse.quote(word, safe="") + '">'
            + escape(word) + "</a>")


def word_list_row(label, words):
    links = ", ".join(bword(w) for w in words)
    return f'<div class="fld"><span class="flk">{label}:</span> {links}</div>'


def formation_html(wf):
    if not wf or not wf.get("analysable"):
        return ""
    parts = []
    for p in wf.get("prefixes") or []:
        parts.append(f'<b>{escape(p["form"])}</b> <span class="wfm">'
                     f'{escape(p["meaning"])}</span>')
    root = wf.get("root")
    if root:
        parts.append(f'<b>{escape(root["form"])}</b>')
    for s in wf.get("suffixes") or []:
        parts.append(f'<b>{escape(s["form"])}</b> <span class="wfm">'
                     f'{escape(s["meaning"])}</span>')
    if not parts:
        return ""
    return ('<div class="fld"><span class="flk">Word parts:</span> '
            + " + ".join(parts) + "</div>")


def bold_headword(word, escaped_example):
    """Bold the headword (or an inflection sharing its stem) in an example."""
    if " " in word:
        pattern = re.compile(re.escape(escape(word)), re.IGNORECASE)
    else:
        stem = word[:-1] if len(word) > 3 and word[-1] in "ey" else word
        pattern = re.compile(r"\b(" + re.escape(stem) + r"[a-z]*)\b", re.IGNORECASE)
    return pattern.sub(lambda m: "<b>" + m.group(0) + "</b>", escaped_example)


def sense_html(word, sense, number):
    """The agreed entry format: Connotations / Meaning / Part of Speech /
    numbered Examples / Synonyms / Antonyms, one sense after another."""
    bits = []
    if number:
        bits.append(f'<div class="ps"><span class="sn">{number}.</span></div>')

    conn = sense.get("connotation") or {}
    label = conn.get("label")
    usage = conn.get("usage_labels") or []
    explanation = conn.get("explanation")
    tone = conn.get("tone")
    if label in ("positive", "negative") or usage or explanation or tone:
        row = ['<div class="fld"><span class="flk">Connotations:</span>']
        if label in ("positive", "negative"):
            css = "cnp" if label == "positive" else "cnn"
            row.append(f' <span class="cn {css}">{label}</span>')
        for ul in usage:
            row.append(f' <span class="ul">{escape(ul)}</span>')
        prose = " ".join(p for p in (tone, explanation) if p)
        if prose:
            row.append(f' <span class="cx">{escape(prose)}</span>')
        row.append("</div>")
        bits.append("".join(row))

    bits.append(f'<div class="fld"><span class="flk">Meaning:</span> '
                f'{escape(sense["definition"])}</div>')
    bits.append(f'<div class="fld"><span class="flk">Part of Speech:</span> '
                f'<span class="pos">{escape(sense["part_of_speech"])}</span></div>')

    examples = sense.get("examples") or []
    if examples:
        bits.append('<div class="fld"><span class="flk">Examples:</span></div>')
        for i, ex in enumerate(examples, 1):
            bits.append(f'<div class="xex">{i}. “{bold_headword(word, escape(ex))}”</div>')
    if sense.get("synonyms"):
        bits.append(word_list_row("Synonyms", sense["synonyms"]))
    if sense.get("antonyms"):
        bits.append(word_list_row("Antonyms", sense["antonyms"]))

    family = sense.get("family")
    if family and family.get("spectrum"):
        # The connotation spectrum: same behaviour, sliding verdict.
        parts = []
        for fam_word, charge in sorted(family["spectrum"], key=lambda p: p[1]):
            badge = f"{charge:+d}" if charge else "0"
            if fam_word == word:
                parts.append(f'<b>{escape(fam_word)}</b> <span class="chg">{badge}</span>')
            else:
                parts.append(f'{bword(fam_word)} <span class="chg">{badge}</span>')
        axis = escape(family.get("axis") or "condemning → praising")
        bits.append(f'<div class="fld"><span class="flk">Family ({axis}):</span> '
                    + " · ".join(parts) + "</div>")
    return "".join(bits)


def entry_html(entry):
    bits = ['<div class="pe">']
    if entry.get("pronunciation"):
        bits.append(f'<div class="phon">/{escape(entry["pronunciation"])}/</div>')
    senses = entry["senses"]
    numbered = len(senses) > 1
    for i, sense in enumerate(senses, 1):
        bits.append(sense_html(entry["word"], sense, i if numbered else 0))
    bits.append(formation_html(entry.get("word_formation")))
    status = entry.get("editorial", {}).get("status", "derived")
    bits.append(f'<div class="tier">{TIER_NOTE.get(status, status)}</div>')
    bits.append("</div>")
    return "".join(bits)


def load_entries(paths, min_rank):
    files = []
    for p in paths:
        if p.is_dir():
            files.extend(p.glob("*.jsonl"))
        elif p.is_file():
            files.append(p)
        else:
            sys.exit(f"not found: {p}")
    # derived-bulk first so curation batches override it on conflicts.
    files.sort(key=lambda f: (0 if f.name.startswith("derived") else 1, f.name))

    merged = {}
    total = 0
    for f in files:
        n = 0
        with open(f, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                entry = json.loads(line)
                n += 1
                word = entry["word"]
                rank = STATUS_RANK.get(
                    entry.get("editorial", {}).get("status", "derived"), 0)
                old = merged.get(word)
                if old is None or rank >= old[0]:
                    merged[word] = (rank, entry)
        print(f"  {f.name}: {n} entries")
        total += n
    kept = {w: e for w, (r, e) in merged.items() if r >= min_rank}
    print(f"merged {total} -> {len(merged)} unique headwords, "
          f"{len(kept)} at or above the requested tier")
    return kept


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="+", type=Path)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--base", default="popup-en")
    ap.add_argument("--bookname", default=BOOKNAME)
    ap.add_argument("--min-tier", default="derived", choices=sorted(STATUS_RANK))
    ap.add_argument("--no-dictzip", action="store_true")
    ap.add_argument("--skip-verify", action="store_true")
    args = ap.parse_args()

    entries = load_entries(args.paths, STATUS_RANK[args.min_tier])
    if not entries:
        sys.exit("nothing to build")

    args.out.mkdir(parents=True, exist_ok=True)
    tsv = args.out / f"{args.base}.tsv"
    tiers = {}
    with open(tsv, "w", encoding="utf-8", newline="\n") as fh:
        for word in sorted(entries, key=lambda w: (w.casefold(), w)):
            entry = entries[word]
            status = entry.get("editorial", {}).get("status", "derived")
            tiers[status] = tiers.get(status, 0) + 1
            html = entry_html(entry)
            row = [word, html]
            forms = [i for i in entry.get("inflections") or []
                     if i and i != word and "\t" not in i and "|" not in i]
            if forms:
                row.append("|".join(dict.fromkeys(forms)))
            fh.write("\t".join(row) + "\n")
    print(f"tsv written: {tsv} ({tsv.stat().st_size} bytes); tiers: {tiers}")

    cmd = [sys.executable, str(TOOLS / "stardict_make.py"), str(tsv),
           str(args.out), args.base,
           "--bookname", args.bookname,
           "--sts", "h",
           "--author", "ColorDict clone project",
           "--description", DESCRIPTION]
    if not args.no_dictzip:
        cmd.append("--dictzip")
    print("running:", " ".join(cmd[1:]))
    subprocess.run(cmd, check=True)

    if not args.skip_verify:
        subprocess.run([sys.executable, str(TOOLS / "verify_stardict.py"),
                        str(args.out / f"{args.base}.ifo"), "--dump", "3"],
                       check=True)

    for f in sorted(args.out.glob(f"{args.base}.*")):
        print(f"  {f.name:24} {f.stat().st_size:>12,} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
