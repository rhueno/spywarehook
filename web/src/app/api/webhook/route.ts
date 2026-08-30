import { NextResponse } from "next/server";
import { requireSession } from "@/lib/auth";
import { setWebhook, validDiscordWebhook } from "@/lib/db";

export async function POST(req: Request) {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const body = await req.json().catch(() => ({}));
  const url = String(body.url ?? "").trim();
  if (!validDiscordWebhook(url)) {
    return NextResponse.json({ error: "invalid discord webhook" }, { status: 400 });
  }
  await setWebhook(sess.tgId, url);
  return NextResponse.json({ ok: true });
}
