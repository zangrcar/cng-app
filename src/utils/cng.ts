const NON_CNG_NAMES = new Set([
  "GNL",
  "LNG",
  "METANO LIQUIDO",
  "GPL",
  "BENZINA",
  "GASOLIO",
]);

export function normalizeFuelName(name: string): string {
  return name
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .replace(/\s+/g, " ")
    .toUpperCase();
}

export function isCngFuelName(name: string): boolean {
  const normalized = normalizeFuelName(name);
  if (!normalized || NON_CNG_NAMES.has(normalized)) return false;
  return normalized === "METANO" || normalized === "CNG" || normalized === "GNC" || normalized === "L-GNC";
}
