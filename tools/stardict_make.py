#!/usr/bin/env python3
"""Build a StarDict dictionary set (.ifo / .idx / .dict / optional .syn) from a TSV file.

TSV columns (tab-separated, UTF-8):
    headword <TAB> definition [<TAB> synonym|synonym|...]

Literal "\\n" sequences in the definition column become newlines.

Examples:
    python3 tools/stardict_make.py words.tsv out/ mydict --bookname "My Dictionary"
    python3 tools/stardict_make.py words.tsv out/ mydict --bookname "My Dictionary" \
        --sts h --dictzip --gzip-idx --offset-bits 64

The output follows the StarDict 3.0.0 format documentation (doc/StarDictFileFormat
in the stardict-3 sources):
  .ifo   text metadata, first line magic "StarDict's dict ifo file"
  .idx   sorted records: word\\0 + offset (u32/u64 BE) + size (u32 BE)
  .dict  concatenated article payloads (optionally dictzip-compressed to .dict.dz)
  .syn   sorted records: synonym\\0 + index of the target .idx entry (u32 BE)

Sorting uses the StarDict collation: g_ascii_strcasecmp on the UTF-8 bytes,
ties broken by a plain byte compare.
"""

import argparse
import functools
import gzip
import struct
import sys
import zlib
from pathlib import Path

DICTZIP_DEFAULT_CHUNK = 58315  # dictd's default; keeps compressed chunks < 64 KiB


# ---------------------------------------------------------------- collation

def _ascii_lower(b: int) -> int:
    return b + 32 if 0x41 <= b <= 0x5A else b


def cmp_fold(a: bytes, b: bytes) -> int:
    """g_ascii_strcasecmp equivalent on raw bytes."""
    for x, y in zip(a, b):
        c = _ascii_lower(x) - _ascii_lower(y)
        if c:
            return c
    return len(a) - len(b)


def stardict_cmp(a: bytes, b: bytes) -> int:
    """Case-insensitive compare, ties broken case-sensitively (stardict_strcmp)."""
    c = cmp_fold(a, b)
    if c:
        return c
    return (a > b) - (a < b)


stardict_key = functools.cmp_to_key(stardict_cmp)


# ---------------------------------------------------------------- dictzip

def dictzip_bytes(data: bytes, chunk_len: int = DICTZIP_DEFAULT_CHUNK,
                  file_name: str = "") -> bytes:
    """Compress `data` into the dictzip variant of gzip (random-access capable).

    A single deflate stream is flushed with Z_FULL_FLUSH at every chunk
    boundary, so each chunk can later be inflated independently with a raw
    inflater. Chunk sizes are recorded in the gzip FEXTRA 'RA' subfield.
    """
    if chunk_len <= 0 or chunk_len > 0xFFFF:
        raise ValueError("chunk_len must be in 1..65535")
    chunks = [data[i:i + chunk_len] for i in range(0, len(data), chunk_len)] or [b""]
    if len(chunks) > 0xFFFF:
        raise ValueError("too many dictzip chunks; increase chunk_len")

    comp = zlib.compressobj(9, zlib.DEFLATED, -15)
    comp_chunks = []
    for chunk in chunks:
        comp_chunks.append(comp.compress(chunk) + comp.flush(zlib.Z_FULL_FLUSH))
    comp_chunks[-1] += comp.flush(zlib.Z_FINISH)
    for c in comp_chunks:
        if len(c) > 0xFFFF:
            raise ValueError("compressed chunk exceeds 64 KiB; lower chunk_len")

    ra = struct.pack("<HHH", 1, chunk_len, len(chunks))
    ra += b"".join(struct.pack("<H", len(c)) for c in comp_chunks)
    extra = b"RA" + struct.pack("<H", len(ra)) + ra

    flg = 0x04  # FEXTRA
    name = file_name.encode("utf-8", "replace")
    if name:
        flg |= 0x08  # FNAME
    header = (b"\x1f\x8b\x08" + bytes([flg]) + b"\x00\x00\x00\x00" + b"\x02\x03"
              + struct.pack("<H", len(extra)) + extra)
    if name:
        header += name + b"\x00"

    trailer = struct.pack("<II", zlib.crc32(data) & 0xFFFFFFFF, len(data) & 0xFFFFFFFF)
    return header + b"".join(comp_chunks) + trailer


# ---------------------------------------------------------------- building

def read_tsv(path: Path):
    """Yield (word, definition, [synonyms]) tuples from a TSV file."""
    entries = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        cols = line.split("\t")
        if len(cols) < 2:
            raise SystemExit(f"{path}:{lineno}: expected at least 2 tab-separated columns")
        word = cols[0].strip()
        definition = cols[1].replace("\\n", "\n")
        syns = [s.strip() for s in cols[2].split("|") if s.strip()] if len(cols) > 2 else []
        if not word:
            raise SystemExit(f"{path}:{lineno}: empty headword")
        entries.append((word, definition, syns))
    return entries


