"use client";

import { useEffect, useRef, useState } from "react";
import { Send, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { TalkLine } from "./types";

export function ChatDock({
  open,
  live,
  lines,
  onOpen,
  onClose,
  onSend,
}: {
  open: boolean;
  live: boolean;
  lines: TalkLine[];
  onOpen: () => void;
  onClose: () => void;
  onSend: (text: string) => void;
}) {
  const [draft, setDraft] = useState("");
  const end = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    end.current?.scrollIntoView({ behavior: "smooth" });
  }, [lines.length, open]);

  if (!open) {
    return (
      <Button size="sm" variant="outline" disabled={!live} onClick={onOpen}>
        private chat
      </Button>
    );
  }

  return (
    <div className="flex h-full min-h-[420px] flex-col overflow-hidden rounded-3xl border border-white/10 bg-card/90">
      <div className="flex items-center justify-between gap-2 border-b border-white/10 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="font-heading text-sm font-semibold">Private chat</span>
          <Badge tone={live ? "ok" : "muted"}>{live ? "open" : "offline"}</Badge>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="grid size-8 place-items-center rounded-xl text-muted-foreground transition hover:bg-white/[0.06] hover:text-foreground"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="flex-1 space-y-2 overflow-auto px-3 py-3">
        {lines.length === 0 ? (
          <p className="px-2 py-8 text-center text-sm text-muted-foreground">
            Native window on the host. Messages land here.
          </p>
        ) : (
          lines.map((l, i) => (
            <div
              key={i}
              className={cn("flex", l.from === "op" ? "justify-end" : "justify-start")}
            >
              <div
                className={cn(
                  "max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-5",
                  l.from === "op"
                    ? "bg-white text-zinc-950"
                    : "border border-white/10 bg-white/[0.05] text-foreground",
                )}
              >
                {l.text}
              </div>
            </div>
          ))
        )}
        <div ref={end} />
      </div>
      <form
        className="flex gap-2 border-t border-white/10 p-3"
        onSubmit={(e) => {
          e.preventDefault();
          const t = draft.trim();
          if (!t) return;
          onSend(t);
          setDraft("");
        }}
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={500}
          placeholder="message…"
          className="h-10 flex-1 rounded-xl border border-white/10 bg-white/[0.03] px-3 text-sm outline-none focus:border-white/25"
        />
        <Button size="sm" type="submit" disabled={!live || !draft.trim()}>
          <Send className="size-3.5" />
        </Button>
      </form>
    </div>
  );
}
