"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Download, Search } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { PageSkeleton } from "@/components/ui/skeleton";

type ThemeRow = {
  id: number;
  tgId: number;
  username: string | null;
  label: string | null;
  createdAt: number;
};

function fmtTime(ts: number) {
  try {
    return new Date(ts * 1000).toLocaleString("en-US", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "—";
  }
}

export default function AdminHtmlPage() {
  const router = useRouter();
  const [themes, setThemes] = useState<ThemeRow[]>([]);
  const [q, setQ] = useState("");
  const [ready, setReady] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    const res = await fetch("/api/admin/themes");
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    const data = await res.json();
    if (!res.ok) {
      setErr(String(data.error || "load fail"));
      setReady(true);
      return;
    }
    setThemes(data.themes || []);
    setErr(null);
    setReady(true);
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = useMemo(() => {
    const s = q.trim().toLowerCase();
    if (!s) return themes;
    return themes.filter(
      (t) =>
        String(t.tgId).includes(s) ||
        String(t.id).includes(s) ||
        (t.username || "").toLowerCase().includes(s) ||
        (t.label || "").toLowerCase().includes(s),
    );
  }, [themes, q]);

  if (!ready) return <PageSkeleton table />;

  return (
    <Page>
      <PageHead kicker="admin / html" title="HTML" desc="Themes uploaded with EXE builds" />

      {err ? (
        <p className="mb-4 border border-mark/30 bg-mark/10 px-4 py-3 font-mono text-sm text-mark">
          {err}
        </p>
      ) : null}

      <Surface>
        <CardHeader>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <CardTitle>{rows.length} themes</CardTitle>
            <div className="relative w-full max-w-xs">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="tg / user / label / id"
              />
            </div>
          </div>
        </CardHeader>
        <CardBody className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                <th className="pb-3 pr-3">build</th>
                <th className="pb-3 pr-3">tg</th>
                <th className="pb-3 pr-3">username</th>
                <th className="pb-3 pr-3">label</th>
                <th className="pb-3 pr-3">when</th>
                <th className="pb-3">file</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.id} className="border-t border-rule/50 hover:bg-white/[0.02]">
                  <td className="py-3 pr-3 font-mono text-xs">#{t.id}</td>
                  <td className="py-3 pr-3 font-mono text-xs">{t.tgId}</td>
                  <td className="py-3 pr-3">{t.username || "—"}</td>
                  <td className="py-3 pr-3">{t.label || "—"}</td>
                  <td className="py-3 pr-3 font-mono text-xs text-muted-foreground">
                    {fmtTime(t.createdAt)}
                  </td>
                  <td className="py-3">
                    <Button asChild size="sm" variant="outline">
                      <a href={`/api/admin/themes/${t.id}/download`}>
                        <Download className="h-4 w-4" /> download
                      </a>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length ? (
            <p className="py-8 text-center text-sm text-muted-foreground">no html themes yet</p>
          ) : null}
        </CardBody>
      </Surface>
    </Page>
  );
}
