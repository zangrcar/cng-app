import { z } from "zod";
import type { Place } from "../types";

const nominatimSchema = z.array(z.object({
  display_name: z.string(),
  lat: z.string(),
  lon: z.string(),
}));

export const NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";

export function parseCoordinateInput(value: string): Place | null {
  const match = value.trim().match(/^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$/);
  if (!match) return null;
  const lat = Number(match[1]);
  const lon = Number(match[2]);
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return { label: value.trim(), lat, lon };
}

export async function searchPlaces(query: string): Promise<Place[]> {
  const coordinates = parseCoordinateInput(query);
  if (coordinates) return [coordinates];
  if (!query.trim()) return [];

  const params = new URLSearchParams({ q: query.trim(), format: "jsonv2", limit: "5" });
  const response = await fetch(`${NOMINATIM_BASE_URL}/search?${params}`, {
    headers: { accept: "application/json", "accept-language": "en,it;q=0.9" },
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`Nominatim request failed (${response.status})`);
  const parsed = nominatimSchema.safeParse(await response.json());
  if (!parsed.success) throw new Error("Nominatim returned unusable results");
  return parsed.data.flatMap((result) => {
    const lat = Number(result.lat);
    const lon = Number(result.lon);
    return Number.isFinite(lat) && Number.isFinite(lon)
      ? [{ label: result.display_name, lat, lon }]
      : [];
  });
}
