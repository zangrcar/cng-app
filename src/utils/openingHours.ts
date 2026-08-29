import type { DayKey, NormalizedOpeningHours } from "../types";

const dayKeys: DayKey[] = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];

type RawHours = {
  giornoSettimanaId?: unknown;
  oraAperturaMattina?: unknown;
  oraChiusuraMattina?: unknown;
  oraAperturaPomeriggio?: unknown;
  oraChiusuraPomeriggio?: unknown;
  flagOrarioContinuato?: unknown;
  oraAperturaOrarioContinuato?: unknown;
  oraChiusuraOrarioContinuato?: unknown;
  flagH24?: unknown;
  flagChiusura?: unknown;
  flagNonComunicato?: unknown;
};

function emptyDays(): NormalizedOpeningHours["days"] {
  return {
    mon: { status: "unknown", periods: [] },
    tue: { status: "unknown", periods: [] },
    wed: { status: "unknown", periods: [] },
    thu: { status: "unknown", periods: [] },
    fri: { status: "unknown", periods: [] },
    sat: { status: "unknown", periods: [] },
    sun: { status: "unknown", periods: [] },
  };
}

function normalizeTime(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const match = value.trim().match(/^(\d{1,2}):(\d{2})(?::\d{2})?$/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  return hour <= 23 && minute <= 59 ? `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}` : null;
}

function period(open: unknown, close: unknown): { open: string; close: string } | null {
  const normalizedOpen = normalizeTime(open);
  const normalizedClose = normalizeTime(close);
  return normalizedOpen && normalizedClose ? { open: normalizedOpen, close: normalizedClose } : null;
}

export function normalizeOpeningHours(input: unknown): NormalizedOpeningHours | null {
  if (!Array.isArray(input) || input.length === 0) return null;
  const days = emptyDays();
  let recognizedDay = false;

  for (const value of input) {
    if (!value || typeof value !== "object") continue;
    const raw = value as RawHours;
    const dayId = Number(raw.giornoSettimanaId);
    if (!Number.isInteger(dayId) || dayId < 1 || dayId > 7) continue;
    recognizedDay = true;
    const day = dayKeys[dayId - 1];
    if (raw.flagNonComunicato === true) {
      days[day] = { status: "unknown", periods: [] };
    } else if (raw.flagChiusura === true) {
      days[day] = { status: "closed", periods: [] };
    } else if (raw.flagH24 === true) {
      days[day] = { status: "h24", periods: [] };
    } else {
      const periods = raw.flagOrarioContinuato === true
        ? [period(raw.oraAperturaOrarioContinuato, raw.oraChiusuraOrarioContinuato)].filter((item) => item !== null)
        : [
            period(raw.oraAperturaMattina, raw.oraChiusuraMattina),
            period(raw.oraAperturaPomeriggio, raw.oraChiusuraPomeriggio),
          ].filter((item) => item !== null);
      days[day] = periods.length > 0 ? { status: "schedule", periods } : { status: "unknown", periods: [] };
    }
  }
  return recognizedDay ? { days } : null;
}

function minuteOfDay(time: string): number {
  const [hour, minute] = time.split(":").map(Number);
  return hour * 60 + minute;
}

export type OpeningStatus = {
  status: "open" | "closed" | "unknown";
  scheduleLabel: string;
};

export function getOpeningStatus(hours: NormalizedOpeningHours | null, now = new Date()): OpeningStatus {
  if (!hours) return { status: "unknown", scheduleLabel: "Opening hours not reported" };
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Rome",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const weekday = parts.find((part) => part.type === "weekday")?.value.toLocaleLowerCase().slice(0, 3) as DayKey | undefined;
  const hour = Number(parts.find((part) => part.type === "hour")?.value);
  const minute = Number(parts.find((part) => part.type === "minute")?.value);
  if (!weekday || !Number.isFinite(hour) || !Number.isFinite(minute)) return { status: "unknown", scheduleLabel: "Hours unknown" };

  const day = hours.days[weekday];
  if (day.status === "unknown") return { status: "unknown", scheduleLabel: "Hours unknown" };
  if (day.status === "closed") return { status: "closed", scheduleLabel: "Reported closed today" };
  if (day.status === "h24") return { status: "open", scheduleLabel: "Open 24 hours" };

  const currentMinute = hour * 60 + minute;
  const open = day.periods.some((item) => {
    const start = minuteOfDay(item.open);
    const end = minuteOfDay(item.close);
    return end >= start ? currentMinute >= start && currentMinute < end : currentMinute >= start || currentMinute < end;
  });
  return {
    status: open ? "open" : "closed",
    scheduleLabel: day.periods.map((item) => `${item.open}–${item.close}`).join(" · "),
  };
}
