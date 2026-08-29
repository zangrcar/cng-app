# Implementation progress

## Current status

Phase: Complete - Definition of Done satisfied
Status: complete

## Completed

- Phase 1: React/Vite/TypeScript scaffold is installed and working.
- Vite binds to `0.0.0.0:5173`; the app is reachable at `http://127.0.0.1:5173`.
- Strict TypeScript configuration and the required root npm scripts are present.
- Added Vite client declarations so CSS side-effect imports typecheck with the installed TypeScript version.
- Phase 2: implemented MIMIT pipe-CSV download/parsing, strict CNG classification, malformed-row isolation, and price normalization.
- Added the `cng-route-planner` Dexie database with stations, station details, trips, meta, and settings stores.
- Station snapshot replacement and update metadata are committed in one Dexie transaction; empty/failed refreshes preserve old data.
- Added manual and non-blocking daily automatic station refresh UI.
- Added a stateless MIMIT-only Node proxy because the official CSV/live endpoints do not return browser CORS headers.
- `npm run dev` starts Vite and the proxy together; `npm start` serves the production PWA and proxy together.
- Phase 3: implemented direct OSRM driving route requests with full GeoJSON geometry and validated response parsing.
- Successful routes are saved separately from station data in Dexie and the latest route is restored at startup.
- Added coordinate-based planning and the specified offline/provider-failure messages; failed route requests do not replace the saved trip.
- Phase 4: implemented Turf-based shortest distance from route, cumulative position along route, corridor filtering, and route-order sorting.
- Cached stations are recalculated from the latest snapshot and saved route at startup and after every successful data/route update.
- Added a route-bounds prefilter and a ~20 m simplified calculation line for phone performance while keeping full route geometry for storage/display.
- Phase 5: implemented the Leaflet/OSM route map with origin, destination, and price-labelled CNG station markers.
- Added the route-ordered station list with distance-from-start, geometric distance-from-route, lowest price, service mode, freshness, and CSV source.
- Marker and list selection share one station detail card; details include all CSV prices, explicit unknown hours, source information, and Google Maps handoff.
- Phase 6: implemented button-triggered Nominatim search for origin and destination with up to five selectable results.
- Direct coordinate entry bypasses geocoding; clear offline/provider errors retain the coordinate fallback.
- Added strictly user-triggered browser GPS, automatic current-location origin selection when appropriate, and a current-location map marker.
- Added browser online/offline event tracking and header status/data-age messaging.
- Phase 7: implemented on-demand selected-station MIMIT detail fetching through the verified proxy and Dexie detail caching.
- Normalized live CNG prices, services, H24/closed/continuous/split/unknown opening hours, and cached-fetch timestamps.
- Added CSV/API price source merging: agreeing prices become CSV + API, while differing live and CSV values remain visible separately.
- Station details show live or cached API sources, freshness, services, Europe/Rome open/closed status, explicit unknown hours, and the holiday caveat.
- Phase 8: configured a standalone PWA manifest, Android-sized PNG icons, automatic service-worker registration, and app-shell-only Workbox precaching.
- Added persisted vehicle range, reserve, and default corridor settings in IndexedDB.
- Added an explicit offline banner, data-source disclaimer, and concise phone/local-proxy/offline README instructions.
- Verified offline-style Dexie close/reopen restores the saved route, complete station snapshot, cached station detail, and settings, then recalculates corridor stations locally.
- Phase 9: audited all Definition of Done paths and added freshness boundary tests; all required high-risk test categories are covered.
- Hardened CSV parsing so blank coordinates are rejected instead of coercing to geographic zero.
- Hardened production service-worker/manifest cache headers to require revalidation while keeping hashed assets immutable.
- Completed portrait-phone CSS review: 44-48 px controls, single-column small-screen flow, bounded map height, wrapping station/detail cards, and no intentional horizontal overflow.
- Phase 10: completed a fresh live Ljubljana to Gallipoli end-to-end provider smoke test using the application parsers and matching logic.
- Final install, development server, production server, service-worker cache-header, typecheck, test, and build verification all pass.

