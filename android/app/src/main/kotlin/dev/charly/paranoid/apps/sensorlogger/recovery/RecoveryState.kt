package dev.charly.paranoid.apps.sensorlogger.recovery

sealed class RecoveryState {
    object None : RecoveryState()
    data class Incomplete(val sessionIds: List<Long>) : RecoveryState()

    companion object {
        fun from(incompleteSessionIds: List<Long>): RecoveryState =
            if (incompleteSessionIds.isEmpty()) None
            else Incomplete(incompleteSessionIds)
    }
}
