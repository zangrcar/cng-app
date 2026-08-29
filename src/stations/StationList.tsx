import type { AlongRouteStation } from "../route/routeStations";
import { formatAge } from "../utils/freshness";

type Props = {
  stations: AlongRouteStation[];
  selectedStationId: number | null;
  onSelect: (stationId: number) => void;
};

function lowestPrice(station: AlongRouteStation) {
  return station.prices.reduce((lowest, price) => price.value < lowest.value ? price : lowest, station.prices[0]);
}

export default function StationList({ stations, selectedStationId, onSelect }: Props) {
  return (
    <section className="stations-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">In travel order</p>
          <h2>Stations ahead</h2>
        </div>
        <span className="count-badge">{stations.length}</span>
      </div>
      {stations.length === 0 ? (
        <p className="empty-state">No cached CNG stations were found in this corridor. Try 20 km or update station data.</p>
      ) : (
        <div className="station-list">
          {stations.map((station) => {
            const price = lowestPrice(station);
            return (
              <button
                type="button"
                className={`station-card${selectedStationId === station.id ? " selected" : ""}`}
                key={station.id}
                onClick={() => onSelect(station.id)}
              >
                <span className="station-position">{Math.round(station.positionAlongRouteKm)} km from start · {station.distanceFromRouteKm.toFixed(1)} km from route</span>
                <strong>{station.name}</strong>
                <span>{station.municipality}{station.province ? ` (${station.province})` : ""}</span>
                <span className="station-price">€{price.value.toFixed(3)}/kg · {price.serviceMode}</span>
                <span className={price.stale ? "stale" : "muted"}>{price.stale ? "Price may be outdated" : formatAge(price.communicatedAt)} · CSV</span>
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}
