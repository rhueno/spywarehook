import { NextResponse } from "next/server";
import { putOtp, keyLoginOk } from "@/lib/db";
import { sendCode } from "@/lib/tg";
import { rateOk } from "@/lib/rate";
import { randomInt } from "crypto";

export async function POST(req: Request) {
  try {
    const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
    if (!rateOk(`login:${ip}`, 8, 600_000)) {
      return NextResponse.json({ error: "too many attempts" }, { status: 429 });
    }
    const body = await req.json().catch(() => ({}));
    const key = String(body.key ?? "").trim();
    if (!key) return NextResponse.json({ error: "key required" }, { status: 400 });

    const res = await keyLoginOk(key);
    if (!res.ok || !res.user) {
      return NextResponse.json({ error: "invalid key" }, { status: 400 });
    }

    const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
    await putOtp(Number(res.user.tg_id), code, 30);
    const sent = await sendCode(Number(res.user.tg_id), code);
    if (!sent.ok) {
      return NextResponse.json(
        { error: sent.error || "telegram code could not be sent" },
        { status: 502 },
      );
    }
    return NextResponse.json({ ok: true, tgId: Number(res.user.tg_id) });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "fail";
    return NextResponse.json({ error: msg.slice(0, 200) }, { status: 500 });
  }
}
