## Context
NetMap is the first native mini-app in Paranoid. The hub is currently WebView-based, so we need a bridge to launch native Activities. NetMap itself requires multiple Android system services (TelephonyManager, FusedLocationProvider), a foreground service for background recording, and a map rendering library (MapLibre).

Constraints from CLAUDE.md and functionality.md:
- Pure Kotlin + Android Views (no Compose)
- Constructor injection (no Hilt/Dagger)
- Minimal dependencies
- Offline-first

## Goals / Non-Goals
- Goals: working signal mapper with live map, background recording, data persistence, and export
- Non-Goals (v1): cloud sync, speed tests, multi-carrier scanning, offline tile packs (download on demand is sufficient), SettingsActivity (hardcode defaults; deferred to follow-up)

## Decisions

### Hub Architecture
- Decision: Convert the hub from WebView to a native RecyclerView-based Activity (separate prerequisite ticket PARANOID-a6z). The hub launches mini-app Activities directly via `startActivity(Intent(...))`.
- Why: The project convention is pure native Kotlin. A WebView hub adds unnecessary complexity (JS bridge, asset bundling, XHR workarounds) when a RecyclerView with a list of app entries is simpler and faster.

### Database
- Decision: Single Room database (`ParanoidDatabase`) at the app level, with per-mini-app DAOs. NetMap gets `RecordingDao` and `MeasurementDao`.
- Why: Room databases are expensive to create; sharing one instance avoids per-app overhead while keeping DAOs isolated.
- Alternative: Per-app database -- simpler isolation but wasteful.

### Cell Measurement Storage
- Decision: Store cell measurements as a JSON string column (`cellsJson`) in the measurements table rather than a separate table with foreign keys.
- Why: A single measurement can have 5-10 cells (serving + neighbors). A join table adds complexity for data that is always read/written together. JSON column is simpler and Room TypeConverters handle serialization.

### MapLibre Tile Source
- Decision: Use Carto Dark Matter basemap (free, no API key, dark theme matches app).
- Why: Aligns with the app's dark theme. OpenStreetMap Carto is light-themed and would clash. No API key means zero configuration.

### Service Architecture
- Decision: `RecordingService` is a bound+started foreground service. Activity binds for live UI updates; service continues when unbound.
- Why: Binding gives the Activity direct access to the measurement Flow without polling. Started mode ensures recording continues in background.

### Dependency Injection
- Decision: Manual factory pattern. `NetMapActivity.onCreate()` creates the database, DAOs, and sources. The service receives the recording ID via Intent extras and constructs its own data sources. No fragments — map and UI live directly in the Activity.
- Why: Matches the "no DI framework" constraint. The dependency graph is small enough for manual wiring.

### Navigation Between Screens
- Decision: NetMapActivity has a toolbar with an overflow menu containing "Recordings" and "About". RecordingsActivity has a simple back arrow. RecordingDetailActivity has back + export menu.
- Why: Minimal UI surface. Three screens don't warrant a bottom nav bar. Toolbar menu is the standard Android pattern for secondary navigation.

## Risks / Trade-offs
- MapLibre adds ~5 MB to APK size (arm64). This will likely push NetMap's contribution past the 8 MB target from functionality.md. Accepted trade-off — MapLibre is essential and there is no lighter alternative for offline-capable vector maps.
- Converting the hub to native means the WebView and `allowFileAccessFromFileURLs` workaround can be removed entirely.
- `TelephonyManager.allCellInfo` requires `ACCESS_FINE_LOCATION` -- users who deny location cannot use the app at all. Mitigated by clear permission rationale.

## Open Questions (deferred to v1.1)
1. Offline tile packs -- download visible region for offline use?
2. Auto-resume recording after device reboot?
3. Auto-delete old recordings after N days?
