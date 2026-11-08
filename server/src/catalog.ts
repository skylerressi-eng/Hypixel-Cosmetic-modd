import { readFileSync } from "node:fs";
import { join } from "node:path";

/** The allowlist: every id the server will accept. Built from the same catalog files the mod bundles. */
export interface Allowlist {
  dyes: Set<string>;
  runes: Map<string, number>; // id -> max level
  blocks: Set<string>;
  entities: Set<string>;
}

export function loadAllowlist(catalogDir: string): Allowlist {
  const cosmetics = JSON.parse(readFileSync(join(catalogDir, "cosmetics.json"), "utf8"));
  const registry = JSON.parse(readFileSync(join(catalogDir, "registry_ids.json"), "utf8"));
  const runes = new Map<string, number>();
  for (const r of cosmetics.runes) runes.set(r.id, Math.max(...r.levels.map((l: { level: number }) => l.level)));
  return {
    dyes: new Set(cosmetics.dyes.filter((d: { hex: string | null }) => d.hex).map((d: { id: string }) => d.id)),
    runes,
    blocks: new Set(registry.blocks),
    entities: new Set(registry.entities),
  };
}
