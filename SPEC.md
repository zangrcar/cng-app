# GOAL: Build a simple CNG Route Planner PWA for my two-week Italy trip

Implement the complete application in the current directory.

This is a **small personal travel utility for one two-week trip**, not a long-term production product.

Do not overengineer it.

The goal is to get a reliable, practical application working on my Android phone as quickly as possible.

I am developing entirely from my phone using:

* Termux;
* Ubuntu/proot;
* Codex CLI;
* terminal only.

Do not require a desktop IDE.

Do not ask me architectural questions unless something is genuinely impossible. The important decisions are already made below.

If a minor detail is unspecified, choose the simplest reasonable solution and continue.

---

# 1. Main philosophy

Build the smallest architecture that satisfies the real use case.

Prefer:

```text
React PWA
+
IndexedDB
+
public APIs
```

over:

```text
frontend
+
backend
+
database server
+
containers
+
deployment infrastructure
```

Do NOT add infrastructure simply because it would be appropriate for a long-lived production system.

This app only needs to work reliably for my trip.

---

# 2. The application I want

I drive a CNG/metano car with approximately:

```text
300 km CNG range
```

I want to enter:

```text
Current location
→
Destination
```

For example:

```text
Ljubljana
→
Gallipoli
```

The application should:

1. calculate the driving route;
2. display it on a map;
3. find CNG/metano stations near the route;
4. show them as map markers;
5. show them in a list ordered from trip start toward destination;
6. let me tap a station;
7. show its CNG price;
8. show opening hours if available;
9. show how old the information is;
10. show whether information came from MIMIT CSV, MIMIT API, or both;
11. show how far the station is from my route;
12. let me tap **Open in Google Maps** for actual navigation;
13. retain useful station and route information when I lose internet.

This is NOT a navigation application.

Google Maps handles actual navigation after I choose a station.

---

# 3. Keep this project small

Do NOT implement:

* Docker;
* Docker Compose;
* PostgreSQL;
* SQLite;
* Redis;
* server-side persistence;
* database migrations;
* repository layers;
* Spring;
* NestJS;
* GraphQL;
* microservices;
* authentication;
* user accounts;
* cloud infrastructure;
* admin interface;
* analytics;
* payment;
* native Android app;
* native iOS app;
* Google Maps SDK;
* Google Places API;
* Google Routes API;
* offline road routing;
* offline map-region downloads;
* complex state management;
* production observability;
* scheduled backend jobs.

Do not introduce a backend unless browser CORS actually makes one necessary.

---

# 4. Technology stack

Use:

```text
Node.js 22
npm
TypeScript
React
Vite
Leaflet
React Leaflet
Turf.js
Dexie
IndexedDB
Zod
vite-plugin-pwa
Vitest
```

Use plain CSS.

Do NOT use:

* Angular;
* Next.js;
* Redux;
* Tailwind;
* Material UI;
* Bootstrap.

Keep dependencies minimal.

---

# 5. Expected architecture

Start with this:

```text
                 Android phone

                     PWA
                      |
        +-------------+-------------+
        |             |             |
        v             v             v
     Leaflet        APIs        IndexedDB
        |             |             |
        |             |             |
        |        +----+----+        |
        |        |    |    |        |
        v        v    v    v        v
       OSM     MIMIT OSRM Nominatim
                              
                              stores:
                              - all CNG stations
                              - prices
                              - cached details
                              - saved route
                              - settings
```

There should initially be **no server-side database and no backend application**.

---

# 6. Important fallback rule

First attempt direct browser requests to the required services.

If a specific provider cannot be used from the browser because of CORS:

do NOT redesign the application.

Add the **smallest possible local proxy** only for the blocked requests.

The proxy must:

* be Node + Fastify or a similarly tiny server;
* contain no database;
* contain no persistence;
* contain no repository layer;
* simply forward requests;
* optionally normalize the response if necessary;
* be started together with the app using one npm command.

Example only if needed:

```text
browser
   |
   v
tiny local proxy
   |
   v
MIMIT
```

Do NOT create the proxy pre-emptively.

First test whether it is required.

---

# 7. Phone development requirements

Everything must work from the terminal.

I should be able to run approximately:

