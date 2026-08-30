import { NextResponse } from "next/server";
import { isAdminTg, setSession } from "@/lib/auth";
import { takeOtp, keyLoginOk } from "@/lib/db";
import { rateOk } from "@/lib/rate";

export async function POST(req: Request) {
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  if (!rateOk(`otp:${ip}`, 8, 600_000)) {
    return NextResponse.json({ error: "too many attempts" }, { status: 429 });
  }
  const body = await req.json().catch(() => ({}));
  const key = String(body.key ?? "").trim();
  const code = String(body.code ?? "").trim();
  const tgId = Number(body.tgId);
  if (!key || !code || !tgId) {
    return NextResponse.json({ error: "missing fields" }, { status: 400 });
  }
  const res = await keyLoginOk(key);
  if (!res.ok || !res.user || Number(res.user.tg_id) !== tgId) {
    return NextResponse.json({ error: "invalid session" }, { status: 400 });
  }
  if (!(await takeOtp(tgId, code))) {
    return NextResponse.json({ error: "invalid/expired code" }, { status: 400 });
  }
  const admin = isAdminTg(tgId);
  await setSession({ kind: admin ? "admin" : "user", tgId, key });
  return NextResponse.json({ ok: true, role: admin ? "admin" : "user" });
}
