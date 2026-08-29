import "fake-indexeddb/auto";
import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { db, getLatestTrip, getSettings, replaceStationSnapshot, saveSettings, saveTrip } from "../src/db/db";
import type { CachedStation } from "../src/types";
import { findStationsAlongRoute } from "../src/route/routeStations";

const station: CachedStation = {
  id: 10,
  name: "Test CNG",
  brand: null,
  address: null,
  municipality: "Padova",
  province: "PD",
  lat: 45.4,
  lon: 11.8,
  stationType: "Stradale",
  prices: [{
    value: 1.5,
    fuelName: "Metano",
    serviceMode: "served",
    communicatedAt: null,
    source: "csv",
    stale: true,
  }],
  csvExtractionDate: "2026-08-28",
};

describe("station snapshot replacement", () => {
  beforeEach(async () => {
    db.close();
    await db.delete();
    await db.open();
  });

  afterAll(() => db.close());

  it("atomically saves a complete snapshot and metadata", async () => {
    await replaceStationSnapshot([station], "2026-08-28");
    expect(await db.stations.toArray()).toEqual([station]);
    expect(await db.meta.get("csvExtractionDate")).toMatchObject({ value: "2026-08-28" });
  });

  it("keeps working data when a replacement is invalid", async () => {
    await replaceStationSnapshot([station], "2026-08-28");
    await expect(replaceStationSnapshot([], null)).rejects.toThrow("empty snapshot");
    expect(await db.stations.toArray()).toEqual([station]);
  });

  it("restores route, stations, details, and settings after an offline-style reopen", async () => {
    await replaceStationSnapshot([station], "2026-08-28");
    const trip = {
      id: "trip-1",
      createdAt: "2026-08-29T10:00:00Z",
      origin: { label: "Start", lat: 45.4, lon: 11.7 },
      destination: { label: "End", lat: 45.4, lon: 11.9 },
      route: { geometry: { type: "LineString" as const, coordinates: [[11.7, 45.4], [11.9, 45.4]] }, distanceKm: 16, durationMinutes: 20 },
      maxDistanceFromRouteKm: 10,
    };
    await saveTrip(trip);
    await db.stationDetails.put({ stationId: 10, fetchedAt: "2026-08-29T10:00:00Z", prices: [], openingHours: null, services: [], rawSourceAvailable: true });
    await saveSettings({ id: "main", vehicleRangeKm: 280, reserveKm: 50, stationCorridorKm: 20 });
    db.close();
    await db.open();

    const restoredTrip = await getLatestTrip();
    const restoredStations = await db.stations.toArray();
    expect(findStationsAlongRoute(restoredTrip!.route.geometry, restoredStations, restoredTrip!.maxDistanceFromRouteKm)).toHaveLength(1);
    expect(await db.stationDetails.get(10)).toMatchObject({ stationId: 10 });
    expect(await getSettings()).toMatchObject({ vehicleRangeKm: 280, reserveKm: 50, stationCorridorKm: 20 });
  });
});