```bash
npm install
npm run dev
```

Then open:

```text
http://127.0.0.1:5173
```

Vite must bind to:

```text
0.0.0.0
```

Do not require two terminal windows.

If a tiny proxy becomes necessary:

`npm run dev` must start both frontend and proxy automatically.

Use something lightweight such as `concurrently`.

Do not require Docker.

---

# 8. Repository structure

Keep the structure small.

Prefer approximately:

```text
cng-route-planner/
├── package.json
├── package-lock.json
├── tsconfig.json
├── vite.config.ts
├── index.html
├── .gitignore
├── .env.example
├── README.md
│
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── styles.css
│   │
│   ├── api/
│   │   ├── mimit.ts
│   │   ├── routing.ts
│   │   └── geocoding.ts
│   │
│   ├── db/
│   │   └── db.ts
│   │
│   ├── map/
│   │   ├── MapView.tsx
│   │   └── StationMarker.tsx
│   │
│   ├── route/
│   │   └── routeStations.ts
│   │
│   ├── stations/
│   │   ├── StationList.tsx
│   │   ├── StationCard.tsx
│   │   └── StationDetails.tsx
│   │
│   ├── components/
│   │   ├── TripForm.tsx
│   │   ├── PlaceSearch.tsx
│   │   ├── OfflineBanner.tsx
│   │   └── SourceBadge.tsx
│   │
│   ├── hooks/
│   │   ├── useCurrentLocation.ts
│   │   └── useNetworkStatus.ts
│   │
│   ├── utils/
│   │   ├── cng.ts
│   │   ├── openingHours.ts
│   │   ├── freshness.ts
│   │   └── googleMaps.ts
│   │
│   └── types.ts
│
├── tests/
│
└── server/
    └── proxy.ts
```

The `server/` directory should only exist if a proxy actually becomes necessary.

Do not create dozens of files merely to follow the example structure.

Keep related logic together where that makes the code easier to understand.

---

# 9. Data sources

Use two MIMIT sources.

## Durable baseline

Official MIMIT CSV data.

Known current endpoints:

```text
https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv

https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv
```

The CSV data provides the reliable baseline:

* stations;
* coordinates;
* addresses;
* CNG availability;
* prices;
* price timestamps.

The current delimiter is expected to be:

```text
|
```

Inspect the actual files before finalizing the parser.

Do not blindly assume the format.

---

# 10. MIMIT live API

Also use the public MIMIT Osservaprezzi JSON interface when available.

Current expected base:

```text
https://carburanti.mise.gov.it/ospzApi
```

Useful operations currently include approximately:

```text
POST /search/route

GET /registry/servicearea/{stationId}
```

For methane/metano the currently known selector is approximately:

```text
3-x
```

Treat this API as:

```text
optional enhancement
```

NOT:

```text
required infrastructure
```

If it changes or fails:

the application must still work using cached/CSV data.

---

# 11. CNG detection

Implement one function:

```ts
isCngFuelName(name: string): boolean
```

Normalize:

* trim;
* uppercase;
* Unicode normalization;
* whitespace.

Treat these as CNG where appropriate:

```text
METANO
CNG
GNC
L-GNC
```

Do NOT treat these as CNG:

```text
GNL
LNG
METANO LIQUIDO
GPL
BENZINA
GASOLIO
```

Write tests.

This distinction matters.

---

# 12. CSV parsing

The current station CSV contains fields approximately like:

```text
idimpianto
Gestore
Bandiera
Tipo Impianto
Nome Impianto
Indirizzo
Comune
Provincia
Latitudine
Longitudine
```

The price CSV contains approximately:

```text
idimpianto
descCarburante
prezzo
isSelf
dtComu
```

Do not rely on the first physical line being the CSV header.

Find the line containing:

```text
idimpianto
```

and parse from there.

Handle:

* UTF-8 BOM;
* extra metadata lines;
* malformed individual rows;
* invalid coordinates;
* invalid price rows.

One malformed station must not break the whole import.

---

# 13. Station identity

Use:

```text
MIMIT station ID
```

as the canonical station identifier.

Do not fuzzy-match station names.

---

# 14. IndexedDB is the database

