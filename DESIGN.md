# CNG Italy - Product Design

## Purpose

A native Android utility for spontaneously finding CNG/metano stations while travelling in Italy.

It is not primarily a trip planner and not a navigation app.

Typical use:
- CNG is getting low.
- Open app.
- Immediately see CNG stations around the current location.
- Inspect a station.
- Open it in Google Maps for navigation.

The app must remain useful with previously downloaded station data when offline.

## Main UI

The application is primarily one almost-full-screen map.

Avoid traditional multi-screen navigation unless genuinely necessary.

### Map overlays

Eventually the main map contains:

- hamburger/menu button in top-left with the compact data/status indicator underneath;
- place search UI;
- MapLibre compass in top-right;
- current-location/recenter button;
- CNG station markers/clusters;
- route when route mode is active.

Do not permanently consume map space with settings panels or lists.

## Settings drawer

Hamburger opens a modal slide-in drawer over the map.

It contains things such as:

- station data date/freshness;
- connection state;
- Refresh station data action;
- data/map sources;
- About/version.

Data refresh belongs here, not as a permanent main-screen button.

## Data status

Main screen shows only one compact status icon.

Internal state distinguishes:

- network connection;
- station-data freshness.

Examples:
- usable local snapshot + last successful refresh less than 24 hours ago -> normal/fresh indicator;
- usable local snapshot + last successful refresh 24 hours ago or more -> warning/stale indicator;
- no usable local snapshot -> no-data warning indicator;
- offline -> offline indicator;
- offline + old data -> offline indication with stale-data warning.

The MIMIT source dataset date is informational and does not determine local-data freshness.

Tapping the indicator should explain the current state.

## Nearby mode

Default mode.

Normal mode loads all locally stored Italian CNG stations into one clustered
MapLibre GeoJSON source. The current dataset is only about 1,600 stations, so
MapLibre handles viewport rendering and clustering without Room queries or
GeoJSON rebuilds on pan and zoom. There is no Search this area control.

If GPS permission/location is available, the initial camera centers on the
current location at zoom 10.5. A manually selected place uses the same zoom.

## Map movement

The map must remain freely pannable and zoomable.

Never continuously force the camera back to GPS.

The recenter/current-location button explicitly returns to the current location.

## Station clustering

Nearby stations must cluster to avoid marker overload.

At low zoom:
- nearby stations combine into cluster markers;
- cluster marker displays station count.

Tapping a cluster zooms into it.

At sufficiently high zoom:
- individual station markers appear;
- individual markers should eventually display CNG price when available.

## Station details

Tapping an individual station opens a bottom sheet over the map.

Eventually it should show:

- station name;
- address;
- CNG price;
- self / served;
- price freshness;
- opening hours when available;
- open/closed/unknown status;
- source/freshness information;
- Open in Google Maps action.

Missing opening hours mean UNKNOWN, never CLOSED.

Actual navigation is delegated to Google Maps in v1.

## Search place

User can search for another location.

Place search uses Photon/OpenStreetMap typeahead. Opening any search sheet
automatically focuses its field and opens the keyboard. Queries with fewer than
two normalized characters do not search; longer queries debounce for about 375
ms. Query changes cancel pending work and clear old suggestions immediately, and
stale responses cannot replace results for the current query. The keyboard Search
action runs immediately and hides the keyboard while leaving suggestions visible.
Exact name matches are promoted ahead of prefix, contains, and fuzzy matches
while preserving Photon order within each group.

Use of the public service remains conservative:
- no more than one HTTP request may start per second;
- requests identify the app with a custom User-Agent;
- repeated normalized queries are cached for the app session;
- search results show OpenStreetMap contributor attribution.

Selecting it:
- closes the search sheet and keyboard;
- moves the camera there at zoom 10.5;
- creates or replaces one temporary searched-place marker;
- leaves the normal all-stations source and any active route unchanged.

Selecting a suggestion does not open another sheet. While the one temporary
searched-place marker exists, the map shows a compact floating Navigate action.

## Route mode

Route mode supports an ordered From -> zero or more Stops -> To route (up to
eight intermediate stops). Stops are visited exactly in their displayed order;
the app never optimizes or reorders them.

The origin can be the current location or a Photon search result. The
destination and intermediate stops are selected from Photon search results.

OSRM will be used for routing.

After route calculation:
- fit the whole route in the viewport;
- keep comfortable visual margin around it.

Stations should be shown near the route, not merely anywhere in the route's huge rectangular bounding box.

The main drawer's Route section offers a session-only station corridor setting.
Auto uses 2% of route length with a 3 km minimum and 10 km maximum. Fixed choices
are 3, 5, 10, or 20 km. Changing the setting reruns only the local Room candidate
query and geometry filter; it never requests a new route.

While route mode is active, the station source contains only corridor-matching
stations. Panning and zooming do not replace them.

Google Maps still handles actual driving navigation.

### Phase 7 route behavior

The common route flow is deliberately short:

Search -> choose destination -> Navigate -> choose From -> route immediately
calculated.

