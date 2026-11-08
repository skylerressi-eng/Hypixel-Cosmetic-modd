#!/usr/bin/env python3
"""Builds catalog/registry_ids.json: every block and entity id of the supported Minecraft versions.

The sync server uses this as its allowlist for pet ids (it only accepts known ids). Ids come straight from the
official client jars: blocks = assets/minecraft/blockstates/<id>.json, entities = "entity.minecraft.<id>" keys in
assets/minecraft/lang/en_us.json. The union over all supported versions is written.

Usage
  python3 generator/registry_ids.py                    # download client jars for versions/*.properties via piston-meta
  python3 generator/registry_ids.py --jar a.jar --jar b.jar
"""
from __future__ import annotations

import argparse
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "catalog" / "registry_ids.json"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ID = re.compile(r"^[a-z0-9_]+$")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "CosmeticStudio-generator"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def client_jar(version: str) -> bytes:
    manifest = json.loads(fetch(MANIFEST))
    entry = next(v for v in manifest["versions"] if v["id"] == version)
    meta = json.loads(fetch(entry["url"]))
    return fetch(meta["downloads"]["client"]["url"])


def ids_from_jar(data: bytes) -> tuple[set[str], set[str], set[str]]:
    blocks, entities, items = set(), set(), set()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            m = re.fullmatch(r"assets/minecraft/blockstates/([a-z0-9_]+)\.json", name)
            if m:
                blocks.add("minecraft:" + m.group(1))
        lang = json.loads(z.read("assets/minecraft/lang/en_us.json"))
        for key in lang:
            if key.startswith("item.minecraft.") and ID.match(key[len("item.minecraft."):]):
                items.add("minecraft:" + key[len("item.minecraft."):])
            if key.startswith("entity.minecraft."):
                rest = key[len("entity.minecraft."):]
                if ID.match(rest):
                    entities.add("minecraft:" + rest)
    return blocks, entities, items | blocks


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", action="append", type=Path, help="Minecraft client jar (repeatable)")
    args = ap.parse_args()
    sources: list[tuple[str, bytes]] = []
    if args.jar:
        sources = [(p.name, p.read_bytes()) for p in args.jar]
    else:
        for props in sorted((ROOT / "versions").glob("*.properties")):
            version = props.stem
            sources.append((version, client_jar(version)))
    blocks: set[str] = set()
    entities: set[str] = set()
    items: set[str] = set()
    for label, data in sources:
        b, e, i = ids_from_jar(data)
        print(f"{label}: {len(b)} blocks, {len(e)} entities, {len(i)} items")
        blocks |= b
        entities |= e
        items |= i
    OUT.write_text(json.dumps({"blocks": sorted(blocks), "entities": sorted(entities), "items": sorted(items)}, indent=1) + "\n")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(blocks)} blocks, {len(entities)} entities")


if __name__ == "__main__":
    main()
