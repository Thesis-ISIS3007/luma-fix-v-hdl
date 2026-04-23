#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "typer",
# ]
# ///
"""Convert a raw binary produced by objcopy into a $readmemh-compatible hex file.

Each 4-byte chunk of the input is emitted as 8 lowercase hex digits
(little-endian interpretation, matching the target RV32 word order) on
its own line.
"""

from pathlib import Path

import typer

app = typer.Typer()


@app.command()
def main(raw_bin: Path, out_hex: Path) -> None:
    """Emit one little-endian 32-bit word per line as 8 lowercase hex digits."""
    data = raw_bin.read_bytes()
    if len(data) % 4 != 0:
        data += b"\x00" * (4 - len(data) % 4)
    lines = [
        f"{int.from_bytes(data[i : i + 4], 'little'):08x}"
        for i in range(0, len(data), 4)
    ]
    out_hex.write_text("\n".join(lines) + "\n")
    typer.echo(f"Generated {out_hex}")


if __name__ == "__main__":
    app()
