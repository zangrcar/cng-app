import { useEffect, useState } from "react";
import type { AppSettings } from "../types";

export default function Settings({ value, onSave }: { value: AppSettings; onSave: (settings: AppSettings) => Promise<void> }) {
  const [draft, setDraft] = useState(value);
  const [saved, setSaved] = useState(false);
  useEffect(() => setDraft(value), [value]);

  const save = async () => {
    const vehicleRangeKm = Math.max(1, Math.round(draft.vehicleRangeKm));
    const reserveKm = Math.max(0, Math.min(vehicleRangeKm, Math.round(draft.reserveKm)));
    const next = { ...draft, vehicleRangeKm, reserveKm };
    setDraft(next);
    await onSave(next);
    setSaved(true);
  };

  return (
    <details className="panel settings-panel">
      <summary>Trip settings</summary>
      <div className="settings-grid">
        <label>
          Vehicle range (km)
          <input type="number" min="1" value={draft.vehicleRangeKm} onChange={(event) => { setDraft({ ...draft, vehicleRangeKm: Number(event.target.value) }); setSaved(false); }} />
        </label>
        <label>
          Reserve (km)
          <input type="number" min="0" value={draft.reserveKm} onChange={(event) => { setDraft({ ...draft, reserveKm: Number(event.target.value) }); setSaved(false); }} />
        </label>
        <label>
          Default corridor
          <select value={draft.stationCorridorKm} onChange={(event) => { setDraft({ ...draft, stationCorridorKm: Number(event.target.value) as 5 | 10 | 20 }); setSaved(false); }}>
            <option value={5}>5 km</option>
            <option value={10}>10 km</option>
            <option value={20}>20 km</option>
          </select>
        </label>
        <button type="button" className="secondary-button" onClick={() => void save()}>Save settings</button>
        {saved && <span className="muted" role="status">Settings saved</span>}
      </div>
    </details>
  );
}
