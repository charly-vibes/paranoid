package dev.charly.paranoid.apps.netdiag.collect

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import androidx.annotation.RequiresApi
import dev.charly.paranoid.apps.netdiag.data.IpConfig
import dev.charly.paranoid.apps.netdiag.data.Measured
import java.net.Inet4Address
import java.net.Inet6Address

class IpConfigCollector {

    suspend fun collect(
        linkProperties: LinkProperties?,
        connectivityManager: ConnectivityManager,
    ): IpConfig {
        if (linkProperties == null) return defaultIpConfig()

        val ipv4Addresses = mutableListOf<String>()
        val ipv6Addresses = mutableListOf<String>()
        var subnetMaskBits: Int? = null

        for (linkAddress in linkProperties.linkAddresses) {
            val addr = linkAddress.address
            when (addr) {
                is Inet4Address -> {
                    ipv4Addresses.add(addr.hostAddress ?: continue)
                    if (subnetMaskBits == null) {
                        subnetMaskBits = linkAddress.prefixLength
                    }
                }
                is Inet6Address -> {
                    ipv6Addresses.add(addr.hostAddress ?: continue)
                }
            }
        }

        var gatewayIpv4: String? = null
        var gatewayIpv6: String? = null
        for (route in linkProperties.routes) {
            if (!route.isDefaultRoute) continue
            when (route.gateway) {
                is Inet4Address -> gatewayIpv4 = route.gateway?.hostAddress
                is Inet6Address -> gatewayIpv6 = route.gateway?.hostAddress
            }
        }

        val mtuValue = linkProperties.mtu.takeIf { it > 0 }

        val nat64Prefix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            extractNat64Prefix(linkProperties)
        } else {
            null
        }

        val proxy = linkProperties.httpProxy
        val httpProxyHost = proxy?.host
        val httpProxyPort = proxy?.port?.takeIf { it > 0 }
        val httpProxyPacUrl = proxy?.pacFileUrl?.toString()?.takeIf { it.isNotBlank() }

        val isPrivateDnsActive: Boolean
        val privateDnsServerName: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isPrivateDnsActive = linkProperties.isPrivateDnsActive
            privateDnsServerName = linkProperties.privateDnsServerName
        } else {
            isPrivateDnsActive = false
            privateDnsServerName = null
        }

        val isRfc1918 = ipv4Addresses.any { isRfc1918Address(it) }
        val isApipa = ipv4Addresses.any { it.startsWith("169.254.") }
        val hasIpv6 = ipv6Addresses.isNotEmpty()

        return IpConfig(
            interfaceName = linkProperties.interfaceName,
            ipv4Addresses = ipv4Addresses,
            ipv6Addresses = ipv6Addresses,
            gatewayIpv4 = gatewayIpv4,
            gatewayIpv6 = gatewayIpv6,
            mtu = Measured(
                value = mtuValue,
                confidence = if (mtuValue != null) 1.0f else 0.0f,
                source = "LinkProperties",
            ),
            nat64Prefix = nat64Prefix,
            httpProxyHost = httpProxyHost,
            httpProxyPort = httpProxyPort,
            httpProxyPacUrl = httpProxyPacUrl,
            isPrivateDnsActive = isPrivateDnsActive,
            privateDnsServerName = privateDnsServerName,
            isRfc1918 = isRfc1918,
            isApipa = isApipa,
            hasIpv6 = hasIpv6,
            subnetMaskBits = subnetMaskBits,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun extractNat64Prefix(linkProperties: LinkProperties): String? {
        return linkProperties.nat64Prefix?.toString()
    }

    private fun defaultIpConfig(): IpConfig = IpConfig(
        interfaceName = null,
        ipv4Addresses = emptyList(),
        ipv6Addresses = emptyList(),
        gatewayIpv4 = null,
        gatewayIpv6 = null,
        mtu = Measured(value = null, confidence = 0.0f, source = "LinkProperties"),
        nat64Prefix = null,
        httpProxyHost = null,
        httpProxyPort = null,
        httpProxyPacUrl = null,
        isPrivateDnsActive = false,
        privateDnsServerName = null,
        isRfc1918 = false,
        isApipa = false,
        hasIpv6 = false,
        subnetMaskBits = null,
    )

    companion object {
        private fun isRfc1918Address(address: String): Boolean {
            return address.startsWith("10.") ||
                address.startsWith("192.168.") ||
                isRfc1918_172Block(address)
        }

        private fun isRfc1918_172Block(address: String): Boolean {
            if (!address.startsWith("172.")) return false
            val secondOctet = address.substringAfter("172.")
                .substringBefore(".")
                .toIntOrNull() ?: return false
            return secondOctet in 16..31
        }
    }
}
