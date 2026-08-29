import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { calculateRoute } from "./api/routing";
import { parseCoordinateInput } from "./api/geocoding";
import { fetchMimitStationDetail } from "./api/mimit";
import { DEFAULT_SETTINGS, db, getLatestTrip, getSettings, getStationSnapshotMeta, saveSettings, saveTrip, updateStationData } from "./db/db";
import { findStationsAlongRoute, type AlongRouteStation } from "./route/routeStations";
import MapView from "./map/MapView";
import StationDetails from "./stations/StationDetails";
import StationList from "./stations/StationList";
import PlaceSearch from "./components/PlaceSearch";
import { useCurrentLocation } from "./hooks/useCurrentLocation";
import { useNetworkStatus } from "./hooks/useNetworkStatus";
import Settings from "./components/Settings";
import type { CachedStationDetail, Place, SavedTrip } from "./types";

const DAY_MS = 24 * 60 * 60 * 1000;

function formatUpdateTime(value: string | null): string {
  if (!value) return "No station data downloaded";
  return `Updated ${new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value))}`;
}

export default function App() {
  const online = useNetworkStatus();
  const { location: currentLocation, locating, locationError, requestLocation } = useCurrentLocation();
  const [stationCount, setStationCount] = useState(0);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [originInput, setOriginInput] = useState("");
  const [destinationInput, setDestinationInput] = useState("Gallipoli");
  const [originPlace, setOriginPlace] = useState<Place | null>(null);
  const [destinationPlace, setDestinationPlace] = useState<Place | null>(null);
  const [corridor, setCorridor] = useState<5 | 10 | 20>(10);
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [planning, setPlanning] = useState(false);
  const [trip, setTrip] = useState<SavedTrip | null>(null);
  const [matchedStations, setMatchedStations] = useState<AlongRouteStation[]>([]);
  const [selectedStationId, setSelectedStationId] = useState<number | null>(null);
  const [stationDetail, setStationDetail] = useState<CachedStationDetail | null>(null);
  const [detailState, setDetailState] = useState<"idle" | "loading" | "live" | "cached" | "error">("idle");
  const [routeMessage, setRouteMessage] = useState<string | null>(null);
  const selectedStation = useMemo(
    () => matchedStations.find((station) => station.id === selectedStationId) ?? null,
    [matchedStations, selectedStationId],
  );
  const autoUpdateStarted = useRef(false);

  const refresh = useCallback(async (automatic = false) => {
    if (!navigator.onLine) {
      if (!automatic) setMessage("Could not update station data. You are offline; using the previous downloaded version.");
      return;
    }
    setUpdating(true);
    if (!automatic) setMessage(null);
    try {
      const result = await updateStationData();
      setStationCount(result.stationCount);
      setUpdatedAt(result.updatedAt);
      const savedTrip = await getLatestTrip();
      if (savedTrip) {
        const stations = await db.stations.toArray();
        setMatchedStations(findStationsAlongRoute(savedTrip.route.geometry, stations, savedTrip.maxDistanceFromRouteKm));
      }
      setMessage(`Station data updated. ${result.stationCount.toLocaleString()} CNG stations saved.`);
    } catch {
      setMessage("Could not update station data. Using the previous downloaded version.");
    } finally {
      setUpdating(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.all([db.stations.toArray(), getStationSnapshotMeta(), getLatestTrip(), getSettings()]).then(([stations, meta, savedTrip, storedSettings]) => {
      if (!active) return;
      setStationCount(stations.length);
      setUpdatedAt(meta.updatedAt);
      setTrip(savedTrip);
      setSettings(storedSettings);
      setCorridor(storedSettings.stationCorridorKm);
      if (savedTrip) {
        setMatchedStations(findStationsAlongRoute(savedTrip.route.geometry, stations, savedTrip.maxDistanceFromRouteKm));
      }
      const oldOrMissing = !meta.updatedAt || Date.now() - new Date(meta.updatedAt).valueOf() > DAY_MS;
      if (oldOrMissing && navigator.onLine && !autoUpdateStarted.current) {
        autoUpdateStarted.current = true;
        void refresh(true);
      }
    });
    return () => {
      active = false;
    };
  }, [refresh]);

  useEffect(() => {
    if (currentLocation && !originPlace && !originInput) {
      setOriginPlace(currentLocation);
      setOriginInput("Current location");
    }
  }, [currentLocation, originInput, originPlace]);

  useEffect(() => {
    let active = true;
    if (selectedStationId === null) {
      setStationDetail(null);
      setDetailState("idle");
      return () => { active = false; };
    }
    void (async () => {
      setStationDetail(null);
      setDetailState("loading");
      const cached = await db.stationDetails.get(selectedStationId);
      if (!active) return;
      if (cached) {
        setStationDetail(cached);
        setDetailState("cached");
      }
      if (!online) {
        if (!cached) setDetailState("idle");
        return;
      }
      try {
        const live = await fetchMimitStationDetail(selectedStationId);
        await db.stationDetails.put(live);
        if (!active) return;
        setStationDetail(live);
        setDetailState("live");
      } catch {
        if (!active) return;
        setDetailState(cached ? "cached" : "error");
      }
    })();
    return () => { active = false; };
  }, [online, selectedStationId]);

  const planRoute = async () => {
    const origin = originPlace ?? parseCoordinateInput(originInput);
    const destination = destinationPlace ?? parseCoordinateInput(destinationInput);
    if (!origin || !destination) {
      setRouteMessage("Search and select both origin and destination, or enter valid latitude,longitude.");
      return;
    }
    if (!navigator.onLine) {
      setRouteMessage("A new route requires internet. Your saved route and cached CNG stations are still available.");
      return;
    }
    setPlanning(true);
    setRouteMessage(null);
    try {
      const route = await calculateRoute(origin, destination);
      const savedTrip: SavedTrip = {
        id: crypto.randomUUID(),
        createdAt: new Date().toISOString(),
        origin,
        destination,
        route,
        maxDistanceFromRouteKm: corridor,
      };
      await saveTrip(savedTrip);
      const stations = await db.stations.toArray();
      setTrip(savedTrip);
      setMatchedStations(findStationsAlongRoute(route.geometry, stations, corridor));
      setSelectedStationId(null);
      setRouteMessage("Route calculated and saved for offline use.");
    } catch {
      setRouteMessage("A new route could not be calculated. Your saved route is still available.");
    } finally {
      setPlanning(false);
    }
  };

  return (
    <main className="app-shell">
      <header>
        <p className="eyebrow">Road-trip utility</p>
        <h1>CNG Route</h1>
        <p className={`network-status ${online ? "online" : "offline"}`}><span />{online ? "Online" : "Offline"}</p>
        <p className="data-status">{online ? formatUpdateTime(updatedAt) : updatedAt ? `Using data from ${new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" }).format(new Date(updatedAt))}` : "No station data downloaded"} · {stationCount.toLocaleString()} stations</p>
      </header>
      {!online && (
        <p className="offline-banner">Offline: the saved route, cached stations, prices, details, and GPS remain available. New routes and place searches require internet.</p>
      )}
      <section className="panel">
        <h2>Plan a route</h2>
        <button
          type="button"
          className="location-button"
          onClick={() => requestLocation()}
          disabled={locating}
        >
          {locating ? "Getting location…" : "Use my location"}
        </button>
        {currentLocation && originPlace?.label !== "Current location" && (
          <button
            type="button"
            className="text-button"
            onClick={() => {
              setOriginPlace(currentLocation);
              setOriginInput("Current location");
            }}
          >
            Select current location as origin
          </button>
        )}
        {locationError && <p className="field-message" role="status">{locationError}</p>}
        <PlaceSearch
          id="origin-search"
          label="Origin"
          value={originInput}
          selected={originPlace}
          onValueChange={(value) => { setOriginInput(value); setOriginPlace(null); }}
          onSelect={(place) => { setOriginPlace(place); setOriginInput(place.label); }}
        />
        <PlaceSearch
          id="destination-search"
          label="Destination"
          value={destinationInput}
          selected={destinationPlace}
          onValueChange={(value) => { setDestinationInput(value); setDestinationPlace(null); }}
          onSelect={(place) => { setDestinationPlace(place); setDestinationInput(place.label); }}
        />
        <label>
          Route corridor
          <select value={corridor} onChange={(event) => {
            const stationCorridorKm = Number(event.target.value) as 5 | 10 | 20;
            const nextSettings = { ...settings, stationCorridorKm };
            setCorridor(stationCorridorKm);
            setSettings(nextSettings);
            void saveSettings(nextSettings);
          }}>
            <option value={5}>5 km</option>
            <option value={10}>10 km</option>
            <option value={20}>20 km</option>
          </select>
        </label>
        <button type="button" onClick={() => void planRoute()} disabled={planning}>
          {planning ? "Calculating route…" : "Find CNG stations"}
        </button>
        {routeMessage && <p className="notice" role="status">{routeMessage}</p>}
      </section>

      {trip && (
        <>
          <section className="panel route-summary">
            <h2>Saved route</h2>
            <p><strong>{trip.origin.label}</strong> → <strong>{trip.destination.label}</strong></p>
            <p>{Math.round(trip.route.distanceKm).toLocaleString()} km · about {Math.round(trip.route.durationMinutes / 60)} h · {trip.maxDistanceFromRouteKm} km corridor</p>
            <p><strong>{matchedStations.length.toLocaleString()}</strong> cached CNG stations found along this route</p>
          </section>
          <MapView
            trip={trip}
            stations={matchedStations}
            selectedStationId={selectedStationId}
            onSelectStation={setSelectedStationId}
            currentLocation={currentLocation}
          />
          {selectedStation && <StationDetails station={selectedStation} detail={stationDetail} detailState={detailState} />}
          <StationList stations={matchedStations} selectedStationId={selectedStationId} onSelect={setSelectedStationId} />
        </>
      )}

      <section className="panel data-panel">
        <h2>Station data</h2>
        <p>Download the official Italian CNG station snapshot for route matching and offline use.</p>
        <button type="button" onClick={() => void refresh(false)} disabled={updating}>
          {updating ? "Updating…" : "Update data"}
        </button>
        {message && <p className="notice" role="status">{message}</p>}
      </section>

      <Settings value={settings} onSave={async (next) => {
        await saveSettings(next);
        setSettings(next);
        setCorridor(next.stationCorridorKm);
      }} />

      <details className="panel about-panel">
        <summary>About the data</summary>
        <p>Station and price data comes from Italian MIMIT fuel datasets and the Osservaprezzi service.</p>
        <p>Opening hours are reported by station operators and may be missing or inaccurate. Station coordinates may occasionally be inaccurate.</p>
        <p>Check critical station information before relying on a station with very little remaining fuel.</p>
        <p>This is a personal trip utility, not an official government application.</p>
      </details>
    </main>
  );
}
