#!/usr/bin/env python3
"""Bake a glTF scene into a static C header for the FX32 ray tracer.

The output header declares three arrays consumable by the freestanding C
program:

    static const fx_triangle_t g_prims[SCENE_NUM_PRIMS];
    static const unsigned char g_matIdx[SCENE_NUM_PRIMS];
    static const fx_vec3_t     g_matColor[SCENE_NUM_MATS];

Vertex positions are baked into world space by composing every node's
local matrix down the scene graph. Float values are written as integer
literals in 16Q16 form so the freestanding build never needs to parse a
float at runtime.
"""

import argparse
import json
import math
import struct
import sys
from pathlib import Path


# Mirrors validation/rt/fx_math.h.
FX_FRAC_BITS = 16
FX_ONE = 1 << FX_FRAC_BITS
FX_INT_MAX = 0x7FFFFFFF
FX_INT_MIN = -0x80000000


def fx_from_double(v: float) -> int:
    """Round-half-away-from-zero into 16Q16 with saturation."""
    scaled = v * FX_ONE
    rounded = math.floor(scaled + 0.5) if scaled >= 0 else -math.floor(-scaled + 0.5)
    if rounded > FX_INT_MAX:
        return FX_INT_MAX
    if rounded < FX_INT_MIN:
        return FX_INT_MIN
    return int(rounded)


# componentType values from the glTF 2.0 spec.
COMPONENT_FORMATS = {
    5120: ("b", 1),
    5121: ("B", 1),
    5122: ("h", 2),
    5123: ("H", 2),
    5125: ("I", 4),
    5126: ("f", 4),
}

TYPE_COMPONENTS = {
    "SCALAR": 1,
    "VEC2": 2,
    "VEC3": 3,
    "VEC4": 4,
    "MAT4": 16,
}


def load_accessor(gltf, buffers, accessor_idx):
    accessor = gltf["accessors"][accessor_idx]
    view = gltf["bufferViews"][accessor["bufferView"]]
    buf = buffers[view["buffer"]]
    fmt_char, comp_size = COMPONENT_FORMATS[accessor["componentType"]]
    components = TYPE_COMPONENTS[accessor["type"]]
    elem_size = comp_size * components
    stride = view.get("byteStride") or elem_size
    base = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    count = accessor["count"]
    out = []
    for i in range(count):
        offset = base + i * stride
        chunk = struct.unpack_from("<" + fmt_char * components, buf, offset)
        out.append(chunk if components > 1 else chunk[0])
    return out


def mat4_identity():
    return [1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0]


def mat4_mul(a, b):
    """Column-major 4x4 multiply matching glTF's storage convention."""
    out = [0.0] * 16
    for col in range(4):
        for row in range(4):
            s = 0.0
            for k in range(4):
                s += a[row + k * 4] * b[k + col * 4]
            out[row + col * 4] = s
    return out


def node_local_matrix(node):
    if "matrix" in node:
        return list(node["matrix"])
    # TRS path: T * R * S, matching glTF semantics. Cornell uses neither
    # here, but we support it so other scenes drop in cleanly.
    t = node.get("translation", [0.0, 0.0, 0.0])
    r = node.get("rotation", [0.0, 0.0, 0.0, 1.0])
    s = node.get("scale", [1.0, 1.0, 1.0])
    x, y, z, w = r
    xx, yy, zz = x * x, y * y, z * z
    xy, xz, yz = x * y, x * z, y * z
    wx, wy, wz = w * x, w * y, w * z
    rot = [
        1 - 2 * (yy + zz), 2 * (xy + wz),     2 * (xz - wy),     0.0,
        2 * (xy - wz),     1 - 2 * (xx + zz), 2 * (yz + wx),     0.0,
        2 * (xz + wy),     2 * (yz - wx),     1 - 2 * (xx + yy), 0.0,
        0.0,               0.0,               0.0,               1.0,
    ]
    scale = [
        s[0], 0.0,  0.0,  0.0,
        0.0,  s[1], 0.0,  0.0,
        0.0,  0.0,  s[2], 0.0,
        0.0,  0.0,  0.0,  1.0,
    ]
    rs = mat4_mul(rot, scale)
    rs[12] = t[0]
    rs[13] = t[1]
    rs[14] = t[2]
    return rs


def transform_point(m, p):
    x, y, z = p
    return (
        m[0] * x + m[4] * y + m[8]  * z + m[12],
        m[1] * x + m[5] * y + m[9]  * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14],
    )


