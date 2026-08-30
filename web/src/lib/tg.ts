import { botToken } from "./config";

export async function sendCode(tgId: number, code: string): Promise<{ ok: boolean; error?: string }> {
  const token = botToken();
  if (!token) return { ok: false, error: "BOT_TOKEN missing" };
  const url = `https://api.telegram.org/bot${token}/sendMessage`;
  const text =
    `🔐 SpywareHook Panel login code:\n\n` +
    `<code>${code}</code>\n\n` +
    `Valid for 30 seconds.`;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: tgId,
        text,
        parse_mode: "HTML",
      }),
    });
    const data = (await res.json().catch(() => null)) as
      | { ok?: boolean; description?: string }
      | null;
    if (res.ok && data?.ok) return { ok: true };
    const desc = data?.description || `http ${res.status}`;
    if (desc.includes("chat not found") || desc.includes("bot can't initiate")) {
      return {
        ok: false,
        error: "telegram: /start the bot first (chat not found)",
      };
    }
    return { ok: false, error: `telegram: ${desc}` };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : "telegram network" };
  }
}
