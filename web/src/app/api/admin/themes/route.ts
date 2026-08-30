import { NextResponse } from "next/server";
import { requireAdmin } from "@/lib/auth";
import { listThemes } from "@/lib/db";

export async function GET() {
  const sess = await requireAdmin();
  if (!sess) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  try {
    const rows = await listThemes(200);
    return NextResponse.json({
      themes: rows.map((r) => ({
        id: r.id,
        tgId: r.tg_id,
        username: r.username || null,
        label: r.label || null,
        createdAt: r.created_at,
      })),
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "db fail";
    return NextResponse.json({ error: msg.slice(0, 500) }, { status: 500 });
  }
}
