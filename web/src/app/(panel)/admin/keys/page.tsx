"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Ban, ChevronLeft, ChevronRight, Copy, Filter, Plus, Search } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardDesc, CardHeader, CardTitle } from "@/components/ui/card";
import { play } from "@/lib/sound";
import { cn } from "@/lib/utils";
import { PageSkeleton } from "@/components/ui/skeleton";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";

type KeyRow = {
  key: string;
  note: string;
  days: number;
  leftDays: number;
  status: string;
  username: string | null;
  tgId?: number | null;
  protected?: boolean;
  owner?: boolean;
};

const PAGE = 12;

export default function AdminKeysPage() {
  const router = useRouter();
  const [keys, setKeys] = useState<KeyRow[]>([]);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<"ALL" | "OK" | "REV" | "EXP">("ALL");
  const [page, setPage] = useState(0);
  const [days, setDays] = useState(30);
  const [note, setNote] = useState("");
  const [flash, setFlash] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const [pendingRevoke, setPendingRevoke] = useState<string | null>(null);
  const [revokeBusy, setRevokeBusy] = useState(false);

  const load = useCallback(async () => {
    const res = await fetch("/api/admin/data");
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    const data = await res.json();
    setKeys(data.keys);
    setReady(true);
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setPage(0);
  }, [q, filter]);

  const rows = useMemo(() => {
    const s = q.trim().toLowerCase();
    return keys.filter((k) => {
      if (filter !== "ALL" && k.status !== filter) return false;
      if (!s) return true;
      return (
        k.key.toLowerCase().includes(s) ||
        (k.note || "").toLowerCase().includes(s) ||
        (k.username || "").toLowerCase().includes(s)
      );
    });
  }, [keys, q, filter]);

  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE));
  const pageSafe = Math.min(page, totalPages - 1);
  const slice = rows.slice(pageSafe * PAGE, pageSafe * PAGE + PAGE);

  async function gen(e: React.FormEvent) {
    e.preventDefault();
    const res = await fetch("/api/admin/keys", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ days, note }),
    });
    const data = await res.json();
    if (!res.ok) {
      play("err");
      setFlash(data.error || "fail");
      return;
    }
    play("ok");
    setFlash(`created: ${data.key}`);
    setNote("");
    await navigator.clipboard.writeText(data.key).catch(() => undefined);
    await load();
  }

  async function revoke(key: string) {
    setRevokeBusy(true);
    try {
      const res = await fetch("/api/admin/revoke", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ key }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        play("err");
        setFlash(String(data.error || "revoke fail"));
        return;
      }
      play("ok");
      setFlash(`revoked: ${key}`);
      setPendingRevoke(null);
      await load();
    } catch {
      play("err");
      setFlash("revoke fail");
    } finally {
      setRevokeBusy(false);
    }
  }

  if (!ready) return <PageSkeleton cards={2} />;

  return (
    <Page>
      <PageHead kicker="admin / keys" title="Keys" />

      <ConfirmDialog
        open={Boolean(pendingRevoke)}
        title="Revoke key"
        desc={
          pendingRevoke ? (
            <>
              This cannot be undone.
              <p className="mt-2 break-all font-mono text-xs text-foreground/90">{pendingRevoke}</p>
            </>
          ) : null
        }
        confirmLabel="revoke"
        busy={revokeBusy}
        onCancel={() => {
          if (!revokeBusy) setPendingRevoke(null);
        }}
        onConfirm={() => {
          if (pendingRevoke) void revoke(pendingRevoke);
        }}
      />

      {flash ? (
        <p className="mb-4 border border-rule bg-card px-4 py-3 font-mono text-sm">{flash}</p>
      ) : null}

      <div className="grid gap-4">
        <Surface>
          <CardHeader>
            <CardTitle>New license</CardTitle>
            <CardDesc>Copied to clipboard when created</CardDesc>
          </CardHeader>
          <CardBody>
            <form onSubmit={gen} className="grid gap-3 md:grid-cols-[120px_1fr_auto]">
              <Input
                type="number"
                min={1}
                max={3650}
                value={days}
                onChange={(e) => setDays(Number(e.target.value))}
              />
              <Input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="note"
              />
              <Button type="submit" sound={false}>
                <Plus className="h-4 w-4" /> create
              </Button>
            </form>
          </CardBody>
        </Surface>

        <Surface>
          <CardHeader>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <CardTitle>
                {rows.length} key
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  OK first · EXP/REV last
                </span>
              </CardTitle>
              <div className="flex flex-wrap items-center gap-2">
                <div className="relative min-w-[200px] flex-1">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    className="pl-9"
                    value={q}
                    onChange={(e) => setQ(e.target.value)}
                    placeholder="key / note / user"
                  />
                </div>
                <div className="flex items-center gap-1 rounded-2xl border border-rule p-1">
                  <Filter className="ml-2 h-3.5 w-3.5 text-muted-foreground" />
                  {(["ALL", "OK", "REV", "EXP"] as const).map((f) => (
                    <button
                      key={f}
                      type="button"
                      onClick={() => {
                        play("tap");
                        setFilter(f);
                      }}
                      className={cn(
                        "rounded-2xl px-2.5 py-1 font-mono text-xs transition",
                        filter === f
                          ? "bg-mark/15 text-mark"
                          : "text-muted-foreground hover:text-foreground",
                      )}
                    >
                      {f}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </CardHeader>
          <CardBody className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                  <th className="pb-3 pr-3">status</th>
                  <th className="pb-3 pr-3">key</th>
                  <th className="pb-3 pr-3">left</th>
                  <th className="pb-3 pr-3">user</th>
                  <th className="pb-3 pr-3">note</th>
                  <th className="pb-3 text-right">actions</th>
                </tr>
              </thead>
              <tbody>
                {slice.map((k) => (
                  <tr key={k.key} className="border-t border-rule/50 hover:bg-white/[0.02]">
                    <td className="py-3 pr-3">
                      <Badge
                        tone={k.status === "OK" ? "ok" : k.status === "REV" ? "bad" : "muted"}
                      >
                        {k.status}
                      </Badge>
                    </td>
                    <td className="py-3 pr-3">
                      <div className="flex items-center gap-2">
                        <code className="font-mono text-xs">{k.key}</code>
                        <button
                          type="button"
                          className="text-muted-foreground hover:text-foreground"
                          onClick={() => {
                            void navigator.clipboard.writeText(k.key);
                            play("tap");
                          }}
                        >
                          <Copy className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                    <td className="py-3 pr-3 font-mono text-xs">{k.leftDays}d</td>
                    <td className="py-3 pr-3 font-mono text-xs">
                      {k.username ? `@${k.username}` : "—"}
                    </td>
                    <td className="py-3 pr-3 text-muted-foreground">{k.note || "—"}</td>
                    <td className="py-3 text-right">
                      {k.status === "REV" ? (
                        <span className="text-xs text-muted-foreground">—</span>
                      ) : k.protected ? (
                        <span className="font-mono text-xs text-muted-foreground">
                          {k.owner ? "owner" : "admin"}
                        </span>
                      ) : (
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          sound={false}
                          onClick={() => {
                            play("tap");
                            setPendingRevoke(k.key);
                          }}
                        >
                          <Ban className="h-3.5 w-3.5" /> revoke
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!rows.length ? (
              <p className="py-8 text-center text-sm text-muted-foreground">no results</p>
            ) : (
              <div className="mt-4 flex items-center justify-between border-t border-rule/50 pt-4">
                <p className="font-mono text-xs text-muted-foreground">
                  page {pageSafe + 1}/{totalPages}
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    sound={false}
                    disabled={pageSafe <= 0}
                    onClick={() => {
                      play("tap");
                      setPage((p) => Math.max(0, p - 1));
                    }}
                  >
                    <ChevronLeft className="h-4 w-4" /> prev
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    sound={false}
                    disabled={pageSafe + 1 >= totalPages}
                    onClick={() => {
                      play("tap");
                      setPage((p) => Math.min(totalPages - 1, p + 1));
                    }}
                  >
                    next <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </CardBody>
        </Surface>
      </div>
    </Page>
  );
}