Use Dexie.

Create database:

```text
cng-route-planner
```

Use only browser-side IndexedDB.

Stores should approximately be:

```text
stations
stationDetails
trips
meta
settings
```

No second database is needed.

---

# 15. `stations` store

Store the complete current Italian CNG station snapshot.

Each record should include approximately:

```ts
type CachedStation = {
  id: number;

  name: string;
  brand: string | null;

  address: string | null;
  municipality: string | null;
  province: string | null;

  lat: number;
  lon: number;

  stationType: string | null;

  prices: CngPrice[];

  csvExtractionDate: string | null;
};
```

Only retain stations that have CNG/metano price information.

If coordinates are invalid:

skip them from map/route calculations.

Do not let one invalid coordinate break synchronization.

---

# 16. Price model

Use approximately:

```ts
type CngPrice = {
  value: number;

  fuelName: string;

  serviceMode:
    | "self"
    | "served";

  communicatedAt: string | null;

  source:
    | "csv"
    | "api"
    | "both";

  stale: boolean;
};
```

Unit is:

```text
EUR/kg
```

---

# 17. Download station data

Create an explicit operation:

```text
Update station data
```

When executed online:

1. download MIMIT price CSV;
2. identify CNG rows;
3. download station CSV;
4. match station IDs;
5. normalize;
6. validate;
7. construct complete CNG station snapshot;
8. save it to IndexedDB;
9. store update timestamp.

Do not erase the existing snapshot before the new one has downloaded and parsed successfully.

Process:

```text
old working data
      |
download new files
      |
parse completely
      |
validate
      |
SUCCESS?
  |        |
 yes       no
  |        |
replace    keep old data
```

This is mandatory.

---

# 18. Automatic update behaviour

When the app starts online:

check when station data was last updated.

If older than approximately:

```text
24 hours
```

offer or automatically attempt a refresh.

Do not block app startup.

Immediately display existing cached data first.

If refresh fails:

continue with old data.

For a two-week trip, a simple daily refresh is sufficient.

Do not implement background schedulers.

---

# 19. Manual update button

Provide:

```text
Update data
```

somewhere unobtrusive.

After update show:

```text
Station data updated
29 Aug · 15:42
```

If it fails:

```text
Update failed.
Using data from 28 Aug.
```

Never delete usable cached data because refresh failed.

---

# 20. Routing

Use OSRM.

Expected public service:

```text
https://router.project-osrm.org
```

Request:

```text
driving
overview=full
geometries=geojson
```

Get:

* full route LineString;
* distance;
* duration.

Save route geometry in IndexedDB after successful planning.

If OSRM cannot be called directly due CORS:

route it through the tiny proxy.

Do not add a larger backend.

---

# 21. Geocoding

Use Nominatim.

Expected service:

```text
https://nominatim.openstreetmap.org
```

Do NOT implement autocomplete on every keystroke.

Use:

```text
Destination
[ Gallipoli             ]
[ Search ]
```

Only perform a geocoding request after the user presses Search.

Show up to approximately five results.

Allow direct coordinate entry:

```text
40.0559,17.9926
```

If valid coordinates are entered:

do not geocode them.

If Nominatim cannot be called directly from the browser:

use the same tiny proxy.

---

# 22. Current location

Do not automatically request location permission.

Provide:

```text
Use my location
```

Only then request browser geolocation.

Success:

```text
Current location
```

becomes origin.

Failure/denial:

show:

```text
Location unavailable.
Search for your starting point instead.
```

GPS may continue working without internet, so preserve it during offline mode.

---

# 23. Trip model

A saved trip should contain approximately:

```ts
type SavedTrip = {
  id: string;

  createdAt: string;

  origin: Place;
  destination: Place;

  route: {
    geometry: GeoJSON.LineString;
    distanceKm: number;
    durationMinutes: number;
  };

  maxDistanceFromRouteKm: number;
};
```

Do not save duplicate copies of every station in the trip.

Stations can be recalculated from:

```text
saved route
+
latest IndexedDB station snapshot
```

That is better.

---

# 24. Why saved route and stations are separate

This is important.

Example:

