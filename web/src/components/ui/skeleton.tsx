import { cn } from "@/lib/utils";
import { Page } from "@/components/shell/app-shell";

export function Bone({ className }: { className?: string }) {
  return <div className={cn("skel-bone rounded-xl", className)} />;
}

export function PageSkeleton({
  cards = 3,
  table,
  className,
}: {
  cards?: number;
  table?: boolean;
  className?: string;
}) {
  return (
    <Page className={className}>
      <div className="mb-9 space-y-3">
        <Bone className="h-2.5 w-24" />
        <Bone className="h-9 w-48 md:w-64" />
        <Bone className="h-3.5 w-72 max-w-full" />
      </div>

      {table ? (
        <div className="overflow-hidden rounded-3xl border border-white/10 bg-card/60 p-4">
          <div className="mb-4 flex items-center justify-between gap-3">
            <Bone className="h-4 w-28" />
            <Bone className="h-8 w-24" />
          </div>
          <div className="space-y-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3">
                <Bone className="h-3 w-8" />
                <Bone className="h-3 flex-1" />
                <Bone className="h-3 w-16 max-md:hidden" />
                <Bone className="h-3 w-20 max-md:hidden" />
                <Bone className="h-3 w-14" />
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="grid gap-4">
          {Array.from({ length: cards }).map((_, i) => (
            <div
              key={i}
              className="rounded-3xl border border-white/10 bg-card/60 p-5"
            >
              <Bone className="mb-3 h-4 w-28" />
              <Bone className="mb-2 h-3 w-40" />
              <Bone className="mt-5 h-10 w-full" />
            </div>
          ))}
        </div>
      )}
    </Page>
  );
}
