import { loadAllowlist } from "./catalog.js";
import { buildApp } from "./app.js";
import { Store } from "./db.js";
import { ProfanityFilter } from "./profanity.js";

const env = process.env;
const catalogDir = env.CATALOG_DIR ?? "../catalog";
const store = new Store(env.DB_PATH ?? "./data/cosmetics.db");
const allow = loadAllowlist(catalogDir);
const app = await buildApp({
  allow,
  store,
  profanity: new ProfanityFilter(env.PROFANITY_EXTRA_FILE),
  sessionServer: env.MOJANG_SESSION_SERVER,
  trustProxy: env.TRUST_PROXY === "true",
  logger: true,
});
setInterval(() => store.purgeExpired(), 60 * 60 * 1000).unref();
const port = Number(env.PORT ?? 8080);
await app.listen({ host: env.HOST ?? "0.0.0.0", port });
app.log.info(`Cosmetic Studio sync: ${allow.dyes.size} dyes, ${allow.runes.size} runes, ${allow.blocks.size} blocks, ${allow.entities.size} entities allowed`);
for (const sig of ["SIGINT", "SIGTERM"]) process.on(sig, async () => { await app.close(); store.close(); process.exit(0); });
