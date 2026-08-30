import { NextRequest, NextResponse } from "next/server";
import { requireAdmin } from "@/lib/auth";
import { listAudit } from "@/lib/db";

export async function GET(req: NextRequest) {
  const sess = await requireAdmin();
  if (!sess) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const sp = req.nextUrl.searchParams;
  const tgIdRaw = sp.get("tg");
  const tgId = tgIdRaw && /^\d+$/.test(tgIdRaw) ? Number(tgIdRaw) : undefined;
  const q = sp.get("q") ?? "";
  const limit = Math.min(500, Math.max(1, Number(sp.get("limit") ?? 200)));
  const rows = await listAudit({ tgId, q, limit });
  return NextResponse.json({ rows });
}
