package dev.charly.paranoid.apps.sensorlogger.service

import dev.charly.paranoid.apps.sensorlogger.model.SensorEvent

class SensorEventBuffer {
    private val items = mutableListOf<SensorEvent>()

    @Synchronized
    fun append(event: SensorEvent) {
        items.add(event)
    }

    @Synchronized
    fun flush(): List<SensorEvent> {
        if (items.isEmpty()) return emptyList()
        val batch = items.toList()
        items.clear()
        return batch
    }

    @Synchronized
    fun size(): Int = items.size
}
