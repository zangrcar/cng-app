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
- [ ] 3. Local Room database + MIMIT station-data refresh + data-status UI
- [ ] 4. Show local stations for map viewport + Search this area + clustering
- [ ] 5. Station bottom sheet + live details + Google Maps navigation
- [ ] 6. Search for another place
- [ ] 7. A -> B route mode + stations along route
- [ ] 8. Offline/polish/testing on real phone

## Current task

Phase 2 complete and manually verified on a physical device. Phase 3 has not been started.

Implemented:
- MapLibre Native Android 13.3.1 using the stable OpenGL `android-sdk` artifact
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
- one-time startup centering at zoom 10 when permission already exists
- bottom-right current-location button with navigation-bar inset handling
- button requests permission when needed and performs the original one-time zoom 10 recenter when location is available
- denial and waiting feedback through a Material Snackbar
- camera remains in `CameraMode.NONE`, so the map never continuously follows location

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
- restored the original current-location button recenter zoom of 10; automatic startup zoom remains 10
- made the menu control a 48 dp black circle with a white icon, aligned using the same status-bar inset and 8 dp edge margin as the compass
- `./gradlew.bat test`: PASS
- `./gradlew.bat assembleDebug`: PASS

## Important discoveries

- MapLibre's stable OpenGL Android artifact is `org.maplibre.gl:android-sdk:13.3.1`.

## Deviations from DESIGN.md

None.
