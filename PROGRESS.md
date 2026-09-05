# Implementation Progress

## Current state

The native Android app currently uses one almost-full-screen MapLibre map with
all locally stored Italian CNG stations in normal mode. There is no dedicated
route button and no Search this area action.

Current navigation flow is destination-first: Photon typeahead selection places
one temporary marker and exposes Navigate; choosing a Photon origin or My
location calculates immediately. An active route exposes quick Add Stop, while
the main drawer owns full ordered route editing and the Auto/3/5/10/20 km
corridor setting. The GPS locator moves only the camera and preserves any active
route. Route mode projects only stations within the selected geometric corridor.

Phase 8A uses the original remote Liberty style online and a separate minimal
bundled style with app-owned marker glyphs offline. The Phase 8B application
foundation loads the full production Italy PMTiles archive locally installed at
`filesDir/maps/italy.pmtiles` through a simple Protomaps-compatible style. The
full archive and offline behavior are physically verified on a Samsung Galaxy
S23; public download and distribution work is deferred for the personal/travel
build.

Branch:
android-native

## Completed

- [x] Archived previous PWA in Git history / pwa-archive branch
- [x] Created fresh Android project
- [x] Kotlin
- [x] Jetpack Compose
- [x] Material 3
- [x] Minimum SDK 26
- [x] Clean project builds
- [x] Baseline pushed to android-native

## Implementation phases

- [x] 1. Full-screen MapLibre map + working settings drawer
- [x] 2. GPS/current-location support + recenter action
- [x] 3. Local Room database + MIMIT station-data refresh + data-status UI
- [x] 4. Show local stations for map viewport + Search this area + clustering
- [x] 5. Station bottom sheet + live details + Google Maps navigation
- [x] 6. Search for another place
- [x] 7. Ordered route mode + stations along route (technically complete)
- [x] 8A. Offline-safe style and app-owned overlays (physically verified)
- [x] 8B. Full Italy offline basemap for personal build (production archive + local installation physically verified; public distribution deferred)
- [x] 8C. Remaining polish/testing on real phone (physically verified; personal trip build ready)

## Implementation history

Phase 1 and Phase 2 are complete and manually verified on a physical device.

Phase 3 / Phase 3A is complete and manually verified on a physical device.

Phase 4 is complete and physically verified.

Phase 5A local station selection/details is physically verified.

Phase 5 is complete and physically verified.

Phase 6 Photon place search is complete and physically verified.

Phase 7 routing and the fast search/navigation UX simplification are implemented
and build-verified. Phase 7 remains pending physical-device verification.

Implemented:
- MapLibre Native Android 13.3.1 using the explicit OpenGL `android-sdk-opengl` artifact
- full-screen native MapView hosted in Compose with AndroidView
- OpenFreeMap Liberty style and temporary startup camera over Italy
- MapView lifecycle and saved-state forwarding in MainActivity
- visible black circular top-left menu button opening a dismissible Material 3 modal drawer
- drawer gestures enabled only while open: edge swipes cannot open it, while swipe-left can close it
- drawer close button plus normal scrim-tap dismissal
- drawer contains only "CNG Italy" and "Map ready"
- built-in MapLibre logo and attribution/info control retained in the bottom-left
- built-in MapLibre compass retained and positioned below the status bar using system insets
- INTERNET permission

Phase 2 implemented:
- foreground coarse/fine location permissions only
- no automatic permission dialog on startup
- MapLibre built-in LocationComponent and default location engine
- normal MapLibre current-location puck after style load and permission grant
- one-time startup centering at the accepted zoom 10.5 when permission already exists
- bottom-right current-location button with navigation-bar inset handling
- button requests permission when needed and performs a one-time zoom 10.5 recenter when location is available
- denial and waiting feedback through a Material Snackbar
- camera remains in `CameraMode.NONE`, so the map never continuously follows location

Phase 3A implemented:
- Room 2.8.4 database with indexed stations, CNG prices, dataset metadata, and station-price foreign key
- direct OkHttp downloads of both MIMIT CSV exports with bounded timeouts
- pipe/BOM/metadata-aware parsing, Rome-zone communication timestamps, validation, and CNG-only filtering
- complete in-memory station/price merge retaining only coordinate-valid stations with usable CNG prices
- one Room transaction replaces prices, stations, and metadata only after a usable snapshot is ready
- failed download, parse, validation, or database replacement preserves the previous snapshot
- separate last-successful refresh time and official MIMIT dataset dates; local freshness uses a rolling 24-hour window independent of source dates
- dynamically tracked validated-internet connectivity
- compact fresh/stale/offline/no-data map status control below the top-left menu button
- drawer DATA section with refresh time, MIMIT date, station count, connection, and refresh progress/action
- one non-blocking first-run refresh only when no snapshot exists and validated internet is available
- no station markers, viewport queries, clustering, or Search this area

Phase 4 implemented:
- Room viewport query returns stations with all stored CNG prices and supports antimeridian-crossing bounds
- compact map model selects the lowest stored CNG price and formats it with exactly three decimals
- initial Italy/GPS viewport and GPS recenter perform one local station search
- user camera gestures only mark the viewport dirty after camera idle; no database work occurs during movement
- compact top-center Search this area action queries the current visible bounds on demand
- one clustered GeoJSON source installed with the loaded style, with cluster radius 50 and max cluster zoom 14
- cluster circle/count and individual circle/price style layers; no deprecated marker annotations
- cluster taps use MapLibre expansion zoom and do not trigger a new Room query or dirty state
- successful MIMIT refresh reruns the last searched bounds, or searches the current viewport after first-run data arrives
- station querying works from Room without contacting MIMIT
- no station details, live API, place search, route UI, Google Maps action, or offline-map implementation

Phase 5A implemented:
- cluster-first map tap handling preserves cluster expansion; individual station circle or price-label taps select only by GeoJSON `stationId`
- one Room relation query loads a selected station with all stored CNG prices
- Compose-safe station details/price models preserve local station metadata, sort prices ascending with self before served ties, format three decimals per kg, and format communication timestamps in Europe/Rome
- ViewModel-owned selected-station and loading state; dismissing the Material 3 modal bottom sheet clears selection
- local details sheet shows clean address, available brand/manager/type, every separate self/served CNG price, communication time when stored, MIMIT source dataset date, and a stale-data note only under the existing 24-hour rule
- Open in Google Maps targets exact station coordinates through the Google Maps app and falls back to an ordinary Google Maps web URI without requiring an API key
- no place search, route mode, or offline-map behavior

