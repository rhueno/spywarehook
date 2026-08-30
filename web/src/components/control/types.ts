export type Tab = "screen" | "files" | "sys" | "proc" | "shell" | "clip" | "info";
export type Quality = "low" | "med" | "high";
export type FsEntry = { n: string; d: boolean; s: number };
export type TalkLine = { from: "op" | "pc"; text: string; at: number };

export type AgentInfo = {
  hwid: string;
  name: string;
  os: string;
  ip: string;
  lastSeen: number;
  online: boolean;
  meta: Record<string, unknown>;
};

export const QUALITY: Record<Quality, { fps: number; q: number; scale: number; label: string }> = {
  low: { fps: 8, q: 0.55, scale: 0.5, label: "low" },
  med: { fps: 12, q: 0.72, scale: 0.7, label: "med" },
  high: { fps: 15, q: 0.85, scale: 0.85, label: "high" },
};

export function streamPayload(q: Quality) {
  const p = QUALITY[q];
  return { op: "screen.start", fps: p.fps, q: p.q, scale: p.scale };
}

export function fmtSize(n: number) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

export function short(h: string) {
  if (h.length < 12) return h;
  return `${h.slice(0, 8)}…${h.slice(-4)}`;
}

export function joinPath(base: string, name: string) {
  if (!base) return name;
  if (base.endsWith("\\") || base.endsWith("/")) return base + name;
  return `${base}\\${name}`;
}

export function parentPath(p: string) {
  const s = p.replace(/[\\/]+$/, "");
  const i = Math.max(s.lastIndexOf("\\"), s.lastIndexOf("/"));
  if (i <= 0) return "";
  if (i === 2 && s[1] === ":") return s.slice(0, 3);
  return s.slice(0, i);
}
