"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { PageSkeleton } from "@/components/ui/skeleton";

type UserRow = {
  tgId: number;
  username: string | null;
  key: string | null;
  webhook: string;
  ingest: boolean;
  updatedAt: number;
};

export default function AdminUsersPage() {
  const router = useRouter();
  const [users, setUsers] = useState<UserRow[]>([]);
  const [q, setQ] = useState("");
  const [ready, setReady] = useState(false);

  const load = useCallback(async () => {
    const res = await fetch("/api/admin/data");
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    const data = await res.json();
    setUsers(data.users);
    setReady(true);
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = useMemo(() => {
    const s = q.trim().toLowerCase();
    if (!s) return users;
    return users.filter(
      (u) =>
        String(u.tgId).includes(s) ||
        (u.username || "").toLowerCase().includes(s) ||
        (u.key || "").toLowerCase().includes(s),
    );
  }, [users, q]);

  if (!ready) return <PageSkeleton table />;

  return (
    <><Page>
        <PageHead kicker="admin / users" title="Users" />

        <Surface>
          <CardHeader>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <CardTitle>{rows.length} users</CardTitle>
              <div className="relative w-full max-w-xs">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                <Input
                  className="pl-9"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="tg / user / key"
                />
              </div>
            </div>
          </CardHeader>
          <CardBody className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="font-mono text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                  <th className="pb-3 pr-3">tg</th>
                  <th className="pb-3 pr-3">username</th>
                  <th className="pb-3 pr-3">key</th>
                  <th className="pb-3 pr-3">webhook</th>
                  <th className="pb-3">status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => (
                  <tr key={u.tgId} className="border-t border-rule/50 hover:bg-white/[0.02]">
                    <td className="py-3 pr-3 font-mono text-xs">{u.tgId}</td>
                    <td className="py-3 pr-3">{u.username || "—"}</td>
                    <td className="py-3 pr-3 font-mono text-xs">{u.key || "—"}</td>
                    <td className="py-3 pr-3 font-mono text-xs text-muted-foreground">
                      {u.webhook}
                    </td>
                    <td className="py-3">
                      <Badge tone={u.ingest ? "ok" : "muted"}>
                        {u.ingest ? "ready" : "none"}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!rows.length ? (
              <p className="py-8 text-center text-sm text-muted-foreground">no users</p>
            ) : null}
          </CardBody>
        </Surface>
      </Page>
    </>
);
}
