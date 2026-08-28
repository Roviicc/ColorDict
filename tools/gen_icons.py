#!/usr/bin/env python3
"""Rasterize the legacy launcher icons (mipmap-*/ic_launcher.png) without any
imaging library: shapes are supersampled point tests, PNG is written by hand.

Draws the same art as res/drawable/ic_launcher_foreground.xml: a rounded
dark square, a white open book, and four color-coded text lines.
"""

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BACKGROUND = (0x26, 0x32, 0x38)
WHITE = (0xFF, 0xFF, 0xFF)
BARS = [(0xEF, 0x53, 0x50), (0x42, 0xA5, 0xF5), (0x66, 0xBB, 0x6A), (0xFF, 0xA7, 0x26)]

# Geometry in a unit square (matches the vector foreground, minus adaptive
# padding: the 108-grid art spans ~30..78, remapped here to ~0.16..0.84).


def remap(p):
    return ((p[0] - 24.0) / 60.0, (p[1] - 24.0) / 60.0)


LEFT_PAGE = [remap(p) for p in [(34.2, 38.6), (52.7, 40.8), (52.7, 70.5), (34.2, 68.3)]]
RIGHT_PAGE = [remap(p) for p in [(73.8, 38.6), (55.3, 40.8), (55.3, 70.5), (73.8, 68.3)]]
BAR_QUADS = [
    [remap(p) for p in [(37, 45), (50, 43.6), (50, 46.4), (37, 47.8)]],
    [remap(p) for p in [(37, 51.5), (50, 50.1), (50, 52.9), (37, 54.3)]],
    [remap(p) for p in [(58, 43.6), (71, 45), (71, 47.8), (58, 46.4)]],
    [remap(p) for p in [(58, 50.1), (71, 51.5), (71, 54.3), (58, 52.9)]],
]


def in_poly(x, y, poly):
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def in_rounded_square(x, y, radius=0.18):
    if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
        return False
    cx = min(x, 1.0 - x)
    cy = min(y, 1.0 - y)
    if cx >= radius or cy >= radius:
        return True
    return (cx - radius) ** 2 + (cy - radius) ** 2 <= radius * radius


def color_at(x, y):
    """Color of the unit-square point, or None if transparent."""
    if not in_rounded_square(x, y):
        return None
    for quad, color in zip(BAR_QUADS, BARS):
        if in_poly(x, y, quad):
            return color
    if in_poly(x, y, LEFT_PAGE) or in_poly(x, y, RIGHT_PAGE):
        return WHITE
    return BACKGROUND


def render(size, samples=3):
    rows = []
    step = 1.0 / (size * samples)
    for py in range(size):
        row = bytearray()
        for px in range(size):
            acc = [0, 0, 0, 0]
            for sy in range(samples):
                for sx in range(samples):
                    x = (px * samples + sx + 0.5) * step
                    y = (py * samples + sy + 0.5) * step
                    c = color_at(x, y)
                    if c is not None:
                        acc[0] += c[0]
                        acc[1] += c[1]
                        acc[2] += c[2]
                        acc[3] += 255
            n = samples * samples
            row += bytes((acc[0] // n, acc[1] // n, acc[2] // n, acc[3] // n))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    def chunk(tag, data):
        block = tag + data
        return struct.pack(">I", len(data)) + block + struct.pack(
            ">I", zlib.crc32(block) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + r for r in rows)  # filter type 0 per scanline
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main():
    for density, size in SIZES.items():
        rows = render(size)
        out = RES / f"mipmap-{density}" / "ic_launcher.png"
        write_png(out, size, rows)
        print(f"{out.relative_to(ROOT)}: {size}x{size}")


if __name__ == "__main__":
    main()
