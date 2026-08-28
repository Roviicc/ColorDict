#!/usr/bin/env python3
"""Generate the StarDict test fixtures under app/src/test/resources/fixtures/
and the bundled sample glossary under app/src/main/assets/dicts/.

Deterministic output — safe to re-run and commit.
"""

import struct
import sys
from pathlib import Path

from stardict_make import build_set, read_tsv

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "app" / "src" / "test" / "resources" / "fixtures"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "dicts"


def sized_block(type_char: str, data: bytes) -> bytes:
    """An upper-case typed block: type byte + u32 BE size + data."""
    return type_char.encode() + struct.pack(">I", len(data)) + data


def text_block(type_char: str, data: bytes, terminated=True) -> bytes:
    """A lower-case typed block: type byte + text + NUL."""
    return type_char.encode() + data + (b"\x00" if terminated else b"")


def main():
    # ---- basic: plain files, sametypesequence=m, synonyms, case-fold clusters
    basic = [
        ("Apple", "the fruit spelled with a capital A.", []),
        ("apple", "a round fruit with crisp flesh.", ["pomme"]),
        ("APPLE", "the same word in all capitals.", []),
        ("car", "a road vehicle with an engine and four wheels.",
         ["automobile", "motorcar"]),
        ("cat", "a small domesticated feline.", []),
        ("catalog", "an organized list of items.", []),
        ("catapult", "a device that hurls objects.", []),
        ("cattle", "farm cows and bulls.", []),
        ("naïve", "showing innocent trust; lacking experience.", []),
        ("résumé", "a short account of one's career.", ["CV"]),
        ("zebra", "an African wild horse with stripes.", []),
        ("über", "a German loanword meaning 'above' or 'super'.", []),
    ]
    build_set(basic, FIXTURES / "basic", "basic", "Basic Test Dict",
              sts="m", author="fixture", description="fixture: plain m")

    # ---- dz64: dictzip + gzipped idx + 64-bit offsets, long payloads spanning chunks
    dz64 = [(w, (f"[{w}] " + "lorem ipsum dolor sit amet " * 12).strip(), [])
            for w in ("alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
                      "golf", "hotel", "india", "juliet", "kilo", "lima")]
    build_set(dz64, FIXTURES / "dz64", "dz64", "DictZip 64-bit Test Dict",
              sts="m", offset_bits=64, use_dictzip=True, gzip_idx=True,
              chunk_len=96)

    # ---- typed: NO sametypesequence; hand-crafted multi-block payloads
    payloads = {
        "alpha": (text_block("m", "plain body line one\nline two".encode())
                  + text_block("t", "ˈæl.fə".encode())),
        "beta": (text_block("h", b"<b>beta</b> is <i>second</i>")
                 + sized_block("W", b"\x00\x01\x02\x03")),
        "gamma": (text_block("x", "<k>gamma</k><tr>ˈɡæm.ə</tr><c c=\"#008000\">third letter</c> "
                             "<kref>alpha</kref> <ex>an example</ex>".encode())
                  + sized_block("P", b"\x89PNG\r\n")),
        "delta": text_block("g", b"river <b>mouth</b> deposit, <span foreground=\"#FF0000\">red</span>",
                            terminated=False),
    }
    typed = [(w, "", []) for w in payloads]
    build_set(typed, FIXTURES / "typed", "typed", "Typed Blocks Test Dict",
              sts="", raw_payloads=payloads)

    # ---- html: sametypesequence=h with links
    html = [
        ("link", '<div>see <a href="bword://target">target</a> and '
                 '<a href="https://example.com/">the web</a></div>', []),
        ("target", "<p>the linked-to entry</p>", []),
    ]
    build_set(html, FIXTURES / "html", "html", "HTML Test Dict", sts="h")

    # ---- bundled sample glossary asset
    tsv = ROOT / "tools" / "sample_glossary.tsv"
    stats = build_set(
        read_tsv(tsv), ASSETS / "sample-glossary", "sample-glossary",
        "Sample English Mini-Glossary", sts="m",
        author="ColorDict clone project",
        description="A tiny built-in glossary so the app works out of the box.\n"
                    "Import full StarDict dictionaries for real use.")
    print(f"fixtures + sample written ({stats['words']} sample words)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
