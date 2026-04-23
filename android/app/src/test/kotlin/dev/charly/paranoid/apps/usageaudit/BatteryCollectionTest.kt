package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySignalMapperTest {

    @Test
    fun `maps battery signal into battery snapshot`() {
        val snapshot = BatterySignalMapper.map(
            BatterySignal(
                timestampMillis = 1_000L,
                level = 42,
                status = "charging",
                health = "good",
                isCharging = true,
            ),
        )

        assertEquals(1_000L, snapshot.timestampMillis)
        assertEquals(42, snapshot.batteryPercent)
        assertEquals(ChargingState.CHARGING, snapshot.chargingState)
        assertEquals("charging", snapshot.batteryStatus)
        assertEquals("good", snapshot.batteryHealth)
    }
}

class BatterySnapshotCollectorTest {

    @Test
    fun `collector persists mapped battery snapshot through repository seam`() {
        val repository = FakeBatterySnapshotRepository()
        val collector = BatterySnapshotCollector(
            signalProvider = { BatterySignal(1_000L, 67, "discharging", "good", false) },
            repository = repository,
        )

        collector.capture()

        assertEquals(1, repository.saved.size)
        assertEquals(67, repository.saved.single().batteryPercent)
    }
}

class AppOpenBatterySnapshotHookTest {

    @Test
    fun `app open hook captures on activity resume`() {
        val collector = FakeBatterySnapshotCollector()
        val hook = AppOpenBatterySnapshotHook(collector)

        hook.onActivityResumed()

        assertEquals(1, collector.captureCalls)
    }
}

class PowerEventBatterySnapshotReceiverTest {

    @Test
    fun `power receiver captures on power connected and disconnected`() {
        val collector = FakeBatterySnapshotCollector()
        val receiver = PowerEventBatterySnapshotReceiver(collector)

        receiver.onReceive(action = "android.intent.action.ACTION_POWER_CONNECTED")
        receiver.onReceive(action = "android.intent.action.ACTION_POWER_DISCONNECTED")

        assertEquals(2, collector.captureCalls)
    }
}

class BootCompletedBatterySnapshotReceiverTest {

    @Test
    fun `boot receiver captures on boot completed`() {
        val collector = FakeBatterySnapshotCollector()
        val receiver = BootCompletedBatterySnapshotReceiver(collector)

        receiver.onReceive(action = "android.intent.action.BOOT_COMPLETED")

        assertEquals(1, collector.captureCalls)
    }

    @Test
    fun `boot receiver ignores unrelated actions`() {
        val collector = FakeBatterySnapshotCollector()
        val receiver = BootCompletedBatterySnapshotReceiver(collector)

        receiver.onReceive(action = "android.intent.action.TIME_SET")

        assertTrue(collector.captureCalls == 0)
    }
}

private class FakeBatterySnapshotRepository : BatterySnapshotRepository {
    val saved = mutableListOf<BatterySnapshot>()

    override fun save(snapshot: BatterySnapshot) {
        saved += snapshot
    }
}

private class FakeBatterySnapshotCollector : SnapshotCollector {
    var captureCalls: Int = 0

    override fun capture() {
        captureCalls += 1
    }
}
