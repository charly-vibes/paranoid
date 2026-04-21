package dev.charly.paranoid.apps.netdiag.collect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpConfigCollectorRfc1918Test {

    private fun check(address: String): Boolean {
        val companionClass = Class.forName(
            "dev.charly.paranoid.apps.netdiag.collect.IpConfigCollector\$Companion"
        )
        val method = companionClass.getDeclaredMethod("isRfc1918Address", String::class.java)
        method.isAccessible = true
        val companion = IpConfigCollector::class.java
            .getDeclaredField("Companion")
            .get(null)
        return method.invoke(companion, address) as Boolean
    }

    @Test
    fun `10_x_x_x is RFC1918`() {
        assertTrue(check("10.0.0.1"))
        assertTrue(check("10.255.255.255"))
    }

    @Test
    fun `192_168_x_x is RFC1918`() {
        assertTrue(check("192.168.1.1"))
        assertTrue(check("192.168.0.100"))
    }

    @Test
    fun `172_16-31_x_x is RFC1918`() {
        assertTrue(check("172.16.0.1"))
        assertTrue(check("172.31.255.255"))
    }

    @Test
    fun `172_15_x_x is not RFC1918`() {
        assertFalse(check("172.15.0.1"))
    }

    @Test
    fun `172_32_x_x is not RFC1918`() {
        assertFalse(check("172.32.0.1"))
    }

    @Test
    fun `public IP is not RFC1918`() {
        assertFalse(check("8.8.8.8"))
        assertFalse(check("1.1.1.1"))
    }

    @Test
    fun `APIPA is not RFC1918`() {
        assertFalse(check("169.254.1.1"))
    }
}
