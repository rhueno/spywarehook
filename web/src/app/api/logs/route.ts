import { NextRequest, NextResponse } from "next/server";
import { isAdminSession, requireSession } from "@/lib/auth";
import { listStealLogs } from "@/lib/db";

export async function GET(req: NextRequest) {
  const sess = await requireSession();
  if (!sess) return NextResponse.json({ error: "auth" }, { status: 401 });
  const sp = req.nextUrl.searchParams;
  const limit = Math.min(500, Math.max(1, Number(sp.get("limit") || 200) || 200));
  const admin = isAdminSession(sess);
  let tgId: number | undefined = sess.tgId;
  if (admin) {
    const f = sp.get("tgId") || sp.get("tg");
    if (f && /^\d+$/.test(f)) tgId = Number(f);
    else tgId = undefined;
  }
  const rows = (await listStealLogs({ tgId, limit })) || [];
  return NextResponse.json({
    rows,
    role: admin ? "admin" : "user",
  });
}
