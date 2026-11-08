# Cosmetic Studio

A client-side, **purely visual** Fabric mod for Hypixel SkyBlock (Minecraft 26.3): preview and apply every
SkyBlock dye and rune to your armor, turn your pet into any block or mob, and see other Cosmetic Studio
users' cosmetics through an optional self-hostable sync server.

> Work in progress. See the milestone table below.

## Safety

- No gameplay advantage, no automation.
- Never sends chat to, or modifies packets sent to, Hypixel. `/cosmetic` is a client-side command that
  Fabric handles locally.
- Sync traffic goes only to the sync server URL you configure, never through Hypixel.

## Repository layout

| Path | Contents |
|---|---|
| `mod/` | The Fabric mod (Java 25, Mojang names, Loom) |
| `generator/` | Script that builds the bundled dye/rune catalog from Hypixel + NEU REPO data |
| `catalog/` | Catalog schema and generated data shared by the mod and the server |
| `server/` | Sync server (TypeScript + Fastify + SQLite, Docker) |

## Toolchain (verified 2026-11-08)

| | Version |
|---|---|
| Minecraft | 26.3 (also 26.1.2 with Fabric API 0.155.3+26.1.2, Mod Menu 18.0.2) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.2+26.3 |
| Fabric Loom | 1.19.4 (`net.fabricmc.fabric-loom`) |
| Gradle | 9.8.2 (wrapper) |
| Java | 25 |
| Mappings | none: 26.x ships unobfuscated (Mojang names) |
| Mod Menu (optional) | 21.0.0 |

## Download

| Minecraft | Fabric API | Jar |
|---|---|---|
| 26.3 | 0.165.2+26.3 | [cosmeticstudio-mc26.3-0.1.0.jar](https://github.com/skylerressi-eng/Hypixel-Cosmetic-modd/raw/claude/funny-goldberg-heyqvp/downloads/cosmeticstudio-mc26.3-0.1.0.jar) |
| 26.1.2 | 0.155.3+26.1.2 | [cosmeticstudio-mc26.1.2-0.1.0.jar](https://github.com/skylerressi-eng/Hypixel-Cosmetic-modd/raw/claude/funny-goldberg-heyqvp/downloads/cosmeticstudio-mc26.1.2-0.1.0.jar) |

Both need Fabric Loader 0.20.1 or newer.

## Building

```bash
./gradlew :mod:build                 # Minecraft 26.3 (default)
./gradlew :mod:build -Pmc=26.1.2     # Minecraft 26.1.2
```

Per-version values live in `versions/<mc>.properties`. Jars are written to
`mod/build/libs/cosmeticstudio-mc<mc>-<version>.jar`.

## Testing

```bash
./gradlew :mod:test                 # unit tests
./gradlew :mod:runClientGameTest    # launches a real client, runs in-game tests, saves screenshots
```

Screenshots land in `mod/build/run/clientGameTest/screenshots/`. On a headless Linux box, wrap the game
test with `xvfb-run -a` and install `mesa-vulkan-drivers` (the game falls back to Vulkan via llvmpipe).

## Milestones

| # | Milestone | Status |
|---|---|---|
| 1 | Project setup, build, launch, in-game smoke test, CI | ✅ |
| 2 | Data generator + bundled catalog + MISSING_DATA.md | |
| 3 | Data storage + client-side dyes on worn armor | |
| 4 | `/cosmetic` GUI with preview, search, sort, filters | |
| 5 | Rune particles | |
| 6 | Pet cosmetics | |
| 7 | Presets, share codes | |
| 8 | Sync server + mod networking | |
| 9 | Mod Menu config, polish, release | |
