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

- [ ] 1. Full-screen MapLibre map + working settings drawer
- [ ] 2. GPS/current-location support + recenter action
- [ ] 3. Local Room database + MIMIT station-data refresh + data-status UI
- [ ] 4. Show local stations for map viewport + Search this area + clustering
- [ ] 5. Station bottom sheet + live details + Google Maps navigation
- [ ] 6. Search for another place
- [ ] 7. A -> B route mode + stations along route
- [ ] 8. Offline/polish/testing on real phone

## Current task

Phase 1:
Render a reliable full-screen interactive map and a functional settings drawer.

No GPS, stations, search, routing or fake controls yet.

## Verification

Baseline:
- Gradle test: PASS
- assembleDebug: PASS
- runs on device/emulator: PASS

## Important discoveries

None for native implementation yet.

## Deviations from DESIGN.md

None.