import { NextRequest, NextResponse } from "next/server";
import fs from "fs";
import path from "path";
import { requireSession } from "@/lib/auth";
import { botDir } from "@/lib/db";
import { hashOf, keyFromEmojiId, tierEmojiOf } from "@/lib/badges";

const WEEK = 86400 * 7;

function cacheDir() {
  return path.join(botDir(), "badge_cache");
}

async function pull(url: string): Promise<Buffer | null> {
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": "Mozilla/5.0" },
      signal: AbortSignal.timeout(8000),
      cache: "force-cache",
    });
    if (!res.ok) return null;
    return Buffer.from(await res.arrayBuffer());
  } catch {
    return null;
  }
}

function serveFile(file: string, data: Buffer) {
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, data);
  } catch {}
  return new NextResponse(new Uint8Array(data), {
    headers: {
      "Content-Type": "image/png",
      "Cache-Control": `public, max-age=${WEEK}`,
    },
  });
}

function fromCache(file: string) {
  try {
    if (!fs.existsSync(file)) return null;
    const age = Date.now() - fs.statSync(file).mtimeMs;
    if (age > WEEK * 1000) return null;
    const data = fs.readFileSync(file);
    return new NextResponse(data, {
      headers: {
        "Content-Type": "image/png",
        "Cache-Control": `public, max-age=${WEEK}`,
      },
    });
  } catch {
    return null;
  }
}

async function serveBadge(hash: string) {
  const file = path.join(cacheDir(), `${hash}.png`);
  const hit = fromCache(file);
  if (hit) return hit;
  const data = await pull(`https://cdn.discordapp.com/badge-icons/${hash}.png`);
  if (!data) return NextResponse.json({ error: "cdn" }, { status: 502 });
  return serveFile(file, data);
}

async function serveEmoji(emojiId: string) {
  const file = path.join(cacheDir(), `emoji_${emojiId}.png`);
  const hit = fromCache(file);
  if (hit) return hit;
  const data = await pull(`https://cdn.discordapp.com/emojis/${emojiId}.png?size=32`);
  if (!data) return NextResponse.json({ error: "cdn" }, { status: 502 });
  return serveFile(file, data);
}

export async function GET(req: NextRequest) {
  const sess = await requireSession();
  if (!sess) return NextResponse.json({ error: "auth" }, { status: 401 });

  const sp = req.nextUrl.searchParams;
  const emojiId = (sp.get("emoji_id") || "").trim();
  const id = (sp.get("id") || "").trim();
  const hashQ = (sp.get("hash") || "").trim();

  if (emojiId) {
    if (!/^\d{15,25}$/.test(emojiId)) {
      return NextResponse.json({ error: "bad" }, { status: 400 });
    }
    const key = keyFromEmojiId(emojiId);
    if (key) {
      const tier = tierEmojiOf(key);
      if (tier) return serveEmoji(tier);
      const h = hashOf(key);
      if (h) return serveBadge(h);
    }
    return serveEmoji(emojiId);
  }

  if (id) {
    const tier = tierEmojiOf(id);
    if (tier) return serveEmoji(tier);
    const h = hashOf(id);
    if (h) return serveBadge(h);
    return NextResponse.json({ error: "missing" }, { status: 404 });
  }

  if (hashQ && /^[a-f0-9]{32}$/i.test(hashQ)) {
    return serveBadge(hashQ.toLowerCase());
  }

  return NextResponse.json({ error: "bad" }, { status: 400 });
}
