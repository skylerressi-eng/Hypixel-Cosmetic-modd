import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import rateLimit from "@fastify/rate-limit";
import { randomBytes } from "node:crypto";
import type { Allowlist } from "./catalog.js";
import { Store } from "./db.js";
import { ProfanityFilter } from "./profanity.js";
import { ValidationError, validateLook } from "./validate.js";

export interface AppOptions {
  allow: Allowlist;
  store: Store;
  profanity: ProfanityFilter;
  /** Base URL of Mojang's session server (overridable for tests). */
  sessionServer?: string;
  /** Replaces the HTTP call to Mojang (tests). Returns the dashed profile UUID or null. */
  hasJoined?: (username: string, serverId: string) => Promise<string | null>;
  sessionTtlMs?: number;
  trustProxy?: boolean;
  logger?: boolean;
}

const UUID_RE = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const USERNAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const CHALLENGE_TTL_MS = 60_000;
const MAX_CHALLENGES = 10_000;
const MAX_BATCH = 100;

export function dashed(uuid: string): string {
  const h = uuid.replace(/-/g, "").toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export async function buildApp(opts: AppOptions): Promise<FastifyInstance> {
  const app = Fastify({ logger: opts.logger ?? false, trustProxy: opts.trustProxy ?? false, bodyLimit: 16 * 1024 });
  const sessionServer = opts.sessionServer ?? "https://sessionserver.mojang.com";
  const ttl = opts.sessionTtlMs ?? 24 * 60 * 60 * 1000;
  const challenges = new Map<string, number>(); // serverId -> expiry

  const hasJoined = opts.hasJoined ?? (async (username: string, serverId: string) => {
    const url = `${sessionServer}/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
    const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
    if (res.status !== 200) return null;
    const body = (await res.json()) as { id?: string };
    return body.id && UUID_RE.test(body.id) ? dashed(body.id) : null;
  });

  // Global limit per IP; tighter per-route limits below.
  await app.register(rateLimit, { global: true, max: 120, timeWindow: "1 minute" });

  const bearer = (req: FastifyRequest): string | null => {
    const h = req.headers.authorization;
    if (!h || !h.startsWith("Bearer ")) return null;
    return opts.store.sessionUuid(h.slice(7).trim());
  };

  app.get("/v1/health", async () => ({ ok: true }));

  /** Step 1: get a one-time serverId to pass to Mojang's joinServer. */
  app.post("/v1/auth/challenge", { config: { rateLimit: { max: 10, timeWindow: "1 minute" } } }, async (_req, reply) => {
    const now = Date.now();
    for (const [k, exp] of challenges) if (exp <= now) challenges.delete(k);
    if (challenges.size >= MAX_CHALLENGES) return reply.code(503).send({ error: "busy" });
    const serverId = randomBytes(20).toString("hex");
    challenges.set(serverId, now + CHALLENGE_TTL_MS);
    return { serverId, expiresInMs: CHALLENGE_TTL_MS };
  });

  /** Step 2: after joinServer, ask Mojang (hasJoined) who joined with that serverId, and issue a session token. */
  app.post("/v1/auth/verify", { config: { rateLimit: { max: 10, timeWindow: "1 minute" } } }, async (req, reply) => {
    const body = (req.body ?? {}) as { username?: unknown; serverId?: unknown };
    if (typeof body.username !== "string" || !USERNAME_RE.test(body.username) || typeof body.serverId !== "string") {
      return reply.code(400).send({ error: "username and serverId required" });
    }
    const exp = challenges.get(body.serverId);
    challenges.delete(body.serverId); // single use
    if (!exp || exp <= Date.now()) return reply.code(401).send({ error: "unknown or expired challenge" });
    let uuid: string | null;
    try {
      uuid = await hasJoined(body.username, body.serverId);
    } catch (e) {
      req.log.warn({ err: e }, "Mojang session server unavailable");
      return reply.code(502).send({ error: "session server unavailable" });
    }
    if (!uuid) return reply.code(401).send({ error: "Mojang did not confirm this login" });
    const session = opts.store.createSession(uuid, ttl);
    return { token: session.token, uuid, expiresAt: session.expiresAt };
  });

  /** Upload my look. Only ever for the UUID proven at login. */
  app.put("/v1/cosmetics", { config: { rateLimit: { max: 30, timeWindow: "1 minute" } } }, async (req, reply) => {
    const uuid = bearer(req);
    if (!uuid) return reply.code(401).send({ error: "login required" });
    try {
      const look = validateLook(req.body, opts.allow, opts.profanity);
      const updatedAt = opts.store.putLook(uuid, look);
      return { ok: true, updatedAt, look };
    } catch (e) {
      if (e instanceof ValidationError) return reply.code(400).send({ error: e.message });
      throw e;
    }
  });

  /** Delete my data (look + sessions). */
  app.delete("/v1/cosmetics", async (req, reply) => {
    const uuid = bearer(req);
    if (!uuid) return reply.code(401).send({ error: "login required" });
    opts.store.deleteLook(uuid);
    opts.store.deleteSessions(uuid);
    return { ok: true };
  });

  /** Batch lookup for nearby players. Players without the mod are simply absent (null). */
  app.get("/v1/cosmetics", { config: { rateLimit: { max: 60, timeWindow: "1 minute" } } }, async (req, reply) => {
    const raw = String((req.query as { uuids?: string }).uuids ?? "");
    const list = raw.split(",").map((s) => s.trim()).filter(Boolean);
    if (list.length === 0 || list.length > MAX_BATCH || !list.every((u) => UUID_RE.test(u))) {
      return reply.code(400).send({ error: `uuids: 1-${MAX_BATCH} comma-separated UUIDs` });
    }
    const uuids = [...new Set(list.map(dashed))];
    const found = opts.store.getLooks(uuids);
    const players: Record<string, unknown> = {};
    for (const u of uuids) {
      const f = found.get(u);
      players[u] = f ? { ...f.look, updatedAt: f.updatedAt } : null;
    }
    reply.header("Cache-Control", "public, max-age=30");
    return { players };
  });

  return app;
}
