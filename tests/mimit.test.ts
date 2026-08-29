import { describe, expect, it } from "vitest";
import { parseMimitSnapshot } from "../src/api/mimit";

const stationHeader = "idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine";
const priceHeader = "idImpianto|descCarburante|prezzo|isSelf|dtComu";

function stations(...rows: string[]): string {
  return `\uFEFFmetadata before header\nEstrazione del 2026-08-28\n${stationHeader}\n${rows.join("\n")}`;
}

function prices(...rows: string[]): string {
  return `not a header\nEstrazione del 2026-08-28\n${priceHeader}\n${rows.join("\n")}`;
}

describe("parseMimitSnapshot", () => {
  it("parses the pipe-delimited snapshot after metadata and BOM", () => {
    const result = parseMimitSnapshot(
      stations("123|Manager|Brand|Stradale|Station name|Via Roma|Padova|PD|45.4|11.8"),
      prices("123|Metano|1.529|1|28/08/2026 08:15:00", "123|Metano|1.579|0|28/08/2026 08:16:00"),
      new Date("2026-08-29T08:00:00Z"),
    );

    expect(result.extractionDate).toBe("2026-08-28");
    expect(result.stations).toHaveLength(1);
    expect(result.stations[0]).toMatchObject({ id: 123, name: "Station name", lat: 45.4, lon: 11.8 });
    expect(result.stations[0].prices).toMatchObject([
      { value: 1.529, serviceMode: "self", source: "csv", stale: false },
      { value: 1.579, serviceMode: "served", source: "csv", stale: false },
    ]);
  });

  it("ignores non-CNG fuels and rejects bad price rows", () => {
    const result = parseMimitSnapshot(
      stations("123|Manager||Stradale|Station||||45.4|11.8"),
      prices(
        "123|GPL|0.799|1|28/08/2026 08:15:00",
        "123|Metano|bad|1|28/08/2026 08:15:00",
        "bad|Metano|1.5|0|28/08/2026 08:15:00",
        "123|Metano|1.6|0|28/08/2026 08:15:00",
      ),
    );
    expect(result.stations[0].prices).toHaveLength(1);
    expect(result.rejectedPriceRows).toBe(2);
  });

  it("skips invalid station IDs and coordinates without breaking valid rows", () => {
    const result = parseMimitSnapshot(
      stations(
        "bad|Manager||Stradale|Bad ID||||45.4|11.8",
        "124|Manager||Stradale|Bad coordinate||||north|11.8",
        "126|Manager||Stradale|Blank coordinate|||||11.8",
        "125|Manager||Stradale|Valid||||45.5|11.9",
      ),
      prices(
        "bad|Metano|1.5|0|28/08/2026 08:15:00",
        "124|Metano|1.5|0|28/08/2026 08:15:00",
        "126|Metano|1.5|0|28/08/2026 08:15:00",
        "125|Metano|1.6|0|28/08/2026 08:15:00",
      ),
    );
    expect(result.stations.map((station) => station.id)).toEqual([125]);
    expect(result.rejectedStationRows).toBe(2);
    expect(result.rejectedPriceRows).toBe(1);
  });

  it("refuses an empty usable snapshot", () => {
    expect(() => parseMimitSnapshot(stations("1||||||||nope|nope"), prices("1|Metano|bad|0|"))).toThrow(
      "no usable CNG stations",
    );
  });
});
