package dev.charly.paranoid.apps.sensorlogger.ui

import dev.charly.paranoid.apps.sensorlogger.config.RecordingProfile
import dev.charly.paranoid.apps.sensorlogger.config.SensorCaptureSetting
import dev.charly.paranoid.apps.sensorlogger.config.SensorRateLevel
import dev.charly.paranoid.apps.sensorlogger.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGateTest {

    @Test
    fun `profile with every sensor OFF cannot start`() {
        val allOff = RecordingProfile(
            SensorType.values().associateWith { RecordingProfile.OffSetting }
        )
        assertFalse(canStartRecording(allOff))
    }

    @Test
    fun `profile with at least one enabled sensor at non-OFF rate can start`() {
        val enabledAccel = RecordingProfile(
            SensorType.values().associateWith { type ->
                if (type == SensorType.ACCELEROMETER) {
                    SensorCaptureSetting(true, SensorRateLevel.NORMAL, false)
                } else {
                    RecordingProfile.OffSetting
                }
            }
        )
        assertTrue(canStartRecording(enabledAccel))
    }

    @Test
    fun `profile with only a visualize-only sensor can start`() {
        // The service registers visualize-only sensors so the Start button
        // must allow this (the session will write zero rows but the live
        // graph will populate).
        val visualizeOnly = RecordingProfile(
            SensorType.values().associateWith { type ->
                if (type == SensorType.GYROSCOPE) {
                    SensorCaptureSetting(false, SensorRateLevel.GAME, true)
                } else {
                    RecordingProfile.OffSetting
                }
            }
        )
        assertTrue(canStartRecording(visualizeOnly))
    }

    @Test
    fun `sensor enabled but rate OFF does not count as startable`() {
        val enabledButRateOff = RecordingProfile(
            SensorType.values().associateWith { type ->
                if (type == SensorType.ACCELEROMETER) {
                    SensorCaptureSetting(true, SensorRateLevel.OFF, true)
                } else {
                    RecordingProfile.OffSetting
                }
            }
        )
        assertFalse(canStartRecording(enabledButRateOff))
    }

    @Test
    fun `default profile can start`() {
        assertTrue(canStartRecording(RecordingProfile.Default))
    }

    @Test
    fun `start gate hint string matches the spec`() {
        assertEquals("No sensors enabled — open Configure capture", START_GATE_HINT)
    }

    // ----- 6.2 RED: Live graph button gating -----

    @Test
    fun `live graph button is disabled when service is not recording`() {
        assertFalse(isLiveGraphButtonEnabled(isRecording = false))
    }

    @Test
    fun `live graph button is enabled when service is recording`() {
        assertTrue(isLiveGraphButtonEnabled(isRecording = true))
    }

    // ----- 6.3 RED: First-launch dialog gating -----

    @Test
    fun `defaults dialog is shown when never seen and not yet shown this session`() {
        assertTrue(
            shouldShowDefaultsDialog(
                hasSeenPersisted = false,
                alreadyShownInThisSession = false,
            )
        )
    }

    @Test
    fun `defaults dialog is suppressed once the persisted key flips to true`() {
        assertFalse(
            shouldShowDefaultsDialog(
                hasSeenPersisted = true,
                alreadyShownInThisSession = false,
            )
        )
    }

    @Test
    fun `defaults dialog does not re-appear within the same session after being shown once`() {
        // Models: user dismisses, Activity goes through onPause/onResume
        // before markDefaultsDialogSeen() has committed.
        assertFalse(
            shouldShowDefaultsDialog(
                hasSeenPersisted = false,
                alreadyShownInThisSession = true,
            )
        )
    }
}
