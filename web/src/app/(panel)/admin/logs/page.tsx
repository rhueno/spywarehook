"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { PageSkeleton } from "@/components/ui/skeleton";

type Row = {
  id: number;
  tg_id: number;
  level: string;
  msg: string;
  host: string;
  created_at: number;
};

function fmt(ts: number) {
  try {
    return new Date(ts * 1000).toLocaleString("en-US");
  } catch {
    return String(ts);
  }
}

export default function AdminLogsPage() {
  const router = useRouter();
  const [rows, setRows] = useState<Row[]>([]);
  const [q, setQ] = useState("");
  const [tg, setTg] = useState("");
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const sp = new URLSearchParams();
      if (q.trim()) sp.set("q", q.trim());
      if (/^\d+$/.test(tg.trim())) sp.set("tg", tg.trim());
      sp.set("limit", "300");
      const res = await fetch(`/api/admin/audit?${sp}`);
      if (res.status === 401) {
        router.push("/login");
        return;
      }
      const data = await res.json();
      setRows(data.rows || []);
    } finally {
      setBusy(false);
      setReady(true);
    }
  }, [q, tg, router]);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 5000);
    return () => clearInterval(t);
  }, [load]);

  if (!ready) return <PageSkeleton table />;

  return (
    <><Page>
        <PageHead kicker="admin / audit" title="Audit" desc="Runtime events" />

        <Surface glow={false}>
          <CardHeader className="flex flex-row items-center border-b border-rule px-5 py-4">
            <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <CardTitle className="leading-none">{rows.length} entries</CardTitle>
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  className="h-9 w-32"
                  value={tg}
                  onChange={(e) => setTg(e.target.value.replace(/\D/g, ""))}
                  placeholder="tg id"
                />
                <div className="relative min-w-[200px] flex-1">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    className="h-9 pl-9"
                    value={q}
                    onChange={(e) => setQ(e.target.value)}
                    placeholder="search…"
                  />
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void load()}
                  disabled={busy}
                >
                  refresh
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardBody className="space-y-2 px-5 pb-5 pt-4">
            {rows.map((r) => (
              <div
                key={r.id}
                className="rounded-2xl border border-rule bg-black/20 px-4 py-3"
              >
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[11px] text-zinc-500">
                  <Badge
                    tone={r.level === "err" ? "bad" : "muted"}
                    className="h-5 leading-none"
                  >
                    {r.level}
                  </Badge>
                  <span className="font-mono leading-none">tg {r.tg_id}</span>
                  <span className="font-mono leading-none">#{r.id}</span>
                  <span className="font-mono leading-none">{fmt(r.created_at)}</span>
                  {r.host ? (
                    <span className="max-w-full truncate font-mono leading-none">{r.host}</span>
                  ) : null}
                </div>
                <p className="mt-2 break-words font-mono text-sm leading-snug text-zinc-100">
                  {r.msg}
                </p>
              </div>
            ))}
            {!rows.length ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                no audit yet — Log.out lands here when exe/jar runs.
                <br />
                <span className="text-xs opacity-70">
                  PANEL_BASE_URL and HUB_URL must be production domains (spywarehook.org).
                </span>
              </p>
            ) : null}
          </CardBody>
        </Surface>
      </Page>
    </>
);
}
