import { mergePriceSources } from "../api/mimit";
import type { AlongRouteStation } from "../route/routeStations";
import type { CachedStationDetail, CngPrice } from "../types";
import { formatAge } from "../utils/freshness";
import { buildGoogleMapsDirectionsUrl } from "../utils/googleMaps";
import { getOpeningStatus } from "../utils/openingHours";

type Props = {
  station: AlongRouteStation;
  detail: CachedStationDetail | null;
  detailState: "idle" | "loading" | "live" | "cached" | "error";
};

function sourceLabel(price: CngPrice, detailState: Props["detailState"]): string {
  if (price.source === "both") return detailState === "cached" ? "CSV + Cached API" : "CSV + API";
  if (price.source === "api") return detailState === "cached" ? "Cached API" : "API";
  return "CSV";
}

export default function StationDetails({ station, detail, detailState }: Props) {
  const prices = mergePriceSources(station.prices, detail?.prices ?? []);
  const opening = getOpeningStatus(detail?.openingHours ?? null);
  const hoursSource = detail ? (detailState === "cached" ? "Cached API" : "API") : "not available";
  return (
    <section className="panel station-detail" aria-live="polite">
      <p className="eyebrow">Selected station</p>
      <h2>{station.name}</h2>
      <p>{[station.address, station.municipality, station.province].filter(Boolean).join(", ") || "Address not reported"}</p>

      <h3>CNG prices</h3>
      <div className="detail-prices">
        {prices.map((price, index) => (
          <div key={`${price.serviceMode}-${price.value}-${index}`}>
            <strong>€{price.value.toFixed(3)}/kg · {price.serviceMode}</strong>
            <span className={price.stale ? "stale" : "muted"}>{price.stale ? "Price may be outdated" : formatAge(price.communicatedAt)} · {sourceLabel(price, detailState)}</span>
          </div>
        ))}
      </div>

      <h3>Opening hours</h3>
      {detailState === "loading" && !detail && <p>Loading live details…</p>}
      {detailState === "error" && <p>Live details are unavailable. Showing cached official station data.</p>}
      <p className={`opening-status ${opening.status}`}>
        {opening.status === "open" ? "Open now" : opening.status === "closed" ? "Closed now" : "Hours unknown"}
      </p>
      <p>{opening.scheduleLabel}</p>
      {detail && <p className="muted">{hoursSource} · fetched {formatAge(detail.fetchedAt).replace("updated ", "")}</p>}
      {detail?.openingHours && <p className="muted">Reported regular hours; holidays may differ.</p>}

      {detail && detail.services.length > 0 && (
        <>
          <h3>Services</h3>
          <p>{detail.services.join(" · ")}</p>
        </>
      )}

      <h3>Distance</h3>
      <p>{Math.round(station.positionAlongRouteKm)} km from trip start<br />{station.distanceFromRouteKm.toFixed(1)} km from route</p>

      <h3>Sources</h3>
      <p>Location: CSV<br />Price: {detail?.prices.length ? (detailState === "cached" ? "CSV + Cached API" : "CSV + API") : "CSV"}<br />Hours: {hoursSource}</p>

      <a className="primary-link" href={buildGoogleMapsDirectionsUrl(station.lat, station.lon)} target="_blank" rel="noreferrer">
        Open in Google Maps
      </a>
    </section>
  );
}