Phase 5B implemented:
- small OkHttp client calls `ospzApi/registry/servicearea/{stationId}` only when validated internet is available, with short timeouts and defensive optional-field JSON parsing
- Room-backed Phase 5A details remain authoritative and appear first; live state is cancellable per selection and routine HTTP, timeout, offline, or parsing failures quietly preserve the local sheet
- live fuels reuse the CSV CNG classifier, reject invalid prices and LNG/GNL/GPL, preserve self/served rows, and replace displayed local price rows only when usable live CNG prices exist
- optional live contact details and services enrich the existing sheet without adding secondary actions
- optional weekly opening hours distinguish 24-hour, explicitly closed, communicated ranges, and unknown/malformed/not-communicated data; current OPEN/CLOSED/UNKNOWN status uses Europe/Rome
- no persistent live cache or Room migration; no live response can overwrite the reliable local snapshot

## Verification

Baseline:
- Gradle test: PASS
- assembleDebug: PASS
- runs on device/emulator: PASS

Phase 1 (2026-08-30):
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- device/emulator runtime verification: not run in this phase

Phase 1 drawer/map usability fix (2026-08-30):
- `ModalNavigationDrawer` gestures depend on `drawerState.isOpen`, preventing closed edge-swipe opening while preserving open swipe-to-close
- hamburger opening, drawer close button, and normal scrim-tap dismissal implemented
- built-in MapLibre logo and attribution/info control enabled; the info control exposes full map/data credits
- built-in compass and rotation gestures retained; compass margins use the actual status-bar inset
- removed the temporary custom attribution text overlay and restored MapLibre's normal built-in attribution presentation
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device verification: PASS
- verified map pan, pinch zoom, rotation, drawer gesture/button/X/scrim behavior, compass visibility/north reset, and MapLibre attribution

Phase 2 (2026-08-30):
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device location permission, puck, initial center, recenter, denial, and free-camera behavior: PASS (user verified)
- physical-device verification was completed before the later UI corrections below

Phase 2 UI corrections (2026-08-30):
- accepted current-location button and automatic startup zoom is 10.5
- made the menu control a 48 dp black circle with a white icon, aligned using the same status-bar inset and 8 dp edge margin as the compass
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS

Phase 3A (2026-08-30):
- parser unit tests: 13 PASS
- total unit tests: 14 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical/manual verification: PASS
- verified real and manual MIMIT refresh, sensible count/date, connectivity changes, offline failure preservation, no-data offline startup, and prior map/GPS/drawer behavior
- Phase 3 complete

Phase 4 (2026-08-30):
- display-price selection tests added: 5 PASS
- GeoJSON conversion test added: verifies longitude/latitude ordering and all marker properties used by the style
- total unit tests: 20 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device viewport query and station/cluster/price rendering: PASS
- physical-device cluster expansion and refresh-requery verification: PASS
- Phase 4 complete

MapLibre renderer dependency correction (2026-08-30):
- Phase 1 accidentally used `org.maplibre.gl:android-sdk:13.3.1`, the default Vulkan renderer in MapLibre Android 13.x, despite progress documentation claiming OpenGL
- switched the single MapLibre dependency to the explicit OpenGL artifact `org.maplibre.gl:android-sdk-opengl:13.3.1`; the version remains 13.3.1
- `./gradlew.bat clean`: PASS
- `./gradlew.bat test`: PASS (20 tests)
- `./gradlew.bat assembleDebug`: PASS

Phase 4 station rendering resolution (2026-08-31):
- physical-device viewport searches returned hundreds or thousands of valid stations but initially rendered no station or cluster layers
- confirmed root cause: dynamically added SymbolLayers inherited MapLibre's default `Open Sans Regular` / `Arial Unicode MS Regular` font stack, which OpenFreeMap Liberty's glyph endpoint does not provide; the failed glyph request prevented the source tile from completing layout
- station price and cluster count SymbolLayers now explicitly use only `Noto Sans Regular`, matching the Liberty style; physical-device station circles, clusters, and price/count labels render successfully
- restored the intended lifecycle after diagnosis: one source and four layers are installed per loaded style, and station results update the current style's source with `setGeoJson`; existing results are pushed immediately after style installation so either async ordering works
- temporary source queries, rendered-feature queries, layer dumps, source recreation, and delayed diagnostic posts were removed
- explicit OpenGL remains in use; renderer selection, source-update timing, source recreation, stale references, and filter alternatives were investigations rather than the confirmed rendering cause
- `./gradlew.bat test`: PASS (20 tests)
- `./gradlew.bat assembleDebug`: PASS
- Phase 4 physical-device verification: PASS

Data-status freshness correction (2026-08-31):
- main status freshness now depends only on a usable snapshot and `lastSuccessfulRefreshEpochMillis`: under 24 hours is fresh, while 24 hours or more is stale
- MIMIT station/price dataset dates remain visible source metadata but do not control the status icon
- status explanations concisely include relative refresh age and the MIMIT price snapshot date when local data exists
- failed refresh behavior remains unchanged: only a successful snapshot replacement writes `lastSuccessfulRefreshEpochMillis`
- added 6 unit tests covering 5-minute and 23h59m freshness, the 24-hour boundary, midnight crossing, old MIMIT source dates, and unchanged last-success timestamp after failure
- `./gradlew.bat test`: PASS (26 tests)
- `./gradlew.bat assembleDebug`: PASS

Phase 5A local station details (2026-08-31):
- added individual station selection, local Room details lookup, and a dismissible Material 3 bottom sheet over the unchanged map camera
- details show all locally stored CNG prices separately, local station/address metadata, Europe/Rome communication timestamps, dataset-level MIMIT source date, and Google Maps navigation with browser fallback
- added 5 focused station-details mapping/formatting tests; total unit tests: 31 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device verification: PASS

