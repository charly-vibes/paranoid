package dev.charly.paranoid.apps.sensorlogger.data

import dev.charly.paranoid.apps.sensorlogger.model.SensorEvent

fun SensorEvent.toEntity(): SensorEventEntity = SensorEventEntity(
    id = id,
    sessionId = sessionId,
    elapsedMs = elapsedMs,
    sensorType = sensorType.name,
    x = x,
    y = y,
    z = z,
    accuracy = accuracy,
)