```text
Monday
plan Ljubljana → Gallipoli

Tuesday
download newer station CSV

Wednesday
lose internet
```

The app should combine:

```text
Monday's route
+
Tuesday's station data
```

and calculate stations near that route locally.

Do not freeze station data into the original trip unnecessarily.

---

# 25. Finding stations near route

Use Turf.js.

Implement:

```ts
findStationsAlongRoute(
  route,
  stations,
  maxDistanceKm
)
```

For every candidate station calculate:

```text
distanceFromRouteKm
positionAlongRouteKm
```

---

# 26. `distanceFromRouteKm`

This means:

```text
shortest geometric distance
between station and route
```

Do NOT call it:

```text
detour
```

because actual driving distance could be different.

UI:

```text
3.2 km from route
```

---

# 27. `positionAlongRouteKm`

This means:

```text
distance along the planned route
from trip origin
to the station's nearest route point
```

Example:

```text
421 km from trip start
```

Use this value to sort stations.

This is one of the most important pieces of information.

---

# 28. Route corridor

Default:

```text
10 km
```

Allow:

```text
5 km
10 km
20 km
```

Simple select.

No custom slider needed.

Filter:

```text
distanceFromRouteKm <= selected corridor
```

---

# 29. Performance

There are only roughly thousands of CNG stations.

It is acceptable to calculate local geometry against all cached CNG stations.

Still, a simple route bounding-box prefilter is welcome if easy.

Do not build a spatial database or R-tree system.

Keep it simple.

---

# 30. Live route search

If MIMIT's live route endpoint works:

use it as additional enrichment.

Do not rely on it for station discovery.

The reliable algorithm is:

```text
OSRM route
+
cached MIMIT CSV stations
+
Turf
```

MIMIT live search can add:

* fresher prices;
* stations missing from the latest local snapshot;
* additional source confidence.

If it fails:

ignore the failure and continue.

---

# 31. Route planning flow

When user presses:

```text
Find CNG stations
```

do:

```text
1. validate origin/destination

2. call OSRM

3. save route locally

4. load cached CNG stations

5. calculate stations within corridor

6. sort by position along route

7. display route + markers + list

8. if internet/live MIMIT API available:
      enrich what is practical

9. do not wait forever for enrichment
```

The route and CSV station list should appear even if live enrichment fails.

---

# 32. MIMIT station detail

When the user taps a station:

try to retrieve current station detail through:

```text
/registry/servicearea/{stationId}
```

if online.

Use the live result for:

* opening hours;
* live price;
* services;
* richer station details.

Save normalized detail to IndexedDB.

Do NOT load details for every station ahead of time.

Only fetch when selected.

This keeps the app fast and reduces network use.

---

# 33. Cached station details

Store approximately:

```ts
type CachedStationDetail = {
  stationId: number;

  fetchedAt: string;

  prices: CngPrice[];

  openingHours: NormalizedOpeningHours | null;

  services: string[];

  rawSourceAvailable: boolean;
};
```

When offline:

use cached detail if available.

Show age.

Example:

```text
Opening hours cached 18h ago
```

---

# 34. Opening hours

Normalize live API hours.

Important rule:

```text
missing opening hours
!=
closed
```

If not reported:

show:

```text
Opening hours not reported
```

Never show:

```text
Closed
```

unless the source explicitly indicates closed.

---

# 35. Opening hour cases

Support:

* explicitly closed;
* H24;
* continuous opening;
* split morning/afternoon opening;
* unknown;
* malformed/incomplete values.

If data cannot safely be interpreted:

use:

```text
unknown
```

Do not invent hours.

---

# 36. Timezone

Calculate open/closed status using:

```text
Europe/Rome
```

Do not rely blindly on browser timezone.

Display:

```text
Open now
```

or:

```text
Closed now
```

only when the information is known.

Otherwise:

```text
Hours unknown
```

---

# 37. Holiday limitation

Opening hours may not correctly reflect holidays.

Where hours are shown, add small text:

```text
Reported regular hours; holidays may differ.
```

Do not attempt to implement an Italian holiday schedule.

Not needed for this trip app.

---

# 38. Price source merging

If CSV and live API both contain price information:

retain both where useful.

If they agree:

source can be:

