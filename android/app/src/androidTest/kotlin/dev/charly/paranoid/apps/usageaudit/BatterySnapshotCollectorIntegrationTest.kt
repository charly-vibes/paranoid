package dev.charly.paranoid.apps.usageaudit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatterySnapshotCollectorIntegrationTest {

    private lateinit var database: ParanoidDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ParanoidDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun collectorPersistsMappedBatterySnapshotThroughRepositorySeam() = runBlocking {
        val collector = BatterySnapshotCollector(
            signalProvider = {
                BatterySignal(
                    timestampMillis = 1_234L,
                    level = 55,
                    status = "discharging",
                    health = "good",
                    isCharging = false,
                )
            },
            repository = RoomBatterySnapshotRepository(database.batterySnapshotDao()),
        )

        collector.capture()
        delay(50)

        val stored = database.batterySnapshotDao().latestAtOrBefore(1_234L)
        assertEquals(55, stored?.batteryPercent)
        assertEquals("DISCHARGING", stored?.chargingState)
    }
}
