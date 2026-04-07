package dev.charly.paranoid.apps.netmap.data

import android.telephony.TelephonyManager
import dev.charly.paranoid.apps.netmap.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class TelephonySourceTest {

    @Test
    fun `mapNetworkType LTE`() {
        assertEquals(NetworkType.LTE, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_LTE))
    }

    @Test
    fun `mapNetworkType NR`() {
        assertEquals(NetworkType.NR_SA, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_NR))
    }

    @Test
    fun `mapNetworkType UMTS`() {
        assertEquals(NetworkType.UMTS, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_UMTS))
    }

    @Test
    fun `mapNetworkType EDGE`() {
        assertEquals(NetworkType.EDGE, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_EDGE))
    }

    @Test
    fun `mapNetworkType GPRS`() {
        assertEquals(NetworkType.GPRS, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_GPRS))
    }

    @Test
    fun `mapNetworkType HSPA`() {
        assertEquals(NetworkType.HSPA, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_HSPA))
    }

    @Test
    fun `mapNetworkType HSPAP`() {
        assertEquals(NetworkType.HSPA_PLUS, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_HSPAP))
    }

    @Test
    fun `mapNetworkType unknown returns NONE`() {
        assertEquals(NetworkType.NONE, TelephonySource.mapNetworkType(-1))
    }

    @Test
    fun `mapNetworkType GSM`() {
        assertEquals(NetworkType.GSM, TelephonySource.mapNetworkType(TelephonyManager.NETWORK_TYPE_GSM))
    }
}
