import { describe, expect, it } from "vitest";
import { findStationsAlongRoute } from "../src/route/routeStations";
import type { CachedStation, RouteGeometry } from "../src/types";

const route: RouteGeometry = {
  type: "LineString",
  coordinates: [[10, 45], [10, 46]],
};

function station(id: number, lat: number, lon: number): CachedStation {
  return {
    id,
    name: `Station ${id}`,
    brand: null,
    address: null,
    municipality: null,
    province: null,
    lat,
    lon,
    stationType: null,
    prices: [],
    csvExtractionDate: null,
  };
}

describe("findStationsAlongRoute", () => {
  it("includes stations on and inside the corridor and excludes stations outside", () => {
    const result = findStationsAlongRoute(route, [
      station(1, 45.25, 10),
      station(2, 45.5, 10.06),
      station(3, 45.75, 10.2),
    ], 10);

    expect(result.map(({ id }) => id)).toEqual([1, 2]);
    expect(result[0].distanceFromRouteKm).toBeCloseTo(0, 3);
    expect(result[1].distanceFromRouteKm).toBeGreaterThan(4);
    expect(result[1].distanceFromRouteKm).toBeLessThan(5);
  });

  it("orders stations by cumulative position from route origin", () => {
    const result = findStationsAlongRoute(route, [
      station(3, 45.9, 10),
      station(1, 45.1, 10),
      station(2, 45.5, 10),
    ], 1);

    expect(result.map(({ id }) => id)).toEqual([1, 2, 3]);
    expect(result[0].positionAlongRouteKm).toBeGreaterThan(10);
    expect(result[2].positionAlongRouteKm).toBeGreaterThan(result[1].positionAlongRouteKm);
  });

  it("returns kilometres and handles invalid inputs safely", () => {
    const result = findStationsAlongRoute(route, [station(1, 45.5, 10.1)], 20);
    expect(result[0].distanceFromRouteKm).toBeGreaterThan(7);
    expect(result[0].distanceFromRouteKm).toBeLessThan(9);
    expect(findStationsAlongRoute({ type: "LineString", coordinates: [] }, [], 10)).toEqual([]);
  });
});
