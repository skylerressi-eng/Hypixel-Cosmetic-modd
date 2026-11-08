import { test } from "node:test";
import assert from "node:assert/strict";
import { join } from "node:path";
import { loadAllowlist } from "../src/catalog.js";
import { buildApp } from "../src/app.js";
import { Store } from "../src/db.js";
import { ProfanityFilter } from "../src/profanity.js";

const catalog = join(import.meta.dirname, "..", "..", "..", "catalog");
const ALICE = "11111111-2222-4333-8444-555555555555";

async function setup() {
  const joined = new Map<string, string>(); // serverId -> username that "joined" via Mojang
  const app = await buildApp({
    allow: loadAllowlist(catalog),
    store: new Store(":memory:"),
    profanity: new ProfanityFilter(),
    hasJoined: async (username, serverId) => (joined.get(serverId) === username && username === "Alice" ? ALICE : null),
  });
  const login = async (username = "Alice") => {
    const { serverId } = (await app.inject({ method: "POST", url: "/v1/auth/challenge" })).json();
    joined.set(serverId, username); // simulates the client calling Mojang joinServer
    return app.inject({ method: "POST", url: "/v1/auth/verify", payload: { username, serverId } });
  };
  return { app, login, joined };
}

const look = {
  armor: { CHESTPLATE: { dye: "DYE_PURE_BLACK", rune: "GRAND_FREEZING_RUNE", runeLevel: 3 } },
  pet: { kind: "ENTITY", id: "minecraft:fox", name: "Birch Buddy", position: "LEFT_SHOULDER", scale: 9 },
};

test("login via Mojang hasJoined, upload, batch fetch", async () => {
  const { app, login } = await setup();
  const res = await login();
  assert.equal(res.statusCode, 200);
  const { token, uuid } = res.json();
  assert.equal(uuid, ALICE);

  const put = await app.inject({ method: "PUT", url: "/v1/cosmetics", headers: { authorization: `Bearer ${token}` }, payload: look });
  assert.equal(put.statusCode, 200, put.body);
  assert.equal(put.json().look.pet.scale, 2.5, "scale clamped");

  const other = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
  const get = await app.inject({ method: "GET", url: `/v1/cosmetics?uuids=${ALICE.replace(/-/g, "")},${other}` });
  assert.equal(get.statusCode, 200);
  const players = get.json().players;
  assert.equal(players[ALICE].armor.CHESTPLATE.dye, "DYE_PURE_BLACK");
  assert.equal(players[ALICE].pet.name, "Birch Buddy");
  assert.equal(players[other], null, "players without the mod are null");
});

test("cannot set cosmetics without proving the account", async () => {
  const { app, login } = await setup();
  assert.equal((await app.inject({ method: "PUT", url: "/v1/cosmetics", payload: look })).statusCode, 401);
  assert.equal((await app.inject({ method: "PUT", url: "/v1/cosmetics", headers: { authorization: "Bearer forged" }, payload: look })).statusCode, 401);
  // Mojang says this user did not join with that serverId -> rejected.
  assert.equal((await login("Mallory")).statusCode, 401);
  // A challenge cannot be reused.
  const { serverId } = (await app.inject({ method: "POST", url: "/v1/auth/challenge" })).json();
  assert.equal((await app.inject({ method: "POST", url: "/v1/auth/verify", payload: { username: "Alice", serverId: "made-up" } })).statusCode, 401);
  assert.ok(serverId);
});

test("only known ids and clean, short pet names are accepted", async () => {
  const { app, login } = await setup();
  const { token } = (await login()).json();
  const put = (payload: unknown) => app.inject({ method: "PUT", url: "/v1/cosmetics", headers: { authorization: `Bearer ${token}` }, payload: payload as object });
  assert.equal((await put({ armor: { HELMET: { dye: "DYE_MADE_UP" } } })).statusCode, 400);
  assert.equal((await put({ armor: { HELMET: { rune: "NOPE_RUNE", runeLevel: 1 } } })).statusCode, 400);
  assert.equal((await put({ armor: { HAT: { dye: "DYE_PURE_BLACK" } } })).statusCode, 400);
  assert.equal((await put({ pet: { kind: "BLOCK", id: "minecraft:not_a_block" } })).statusCode, 400);
  assert.equal((await put({ pet: { kind: "ENTITY", id: "minecraft:fox", name: "x".repeat(25) } })).statusCode, 400);
  assert.equal((await put({ pet: { kind: "ENTITY", id: "minecraft:fox", name: "fUcK" } })).statusCode, 400);
  assert.equal((await put({ pet: { kind: "ENTITY", id: "minecraft:fox", name: "sh1t lord" } })).statusCode, 400);
  const ok = await put({ pet: { kind: "BLOCK", id: "minecraft:diamond_block", name: "§cShiny\u0000 Rock" } });
  assert.equal(ok.statusCode, 200);
  assert.equal(ok.json().look.pet.name, "Shiny Rock", "formatting codes and control chars stripped");
});

test("delete removes data and sessions", async () => {
  const { app, login } = await setup();
  const { token } = (await login()).json();
  const auth = { authorization: `Bearer ${token}` };
  await app.inject({ method: "PUT", url: "/v1/cosmetics", headers: auth, payload: look });
  assert.equal((await app.inject({ method: "DELETE", url: "/v1/cosmetics", headers: auth })).statusCode, 200);
  assert.equal((await app.inject({ method: "GET", url: `/v1/cosmetics?uuids=${ALICE}` })).json().players[ALICE], null);
  assert.equal((await app.inject({ method: "PUT", url: "/v1/cosmetics", headers: auth, payload: look })).statusCode, 401);
});

test("rate limiting kicks in", async () => {
  const { app } = await setup();
  let limited = false;
  for (let i = 0; i < 15; i++) {
    if ((await app.inject({ method: "POST", url: "/v1/auth/challenge" })).statusCode === 429) limited = true;
  }
  assert.ok(limited, "challenge endpoint should be limited to 10/min");
});

test("bad batch requests are rejected", async () => {
  const { app } = await setup();
  assert.equal((await app.inject({ method: "GET", url: "/v1/cosmetics?uuids=not-a-uuid" })).statusCode, 400);
  const many = Array.from({ length: 101 }, () => ALICE).join(",");
  assert.equal((await app.inject({ method: "GET", url: `/v1/cosmetics?uuids=${many}` })).statusCode, 400);
});
