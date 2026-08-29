import { describe, expect, it } from "vitest";
import { getOpeningStatus, normalizeOpeningHours } from "../src/utils/openingHours";

const monday = { giornoSettimanaId: 1 };

describe("opening hour normalization", () => {
  it("treats missing hours as unknown, never closed", () => {
    expect(normalizeOpeningHours(undefined)).toBeNull();
    expect(getOpeningStatus(null).status).toBe("unknown");
  });

  it("supports H24 and explicitly closed", () => {
    expect(normalizeOpeningHours([{ ...monday, flagH24: true }])?.days.mon.status).toBe("h24");
    expect(normalizeOpeningHours([{ ...monday, flagChiusura: true }])?.days.mon.status).toBe("closed");
  });

  it("supports continuous and split schedules", () => {
    expect(normalizeOpeningHours([{
      ...monday,
      flagOrarioContinuato: true,
      oraAperturaOrarioContinuato: "06:00:00",
      oraChiusuraOrarioContinuato: "22:00:00",
    }])?.days.mon.periods).toEqual([{ open: "06:00", close: "22:00" }]);
    expect(normalizeOpeningHours([{
      ...monday,
      oraAperturaMattina: "06:30",
      oraChiusuraMattina: "12:00",
      oraAperturaPomeriggio: "14:00",
      oraChiusuraPomeriggio: "19:30",
    }])?.days.mon.periods).toHaveLength(2);
  });

  it("marks uncommunicated and malformed values unknown", () => {
    expect(normalizeOpeningHours([{ ...monday, flagNonComunicato: true }])?.days.mon.status).toBe("unknown");
    expect(normalizeOpeningHours([{ ...monday, oraAperturaMattina: "bad", oraChiusuraMattina: "12:00" }])?.days.mon.status).toBe("unknown");
  });

  it("calculates known status in Europe/Rome", () => {
    const hours = normalizeOpeningHours([{ ...monday, flagH24: true }]);
    expect(getOpeningStatus(hours, new Date("2026-08-31T12:00:00Z")).status).toBe("open");
  });
});
