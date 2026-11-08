import type { Allowlist } from "./catalog.js";
import type { ProfanityFilter } from "./profanity.js";

export const SLOTS = ["HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"] as const;
export const POSITIONS = ["FOLLOW", "LEFT_SHOULDER", "RIGHT_SHOULDER", "HEAD", "LEFT_ARM", "RIGHT_ARM", "BEHIND"] as const;
export const MAX_PET_NAME = 24;

export interface ArmorCosmetic { dye: string | null; rune: string | null; runeLevel: number }
export interface Pet {
  kind: "NONE" | "BLOCK" | "ENTITY";
  id: string | null;
  name: string | null;
  showName: boolean;
  position: (typeof POSITIONS)[number];
  scale: number;
  offsetX: number;
  offsetY: number;
  offsetZ: number;
  spin: boolean;
  bob: boolean;
}
export interface Look { armor: Partial<Record<(typeof SLOTS)[number], ArmorCosmetic>>; pet: Pet }

export class ValidationError extends Error {}

const clamp = (v: unknown, lo: number, hi: number, dflt: number) =>
  typeof v === "number" && Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : dflt;

/**
 * Validates and normalises an uploaded look. Unknown dye/rune/block/entity ids are rejected, not dropped, so a
 * misbehaving client finds out. Unknown fields are ignored. Returns a clean copy.
 */
export function validateLook(input: unknown, allow: Allowlist, profanity: ProfanityFilter): Look {
  if (typeof input !== "object" || input === null) throw new ValidationError("body must be an object");
  const body = input as Record<string, unknown>;
  const armorIn = (body.armor ?? {}) as Record<string, unknown>;
  if (typeof armorIn !== "object" || armorIn === null || Array.isArray(armorIn)) throw new ValidationError("armor must be an object");

  const armor: Look["armor"] = {};
  for (const [slot, value] of Object.entries(armorIn)) {
    if (!(SLOTS as readonly string[]).includes(slot)) throw new ValidationError(`unknown slot ${slot}`);
    if (value === null) continue;
    const v = value as Record<string, unknown>;
    const dye = v.dye ?? null;
    const rune = v.rune ?? null;
    if (dye !== null && (typeof dye !== "string" || !allow.dyes.has(dye))) throw new ValidationError(`unknown dye ${String(dye)}`);
    let runeLevel = 0;
    if (rune !== null) {
      if (typeof rune !== "string" || !allow.runes.has(rune)) throw new ValidationError(`unknown rune ${String(rune)}`);
      runeLevel = clamp(v.runeLevel, 1, allow.runes.get(rune)!, 1);
      runeLevel = Math.round(runeLevel);
    }
    if (dye !== null || rune !== null) armor[slot as (typeof SLOTS)[number]] = { dye: dye as string | null, rune: rune as string | null, runeLevel };
  }

  const p = (body.pet ?? {}) as Record<string, unknown>;
  const kind = p.kind === "BLOCK" || p.kind === "ENTITY" ? p.kind : "NONE";
  let id: string | null = null;
  if (kind !== "NONE") {
    if (typeof p.id !== "string") throw new ValidationError("pet id required");
    const known = kind === "BLOCK" ? allow.blocks.has(p.id) : allow.entities.has(p.id);
    if (!known) throw new ValidationError(`unknown ${kind === "BLOCK" ? "block" : "entity"} ${p.id}`);
    id = p.id;
  }
  let name: string | null = null;
  if (p.name !== undefined && p.name !== null) {
    if (typeof p.name !== "string") throw new ValidationError("pet name must be a string");
    // Strip control characters and Minecraft formatting codes, then trim.
    const cleaned = p.name.replace(/§./g, "").replace(/[\u0000-\u001f\u007f-\u009f§]/g, "").trim();
    if ([...cleaned].length > MAX_PET_NAME) throw new ValidationError(`pet name longer than ${MAX_PET_NAME} characters`);
    if (cleaned && !profanity.isClean(cleaned)) throw new ValidationError("pet name not allowed");
    name = cleaned || null;
  }
  const position = (POSITIONS as readonly string[]).includes(p.position as string) ? (p.position as Pet["position"]) : "FOLLOW";
  return {
    armor,
    pet: {
      kind, id, name, position,
      showName: p.showName !== false,
      scale: clamp(p.scale, 0.25, 2.5, 1),
      offsetX: clamp(p.offsetX, -1, 1, 0),
      offsetY: clamp(p.offsetY, -1, 1, 0),
      offsetZ: clamp(p.offsetZ, -1, 1, 0),
      spin: p.spin !== false,
      bob: p.bob !== false,
    },
  };
}
