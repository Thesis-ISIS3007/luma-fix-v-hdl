#!/usr/bin/env python3.14
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "typer>=0.25.1",
#     "matplotlib>=3.10.9",
# ]
# ///
"""Plot microarchitecture stats from LUMAFIXV_UARCH_STATS JSON files.

Single JSON: two PNGs — stalls (`-o` path) and CUSTOM-0 ops (`<stem>_custom0_ops.png`).
Directory: IPC comparison across programs + dominant stall bucket.

Run: uv run scripts/plot_uarch_stats.py STATS.json -o out/uarch_stalls.png
"""

import json
from pathlib import Path

import matplotlib.pyplot as plt
import typer

STALL_METRIC_KEYS = (
    "raw_stall",
    "ex_busy",
    "fx_hold_fetch",
    "mem_stall",
    "pipe_stall",
    "flush",
)

METRIC_LABELS = {
    "raw_stall": "RAW",
    "ex_busy": "FXDIV",
    "fx_hold_fetch": "FX hold",
    "mem_stall": "MEM",
    "pipe_stall": "pipe",
    "flush": "flush",
}

CUSTOM0_OP_NAMES = (
    "FXADD",
    "FXSUB",
    "FXMUL",
    "FXNEG",
    "INT2FX",
    "FX2INT",
    "FXABS",
    "FXDIV",
)

MIN_STALL_FRAC = 0.005
MIN_OP_FRAC = 0.001

FIG_W = 7.5
FIG_ROW_H = 0.42
FIG_PAD_H = 1.0

app = typer.Typer(add_completion=False, no_args_is_help=True)


def normalize_stats(data: dict) -> dict:
    fr = dict(data.get("fractions", {}))
    ct = dict(data.get("counters", {}))
    if "raw_stall" not in fr and "load_hazard" in fr:
        fr["raw_stall"] = fr["load_hazard"]
    if "raw_stall" not in ct and "load_hazard" in ct:
        ct["raw_stall"] = ct["load_hazard"]
    return {**data, "fractions": fr, "counters": ct}


