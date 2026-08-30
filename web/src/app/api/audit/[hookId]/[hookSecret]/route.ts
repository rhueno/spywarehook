import { NextRequest, NextResponse } from "next/server";
import { addAudit, hookUser, userActive } from "@/lib/db";
import { rateOk } from "@/lib/rate";

export const runtime = "nodejs";

export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ hookId: string; hookSecret: string }> },
) {
  const { hookId, hookSecret } = await ctx.params;
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  if (!rateOk(`audit:${ip}:${hookId}`, 120, 60_000)) {
    return NextResponse.json({ error: "rate" }, { status: 429 });
  }
  const user = await hookUser(hookId, hookSecret);
  if (!user) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const active = await userActive(Number(user.tg_id));
  if (!active.ok) return NextResponse.json({ error: active.why }, { status: 403 });

  const body = await req.json().catch(() => ({}));
  const level = String(body.level ?? "info").slice(0, 16);
  const msg = String(body.msg ?? body.message ?? "").trim();
  if (!msg) return NextResponse.json({ error: "msg" }, { status: 400 });
  const host = String(body.host ?? req.headers.get("user-agent") ?? "").slice(0, 200);
  const res = await addAudit(Number(user.tg_id), level, msg, host);
  return NextResponse.json({ ok: true, id: res.id });
}