Map UX corrections (2026-08-31):
- station details translate only the official MIMIT types for display: `Stradale` to `Roadside` and `Autostradale` to `Motorway`; matching ignores case/whitespace, unknown values use their trimmed source text, and Room/parser values remain unchanged
- Android Back closes an open drawer without leaving the app; the handler is disabled while the station sheet is present so normal sheet dismissal keeps priority, while closed-drawer Back remains normal Android behavior
- moved the single data-status control from top-right to the same top-left alignment directly below the hamburger; MapLibre compass remains alone at top-right
- added 4 focused station-type display tests; total unit tests: 35 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- no new phase was started or completed

Phase 5B live MIMIT enrichment (2026-08-31):
- implemented best-effort live station enrichment from the undocumented MIMIT `ospzApi` endpoint while retaining Room as the authoritative offline/failure fallback
- live data can replace price rows only with valid CNG prices and can add confident Rome-time opening status, weekly hours, phone, website, email, and services
- missing, malformed, timed-out, non-2xx, offline, or incomplete live responses leave the local sheet usable and do not produce routine error Snackbars
- selecting another station or dismissing the sheet cancels and clears the prior live state
- removed temporary viewport bounds, Room count, and mapped-count `CngMap` diagnostics; retained only a useful missing-current-source failure log
- added 14 deterministic unit tests for live CNG filtering, self/served preservation, opening-state interpretation, and partial/optional JSON parsing
- total unit tests: 49 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- Phase 5B physical-device verification: PASS
- full Phase 5 complete

Phase 5B opening-hours presentation cleanup (2026-08-31):
- UNKNOWN current opening status is now omitted rather than displaying an unavailable/unknown message; confident OPEN/CLOSED status and transition details remain unchanged
- the weekly OPENING HOURS section is hidden when every entry is missing, not communicated, or malformed; when any weekday is meaningful, the full seven-day context remains visible with Unknown for unavailable days
- parsing, Europe/Rome calculations, and OPEN/CLOSED/UNKNOWN interpretation were unchanged
- added 7 focused presentation tests; total unit tests: 56 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS

Phase 6 place search (2026-08-31):
- compact top-left place-search control opens a Material 3 sheet without moving the map
- searches occur only on explicit keyboard/button submission; no autocomplete requests are made
- Photon requests are restricted to Italy, request up to 10 typo-tolerant candidates, identify the app, and include JSON/device-language headers; local ranking returns the best five
- the client enforces at least one second between HTTP request starts and caches 32 normalized successful queries for the app session
- selecting a result dismisses the sheet, animates to zoom 10.5 without enabling tracking, and automatically searches the resulting Room viewport
- offline and request failures remain inline in the sheet; existing local map/station behavior remains usable
- Phase 5 station-selection cancellation now propagates `CancellationException`
- added focused unit tests for defensive GeoJSON parsing, coordinate ordering, label deduplication, match ranking, and normalized cache keys
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- Phase 6 physical-device verification: pending

Phase 6 search UX and typo-tolerance correction (2026-08-31):
- Photon/OpenStreetMap is the single geocoder, requesting up to 10 Italy-only typo-tolerant candidates before locally returning the best five
- exact-name results rank ahead of prefix, contains, and fuzzy results while Photon order is retained within each group
- Photon GeoJSON parsing reads longitude/latitude in documented order, builds deduplicated display labels, and skips malformed features
- Android Back hides the visible keyboard and clears field focus before a later Back dismisses the search sheet
- keyboard and button submissions hide the keyboard before searching; dismissal and result selection clear query/results/error/loading UI state while retaining the session network cache
- explicit submission, the identifying User-Agent, normalized 32-query session cache, and conservative one-request-per-second limiter remain in place
- replaced the earlier geocoder tests with 11 focused Photon parsing, coordinate-order, display-label, ranking, typo-retention, and normalization tests; total unit tests: 67 PASS
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- Phase 6 physical-device verification remains pending

Phase 6 place-search Back correction (2026-08-31):
- the place-search sheet disables Material's direct Back dismissal; while the IME is visible Android handles Back to hide it, and the sheet BackHandler becomes enabled only after the IME is hidden
- scrim-tap and swipe-down dismissal remain enabled and continue clearing search UI state
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device verification: PASS
- Phase 6 complete

Phase 7A core route mode (2026-08-31):
- compact route control below place search opens a Material 3 From/To sheet
- both endpoints require selection from explicitly submitted global Photon results; standalone search remains Italy-only and Photon cache keys include normalized query plus country scope
- cancellable OSRM driving request uses longitude,latitude endpoint order, full GeoJSON overview, no alternatives, steps, or turn-by-turn instructions, and enforces one second between actual request starts
- ViewModel owns selected endpoint drafts, route loading/error state, the active route, and route-station replacement without destroying an older active route when a new request fails
- one route GeoJSON source with a white casing and dark main line is installed before the existing station layer for every loaded MapLibre style
- successful routes fit all geometry points with map padding and retain CameraMode.NONE
- automatic straight-line corridor is 2% of route distance, clamped to 3-10 km
- Room uses an expanded route bounding box only as a candidate pre-filter; candidates are then filtered off the main thread by minimum equirectangular point-to-polyline-segment distance
- route mode reuses the existing clustered station GeoJSON source, reruns corridor filtering after successful MIMIT refresh, suppresses normal Search this area/viewport replacement, and remains visible and locally useful after connectivity loss
- clearing route mode retains the camera and triggers a normal current-viewport search; GPS recenter and standalone place selection clear route mode before nearby behavior
- focused OSRM parser, corridor, segment-distance, route filtering, Photon URL scope, and scoped-cache tests added
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device verification: pending
Phase 7B route UX and waypoint support (2026-08-31):
- route sheet closes immediately after valid Find route submission; a compact map overlay shows calculation progress and failures use Snackbar while preserving drafts and any prior active route
- route drafts now have stable per-point identity and independent query, Photon result, loading, error, and selected endpoint state; results render below only their owning field
- From supports My location through the existing foreground permission/location flow, with inline unavailable/denied feedback and no duplicate A marker over the location puck
- up to eight removable/reorderable intermediate stops are sent to OSRM Route in exact displayed order; cache keys include all ordered coordinates and RouteResult retains them
- route waypoint source/layers render A, numbered stops, and B above stations with Liberty-compatible Noto Sans glyphs; waypoint taps take priority and animate to zoom 10.5 without route/search mutation
- route corridor is session-configurable as Auto (2%, clamped 3-10 km) or fixed 3/5/10/20 km; changes rerun only local Room candidate/filter work against the existing route
- standalone place selection creates one temporary source/layer marker; its action sheet supports Route here and Remove marker, and successful matching route activation removes it
- route clearing removes route geometry/waypoints/stops and resumes current-viewport nearby search; route mode continues suppressing normal Search this area
- focused tests cover OSRM two/multi-point order, automatic/fixed corridors, per-field draft assignment, stop removal/reordering, and place-marker actions
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- physical-device verification: pending

