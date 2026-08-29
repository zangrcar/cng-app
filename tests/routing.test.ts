import { afterEach, describe, expect, it, vi } from "vitest";
import { calculateRoute } from "../src/api/routing";

const origin = { label: "Ljubljana", lat: 46.0569, lon: 14.5058 };
const destination = { label: "Gallipoli", lat: 40.0559, lon: 17.9926 };

describe("calculateRoute", () => {
  afterEach(() => vi.restoreAllMocks());

  it("requests a full GeoJSON driving route and converts units", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({
      code: "Ok",
      routes: [{
        geometry: { type: "LineString", coordinates: [[14.5, 46.0], [18, 40.0]] },
        distance: 1_120_500,
        duration: 43_200,
      }],
    }), { status: 200 }));

    await expect(calculateRoute(origin, destination)).resolves.toMatchObject({
      distanceKm: 1120.5,
      durationMinutes: 720,
    });
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain("/route/v1/driving/14.5058,46.0569;17.9926,40.0559");
    expect(url).toContain("overview=full");
    expect(url).toContain("geometries=geojson");
  });

  it("rejects failed or malformed provider responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ code: "NoRoute", routes: [] }), { status: 200 }));
    await expect(calculateRoute(origin, destination)).rejects.toThrow("unusable route");
  });
});
