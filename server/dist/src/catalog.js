import { readFileSync } from "node:fs";
import { join } from "node:path";
export function loadAllowlist(catalogDir) {
    const cosmetics = JSON.parse(readFileSync(join(catalogDir, "cosmetics.json"), "utf8"));
    const registry = JSON.parse(readFileSync(join(catalogDir, "registry_ids.json"), "utf8"));
    const runes = new Map();
    for (const r of cosmetics.runes)
        runes.set(r.id, Math.max(...r.levels.map((l) => l.level)));
    return {
        dyes: new Set(cosmetics.dyes.filter((d) => d.hex).map((d) => d.id)),
        runes,
        blocks: new Set(registry.blocks),
        entities: new Set(registry.entities),
    };
}
