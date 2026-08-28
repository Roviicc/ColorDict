#!/usr/bin/env python3
"""Independently parse and validate a StarDict dictionary set.

Checks performed:
  * .ifo magic line and metadata sanity (wordcount, idxfilesize, synwordcount)
  * .idx / .idx.gz record structure and StarDict sort order
  * every (offset, size) lies inside the .dict payload
  * .dict.dz gzip/dictzip header, per-chunk random-access inflation matches a
    plain whole-stream decompression, and the CRC32/ISIZE trailer
  * .syn record structure, sort order, and target indexes in range

Usage:
    python3 tools/verify_stardict.py path/to/base.ifo [--dump N]
"""

import argparse
import gzip
import struct
import sys
import zlib
from pathlib import Path

from stardict_make import stardict_cmp


def fail(msg):
    raise SystemExit(f"FAIL: {msg}")


def parse_ifo(path: Path) -> dict:
    text = path.read_text(encoding="utf-8-sig")
    lines = [ln.rstrip("\r") for ln in text.splitlines()]
    if not lines or lines[0].strip() != "StarDict's dict ifo file":
        fail(f"{path}: bad magic line")
    meta = {}
    for ln in lines[1:]:
        if "=" in ln:
            k, v = ln.split("=", 1)
            meta[k.strip()] = v.strip()
    if "bookname" not in meta:
        fail(f"{path}: missing bookname")
    return meta


def read_idx(base: Path, offset_bits: int):
    idx_path = base.with_suffix(".idx")
    if idx_path.exists():
        blob = idx_path.read_bytes()
    else:
        gz = base.with_suffix(".idx.gz")
        if not gz.exists():
            fail(f"missing {idx_path} / {gz}")
        blob = gzip.decompress(gz.read_bytes())
    entries = []
    off_size = 4 if offset_bits == 32 else 8
    pos = 0
    while pos < len(blob):
        nul = blob.index(b"\x00", pos)
        word = blob[pos:nul]
        rec = blob[nul + 1: nul + 1 + off_size + 4]
        if len(rec) != off_size + 4:
            fail("truncated idx record")
        if offset_bits == 32:
            offset, size = struct.unpack(">II", rec)
        else:
            offset, size = struct.unpack(">QI", rec)
        entries.append((word, offset, size))
        pos = nul + 1 + off_size + 4
    return blob, entries


def read_dictzip(path: Path) -> bytes:
    raw = path.read_bytes()
    if raw[:2] != b"\x1f\x8b" or raw[2] != 8:
        fail(f"{path}: not a gzip file")
    flg = raw[3]
    pos = 10
    chunk_len = chunk_sizes = None
    if flg & 0x04:  # FEXTRA
        xlen = struct.unpack_from("<H", raw, pos)[0]
        extra = raw[pos + 2: pos + 2 + xlen]
        pos += 2 + xlen
        epos = 0
        while epos + 4 <= len(extra):
            si, slen = extra[epos:epos + 2], struct.unpack_from("<H", extra, epos + 2)[0]
            data = extra[epos + 4: epos + 4 + slen]
            if si == b"RA":
                ver, chunk_len, chcnt = struct.unpack_from("<HHH", data, 0)
                if ver != 1:
                    fail(f"{path}: unsupported RA version {ver}")
                chunk_sizes = list(struct.unpack_from(f"<{chcnt}H", data, 6))
            epos += 4 + slen
    if chunk_sizes is None:
        fail(f"{path}: no dictzip RA extra field")
    if flg & 0x08:  # FNAME
        pos = raw.index(b"\x00", pos) + 1
    if flg & 0x10:  # FCOMMENT
        pos = raw.index(b"\x00", pos) + 1
    if flg & 0x02:  # FHCRC
        pos += 2

    # Whole-stream reference decompression.
    whole = zlib.decompress(raw[pos:len(raw) - 8], -15)

    # Random-access decompression chunk by chunk must give the same bytes.
    out = bytearray()
    cpos = pos
    for i, csize in enumerate(chunk_sizes):
        d = zlib.decompressobj(-15)
        piece = d.decompress(raw[cpos:cpos + csize], chunk_len)
        if i < len(chunk_sizes) - 1 and len(piece) != chunk_len:
            fail(f"{path}: chunk {i} inflated to {len(piece)} != {chunk_len}")
        out += piece
        cpos += csize
    if cpos != len(raw) - 8:
        fail(f"{path}: chunk sizes do not cover the compressed payload")
    if bytes(out) != whole:
        fail(f"{path}: random-access decompression mismatch")

    crc, isize = struct.unpack_from("<II", raw, len(raw) - 8)
    if crc != zlib.crc32(whole) & 0xFFFFFFFF:
        fail(f"{path}: CRC mismatch")
    if isize != len(whole) & 0xFFFFFFFF:
        fail(f"{path}: ISIZE mismatch")
    return whole


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("ifo", type=Path)
    ap.add_argument("--dump", type=int, default=0, help="print the first N entries")
    args = ap.parse_args(argv)

    meta = parse_ifo(args.ifo)
    base = args.ifo.with_suffix("")
    offset_bits = int(meta.get("idxoffsetbits", "32"))

    idx_blob, entries = read_idx(base, offset_bits)
    if "wordcount" in meta and int(meta["wordcount"]) != len(entries):
        fail(f"wordcount={meta['wordcount']} but idx has {len(entries)} entries")
    if "idxfilesize" in meta and int(meta["idxfilesize"]) != len(idx_blob):
        fail(f"idxfilesize={meta['idxfilesize']} but idx is {len(idx_blob)} bytes")
    for i in range(1, len(entries)):
        if stardict_cmp(entries[i - 1][0], entries[i][0]) > 0:
            fail(f"idx not sorted at #{i}: {entries[i-1][0]!r} > {entries[i][0]!r}")

    dz = base.with_suffix(".dict.dz")
    plain = base.with_suffix(".dict")
    if dz.exists():
        payload = read_dictzip(dz)
    elif plain.exists():
        payload = plain.read_bytes()
    else:
        fail(f"missing {plain} / {dz}")
    for word, offset, size in entries:
        if offset + size > len(payload):
            fail(f"{word!r}: offset+size {offset}+{size} beyond dict of {len(payload)} bytes")

    syn = base.with_suffix(".syn")
    syn_count = 0
    if syn.exists():
        blob = syn.read_bytes()
        pos = 0
        prev = None
        while pos < len(blob):
            nul = blob.index(b"\x00", pos)
            word = blob[pos:nul]
            target = struct.unpack_from(">I", blob, nul + 1)[0]
            if target >= len(entries):
                fail(f"syn {word!r}: target {target} out of range")
            if prev is not None and stardict_cmp(prev, word) > 0:
                fail(f"syn not sorted: {prev!r} > {word!r}")
            prev = word
            syn_count += 1
            pos = nul + 5
        if "synwordcount" in meta and int(meta["synwordcount"]) != syn_count:
            fail(f"synwordcount={meta['synwordcount']} but syn has {syn_count}")

    for word, offset, size in entries[:args.dump]:
        preview = payload[offset:offset + size][:60].decode("utf-8", "replace")
        print(f"  {word.decode('utf-8')!r:24} -> {preview!r}")

    print(f"OK: {base.name}: {len(entries)} words, {syn_count} synonyms, "
          f"dict {len(payload)} B ({'dz' if dz.exists() else 'plain'}), "
          f"sts={meta.get('sametypesequence', '')!r}, bits={offset_bits}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
