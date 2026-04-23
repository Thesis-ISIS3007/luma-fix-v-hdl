#!/usr/bin/env python3
"""Preview PPM for a centered equilateral triangle in the xy plane.

Renders with an orthographic “camera” at z = +1 and rays (sx, sy, 1) with
D = (0, 0, -1) — same family as the C ray-triangle smokes, but the
geometry here is a **centroid-centred** equilateral triangle (vertices on
a circle, side length sqrt(3)), not the C validation's right-triangle
fixture in ``validation/fx_rt_triangle_smoke.c``.

The (sx, sy) frustum is a **square** centered at the origin (triangle
centroid) with equal padding so the shape sits in the middle of the image.

This is a fast 2D point-in-triangle test. It does not run the RISC-V core;
use the output to sanity-check a future MMIO sweep or the log decoder.

By default also writes a binary stream compatible with
scripts/fx_rt_log_to_ppm.py (header + sentinel + ARGB words) if --log is
set, so:  python3 scripts/fx_rt_log_to_ppm.py that.bin out.ppm
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
from pathlib import Path

SENTINEL = 0x5254524C
# Match fx_rt_cornell_smoke background; hit pixels are solid red.
BG = 0xFF101010
HIT = 0xFFFF0000  # ARGB: opaque red

# Equilateral triangle, centroid at (0, 0), circumradius = 1 (vertices on
# the unit circle; side length = sqrt(3)).  Angles: -90°, 30°, 150°.
_SQRT3 = math.sqrt(3.0)
_V0 = (0.0, -1.0)
_V1 = (_SQRT3 / 2.0, 0.5)
_V2 = (-_SQRT3 / 2.0, 0.5)
# Square frustum half-extent: vertices are within |y| <= 1, |x| <= sqrt(3)/2.
_TRI_HALF = 1.1


def _cross2(
    ax: float, ay: float, bx: float, by: float, cx: float, cy: float
) -> float:
    """2D cross of (B-A) and (C-A)."""
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


def _inside_equilateral(sx: float, sy: float) -> bool:
    """Point-in-triangle; boundary counts as inside (with epsilon)."""
    eps = 1e-9
    px, py = sx, sy
    x0, y0 = _V0
    x1, y1 = _V1
    x2, y2 = _V2
    d0 = _cross2(x0, y0, x1, y1, px, py)
    d1 = _cross2(x1, y1, x2, y2, px, py)
    d2 = _cross2(x2, y2, x0, y0, px, py)
    has_neg = (d0 < -eps) or (d1 < -eps) or (d2 < -eps)
    has_pos = (d0 > eps) or (d1 > eps) or (d2 > eps)
    return not (has_neg and has_pos)


def pixel_rgba32(px: int, py: int, w: int, h: int) -> int:
    # Normalized image coords: u left→right, v top→bottom (0 at top row).
    if w <= 1:
        u = 0.5
    else:
        u = px / (w - 1)
    if h <= 1:
        v = 0.5
    else:
        v = py / (h - 1)

    # Centered square in (sx, sy) around the triangle centroid (0, 0).
    s = _TRI_HALF
    sx = -s + u * (2.0 * s)
    sy = s - v * (2.0 * s)

    return HIT if _inside_equilateral(sx, sy) else BG


def write_p6(path: Path, w: int, h: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        fh.write(f"P6\n{w} {h}\n255\n".encode("ascii"))
        for py in range(h):
            for px in range(w):
                word = pixel_rgba32(px, py, w, h)
                r = (word >> 16) & 0xFF
                g = (word >> 8) & 0xFF
                b = word & 0xFF
                fh.write(bytes((r, g, b)))


def _write_render_log_impl(path: Path, width: int, height: int) -> None:
    out_words = []
    out_words.append((width << 16) | (height & 0xFFFF))
    out_words.append(SENTINEL)
    for py in range(height):
        for px in range(width):
            out_words.append(pixel_rgba32(px, py, width, height))
    data = b"".join(struct.pack("<I", x & 0xFFFFFFFF) for x in out_words)
    path.write_bytes(data)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument(
        "-o",
        "--out",
        type=Path,
        default=Path("scripts/out/triangle.ppm"),
        help="Output P6 PPM path (default: scripts/out/triangle.ppm).",
    )
    p.add_argument("--width", type=int, default=32, help="Image width (default: 32).")
    p.add_argument("--height", type=int, default=24, help="Image height (default: 24).")
    p.add_argument(
        "--log",
        type=Path,
        default=None,
        help="If set, also write render-log u32 .bin (for fx_rt_log_to_ppm.py).",
    )
    args = p.parse_args(argv if argv is not None else None)
    w, h = args.width, args.height
    if w < 1 or h < 1:
        p.error("width and height must be >= 1")
    write_p6(args.out, w, h)
    print(f"wrote {args.out} ({w}x{h})")
    if args.log is not None:
        _write_render_log_impl(args.log, w, h)
        print(f"wrote render log {args.log} ({(2 + w * h) * 4} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

