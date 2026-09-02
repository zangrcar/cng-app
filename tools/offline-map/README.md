# Developer offline map pipeline

These scripts build and install a real Protomaps-schema PMTiles basemap for
physical debug-device testing. They are developer tooling, not an end-user map
download feature.

Production sources are OpenStreetMap data from Geofabrik. A temporary Ljubljana
developer proof can instead use BBBike's raw OSM PBF. Every archive is generated
directly by the official [`protomaps/basemaps`](https://github.com/protomaps/basemaps)
Planetiler profile. The scripts never download or scrape OpenFreeMap tiles,
never use BBBike's ready-made Shortbread PMTiles/MBTiles, and never use
`pmtiles extract`.

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

When both Geofabrik hosts are unavailable, the temporary end-to-end proof region
uses BBBike's raw Ljubljana OSM PBF:

```powershell
.\tools\offline-map\build-italy-map.ps1 -Region LjubljanaTest
```

It downloads
`https://download.bbbike.org/osm/bbbike/Ljubljana/Ljubljana.osm.pbf` to
`tiles/data/sources/ljubljana-test.osm.pbf`, then passes area
`ljubljana-test` through the same pinned Protomaps Basemap Planetiler profile.
It does not consume BBBike PMTiles or MBTiles, whose Shortbread schema is
incompatible with the current Android offline style.

All modes deliberately write the artifact to:

```text
build/offline/italy.pmtiles
```

The basemaps checkout and downloaded source cache remain under
`build/offline/cache/`, outside Android assets and ignored by Git.

The Protomaps/Planetiler Geofabrik area name for Nord-Est is `nord-est`.
`italy/nord-est` is not valid for this profile, even though the final Geofabrik
URL contains `/italy/nord-est-latest.osm.pbf`.

During the first physical Nord-Est build, Planetiler resolved the correct URL
but its downloader timed out after 10 seconds while obtaining HTTP metadata with
a HEAD request. A direct 20-second `curl.exe -I` check timed out as well, while
Geofabrik still listed the approximately 593 MB file. To avoid that short
metadata path, this script downloads the OSM PBF, Natural Earth, water and land
polygons, and daylight landcover into `tiles/data/sources` before starting
Planetiler. It prefers Windows BITS for fresh long-running downloads and falls
back to resumable `curl.exe` GET requests. Planetiler then runs without
`--download` and consumes those local files.

Completed cached inputs are reused after conservative local size and format-header
checks. This includes the OSM PBF, Natural Earth, water/land polygons,
landcover, QRank, and PGF encoding inputs; all are prepared before Planetiler is
started, so the generation run does not depend on Planetiler downloading any
missing source. Invalid completed cache files fail clearly instead of being
silently consumed.
Interrupted `.partial` files are retained for a later resumable attempt. The
same cache paths continue to be used across regions. Use `-Rebuild` only when
the Planetiler JAR must be rebuilt despite an unchanged pinned basemaps revision.
If the pinned revision and its built JAR are already cached, neither is fetched
or rebuilt unnecessarily.

If the primary `https://download.geofabrik.de` OSM download fails because of a
timeout, network error, or HTTP failure such as service overload, the script
continues the same cached OSM download from Geofabrik's official
`https://download-ext2.geofabrik.de` mirror. This mirror fallback applies only
to the Geofabrik OSM PBF; all other source URLs remain unchanged.

`LjubljanaTest` is only a small end-to-end developer proof of raw OSM PBF ->
pinned Protomaps Planetiler -> Protomaps-schema `italy.pmtiles` -> the existing
Android `OFFLINE_PMTILES` path. It is not the eventual Italy package. Once
Geofabrik access is available, the production pipeline remains Geofabrik Italy
OSM -> Protomaps Planetiler -> `italy.pmtiles`.

This Ljubljana developer path has rendered successfully on a physical Samsung
Galaxy S23. Its 39,947,520-byte (approximately 38.1 MiB) archive loaded from the
app-private map location and, in airplane mode, displayed land/water, Ljubljana
roads and the Ljubljana place label while GPS and local CNG station overlays,
prices, and clusters remained visible. This proves the Android PMTiles path; it
does not make LjubljanaTest a production Italy dataset.

The production NordEst path has also completed successfully. Its canonical
archive is 731,451,414 bytes (approximately 697.6 MiB), contains all five source
layers required by the Android style, and passes `pmtiles verify`. A Samsung
Galaxy S23 loaded it successfully in airplane mode with roads, place labels,
CNG stations, prices, clusters, and app overlays. The first visual pass exposed
diagonal blue/grey artifacts from unfiltered fill styling of Protomaps' mixed-
geometry `earth` and `water` layers. The Android style now restricts those fills
to polygon geometry; physical confirmation of that visual correction remains
pending. This style-only correction does not require rebuilding the archive.

## Inspect before installing

When the optional `pmtiles` CLI is available, the build script runs both checks
automatically and refuses to publish the archive unless metadata contains all
Android-required layers: `earth`, `water`, `roads`, `boundaries`, and `places`.
It also checks the PMTiles v3 header and a conservative minimum size. Generation
uses `build/offline/italy.building.pmtiles`, whose final extension lets
Planetiler infer PMTiles output correctly. Only after these checks succeed is
that file moved over the canonical archive, preserving an earlier good archive
if generation or verification fails. A failed staged artifact is removed. The
CLI checks can also be repeated
manually:

```powershell
pmtiles show build/offline/italy.pmtiles --metadata
pmtiles verify build/offline/italy.pmtiles
```

Confirm vector MVT metadata and the official Protomaps basemap profile. The
current pinned profile defines the source layers used by the Android style:
`earth`, `water`, `roads`, `boundaries`, and `places`. If a deliberately updated
profile changes its schema, reconcile the style against the generated metadata
before installing; do not guess layer names.

To extract the exact raw MVT for a visibly problematic tile without modifying
the archive or Android runtime, identify its Z/X/Y coordinate and run:

```powershell
.\tools\offline-map\inspect-pmtiles-tile.ps1 -Zoom <z> -X <x> -Y <y>
```

The binary-safe extractor writes `build/offline/inspect/<z>-<x>-<y>.mvt` for
inspection with a vector-tile decoder. This is developer diagnostics only.

## Install into the debug app

Install/run the debug APK once, then:

```powershell
.\tools\offline-map\install-italy-map.ps1
```

With multiple devices:

```powershell
.\tools\offline-map\install-italy-map.ps1 -Serial <adb-serial>
```

The installer first rejects an undersized file or invalid PMTiles v3 header. It
then pushes through `/data/local/tmp`, uses Android `run-as` to stage
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
used. Phase 8B still requires a production Italy/Nord-Est source/build, an
end-user download/update mechanism, and final offline basemap styling/polish.
