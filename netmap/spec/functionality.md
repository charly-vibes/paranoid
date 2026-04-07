# NetMap Android — Technical Specification

**Version:** 1.0
**Target:** Android 8.0+ (API 26+)
**Language:** Kotlin 1.9+
**UI Framework:** Jetpack Compose
**Build System:** Gradle (Kotlin DSL)

---

## 1. Overview

NetMap is a native Android application that maps cellular network connectivity along user-traveled routes. It records GPS coordinates synchronized with real-time radio measurements (signal strength, technology, cell identity) and visualizes the data on an interactive map. Recordings can be exported as GeoJSON/CSV for offline analysis.

### 1.1 Core Goals
- Continuous high-accuracy GPS tracking with low battery impact
- Real radio measurements from `TelephonyManager` (RSRP, RSRQ, RSSI, SINR)
- Offline-first: works without internet, syncs map tiles on demand
- Background recording survives screen-off and app-switching
- Export to standard formats (GeoJSON, CSV, KML)

### 1.2 Non-Goals (v1)
- iOS support (Apple restricts carrier APIs)
- Multi-carrier scanning from a single device (hardware-limited)
- Cloud sync / multi-user backend
- Speed test against external servers (deferred to v2)

---

## 2. Architecture

### 2.1 Pattern
**MVVM + Clean Architecture** with three layers:

```
┌─────────────────────────────────────┐
│  UI Layer (Jetpack Compose)         │
│  - Screens, ViewModels, Theme       │
├─────────────────────────────────────┤
│  Domain Layer                       │
│  - UseCases, Models, Repositories   │
├─────────────────────────────────────┤
│  Data Layer                         │
│  - Room DB, TelephonyDataSource,    │
│    LocationDataSource, Exporters    │
└─────────────────────────────────────┘
```

### 2.2 Key Libraries

| Purpose | Library | Version |
|---|---|---|
| UI | Jetpack Compose BOM | 2024.09.00 |
| Maps | MapLibre Native Android | 11.0.0 |
| DI | Hilt | 2.51 |
| Database | Room | 2.6.1 |
| Async | Kotlin Coroutines + Flow | 1.8.0 |
| Location | Google Play Services Location | 21.3.0 |
| Permissions | Accompanist Permissions | 0.34.0 |
| Serialization | kotlinx.serialization | 1.6.3 |
| Logging | Timber | 5.0.1 |

### 2.3 Module Structure

```
app/
├── di/                       # Hilt modules
├── ui/
│   ├── theme/                # Colors, Typography, Shapes
│   ├── map/                  # MapScreen + ViewModel
│   ├── recordings/           # RecordingsList + Detail
│   ├── settings/             # SettingsScreen
│   └── components/           # Reusable composables
├── domain/
│   ├── model/                # Measurement, Recording, CellInfo
│   ├── repository/           # Interfaces
│   └── usecase/              # StartRecordingUseCase, etc.
├── data/
│   ├── local/
│   │   ├── db/               # Room DAOs, Entities
│   │   └── prefs/            # DataStore preferences
│   ├── telephony/            # TelephonyDataSource
│   ├── location/             # FusedLocationDataSource
│   ├── export/               # GeoJsonExporter, CsvExporter
│   └── repository/           # Repository implementations
├── service/
│   └── RecordingService.kt   # Foreground service
└── MainActivity.kt
```

---

## 3. Permissions

### 3.1 Manifest Declarations

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 3.2 Runtime Request Order
1. `ACCESS_FINE_LOCATION` (mandatory — block app if denied)
2. `READ_PHONE_STATE` (mandatory for radio metrics)
3. `POST_NOTIFICATIONS` (Android 13+, for foreground service notification)
4. `ACCESS_BACKGROUND_LOCATION` (only when user enables background recording)

### 3.3 Rationale UX
Show a pre-permission dialog explaining each permission with a concrete reason ("We need location to map signal strength to coordinates"). Use Accompanist Permissions to handle rationale + denied states.

