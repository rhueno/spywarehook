const buckets = new Map<string, number[]>();

export function rateOk(key: string, limit = 40, winMs = 60_000) {
  const now = Date.now();
  const arr = buckets.get(key) ?? [];
  const next = arr.filter((t) => t > now - winMs);
  if (next.length >= limit) {
    buckets.set(key, next);
    return false;
  }
  next.push(now);
  buckets.set(key, next);
  return true;
}
