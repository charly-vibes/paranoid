package dev.charly.paranoid.apps.sensorlogger.service

import android.database.sqlite.SQLiteFullException

sealed class FlushResult {
    object Success : FlushResult()
    data class DiskFull(val cause: SQLiteFullException) : FlushResult()
}