---

## 4. Data Model

### 4.1 Domain Models

```kotlin
data class Recording(
    val id: String,                    // UUID
    val name: String,                  // user-provided or auto "Trip 2026-04-07 14:32"
    val startedAt: Instant,
    val endedAt: Instant?,
    val measurementCount: Int,
    val distanceMeters: Double,
    val carrier: String?,              // "Telcel", "AT&T MX", etc.
    val notes: String?
)

data class Measurement(
    val id: Long,                      // auto-increment
    val recordingId: String,
    val timestamp: Instant,
    val location: GeoPoint,
    val gpsAccuracyM: Float,
    val gpsSpeedKmh: Float?,
    val gpsBearing: Float?,
    val gpsAltitude: Double?,
    val cells: List<CellMeasurement>,  // serving + neighbors
    val networkType: NetworkType,      // LTE, NR, UMTS, GSM, NONE
    val dataState: DataState           // CONNECTED, DISCONNECTED, SUSPENDED
)

data class CellMeasurement(
    val isServing: Boolean,
    val technology: CellTech,          // GSM, WCDMA, LTE, NR
    val mcc: Int?,                     // Mobile Country Code
    val mnc: Int?,                     // Mobile Network Code
    val cellId: Long?,                 // CI / NCI
    val tac: Int?,                     // Tracking Area Code (LTE/NR)
    val pci: Int?,                     // Physical Cell ID
    val earfcn: Int?,                  // EARFCN (LTE) / NRARFCN (NR)
    val band: String?,                 // "B28", "n78", etc.
    // Signal metrics
    val rssi: Int?,                    // dBm
    val rsrp: Int?,                    // dBm (LTE/NR)
    val rsrq: Int?,                    // dB (LTE/NR)
    val rssnr: Int?,                   // dB (LTE)
    val sinr: Int?,                    // dB (NR)
    val cqi: Int?,                     // Channel Quality Indicator
    val asuLevel: Int?,                // 0-99
    val signalLevel: SignalLevel       // computed: NONE..EXCELLENT
)

data class GeoPoint(val lat: Double, val lng: Double)

enum class CellTech { GSM, WCDMA, LTE, NR, CDMA, UNKNOWN }
enum class NetworkType { GSM, GPRS, EDGE, UMTS, HSPA, HSPA_PLUS, LTE, LTE_CA, NR_NSA, NR_SA, NONE }
enum class DataState { CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED }
enum class SignalLevel { NONE, POOR, FAIR, GOOD, EXCELLENT }
```

### 4.2 Room Schema

```kotlin
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val carrier: String?,
    val notes: String?
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(
        entity = RecordingEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recordingId"), Index("timestamp")]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val speedKmh: Float?,
    val bearing: Float?,
    val altitude: Double?,
    val networkType: String,
    val dataState: String,
    val cellsJson: String   // serialized List<CellMeasurement>
)
```

### 4.3 DAO

```kotlin
@Dao
interface MeasurementDao {
    @Insert
    suspend fun insert(measurement: MeasurementEntity): Long

    @Insert
    suspend fun insertBatch(measurements: List<MeasurementEntity>)

    @Query("SELECT * FROM measurements WHERE recordingId = :id ORDER BY timestamp ASC")
    fun observeByRecording(id: String): Flow<List<MeasurementEntity>>

    @Query("SELECT COUNT(*) FROM measurements WHERE recordingId = :id")
    suspend fun countForRecording(id: String): Int

    @Query("DELETE FROM measurements WHERE recordingId = :id")
    suspend fun deleteByRecording(id: String)
}
```

---

## 5. Telephony Data Source

### 5.1 Responsibilities
- Read serving + neighbor cells via `TelephonyManager.allCellInfo`
- Listen to signal strength changes via `TelephonyCallback` (API 31+) or `PhoneStateListener` (older)
- Map raw `CellInfo` subclasses to unified `CellMeasurement` model
- Detect carrier name and network operator