def collect_triangles(gltf, buffers):
    """Walk the scene graph and emit (v0, v1, v2, mat_idx) tuples."""
    scene = gltf["scenes"][gltf.get("scene", 0)]
    triangles = []

    def visit(node_idx, parent_mat):
        node = gltf["nodes"][node_idx]
        local = node_local_matrix(node)
        world = mat4_mul(parent_mat, local)
        if "mesh" in node:
            mesh = gltf["meshes"][node["mesh"]]
            for prim in mesh["primitives"]:
                positions = load_accessor(gltf, buffers, prim["attributes"]["POSITION"])
                if "indices" in prim:
                    indices = load_accessor(gltf, buffers, prim["indices"])
                else:
                    indices = list(range(len(positions)))
                mat_idx = prim.get("material", 0)
                for i in range(0, len(indices), 3):
                    v0 = transform_point(world, positions[indices[i]])
                    v1 = transform_point(world, positions[indices[i + 1]])
                    v2 = transform_point(world, positions[indices[i + 2]])
                    triangles.append((v0, v1, v2, mat_idx))
        for child in node.get("children", []):
            visit(child, world)

    for root in scene["nodes"]:
        visit(root, mat4_identity())
    return triangles


def fx_literal(v):
    n = fx_from_double(v)
    return f"{n:d}"  # signed decimal; freestanding build doesn't care about sign


def emit_header(triangles, materials, scene_name):
    n_prims = len(triangles)
    n_mats = len(materials)
    lines = []
    lines.append("// AUTOGENERATED by scripts/fx_gltf_to_c_scene.py - DO NOT EDIT.")
    lines.append(f"// Source scene: {scene_name}")
    lines.append("// Vertex positions baked into world space; values stored in 16Q16.")
    lines.append("")
    lines.append("#ifndef SCENE_CORNELL_H")
    lines.append("#define SCENE_CORNELL_H")
    lines.append("")
    lines.append('#include "fx_triangle.h"')
    lines.append('#include "fx_vec3.h"')
    lines.append("")
    lines.append(f"#define SCENE_NUM_PRIMS {n_prims}")
    lines.append(f"#define SCENE_NUM_MATS  {n_mats}")
    lines.append("")
    lines.append("static const fx_triangle_t g_prims[SCENE_NUM_PRIMS] = {")
    for v0, v1, v2, _mat in triangles:
        # fx_triangle_t = { fx_vec3_t v0, v1, v2 }, so one outer brace per
        # triangle plus one inner brace per vec3 (NOT three nested braces).
        verts = ", ".join(
            f"{{{fx_literal(v[0])}, {fx_literal(v[1])}, {fx_literal(v[2])}}}"
            for v in (v0, v1, v2)
        )
        lines.append(f"  {{{verts}}},")
    lines.append("};")
    lines.append("")
    lines.append("static const unsigned char g_matIdx[SCENE_NUM_PRIMS] = {")
    row = []
    for _, _, _, mat in triangles:
        row.append(f"{mat}")
        if len(row) == 8:
            lines.append("  " + ", ".join(row) + ",")
            row = []
    if row:
        lines.append("  " + ", ".join(row) + ",")
    lines.append("};")
    lines.append("")
    lines.append("static const fx_vec3_t g_matColor[SCENE_NUM_MATS] = {")
    for name, (r, g, b) in materials:
        lines.append(
            f"  {{{fx_literal(r)}, {fx_literal(g)}, {fx_literal(b)}}}, // {name}"
        )
    lines.append("};")
    lines.append("")
    lines.append("#endif  // SCENE_CORNELL_H")
    return "\n".join(lines) + "\n"


def collect_materials(gltf):
    out = []
    for mat in gltf.get("materials", []):
        name = mat.get("name", "unnamed")
        pbr = mat.get("pbrMetallicRoughness", {})
        base = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])
        out.append((name, (base[0], base[1], base[2])))
    return out


def load_buffers(gltf, gltf_path: Path):
    buffers = []
    for buf in gltf["buffers"]:
        uri = buf.get("uri")
        if uri is None or uri.startswith("data:"):
            raise SystemExit("Embedded / data-URI buffers are not supported by this baker.")
        buffers.append((gltf_path.parent / uri).read_bytes())
    return buffers


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("gltf_path", type=Path, help="Path to the .gltf file.")
    parser.add_argument(
        "-o", "--out", dest="out_path", type=Path, required=True,
        help="Destination C header path.",
    )
    args = parser.parse_args(argv)
    if not args.gltf_path.is_file():
        parser.error(f"glTF file not found: {args.gltf_path}")

    gltf = json.loads(args.gltf_path.read_text())
    buffers = load_buffers(gltf, args.gltf_path)
    triangles = collect_triangles(gltf, buffers)
    materials = collect_materials(gltf)
    if not triangles:
        parser.error("Scene contains no triangles.")
    if not materials:
        materials = [("default", (1.0, 1.0, 1.0))]

    header = emit_header(triangles, materials, args.gltf_path.as_posix())
    args.out_path.parent.mkdir(parents=True, exist_ok=True)
    args.out_path.write_text(header)
    print(
        f"Baked {len(triangles)} triangles, {len(materials)} materials -> {args.out_path}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
