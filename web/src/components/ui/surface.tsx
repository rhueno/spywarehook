"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { MagicCard } from "@/components/ui/magic-card";

export function Surface({
  children,
  className,
  glow = true,
}: {
  children: ReactNode;
  className?: string;
  beam?: boolean;
  glow?: boolean;
}) {
  if (!glow) {
    return (
      <div
        className={cn(
          "relative overflow-hidden rounded-3xl border border-white/10 bg-card/80 backdrop-blur-xl",
          className,
        )}
      >
        {children}
      </div>
    );
  }

  return (
    <MagicCard
      className="rounded-3xl border border-white/10"
      gradientSize={260}
      gradientColor="#27272a"
      gradientOpacity={0.4}
      gradientFrom="#fafafa"
      gradientTo="#52525b"
    >
      <div className={cn("relative overflow-hidden rounded-3xl bg-card/85 backdrop-blur-xl", className)}>
        {children}
      </div>
    </MagicCard>
  );
}
