import { NextResponse } from "next/server";
import { isAdminTg, requireAdmin } from "@/lib/auth";
import { isOwnerKey } from "@/lib/config";
import { getKey, revokeKey } from "@/lib/db";

export async function POST(req: Request) {
  const sess = await requireAdmin();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const body = await req.json().catch(() => ({}));
  const key = String(body.key ?? "").trim();
  if (!key) return NextResponse.json({ error: "key required" }, { status: 400 });

  const row = await getKey(key);
  if (!row) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  if (isOwnerKey(row)) {
    return NextResponse.json({ error: "protected owner key" }, { status: 403 });
  }
  const bound = row.tg_id != null ? Number(row.tg_id) : null;
  if (bound != null && isAdminTg(bound)) {
    return NextResponse.json({ error: "protected admin key" }, { status: 403 });
  }

  const ok = await revokeKey(key);
  return NextResponse.json({ ok });
}
