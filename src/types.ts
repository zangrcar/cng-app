export type DataSource = "csv" | "api" | "both";

export type CngPrice = {
  value: number;
  fuelName: string;
  serviceMode: "self" | "served";
  communicatedAt: string | null;
  source: DataSource;
  stale: boolean;
};

export type CachedStation = {
  id: number;
  name: string;
  brand: string | null;
  address: string | null;
  municipality: string | null;
  province: string | null;
  lat: number;
  lon: number;
  stationType: string | null;
  prices: CngPrice[];
  csvExtractionDate: string | null;
};

export type Place = {
  label: string;
  lat: number;
  lon: number;
};

export type RouteGeometry = LineString;

export type SavedTrip = {
  id: string;
  createdAt: string;
  origin: Place;
  destination: Place;
  route: {
    geometry: RouteGeometry;
    distanceKm: number;
    durationMinutes: number;
  };
  maxDistanceFromRouteKm: number;
};

export type DayKey = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";

export type NormalizedOpeningHours = {
  days: Record<DayKey, {
    status: "unknown" | "closed" | "h24" | "schedule";
    periods: Array<{ open: string; close: string }>;
  }>;
};

export type CachedStationDetail = {
  stationId: number;
  fetchedAt: string;
  prices: CngPrice[];
  openingHours: NormalizedOpeningHours | null;
  services: string[];
  rawSourceAvailable: boolean;
};

export type AppSettings = {
  id: "main";
  vehicleRangeKm: number;
  reserveKm: number;
  stationCorridorKm: 5 | 10 | 20;
};

export type MetaRecord = {
  key: string;
  value: string | number | null;
};
import type { LineString } from "geojson";
