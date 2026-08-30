import { NextResponse } from "next/server";
import { requireSession } from "@/lib/auth";
import { getKey, getUser, maskWebhook, now } from "@/lib/db";

export async function GET() {
  const sess = await requireSession();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const user = await getUser(sess.tgId);
  if (!user) return NextResponse.json({ error: "user not found" }, { status: 404 });
  const row = user.key ? await getKey(user.key) : null;
  const left = row ? Math.max(0, row.expires_at - now()) : 0;
  return NextResponse.json({
    user: {
      tgId: user.tg_id,
      username: user.username,
      key: user.key,
      webhookMask: maskWebhook(user.webhook),
      hasWebhook: Boolean(user.webhook),
    },
    daysLeft: Math.floor(left / 86400),
    role: sess.kind === "admin" ? "admin" : "user",
  });
}