### 5.2 Implementation Sketch

```kotlin
@Singleton
class TelephonyDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun snapshot(): TelephonySnapshot = withContext(Dispatchers.IO) {
        val cellInfos = telephonyManager.allCellInfo ?: emptyList()
        val cells = cellInfos.mapNotNull { mapCellInfo(it) }
        TelephonySnapshot(
            cells = cells,
            networkType = mapNetworkType(telephonyManager.dataNetworkType),
            carrierName = telephonyManager.networkOperatorName,
            mccMnc = telephonyManager.networkOperator
        )
    }

    private fun mapCellInfo(info: CellInfo): CellMeasurement? = when (info) {
        is CellInfoLte -> mapLte(info)
        is CellInfoNr -> mapNr(info)
        is CellInfoWcdma -> mapWcdma(info)
        is CellInfoGsm -> mapGsm(info)
        else -> null
    }

    private fun mapLte(info: CellInfoLte): CellMeasurement {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        return CellMeasurement(
            isServing = info.isRegistered,
            technology = CellTech.LTE,
            mcc = id.mccString?.toIntOrNull(),
            mnc = id.mncString?.toIntOrNull(),
            cellId = id.ci.takeIf { it != Int.MAX_VALUE }?.toLong(),
            tac = id.tac.takeIf { it != Int.MAX_VALUE },
            pci = id.pci.takeIf { it != Int.MAX_VALUE },
            earfcn = id.earfcn.takeIf { it != Int.MAX_VALUE },
            band = id.bands.firstOrNull()?.toString(),
            rssi = ss.rssi.takeIf { it != Int.MAX_VALUE },
            rsrp = ss.rsrp.takeIf { it != Int.MAX_VALUE },
            rsrq = ss.rsrq.takeIf { it != Int.MAX_VALUE },
            rssnr = ss.rssnr.takeIf { it != Int.MAX_VALUE },
            sinr = null,
            cqi = ss.cqi.takeIf { it != Int.MAX_VALUE },
            asuLevel = ss.asuLevel,
            signalLevel = computeLevel(ss.rsrp)
        )
    }

    // mapNr, mapWcdma, mapGsm follow same pattern
}
```

### 5.3 Signal Level Thresholds (LTE RSRP, dBm)

| Level | Range |
|---|---|
| EXCELLENT | ≥ -85 |
| GOOD | -85 to -95 |
| FAIR | -95 to -105 |
| POOR | -105 to -115 |
| NONE | < -115 |

NR (5G) RSRP uses similar thresholds; WCDMA uses RSCP; GSM uses RSSI.

---

## 6. Location Data Source

### 6.1 Strategy
Use **Fused Location Provider** (Google Play Services) with custom `LocationRequest`:

```kotlin
val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
    .setMinUpdateIntervalMillis(1000L)
    .setMinUpdateDistanceMeters(5f)
    .setWaitForAccurateLocation(true)
    .build()
```

### 6.2 Flow Wrapper

```kotlin
@Singleton
class LocationDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
```

---

## 7. Recording Service (Foreground)

### 7.1 Why a Service
Recording must continue when the screen is off and during long drives. Android 14+ requires `FOREGROUND_SERVICE_LOCATION` type and a persistent notification.

### 7.2 Implementation

```kotlin
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var locationDs: LocationDataSource
    @Inject lateinit var telephonyDs: TelephonyDataSource
    @Inject lateinit var repo: MeasurementRepository

    private val buffer = mutableListOf<Measurement>()
    private var recordingId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIF_ID, buildNotification())

        recordingId = intent?.getStringExtra(EXTRA_RECORDING_ID) ?: return START_NOT_STICKY

        lifecycleScope.launch {
            locationDs.locationUpdates()
                .sample(2000)  // throttle to one measurement every 2s max
                .collect { location ->
                    val telSnapshot = telephonyDs.snapshot()
                    val measurement = buildMeasurement(location, telSnapshot)
                    buffer.add(measurement)
                    if (buffer.size >= BATCH_SIZE) flushBuffer()
                    updateNotification(buffer.size)
                }
        }
        return START_STICKY
    }

    private suspend fun flushBuffer() {
        repo.saveBatch(buffer.toList())
        buffer.clear()
    }

    override fun onDestroy() {
        runBlocking { flushBuffer() }
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
        const val BATCH_SIZE = 20
        const val EXTRA_RECORDING_ID = "recording_id"
    }
}
```

