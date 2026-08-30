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
- Search this area button when map has moved from the last searched bounds;
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
- online + today's data -> normal/fresh indicator;
- online + old data -> warning indicator;
- offline -> offline indicator;
- offline + old data -> offline indication with stale-data warning.

Tapping the indicator should explain the current state.

## Nearby mode

Default mode.

If GPS permission/location is available:
- center map on current location;
- initial camera should show approximately 30 km around the point.

If a manually selected place is used:
- center there using approximately the same initial scale.

There is NO user-configurable search radius.

The visible map viewport defines the search area.

Stations shown after a search are those relevant to the visible bounds.

After the user pans or zooms sufficiently:
- do not continuously rerun the search;
- show "Search this area";
- tapping it makes the current viewport the new searched area.

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

Selecting it:
- moves camera there;
- uses approximately the normal 30 km initial scale;
- shows stations in that area.

The user can then pan/zoom and use Search this area.

## Route mode

Route mode supports arbitrary A -> B.

A can be:
- current location;
- searched place;
- location chosen on map.

B can be:
- searched place;
- location chosen on map.

OSRM will be used for routing.

After route calculation:
- fit the whole route in the viewport;
- keep comfortable visual margin around it.

Stations should be shown near the route, not merely anywhere in the route's huge rectangular bounding box.

There is NO user-configurable route corridor.

The useful route corridor should be derived automatically from map scale and bounded to sensible values.

After zooming/panning in route mode:
- Search this area appears;
- tapping it recalculates useful visible stations for that part/scale of the route.

Google Maps still handles actual driving navigation.

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

Future offline-map work, intentionally not part of Phase 4:
- bundle the map style with the APK;
- support a downloadable and updateable MapLibre offline region for Italy;
- choose a sensible maximum offline zoom to control download size.

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
