"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { play } from "@/lib/sound";
import { Particles } from "@/components/ui/particles";
import { ShineBorder } from "@/components/ui/shine-border";

export default function LoginPage() {
  const [step, setStep] = useState<"key" | "otp">("key");
  const [key, setKey] = useState("");
  const [tgId, setTgId] = useState<number | null>(null);
  const [code, setCode] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);

  async function sendKey(e: React.FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setErr(null);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ key }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || `fail ${res.status}`);
      play("ok");
      setTgId(data.tgId);
      setStep("otp");
    } catch (ex) {
      play("err");
      setErr(ex instanceof Error ? ex.message : "fail");
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }

  async function sendOtp(nextCode?: string) {
    if (lock.current) return;
    const otp = (nextCode ?? code).replace(/\D/g, "").slice(0, 6);
    if (otp.length < 6 || tgId == null) return;
    lock.current = true;
    setBusy(true);
    setErr(null);
    try {
      const res = await fetch("/api/auth/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ key, code: otp, tgId }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || `fail ${res.status}`);
      play("ok");
      window.location.assign("/dash");
      return;
    } catch (ex) {
      play("err");
      setErr(ex instanceof Error ? ex.message : "fail");
      lock.current = false;
      setBusy(false);
    }
  }

  useEffect(() => {
    if (step !== "otp" || code.length !== 6 || busy || lock.current) return;
    void sendOtp(code);
  }, [code, step]);

  return (
    <main className="relative flex min-h-dvh items-center justify-center overflow-hidden px-4 py-8">
      <div className="pointer-events-none absolute inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(255,255,255,0.06),transparent_55%)]" />
        <Particles
          className="absolute inset-0"
          quantity={48}
          ease={80}
          staticity={50}
          size={0.4}
          color="#ffffff"
        />
      </div>

      <div className="relative z-10 w-full max-w-md page-enter">
        <div className="mb-8 text-center">
          <p className="text-[11px] font-medium uppercase tracking-[0.22em] text-zinc-500">spywarehook</p>
          <h1 className="mt-3 font-heading text-4xl font-semibold tracking-tight text-white">
            Sign in
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-zinc-400">License + Telegram code</p>
        </div>

        <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-card/80 p-6 shadow-[0_24px_80px_rgba(0,0,0,0.45)] backdrop-blur-xl">
          <ShineBorder
            borderWidth={1}
            duration={12}
            shineColor={["#ffffff33", "#a1a1aa55", "#ffffff22"]}
            className="rounded-3xl"
          />
          {err ? <p className="relative z-10 mb-4 text-center text-sm text-red-300">{err}</p> : null}
          <AnimatePresence mode="wait">
            {step === "key" ? (
              <motion.form
                key="key"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.2 }}
                onSubmit={sendKey}
                className="relative z-10 space-y-4"
              >
                <div className="space-y-2 text-center">
                  <label className="block text-xs text-zinc-500">license key</label>
                  <Input
                    value={key}
                    onChange={(e) => setKey(e.target.value.toUpperCase())}
                    placeholder="NF-XXXX-XXXX-XXXX"
                    required
                    autoFocus
                    className="text-center font-mono tracking-wide"
                  />
                </div>
                <Button type="submit" className="w-full" disabled={busy} sound={false}>
                  {busy ? "sending…" : "send code"}
                </Button>
              </motion.form>
            ) : (
              <motion.form
                key="otp"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.2 }}
                onSubmit={(e) => {
                  e.preventDefault();
                  void sendOtp();
                }}
                className="relative z-10 space-y-4"
              >
                <p className="text-center text-sm text-zinc-400">
                  Code sent to Telegram. Make sure you{" "}
                  <span className="text-zinc-200">/start</span> the bot.
                </p>
                <div className="relative overflow-hidden rounded-2xl border border-white/10 bg-zinc-950/60 p-1">
                  <ShineBorder
                    borderWidth={1}
                    duration={10}
                    shineColor={["#fafafa66", "#71717a44", "#fafafa33"]}
                    className="rounded-2xl"
                  />
                  <Input
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    placeholder="000000"
                    inputMode="numeric"
                    maxLength={6}
                    required
                    autoFocus
                    disabled={busy}
                    className="relative border-0 bg-transparent text-center font-mono text-2xl tracking-[0.35em] shadow-none focus-visible:ring-0"
                  />
                </div>
                <Button
                  type="submit"
                  className="w-full"
                  disabled={busy || code.length < 6}
                  sound={false}
                >
                  {busy ? "verifying…" : "sign in"}
                </Button>
                <button
                  type="button"
                  className="w-full text-xs text-zinc-500 hover:text-zinc-200"
                  onClick={() => {
                    play("tap");
                    setStep("key");
                    setCode("");
                    setErr(null);
                  }}
                >
                  change key
                </button>
              </motion.form>
            )}
          </AnimatePresence>
        </div>
      </div>
    </main>
  );
}
