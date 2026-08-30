"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, RefreshCw } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { PageSkeleton } from "@/components/ui/skeleton";

type Agent = {
  hwid: string;
  name: string;
  os: string;
  ip: string;
  lastSeen: number;
  online: boolean;
};

function shortHwid(h: string) {
  if (!h || h.length < 12) return h;
  return `${h.slice(0, 8)}…${h.slice(-4)}`;
}

function ago(ts: number) {
  if (!ts) return "—";
  const s = Math.max(0, Math.floor(Date.now() / 1000 - ts));
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  if (s < 86400) return `${Math.floor(s / 3600)}h`;
  return `${Math.floor(s / 86400)}d`;
}

export default function HwidPage() {
  const router = useRouter();
  const [rows, setRows] = useState<Agent[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    setErr(null);
    try {
      const res = await fetch("/api/agents");
      if (res.status === 401) {
        router.push("/login");
        return;
      }
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "fail");
      setRows(data.agents || []);
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "fail");
    } finally {
      setBusy(false);
      setReady(true);
    }
  }, [router]);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 8000);
    return () => clearInterval(t);
  }, [load]);

  if (!ready) return <PageSkeleton table />;

  return (
    <><Page>
        <PageHead
          kicker="03 / hwid"
          title="HWID"
          desc="Connected machines"
          action={
            <Button variant="outline" size="sm" onClick={() => void load()} disabled={busy}>
              <RefreshCw className={`h-3.5 w-3.5 ${busy ? "animate-spin" : ""}`} />
              refresh
            </Button>
          }
        />

        {err ? (
          <p className="mb-4 border border-destructive/30 bg-destructive/10 px-4 py-3 font-mono text-sm text-destructive">
            {err}
          </p>
        ) : null}

        <Surface className="overflow-hidden p-0" glow={false}>
          <CardHeader className="flex flex-row items-center border-b border-rule px-5 py-4">
            <CardTitle className="leading-none">{rows.length} agent</CardTitle>
          </CardHeader>
          <CardBody className="overflow-x-auto p-0">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-rule font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                  <th className="px-5 py-3 font-medium">Host</th>
                  <th className="px-5 py-3 font-medium">HWID</th>
                  <th className="px-5 py-3 font-medium">OS</th>
                  <th className="px-5 py-3 font-medium">Seen</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 font-medium" />
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-5 py-12 text-center text-muted-foreground">
                      No agents yet
                    </td>
                  </tr>
                ) : (
                  rows.map((a) => (
                    <tr
                      key={a.hwid}
                      className="border-b border-rule/50 transition hover:bg-white/[0.02]"
                    >
                      <td className="px-5 py-3 font-medium">{a.name || "—"}</td>
                      <td className="px-5 py-3 font-mono text-xs text-muted-foreground">
                        {shortHwid(a.hwid)}
                      </td>
                      <td className="px-5 py-3 text-muted-foreground">{a.os || "—"}</td>
                      <td className="px-5 py-3 font-mono text-xs text-muted-foreground">
                        {ago(a.lastSeen)}
                      </td>
                      <td className="px-5 py-3">
                        <Badge tone={a.online ? "ok" : "muted"}>
                          {a.online ? "online" : "offline"}
                        </Badge>
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Button asChild size="sm" variant="outline">
                          <Link href={`/hwid/${encodeURIComponent(a.hwid)}`}>
                            Control
                            <ArrowRight className="h-3.5 w-3.5" />
                          </Link>
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </CardBody>
        </Surface>
      </Page>
    </>
);
}