### 7.3 Notification
Persistent notification shows: recording duration, measurement count, current signal level. Tap opens the live map; long-press reveals "Stop" action.

---

## 8. UI Screens

### 8.1 Screen Inventory

| Screen | Purpose |
|---|---|
| `MapScreen` | Live map, recording controls, current signal HUD |
| `RecordingsListScreen` | List of saved recordings with summary stats |
| `RecordingDetailScreen` | Detailed view of one recording, segment colors, charts |
| `SettingsScreen` | Sample rate, distance threshold, color scheme, units |
| `ExportScreen` | Choose format, share via Android share sheet |
| `OnboardingScreen` | Permission rationale + first-run tour |

### 8.2 MapScreen Composition

```kotlin
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            measurements = state.measurements,
            currentLocation = state.currentLocation,
            colorScheme = state.colorScheme
        )
        TopBar(
            carrier = state.carrierName,
            networkType = state.networkType,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        SignalHud(
            current = state.currentSignal,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        RecordingControls(
            isRecording = state.isRecording,
            duration = state.duration,
            count = state.measurementCount,
            onStart = viewModel::startRecording,
            onStop = viewModel::stopRecording,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

### 8.3 Map Rendering with MapLibre
- Use MapLibre Native (open-source MapBox fork, no API key required)
- Tile source: OpenStreetMap or Carto Dark (configurable)
- Render measurement track as a `LineLayer` with data-driven color expression based on `signalLevel` property
- Cluster markers at low zoom levels using `SymbolLayer` with cluster expressions
- Offline tile pack: download visible region for offline use

### 8.4 Theming
- Material 3 dynamic color (Android 12+) with dark theme as default
- Custom signal palette: green / cyan / amber / red / gray
- Typography: Inter for UI, JetBrains Mono for numeric readouts

---

## 9. Export

### 9.1 Supported Formats

| Format | Use case |
|---|---|
| GeoJSON | GIS tools (QGIS, Mapbox Studio) |
| CSV | Spreadsheet analysis (Excel, Pandas) |
| KML | Google Earth |
| GPX | Route tools (Strava, OsmAnd) |

### 9.2 GeoJsonExporter

```kotlin
class GeoJsonExporter @Inject constructor(
    private val json: Json
) {
    fun export(recording: Recording, measurements: List<Measurement>): String {
        val features = measurements.map { m ->
            buildJsonObject {
                put("type", "Feature")
                put("geometry", buildJsonObject {
                    put("type", "Point")
                    put("coordinates", buildJsonArray {
                        add(m.location.lng)
                        add(m.location.lat)
                    })
                })
                put("properties", buildJsonObject {
                    put("timestamp", m.timestamp.toString())
                    put("accuracy_m", m.gpsAccuracyM)
                    put("speed_kmh", m.gpsSpeedKmh)
                    put("network_type", m.networkType.name)
                    val serving = m.cells.firstOrNull { it.isServing }
                    put("rsrp", serving?.rsrp)
                    put("rsrq", serving?.rsrq)
                    put("cell_id", serving?.cellId)
                    put("pci", serving?.pci)
                    put("signal_level", serving?.signalLevel?.name)
                })
            }
        }
        return json.encodeToString(buildJsonObject {
            put("type", "FeatureCollection")
            put("name", recording.name)
            put("features", JsonArray(features))
        })
    }
}
```

### 9.3 Sharing
Use `FileProvider` + `ACTION_SEND` intent to share via any installed app (Drive, Gmail, WhatsApp, etc.).

---

## 10. Settings & Preferences

Stored via **DataStore Preferences**:

| Key | Type | Default | Description |
|---|---|---|---|
| `sample_interval_ms` | Long | 2000 | Min time between measurements |
| `min_distance_m` | Float | 5.0 | Min distance between measurements |
| `auto_pause` | Boolean | true | Pause recording when stationary |
| `keep_screen_on` | Boolean | false | During active recording |
| `map_style` | String | "dark" | dark / light / satellite |
| `units` | String | "metric" | metric / imperial |
| `color_blind_mode` | Boolean | false | Use shape encoding too |

---

## 11. Performance Targets

| Metric | Target |
|---|---|
| Cold start to map | < 1.5 s |
| Idle RAM | < 80 MB |
| Recording RAM | < 150 MB |
| Battery / hour recording | < 6% |
| APK size | < 15 MB (without offline tiles) |
| Measurement throughput | 30+/min sustained |

### 11.1 Optimizations
- Batch DB writes (every 20 measurements or 30 seconds)
- Use `Flow.sample()` to throttle high-frequency GPS updates
- Lazy-load map tiles, cache aggressively
- Avoid recomposition with `remember` and `derivedStateOf`
- Use Baseline Profiles for startup speed

---

## 12. Testing Strategy

### 12.1 Unit Tests
- All mappers in `TelephonyDataSource` (use fake `CellInfo` instances)
- `Exporter` outputs (snapshot tests with golden files)
- `SignalLevelCalculator` thresholds
- ViewModels with `TestDispatcher` and Turbine for Flow assertions

### 12.2 Instrumentation Tests
- Room DAO tests with in-memory database
- Service lifecycle with `ServiceTestRule`
- Compose UI tests for critical flows (start recording, view recording)

### 12.3 Manual QA Checklist
- Drive 30+ min with screen off — verify no data loss
- Force-stop and restart — verify recording resumes / saves
- Airplane mode mid-recording — verify graceful handling
- Permission denial paths — verify clear error messaging
- Test on low-end device (2GB RAM) — verify no OOM

---

## 13. Build & Release

### 13.1 Build Variants
- `debug` — verbose logging, debug symbols
- `release` — R8 enabled, ProGuard rules for Room/Hilt/Serialization

### 13.2 Signing
- Generate upload keystore, store securely
- Use Play App Signing for distribution

### 13.3 Distribution Path
1. **Internal testing track** — team only
2. **Closed beta** — 20-50 users via Play Console
3. **Open beta** — public opt-in
4. **Production** — staged rollout (10% → 50% → 100%)

---

## 14. Roadmap Beyond v1

| Version | Features |
|---|---|
| v1.1 | GPX export, custom map themes, cell tower overlay (OpenCelliD) |
| v1.2 | Active speed test (download/upload/ping) at intervals |
| v1.3 | Backend sync for crowdsourced coverage maps |
| v1.4 | Heatmap visualization, statistical reports |
| v2.0 | Multi-SIM support, automatic carrier detection per recording |

---

## 15. Open Questions

1. **Map provider** — MapLibre (free) vs Google Maps (better tiles, requires API key + billing)?
2. **Background recording UX** — Auto-resume after reboot, or require manual restart?
3. **Storage policy** — Auto-delete recordings older than N days, or unlimited?
4. **Privacy** — Should recordings be encrypted at rest? (Useful for journalists/researchers)
5. **Crowdsourcing** — Opt-in to share anonymized data with the community?

---

## 16. References

- [Android TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [CellInfo class hierarchy](https://developer.android.com/reference/android/telephony/CellInfo)
- [Foreground Services guide](https://developer.android.com/develop/background-work/services/foreground-services)
- [Fused Location Provider](https://developers.google.com/location-context/fused-location-provider)
- [MapLibre Native Android](https://github.com/maplibre/maplibre-native)
- [Jetpack Compose docs](https://developer.android.com/jetpack/compose)