```text
CSV + API
```

If they differ:

show live API price as primary if valid.

Keep CSV as fallback/reference.

Example detail:

```text
Live:
€1.529/kg · self

CSV:
€1.549/kg · self
snapshot 28 Aug
```

---

# 39. Multiple prices

Keep self-service and served separately.

Example:

```text
€1.499/kg · self

€1.549/kg · served
```

Do not merge them into one ambiguous value.

Primary marker/list price can use the lowest available valid CNG price.

Always show its service type.

---

# 40. Price freshness

Where timestamps are available:

roughly classify:

```text
<= 24h
fresh

1–7 days
old

> 7 days
stale
```

Do not hide stale prices.

Show:

```text
€1.529/kg
updated 4h ago
```

or:

```text
€1.529/kg
price may be outdated
```

---

# 41. Source display

Track source independently for important information.

Possible labels:

```text
CSV
API
CSV + API
Cached API
```

Important fields:

```text
location
price
opening hours
```

Station details should show where each came from.

---

# 42. Main UI

Optimize first for Android phone.

Use this rough order:

```text
CNG Route
Online / Offline
data age

Origin

Destination

Route corridor

[ Find CNG stations ]

Map

Selected station detail

Stations ahead
```

Keep it practical.

Do not create a fancy dashboard.

---

# 43. Header

Show:

```text
CNG Route
```

Then small status:

```text
Online
CSV: updated today
```

or:

```text
Offline
Using data from 28 Aug
```

---

# 44. Planning form

Origin:

```text
[ Use my location ]

or

[ Search origin ]
```

Destination:

```text
[ Gallipoli             ]
[ Search ]
```

After selecting valid origin and destination:

```text
Corridor: [ 10 km ▼ ]

[ Find CNG stations ]
```

---

# 45. Map

Use Leaflet + OpenStreetMap.

Display:

* route;
* origin;
* destination;
* CNG stations;
* current location when available.

After planning a new route:

fit the map to route bounds.

Do not constantly recenter while the user is inspecting it.

---

# 46. Offline map behaviour

Do not attempt to download Italy's OSM map tiles.

If map tiles happen to be cached by the browser:

great.

If not:

the route/stations should still be accessible through the list.

The application must remain useful without a visible basemap.

---

# 47. Station markers

Where practical show something like:

```text
€1.52
```

on a station marker.

Do not spend excessive implementation time creating complex custom markers.

A simple marker with a price label is enough.

---

# 48. Station list

This is as important as the map.

Sort by:

```text
positionAlongRouteKm
```

Example:

```text
218 km from start
2.4 km from route

ENI Padova
€1.52/kg · served

Open until 22:00
```

Then:

```text
437 km from start
1.8 km from route

Q8 Rimini
€1.61/kg · self
```

This makes the app useful while travelling even if map tiles are unavailable.

---

# 49. Selecting station

Tap marker or list card.

Open the same station details.

List selection and map selection should stay synchronized.

Do not depend on Leaflet's tiny default popup for all detail.

Use a proper station detail card.

---

# 50. Station detail card

Show approximately:

```text
ENI San Pelagio

Via ...

CNG

€1.529/kg · served
updated 4h ago
API

€1.549/kg · served
CSV snapshot 28 Aug

OPENING HOURS

Open now
06:00–22:00

DISTANCE

421 km from trip start
3.2 km from route

SOURCES

Location: CSV
Price: API
Hours: API

[ Open in Google Maps ]
```

---

# 51. Google Maps handoff

No Google API.

Generate:

```text
https://www.google.com/maps/dir/?api=1&destination=LAT,LON&travelmode=driving
```

Do not include origin.

Function:

```ts
buildGoogleMapsDirectionsUrl(...)
```

Tap:

```text
Open in Google Maps
```

---

# 52. Offline behavior

The app should work offline for the things that matter most.

Offline must support:

```text
open installed PWA

load saved route

load complete cached CNG station database

recalculate stations near route

show station list

show CSV prices

show cached opening hours/details

show source/freshness

use GPS if available
```

---

# 53. Offline limitations

It is okay that offline cannot:

