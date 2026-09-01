# Developer offline map pipeline

These scripts build and install a real Protomaps-schema PMTiles basemap for
physical debug-device testing. They are developer tooling, not an end-user map
download feature.

The source is OpenStreetMap data from Geofabrik. The archive is generated
directly by the official [`protomaps/basemaps`](https://github.com/protomaps/basemaps)
Planetiler profile. The scripts never download or scrape OpenFreeMap tiles and
never use `pmtiles extract`.

## Prerequisites

- Git
- Java 21 or newer
- Maven
- For installation: Android platform-tools (`adb`), one connected authorized
  device, and an installed debuggable `com.zangrcar.cngitaly` build
- Significant disk, memory, download, and processing time for full Italy

The build script defaults to a pinned basemaps commit. Pass `-BasemapsRef` only
when intentionally validating a newer profile/schema.

## Build

From the repository root:

```powershell
.\tools\offline-map\build-italy-map.ps1
```

This uses:

```text
https://download.geofabrik.de/europe/italy-latest.osm.pbf
```

For a faster physical proof covering Friuli/Veneto, build Nord-Est with the same
Protomaps schema:

```powershell
.\tools\offline-map\build-italy-map.ps1 -Region NordEst
```

That uses:

```text
https://download.geofabrik.de/europe/italy/nord-est-latest.osm.pbf
```

Both modes deliberately write the test artifact to:

```text
build/offline/italy.pmtiles
```

The basemaps checkout and downloaded source cache remain under
`build/offline/cache/`, outside Android assets and ignored by Git.

## Inspect before installing

When the optional `pmtiles` CLI is available, the build script runs both checks
automatically. They can also be repeated manually:

```powershell
pmtiles show build/offline/italy.pmtiles --metadata
pmtiles verify build/offline/italy.pmtiles
```

Confirm vector MVT metadata and the official Protomaps basemap profile. The
current pinned profile defines the source layers used by the Android style:
`earth`, `water`, `roads`, `boundaries`, and `places`. If a deliberately updated
profile changes its schema, reconcile the style against the generated metadata
before installing; do not guess layer names.

## Install into the debug app

Install/run the debug APK once, then:

```powershell
.\tools\offline-map\install-italy-map.ps1
```

With multiple devices:

```powershell
.\tools\offline-map\install-italy-map.ps1 -Serial <adb-serial>
```

The installer pushes through `/data/local/tmp`, uses Android `run-as` to stage
and replace the file, compares byte sizes, and verifies:

```text
files/maps/italy.pmtiles
```

The package must be debuggable; a `run-as` failure is intentional and stops the
script. After installation, force-stop the app, enable airplane mode, and launch
it. `CngMapStyle` should report `exists=true`, `OFFLINE_PMTILES`, and a successful
style load. If the style loads but roads/water are absent, inspect archive
metadata and source layers before changing app mode selection.

The app must visibly retain OpenStreetMap attribution when this produced work is
used. A real Italy/Nord-Est archive rendering on the physical phone is still
required before Phase 8B can be marked complete.
