"use client";

import { useEffect, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export function WarnDialog({
  open,
  title,
  text,
  sticky,
  busy,
  onTitle,
  onText,
  onSticky,
  onSend,
  onStop,
  onClose,
}: {
  open: boolean;
  title: string;
  text: string;
  sticky: boolean;
  busy?: boolean;
  onTitle: (v: string) => void;
  onText: (v: string) => void;
  onSticky: (v: boolean) => void;
  onSend: () => void;
  onStop: () => void;
  onClose: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, busy, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button
        type="button"
        aria-label="close"
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        disabled={busy}
        onClick={() => {
          if (!busy) onClose();
        }}
      />
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          "relative z-10 w-full max-w-lg overflow-hidden rounded-3xl border border-white/10",
          "bg-card/95 shadow-2xl shadow-black/40 backdrop-blur-xl",
        )}
      >
        <div className="space-y-1.5 px-6 pt-6">
          <h3 className="font-heading text-xl font-semibold tracking-tight">Warn</h3>
          <p className="text-sm text-muted-foreground">
            Native Windows message on the host. Sticky loops until you stop it.
          </p>
        </div>
        <div className="space-y-3 px-6 pt-5">
          <label className="block space-y-1.5">
            <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
              title
            </span>
            <Input value={title} onChange={(e) => onTitle(e.target.value)} maxLength={120} />
          </label>
          <label className="block space-y-1.5">
            <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
              message
            </span>
            <textarea
              value={text}
              onChange={(e) => onText(e.target.value)}
              maxLength={2000}
              rows={5}
              className="min-h-[120px] w-full rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 text-sm text-foreground outline-none transition focus:border-white/25 focus:ring-4 focus:ring-white/5"
            />
          </label>
          <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3">
            <input
              type="checkbox"
              checked={sticky}
              onChange={(e) => onSticky(e.target.checked)}
              className="mt-0.5 size-4 accent-white"
            />
            <span className="text-sm leading-5">
              Unlimited — OK still reopens the box until Stop
            </span>
          </label>
        </div>
        <div className="flex flex-wrap justify-end gap-2 px-6 pb-6 pt-5">
          <Button type="button" variant="outline" sound={false} disabled={busy} onClick={onClose}>
            cancel
          </Button>
          <Button type="button" variant="ghost" sound={false} disabled={busy} onClick={onStop}>
            stop
          </Button>
          <Button type="button" variant="danger" sound={false} disabled={busy || !text.trim()} onClick={onSend}>
            send
          </Button>
        </div>
      </div>
    </div>
  );
}

export function FieldNote({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-2xl border border-destructive/25 bg-destructive/10 px-3 py-2 font-mono text-xs text-red-300">
      {children}
    </p>
  );
}
