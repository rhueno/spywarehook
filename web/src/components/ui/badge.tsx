import { cn } from "@/lib/utils";

export function Badge({
  className,
  tone = "muted",
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & { tone?: "ok" | "bad" | "muted" }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-medium tracking-wide",
        tone === "ok" && "bg-white/15 text-white",
        tone === "bad" && "bg-red-500/15 text-red-300",
        tone === "muted" && "bg-white/5 text-zinc-400",
        className,
      )}
      {...props}
    />
  );
}
