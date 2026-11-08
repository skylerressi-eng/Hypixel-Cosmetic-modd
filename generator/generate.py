#!/usr/bin/env python3
"""Build catalog/cosmetics.json (every SkyBlock dye and rune) from verified public data.

Sources
  * NotEnoughUpdates-REPO (MIT, (c) Moulberry): constants/dyes.json for dye hex colours and
    animated-dye frames; items/DYE_*.json and items/*_RUNE;<level>.json for names, rarity,
    allowed slot and description.
  * Hypixel public API /v2/resources/skyblock/items: cross-check only. It does not list dyes
    or runes individually (runes are a generic RUNE / UNIQUE_RUNE item), so it cannot be the
    primary list; the generator records what it found there.

Nothing is invented. Anything that cannot be verified is written to MISSING_DATA.md.

Usage
  python3 generator/generate.py                 # clones NEU REPO into generator/.cache
  python3 generator/generate.py --neu PATH      # use an existing NEU REPO checkout
  python3 generator/generate.py --offline       # skip the Hypixel cross-check
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog" / "cosmetics.json"
MISSING = ROOT / "MISSING_DATA.md"
NEU_GIT = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO.git"
HYPIXEL_ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items"

SCHEMA_VERSION = 1
FORMAT = re.compile(r"§.")
HEX = re.compile(r"#([0-9A-Fa-f]{6})")
RARITIES = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL"]
ROMAN = {"I": 1, "II": 2, "III": 3}

# Lore line 2 of a rune ("§8Boots") -> canonical slot id used by the mod.
SLOT_LABELS = {
    "Helmet": "HELMET", "Helmets": "HELMET",
    "Chestplate": "CHESTPLATE", "Chestplates": "CHESTPLATE",
    "Leggings": "LEGGINGS",
    "Boots": "BOOTS",
    "Weapons": "WEAPON", "Bows": "BOW", "Axe": "AXE", "Axes": "AXE",
    "Farming Tools": "FARMING_TOOL", "Fishing Rods": "FISHING_ROD",
}
# Entries in NEU constants/dyes.json that are armor pieces, not dyes.
NOT_DYES = re.compile(r"^FAIRY_")


def strip(s: str) -> str:
    return FORMAT.sub("", s).strip()


def rarity_of(lore: list[str]) -> str | None:
    for line in reversed(lore):
        text = strip(line).upper()
        for r in sorted(RARITIES, key=len, reverse=True):
            if text.startswith(r + " "):
                return r
    return None


def leading_color(display: str) -> str | None:
    m = re.match(r"(?:§[k-or])*§([0-9a-f])", display)
    return m.group(1) if m else None


def ensure_neu(path: Path | None) -> tuple[Path, str]:
    if path is None:
        path = ROOT / "generator" / ".cache" / "NotEnoughUpdates-REPO"
        if not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "clone", "-q", "--depth", "1", NEU_GIT, str(path)], check=True)
        else:
            subprocess.run(["git", "-C", str(path), "pull", "-q", "--ff-only"], check=False)
    try:
        commit = subprocess.run(["git", "-C", str(path), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        commit = "unknown"
    return path, commit


def load_hypixel(offline: bool) -> dict | None:
    if offline:
        return None
    try:
        req = urllib.request.Request(HYPIXEL_ITEMS, headers={"User-Agent": "CosmeticStudio-generator"})
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)
    except Exception as e:  # cross-check is best effort
        print(f"warning: Hypixel items endpoint unavailable ({e}); continuing without cross-check", file=sys.stderr)
        return None


def build_dyes(neu: Path, missing: list[str]) -> list[dict]:
    consts = json.loads((neu / "constants" / "dyes.json").read_text())
    static: dict[str, str] = consts["static"]
    animated: dict[str, list[str]] = {k: v for k, v in consts["animated"].items() if not NOT_DYES.match(k)}
    item_ids = {p.stem for p in (neu / "items").glob("DYE_*.json")}
    dyes = []
    for dye_id in sorted(set(static) | set(animated) | item_ids):
        item = None
        f = neu / "items" / f"{dye_id}.json"
        if f.exists():
            item = json.loads(f.read_text())
        lore = item["lore"] if item else []
        lore_hex = next((m.group(0).upper() for line in lore for m in [HEX.search(strip(line))] if m), None)
        entry = {
            "id": dye_id,
            "name": strip(item["displayname"]) if item else None,
            "rarity": rarity_of(lore) if item else None,
            "animated": dye_id in animated,
        }
        if dye_id in animated:
            frames = [c.upper() for c in animated[dye_id]]
            entry["hex"] = lore_hex or frames[0]
            entry["frames"] = frames
            # Frame timing is not published anywhere we can verify; see MISSING_DATA.md.
            entry["frameTicks"] = 1
            entry["frameTicksVerified"] = False
        elif dye_id in static:
            entry["hex"] = static[dye_id].upper()
            if lore_hex and lore_hex != entry["hex"]:
                missing.append(f"- **{dye_id}**: hex conflict — constants/dyes.json says `{entry['hex']}`, item lore says `{lore_hex}`. Using constants value.")
        elif lore_hex:
            entry["hex"] = lore_hex
        else:
            entry["hex"] = None
            missing.append(f"- **{dye_id}**: no verified hex colour (item exists in NEU but has no colour in constants/dyes.json or its lore). Excluded from the catalog until filled in.")
        if entry["name"] is None:
            entry["name"] = " ".join(w.capitalize() for w in dye_id.removeprefix("DYE_").split("_")) + " Dye"
            missing.append(f"- **{dye_id}**: no NEU item file, so display name `{entry['name']}` is derived from the ID and rarity is unknown.")
        if entry["rarity"] is None and item:
            missing.append(f"- **{dye_id}**: rarity not found in lore.")
        if entry["hex"] is not None:
            dyes.append(entry)
    return dyes


def build_runes(neu: Path, missing: list[str]) -> list[dict]:
    runes: dict[str, dict] = {}
    for f in sorted((neu / "items").glob("*_RUNE;*.json")):
        rune_id, level = f.stem.split(";")
        item = json.loads(f.read_text())
        lore = item["lore"]
        display = strip(item["displayname"]).lstrip("◆").strip()
        name = re.sub(r"\s+(I|II|III)$", "", display)
        slot_label = strip(lore[1]) if len(lore) > 1 else ""
        slot = SLOT_LABELS.get(slot_label)
        # Description = lore lines between the slot line and the "Apply this rune..." line.
        desc, started = [], False
        for line in lore[2:]:
            t = strip(line)
            if t.startswith("Apply this rune") or t.startswith("Obtained"):
                break
            if t:
                desc.append(t)
                started = True
            elif started:
                break
        r = runes.setdefault(rune_id, {
            "id": rune_id,
            "name": name,
            "color": leading_color(item["displayname"].replace("§b◆ ", "", 1)) or leading_color(item["displayname"]),
            "slot": slot,
            "slotLabel": slot_label,
            "description": " ".join(desc),
            "levels": [],
        })
        r["levels"].append({"level": int(level), "rarity": rarity_of(lore), "requiredLevel": int(re.sub(r"\D", "", strip(lore[0])) or 0)})
        if slot is None:
            missing.append(f"- **{rune_id}**: unrecognised slot label `{slot_label}`.")
    out = []
    for r in sorted(runes.values(), key=lambda x: x["id"]):
        r["levels"].sort(key=lambda l: l["level"])
        r["unique"] = len(r["levels"]) == 1
        out.append(r)
    return out


# Legacy (1.8) Hypixel material names -> modern item ids, for armor appearances.
LEGACY_MATERIALS = {
    "LEATHER_HELMET": "minecraft:leather_helmet", "LEATHER_CHESTPLATE": "minecraft:leather_chestplate",
    "LEATHER_LEGGINGS": "minecraft:leather_leggings", "LEATHER_BOOTS": "minecraft:leather_boots",
    "IRON_HELMET": "minecraft:iron_helmet", "IRON_CHESTPLATE": "minecraft:iron_chestplate",
    "IRON_LEGGINGS": "minecraft:iron_leggings", "IRON_BOOTS": "minecraft:iron_boots",
    "GOLD_HELMET": "minecraft:golden_helmet", "GOLD_CHESTPLATE": "minecraft:golden_chestplate",
    "GOLD_LEGGINGS": "minecraft:golden_leggings", "GOLD_BOOTS": "minecraft:golden_boots",
    "DIAMOND_HELMET": "minecraft:diamond_helmet", "DIAMOND_CHESTPLATE": "minecraft:diamond_chestplate",
    "DIAMOND_LEGGINGS": "minecraft:diamond_leggings", "DIAMOND_BOOTS": "minecraft:diamond_boots",
    "CHAINMAIL_HELMET": "minecraft:chainmail_helmet", "CHAINMAIL_CHESTPLATE": "minecraft:chainmail_chestplate",
    "CHAINMAIL_LEGGINGS": "minecraft:chainmail_leggings", "CHAINMAIL_BOOTS": "minecraft:chainmail_boots",
    "SEA_LANTERN": "minecraft:sea_lantern", "SPONGE": "minecraft:sponge", "PACKED_ICE": "minecraft:packed_ice",
    "HAY_BLOCK": "minecraft:hay_block", "LEAVES": "minecraft:oak_leaves",
}
LEGACY_RED = {"CARPET": "minecraft:red_carpet", "STAINED_CLAY": "minecraft:red_terracotta", "STAINED_GLASS": "minecraft:red_stained_glass"}
SKULLS = {0: "minecraft:skeleton_skull", 1: "minecraft:wither_skeleton_skull", 2: "minecraft:zombie_head", 3: "minecraft:player_head", 4: "minecraft:creeper_head"}


def build_appearances(hypixel: dict | None, missing: list[str]) -> list[dict]:
    """Every SkyBlock armor piece's look (material + leather colour or skull texture), from Hypixel's item list."""
    out = []
    if not hypixel:
        missing.append("- Armor appearances: Hypixel items endpoint unavailable; appearances.json not updated.")
        return out
    for item in hypixel.get("items", []):
        slot = item.get("category")
        if slot not in ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"):
            continue
        mat, dur = item.get("material"), item.get("durability", 0)
        if mat == "SKULL_ITEM":
            model = SKULLS.get(dur or 0)
        elif mat in LEGACY_RED and dur == 14:
            model = LEGACY_RED[mat]
        else:
            model = LEGACY_MATERIALS.get(mat)
        if model is None:
            missing.append(f"- **{item['id']}**: unknown armor material `{mat}:{dur}`; not offered as an appearance.")
            continue
        entry = {"id": item["id"], "name": item.get("name", item["id"]), "slot": slot, "rarity": item.get("tier"), "item": model}
        if "color" in item:
            r, g, b = (int(x) for x in str(item["color"]).split(","))
            entry["color"] = f"#{r:02X}{g:02X}{b:02X}"
        skin = item.get("skin")
        if model == "minecraft:player_head":
            if isinstance(skin, dict) and skin.get("value"):
                entry["texture"] = skin["value"]
            else:
                missing.append(f"- **{item['id']}**: player-head helmet without a texture in Hypixel's data.")
        out.append(entry)
    out.sort(key=lambda e: (e["slot"], e["name"]))
    return out


def write_missing(missing: list[str], dyes: list[dict], runes: list[dict]) -> None:
    animated = [d for d in dyes if d["animated"]]
    lines = [
        "# Missing / unverified data",
        "",
        "Generated by `generator/generate.py`. Nothing here was guessed into the catalog. Fill in what you can",
        "verify in game and the generator/mod will pick it up (rune effects go in `catalog/rune_effects.json`).",
        "",
        "## Animated dye timing",
        "",
        f"NEU REPO provides the colour frames for all {len(animated)} animated dyes, but not how fast Hypixel plays",
        "them. The catalog uses **1 frame per game tick** (`frameTicks: 1`, `frameTicksVerified: false`).",
        "To verify: record a piece wearing the dye and count how long one full cycle takes.",
        "",
        "| Dye | Frames | Cycle at 1 frame/tick | Verified frameTicks |",
        "|---|---|---|---|",
    ]
    lines += [f"| {d['name']} (`{d['id']}`) | {len(d['frames'])} | {len(d['frames']) / 20:.1f}s | |" for d in animated]
    lines += [
        "",
        "## Rune particle effects",
        "",
        "No public dataset describes what each rune's particles look like (type, shape, colour, rate). NEU REPO",
        "only has the in-game description, listed below. Until an entry is added to `catalog/rune_effects.json`",
        "with `\"verified\": true`, the mod shows the rune as **effect data missing**. An opt-in config toggle",
        "(\"Show unverified rune effects\", off by default) displays the keyword-based approximations from",
        "`generator/approximate_rune_effects.py`, which are labelled as approximate in the GUI.",
        "",
        "| Rune | Slot | In-game description | Current approximation (unverified) | Verified effect |",
        "|---|---|---|---|---|",
    ]
    approx = {}
    effects_file = ROOT / "catalog" / "rune_effects.json"
    if effects_file.exists():
        for rid, eff in json.loads(effects_file.read_text()).get("effects", {}).items():
            if not eff.get("verified"):
                em = eff["emitters"][0]
                approx[rid] = f"{em['particle'].removeprefix('minecraft:')}{' ' + em['color'] if em.get('color') else ''}, {em['shape'].lower()}, {em['trigger'].lower()}"
    lines += [f"| {r['name']} (`{r['id']}`) | {r['slotLabel']} | {r['description']} | {approx.get(r['id'], 'none')} | |" for r in runes]
    lines += ["", "## Other gaps found while generating", ""]
    lines += missing or ["- None."]
    MISSING.write_text("\n".join(lines) + "\n")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--neu", type=Path, help="existing NotEnoughUpdates-REPO checkout")
    ap.add_argument("--offline", action="store_true", help="skip Hypixel cross-check")
    ap.add_argument("--hypixel-file", type=Path, help="use a saved copy of the Hypixel items response")
    args = ap.parse_args()

    neu, commit = ensure_neu(args.neu)
    missing: list[str] = []
    dyes = build_dyes(neu, missing)
    runes = build_runes(neu, missing)

    hypixel = load_hypixel(args.offline) if not args.hypixel_file else json.loads(args.hypixel_file.read_text())
    hypixel_note = None
    if hypixel:
        ids = {i["id"] for i in hypixel.get("items", [])}
        hypixel_note = {
            "lastUpdated": hypixel.get("lastUpdated"),
            "itemCount": len(ids),
            "dyesListed": sorted(i for i in ids if i.startswith("DYE_")),
            "runeItemsListed": sorted(i for i in ids if i in ("RUNE", "UNIQUE_RUNE")),
        }

    body = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "sources": {
            "neuRepo": {"url": "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO", "commit": commit, "license": "MIT (c) Moulberry"},
            "hypixel": hypixel_note,
        },
        "dyes": dyes,
        "runes": runes,
    }
    # Content hash (excluding generatedAt) lets the mod skip refreshes when nothing changed.
    digest_src = json.dumps({k: v for k, v in body.items() if k != "generatedAt"}, sort_keys=True).encode()
    body["contentHash"] = hashlib.sha256(digest_src).hexdigest()

    appearances = build_appearances(hypixel, missing)
    if appearances:
        (ROOT / "catalog" / "appearances.json").write_text(json.dumps({"schemaVersion": 1, "source": "Hypixel /v2/resources/skyblock/items",
            "appearances": appearances}, indent=1, ensure_ascii=False) + "\n")

    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    CATALOG.write_text(json.dumps(body, indent=1, ensure_ascii=False) + "\n")
    write_missing(missing, dyes, runes)
    print(f"appearances: {len(appearances)}")
    print(f"wrote {CATALOG.relative_to(ROOT)}: {len(dyes)} dyes ({sum(d['animated'] for d in dyes)} animated), "
          f"{len(runes)} runes ({sum(len(r['levels']) for r in runes)} levels); {len(missing)} gaps -> MISSING_DATA.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
