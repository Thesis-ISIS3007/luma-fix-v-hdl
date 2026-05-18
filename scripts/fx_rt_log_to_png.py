#!/usr/bin/env python3.14
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "typer>=0.25.0",
#     "pillow>=12.2.0",
# ]
# ///
"""FX32 ray-tracer MMIO log -> 8-bit RGB PNG.

Word stream (little-endian u32): (w<<16)|h, sentinel 0x5254524C, then w*h ARGB
pixels (alpha ignored).

Run: uv run scripts/fx_rt_log_to_png.py LOG.bin OUT.png (PEP 723 installs deps).
"""

import struct
from pathlib import Path

import typer
from PIL import Image

SENTINEL = 0x5254524C

app = typer.Typer(add_completion=False, no_args_is_help=True)


@app.command()
def main(
    log_path: Path = typer.Argument(..., exists=True, dir_okay=False, readable=True),
    out_path: Path = typer.Argument(..., help="Output .png path."),
) -> None:
    data = log_path.read_bytes()
    if len(data) < 8 or len(data) % 4:
        typer.echo(f"invalid log size: {len(data)} bytes", err=True)
        raise typer.Exit(1)
    words = struct.unpack(f"<{len(data) // 4}I", data)
    if words[1] != SENTINEL:
        typer.echo(
            f"sentinel: want 0x{SENTINEL:08X}, got 0x{words[1]:08X}", err=True
        )
        raise typer.Exit(1)
    w, h = (words[0] >> 16) & 0xFFFF, words[0] & 0xFFFF
    need, got = w * h, len(words) - 2
    if got != need:
        typer.echo(
            f"warning: expected {need} pixels, log has {got} (pad/truncate)",
            err=True,
        )
    n = min(got, need)
    rgb = bytearray(3 * need)
    for i in range(n):
        v = words[2 + i]
        o = 3 * i
        rgb[o] = (v >> 16) & 0xFF
        rgb[o + 1] = (v >> 8) & 0xFF
        rgb[o + 2] = v & 0xFF
    img = Image.frombytes("RGB", (w, h), bytes(rgb), "raw", "RGB")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path, format="PNG")
    typer.echo(f"wrote {out_path} ({w}x{h})")


if __name__ == "__main__":
    app()