Phase 7C final route-flow consolidation (2026-09-01):
- removed the dedicated top-left route control; the controls are again menu, data status, and place search
- standalone Photon selection now centers at zoom 10.5, creates/replaces the temporary marker, immediately opens its action sheet, and never clears an active route or queries Room by viewport
- marker actions are Navigate to, Add as stop, and Remove marker; Navigate to sets/replaces To while preserving a sensible From and ordered stops, and Add as stop inserts immediately before To or creates a stop-first draft
- usable current location prefills an empty From as My location, while explicit Use my location still replaces From; neither marker action calls OSRM
- the active route summary body opens the route editor and its separate X clears the route
- GPS recenter and route-waypoint taps are camera-only zoom-10.5 actions and preserve route/filter state
- removed Search this area, viewport-dirty tracking, last-searched-bounds state, camera-idle dirty listeners, and automatic viewport queries after GPS/place selection
- normal mode loads every local station with its CNG prices into the clustered source at existing-data startup, successful normal refresh, and route clear; active-route refresh and corridor changes retain exact local route filtering
- retained Room bounds querying exclusively as the route-corridor candidate prefilter and retained exact point-to-route filtering
- expanded focused route-draft tests for destination replacement, origin/stop preservation, stop insertion, empty-destination drafts, and My location preservation; existing coordinate-order and corridor tests remain
- `\.\gradlew.bat test`: PASS
- `\.\gradlew.bat assembleDebug`: PASS
- physical-device verification: pending

Phase 7 fast search/navigation UX simplification (2026-09-01):
- all normal and quick place inputs autofocus and open the keyboard after entering composition, using shared Compose typeahead UI rather than Activity/window focus hacks
- Photon typeahead starts at two normalized characters, debounces 375 ms, cancels pending work, clears old suggestions immediately, rejects stale responses, preserves ranking/throttling/session caching, and supports immediate keyboard Search
- normal suggestion selection closes search, centers at zoom 10.5, creates/replaces one temporary marker, and shows a compact floating Navigate action without opening another sheet
- Navigate opens a minimal destination-context + From sheet; selecting a global Photon result or usable My location immediately closes it and requests the two-point OSRM route with no Find route confirmation
- a route-active Add Stop map control opens a minimal global Photon sheet; selection inserts immediately before To, preserves prior stop order, and triggers one recalculation
- active-route ordering/removal moved to the hamburger drawer Route section; changes remain local until Apply changes, while unchanged order uses Done
- the Auto/3/5/10/20 km corridor selector moved to the drawer and continues to apply immediately through local Room filtering without OSRM
- route replacement failures preserve the previous active route and temporary searched destination for retry; the compact calculating overlay and Snackbar behavior remain
- retained all-stations normal mode, route-only corridor stations, camera-only locator/waypoint actions, ordered A/number/B markers, and the exact Noto Sans waypoint font fix
- added focused tests for typeahead threshold/stale-query behavior, normalized Photon cache reuse, quick Navigate endpoint creation, ordered single/multiple stop insertion, drawer reorder/removal, and Apply-vs-Done state
- `\.\gradlew.bat test`: PASS
- `\.\gradlew.bat assembleDebug`: PASS
- physical-device verification: pending

Technical stability/cleanup pass (2026-09-01):
- centralized asynchronous station projection behind one replaceable job and a
  monotonic generation guard; commits additionally validate normal/route mode,
  active route identity, and corridor setting
- route replacement reserves the newest station generation and commits the new
  route only after OSRM and matching corridor-station filtering both succeed;
  cancellation and failures preserve the previous route and station projection
- unified immediate/callback location resolution so a pending My location route
  request takes priority after permission grant, while ordinary locator requests
  remain camera-only; denial, dismissal, engine failure, and security failures
  clear pending flags and updates
- removed unused Phase 7B route draft types/actions/tests, unused routeError
  state/imports, and the low-value missing-source debug log
- retained the persistent clustered source, route/place layers, interactions,
  and all three required `Noto Sans Regular` SymbolLayer font declarations
- replaced obsolete draft tests with current QuickRouteActions coverage and
  added pure station-generation/location-decision tests
- total unit tests: 99 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS

Route/projection concurrency correction (2026-09-01):
- route startup still cancels the current station-only projection and advances
  its generation once so that projection cannot commit
- station-only generation changes after route startup, including those caused
  by successful MIMIT refresh, no longer invalidate the route transaction
- route commit eligibility now depends only on the newest `routeRequestId` and
  the corridor used for filtering still being current; OSRM and station
  filtering must both succeed before route/stations commit
- added focused tests separating station-projection generation from route
  transaction identity and retaining newer-request/corridor invalidation
- total unit tests: 101 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS

Offline route-loading state correction (2026-09-01):
- an offline route attempt now clears the cancelled route job reference and
  resets route loading after advancing `routeRequestId`, preventing an older
  request's calculating state from remaining visible indefinitely
- the existing active route, stations, and station-only projection state remain
  unchanged; no OSRM request starts
- endpoint-count validation remains before request cancellation and correctly
  leaves an existing valid request untouched
- total unit tests: 101 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS

Phase 8A offline-safe app behavior (2026-09-01):
- Phase 7 is technically complete
- added `asset://styles/offline.json`, containing only a local background layer
  with no remote sources, tiles, sprites, or glyphs
