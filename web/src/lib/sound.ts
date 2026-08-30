const KEY = "noface_mute";

let ctx: AudioContext | null = null;

function ac(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!ctx) {
    const C =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext;
    if (!C) return null;
    ctx = new C();
  }
  return ctx;
}

export function muted() {
  if (typeof window === "undefined") return true;
  return localStorage.getItem(KEY) === "1";
}

export function setMuted(v: boolean) {
  if (typeof window === "undefined") return;
  localStorage.setItem(KEY, v ? "1" : "0");
}

export type SoundKind = "tap" | "ok" | "err";

export function play(kind: SoundKind = "tap") {
  if (typeof window === "undefined" || muted()) return;
  const c = ac();
  if (!c) return;
  void c.resume().catch(() => null);

  const now = c.currentTime;
  const o = c.createOscillator();
  const g = c.createGain();
  const f = c.createBiquadFilter();
  o.connect(f);
  f.connect(g);
  g.connect(c.destination);
  f.type = "lowpass";
  f.frequency.setValueAtTime(4200, now);

  if (kind === "tap") {
    o.type = "sine";
    o.frequency.setValueAtTime(620, now);
    o.frequency.exponentialRampToValueAtTime(420, now + 0.07);
    g.gain.setValueAtTime(0.0001, now);
    g.gain.exponentialRampToValueAtTime(0.18, now + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, now + 0.1);
    o.start(now);
    o.stop(now + 0.11);
  } else if (kind === "ok") {
    o.type = "triangle";
    o.frequency.setValueAtTime(520, now);
    o.frequency.setValueAtTime(780, now + 0.08);
    g.gain.setValueAtTime(0.0001, now);
    g.gain.exponentialRampToValueAtTime(0.22, now + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, now + 0.22);
    o.start(now);
    o.stop(now + 0.24);

    const o2 = c.createOscillator();
    const g2 = c.createGain();
    o2.type = "sine";
    o2.connect(g2);
    g2.connect(c.destination);
    o2.frequency.setValueAtTime(1040, now + 0.06);
    g2.gain.setValueAtTime(0.0001, now + 0.06);
    g2.gain.exponentialRampToValueAtTime(0.1, now + 0.08);
    g2.gain.exponentialRampToValueAtTime(0.0001, now + 0.22);
    o2.start(now + 0.06);
    o2.stop(now + 0.24);
  } else {
    o.type = "sawtooth";
    o.frequency.setValueAtTime(220, now);
    o.frequency.exponentialRampToValueAtTime(95, now + 0.16);
    g.gain.setValueAtTime(0.0001, now);
    g.gain.exponentialRampToValueAtTime(0.2, now + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, now + 0.2);
    o.start(now);
    o.stop(now + 0.22);
  }
}
