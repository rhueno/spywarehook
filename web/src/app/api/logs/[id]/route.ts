import { NextRequest, NextResponse } from "next/server";
import { isAdminSession, requireSession } from "@/lib/auth";
import { getStealLog } from "@/lib/db";

export async function GET(
  _req: NextRequest,
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
  return NextResponse.json({ row });
}