- validated internet at initial map setup selects OpenFreeMap Liberty; an
  offline start selects the bundled style immediately, and a failed initial
  Liberty load falls back once to the bundled style
- both styles share one custom-layer initialization path that restores current
  stations, active route geometry/waypoints, temporary place marker, and the
  permission-backed location component without route recalculation
- Liberty retains station price, cluster count, and waypoint text with the
  explicit `Noto Sans Regular` font; the local style omits those SymbolLayers
  while retaining station/cluster/waypoint circles, route lines, and taps
- existing Room station loading/details and GPS remain connectivity-independent;
  Photon, OSRM, and MIMIT refresh exit before HTTP while offline, and live
  enrichment remains skipped offline
- added 2 pure tests for initial style selection and online/offline station and
  waypoint text/interaction configuration
- total unit tests: 103 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- physical offline-device verification: pending

Phase 8A offline MapLibre resource correction (2026-09-01):
- replaced the separate remote-Liberty/minimal-fallback choice with one current
  Liberty style bundled at `asset://map/liberty.json`; its normal OpenFreeMap
  tile sources and sprite remain network-backed
- changed the style-wide glyph template to
  `asset://map/glyphs/{fontstack}/{range}.pbf` and bundled Liberty's Noto Sans
  Regular/Italic/Bold Latin and Latin-extended ranges plus Regular 8192-8447 for
  the Euro sign used by station prices
- station price, cluster count, and route waypoint SymbolLayers are now always
  installed and retain exactly `Noto Sans Regular`; the decorative searched-place
  dot text was removed because its existing circle is authoritative
- local Room stations, circles, prices, counts, taps/details, route overlays,
  and GPS are expected to work in airplane mode; uncached detailed basemap tiles
  remain unavailable offline
- replaced style-selection tests with local style/glyph URL checks and nonempty
  required-glyph asset checks; verified all style and glyph assets are packaged
  in the debug APK
- total unit tests: 103 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- physical airplane-mode glyph verification: pending
- Offline overlays fixed; detailed offline basemap data still pending.

Phase 8A two-style regression repair (2026-09-01):
- reverted the locally modified Liberty style that caused total map-style
  failure; online startup again uses the unchanged known-good
  `https://tiles.openfreemap.org/styles/liberty`
- added independent `asset://map/offline.json` with only a neutral background,
  local Noto glyph template, no sources, and no sprite
- validated connectivity selects remote Liberty or the offline asset once during
  MainActivity map initialization; a failed online style load is logged under
  `CngMapStyle` and falls back once to the offline style
- both loaded styles use the same initialization for route, station, waypoint,
  searched-place, and location layers; dynamic text layers remain enabled with
  exactly `Noto Sans Regular`
- retained only app-required local glyphs: Regular 0-255 for digits/A/B and
  Regular 8192-8447 for the Euro sign; removed the decorative place-marker glyph
  dependency
- removed the unused bundled Liberty JSON and its extra Bold/Italic/extended
  glyph downloads; these generated assets were deleted directly and are not
  recoverable from the working tree
- total unit tests: 103 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- online and airplane-mode physical verification: pending

Online Liberty fallback diagnostic correction (2026-09-01):
- removed automatic `ONLINE_LIBERTY` to `OFFLINE_ASSET` replacement from
  `addOnDidFailLoadingMapListener`, so an online map-load error can no longer be
  hidden behind the beige offline style
- fresh validated-online startup still requests exactly
  `https://tiles.openfreemap.org/styles/liberty`; fresh offline startup still
  requests `asset://map/offline.json`, with no live connectivity switching
- `CngMapStyle` now logs the startup validated-internet value, requested style
  mode and URI, successful setStyle callback, and full failure message with the
  current requested mode
- MapLibre Android 13.3.1 maps this listener to native
  `onDidFailLoadingMap(MapLoadError, message)`; native categories are style
  parse/load, not-found, or unknown errors, but the Android callback exposes only
  the message. Tile, glyph, and sprite events use separate listener APIs.
- total unit tests: 103 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- online Liberty physical restoration and the exact device failure message:
  pending device verification

Lifecycle-aware map style selection (2026-09-01):
- exposed the existing ConnectivityManager-backed `online` StateFlow as
  `validatedInternet`; no second connectivity source or callback was added
- MainActivity collects that StateFlow while STARTED, updates the desired style,
  and applies it whenever both connectivity intent and MapLibreMap are available
- a small generation tracker suppresses duplicate `setStyle` calls and rejects
  stale callbacks when connectivity changes during style loading
- every authoritative style callback reinstalls route, station, waypoint/place,
  and location layers from current ViewModel state; style changes preserve the
  MapLibre camera and disable repeat automatic GPS centering after the first
  successfully loaded style
- failure logging remains diagnostic only and never changes the desired style
- added 2 focused tests for offline-to-online latest-wins behavior, stale callback
  rejection, and duplicate request suppression
- total unit tests: 105 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- physical connectivity transition verification: pending

Phase 8B application-side PMTiles foundation (2026-09-01):
- added three-way map selection: validated internet always uses unchanged remote
  Liberty; offline uses an installed Italy PMTiles archive when present and the
  existing minimal background otherwise
- canonical archive location is `filesDir/maps/italy.pmtiles`; MapLibre receives
  the native `pmtiles://file://<absolute-path>` source URL without another PMTiles
  library or an embedded HTTP server
- added a storage-only offline map manager for existence, size, optional version
  metadata, same-directory staged/atomic replacement, and deletion
- added a runtime-substituted local style template explicitly targeting the
  Protomaps `earth`, `water`, `roads`, `boundaries`, and `places` source layers;
  it provides a deliberately simple basemap and reuses local Noto glyphs
- all three styles continue through the same style-ready path, restoring current
  station, route, place, and location layers without camera reset or route/data
  refresh
- no archive, production download URL, or download UI was added; intended archive
  production is Geofabrik Italy OSM PBF -> Protomaps basemap/Planetiler -> PMTiles,
  and physical-device rendering remains pending
- added focused mode-selection, template-schema, PMTiles URI, installation
  metadata, size, and deletion tests
