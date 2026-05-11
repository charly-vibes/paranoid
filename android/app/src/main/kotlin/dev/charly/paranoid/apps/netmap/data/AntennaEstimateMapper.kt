// NO-NETWORK INVARIANT (mapping is pure)

package dev.charly.paranoid.apps.netmap.data

import dev.charly.paranoid.apps.netmap.model.AntennaEstimate
import dev.charly.paranoid.apps.netmap.model.CellTech
import dev.charly.paranoid.apps.netmap.model.DataState
import dev.charly.paranoid.apps.netmap.model.GeoPoint
import dev.charly.paranoid.apps.netmap.model.Measurement
import dev.charly.paranoid.apps.netmap.model.NetworkType
import dev.charly.paranoid.apps.netmap.model.SignalLevel

/**
 * Map a persisted [MeasurementEntity] back into the domain [Measurement]
 * shape consumed by [dev.charly.paranoid.apps.netmap.estimate.AntennaEstimator].
 */
internal fun MeasurementEntity.toDomain(): Measurement = Measurement(
    id = id,
    recordingId = recordingId,
    timestamp = timestamp,
    location = GeoPoint(lat, lng),
    gpsAccuracyM = accuracyM,
    gpsSpeedKmh = speedKmh,
    gpsBearing = bearing,
    gpsAltitude = altitude,
    cells = CellsJsonConverter.fromJson(cellsJson),
    networkType = runCatching { NetworkType.valueOf(networkType) }
        .getOrDefault(NetworkType.NONE),
    dataState = runCatching { DataState.valueOf(dataState) }
        .getOrDefault(DataState.DISCONNECTED)
)

internal fun AntennaEstimate.toEntity(): AntennaEstimateEntity = AntennaEstimateEntity(
    recordingId = recordingId,
    cellKey = cellKey,
    technology = technology.name,
    lat = location.lat,
    lng = location.lng,
    radiusM = radiusM,
    sampleCount = sampleCount,
    strongestSignal = strongestSignal.name,
    isPciOnly = isPciOnly
)

internal fun AntennaEstimateEntity.toDomain(): AntennaEstimate = AntennaEstimate(
    recordingId = recordingId,
    cellKey = cellKey,
    technology = runCatching { CellTech.valueOf(technology) }.getOrDefault(CellTech.UNKNOWN),
    location = GeoPoint(lat, lng),
    radiusM = radiusM,
    sampleCount = sampleCount,
    strongestSignal = runCatching { SignalLevel.valueOf(strongestSignal) }
        .getOrDefault(SignalLevel.NONE),
    isPciOnly = isPciOnly
)
