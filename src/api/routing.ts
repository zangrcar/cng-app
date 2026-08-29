import { z } from "zod";
import type { Place, SavedTrip } from "../types";

const lineStringSchema = z.object({
  type: z.literal("LineString"),
  coordinates: z.array(z.tuple([z.number(), z.number()])).min(2),
});

const osrmResponseSchema = z.object({
  code: z.literal("Ok"),
  routes: z.array(z.object({
    geometry: lineStringSchema,
    distance: z.number().nonnegative(),
    duration: z.number().nonnegative(),
  })).min(1),
});

export const OSRM_BASE_URL = "https://router.project-osrm.org";

export type PlannedRoute = SavedTrip["route"];

export async function calculateRoute(origin: Place, destination: Place): Promise<PlannedRoute> {
  const coordinates = `${origin.lon},${origin.lat};${destination.lon},${destination.lat}`;
  const params = new URLSearchParams({ overview: "full", geometries: "geojson" });
  const response = await fetch(`${OSRM_BASE_URL}/route/v1/driving/${coordinates}?${params}`, {
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`OSRM request failed (${response.status})`);

  const result = osrmResponseSchema.safeParse(await response.json());
  if (!result.success) throw new Error("OSRM returned an unusable route");
  const route = result.data.routes[0];
  return {
    geometry: route.geometry,
    distanceKm: route.distance / 1000,
    durationMinutes: route.duration / 60,
  };
}
