"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowUpRight } from "lucide-react";
import { Page, PageHead } from "@/components/shell/app-shell";
import { Surface } from "@/components/ui/surface";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardDesc, CardHeader, CardTitle } from "@/components/ui/card";
import { play } from "@/lib/sound";
import { PageSkeleton } from "@/components/ui/skeleton";

type Me = {
  user: {
    tgId: number;
    username: string | null;
    key: string | null;
    webhookMask: string;
    hasWebhook: boolean;
  };
  daysLeft: number;
  role: string;
};

export default function DashPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [url, setUrl] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const res = await fetch("/api/me");
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    setMe(await res.json());
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  async function saveWebhook(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      const res = await fetch("/api/webhook", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "fail");
      play("ok");
      setUrl("");
      setMsg("Webhook saved.");
      await load();
    } catch (ex) {
      play("err");
      setErr(ex instanceof Error ? ex.message : "fail");
    } finally {
      setBusy(false);
    }
  }

  if (!me) return <PageSkeleton cards={3} />;

  return (
    <><Page>
        <PageHead
          kicker="01 / dashboard"
          title={String(me.user.username || me.user.tgId)}
          desc="License, webhook, and package output"
          action={<Badge tone="muted">{`${me.daysLeft} days`}</Badge>}
        />

        {msg ? (
          <p className="mb-4 border border-mark/30 bg-mark/10 px-4 py-3 font-mono text-sm text-mark">
            {msg}
          </p>
        ) : null}
        {err ? (
          <p className="mb-4 border border-destructive/30 bg-destructive/10 px-4 py-3 font-mono text-sm text-destructive">
            {err}
          </p>
        ) : null}

        <div className="grid gap-4">
          <Surface>
            <CardHeader>
              <CardTitle>License</CardTitle>
              <CardDesc>Active key</CardDesc>
            </CardHeader>
            <CardBody>
              <p className="font-mono text-sm text-mark">{me.user.key || "—"}</p>
              <p className="mt-2 font-mono text-xs text-muted-foreground">
                role · {me.role} · tg {me.user.tgId}
              </p>
            </CardBody>
          </Surface>

          <Surface>
            <CardHeader>
              <CardTitle>Webhook</CardTitle>
              <CardDesc>Discord notification URL</CardDesc>
            </CardHeader>
            <CardBody className="space-y-4">
              <p className="font-mono text-xs text-muted-foreground">
                saved · {me.user.webhookMask ?? "-"}
              </p>
              <form onSubmit={saveWebhook} className="flex flex-col gap-3 sm:flex-row">
                <Input
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://discord.com/api/webhooks/..."
                  required
                />
                <Button type="submit" disabled={busy} sound={false}>
                  save
                </Button>
              </form>
            </CardBody>
          </Surface>

          <Surface>
            <CardHeader>
              <CardTitle>Next step</CardTitle>
              <CardDesc>
                {me.user.hasWebhook
                  ? "Webhook ready — build a package."
                  : "Save a webhook first."}
              </CardDesc>
            </CardHeader>
            <CardBody className="flex flex-wrap gap-3">
              <Button asChild variant={me.user.hasWebhook ? "default" : "outline"}>
                <Link href="/build">
                  build <ArrowUpRight className="size-4" />
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link href="/hwid">
                  hwid <ArrowUpRight className="size-4" />
                </Link>
              </Button>
            </CardBody>
          </Surface>
        </div>
      </Page>
    </>
);
}
