# Implementation Progress

## Current state

Native Android baseline created and verified.

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
- [ ] 7. Ordered route mode + stations along route (7A/7B implemented; physical verification pending)
- [ ] 8. Offline/polish/testing on real phone

## Current task

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

## Important discoveries

- MapLibre Android 13.x uses Vulkan for `org.maplibre.gl:android-sdk`; the explicit OpenGL artifact is `org.maplibre.gl:android-sdk-opengl:13.3.1`.
- AGP 9 built-in Kotlin requires `android.disallowKotlinSourceSets=false` for the required KSP-generated Room sources.

## Deviations from DESIGN.md

None.
