// DEV/TEST ONLY: a stand-in for Mojang's session server so the full login flow can be tested offline.
// Implements POST /session/minecraft/join (what the game calls) and GET /session/minecraft/hasJoined (what the
// sync server calls). Never use in production.
//   node server/dev/fake-mojang.mjs 18090
// Game client:  -Dminecraft.api.session.host=http://127.0.0.1:18090
// Sync server:  MOJANG_SESSION_SERVER=http://127.0.0.1:18090
import { createServer } from "node:http";

const joins = new Map(); // serverId -> profile id
const port = Number(process.argv[2] ?? 18090);
createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  if (req.method === "POST" && url.pathname === "/session/minecraft/join") {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const { selectedProfile, serverId } = JSON.parse(body || "{}");
      joins.set(serverId, selectedProfile);
      console.log(`join ${selectedProfile} ${serverId}`);
      res.writeHead(204).end();
    });
    return;
  }
  if (req.method === "GET" && url.pathname === "/session/minecraft/hasJoined") {
    const id = joins.get(url.searchParams.get("serverId"));
    console.log(`hasJoined ${url.searchParams.get("username")} -> ${id ?? "no"}`);
    if (!id) return res.writeHead(204).end();
    res.writeHead(200, { "Content-Type": "application/json" }).end(JSON.stringify({ id, name: url.searchParams.get("username") }));
    return;
  }
  res.writeHead(404).end();
}).listen(port, "127.0.0.1", () => console.log(`fake mojang on ${port}`));
