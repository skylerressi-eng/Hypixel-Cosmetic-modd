#!/usr/bin/env python3
"""Writes catalog/rune_effects.json: APPROXIMATE (unverified) particle effects for runes.

No public source documents what each rune's particles look like, so these entries are derived mechanically from
each rune's name and in-game description using the keyword rules below. Every generated entry has
"verified": false and "source": "name-inferred"; the mod only shows them when the user turns on
"Show unverified rune effects" (off by default), and labels them as approximate in the GUI.

To record a verified effect, add it to config/cosmeticstudio/rune_effects.json in game (local override) or edit
VERIFIED below and re-run; verified entries are never overwritten by the rules.

Only particles that exist in every supported Minecraft version are used.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog" / "cosmetics.json"
OUT = ROOT / "catalog" / "rune_effects.json"

# Effects confirmed in game: rune id -> effect dict (verified: true). Empty until someone verifies them.
VERIFIED: dict[str, dict] = {}

# (keywords, emitter template). First matching rule wins; keywords are matched against "id name description".
RULES: list[tuple[tuple[str, ...], dict]] = [
    (("rainbow",), {"particle": "minecraft:dust", "color": "rainbow", "count": 2}),
    (("music", "tunes", "meow"), {"particle": "minecraft:note", "count": 1}),
    (("heart", "smitten"), {"particle": "minecraft:heart", "count": 1}),
    (("lavatears",), {"particle": "minecraft:dripping_lava", "count": 1}),
    (("lava", "fiery", "searing", "blazing", "fire", "flame", "sun"), {"particle": "minecraft:flame", "count": 2}),
    (("ice", "freezing", "snow", "frost", "skates"), {"particle": "minecraft:snowflake", "count": 2}),
    (("zap", "lightning"), {"particle": "minecraft:electric_spark", "count": 3}),
    (("smokey", "darkness"), {"particle": "minecraft:large_smoke", "count": 1}),
    (("rainy",), {"particle": "minecraft:falling_water", "count": 3, "shape": "RAIN"}),
    (("clouds", "cloud"), {"particle": "minecraft:cloud", "count": 1}),
    (("enchant", "spellbound"), {"particle": "minecraft:enchant", "count": 4}),
    (("magic",), {"particle": "minecraft:witch", "count": 2}),
    (("golden",), {"particle": "minecraft:dust", "color": "#FFD700", "count": 2}),
    (("gem", "sparkling"), {"particle": "minecraft:end_rod", "count": 1}),
    (("blue",), {"particle": "minecraft:dust", "color": "#3C78FF", "count": 2}),
    (("green",), {"particle": "minecraft:dust", "color": "#3CFF5A", "count": 2}),
    (("white",), {"particle": "minecraft:dust", "color": "#FFFFFF", "count": 2}),
    (("blood",), {"particle": "minecraft:dust", "color": "#8A0303", "count": 4}),
    (("redstone",), {"particle": "minecraft:dust", "color": "#FF0000", "count": 3}),
    (("slimy",), {"particle": "minecraft:item_slime", "count": 2}),
    (("endersnake", "dragon"), {"particle": "minecraft:reverse_portal", "count": 3}),
    (("spirit", "soultwist", "soul"), {"particle": "minecraft:soul", "count": 1}),
    (("tidal", "wake"), {"particle": "minecraft:splash", "count": 4}),
    (("blooming", "flower", "gardening", "leafing", "leaf", "leaves", "ornament", "peafowl", "petal"), {"particle": "minecraft:cherry_leaves", "count": 1}),
    (("pumpkin",), {"particle": "minecraft:dust", "color": "#FF8C00", "count": 2}),
    (("jerry",), {"particle": "minecraft:happy_villager", "count": 2}),
    (("explosi", "burst"), {"particle": "minecraft:poof", "count": 2}),
]

# Trigger + base shape by the rune's normal slot.
SLOT_BEHAVIOUR = {
    "BOOTS": {"trigger": "WALK", "shape": "TRAIL", "interval": 2, "spread": 0.2},
    "HELMET": {"trigger": "AMBIENT", "shape": "POINT", "interval": 8, "spread": 0.35},
    "CHESTPLATE": {"trigger": "AMBIENT", "shape": "RING", "interval": 6, "spread": 0.55},
    "WEAPON": {"trigger": "ATTACK", "shape": "BURST", "interval": 1, "spread": 0.4, "speed": 0.08},
    "BOW": {"trigger": "ATTACK", "shape": "BURST", "interval": 1, "spread": 0.3, "speed": 0.05},
    "AXE": {"trigger": "ATTACK", "shape": "BURST", "interval": 1, "spread": 0.4, "speed": 0.06},
    "FARMING_TOOL": {"trigger": "ATTACK", "shape": "BURST", "interval": 1, "spread": 0.4, "speed": 0.04},
}


def main() -> None:
    catalog = json.loads(CATALOG.read_text())
    effects: dict[str, dict] = {}
    unmatched = []
    for rune in catalog["runes"]:
        rid = rune["id"]
        if rid in VERIFIED:
            effects[rid] = {"verified": True, "source": "verified-in-game", **VERIFIED[rid]}
            continue
        # Match whole words (prefixes allowed), the rune's own name first, then its description.
        name_words = re.findall(r"[a-z]+", f"{rid.replace('_', ' ')} {rune['name']}".lower())
        desc_words = re.findall(r"[a-z]+", rune["description"].split("Obtained")[0].lower())
        rule = None
        for words in (name_words, desc_words):
            rule = next((emitter for keys, emitter in RULES if any(w.startswith(k) for w in words for k in keys)), None)
            if rule:
                break
        text = " ".join(name_words + desc_words)
        if rule is None:
            unmatched.append(rid)
            continue
        emitter = {**SLOT_BEHAVIOUR.get(rune.get("slot") or "", SLOT_BEHAVIOUR["HELMET"]), **rule}
        if "spiral" in text:
            emitter["shape"] = "SPIRAL"
        if "carpet" in text:
            emitter.update({"trigger": "WALK", "shape": "TRAIL"})
        effects[rid] = {"verified": False, "source": "name-inferred", "emitters": [emitter]}

    OUT.write_text(json.dumps({
        "schemaVersion": 1,
        "_comment": "APPROXIMATE effects inferred from rune names/descriptions (verified=false). See generator/approximate_rune_effects.py and MISSING_DATA.md.",
        "effects": effects,
    }, indent=1, ensure_ascii=False) + "\n")
    print(f"wrote {OUT.relative_to(ROOT)}: {len(effects)} approximate effects, {len(unmatched)} runes with no rule: {', '.join(unmatched)}")


if __name__ == "__main__":
    main()