```text
calculate an entirely new OSRM route

geocode a new place name

fetch new MIMIT prices

fetch new opening hours

load uncached map tiles

use online Google navigation
```

Show clear explanations instead of errors.

---

# 54. Offline route attempt

If user tries to calculate a completely new route offline:

show:

```text
A new route requires internet.

Your saved route and cached CNG stations are still available.
```

---

# 55. Settings

Persist simple settings:

```text
Vehicle range:
300 km

Reserve:
60 km

Station corridor:
10 km
```

Do not make settings complicated.

---

# 56. Range feature

Do not build a complicated fuel optimizer in v1.

However, because stations are sorted by:

```text
positionAlongRouteKm
```

make it easy to add later.

Optionally, if it is trivial after the core app works, add simple visual warnings when there is a gap larger than:

```text
vehicleRangeKm - reserveKm
```

For defaults:

```text
300 - 60 = 240 km
```

For example:

```text
⚠ 263 km gap between suitable CNG stations
```

BUT:

this is optional.

Do not delay core functionality for it.

---

# 57. PWA

Make it installable.

Manifest:

```text
name:
CNG Route Planner

short_name:
CNG Route

display:
standalone
```

Cache:

* application HTML;
* JavaScript;
* CSS;
* icons.

Application data belongs in IndexedDB.

Do not implement complicated service-worker caching logic for API responses.

---

# 58. Important installed-PWA behavior

The app must start from its cached shell even without internet.

At startup:

```text
1. open IndexedDB

2. load cached station snapshot

3. load latest saved route

4. render those immediately

5. only then try network updates
```

Do not make the loading screen depend on internet.

---

# 59. Network status

Show:

```text
Online
```

or:

```text
Offline
```

Use browser network events.

Remember:

`navigator.onLine` does not guarantee a provider works.

Provider request failures must still be handled individually.

---

# 60. Simple error handling

Use human-readable messages.

Examples:

Location:

```text
Location access is unavailable.
Search for your starting point instead.
```

Geocoding:

```text
Place search is unavailable.
You can enter latitude,longitude instead.
```

Routing:

```text
A new route could not be calculated.
Your saved route is still available.
```

Live MIMIT:

```text
Live details are unavailable.
Showing cached official station data.
```

Data update:

```text
Could not update station data.
Using the previous downloaded version.
```

---

# 61. CORS testing

Very early in implementation:

test real browser access to:

```text
MIMIT CSV
MIMIT live API
OSRM
Nominatim
```

Do not assume all of them support browser CORS.

For each source:

```text
direct browser request works?
    |
   yes
    |
use direct

direct browser request blocked?
    |
   yes
    |
use tiny proxy only for that source
```

---

# 62. Tiny proxy rules

ONLY create this if necessary.

If created, use:

```text
server/proxy.ts
```

Keep endpoints minimal.

For example:

```text
GET /proxy/mimit/stations
GET /proxy/mimit/prices

POST /proxy/mimit/route

GET /proxy/mimit/station/:id
```

And only proxy OSRM/Nominatim if those also require it.

No database.

No cache tables.

No migrations.

No repositories.

No cron jobs.

No complicated architecture.

It is simply:

```ts
receive request
→
fetch provider
→
return response
```

with basic validation and timeouts.

---

# 63. Proxy runtime

If proxy is needed:

`npm run dev` should start:

```text
Vite
+
proxy
```

together.

For production/local-trip use, provide one simple command such as:

```bash
npm start
```

that serves the built PWA and proxy from one Node process if practical.

If no proxy is needed:

keep the app completely static/client-side.

Do not create a server just to satisfy this section.

---

# 64. Deployment philosophy

Do not spend time building deployment infrastructure.

This application can simply run locally on my phone.

The README should explain the easiest method.

Expected development:

```bash
npm install
npm run dev
```

Expected use before/during trip may be:

```bash
npm run build
npm start
```

if a small local server is required.

If a fully static PWA works:

even better.

Do not require me to rent a server.

---

# 65. Testing priorities

Do not build an enterprise-size test suite.

Focus tests on the logic where bugs could actually ruin the trip.

Required tests:

```text
CNG classification

CSV parsing

price parsing

route station filtering

route station ordering

opening hour normalization

source merge

Google Maps URL
```

