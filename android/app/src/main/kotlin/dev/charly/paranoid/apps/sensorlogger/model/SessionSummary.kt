package dev.charly.paranoid.apps.sensorlogger.model

fun countEventsBySensor(events: List<SensorEvent>): Map<SensorType, Int> =
    events.groupingBy { it.sensorType }.eachCount()
