"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  Cpu,
  FileCode2,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Package,
  ScrollText,
  Users,
  Volume2,
  VolumeX,
  FileText,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { muted as isMuted, play, setMuted } from "@/lib/sound";
import { Particles } from "@/components/ui/particles";

type Role = "admin" | "user" | null;

type NavItem = {
  href: string;
  label: string;
  icon: React.ReactNode;
};

const ROLE_KEY = "nf_role";
let roleMem: Role = null;

function readStoredRole(): Role {
  if (roleMem === "admin" || roleMem === "user") return roleMem;
  try {
    const v = sessionStorage.getItem(ROLE_KEY);
    if (v === "admin" || v === "user") {
      roleMem = v;
      return v;
    }
  } catch {
  }
  return null;
}

function writeRole(next: Role) {
  roleMem = next;
  try {
    if (next === "admin" || next === "user") sessionStorage.setItem(ROLE_KEY, next);
    else sessionStorage.removeItem(ROLE_KEY);
  } catch {
  }
}

function mergeRole(prev: Role, next: Role): Role {
  if (!next) return prev;
  if (prev === "admin" && next === "user") return "admin";
  return next;
}

function resolveInit(initialRole: Role): Role {
  return mergeRole(null, initialRole) || readStoredRole();
}

const MAIN_NAV: NavItem[] = [
  { href: "/dash", label: "Dashboard", icon: <LayoutDashboard className="size-4" strokeWidth={1.75} /> },
  { href: "/build", label: "Build", icon: <Package className="size-4" strokeWidth={1.75} /> },
  { href: "/hwid", label: "HWID", icon: <Cpu className="size-4" strokeWidth={1.75} /> },
  { href: "/logs", label: "Logs", icon: <FileText className="size-4" strokeWidth={1.75} /> },
];

const ADMIN_NAV: NavItem[] = [
  { href: "/admin/keys", label: "Keys", icon: <KeyRound className="size-4" strokeWidth={1.75} /> },
  { href: "/admin/users", label: "Users", icon: <Users className="size-4" strokeWidth={1.75} /> },
  { href: "/admin/html", label: "HTML", icon: <FileCode2 className="size-4" strokeWidth={1.75} /> },
  { href: "/admin/logs", label: "Audit", icon: <ScrollText className="size-4" strokeWidth={1.75} /> },
];