## Important discoveries

- The initial scaffold already existed even though this file said Phase 1 had not started.
- The environment initially had no Node.js installation. Node.js 22.23.2 was installed for verification.
- The resolved Vite/TypeScript versions require `src/vite-env.d.ts` for CSS side-effect import declarations.
- Real CORS probes: MIMIT CSV and live API omit `Access-Control-Allow-Origin`; OSRM and Nominatim return `Access-Control-Allow-Origin: *`.
- The 2026-08-28 MIMIT files still use metadata before a pipe-delimited header and the documented columns.
- The live snapshot parsed 1,607 usable CNG stations and 1,707 CNG prices; 7 CNG stations had invalid coordinates and were safely skipped.
- A live Ljubljana to Gallipoli OSRM route returned 1,232 km, about 12.8 hours, and 11,642 geometry points.
- The live 10 km corridor match found 305 route-ordered stations in about 2.1 seconds (improved from 17.7 seconds against raw full geometry).
- The minified main chunk is currently about 530 kB due Leaflet/Turf; build succeeds and PWA shell caching will cover it in Phase 8.
- “Gallipoli” currently returns three Nominatim results; Gallipoli, Lecce is the second result, so explicit result selection is important and implemented.
- Current live detail payloads use `fuels`, `services`, and `orariapertura`; weekday IDs 1-7 map Monday-Sunday and ID 8 is a special/holiday row ignored by regular-week normalization.
- Live station 3598 returned one CNG price, seven H24 weekday rows, and four services through the local proxy.
- Production PWA generation precaches 11 shell/icon entries (~599 KiB); API responses are intentionally not service-worker cached.
- Generated TypeScript build-info files are now ignored as build artifacts.
- Final live smoke: 1,607 CNG stations; 1,235 km/11,750-point route; 305 ordered 10 km corridor matches; selected station 13645 returned a live price and opening hours; Google Maps URL valid.
- Node-only Nominatim smoke requests require an identifying user agent; Android/browser requests naturally provide one. Direct browser CORS remains supported.

## Deviations from SPEC.md

- A tiny proxy is required only for MIMIT due verified browser CORS limitations. It has no database or persistence. OSRM and Nominatim remain direct browser calls.

## Verification

- npm install: PASS (413 packages, 0 vulnerabilities)
- dev server: PASS (`127.0.0.1:5173`, Vite + MIMIT proxy from one command)
- production server: PASS (`127.0.0.1:4173`, built app + MIMIT proxy)
- typecheck: PASS
- tests: PASS (39 tests across 10 files)
- build: PASS
- live MIMIT CSV: PASS (1,607 stations, 1,707 prices)
- live OSRM route: PASS (Ljubljana to Gallipoli)
- live route/station matching: PASS (305 stations, correctly ordered)
- live Nominatim Gallipoli search: PASS (3 results including Gallipoli, Lecce)
- live MIMIT station detail: PASS (price, hours, services)
- full live Ljubljana to Gallipoli flow: PASS (305 ordered stations; selected detail and Google Maps handoff valid)
- offline data reopen/recalculation test: PASS
- PWA shell precache inspection: PASS (HTML, JS, CSS, SVG/PNG icons)

## Remaining phases

- [x] 1. React/Vite project setup
- [x] 2. IndexedDB + MIMIT CSV download/parser
- [x] 3. OSRM route calculation
- [x] 4. Turf route/station matching
- [x] 5. Map + station list
- [x] 6. GPS + destination search
- [x] 7. MIMIT live station enrichment
- [x] 8. PWA/offline mode
- [x] 9. Tests/mobile polish
- [x] 10. Ljubljana -> Gallipoli real smoke test

## Next action

No implementation task remains. Run `npm run dev`, open `http://127.0.0.1:5173`, update station data, and install/add the PWA to the Android home screen before the trip.