Use Vitest.

---

# 66. CSV tests

Test:

```text
pipe delimiter

BOM

metadata before header

valid CNG station

invalid station ID

invalid coordinate

self-service price

served price

bad price row
```

---

# 67. CNG tests

At minimum:

```text
Metano           true
METANO           true
CNG              true
GNC              true
L-GNC            true

GNL              false
LNG              false
Metano Liquido   false
GPL              false
Benzina          false
```

---

# 68. Geometry tests

Create a simple known route fixture.

Test:

```text
station on route

station inside 10 km

station outside 10 km

station order along route
```

Ensure values are in kilometres.

---

# 69. Opening hours tests

Test:

```text
H24

explicitly closed

unknown/not communicated

continuous opening

morning + afternoon

malformed value
```

Most importantly:

```text
missing
→ unknown

NOT
→ closed
```

---

# 70. Manual real-world verification

After implementation and unit tests:

perform real smoke testing if internet is available.

At minimum:

1. download current MIMIT data;
2. verify a reasonable number of CNG stations parses;
3. search Gallipoli;
4. create a route roughly Ljubljana → Gallipoli;
5. verify stations appear along route;
6. select one;
7. try live details;
8. verify Google Maps URL.

Do not make assertions about exact fuel prices because they change.

---

# 71. Mobile verification

The application must be usable at approximately phone width.

Check:

* no horizontal scrolling;
* form inputs large enough;
* buttons easy to tap;
* map does not push controls offscreen;
* station details readable;
* station list readable.

Prioritize portrait Android use.

---

# 72. Performance

Do not optimize prematurely.

A few thousand stations in IndexedDB is fine.

A few thousand Turf calculations after route planning is acceptable.

If needed, first use route bounding box to reduce candidates.

Do NOT build a spatial index/database unless actual measured performance proves necessary.

---

# 73. Data source disclaimer

Add a small About/Data section:

```text
Station and price data comes from Italian MIMIT fuel datasets and the Osservaprezzi service.

Opening hours are reported by station operators and may be missing or inaccurate.

Station coordinates may occasionally be inaccurate.

Check critical station information before relying on a station with very little remaining fuel.
```

Include OpenStreetMap attribution on the map.

Do not present this as an official government application.

---

# 74. Data freshness

Make old data obvious but still usable.

Examples:

```text
Station data:
updated today 08:15
```

```text
CSV price:
28 Aug
```

```text
Live price:
3h ago
```

```text
Opening hours:
cached yesterday
```

Do not hide useful information just because it is old.

---

# 75. README

Write a short useful README.

Do not turn it into enterprise documentation.

Include:

## Run

```bash
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

## What it does

Brief explanation.

## Data sources

MIMIT CSV/live API, OSRM, Nominatim, OSM.

## Offline

Explain exactly what works.

## Phone use

Explain how to install/add the PWA and how to start it locally if a proxy is required.

## Important limitation

Explain:

```text
MIMIT live API is optional.

