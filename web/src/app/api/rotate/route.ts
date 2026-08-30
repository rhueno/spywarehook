import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/auth";
import { ensureHook } from "@/lib/db";

export async function POST() {
  const sess = await requireAdmin();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  await ensureHook(sess.tgId, true);
  return NextResponse.json({ ok: true });
}
