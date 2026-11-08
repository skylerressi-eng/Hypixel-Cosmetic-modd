import { DatabaseSync } from "node:sqlite";
import { createHash, randomBytes } from "node:crypto";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import type { Look } from "./validate.js";

/** SQLite storage (built into Node, no native modules). Session tokens are stored hashed. */
export class Store {
  private readonly db: DatabaseSync;

  constructor(path: string) {
    if (path !== ":memory:") mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS looks (uuid TEXT PRIMARY KEY, look TEXT NOT NULL, updated_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires_at INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions(expires_at);
    `);
  }

  putLook(uuid: string, look: Look): number {
    const now = Date.now();
    this.db.prepare("INSERT INTO looks(uuid, look, updated_at) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET look = excluded.look, updated_at = excluded.updated_at")
      .run(uuid, JSON.stringify(look), now);
    return now;
  }

  getLooks(uuids: string[]): Map<string, { look: Look; updatedAt: number }> {
    const out = new Map<string, { look: Look; updatedAt: number }>();
    if (uuids.length === 0) return out;
    const rows = this.db.prepare(`SELECT uuid, look, updated_at FROM looks WHERE uuid IN (${uuids.map(() => "?").join(",")})`).all(...uuids) as
      { uuid: string; look: string; updated_at: number }[];
    for (const r of rows) out.set(r.uuid, { look: JSON.parse(r.look), updatedAt: r.updated_at });
    return out;
  }

  deleteLook(uuid: string): void {
    this.db.prepare("DELETE FROM looks WHERE uuid = ?").run(uuid);
  }

  createSession(uuid: string, ttlMs: number): { token: string; expiresAt: number } {
    const token = randomBytes(32).toString("base64url");
    const expiresAt = Date.now() + ttlMs;
    this.db.prepare("INSERT INTO sessions(token_hash, uuid, expires_at) VALUES (?, ?, ?)").run(hash(token), uuid, expiresAt);
    return { token, expiresAt };
  }

  sessionUuid(token: string): string | null {
    const row = this.db.prepare("SELECT uuid, expires_at FROM sessions WHERE token_hash = ?").get(hash(token)) as { uuid: string; expires_at: number } | undefined;
    return row && row.expires_at > Date.now() ? row.uuid : null;
  }

  deleteSessions(uuid: string): void {
    this.db.prepare("DELETE FROM sessions WHERE uuid = ?").run(uuid);
  }

  purgeExpired(): void {
    this.db.prepare("DELETE FROM sessions WHERE expires_at <= ?").run(Date.now());
  }

  close(): void {
    this.db.close();
  }
}

const hash = (s: string) => createHash("sha256").update(s).digest("hex");
