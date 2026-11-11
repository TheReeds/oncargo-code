#!/usr/bin/env python3
"""Compatibilidad de contrato OpenAPI (solo stdlib).

Uso: python3 scripts/openapi-compat.py <base.json> <head.json>

Compara semánticamente (el orden de claves no importa) el contrato base contra el de la PR.
Exit 1 y lista de problemas si hay cambios incompatibles; las adiciones se informan y no fallan.
"""
import json
import sys

METHODS = ("get", "put", "post", "delete", "options", "head", "patch", "trace")


class Ctx:
    def __init__(self, doc):
        self.doc = doc

    def deref(self, node, seen=()):
        while isinstance(node, dict) and "$ref" in node and node["$ref"] not in seen:
            ref = node["$ref"]
            seen = seen + (ref,)
            cur = self.doc
            for part in ref.lstrip("#/").split("/"):
                cur = cur.get(part.replace("~1", "/").replace("~0", "~"), {}) if isinstance(cur, dict) else {}
            node = cur
        return node

    def flatten(self, schema, depth=0):
        """Resuelve allOf de forma simple: fusiona properties/required; no sigue $ref de propiedades."""
        if not isinstance(schema, dict) or depth > 10:
            return schema if isinstance(schema, dict) else {}
        out = {k: v for k, v in schema.items() if k != "allOf"}
        for part in schema.get("allOf", []):
            part = self.flatten(self.deref(part), depth + 1)
            for k, v in part.items():
                if k == "properties":
                    out.setdefault("properties", {}).update(v)
                elif k == "required":
                    out["required"] = sorted(set(out.get("required", [])) | set(v))
                else:
                    out.setdefault(k, v)
        return out


def prop_sig(p):
    """Firma comparable de una propiedad: $ref, type, format, items."""
    if not isinstance(p, dict):
        return None
    sig = {k: p.get(k) for k in ("$ref", "type", "format")}
    if "items" in p:
        sig["items"] = prop_sig(p["items"])
    if "allOf" in p:
        sig["allOf"] = [prop_sig(x) for x in p["allOf"]]
    return sig


def compare_schema(path, b, h, bc, hc, breaking, info):
    b, h = bc.flatten(b), hc.flatten(h)
    bp, hp = b.get("properties", {}), h.get("properties", {})
    for name in bp:
        if name not in hp:
            breaking.append(f"{path}: se quitó la propiedad '{name}'")
            continue
        if prop_sig(bp[name]) != prop_sig(hp[name]):
            breaking.append(f"{path}.{name}: cambió el tipo/formato/$ref ({prop_sig(bp[name])} -> {prop_sig(hp[name])})")
        be, he = bp[name].get("enum"), hp[name].get("enum")
        if be is not None and he is not None:
            compare_enum(f"{path}.{name}", be, he, breaking, info)
        if isinstance(bp[name], dict) and isinstance(hp[name], dict) and "properties" in bp[name]:
            compare_schema(f"{path}.{name}", bp[name], hp[name], bc, hc, breaking, info)
    for name in hp:
        if name not in bp:
            info.append(f"{path}: propiedad nueva '{name}'")
    for r in sorted(set(h.get("required", [])) - set(b.get("required", []))):
        breaking.append(f"{path}: nuevo campo requerido '{r}'")
    if "enum" in b and "enum" in h:
        compare_enum(path, b["enum"], h["enum"], breaking, info)
    if "enum" in b and "enum" not in h:
        info.append(f"{path}: se relajó el enum")
    if "enum" not in b and "enum" in h:
        breaking.append(f"{path}: se agregó una restricción enum")
    if b.get("type") != h.get("type") and "properties" not in b:
        breaking.append(f"{path}: cambió el tipo ({b.get('type')} -> {h.get('type')})")


def compare_enum(path, be, he, breaking, info):
    for v in be:
        if v not in he:
            breaking.append(f"{path}: se quitó el valor de enum '{v}'")
    for v in he:
        if v not in be:
            info.append(f"{path}: valor de enum nuevo '{v}'")


def params_of(op, path_item, ctx):
    out = {}
    for p in list(path_item.get("parameters", [])) + list(op.get("parameters", [])):
        p = ctx.deref(p)
        out[(p.get("in"), p.get("name"))] = p
    return out


def compare(base, head):
    breaking, info = [], []
    bc, hc = Ctx(base), Ctx(head)
    bpaths, hpaths = base.get("paths", {}), head.get("paths", {})
    for path, bitem in bpaths.items():
        if path not in hpaths:
            breaking.append(f"se quitó el path {path}")
            continue
        hitem = hpaths[path]
        for m in METHODS:
            if m in bitem and m not in hitem:
                breaking.append(f"{m.upper()} {path}: se quitó la operación")
            elif m in hitem and m not in bitem:
                info.append(f"{m.upper()} {path}: operación nueva")
            elif m in bitem:
                bp, hp = params_of(bitem[m], bitem, bc), params_of(hitem[m], hitem, hc)
                label = f"{m.upper()} {path}"
                for k, p in bp.items():
                    if k not in hp:
                        breaking.append(f"{label}: se quitó el parámetro {k[0]} '{k[1]}'")
                    else:
                        if hp[k].get("required") and not p.get("required"):
                            breaking.append(f"{label}: el parámetro '{k[1]}' pasó a requerido")
                        if prop_sig(bc.deref(p.get("schema", {}))) != prop_sig(hc.deref(hp[k].get("schema", {}))):
                            breaking.append(f"{label}: el parámetro '{k[1]}' cambió de tipo/formato")
                        be, he = bc.deref(p.get("schema", {})).get("enum"), hc.deref(hp[k].get("schema", {})).get("enum")
                        if be is not None and he is not None:
                            compare_enum(f"{label} parámetro '{k[1]}'", be, he, breaking, info)
                for k, p in hp.items():
                    if k not in bp:
                        (breaking if p.get("required") else info).append(
                            f"{label}: parámetro {'requerido ' if p.get('required') else ''}nuevo {k[0]} '{k[1]}'"
                        )
    for path in hpaths:
        if path not in bpaths:
            info.append(f"path nuevo {path}")
    bs = base.get("components", {}).get("schemas", {})
    hs = head.get("components", {}).get("schemas", {})
    for name, schema in bs.items():
        if name not in hs:
            breaking.append(f"schema '{name}': eliminado")
        else:
            compare_schema(f"schema '{name}'", schema, hs[name], bc, hc, breaking, info)
    for name in hs:
        if name not in bs:
            info.append(f"schema nuevo '{name}'")
    return breaking, info


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    with open(argv[1], encoding="utf-8") as f:
        base = json.load(f)
    with open(argv[2], encoding="utf-8") as f:
        head = json.load(f)
    breaking, info = compare(base, head)
    for i in info:
        print(f"[info] {i}")
    for b in breaking:
        print(f"[INCOMPATIBLE] {b}")
    if breaking:
        print(f"\n{len(breaking)} cambio(s) incompatible(s).")
        return 1
    print("Contrato compatible.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
