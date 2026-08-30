import { NextResponse } from "next/server";
import { requireSession } from "@/lib/auth";
import { cancelBuild, getBuild } from "@/lib/db";

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
  const { artifact, icon, ...safe } = build;
  void artifact;
  void icon;
  return NextResponse.json({ build: safe });
}

export async function DELETE(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const { id } = await ctx.params;
  const bid = Number(id);
  if (!Number.isFinite(bid) || bid <= 0) {
    return NextResponse.json({ error: "id" }, { status: 400 });
  }
  const res = await cancelBuild(sess.tgId, bid);
  if (!res?.ok) {
    return NextResponse.json(
      { error: res?.error || "could not cancel" },
      { status: res?.error === "yok" || res?.error === "not found" ? 404 : 409 },
    );
  }
  return NextResponse.json({ ok: true, id: bid });
}
