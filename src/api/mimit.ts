import { z } from "zod";
import type { CachedStation, CachedStationDetail, CngPrice } from "../types";
import { isCngFuelName } from "../utils/cng";
import { normalizeOpeningHours } from "../utils/openingHours";

const stationRowSchema = z.object({
  id: z.number().int().positive(),
  lat: z.number().min(-90).max(90),
  lon: z.number().min(-180).max(180),
});

const priceRowSchema = z.object({
  id: z.number().int().positive(),
  value: z.number().positive().max(20),
});

export const MIMIT_STATIONS_URL = "/proxy/mimit/stations";
export const MIMIT_PRICES_URL = "/proxy/mimit/prices";
export const MIMIT_STATION_DETAIL_URL = "/proxy/mimit/station";

type CsvTable = {
  extractionDate: string | null;
  headers: string[];
  rows: string[][];
};

export type ParsedStationSnapshot = {
  extractionDate: string | null;
  stations: CachedStation[];
  rejectedStationRows: number;
  rejectedPriceRows: number;
};

function parsePipeTable(input: string): CsvTable {
  const text = input.replace(/^\uFEFF/, "");
  const lines = text.split(/\r?\n/);
  const headerIndex = lines.findIndex((line) => line.toLocaleLowerCase("it-IT").includes("idimpianto"));
  if (headerIndex < 0) throw new Error("MIMIT CSV header was not found");

  const metadata = lines.slice(0, headerIndex).join(" ");
  const extractionMatch = metadata.match(/(20\d{2})-(\d{2})-(\d{2})/);
  const extractionDate = extractionMatch ? `${extractionMatch[1]}-${extractionMatch[2]}-${extractionMatch[3]}` : null;
  const headers = lines[headerIndex].split("|").map((cell) => cell.trim().toLocaleLowerCase("it-IT"));
  const rows = lines
    .slice(headerIndex + 1)
    .filter((line) => line.trim().length > 0)
    .map((line) => line.split("|").map((cell) => cell.trim()));
  return { extractionDate, headers, rows };
}

function columnIndex(headers: string[], name: string): number {
  const index = headers.indexOf(name.toLocaleLowerCase("it-IT"));
  if (index < 0) throw new Error(`Required MIMIT CSV column is missing: ${name}`);
  return index;
}

