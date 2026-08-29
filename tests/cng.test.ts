import { describe, expect, it } from "vitest";
import { isCngFuelName } from "../src/utils/cng";

describe("isCngFuelName", () => {
  it.each(["Metano", "METANO", " CNG ", "GNC", "L-GNC", "  Metano  "])("classifies %s as CNG", (name) => {
    expect(isCngFuelName(name)).toBe(true);
  });

  it.each(["GNL", "LNG", "Metano Liquido", "GPL", "Benzina", "Gasolio", ""])("does not classify %s as CNG", (name) => {
    expect(isCngFuelName(name)).toBe(false);
  });
});
