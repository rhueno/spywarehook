import { NextResponse } from "next/server";

const MAX = 25 * 1024 * 1024;

function needsComponents(body: Buffer, ct: string) {
  if (!ct.toLowerCase().includes("json")) return false;
  const peek = body.toString("utf8", 0, Math.min(body.length, 8192));
  return peek.includes('"components"') || peek.includes('"flags"');
}

export function discordDest(webhook: string, query: string, body: Buffer, ct: string) {
  const u = new URL(webhook.trim());
  if (query) {
    const q = query.startsWith("?") ? query.slice(1) : query;
    for (const [k, v] of new URLSearchParams(q)) u.searchParams.set(k, v);
  }
  if (!u.searchParams.has("with_components") && needsComponents(body, ct)) {
    u.searchParams.set("with_components", "true");
  }
  return u.toString();
}

export function stripFootJson(json: string) {
  if (!json) return json;
  let needle = ',{"type":14,"divider":true,"spacing":1},{"type":10,"content":"-# ';
  let at = json.lastIndexOf(needle);
  if (at < 0) {
    needle = ',{"type":10,"content":"-# ';
    at = json.lastIndexOf(needle);
    if (at < 0) return json;
  }
  let p = at + needle.length;
  while (p < json.length) {
    const c = json.charAt(p);
    if (c === "\\") {
      p += 2;
      continue;
    }
    if (c === '"') break;
    p++;
  }
  if (p >= json.length) return json;
  p++;
  if (p < json.length && json.charAt(p) === "}") p++;
  return json.slice(0, at) + json.slice(p);
}

export function stripFootBody(body: Buffer, ct: string): Buffer {
  const low = ct.toLowerCase();
  if (low.includes("application/json") || low.includes("+json")) {
    return Buffer.from(stripFootJson(body.toString("utf8")), "utf8");
  }
  if (!low.includes("multipart/form-data")) return body;
  const text = body.toString("binary");
  const mark = 'name="payload_json"';
  const i = text.indexOf(mark);
  if (i < 0) return body;
  const hdrEnd = text.indexOf("\r\n\r\n", i);
  if (hdrEnd < 0) return body;
  const start = hdrEnd + 4;
  const next = text.indexOf("\r\n--", start);
  if (next < 0) return body;
  let jsonPart = text.slice(start, next);
  if (jsonPart.endsWith("\r\n")) jsonPart = jsonPart.slice(0, -2);
  const stripped = stripFootJson(jsonPart);
  if (stripped === jsonPart) return body;
  const out = text.slice(0, start) + stripped + text.slice(next);
  return Buffer.from(out, "binary");
}

export async function postDiscord(
  dest: string,
  body: Uint8Array,
  contentType: string,
  ua: string,
) {
  if (body.byteLength > MAX) {
    return new NextResponse("too large", { status: 413 });
  }

  let lastErr = "network";
  for (let i = 0; i < 3; i++) {
    try {
      const res = await fetch(dest, {
        method: "POST",
        headers: {
          "Content-Type": contentType,
          "User-Agent": ua,
        },
        body: body as BodyInit,
        signal: AbortSignal.timeout(55_000),
      });

      if (res.status === 429 && i < 2) {
        const ra = Number(res.headers.get("retry-after") || "1");
        await new Promise((r) => setTimeout(r, Math.min(5000, (ra || 1) * 1000)));
        continue;
      }

      if (res.status === 204 || (res.ok && !res.headers.get("content-length"))) {
        return new NextResponse(null, { status: 204 });
      }

      const raw = Buffer.from(await res.arrayBuffer());
      if (res.ok && raw.length === 0) {
        return new NextResponse(null, { status: 204 });
      }

      return new NextResponse(raw, {
        status: res.status,
        headers: {
          "Content-Type": res.headers.get("content-type") ?? "text/plain",
        },
      });
    } catch (e) {
      lastErr = e instanceof Error ? e.message : "network";
      if (i < 2) {
        await new Promise((r) => setTimeout(r, 400 * (i + 1)));
        continue;
      }
    }
  }

  return NextResponse.json({ ok: false, error: lastErr }, { status: 502 });
}