Downloaded MIMIT CSV data is the reliable offline baseline.
```

---

# 76. Gitignore

Ignore:

```text
node_modules/
dist/
.env
coverage/
*.log
```

Do NOT ignore:

```text
package-lock.json
.env.example
```

---

# 77. Root npm scripts

At minimum:

```text
npm run dev
npm run build
npm run preview
npm test
npm run typecheck
npm start
```

If no proxy/server is required:

`npm start` may simply serve the production build using a tiny static server.

Keep commands easy to remember.

---

# 78. Code quality

Use strict TypeScript.

Avoid `any`.

Do not create abstractions for their own sake.

Prefer:

```ts
parseMimitCsv(...)
mergeStationData(...)
findStationsAlongRoute(...)
normalizeOpeningHours(...)
```

over abstract frameworks.

Keep code understandable enough that I can inspect/fix it from my phone.

---

# 79. Comments

Only comment unusual external-source behaviour or non-obvious decisions.

For example:

```ts
// MIMIT live API is optional; CSV remains the offline baseline.
```

Do not comment obvious code.

---

# 80. Implementation order

Follow this order.

## Phase 1

Create basic React + Vite + TypeScript app.

Get:

```bash
npm run dev
```

working on:

```text
0.0.0.0:5173
```

## Phase 2

Implement IndexedDB.

Implement MIMIT CSV download and parsing.

Store complete CNG station snapshot.

Add:

```text
Update data
```

Verify real data.

## Phase 3

Implement OSRM route calculation.

Save route.

## Phase 4

Implement Turf station filtering and ordering.

At this point:

```text
route
+
cached CNG stations
```

must already work.

This is the core application.

## Phase 5

Implement map and station list.

## Phase 6

Implement Nominatim search and GPS origin.

## Phase 7

Implement live MIMIT enrichment and station details.

Cache details in IndexedDB.

## Phase 8

Implement PWA/offline app shell.

Verify saved route + cached stations survive offline reload.

## Phase 9

Add tests and polish mobile UI.

## Phase 10

Run real Ljubljana → Gallipoli smoke test.

---

# 81. Definition of done

Do not stop at scaffolding.

The project is done when:

```text
npm install
```

works.

```text
npm run dev
```

works.

The app opens on the phone.

Current GPS can be used.

Origin/destination can be selected.

A route can be calculated online.

The route appears on the map.

MIMIT CSV CNG stations are downloaded.

CNG stations near the route are found locally.

Stations are ordered correctly along the route.

Prices are shown.

Self/served is shown.

Station details open.

Opening hours appear when available.

Missing hours show unknown.

Source information is visible.

Freshness is visible.

Open in Google Maps works.

The full station snapshot is stored in IndexedDB.

The route is stored in IndexedDB.

The app reloads offline.

The saved route remains visible/usable.

Stations can still be calculated along the cached route while offline.

Cached details remain visible.

Tests for important data logic pass.

Production build succeeds.

````

No important TODOs should remain.

---

# 82. Verification commands

Before finishing run:

```bash
npm run typecheck
npm test
npm run build
````

Fix failures.

Also run the application and perform real smoke tests if network is available.

Do not merely generate files without running them.

---

# 83. Do not waste time on environment-specific problems

This is running on a phone/proot Linux environment.

If some optional desktop-only test tooling causes trouble:

do not spend hours forcing it to work.

The priorities are:

```text
actual app works
>
unit tests work
>
build works
>
fancy tooling
```

Do not add Docker or Playwright merely because they are common.

---

# 84. Critical fallback behavior

Always preserve these rules:

```text
MIMIT live API fails
→ use CSV

CSV refresh fails
→ use previous IndexedDB snapshot

no internet
→ use saved route + cached stations

opening hours missing
→ unknown, not closed

Nominatim unavailable
→ allow coordinates

map tiles unavailable
→ station list still works

Google Maps unavailable
→ station coordinates/details remain visible
```

---

# 85. Final Codex report

When finished, do not dump all source code.

Give me a short report:

```text
Implemented:
- CNG station CSV sync
- route planning
- map
- station filtering
- live details
- offline caching
- PWA
...

Verification:
- typecheck: PASS
- tests: PASS
- build: PASS
- live MIMIT CSV: PASS
- Ljubljana → Gallipoli route: PASS
- offline reload: PASS

Architecture:
- frontend-only
```

OR if CORS required the proxy:

```text
Architecture:
- React PWA
- tiny local proxy only for MIMIT
- no server database
```

Then tell me exactly:

```bash
npm run dev
```

and what URL to open.

If something genuinely remains broken:

state precisely what it is.

If it has an obvious solution:

fix it before stopping.

---

# 86. Final expectation

This should feel like a small application I built specifically for a road trip.

Not a startup.

Not an enterprise system.

Not a reusable platform.

The core user experience is:

```text
Open app

Use current location

Enter Gallipoli

Plan route

See CNG stations ahead

Tap one

See:

€1.52/kg
self

Open 06:00–22:00

218 km ahead
2.4 km from route

Price:
API · 4h ago

Station:
CSV

[ Open in Google Maps ]
```

If internet disappears:

```text
Offline

Using station data from yesterday

Saved route still shown

CNG stations still listed
```

That is the product.

Keep it simple, reliable and easy to operate from my Android phone.

Start by inspecting the current directory and then implement it completely.

