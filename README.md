# Cosmetic Studio

A client-side, **purely visual** Fabric mod for Hypixel SkyBlock. Preview and apply every SkyBlock dye and rune
to your armor, turn your pet into any block or mob, and (optionally, through a self-hosted sync server) see other
Cosmetic Studio users' cosmetics.

## Download

| Minecraft | Fabric API | Jar |
|---|---|---|
| 26.3 | 0.165.2+26.3 | [cosmeticstudio-mc26.3-latest.jar](https://github.com/skylerressi-eng/Hypixel-Cosmetic-modd/raw/claude/funny-goldberg-heyqvp/downloads/cosmeticstudio-mc26.3-latest.jar) |
| 26.2 | 0.161.0+26.2 | [cosmeticstudio-mc26.2-latest.jar](https://github.com/skylerressi-eng/Hypixel-Cosmetic-modd/raw/claude/funny-goldberg-heyqvp/downloads/cosmeticstudio-mc26.2-latest.jar) |
| 26.1.2 | 0.155.3+26.1.2 | [cosmeticstudio-mc26.1.2-latest.jar](https://github.com/skylerressi-eng/Hypixel-Cosmetic-modd/raw/claude/funny-goldberg-heyqvp/downloads/cosmeticstudio-mc26.1.2-latest.jar) |

Both need Fabric Loader 0.19.5 or newer. [Mod Menu](https://modrinth.com/mod/modmenu) is optional (adds a
Configure button). These links always point to the newest build.

## Features

### `/cosmetic` (and `/cosmetic config`)
- Live 3D preview of you in your current armor: drag to rotate, scroll to zoom.
- Slot buttons for helmet, chestplate, leggings, boots and pet.
- **Dyes** tab: all 69 SkyBlock dyes as colour swatches (27 animated ones animate in the grid and on you).
  Search, sort (A–Z / colour / rarity), filter (static / animated / by rarity).
- **Runes** tab: all 69 runes with levels I–III, coloured like in game, with description, rarity and the slot
  SkyBlock normally allows. Filter "Fits piece" / all / by slot type; sort A–Z / rarity / slot.
- Hover anything to preview it live; click to select (click again to remove).
- Apply · Clear piece · Reset all · Done. Closing the screen (even with Esc) keeps what you picked.
- **Presets**: save as many outfits as you like ("Birch Fit", "Necron Black", …). Each stores all armor
  cosmetics + the pet. The Presets screen lists them all with a summary: Load, Overwrite, Delete, Save current
  (leave the name empty to auto-name).
- Clear messages instead of silent failures: non-leather pieces show "isn't dyeable", runes on the wrong
  slot explain how to allow them, unverified rune effects say so.

### Dyes
- Recolours leather-based armor **when worn and as item icons** (inventory, hotbar, GUI). Same rule as
  SkyBlock: only dyeable (leather) pieces change.
- Exact hex colours from the NEU REPO. Animated dyes interpolate smoothly between NEU's frames.

### Runes
- Client-side particles using vanilla particles, emitted from the piece they're on (head, body, legs, feet),
  triggered like the real thing (ambient, while walking, or when you swing).
- Follows the vanilla **Particles** video setting, plus an on/off switch, a density slider and
  "Off in Dungeons / Kuudra" switches.
- "Any rune on any piece" config toggle (off = SkyBlock's slot rules).
- **Hide real runes**: hides the particles of runes really applied to your SkyBlock items (shown per piece in the
  Runes tab), on your screen only, so they don't mix with your cosmetic runes. It hides incoming server particles
  right around you while you wear/hold a runed item; nothing is sent to the server.
- **Remove all my runes**: clears every cosmetic rune you picked before (all items and slots); dyes and pet stay.
- **No public source documents what rune particles look like**, so the effects shipped are approximations
  derived from each rune's name/description (shown by default, labelled "approximate" in the GUI; turn off with
  "Show unverified rune effects"). See `MISSING_DATA.md` to correct them.

### Pets
- **Blocks**: every block in the game (read from the block registry), with slow spin and gentle bob.
- **Animals**: every living mob (read from the entity registry), rendered with its normal model and idle
  animations (age-driven blinking/tails, head turning, walk cycle while following).
- **Replace my SkyBlock pet**: hides your real SkyBlock pet's model and draws your block/mob exactly where it is,
  following it around (your real pet's name tag stays unless you give the cosmetic its own name).
- Positions: follow me, replace my SkyBlock pet, left/right shoulder, on my head, left/right arm (moves with the
  arm), floating behind me. Scale slider and X/Y/Z offsets.
- Walking mobs (fox, wolf, cat, …) **walk on the ground** after you, facing where they go, with their walk
  animation; flying/swimming mobs (allay, bee, parrot, bat, fish, …) and blocks float.
- Custom name tag (max 24 chars) with a show/hide toggle.
- "Hide my real SkyBlock pet": hides your own pet's two armor stands on your screen only. Identified the same
  way other SkyBlock mods do: name tag `[Lv99] Ammonite` (SkyHanni's pattern), matched against the
  "Pet:" line of the tab list, plus the player-head armor stand just below it.

### Persistence
- `config/cosmeticstudio/cosmetics.json`: armor cosmetics keyed by the SkyBlock item **UUID** (from the item's
  custom data) so they follow that exact item between armor sets; items without a UUID fall back to per-slot.
- `config/cosmeticstudio/presets.json`, `config/cosmeticstudio/config.json`.
- `config/cosmeticstudio/rune_effects.json` (optional): your own rune effect overrides.
- Catalog refreshes from GitHub in the background and is cached (`catalog-cache.json`), so new dyes/runes
  work without a mod update.

### Multiplayer sync (optional, self-hosted)
- Set the sync server URL in the config (blank = off). Nothing is sent anywhere until you do.
- Login uses Mojang's session server exactly like joining an online-mode server (`joinServer` from the
  client, `hasJoined` on the server), so nobody can set cosmetics for someone else's UUID.
- You upload only what you're wearing right now, resolved per slot (item UUIDs are never sent), and your pet.
- Nearby players are fetched in batches and cached for 60 seconds. Players without the mod show nothing.
- The server only accepts known dye/rune/block/entity IDs. Pet names (the only free text) are limited to 24
  characters and run through a profanity filter, and you can hide other people's pet names.

## Safety and Hypixel rules
- Purely visual: no gameplay advantage, no automation, no macros.
- Never sends chat, commands or packets to Hypixel, and never modifies packets sent to Hypixel.
  `/cosmetic` is a client-side command, location detection only *reads* the scoreboard/tab list
  (no `/locraw`, no Mod API packets), and all particles/pets are spawned locally.
- Sync traffic goes only to the server URL you configure.

## Testing it in game
1. Install Fabric Loader + Fabric API for your version and drop the jar in `mods`.
2. Join Hypixel SkyBlock wearing **leather-based armor** (e.g. Farm Suit, any dyed leather set).
3. `/cosmetic` → pick the chestplate → hover dyes (preview), click one, press **Done**. Your worn chestplate
   and its inventory icon change colour. Swap to another set: the cosmetic stays on the original item.
4. Select a non-leather piece → the Dyes tab says it isn't dyeable.
5. Runes: `/cosmetic config` → turn on "Show unverified rune effects" → `/cosmetic` → Runes → pick e.g.
   Rainbow Rune III on boots → walk. Toggle the vanilla Particles setting / density slider to see it change.
6. Pets: Pet slot → Animals → Fox → Settings: name it, try each Position, scale/offsets, and
   "Hide my real SkyBlock pet". Press F5 to see it.
7. Presets: type "Birch Fit" → Save; change things; click "Load: Birch Fit" → Apply.
8. Sync: run the server (below) on a host both players can reach, set the URL in both clients' config,
   stand near each other → each sees the other's dyes/runes/pet within a few seconds.

## Sync server (`server/`)
TypeScript + Fastify + SQLite (Node's built-in `node:sqlite`, no native modules).

```bash
cd server
docker compose up -d --build
```

Then set `syncServerUrl` to `http://<host>:8080` (use a TLS reverse proxy such as Caddy for public use and set
`TRUST_PROXY=true`). Data lives in the `sync-data` volume. Environment: `PORT`, `DB_PATH`, `CATALOG_DIR`,
`TRUST_PROXY`, `PROFANITY_EXTRA_FILE`, `MOJANG_SESSION_SERVER`.

API: `POST /v1/auth/challenge`, `POST /v1/auth/verify`, `PUT /v1/cosmetics`, `DELETE /v1/cosmetics`,
`GET /v1/cosmetics?uuids=a,b,c` (max 100), `GET /v1/health`. Rate limits: 120 req/min per IP overall,
10/min for auth, 30/min uploads, 60/min batch reads.

## Data (`generator/`, `catalog/`)
```bash
python3 generator/generate.py                 # dyes + runes from NEU REPO, cross-checked with Hypixel's API
python3 generator/approximate_rune_effects.py # unverified, opt-in rune effect approximations
python3 generator/registry_ids.py             # block/entity id allowlist for the server
```
- NEU REPO (MIT, © Moulberry) provides dye hex colours, animated-dye frames, rune names/levels/rarities/slots.
- Hypixel's `/v2/resources/skyblock/items` does not list dyes or runes individually (runes are a generic
  `RUNE` item), so it's used as a cross-check only.
- Anything unverified is listed in `MISSING_DATA.md` (animated-dye speed, rune particle effects).

## Building
```bash
./gradlew :mod:build                 # Minecraft 26.3 (default)
./gradlew :mod:build -Pmc=26.1.2     # Minecraft 26.1.2
./gradlew :mod:test                  # unit tests
xvfb-run -a ./gradlew :mod:runClientGameTest   # real client: dyes, GUI, runes, pets (screenshots in mod/build/run/clientGameTest/screenshots)
cd server && npm ci && npm test       # server tests
```
Per-version values are in `versions/<mc>.properties`; the few APIs that differ between versions live in
`mod/src/mc<version>/java/.../compat/Compat.java`.

## Toolchain (verified 2026-11-08)
| | Version |
|---|---|
| Minecraft | 26.3 and 26.1.2 |
| Fabric Loader | 0.19.5+ (built against 0.19.5) |
| Fabric API | 0.165.2+26.3 / 0.155.3+26.1.2 |
| Fabric Loom | 1.19.4 (`net.fabricmc.fabric-loom`) |
| Gradle | 9.8.2 (wrapper) |
| Java | 25 |
| Mappings | none: 26.x ships unobfuscated (Mojang names) |
| Mod Menu (optional) | 21.0.0 / 18.0.2 |

## Code layout
| Package | Purpose |
|---|---|
| `catalog` | Dye/rune/effect data, loading, cache + remote refresh |
| `model`, `store` | Look / armor / pet model, JSON persistence + presets |
| `resolve` | Which cosmetic applies where (preview > saved > remote) |
| `render` | Dye hook glue, pet layer + pet entities, rune particles |
| `skyblock` | Read-only location detection, item UUIDs, real-pet hiding |
| `gui`, `config` | `/cosmetic` screen, widgets, settings screen, Mod Menu |
| `sync` | Sync client + remote cache |
| `mixin` | 4 small mixins: armor dye colour, item icon dye colour, skip rendering hidden pet stands, skip displaying real-rune particles |