def load_stats(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    for key in ("program", "cycles", "retired", "ipc", "counters", "fractions"):
        if key not in data:
            typer.echo(f"missing key {key!r} in {path}", err=True)
            raise typer.Exit(1)
    return normalize_stats(data)


def collect_json_paths(input_path: Path) -> list[Path]:
    if input_path.is_dir():
        paths = sorted(input_path.glob("*.json"))
        if not paths:
            typer.echo(f"no JSON files in {input_path}", err=True)
            raise typer.Exit(1)
        return paths
    if not input_path.is_file():
        typer.echo(f"not found: {input_path}", err=True)
        raise typer.Exit(1)
    return [input_path]


def program_label(stats: dict) -> str:
    program = stats["program"]
    name = Path(program).name
    if name.endswith(".hex"):
        return name[: -len(".hex")]
    return name


def pct(frac: float) -> str:
    return f"{frac * 100:.1f}%"


def format_count(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.2f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}k"
    return str(n)


def custom0_ops_out_path(metrics_path: Path) -> Path:
    return metrics_path.with_name(f"{metrics_path.stem}_custom0_ops.png")


def resolve_op_fractions(stats: dict) -> dict[str, float]:
    cycles = stats["cycles"]
    op_fracs = dict(stats.get("custom0_op_fractions", {}))
    if not op_fracs and "custom0_ops" in stats:
        op_fracs = {
            name: (stats["custom0_ops"][name] / cycles if cycles else 0.0)
            for name in stats["custom0_ops"]
        }
    return op_fracs


def stall_items(stats: dict) -> list[tuple[str, float]]:
    fractions = stats["fractions"]
    items = [
        (METRIC_LABELS[k], fractions.get(k, 0.0))
        for k in STALL_METRIC_KEYS
        if fractions.get(k, 0.0) >= MIN_STALL_FRAC
    ]
    items.sort(key=lambda item: item[1], reverse=True)
    return items


def custom0_items(stats: dict) -> list[tuple[str, float]]:
    op_fracs = resolve_op_fractions(stats)
    items = [
        (name, op_fracs.get(name, 0.0))
        for name in CUSTOM0_OP_NAMES
        if op_fracs.get(name, 0.0) >= MIN_OP_FRAC
    ]
    items.sort(key=lambda item: item[1], reverse=True)
    return items


def stats_subtitle(stats: dict) -> str:
    custom0 = stats["fractions"].get("custom0_active", 0.0)
    return (
        f"{format_count(stats['cycles'])} cycles  ·  "
        f"IPC {stats['ipc']:.2f}  ·  "
        f"CUSTOM-0 {pct(custom0)}"
    )


def style_axes(ax) -> None:
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.grid(axis="x", alpha=0.25, linestyle=":", linewidth=0.8)
    ax.tick_params(axis="y", length=0, pad=6)
    ax.tick_params(axis="x", labelsize=9)


def plot_bars(
    ax,
    items: list[tuple[str, float]],
    *,
    total_cycles: int,
    empty_message: str,
) -> None:
    if not items:
        ax.text(0.5, 0.5, empty_message, ha="center", va="center", fontsize=10)
        ax.axis("off")
        return

    labels = [name for name, _ in items]
    values = [frac * 100 for _, frac in items]

    ax.barh(labels, values, height=0.58, edgecolor="none")
    xmax = max(max(values) * 1.32, 1.0)
    for y, value in enumerate(values):
        count = int(round(value / 100 * total_cycles))
        label = f"{value:.1f}% ({format_count(count)})"
        ax.text(value + xmax * 0.015, y, label, va="center", fontsize=8.5)

    ax.set_xlim(0, xmax)
    ax.invert_yaxis()
    ax.set_xlabel(f"% of {format_count(total_cycles)} cycles", fontsize=9)
    style_axes(ax)


def plot_panel(
    stats: dict,
    out_path: Path,
    *,
    panel: str,
    items_fn,
    empty_message: str,
) -> None:
    items = items_fn(stats)
    n = max(len(items), 1)
    fig, ax = plt.subplots(figsize=(FIG_W, FIG_PAD_H + FIG_ROW_H * n))

    plot_bars(
        ax,
        items,
        total_cycles=stats["cycles"],
        empty_message=empty_message,
    )
    plt.title(
        f"{program_label(stats)} — {panel}\n{stats_subtitle(stats)}",
        loc="left",
        fontsize=11,
        pad=12,
    )
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def plot_single(stats: dict, out_path: Path) -> list[Path]:
    plot_panel(
        stats,
        out_path,
        panel="Stalls",
        items_fn=stall_items,
        empty_message="no stall signals",
    )

    ops_path = custom0_ops_out_path(out_path)
    plot_panel(
        stats,
        ops_path,
        panel="CUSTOM-0 ops",
        items_fn=custom0_items,
        empty_message="no CUSTOM-0 op data",
    )

    return [out_path, ops_path]


def plot_multi(all_stats: list[dict], out_path: Path) -> None:
    labels = [program_label(s) for s in all_stats]
    ipcs = [s["ipc"] for s in all_stats]

    fig, (ax_ipc, ax_dom) = plt.subplots(2, 1, figsize=(max(7, len(labels) * 0.5), 6))

    x = range(len(labels))
    bars = ax_ipc.bar(x, ipcs, width=0.65)
    ax_ipc.set_xticks(list(x))
    ax_ipc.set_xticklabels(labels, rotation=40, ha="right", fontsize=8)
    ax_ipc.set_ylabel("IPC")
    ax_ipc.set_title("IPC", fontsize=10, loc="left")
    style_axes(ax_ipc)
    for bar, ipc_val in zip(bars, ipcs):
        ax_ipc.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height(),
            f"{ipc_val:.2f}",
            ha="center",
            va="bottom",
            fontsize=7,
        )

    dominant: list[str] = []
    dominant_frac: list[float] = []
    for stats in all_stats:
        fr = stats["fractions"]
        best_key = max(STALL_METRIC_KEYS, key=lambda k: fr.get(k, 0.0))
        best_val = fr.get(best_key, 0.0)
        if best_val < MIN_STALL_FRAC:
            dominant.append("—")
            dominant_frac.append(0.0)
        else:
            dominant.append(METRIC_LABELS[best_key])
            dominant_frac.append(best_val * 100)

    dom_bars = ax_dom.bar(x, dominant_frac, width=0.65)
    ax_dom.set_xticks(list(x))
    ax_dom.set_xticklabels(labels, rotation=40, ha="right", fontsize=8)
    ax_dom.set_ylabel("% cycles")
    ax_dom.set_title("Top stall", fontsize=10, loc="left")
    style_axes(ax_dom)
    for bar, value, dom in zip(dom_bars, dominant_frac, dominant):
        if value > 0:
            ax_dom.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height(),
                f"{dom} {value:.1f}%",
                ha="center",
                va="bottom",
                fontsize=6,
            )

    fig.tight_layout()
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)


@app.command()
def main(
    input_path: Path = typer.Argument(
        ...,
        exists=True,
        help="JSON stats file or directory of *.json files.",
    ),
    out_path: Path = typer.Option(
        ...,
        "--output",
        "-o",
        help="Stalls PNG path; CUSTOM-0 ops written as <stem>_custom0_ops.png.",
    ),
) -> None:
    paths = collect_json_paths(input_path)
    all_stats = [load_stats(p) for p in paths]

    out_path.parent.mkdir(parents=True, exist_ok=True)

    if len(all_stats) == 1:
        written = plot_single(all_stats[0], out_path)
    else:
        plot_multi(all_stats, out_path)
        written = [out_path]

    for path in written:
        typer.echo(f"Wrote {path}")


if __name__ == "__main__":
    app()
