package dev.charly.paranoid.apps.sensorlogger.model

sealed class SensorCategory {
    object Motion : SensorCategory()
    object Orientation : SensorCategory()
    object Environment : SensorCategory()
}

enum class SensorType(val category: SensorCategory) {
    ACCELEROMETER(SensorCategory.Motion),
    GYROSCOPE(SensorCategory.Motion),
    LINEAR_ACCELERATION(SensorCategory.Motion),
    GRAVITY(SensorCategory.Motion),
    ROTATION_VECTOR(SensorCategory.Orientation),
    MAGNETIC_FIELD(SensorCategory.Orientation),
    PRESSURE(SensorCategory.Environment),
    LIGHT(SensorCategory.Environment),
    PROXIMITY(SensorCategory.Environment),
}

data class SensorSession(
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
) {
    val isIncomplete: Boolean get() = endedAt == null
    val durationMs: Long? get() = endedAt?.minus(startedAt)
}

data class SensorEvent(
    val id: Long = 0,
    val sessionId: Long,
    val elapsedMs: Long,
    val sensorType: SensorType,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int,
)