export function AppShell({
  children,
  initialRole = null,
}: {
  children: React.ReactNode;
  initialRole?: Role;
}) {
  const path = usePathname();
  const [role, setRoleState] = useState<Role>(() => resolveInit(initialRole));
  const [mute, setMute] = useState(false);
  const [pending, setPending] = useState<string | null>(null);

  function setRole(next: Role) {
    setRoleState((prev) => {
      const merged = mergeRole(prev, next);
      writeRole(merged);
      return merged;
    });
  }

  useEffect(() => {
    if (initialRole) setRole(initialRole);
  }, [initialRole]);

  useEffect(() => {
    setMute(isMuted());
    void fetch("/api/me")
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (d?.role === "admin" || d?.role === "user") setRole(d.role);
      })
      .catch(() => null);
  }, []);

  useEffect(() => {
    setPending(null);
  }, [path]);

  useEffect(() => {
    const wait = 300_000;
    let t: ReturnType<typeof setTimeout>;
    function kick() {
      writeRole(null);
      setRoleState(null);
      void fetch("/api/auth/logout", {
        method: "POST",
        credentials: "same-origin",
      }).finally(() => {
        window.location.assign("/login");
      });
    }
    function arm() {
      clearTimeout(t);
      t = setTimeout(kick, wait);
    }
    const ev = ["mousemove", "mousedown", "keydown", "scroll", "touchstart", "wheel"] as const;
    for (const e of ev) window.addEventListener(e, arm, { passive: true });
    arm();
    return () => {
      clearTimeout(t);
      for (const e of ev) window.removeEventListener(e, arm);
    };
  }, []);

  const isAdmin = role === "admin";

  const main = useMemo(() => MAIN_NAV, []);
  const admin = useMemo(() => (isAdmin ? ADMIN_NAV : []), [isAdmin]);

  function active(href: string) {
    if (href === "/hwid") return path === "/hwid" || path.startsWith("/hwid/");
    if (href === "/logs") return path === "/logs" || path.startsWith("/logs/");
    if (href === "/admin/keys") return path.startsWith("/admin/keys");
    if (href === "/admin/users") return path.startsWith("/admin/users");
    if (href === "/admin/logs") return path.startsWith("/admin/logs");
    return path === href || path.startsWith(href + "/");
  }

  function NavLink({ item }: { item: NavItem }) {
    const on = active(item.href);
    const wait = pending === item.href;
    return (
      <Link
        href={item.href}
        onClick={() => {
          play("tap");
          if (!on) setPending(item.href);
        }}
        className={cn(
          "group relative flex h-11 items-center gap-3 rounded-2xl px-3 text-sm font-medium transition duration-200 max-md:justify-center",
          on
            ? "bg-white text-zinc-950 shadow-[0_8px_24px_rgba(255,255,255,0.08)]"
            : "text-zinc-400 hover:bg-white/[0.06] hover:text-white",
          wait && !on && "opacity-70",
        )}
      >
        <span
          className={cn(
            "absolute left-1 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-zinc-950 transition-opacity max-md:hidden",
            on ? "opacity-100" : "opacity-0",
          )}
        />
        {item.icon}
        <span className="max-md:hidden">{item.label}</span>
      </Link>
    );
  }

  return (
    <div className="relative min-h-screen bg-background text-foreground">
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(255,255,255,0.05),transparent_55%)]" />
        <Particles
          className="absolute inset-0"
          quantity={22}
          ease={80}
          staticity={55}
          size={0.35}
          color="#ffffff"
        />
      </div>

      <aside className="fixed inset-y-0 left-0 z-40 flex w-60 flex-col p-4 max-md:w-[4.75rem] max-md:p-2">
        <div className="flex h-full flex-col rounded-3xl border border-white/10 bg-zinc-950/75 p-3 shadow-[0_24px_70px_rgba(0,0,0,0.5)] backdrop-blur-2xl">
          <div className="mb-5 flex h-12 items-center gap-3 rounded-2xl border border-white/[0.06] bg-white/[0.04] px-3 max-md:justify-center max-md:px-2">
            <span className="size-2.5 shrink-0 rounded-full bg-white shadow-[0_0_14px_rgba(255,255,255,0.6)]" />
            <div className="min-w-0 max-md:hidden">
              <p className="font-heading text-[15px] font-semibold tracking-tight text-white">
                spywarehook
              </p>
              <p className="mt-0.5 text-[10px] uppercase tracking-[0.18em] text-zinc-500">panel</p>
            </div>
          </div>

          <nav className="flex flex-1 flex-col gap-1 overflow-y-auto">
            {main.map((item) => (
              <NavLink key={item.href} item={item} />
            ))}

            {isAdmin ? (
              <div className="mt-3 space-y-1 border-t border-white/[0.06] pt-3">
                <p className="mb-1 px-3 text-[10px] font-medium uppercase tracking-[0.2em] text-zinc-600 max-md:px-0 max-md:text-center">
                  Admin
                </p>
                {admin.map((item) => (
                  <NavLink key={item.href} item={item} />
                ))}
              </div>
            ) : null}
          </nav>

          <div className="mt-2 space-y-1 border-t border-white/[0.06] pt-3">
            <button
              type="button"
              className="flex h-11 w-full items-center gap-3 rounded-2xl px-3 text-sm font-medium text-zinc-400 transition hover:bg-white/[0.06] hover:text-white max-md:justify-center"
              onClick={() => {
                const next = !isMuted();
                setMuted(next);
                setMute(next);
                if (!next) play("tap");
              }}
            >
              {mute ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
              <span className="max-md:hidden">{mute ? "Sound off" : "Sound on"}</span>
            </button>
            <button
              type="button"
              className="flex h-11 w-full items-center gap-3 rounded-2xl px-3 text-sm font-medium text-zinc-400 transition hover:bg-red-500/10 hover:text-red-300 max-md:justify-center"
              onClick={() => {
                play("tap");
                writeRole(null);
                setRoleState(null);
                void fetch("/api/auth/logout", {
                  method: "POST",
                  credentials: "same-origin",
                }).finally(() => {
                  window.location.assign("/login");
                });
              }}
            >
              <LogOut className="size-4" />
              <span className="max-md:hidden">Sign out</span>
            </button>
          </div>
        </div>
      </aside>

      <div className="relative z-10 min-h-screen pl-60 max-md:pl-[4.75rem]">
        <motion.div
          key={path}
          initial={{ opacity: 0, y: 5 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
        >
          {children}
        </motion.div>
      </div>
    </div>
  );
}

export function Page({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <main className={cn("mx-auto w-full max-w-4xl px-6 py-10 md:px-10", className)}>
      {children}
    </main>
  );
}

export function PageHead({
  kicker,
  title,
  desc,
  action,
}: {
  kicker: string;
  title: string;
  desc?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-9 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0 flex-1">
        <p className="text-[10px] font-medium uppercase tracking-[0.24em] text-zinc-500">
          {kicker}
        </p>
        <h1 className="mt-1.5 font-heading text-[1.85rem] font-semibold tracking-tight text-white md:text-[2.15rem] md:leading-[1.15]">
          {title}
        </h1>
        {desc ? (
          <p className="mt-1.5 max-w-xl text-[13px] leading-relaxed text-zinc-400">{desc}</p>
        ) : null}
      </div>
      {action ? (
        <div className="flex shrink-0 flex-wrap items-center gap-2 sm:justify-end">{action}</div>
      ) : null}
    </div>
  );
}
