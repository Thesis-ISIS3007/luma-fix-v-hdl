#!/usr/bin/env python3
"""Decode a render-log binary produced by the FX32 ray tracer into a PPM image.

The log is a little-endian uint32 stream:

    word 0     : (width << 16) | height
    word 1     : sentinel 0x5254524C ("RTRL")
    word 2..   : width * height pixels in row-major (top-to-bottom) order,
                 each laid out as 0xAA_RR_GG_BB

Alpha is dropped on the way out because PPM has no alpha channel.
"""

import argparse
import struct
import sys
from pathlib import Path


SENTINEL = 0x5254524C


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log_path", type=Path, help="Path to the .bin render log.")
    parser.add_argument("out_path", type=Path, help="Destination .ppm file.")
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
    args.out_path.parent.mkdir(parents=True, exist_ok=True)
    with args.out_path.open("wb") as fh:
        fh.write(f"P6\n{width} {height}\n255\n".encode("ascii"))
        rgb = bytearray(3 * n)
        for i in range(n):
            px = words[2 + i]
            rgb[3 * i + 0] = (px >> 16) & 0xFF
            rgb[3 * i + 1] = (px >> 8) & 0xFF
            rgb[3 * i + 2] = px & 0xFF
        if n < expected_pixels:
            rgb.extend(bytes(3 * (expected_pixels - n)))
        fh.write(bytes(rgb))

    print(f"wrote {args.out_path} ({width}x{height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
