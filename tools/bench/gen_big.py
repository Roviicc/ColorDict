#!/usr/bin/env python3
"""Generate a synthetic StarDict dictionary of N entries for benchmarking.

    python3 tools/bench/gen_big.py 150000 /tmp/bench-150k dz

Writes big.ifo/.idx/.dict[.dz] plus probes.txt (2000 headwords sampled across
the whole index) for Bench.java to look up.
"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from stardict_make import build_set

if len(sys.argv) < 3:
    sys.exit(__doc__)

N = int(sys.argv[1])
OUT = Path(sys.argv[2])
DICTZIP = len(sys.argv) > 3 and sys.argv[3] == "dz"

random.seed(42)
SYL = ["ba", "co", "den", "fer", "gil", "hom", "int", "jak", "lum", "mor",
       "nex", "opt", "pra", "quin", "ret", "sal", "tor", "umb", "vex", "zon"]

# A realistic-ish article: POS, gloss, example, connotation line.
POS = ["noun", "verb", "adjective", "adverb"]
CONNOTATION = ["neutral", "positive", "negative", "formal", "informal", "derogatory"]

words = set()
while len(words) < N:
    n = random.randint(2, 4)
    words.add("".join(random.choice(SYL) for _ in range(n)))
words = sorted(words)

entries = []
for i, w in enumerate(words):
    pos = POS[i % len(POS)]
    con = CONNOTATION[i % len(CONNOTATION)]
    definition = (
        f"({pos}) a sense of {w} used to illustrate a realistic article body "
        f"with enough text to resemble a real dictionary entry.\\n"
        f"Connotation: {con}.\\n"
        f'Example: "The {w} was clearly visible from where they stood."'
    )
    entries.append((w, definition.replace("\\n", "\n"), []))

stats = build_set(entries, OUT, "big", f"Synthetic {N}", sts="m",
                  use_dictzip=DICTZIP)

# Probe list sampled across the whole index, for the Java benchmark.
probes = random.sample(words, min(2000, len(words)))
(OUT / "probes.txt").write_text("\n".join(probes), encoding="utf-8")
print(f"{N} entries -> dict {stats['dict_bytes']/1e6:.1f} MB, "
      f"idx {stats['idx_bytes']/1e6:.1f} MB, dictzip={DICTZIP}")
