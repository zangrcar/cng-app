import { useState } from "react";
import { parseCoordinateInput, searchPlaces } from "../api/geocoding";
import type { Place } from "../types";

type Props = {
  id: string;
  label: string;
  value: string;
  selected: Place | null;
  onValueChange: (value: string) => void;
  onSelect: (place: Place) => void;
};

export default function PlaceSearch({ id, label, value, selected, onValueChange, onSelect }: Props) {
  const [results, setResults] = useState<Place[]>([]);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const search = async () => {
    if (!value.trim()) {
      setMessage("Enter a place name or latitude,longitude.");
      return;
    }
    const directCoordinates = parseCoordinateInput(value);
    if (!directCoordinates && !navigator.onLine) {
      setMessage("Place search requires internet. You can enter latitude,longitude instead.");
      return;
    }
    setSearching(true);
    setMessage(null);
    setResults([]);
    try {
      const places = await searchPlaces(value);
      if (directCoordinates && places[0]) {
        onSelect(places[0]);
        setMessage("Coordinates selected.");
      } else if (places.length === 0) {
        setMessage("No matching places found. You can enter latitude,longitude instead.");
      } else {
        setResults(places);
      }
    } catch {
      setMessage("Place search is unavailable. You can enter latitude,longitude instead.");
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="place-search">
      <label htmlFor={id}>{label}</label>
      <div className="input-action">
        <input
          id={id}
          value={value}
          onChange={(event) => {
            onValueChange(event.target.value);
            setResults([]);
            setMessage(null);
          }}
          placeholder={label === "Destination" ? "Gallipoli or 40.0559,17.9926" : "Ljubljana or 46.0569,14.5058"}
        />
        <button type="button" className="secondary-button" onClick={() => void search()} disabled={searching}>
          {searching ? "Searching…" : "Search"}
        </button>
      </div>
      {selected && <p className="selected-place">Selected: {selected.label}</p>}
      {message && <p className="field-message" role="status">{message}</p>}
      {results.length > 0 && (
        <div className="search-results" aria-label={`${label} search results`}>
          {results.map((place) => (
            <button
              type="button"
              key={`${place.lat}-${place.lon}`}
              onClick={() => {
                onSelect(place);
                setResults([]);
                setMessage(null);
              }}
            >
              {place.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
