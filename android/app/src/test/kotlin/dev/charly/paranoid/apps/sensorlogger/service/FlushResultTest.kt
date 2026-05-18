package dev.charly.paranoid.apps.sensorlogger.service

import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertTrue
import org.junit.Test

class FlushResultTest {

    @Test
    fun `FlushResult Success represents a successful flush`() {
        val result: FlushResult = FlushResult.Success
        assertTrue(result is FlushResult.Success)
    }

    @Test
    fun `FlushResult DiskFull wraps the exception`() {
        val ex = SQLiteFullException()
        val result: FlushResult = FlushResult.DiskFull(ex)
        assertTrue(result is FlushResult.DiskFull)
    }
}
