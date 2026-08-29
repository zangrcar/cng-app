import { afterEach, describe, expect, it, vi } from "vitest";
import { parseCoordinateInput, searchPlaces } from "../src/api/geocoding";

describe("geocoding", () => {
  afterEach(() => vi.restoreAllMocks());

  it("parses valid latitude,longitude and rejects invalid coordinates", () => {
    expect(parseCoordinateInput("40.0559, 17.9926")).toMatchObject({ lat: 40.0559, lon: 17.9926 });
    expect(parseCoordinateInput("91,17")).toBeNull();
    expect(parseCoordinateInput("Gallipoli")).toBeNull();
  });

  it("bypasses Nominatim for direct coordinates", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    await expect(searchPlaces("40.0559,17.9926")).resolves.toHaveLength(1);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns up to provider results for button-triggered search", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify([
      { display_name: "Gallipoli, Lecce, Italy", lat: "40.0559", lon: "17.9926" },
    ]), { status: 200 }));
    await expect(searchPlaces("Gallipoli")).resolves.toEqual([
      { label: "Gallipoli, Lecce, Italy", lat: 40.0559, lon: 17.9926 },
    ]);
  });
});
