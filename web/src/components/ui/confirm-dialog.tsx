"use client";

import { useEffect, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function ConfirmDialog({
  open,
  title,
  desc,
  confirmLabel = "confirm",
  cancelLabel = "cancel",
  busy = false,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  desc?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, busy, onCancel]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button
        type="button"
        aria-label="close"
        className="absolute inset-0 bg-black/60 backdrop-blur-sm transition-opacity"
        disabled={busy}
        onClick={() => {
          if (!busy) onCancel();
        }}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        className={cn(
          "relative z-10 w-full max-w-md overflow-hidden rounded-3xl border border-white/10",
          "bg-card/95 shadow-2xl shadow-black/40 backdrop-blur-xl",
          "animate-in fade-in zoom-in-95 duration-150",
        )}
      >
        <div className="space-y-1.5 px-6 pt-6">
          <h3
            id="confirm-dialog-title"
            className="font-heading text-xl font-semibold tracking-tight text-foreground"
          >
            {title}
          </h3>
          {desc ? <div className="text-sm text-muted-foreground">{desc}</div> : null}
        </div>
        <div className="flex flex-wrap justify-end gap-2 px-6 pb-6 pt-5">
          <Button
            type="button"
            variant="outline"
            sound={false}
            disabled={busy}
            onClick={onCancel}
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            variant="danger"
            sound={false}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? "…" : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
