import { useEffect, useMemo } from "react";
import { CircleMarker, MapContainer, Marker, Polyline, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import type { Place, SavedTrip } from "../types";
import type { AlongRouteStation } from "../route/routeStations";

type Props = {
  trip: SavedTrip;
  stations: AlongRouteStation[];
  selectedStationId: number | null;
  onSelectStation: (stationId: number) => void;
  currentLocation: Place | null;
};

function lowestPrice(station: AlongRouteStation) {
  return station.prices.reduce((lowest, price) => price.value < lowest.value ? price : lowest, station.prices[0]);
}

function FitRoute({ tripId, positions }: { tripId: string; positions: Array<[number, number]> }) {
  const map = useMap();
  useEffect(() => {
    if (positions.length > 1) map.fitBounds(positions, { padding: [18, 18] });
  }, [map, positions, tripId]);
  return null;
}

export default function MapView({ trip, stations, selectedStationId, onSelectStation, currentLocation }: Props) {
  const routePositions = useMemo(
    () => trip.route.geometry.coordinates.map(([lon, lat]) => [lat, lon] as [number, number]),
    [trip.id, trip.route.geometry],
  );
  return (
    <section className="map-panel" aria-label="Route map">
      <MapContainer center={[trip.origin.lat, trip.origin.lon]} zoom={7} scrollWheelZoom={false}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Polyline positions={routePositions} pathOptions={{ color: "#166b51", weight: 5, opacity: 0.85 }} />
        <CircleMarker center={[trip.origin.lat, trip.origin.lon]} radius={7} pathOptions={{ color: "#fff", fillColor: "#176b51", fillOpacity: 1, weight: 2 }} />
        <CircleMarker center={[trip.destination.lat, trip.destination.lon]} radius={7} pathOptions={{ color: "#fff", fillColor: "#bd4c35", fillOpacity: 1, weight: 2 }} />
        {currentLocation && (
          <CircleMarker center={[currentLocation.lat, currentLocation.lon]} radius={8} pathOptions={{ color: "#fff", fillColor: "#2878c8", fillOpacity: 1, weight: 3 }} />
        )}
        {stations.map((station) => {
          const price = lowestPrice(station);
          const selected = selectedStationId === station.id;
          const icon = L.divIcon({
            className: "station-marker-shell",
            html: `<span class="station-marker${selected ? " selected" : ""}">€${price.value.toFixed(2)}</span>`,
            iconSize: [52, 26],
            iconAnchor: [26, 13],
          });
          return (
            <Marker
              key={station.id}
              position={[station.lat, station.lon]}
              icon={icon}
              eventHandlers={{ click: () => onSelectStation(station.id) }}
              zIndexOffset={selected ? 1000 : 0}
            />
          );
        })}
        <FitRoute tripId={trip.id} positions={routePositions} />
      </MapContainer>
    </section>
  );
}