- total unit tests: 108 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS
- verified the debug APK packages the PMTiles style template, minimal style, and
  both required local Noto glyph ranges

Phase 8B developer archive pipeline (2026-09-01):
- added `tools/offline-map/build-italy-map.ps1`, which checks Git, Java 21+, and
  Maven; pins the official `protomaps/basemaps` revision; builds its Planetiler
  profile; and directly generates `build/offline/italy.pmtiles` from Geofabrik
  Italy data without `pmtiles extract`
- the build supports a same-schema `-Region NordEst` proof archive using
  Planetiler area `nord-est` and Geofabrik's Italy/Nord-Est PBF, keeps its
  checkout/source cache under ignored
  `build/offline`, rejects missing or under-10 MiB output, and automatically runs
  `pmtiles show --metadata` plus `pmtiles verify` when that optional CLI exists
- validated the pinned official basemaps source: its documented build flags and
  nested Geofabrik area handling match the script, and its profile defines the
  style's `earth`, `water`, `roads`, `boundaries`, and `places` source layers;
  its road classification also retains `pm:kind` values `highway` and
  `major_road`
- added `tools/offline-map/install-italy-map.ps1`, which requires the generated
  archive and one selected authorized device, verifies debug `run-as`, stages
  through `/data/local/tmp`, atomically replaces `files/maps/italy.pmtiles`,
  compares byte sizes, lists the installed file, and removes the temporary file
- the offline vector source now carries visible `Protomaps © OpenStreetMap`
  attribution, and `CngMapStyle` logs the absolute Italy archive path, existence,
  and byte size whenever validated connectivity becomes offline
- documented prerequisites, full-Italy/Nord-Est commands, metadata checks,
  installation, logging, and physical test boundaries in
  `tools/offline-map/README.md`
- PowerShell parser checks for both scripts: PASS
- total unit tests: 108 PASS
- `\.\gradlew.bat test`: PASS
- `\.\gradlew.bat assembleDebug`: PASS
- the large map-data build was not run in this environment; `pmtiles` and `adb`
  are also unavailable for the requested archive verification and installation
- real archive metadata and physical-device rendering remain pending, so Phase
  8B is not complete

Phase 8B Windows source-download correction (2026-09-01):
- physical `-Region NordEst` testing confirmed that `nord-est` is the correct
  Planetiler Geofabrik area (not `italy/nord-est`) and resolves the expected
  Geofabrik Italy/Nord-Est URL
- the build then physically failed in Planetiler's 10-second HTTP HEAD metadata
  lookup, and a separate 20-second `curl.exe -I -L` request to the listed ~593 MB
  PBF also timed out with zero bytes; the upstream file was not missing
- `build-italy-map.ps1` now pre-downloads the selected OSM PBF, Natural Earth,
  water polygons, land polygons, and daylight landcover to the exact
  `tiles/data/sources` paths expected by the pinned profile, then runs Planetiler
  without `--download`
- completed files are reused using conservative local minimum sizes; downloads
  stage to retained `.partial` files, prefer Windows BITS for fresh transfers,
  and use resumable/retrying `curl.exe` GET as fallback or for partial files
- existing QRank and PGF encoding caches are preserved and reported; their
  separate profile-managed behavior is unchanged
- the pinned Planetiler JAR is reused when its recorded basemaps revision still
  matches, with `-Rebuild` available for an explicit Maven rebuild
- documented the corrected area name, physical timeout, local-source strategy,
  cache behavior, and JAR reuse in `tools/offline-map/README.md`
- PowerShell parser check: PASS
- cached-file helper behavior check (invalid URL, valid local destination): PASS
- total unit tests: 108 PASS
- `\.\gradlew.bat test`: PASS
- `\.\gradlew.bat assembleDebug`: PASS
- the large downloads and Planetiler build were intentionally not run in this
  environment; PMTiles generation and physical rendering remain pending
- after the primary Geofabrik host physically returned HTTP 503 overload, the
  OSM downloader was extended to try Geofabrik's official `download-ext2` mirror
  after primary timeout/network/HTTP failure while preserving the same resumable
  `.partial` destination; no other source URL or pipeline behavior changed
- mirror-fallback PowerShell parser check: PASS
- post-change `\.\gradlew.bat test` and `\.\gradlew.bat assembleDebug`: PASS

Phase 8B Ljubljana developer proof input (2026-09-01):
- added temporary `-Region LjubljanaTest`, mapping BBBike's raw Ljubljana OSM
  PBF to local `data/sources/ljubljana-test.osm.pbf`, Planetiler area
  `ljubljana-test`, and a conservative 10 MiB input minimum
- the raw PBF still passes through the same pinned Protomaps Basemap Planetiler
  profile with automatic downloads disabled and produces the existing
  `build/offline/italy.pmtiles` developer artifact; no BBBike Shortbread
  PMTiles/MBTiles product is used
- all Natural Earth, water/land polygon, landcover, QRank, and PGF sources and
  cache behavior remain unchanged
- this region is only an end-to-end architecture proof; the eventual Italy
  package remains Geofabrik Italy OSM -> Protomaps Planetiler -> `italy.pmtiles`
  when Geofabrik access is available, and physical rendering is still pending
- PowerShell parser check: PASS
- total unit tests: 108 PASS
- `\.\gradlew.bat test`: PASS
- `\.\gradlew.bat assembleDebug`: PASS

Phase 8B Ljubljana physical developer proof and installer cleanup (2026-09-02):
- physically verified on a Samsung Galaxy S23 that the `LjubljanaTest` raw
  BBBike OSM PBF builds successfully through the pinned Protomaps Planetiler
  profile into `build/offline/italy.pmtiles`
- the generated developer archive was 39,947,520 bytes (approximately 38.1 MiB)
  and loaded from the app-private `files/maps/italy.pmtiles` location
- in airplane mode, MapLibre rendered local land and water at world zoom and,
  around Ljubljana, local roads and the Ljubljana place label
- GPS remained visible, and local CNG station overlays, prices, and clusters
  rendered above the offline basemap
- the Android PMTiles architecture is therefore physically proven; LjubljanaTest
  remains only a developer proof and is not the production Italy dataset
