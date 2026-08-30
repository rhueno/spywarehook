"use client";

import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as REKeyboardEvent,
  type PointerEvent as REPointerEvent,
  type WheelEvent as REWheelEvent,
} from "react";
import { Maximize2, Minimize2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { BorderBeam } from "@/components/ui/border-beam";
import { cn } from "@/lib/utils";
import { QUALITY, type Quality } from "./types";

export function ScreenView({
  live,
  online,
  streaming,
  fpsUi,
  quality,
  hasFrame,
  canvasRef,
  imgBox,
  onStart,
  onStop,
  onQuality,
  onPtr,
  onWheel,
  onKey,
}: {
  live: boolean;
  online: boolean;
  streaming: boolean;
  fpsUi: number;
  quality: Quality;
  hasFrame: boolean;
  canvasRef: React.RefObject<HTMLCanvasElement | null>;
  imgBox: React.RefObject<HTMLDivElement | null>;
  onStart: () => void;
  onStop: () => void;
  onQuality: (q: Quality) => void;
  onPtr: (e: REPointerEvent, kind: "move" | "down" | "up") => void;
  onWheel: (e: REWheelEvent) => void;
  onKey: (e: REKeyboardEvent, down: boolean) => void;
}) {
  const [h, setH] = useState(560);
  const [full, setFull] = useState(false);
  const drag = useRef<{ y: number; h: number } | null>(null);

  useEffect(() => {
    const next = Math.round(Math.min(window.innerHeight * 0.72, 820));
    setH(Math.max(420, next));
  }, []);

  useEffect(() => {
    if (!full) return;
    function onEsc(e: KeyboardEvent) {
      if (e.key === "Escape") setFull(false);
    }
    window.addEventListener("keydown", onEsc);
    return () => window.removeEventListener("keydown", onEsc);
  }, [full]);

  useEffect(() => {
    function move(e: PointerEvent) {
      if (!drag.current) return;
      const max = Math.max(420, window.innerHeight - 180);
      const nh = Math.min(max, Math.max(360, drag.current.h + (e.clientY - drag.current.y)));
      setH(nh);
    }
    function up() {
      drag.current = null;
    }
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
  }, []);

  const stage = (
    <div
      ref={imgBox}
      tabIndex={0}
      className={cn(
        "relative w-full cursor-crosshair overflow-hidden bg-black outline-none",
        full ? "h-full rounded-none" : "rounded-2xl ring-1 ring-white/10 focus:ring-white/30",
      )}
      style={full ? undefined : { height: h }}
      onContextMenu={(e) => e.preventDefault()}
      onPointerMove={(e) => onPtr(e, "move")}
      onPointerDown={(e) => onPtr(e, "down")}
      onPointerUp={(e) => onPtr(e, "up")}
      onWheel={onWheel}
      onKeyDown={(e) => onKey(e, true)}
      onKeyUp={(e) => onKey(e, false)}
    >
      <canvas
        ref={canvasRef}
        className={cn("h-full w-full object-contain", hasFrame ? "opacity-100" : "opacity-0")}
      />
                  {!hasFrame ? (
        <div className="pointer-events-none absolute inset-0 grid place-items-center text-sm text-muted-foreground">
          {streaming
            ? "waiting for frame…"
            : !live
              ? "waiting for ws"
              : !online
                ? "agent offline"
                : "press start"}
        </div>
      ) : null}
      <div className="pointer-events-none absolute left-3 top-3 flex items-center gap-2">
        <Badge tone={streaming ? "ok" : "muted"}>
          {streaming ? `live · ${fpsUi} fps` : "stopped"}
        </Badge>
      </div>
    </div>
  );

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" disabled={streaming} onClick={onStart}>
          start
        </Button>
        <Button size="sm" variant="outline" disabled={!streaming} onClick={onStop}>
          stop
        </Button>
        <div className="flex overflow-hidden rounded-2xl border border-white/10">
          {(Object.keys(QUALITY) as Quality[]).map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => onQuality(k)}
              className={cn(
                "px-3 py-1.5 font-mono text-[11px] uppercase tracking-[0.08em] transition",
                quality === k
                  ? "bg-white text-zinc-950"
                  : "text-muted-foreground hover:bg-white/[0.04] hover:text-foreground",
              )}
            >
              {QUALITY[k].label}
            </button>
          ))}
        </div>
        <Button size="icon" variant="ghost" onClick={() => setFull((v) => !v)}>
          {full ? <Minimize2 /> : <Maximize2 />}
        </Button>
        <span className="self-center font-mono text-xs text-muted-foreground">
          click + focus for keyboard
        </span>
      </div>
      {full ? (
        <div className="fixed inset-0 z-40 bg-black">
          {stage}
          <button
            type="button"
            onClick={() => setFull(false)}
            className="absolute right-4 top-4 z-50 rounded-xl border border-white/15 bg-black/50 px-3 py-1.5 font-mono text-[11px] uppercase tracking-wider text-white"
          >
            esc
          </button>
        </div>
      ) : (
        <div className="relative overflow-hidden rounded-3xl border border-white/10">
          <BorderBeam size={80} duration={9} colorFrom="#fafafa" colorTo="#71717a" borderWidth={1} />
          {stage}
          <div
            role="separator"
            onPointerDown={(e) => {
              e.preventDefault();
              drag.current = { y: e.clientY, h };
            }}
            className="absolute inset-x-0 bottom-0 z-10 flex h-4 cursor-ns-resize items-end justify-center"
          >
            <span className="mb-1 h-1 w-12 rounded-full bg-white/25" />
          </div>
        </div>
      )}
    </div>
  );
}
