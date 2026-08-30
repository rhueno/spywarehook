import fs from "fs";
import path from "path";
import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/auth";
import { botDir, getBuild, projectRoot } from "@/lib/db";

function resolveThemePath(themeRel: string | null | undefined) {
  if (!themeRel) return null;
  const rel = themeRel.replace(/\\/g, "/");
  const cands = [
    path.resolve(themeRel),
    path.join(botDir(), themeRel),
    path.join(botDir(), "data", themeRel),
    path.join(projectRoot(), themeRel),
  ];
  if (rel.startsWith("data/")) {
    const rest = rel.slice("data/".length);
    cands.push(path.join(botDir(), rest));
    cands.push(path.join(botDir(), "data", rest));
  }
  for (const c of cands) {
    try {
      if (fs.existsSync(c) && fs.statSync(c).isFile()) return c;
    } catch {
      /* skip */
    }
  }
  return null;
}

export async function GET(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const sess = await requireAdmin();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const { id } = await ctx.params;
  const build = await getBuild(Number(id));
  if (!build || !build.theme) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }

  const file = resolveThemePath(build.theme);
  if (!file) {
    return NextResponse.json({ error: "file missing" }, { status: 404 });
  }

  const root = path.resolve(botDir());
  const abs = path.resolve(file);
  const ok =
    abs === root ||
    abs.startsWith(root + path.sep) ||
    abs.startsWith(root + "/");
  if (!ok) {
    return NextResponse.json({ error: "not downloadable" }, { status: 400 });
  }

  const buf = fs.readFileSync(abs);
  const label = (build.label || "theme").replace(/[^\w\- ]+/g, "").trim() || "theme";
  const fname = `${label.replace(/\s+/g, "") || "theme"}-${build.id}.html`;
  return new NextResponse(buf, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Disposition": `attachment; filename="${fname}"`,
      "Content-Length": String(buf.length),
    },
  });
}
