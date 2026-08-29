# CNG Route Planner

A small Android-first PWA for finding official Italian CNG/metano stations along a driving route. It calculates the route online, matches downloaded stations locally, and hands a selected station to Google Maps for navigation.

## Run

Requires Node.js 22.

```bash
npm install
npm run dev
```

Open `http://127.0.0.1:5173`.

For the built app:

```bash
npm run build
npm start
```

Open `http://127.0.0.1:4173`.

## Data sources

- MIMIT official station/price CSV and optional Osservaprezzi live detail API
- OSRM for driving routes
- Nominatim for deliberate place searches
- OpenStreetMap tiles and map data

MIMIT does not allow direct browser requests, so the included tiny stateless Node proxy forwards only MIMIT CSV/detail calls. There is no server database or authentication. OSRM and Nominatim are called directly.

## Offline

After the app has loaded once and station data plus a route have been saved, the installed PWA can reopen offline and show the saved route, locally recalculate stations, list CSV prices, and show previously cached station details. GPS may still work. A new route, place-name search, live update, uncached map tile, Google navigation, or uncached station detail requires internet.

The MIMIT live API is optional. Downloaded MIMIT CSV data is the reliable offline baseline, and a failed update never deletes the previous snapshot.

## Phone use

Keep `npm run dev` (or `npm start` after a build) running in the Ubuntu/proot terminal. Open the local URL in Android Chrome, then use **Install app** or **Add to Home screen** from the browser menu. Load the app, tap **Update data**, and plan the trip while online before relying on offline mode.
