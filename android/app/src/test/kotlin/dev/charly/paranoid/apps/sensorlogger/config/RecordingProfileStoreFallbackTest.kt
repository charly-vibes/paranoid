package dev.charly.paranoid.apps.sensorlogger.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingProfileStoreFallbackTest {

    @Test
    fun `IOException upstream emits default profile without propagating`() = runBlocking {
        val store = RecordingProfileStore(ErroringPreferencesDataStore())

        val emitted = store.flow.first()

        assertEquals(RecordingProfile.Default, emitted)
    }

    @Test
    fun `IOException upstream marks defaults-dialog flag as unseen`() = runBlocking {
        val store = RecordingProfileStore(ErroringPreferencesDataStore())

        val seen = store.hasSeenDefaultsDialog().first()

        assertEquals(false, seen)
    }
}
