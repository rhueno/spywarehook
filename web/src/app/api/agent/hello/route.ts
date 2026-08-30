import { NextRequest, NextResponse } from "next/server";
import { mintAgentTok } from "@/lib/agent-tok";
import { hubPublicUrl } from "@/lib/config";
import { hookUser, upsertAgent, userActive } from "@/lib/db";
import { rateOk } from "@/lib/rate";

export const runtime = "nodejs";

export async function POST(req: NextRequest) {
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  if (!rateOk(`hello:${ip}`, 60, 60_000)) {
    return NextResponse.json({ error: "rate" }, { status: 429 });
  }
  const body = await req.json().catch(() => ({}));
  const hookId = String(body.hookId ?? "").trim();
  const hookSecret = String(body.hookSecret ?? "").trim();
  const hwid = String(body.hwid ?? "").trim().toLowerCase();
  if (!hookId || !hookSecret || !hwid || hwid.length < 8) {
    return NextResponse.json({ error: "missing" }, { status: 400 });
  }
  const user = await hookUser(hookId, hookSecret);
  if (!user) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const active = await userActive(Number(user.tg_id));
  if (!active.ok) return NextResponse.json({ error: active.why }, { status: 403 });

  const name = String(body.name ?? "").slice(0, 120);
  const os = String(body.os ?? "").slice(0, 120);
  const meta = JSON.stringify(body.meta ?? {});
  await upsertAgent({
    hwid,
    tgId: Number(user.tg_id),
    name,
    os,
    ip,
    meta,
  });
  const token = await mintAgentTok(Number(user.tg_id), hwid);
  const hub = hubPublicUrl();
  return NextResponse.json({
    ok: true,
    token,
    ws: `${hub.replace(/^http/, "ws")}/?role=agent&token=${encodeURIComponent(token)}`,
    hwid,
  });
}
