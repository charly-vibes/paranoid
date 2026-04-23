package dev.charly.paranoid.apps.usageaudit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatterySnapshotDaoTest {

    private lateinit var database: ParanoidDatabase
    private lateinit var dao: BatterySnapshotDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ParanoidDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.batterySnapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistsAndReadsBatterySnapshotRecords() = runBlocking {
        val expected = BatterySnapshot(
            timestampMillis = 1_713_736_800_000L,
            batteryPercent = 78,
            chargingState = ChargingState.DISCHARGING,
            batteryStatus = "not_charging",
            batteryHealth = "good",
        )

        dao.insert(BatterySnapshotEntity.fromDomain(expected))

        val stored = dao.latestAtOrBefore(expected.timestampMillis)
        assertNotNull(stored)
        assertEquals(expected, stored?.toDomain())
    }
}
