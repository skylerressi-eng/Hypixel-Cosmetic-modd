import { DatabaseSync } from "node:sqlite";
import { createHash, randomBytes } from "node:crypto";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
/** SQLite storage (built into Node, no native modules). Session tokens are stored hashed. */
export class Store {
    db;
    constructor(path) {
        if (path !== ":memory:")
            mkdirSync(dirname(path), { recursive: true });
        this.db = new DatabaseSync(path);
        this.db.exec(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS looks (uuid TEXT PRIMARY KEY, look TEXT NOT NULL, updated_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires_at INTEGER NOT NULL);
      CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions(expires_at);
    `);
    }
    putLook(uuid, look) {
        const now = Date.now();
        this.db.prepare("INSERT INTO looks(uuid, look, updated_at) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET look = excluded.look, updated_at = excluded.updated_at")
            .run(uuid, JSON.stringify(look), now);
        return now;
    }
    getLooks(uuids) {
        const out = new Map();
        if (uuids.length === 0)
            return out;
        const rows = this.db.prepare(`SELECT uuid, look, updated_at FROM looks WHERE uuid IN (${uuids.map(() => "?").join(",")})`).all(...uuids);
        for (const r of rows)
            out.set(r.uuid, { look: JSON.parse(r.look), updatedAt: r.updated_at });
        return out;
    }
    deleteLook(uuid) {
        this.db.prepare("DELETE FROM looks WHERE uuid = ?").run(uuid);
    }
    createSession(uuid, ttlMs) {
        const token = randomBytes(32).toString("base64url");
        const expiresAt = Date.now() + ttlMs;
        this.db.prepare("INSERT INTO sessions(token_hash, uuid, expires_at) VALUES (?, ?, ?)").run(hash(token), uuid, expiresAt);
        return { token, expiresAt };
    }
    sessionUuid(token) {
        const row = this.db.prepare("SELECT uuid, expires_at FROM sessions WHERE token_hash = ?").get(hash(token));
        return row && row.expires_at > Date.now() ? row.uuid : null;
    }
    deleteSessions(uuid) {
        this.db.prepare("DELETE FROM sessions WHERE uuid = ?").run(uuid);
    }
    purgeExpired() {
        this.db.prepare("DELETE FROM sessions WHERE expires_at <= ?").run(Date.now());
    }
    close() {
        this.db.close();
    }
}
const hash = (s) => createHash("sha256").update(s).digest("hex");
