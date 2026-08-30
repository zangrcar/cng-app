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

- [ ] 1. Full-screen MapLibre map + working settings drawer (implementation/build complete; device gesture verification pending)
- [ ] 2. GPS/current-location support + recenter action
- [ ] 3. Local Room database + MIMIT station-data refresh + data-status UI
- [ ] 4. Show local stations for map viewport + Search this area + clustering
- [ ] 5. Station bottom sheet + live details + Google Maps navigation
- [ ] 6. Search for another place
- [ ] 7. A -> B route mode + stations along route
- [ ] 8. Offline/polish/testing on real phone

## Current task

Phase 1 usability fix implemented. Final device gesture verification is pending. Phase 2 has not been started.

Implemented:
- MapLibre Native Android 13.3.1 using the stable OpenGL `android-sdk` artifact
- full-screen native MapView hosted in Compose with AndroidView
- OpenFreeMap Liberty style and temporary startup camera over Italy
- MapView lifecycle and saved-state forwarding in MainActivity
- top-left menu button opening a dismissible Material 3 modal drawer
- drawer gestures enabled only while open: edge swipes cannot open it, while swipe-left can close it
- drawer close button plus normal scrim-tap dismissal
- drawer contains only "CNG Italy" and "Map ready"
- built-in MapLibre logo and attribution/info control retained in the bottom-left
- built-in MapLibre compass retained and positioned below the status bar using system insets
- INTERNET permission

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
- physical pan/pinch/rotate, drawer gestures, and compass interaction verification: pending device/emulator check

## Important discoveries

- MapLibre's stable OpenGL Android artifact is `org.maplibre.gl:android-sdk:13.3.1`.

## Deviations from DESIGN.md

None.
