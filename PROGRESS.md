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
- separate last-successful refresh time and official MIMIT dataset dates; local freshness uses a rolling 24-hour window independent of source dates
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
- one clustered GeoJSON source installed with the loaded style, with cluster radius 50 and max cluster zoom 14
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
- physical-device viewport query and station/cluster/price rendering: PASS
- physical-device cluster expansion and refresh-requery verification: pending
- Phase 5 not started

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
- full Phase 4 completion remains pending physical-device cluster-expansion and refresh-requery verification

Data-status freshness correction (2026-08-31):
- main status freshness now depends only on a usable snapshot and `lastSuccessfulRefreshEpochMillis`: under 24 hours is fresh, while 24 hours or more is stale
- MIMIT station/price dataset dates remain visible source metadata but do not control the status icon
- status explanations concisely include relative refresh age and the MIMIT price snapshot date when local data exists
- failed refresh behavior remains unchanged: only a successful snapshot replacement writes `lastSuccessfulRefreshEpochMillis`
- added 6 unit tests covering 5-minute and 23h59m freshness, the 24-hour boundary, midnight crossing, old MIMIT source dates, and unchanged last-success timestamp after failure
- `./gradlew.bat test`: PASS (26 tests)
- `./gradlew.bat assembleDebug`: PASS

## Important discoveries

- MapLibre Android 13.x uses Vulkan for `org.maplibre.gl:android-sdk`; the explicit OpenGL artifact is `org.maplibre.gl:android-sdk-opengl:13.3.1`.
- AGP 9 built-in Kotlin requires `android.disallowKotlinSourceSets=false` for the required KSP-generated Room sources.

## Deviations from DESIGN.md

None.
