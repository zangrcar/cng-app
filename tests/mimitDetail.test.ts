import { describe, expect, it } from "vitest";
import { mergePriceSources, normalizeMimitStationDetail } from "../src/api/mimit";
import type { CngPrice } from "../src/types";

function price(value: number, serviceMode: "self" | "served", source: "csv" | "api"): CngPrice {
  return { value, serviceMode, source, fuelName: "Metano", communicatedAt: null, stale: true };
}

describe("MIMIT live station detail", () => {
  it("normalizes only CNG prices, services, and hours", () => {
    const detail = normalizeMimitStationDetail(3503, {
      fuels: [
        { name: "Metano", price: 1.599, isSelf: true, validityDate: "2026-08-29T10:00:00Z" },
        { name: "GPL", price: 0.8, isSelf: true },
      ],
      services: [{ description: "Food & Beverage" }],
      orariapertura: [{ giornoSettimanaId: 1, flagH24: true }],
    }, new Date("2026-08-29T12:00:00Z"));
    expect(detail.prices).toMatchObject([{ value: 1.599, serviceMode: "self", source: "api", stale: false }]);
    expect(detail.services).toEqual(["Food & Beverage"]);
    expect(detail.openingHours?.days.mon.status).toBe("h24");
  });

  it("keeps missing hours unknown", () => {
    expect(normalizeMimitStationDetail(1, { fuels: [], orariapertura: [] }).openingHours).toBeNull();
  });

  it("merges agreeing prices and retains differing API and CSV prices", () => {
    expect(mergePriceSources([price(1.5, "self", "csv")], [price(1.5, "self", "api")])).toMatchObject([
      { value: 1.5, source: "both" },
    ]);
    expect(mergePriceSources([price(1.55, "served", "csv")], [price(1.5, "served", "api")])).toMatchObject([
      { value: 1.5, source: "api" },
      { value: 1.55, source: "csv" },
    ]);
  });
});
