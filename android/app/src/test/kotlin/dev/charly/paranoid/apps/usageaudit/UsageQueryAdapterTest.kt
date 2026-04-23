package dev.charly.paranoid.apps.usageaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventIntervalMapperTest {

    @Test
    fun `maps normalized usage intervals into app usage slices`() {
        val slices = UsageEventIntervalMapper.toSlices(
            intervals = listOf(
                UsageIntervalRecord(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    startMillis = 1_000L,
                    endMillis = 4_000L,
                ),
            ),
            windowStartMillis = 0L,
            windowEndMillis = 10_000L,
        )

        assertEquals(1, slices.size)
        assertEquals("dev.example.reader", slices.single().packageName)
        assertEquals("Reader", slices.single().appLabel)
        assertEquals(3_000L, slices.single().foregroundDurationMillis)
    }

    @Test
    fun `uses package name when app label is missing`() {
        val slices = UsageEventIntervalMapper.toSlices(
            intervals = listOf(
                UsageIntervalRecord(
                    packageName = "dev.example.reader",
                    appLabel = "",
                    startMillis = 1_000L,
                    endMillis = 4_000L,
                ),
            ),
            windowStartMillis = 0L,
            windowEndMillis = 10_000L,
        )

        assertEquals("dev.example.reader", slices.single().appLabel)
    }

    @Test
    fun `filters noisy system packages before building slices`() {
        val slices = UsageEventIntervalMapper.toSlices(
            intervals = listOf(
                UsageIntervalRecord(
                    packageName = "com.android.systemui",
                    appLabel = "System UI",
                    startMillis = 1_000L,
                    endMillis = 2_000L,
                ),
                UsageIntervalRecord(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    startMillis = 2_000L,
                    endMillis = 4_000L,
                ),
            ),
            windowStartMillis = 0L,
            windowEndMillis = 10_000L,
        )

        assertEquals(listOf("dev.example.reader"), slices.map { it.packageName })
    }
}

class UsageQueryAdapterTest {

    @Test
    fun `today query requests the provided window from the reader`() {
        val reader = FakeUsageIntervalReader(
            result = listOf(
                UsageIntervalRecord(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    startMillis = 1_000L,
                    endMillis = 2_000L,
                ),
            ),
        )
        val adapter = UsageQueryAdapter(reader)

        val slices = adapter.queryToday(windowStartMillis = 1_000L, windowEndMillis = 5_000L)

        assertEquals(1_000L to 5_000L, reader.requestedWindow)
        assertEquals(1, slices.size)
    }

    @Test
    fun `overnight query requests the provided window from the reader`() {
        val reader = FakeUsageIntervalReader(
            result = listOf(
                UsageIntervalRecord(
                    packageName = "dev.example.reader",
                    appLabel = "Reader",
                    startMillis = 1_000L,
                    endMillis = 2_000L,
                ),
            ),
        )
        val adapter = UsageQueryAdapter(reader)

        adapter.queryOvernight(windowStartMillis = 22_000L, windowEndMillis = 31_000L)

        assertEquals(22_000L to 31_000L, reader.requestedWindow)
    }

    @Test
    fun `overnight query returns slices overlapping the requested window`() {
        val adapter = UsageQueryAdapter(
            reader = FakeUsageIntervalReader(
                result = listOf(
                    UsageIntervalRecord(
                        packageName = "dev.example.reader",
                        appLabel = "Reader",
                        startMillis = 21_500L,
                        endMillis = 22_500L,
                    ),
                    UsageIntervalRecord(
                        packageName = "dev.example.chat",
                        appLabel = "Chat",
                        startMillis = 24_000L,
                        endMillis = 25_500L,
                    ),
                ),
            ),
        )

        val slices = adapter.queryOvernight(windowStartMillis = 22_000L, windowEndMillis = 26_000L)

        assertEquals(listOf("dev.example.reader", "dev.example.chat"), slices.map { it.packageName })
        assertEquals(listOf(500L, 1_500L), slices.map { it.foregroundDurationMillis })
    }
}

private class FakeUsageIntervalReader(
    private val result: List<UsageIntervalRecord>,
) : UsageIntervalReader {
    var requestedWindow: Pair<Long, Long>? = null

    override fun readIntervals(windowStartMillis: Long, windowEndMillis: Long): List<UsageIntervalRecord> {
        requestedWindow = windowStartMillis to windowEndMillis
        return result
    }
}
