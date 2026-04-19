package dev.charly.paranoid

import dev.charly.paranoid.apps.netdiag.NetDiagActivity
import dev.charly.paranoid.apps.netmap.NetMapActivity

object AppRegistry {
    val apps: List<AppEntry> = listOf(
        AppEntry(
            id = "netmap",
            name = "NetMap",
            description = "Map cellular network signal along your route",
            activityClass = NetMapActivity::class.java
        ),
        AppEntry(
            id = "netdiag",
            name = "NetDiag",
            description = "Compare network diagnostics between two devices",
            activityClass = NetDiagActivity::class.java
        )
    )
}