- fixed `install-italy-map.ps1` so normal adb stderr progress does not become a
  terminating PowerShell error; the helper captures both native streams and
  decides success or failure from adb's exit code while preserving global
  terminating cmdlet error handling
- Phase 8B remains incomplete: production Italy/NordEst map source/build, an
  end-user download/update mechanism, and final offline basemap styling/polish
  remain
- PowerShell parser check for `install-italy-map.ps1`: PASS
- total unit tests: 108 PASS
- `.\gradlew.bat test`: PASS
- `.\gradlew.bat assembleDebug`: PASS

Phase 8B production pipeline hardening (2026-09-02):
- retained `NordEst` and `Italy` as Geofabrik-only production inputs, including
  the official `download-ext2` fallback, while leaving the physically proven
  `LjubljanaTest` developer path available
- all pinned-profile inputs are now prepared and cached before Planetiler runs:
  OSM PBF, Natural Earth, water/land polygons, daylight landcover, QRank, and
  PGF encoding; automatic Planetiler source downloading remains disabled
- completed inputs are reused and resumable `.partial` files are preserved;
  conservative size checks now also include format signatures for PBF, ZIP,
  gzip, and GeoPackage inputs so obviously invalid caches fail clearly
- a locally available pinned basemaps commit no longer requires a network fetch;
  the matching Planetiler JAR is still reused, and `-Rebuild` remains the
  explicit rebuild override
- generation now targets `italy.pmtiles.building`, checks minimum size and the
  PMTiles v3 header, runs optional `pmtiles show --metadata` and `pmtiles verify`,
  and requires metadata IDs `earth`, `water`, `roads`, `boundaries`, and
  `places` before replacing canonical `build/offline/italy.pmtiles`
- the installer rejects undersized or non-PMTiles-v3 input before touching the
  connected device
- PowerShell parser checks for both offline-map scripts: PASS
- `.\gradlew.bat test`: PASS (108 tests, tasks up-to-date)
- `.\gradlew.bat assembleDebug`: PASS
- the cached Ljubljana artifact headers and cached pinned-profile source headers
  were inspected successfully; `pmtiles` CLI is unavailable in this environment
- no production NordEst/Italy download, generation, metadata inspection, PMTiles
  verification, installation, or physical airplane-mode test was performed here;
  production artifacts remain physically unverified

Phase 8B NordEst staged-output regression correction (2026-09-02):
- the first physical production `-Region NordEst` attempt successfully downloaded
  and cached the 593.5 MiB Geofabrik PBF, reused all support sources and the
  pinned Planetiler JAR, and reached generation with automatic downloads disabled
- Planetiler then failed immediately because the staged filename
  `italy.pmtiles.building` ended in `.building`, which is not a supported archive
  extension; the repeated `Unexpected token type: START_OBJECT` warnings were
  not treated as this failure
- changed only the same-directory staged output to `italy.building.pmtiles`, so
  Planetiler infers PMTiles while validation still precedes canonical publication
- added an early runtime invariant requiring the staged output's final extension
  to be `.pmtiles`; failed generation or validation removes the staged artifact
  and preserves any existing canonical `build/offline/italy.pmtiles`
- focused staging-name source check: PASS (`italy.building.pmtiles`, distinct
  from the canonical archive)
- PowerShell parser checks for both offline-map scripts: PASS
- `.\gradlew.bat test`: PASS (108 tests, tasks up-to-date)
- `.\gradlew.bat assembleDebug`: PASS
- `git diff --check`: PASS
- NordEst generation has not yet been rerun successfully or physically verified

Phase 8B production NordEst proof and mixed-geometry fill correction (2026-09-02):
- production `-Region NordEst` generation: PASS; canonical
  `build/offline/italy.pmtiles` is 731,451,414 bytes (approximately 697.6 MiB)
- generated metadata contains the Android-required `earth`, `water`, `roads`,
  `boundaries`, and `places` layers; `pmtiles verify`: PASS with no errors
- physical Samsung Galaxy S23 airplane-mode PMTiles rendering: PASS for basemap,
  roads, place labels, CNG stations, prices, clusters, and app-owned overlays
- physical testing also found large diagonal/triangular light-blue and grey fill
  bands at some locations/zooms; offline basemap visual artifact/polish remains
  pending physical retest
- pinned profile inspection found that `earth` deliberately mixes land polygons
  with cliff lines and island label points, while `water` mixes water/ocean
  polygons with waterway/reef lines and sea/ocean label points
- the local style had unfiltered fill layers for both mixed-geometry sources;
  added explicit `geometry-type == Polygon` filters, matching the official
  Protomaps fill convention and leaving valid earth/water polygons visible
- added focused asset assertions for both polygon filters and a developer-only,
  binary-safe Z/X/Y raw MVT extraction script; neither changes production runtime
- the verified 697.6 MiB archive is unchanged and reusable; only the APK/style
  needs rebuilding and reinstalling for the physical visual retest
- focused `MapAssetsTest`: PASS
- PowerShell parser checks for all three offline-map scripts: PASS
- `.\gradlew.bat test`: PASS (108 tests)
- `.\gradlew.bat assembleDebug`: PASS
- `git diff --check`: PASS

Phase 8B full-Italy production proof:
- production Italy generation: PASS; canonical archive approximately 2543.7 MiB
- physical Samsung Galaxy S23 airplane-mode rendering: PASS
- offline roads/place labels, CNG stations, prices, clusters, station details,
  and GPS: PASS; visual result accepted by the user
- local developer installation is the current personal/travel distribution method
- public hosting, in-app download/update, Play Store distribution, and map-size
  optimization are deferred until after the personal app is finished
- full Italy remains locally installed at `filesDir/maps/italy.pmtiles`; Phase 8C
  trip-readiness and reliability is now active

Map style load-failure fallback patch:
- preserved normal connectivity-based style selection and added one-direction
  failure degradation: online Liberty to installed PMTiles or minimal offline,
  and PMTiles to minimal offline; minimal offline has no further fallback
- fallback attempts retain the current loaded style and do not show Map unavailable;
  terminal failure clears the loaded style and reports Map unavailable
- unit/build verification completed; physical Samsung Galaxy S23 transition
  testing is pending

