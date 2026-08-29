export type Freshness = "fresh" | "old" | "stale" | "unknown";

export function classifyFreshness(timestamp: string | null, now = new Date()): Freshness {
  if (!timestamp) return "unknown";
  const age = now.valueOf() - new Date(timestamp).valueOf();
  if (!Number.isFinite(age)) return "unknown";
  if (age <= 24 * 60 * 60 * 1000) return "fresh";
  if (age <= 7 * 24 * 60 * 60 * 1000) return "old";
  return "stale";
}

export function formatAge(timestamp: string | null, now = new Date()): string {
  if (!timestamp) return "date not reported";
  const ageMs = Math.max(0, now.valueOf() - new Date(timestamp).valueOf());
  if (!Number.isFinite(ageMs)) return "date not reported";
  const hours = Math.floor(ageMs / (60 * 60 * 1000));
  if (hours < 1) return "updated less than 1h ago";
  if (hours < 24) return `updated ${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `updated ${days}d ago`;
}
