import Dexie, { type EntityTable } from "dexie";
import { downloadMimitSnapshot } from "../api/mimit";
import type { AppSettings, CachedStation, CachedStationDetail, MetaRecord, SavedTrip } from "../types";

export const META_STATIONS_UPDATED_AT = "stationsUpdatedAt";
export const META_CSV_EXTRACTION_DATE = "csvExtractionDate";
export const DEFAULT_SETTINGS: AppSettings = {
  id: "main",
  vehicleRangeKm: 300,
  reserveKm: 60,
  stationCorridorKm: 10,
};

class CngDatabase extends Dexie {
  stations!: EntityTable<CachedStation, "id">;
  stationDetails!: EntityTable<CachedStationDetail, "stationId">;
  trips!: EntityTable<SavedTrip, "id">;
  meta!: EntityTable<MetaRecord, "key">;
  settings!: EntityTable<AppSettings, "id">;

  constructor() {
    super("cng-route-planner");
    this.version(1).stores({
      stations: "id, province",
      stationDetails: "stationId, fetchedAt",
      trips: "id, createdAt",
      meta: "key",
      settings: "id",
    });
  }
}

export const db = new CngDatabase();

export async function replaceStationSnapshot(stations: CachedStation[], extractionDate: string | null): Promise<string> {
  if (stations.length === 0) throw new Error("Refusing to replace station data with an empty snapshot");
  const updatedAt = new Date().toISOString();
  await db.transaction("rw", db.stations, db.meta, async () => {
    await db.stations.clear();
    await db.stations.bulkPut(stations);
    await db.meta.bulkPut([
      { key: META_STATIONS_UPDATED_AT, value: updatedAt },
      { key: META_CSV_EXTRACTION_DATE, value: extractionDate },
    ]);
  });
  return updatedAt;
}

export async function getStationSnapshotMeta(): Promise<{ updatedAt: string | null; extractionDate: string | null }> {
  const [updated, extraction] = await Promise.all([
    db.meta.get(META_STATIONS_UPDATED_AT),
    db.meta.get(META_CSV_EXTRACTION_DATE),
  ]);
  return {
    updatedAt: typeof updated?.value === "string" ? updated.value : null,
    extractionDate: typeof extraction?.value === "string" ? extraction.value : null,
  };
}

export async function updateStationData(): Promise<{ stationCount: number; updatedAt: string; extractionDate: string | null }> {
  const { stations, extractionDate } = await downloadMimitSnapshot();
  const updatedAt = await replaceStationSnapshot(stations, extractionDate);
  return { stationCount: stations.length, updatedAt, extractionDate };
}

export async function saveTrip(trip: SavedTrip): Promise<void> {
  await db.trips.put(trip);
}

export async function getLatestTrip(): Promise<SavedTrip | null> {
  return (await db.trips.orderBy("createdAt").last()) ?? null;
}

export async function getSettings(): Promise<AppSettings> {
  return (await db.settings.get("main")) ?? DEFAULT_SETTINGS;
}

export async function saveSettings(settings: AppSettings): Promise<void> {
  await db.settings.put(settings);
}
