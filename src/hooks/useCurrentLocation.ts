import { useState } from "react";
import type { Place } from "../types";

export function useCurrentLocation() {
  const [location, setLocation] = useState<Place | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);

  const requestLocation = () => {
    if (!("geolocation" in navigator)) {
      setLocationError("Location unavailable. Search for your starting point instead.");
      return;
    }
    setLocating(true);
    setLocationError(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocation({ label: "Current location", lat: coords.latitude, lon: coords.longitude });
        setLocating(false);
      },
      () => {
        setLocationError("Location unavailable. Search for your starting point instead.");
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    );
  };

  return { location, locating, locationError, requestLocation };
}
