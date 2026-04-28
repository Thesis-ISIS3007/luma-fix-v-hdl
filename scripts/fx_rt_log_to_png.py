#!/usr/bin/env python3
"""Decode a render-log binary produced by the FX32 ray tracer into a PNG image.

The log is a little-endian uint32 stream:

    word 0     : (width << 16) | height
    word 1     : sentinel 0x5254524C ("RTRL")
    word 2..   : width * height pixels in row-major (top-to-bottom) order,
                 each laid out as 0xAA_RR_GG_BB

Alpha is dropped on the way out; output is 8-bit RGB PNG (color type 2).
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib
from pathlib import Path


SENTINEL = 0x5254524C


def _png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    if len(chunk_type) != 4:
        raise ValueError("chunk type must be 4 bytes")
    crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", crc)


def write_png_rgb(
    path: Path,
    width: int,
    height: int,
    rgb: bytes,
) -> None:
    """Write 8-bit RGB PNG. rgb length must be width * height * 3, row-major top-down."""
    expected = width * height * 3
    if len(rgb) != expected:
        raise ValueError(f"rgb length {len(rgb)} != {expected} for {width}x{height}")
    ihdr = struct.pack(">2I5B", width, height, 8, 2, 0, 0, 0)
    raw = bytearray()
    stride = width * 3
    for y in range(height):
        raw.append(0)
        raw.extend(rgb[y * stride : (y + 1) * stride])
    idat = zlib.compress(bytes(raw), 9)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(_png_chunk(b"IHDR", ihdr))
        fh.write(_png_chunk(b"IDAT", idat))
        fh.write(_png_chunk(b"IEND", b""))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log_path", type=Path, help="Path to the .bin render log.")
    parser.add_argument("out_path", type=Path, help="Destination .png file.")
    args = parser.parse_args(argv)
    if not args.log_path.is_file():
        parser.error(f"render log not found: {args.log_path}")

    data = args.log_path.read_bytes()
    if len(data) < 8:
        parser.error(
            f"Log {args.log_path} is too short ({len(data)} bytes); "
            "expected at least an 8 byte header."
        )
    if len(data) % 4 != 0:
        parser.error(
            f"Log {args.log_path} length {len(data)} is not a multiple of 4 bytes."
        )

    words = struct.unpack(f"<{len(data) // 4}I", data)

    header = words[0]
    width = (header >> 16) & 0xFFFF
    height = header & 0xFFFF
    sentinel = words[1]
    if sentinel != SENTINEL:
        parser.error(
            f"Sentinel mismatch: expected 0x{SENTINEL:08X}, got 0x{sentinel:08X}."
        )

    expected_pixels = width * height
    actual_pixels = len(words) - 2
    if actual_pixels != expected_pixels:
        print(
            f"warning: header says {expected_pixels} pixels ({width}x{height}), "
            f"got {actual_pixels} - clamping to min.",
            file=sys.stderr,
        )

    n = min(actual_pixels, expected_pixels)
    rgb = bytearray(3 * expected_pixels)
    for i in range(n):
        px = words[2 + i]
        rgb[3 * i + 0] = (px >> 16) & 0xFF
        rgb[3 * i + 1] = (px >> 8) & 0xFF
        rgb[3 * i + 2] = px & 0xFF

    write_png_rgb(args.out_path, width, height, bytes(rgb))
    print(f"wrote {args.out_path} ({width}x{height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