function nullable(value: string | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function numeric(value: string | undefined): number {
  return value?.trim() ? Number(value) : Number.NaN;
}

function parseMimitTimestamp(value: string | undefined): string | null {
  const match = value?.trim().match(/^(\d{2})\/(\d{2})\/(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?$/);
  if (!match) return null;
  const [, day, month, year, hour, minute, second = "00"] = match;
  const timestamp = new Date(`${year}-${month}-${day}T${hour}:${minute}:${second}+02:00`);
  return Number.isNaN(timestamp.valueOf()) ? null : timestamp.toISOString();
}

function isStale(communicatedAt: string | null, now: Date): boolean {
  if (!communicatedAt) return true;
  return now.valueOf() - new Date(communicatedAt).valueOf() > 7 * 24 * 60 * 60 * 1000;
}

export function parseMimitSnapshot(stationsCsv: string, pricesCsv: string, now = new Date()): ParsedStationSnapshot {
  const priceTable = parsePipeTable(pricesCsv);
  const priceIdIndex = columnIndex(priceTable.headers, "idimpianto");
  const fuelIndex = columnIndex(priceTable.headers, "desccarburante");
  const valueIndex = columnIndex(priceTable.headers, "prezzo");
  const selfIndex = columnIndex(priceTable.headers, "isself");
  const communicatedIndex = columnIndex(priceTable.headers, "dtcomu");

  const pricesByStation = new Map<number, CngPrice[]>();
  let rejectedPriceRows = 0;
  for (const row of priceTable.rows) {
    const fuelName = row[fuelIndex] ?? "";
    if (!isCngFuelName(fuelName)) continue;
    const parsed = priceRowSchema.safeParse({ id: numeric(row[priceIdIndex]), value: numeric(row[valueIndex]) });
    const selfValue = row[selfIndex];
    if (!parsed.success || (selfValue !== "0" && selfValue !== "1")) {
      rejectedPriceRows += 1;
      continue;
    }
    const communicatedAt = parseMimitTimestamp(row[communicatedIndex]);
    const price: CngPrice = {
      value: parsed.data.value,
      fuelName: fuelName.trim(),
      serviceMode: selfValue === "1" ? "self" : "served",
      communicatedAt,
      source: "csv",
      stale: isStale(communicatedAt, now),
    };
    const existing = pricesByStation.get(parsed.data.id) ?? [];
    existing.push(price);
    pricesByStation.set(parsed.data.id, existing);
  }

  const stationTable = parsePipeTable(stationsCsv);
  const indexes = {
    id: columnIndex(stationTable.headers, "idimpianto"),
    manager: columnIndex(stationTable.headers, "gestore"),
    brand: columnIndex(stationTable.headers, "bandiera"),
    type: columnIndex(stationTable.headers, "tipo impianto"),
    name: columnIndex(stationTable.headers, "nome impianto"),
    address: columnIndex(stationTable.headers, "indirizzo"),
    municipality: columnIndex(stationTable.headers, "comune"),
    province: columnIndex(stationTable.headers, "provincia"),
    lat: columnIndex(stationTable.headers, "latitudine"),
    lon: columnIndex(stationTable.headers, "longitudine"),
  };

  const stations: CachedStation[] = [];
  let rejectedStationRows = 0;
  for (const row of stationTable.rows) {
    const id = numeric(row[indexes.id]);
    const prices = pricesByStation.get(id);
    if (!prices) continue;
    const parsed = stationRowSchema.safeParse({ id, lat: numeric(row[indexes.lat]), lon: numeric(row[indexes.lon]) });
    if (!parsed.success) {
      rejectedStationRows += 1;
      continue;
    }
    stations.push({
      id: parsed.data.id,
      name: nullable(row[indexes.name]) ?? nullable(row[indexes.manager]) ?? `Station ${parsed.data.id}`,
      brand: nullable(row[indexes.brand]),
      address: nullable(row[indexes.address]),
      municipality: nullable(row[indexes.municipality]),
      province: nullable(row[indexes.province]),
      lat: parsed.data.lat,
      lon: parsed.data.lon,
      stationType: nullable(row[indexes.type]),
      prices: prices.sort((a, b) => a.value - b.value),
      csvExtractionDate: stationTable.extractionDate ?? priceTable.extractionDate,
    });
  }

  if (stations.length === 0) throw new Error("MIMIT data contained no usable CNG stations");
  return {
    extractionDate: stationTable.extractionDate ?? priceTable.extractionDate,
    stations,
    rejectedStationRows,
    rejectedPriceRows,
  };
}

async function fetchText(url: string): Promise<string> {
  const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`MIMIT request failed (${response.status})`);
  return response.text();
}

export async function downloadMimitSnapshot(): Promise<ParsedStationSnapshot> {
  const [pricesCsv, stationsCsv] = await Promise.all([
    fetchText(MIMIT_PRICES_URL),
    fetchText(MIMIT_STATIONS_URL),
  ]);
  return parseMimitSnapshot(stationsCsv, pricesCsv);
}

function validIsoTimestamp(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? null : date.toISOString();
}

export function normalizeMimitStationDetail(stationId: number, input: unknown, now = new Date()): CachedStationDetail {
  if (!input || typeof input !== "object") throw new Error("MIMIT station detail was unusable");
  const raw = input as Record<string, unknown>;
  const fuels = Array.isArray(raw.fuels) ? raw.fuels : [];
  const prices: CngPrice[] = [];
  for (const fuel of fuels) {
    if (!fuel || typeof fuel !== "object") continue;
    const value = fuel as Record<string, unknown>;
    const fuelName = typeof value.name === "string" ? value.name : "";
    const price = Number(value.price);
    if (!isCngFuelName(fuelName) || !Number.isFinite(price) || price <= 0 || price > 20 || typeof value.isSelf !== "boolean") continue;
    const communicatedAt = validIsoTimestamp(value.validityDate) ?? validIsoTimestamp(value.insertDate);
    prices.push({
      value: price,
      fuelName,
      serviceMode: value.isSelf ? "self" : "served",
      communicatedAt,
      source: "api",
      stale: isStale(communicatedAt, now),
    });
  }

  const services = Array.isArray(raw.services)
    ? raw.services.flatMap((service) => {
        if (typeof service === "string" && service.trim()) return [service.trim()];
        if (service && typeof service === "object" && typeof (service as Record<string, unknown>).description === "string") {
          return [String((service as Record<string, unknown>).description).trim()].filter(Boolean);
        }
        return [];
      })
    : [];

  return {
    stationId,
    fetchedAt: now.toISOString(),
    prices: prices.sort((a, b) => a.value - b.value),
    openingHours: normalizeOpeningHours(raw.orariapertura),
    services,
    rawSourceAvailable: true,
  };
}

export function mergePriceSources(csvPrices: CngPrice[], apiPrices: CngPrice[]): CngPrice[] {
  const usedCsv = new Set<number>();
  const merged = apiPrices.map((apiPrice) => {
    const matchingIndex = csvPrices.findIndex((csvPrice, index) =>
      !usedCsv.has(index)
      && csvPrice.serviceMode === apiPrice.serviceMode
      && Math.abs(csvPrice.value - apiPrice.value) < 0.0005,
    );
    if (matchingIndex < 0) return apiPrice;
    usedCsv.add(matchingIndex);
    return { ...apiPrice, source: "both" as const };
  });
  csvPrices.forEach((price, index) => {
    if (!usedCsv.has(index)) merged.push(price);
  });
  return merged;
}

export async function fetchMimitStationDetail(stationId: number): Promise<CachedStationDetail> {
  if (!Number.isInteger(stationId) || stationId <= 0) throw new Error("Invalid MIMIT station ID");
  const response = await fetch(`${MIMIT_STATION_DETAIL_URL}/${stationId}`, {
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`MIMIT live detail failed (${response.status})`);
  return normalizeMimitStationDetail(stationId, await response.json());
}