Narrow LocationComponent safety patch:
- removed `pmtiles.exe` from Git tracking and ignored it as a local developer tool
- searched-place camera movement now changes LocationComponent camera mode only
  after activation
- Use my location for route now reads `lastKnownLocation` only from an activated
  LocationComponent and no longer reports Current location unavailable before
  attempting a fresh location request
- physical Samsung Galaxy S23 verification is pending

Phase 8C one-shot location reliability patch:
- stale/null last-location callbacks no longer restart location work after the
  pending route or center action is cancelled
- `getLastLocation` failure now falls through to one active high-accuracy request;
  active update failure is terminal instead of displaying a false Waiting message
- a missing LocationEngine reports Current location unavailable rather than Waiting
- permission loss during the active request reports a permission error consistently
- pure failure-decision logic is unit tested; physical Samsung Galaxy S23
  verification remains pending

Phase 8C bounded location-resolution deadline:
- one total 30-second deadline now bounds last-known lookup plus the optional
  active high-accuracy location request and is not restarted between phases
- success, cancellation, and terminal failure cancel the timeout; stale timeout
  completion cannot report after the pending location action is gone
- a null LocationEngine during `getLastLocation` now fails immediately, and
  `enableLocationAndCenter` SecurityException clears pending state consistently
- the pure timeout decision is unit tested; Samsung Galaxy S23 physical
  verification remains pending

Phase 8C connectivity transition fix:
- Samsung Galaxy S23 physical testing found a reproducible online-to-offline
  transition bug: offline-to-online worked, but after app removal from Recents
  and reopening online, the inverse transition could remain stuck as online
- root cause was synchronous `activeNetwork`/`getNetworkCapabilities` re-querying
  from `NetworkCallback` callbacks, which is race-prone under Android's API contract
- `onLost` now marks the default network offline directly;
  `onCapabilitiesChanged` derives validated internet state from the supplied
  `NetworkCapabilities`; and `onAvailable` waits for the following capability
  callback instead of assuming internet
- the initial synchronous connectivity read remains for ViewModel initialization
- a unit test covers the `INTERNET` + `VALIDATED` capability truth table
- Samsung Galaxy S23 retest is pending

Phase 8C disabled device Location UX:
- added the user-requested trip UX improvement for disabled Android Location
  services: explicit current-location actions detect the device-wide Location
  toggle before starting GPS resolution
- disabled Location shows a Material prompt with Cancel and Open settings; Open
  settings navigates to Android Location settings
- returning with Location enabled automatically resumes the originally requested
  CENTER or ROUTE action; returning without enabling it leaves no pending
  route-location action and does not reopen the prompt
- airplane mode remains independent, so GPS continues to work offline when
  Location is enabled
- the existing permission and 30-second GPS flows remain unchanged
- physical Samsung Galaxy S23 verification is pending

Phase 8C final Samsung Galaxy S23 verification:
- the connectivity transition fix was physically retested successfully:
  online -> offline and offline -> online both switch map styles correctly,
  including after removing the app from Recents and reopening it
- the disabled-device-Location flow was physically verified: the map current-
  location action prompts immediately when Location is off; Cancel works; Open
  settings opens Android Location settings; returning after enabling Location
  automatically resumes CENTER; returning without enabling it leaves no pending
  action; and ROUTE resumes correctly after enabling Location
- airplane mode remains independent from the Location toggle: with Android
  Location enabled, current-position GPS continues to work offline
- an active route was physically verified across online -> offline -> online
  style changes: route geometry, waypoint markers, route summary, route-filtered
  stations, and camera state survive Liberty <-> local PMTiles transitions without
  duplicate overlays; clearing the route restores normal station display
- the full-Italy offline PMTiles build, offline cold-start rendering, station
  markers/prices/clusters/details, and GPS behavior had already been physically
  verified on the same Samsung Galaxy S23
- Phase 8C is complete for the current personal-use scope; this commit documents
  the known-good personal trip build
- Google Play/public distribution, hosting, in-app map downloading, and map-size
  optimization remain intentionally deferred and are not part of the completed
  personal trip scope

Post-8C personal-build performance polish:
- MIMIT station and price CSV downloads now start concurrently instead of serially
- route rendering still uses the untouched full OSRM geometry
- route station projection uses a 75 m simplified filtering polyline to reduce
  segment comparisons
- stations near the corridor boundary fall back to the full route geometry so
  corridor semantics remain exactly preserved
- distance-to-route iteration no longer uses `zipWithNext` in the hot loop
- the OSRM endpoint, full-route request, cache, request spacing, and timeouts
  remain unchanged
- Samsung Galaxy S23 verification passed: route calculation is noticeably
  faster; station refresh showed no meaningful perceptible speed difference,
  so no further refresh optimization is planned

D1 visual work:
- initial combined visual pass was physically rejected on Samsung Galaxy S23
  because the launcher icon was too small/crude and the theme direction was not
  accepted
- visual work is now split into independently verified steps
- D1a launcher icon was physically approved on Samsung Galaxy S23 after using
  the provided final raster artwork at the approved 75% foreground scale
- D1b light-theme palette and D1b.1 theme-aware map controls were physically
  approved on Samsung Galaxy S23
- D1c dark navy theme was visually approved, then refined to use muted dark-blue
  and forest-green accents
- a persistent light/dark theme toggle was added to the drawer header
- first launch follows the Android system theme until the user explicitly chooses
  a mode; that choice then persists across app restarts
- final D1c/theme-toggle behavior was physically approved on Samsung Galaxy S23;
  D1 visual identity is complete
- D2a replaces only the normal place-search bottom sheet with a full-screen
  search surface; physical Samsung Galaxy S23 verification is pending
- D2b navigation UX remains pending
- D3 route editing remains pending

## Important discoveries

- MapLibre Android 13.x uses Vulkan for `org.maplibre.gl:android-sdk`; the explicit OpenGL artifact is `org.maplibre.gl:android-sdk-opengl:13.3.1`.
- AGP 9 built-in Kotlin requires `android.disallowKotlinSourceSets=false` for the required KSP-generated Room sources.

## Deviations from DESIGN.md

None.
