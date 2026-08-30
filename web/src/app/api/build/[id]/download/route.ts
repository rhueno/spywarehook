import fs from "fs";
import path from "path";
import { NextResponse } from "next/server";
import { requireSession } from "@/lib/auth";
import { getBuild, projectRoot } from "@/lib/db";

export async function GET(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const { id } = await ctx.params;
  const build = await getBuild(Number(id));
  if (!build || Number(build.tg_id) !== sess.tgId) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  if (build.kind !== "jar" || build.status !== "ok") {
    return NextResponse.json({ error: "not downloadable" }, { status: 400 });
  }

  const root = path.resolve(projectRoot(), "dist");
  const candidate = build.artifact
    ? path.resolve(build.artifact)
    : path.join(root, "zombiesurvival-1.4.2.jar");
  const ok =
    candidate === root ||
    candidate.startsWith(root + path.sep) ||
    candidate.startsWith(root + "/");
  if (!ok) {
    return NextResponse.json({ error: "not downloadable" }, { status: 400 });
  }
  if (!fs.existsSync(candidate)) {
    return NextResponse.json({ error: "file missing" }, { status: 404 });
  }

  const buf = fs.readFileSync(candidate);
  const label = (build.label || "zombiesurvival").replace(/[^\w\- ]+/g, "").trim() || "zombiesurvival";
  const fname = `${label.replace(/\s+/g, "") || "build"}.jar`;
  return new NextResponse(buf, {
    status: 200,
    headers: {
      "Content-Type": "application/java-archive",
      "Content-Disposition": `attachment; filename="${fname}"`,
      "Content-Length": String(buf.length),
    },
  });
}
