import { lineString, point } from "@turf/helpers";
import nearestPointOnLine from "@turf/nearest-point-on-line";
import simplify from "@turf/simplify";
import type { CachedStation, RouteGeometry } from "../types";

export type AlongRouteStation = CachedStation & {
  distanceFromRouteKm: number;
  positionAlongRouteKm: number;
};

function polylineLengthKm(coordinates: number[][]): number {
  let total = 0;
  for (let index = 1; index < coordinates.length; index += 1) {
    const [lon1, lat1] = coordinates[index - 1];
    const [lon2, lat2] = coordinates[index];
    const lat1Radians = lat1 * Math.PI / 180;
    const lat2Radians = lat2 * Math.PI / 180;
    const deltaLat = (lat2 - lat1) * Math.PI / 180;
    const deltaLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(deltaLat / 2) ** 2
      + Math.cos(lat1Radians) * Math.cos(lat2Radians) * Math.sin(deltaLon / 2) ** 2;
    total += 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
  return total;
}

function routeBounds(route: RouteGeometry, paddingKm: number) {
  let minLon = Infinity;
  let minLat = Infinity;
  let maxLon = -Infinity;
  let maxLat = -Infinity;
  for (const [lon, lat] of route.coordinates) {
    minLon = Math.min(minLon, lon);
    maxLon = Math.max(maxLon, lon);
    minLat = Math.min(minLat, lat);
    maxLat = Math.max(maxLat, lat);
  }
  const latPadding = paddingKm / 110.574;
  const highestAbsoluteLatitude = Math.max(Math.abs(minLat), Math.abs(maxLat));
  const lonPadding = paddingKm / (111.32 * Math.max(0.1, Math.cos(highestAbsoluteLatitude * Math.PI / 180)));
  return {
    minLon: minLon - lonPadding,
    maxLon: maxLon + lonPadding,
    minLat: minLat - latPadding,
    maxLat: maxLat + latPadding,
  };
}

export function findStationsAlongRoute(
  route: RouteGeometry,
  stations: CachedStation[],
  maxDistanceKm: number,
): AlongRouteStation[] {
  if (route.coordinates.length < 2 || maxDistanceKm < 0) return [];
  const bounds = routeBounds(route, maxDistanceKm);
  // OSRM full routes can contain >10k points. A ~20 m calculation line keeps
  // corridor matching responsive while the original geometry remains saved/displayed.
  const routeLine = simplify(lineString(route.coordinates), { tolerance: 0.0002, highQuality: false });
  const simplifiedLength = polylineLengthKm(routeLine.geometry.coordinates);
  const positionScale = simplifiedLength > 0 ? polylineLengthKm(route.coordinates) / simplifiedLength : 1;
  const matches: AlongRouteStation[] = [];

  for (const station of stations) {
    if (
      station.lon < bounds.minLon || station.lon > bounds.maxLon
      || station.lat < bounds.minLat || station.lat > bounds.maxLat
    ) continue;

    const nearest = nearestPointOnLine(routeLine, point([station.lon, station.lat]), { units: "kilometers" });
    const distanceFromRouteKm = nearest.properties.dist;
    const positionAlongRouteKm = nearest.properties.location * positionScale;
    if (distanceFromRouteKm <= maxDistanceKm) {
      matches.push({ ...station, distanceFromRouteKm, positionAlongRouteKm });
    }
  }

  return matches.sort((a, b) => a.positionAlongRouteKm - b.positionAlongRouteKm);
}