def build_set(entries, out_dir: Path, base: str, bookname: str, *,
              sts: str = "m", offset_bits: int = 32, use_dictzip: bool = False,
              gzip_idx: bool = False, chunk_len: int = DICTZIP_DEFAULT_CHUNK,
              author: str = "", description: str = "", website: str = "",
              raw_payloads=None):
    """Write a StarDict set. `entries` is a list of (word, definition, syns).

    If `raw_payloads` is given it must map word -> bytes and overrides the
    definition text (used for fixtures with hand-crafted typed articles;
    combine with sts="" for a typed byte stream).
    """
    if offset_bits not in (32, 64):
        raise ValueError("offset_bits must be 32 or 64")
    out_dir.mkdir(parents=True, exist_ok=True)

    # Sort headwords with the StarDict collation.
    ordered = sorted(entries, key=lambda e: stardict_key(e[0].encode("utf-8")))

    dict_blob = bytearray()
    idx_blob = bytearray()
    positions = {}  # word -> entry index (first occurrence wins for .syn targets)
    off_fmt = ">I" if offset_bits == 32 else ">Q"
    for i, (word, definition, _syns) in enumerate(ordered):
        payload = (raw_payloads[word] if raw_payloads is not None
                   else definition.encode("utf-8"))
        offset = len(dict_blob)
        dict_blob += payload
        wb = word.encode("utf-8")
        if len(wb) > 255:
            raise SystemExit(f"headword too long: {word!r}")
        idx_blob += wb + b"\x00" + struct.pack(off_fmt, offset) + struct.pack(">I", len(payload))
        positions.setdefault(word, i)

    # Synonyms.
    syn_pairs = []
    for word, _definition, syns in ordered:
        for s in syns:
            syn_pairs.append((s, positions[word]))
    syn_pairs.sort(key=lambda p: (stardict_key(p[0].encode("utf-8")), p[1]))
    syn_blob = bytearray()
    for s, target in syn_pairs:
        syn_blob += s.encode("utf-8") + b"\x00" + struct.pack(">I", target)

    # Write files.
    if use_dictzip:
        (out_dir / f"{base}.dict.dz").write_bytes(
            dictzip_bytes(bytes(dict_blob), chunk_len, file_name=f"{base}.dict"))
    else:
        (out_dir / f"{base}.dict").write_bytes(dict_blob)

    if gzip_idx:
        (out_dir / f"{base}.idx.gz").write_bytes(
            gzip.compress(bytes(idx_blob), 9, mtime=0))
    else:
        (out_dir / f"{base}.idx").write_bytes(idx_blob)

    if syn_pairs:
        (out_dir / f"{base}.syn").write_bytes(syn_blob)

    ifo_lines = ["StarDict's dict ifo file", "version=3.0.0",
                 f"bookname={bookname}", f"wordcount={len(ordered)}"]
    if syn_pairs:
        ifo_lines.append(f"synwordcount={len(syn_pairs)}")
    ifo_lines.append(f"idxfilesize={len(idx_blob)}")
    if offset_bits == 64:
        ifo_lines.append("idxoffsetbits=64")
    if sts:
        ifo_lines.append(f"sametypesequence={sts}")
    if author:
        ifo_lines.append(f"author={author}")
    if website:
        ifo_lines.append(f"website={website}")
    if description:
        ifo_lines.append("description=" + description.replace("\n", "<br>"))
    (out_dir / f"{base}.ifo").write_text("\n".join(ifo_lines) + "\n", encoding="utf-8")

    return {"words": len(ordered), "synonyms": len(syn_pairs),
            "dict_bytes": len(dict_blob), "idx_bytes": len(idx_blob)}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("tsv", type=Path)
    ap.add_argument("out_dir", type=Path)
    ap.add_argument("base", help="base file name (without extension)")
    ap.add_argument("--bookname", required=True)
    ap.add_argument("--sts", default="m", help="sametypesequence (default: m; '' for none)")
    ap.add_argument("--offset-bits", type=int, default=32, choices=(32, 64))
    ap.add_argument("--dictzip", action="store_true", help="write .dict.dz")
    ap.add_argument("--chunk-len", type=int, default=DICTZIP_DEFAULT_CHUNK)
    ap.add_argument("--gzip-idx", action="store_true", help="write .idx.gz")
    ap.add_argument("--author", default="")
    ap.add_argument("--website", default="")
    ap.add_argument("--description", default="")
    args = ap.parse_args(argv)

    stats = build_set(read_tsv(args.tsv), args.out_dir, args.base, args.bookname,
                      sts=args.sts, offset_bits=args.offset_bits,
                      use_dictzip=args.dictzip, gzip_idx=args.gzip_idx,
                      chunk_len=args.chunk_len, author=args.author,
                      website=args.website, description=args.description)
    print(f"{args.base}: {stats['words']} words, {stats['synonyms']} synonyms, "
          f"dict {stats['dict_bytes']} B, idx {stats['idx_bytes']} B")
    return 0


if __name__ == "__main__":
    sys.exit(main())