There is no dedicated Route control and no confirmation or Find route step. The
Navigate action opens a minimal task-specific sheet containing only read-only
destination context, an autofocused global Photon From typeahead, Use my location,
suggestions, and attribution. Selecting a From suggestion or a usable current
location closes the sheet and immediately requests OSRM. Failure retains the
current active route and the temporary destination marker so the attempt can be
retried.

When a route is active, a top-left Add Stop control appears below Search. Its
minimal sheet contains only an autofocused global Photon typeahead, suggestions,
and attribution. Selecting a result inserts it immediately before the final
destination, preserves all existing stop order, closes the sheet, and immediately
recalculates the route.

Temporary sheets contain only controls required for their immediate task and may
expand to use most of the screen with the keyboard and suggestions. Advanced route
management is not placed in these sheets.

From can use the current location through the existing foreground permission and
location infrastructure. It displays as My location, uses the current coordinates,
and does not add an A marker because the MapLibre location puck represents it.

The app sends all selected coordinates to the OSRM Route service in displayed
order, requests one full driving-route GeoJSON geometry, draws it
below the existing station layers, fits every route point with comfortable map
padding, and shows a compact distance/duration summary. It does not provide
turn-by-turn directions or navigation.

Every quick route request hides the keyboard, closes its sheet immediately, and
shows a compact calculating overlay on the map. Failure keeps the previous active
route and reports a concise Snackbar.

Active route waypoints use source/layer rendering above station layers: A for a
non-GPS origin, numbered intermediate stops, and B for destination. Tapping one
animates to zoom 10.5 without changing route or nearby/search state.

Selecting a standalone Photon result creates one temporary, session-only place
marker and floating Navigate action. A successful route containing that point
removes the temporary marker because route waypoint markers become authoritative.

The main hamburger drawer contains a Route section while a route is active. It
shows From, every numbered intermediate stop, and To. Intermediate stops can be
reordered with up/down controls or removed. These edits remain a draft until Apply
changes, which closes the drawer and requests one recalculation; Done closes the
drawer without OSRM when order is unchanged. From stays first and To stays last.
The corridor selector also lives here and applies immediately without OSRM.

Route stations are selected by actual geometric distance to the OSRM route, not
merely by inclusion in the route bounding box. Room first retrieves candidates
from an expanded geometry bounding box; each candidate is then filtered by its
minimum straight-line distance to any route polyline segment.

The automatic geometric corridor is:

`(route distance * 2%).coerceIn(3 km, 10 km)`

This is a maximum straight-line distance from route geometry, not a driving
detour distance. It can be tuned after physical testing.

Normal place search remains available while a route is active and does not clear
or recalculate it. The GPS locator is always a camera-only action at zoom 10.5;
it retains route geometry, endpoints, waypoints, corridor setting, filtered
stations, and summary. Clearing the route retains the camera and restores all
locally stored stations to the normal clustered source.


## Offline behavior

Station data is downloaded locally.

If offline:
- clearly show offline status;
- show the exact date/freshness of local station data;
- nearby station discovery from local data must still work;
- cached station details may still be shown;
- GPS should still work where Android provides location;
- new online geocoding/routing/live data may be unavailable.

Never erase valid local data because a refresh fails.

Phase 8A uses two deliberately separate styles. A validated-online launch loads
the original remote OpenFreeMap Liberty style unchanged. An offline launch loads
a tiny bundled style containing only a neutral background and a local glyph URL,
with no basemap sources or sprite. Both styles receive the same dynamic station,
route, waypoint, and location layers after loading.

The activity observes the existing validated-connectivity state for its active
session. Validation changes replace offline and online styles without restarting
the app, resetting the camera, recalculating a route, or performing another
automatic GPS recenter.

The offline style uses bundled `Noto Sans Regular` PBF assets, so local Room
station prices, cluster counts, route waypoint text, clustering, station details,
current-session route geometry, and GPS remain usable without network access.
The temporary searched-place marker is represented by its circle rather than a
decorative text glyph.

Offline mode intentionally has no roads, landcover, places, POIs, or terrain.
Online Liberty continues using OpenFreeMap's own sources, sprite, and glyphs
without modification.

Photon place search, OSRM routing, MIMIT refresh/live enrichment, and uncached
OpenFreeMap basemap resources remain online-only and fail without deleting valid
local data or an active route.

A downloadable full offline basemap is future Phase 8B work, pending selection
of a suitable tile/package source and compatible usage terms. Phase 8A does not
download or bundle Italy vector tiles.

## Architecture principles

Native Android only.

Use:
- Kotlin;
- Jetpack Compose + Material 3;
- MapLibre Native Android for the map;
- Room later for local station data.

MapLibre Native should be hosted inside Compose rather than depending on an unstable Compose-specific map abstraction.

Keep the architecture straightforward.

Do not add layers/classes merely for architectural purity.

## Implementation philosophy

Build vertically in small verified stages.

Never fill the UI with buttons for future features that do not work yet.

Each stage should leave an application that actually runs and whose visible controls work.
