import { NextResponse } from "next/server";
import { requireSession, isAdminSession } from "@/lib/auth";
import { getAgent } from "@/lib/db";
import { mintOpTok } from "@/lib/agent-tok";
import { hubPublicUrl } from "@/lib/config";

export async function GET(
  _req: Request,
  ctx: { params: Promise<{ hwid: string }> },
) {
  const sess = await requireSession();
  if (!sess) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const { hwid } = await ctx.params;
  const agent = await getAgent(hwid);
  if (!agent) return NextResponse.json({ error: "not found" }, { status: 404 });
  if (!isAdminSession(sess) && Number(agent.tg_id) !== sess.tgId) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  const token = await mintOpTok(sess.tgId, hwid);
  const hub = hubPublicUrl();
  return NextResponse.json({
    agent: {
      hwid: agent.hwid,
      name: agent.name,
      os: agent.os,
      ip: agent.ip,
      lastSeen: agent.last_seen,
      online: Boolean(agent.online),
      meta: (() => {
        try {
          return JSON.parse(agent.meta || "{}");
        } catch {
          return {};
        }
      })(),
    },
    token,
    ws: `${hub.replace(/^http/, "ws")}/?role=op&token=${encodeURIComponent(token)}`,
  });
}
