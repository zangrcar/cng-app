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
- [ ] 4. Show local stations for map viewport + Search this area + clustering (implemented/build-verified; physical verification pending)
- [ ] 5. Station bottom sheet + live details + Google Maps navigation
- [ ] 6. Search for another place
- [ ] 7. A -> B route mode + stations along route
- [ ] 8. Offline/polish/testing on real phone

## Current task

Phase 1 and Phase 2 are complete and manually verified on a physical device.

Phase 3 / Phase 3A is complete and manually verified on a physical device.

Phase 4 is the current task. It is implemented and build-verified, awaiting physical-device verification. Phase 5 has not been started.

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
- separate refresh time and official MIMIT dataset dates with Europe/Rome freshness rules
- dynamically tracked validated-internet connectivity
- compact fresh/old/offline map status control below the MapLibre compass
- drawer DATA section with refresh time, MIMIT date, station count, connection, and refresh progress/action
- one non-blocking first-run refresh only when no snapshot exists and validated internet is available
- no station markers, viewport queries, clustering, or Search this area

Phase 4 implemented:
- Room viewport query returns stations with all stored CNG prices and supports antimeridian-crossing bounds
- compact map model selects the lowest stored CNG price and formats it with exactly three decimals
- initial Italy/GPS viewport and GPS recenter perform one local station search
- user camera gestures only mark the viewport dirty after camera idle; no database work occurs during movement
- compact top-center Search this area action queries the current visible bounds on demand
- a clustered GeoJSON source with cluster radius 50 and max cluster zoom 14, recreated with complete data for each station result
- cluster circle/count and individual circle/price style layers; no deprecated marker annotations
- cluster taps use MapLibre expansion zoom and do not trigger a new Room query or dirty state
- successful MIMIT refresh reruns the last searched bounds, or searches the current viewport after first-run data arrives
- station querying works from Room without contacting MIMIT
- no station details, live API, place search, route UI, Google Maps action, or offline-map implementation

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
- physical-device viewport search, clustering, prices, cluster expansion, and refresh requery verification: pending
- Phase 5 not started

Phase 4 rendering/search correction (2026-08-30):
- physical-device symptom: viewport searches completed and hid Search this area, but rendered no stations or clusters
- root cause in the rendering handoff: station updates retained and mutated the `GeoJsonSource` instance created with an earlier style instead of resolving the source from the currently loaded MapLibre style
- station updates now get `map.style` and `getSourceAs<GeoJsonSource>("cng-stations")` for every update; existing results are still pushed immediately after source/layer installation, covering both style-first and Room-first ordering
- a non-empty result whose current style/source cannot be resolved is not treated as a completed visible map update; Search this area remains available
- `CngMap` diagnostic logs now trace bounds, metadata versus actual station-table count, DAO results, mapped results, GeoJSON feature count, current source/style state, and source/layer installation
- DAO viewport SQL, MapLibre bounds field mapping, antimeridian handling, `Point.fromLngLat(longitude, latitude)`, cluster filters, individual filters, and top-of-style layer insertion were inspected and retained as correct
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS
- physical-device verification remains pending

Phase 4 MapLibre layer correction (2026-08-30):
- follow-up device evidence proved the complete data path through current-source update: 1,115 Room rows mapped to 1,115 GeoJSON features with `sourceExists=true` and `styleLoaded=true`, while no markers rendered
- the remaining code-level rendering fault was the layer filter setup: it inferred individual points with `not(has("point_count"))` instead of using MapLibre's generated `cluster` boolean property
- cluster layers now use `cluster == true`; individual layers use `cluster != true`
- cluster count and station price use direct `Expression.get(...)` text fields; both symbol layers allow overlap and ignore placement
- final top-of-style order is station circle, station price, cluster circle, cluster count; circle opacity is explicitly 1
- the source remains a single clustered `GeoJsonSource` configured with radius 50 and max cluster zoom 14, and every layer uses that same source ID
- delayed `CngMap` diagnostics now log `querySourceFeatures(null)` count, rendered-feature count over the visible map rectangle, and existence/visibility/filter for all four intended layers after source updates and on camera idle
- no diagnostic all-points layer remains in the finished implementation
- `./gradlew.bat test`: PASS (20 tests)
- `./gradlew.bat assembleDebug`: PASS
- corrected filters and new source/render diagnostics await physical-device verification; Phase 4 remains pending

MapLibre renderer dependency correction (2026-08-30):
- Phase 1 accidentally used `org.maplibre.gl:android-sdk:13.3.1`, the default Vulkan renderer in MapLibre Android 13.x, despite progress documentation claiming OpenGL
- switched the single MapLibre dependency to the explicit OpenGL artifact `org.maplibre.gl:android-sdk-opengl:13.3.1`; the version remains 13.3.1
- station rendering implementation was intentionally left unchanged
- `./gradlew.bat clean`: PASS
- `./gradlew.bat test`: PASS (20 tests)
- `./gradlew.bat assembleDebug`: PASS
- Phase 4 remains pending physical-device verification

Phase 4 populated-source recreation (2026-08-30):
- physical-device diagnostics showed `querySourceFeatures=0` and `queryRenderedFeatures=0` after dynamic `setGeoJson`, despite hundreds or thousands of valid features reaching the current source
- stopped using the dynamic GeoJSON update path: each render removes current station layer objects and source, serializes the complete FeatureCollection, creates a new clustered `GeoJsonSource` containing that raw JSON, and recreates the four layers
- removed `withSynchronousUpdate(true)` and all station-rendering calls to `setGeoJson`
- restored common MapLibre clustering filters: cluster layers have `point_count`; individual layers do not have `point_count`
- cluster clicks continue to retrieve the current source from the current style
- retained concise source recreation, layer recreation, source-query, and rendered-query diagnostics
- `./gradlew.bat test`: PASS (20 tests)
- `./gradlew.bat assembleDebug`: PASS
- Phase 4 remains pending physical-device verification

## Important discoveries

- MapLibre Android 13.x uses Vulkan for `org.maplibre.gl:android-sdk`; the explicit OpenGL artifact is `org.maplibre.gl:android-sdk-opengl:13.3.1`.
- AGP 9 built-in Kotlin requires `android.disallowKotlinSourceSets=false` for the required KSP-generated Room sources.

## Deviations from DESIGN.md

None.
