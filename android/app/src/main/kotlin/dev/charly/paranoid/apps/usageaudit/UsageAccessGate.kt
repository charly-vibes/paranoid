package dev.charly.paranoid.apps.usageaudit

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

fun interface UsageAccessChecker {
    fun hasUsageAccess(): Boolean
}

class AndroidUsageAccessChecker(
    private val modeReader: () -> Int,
) : UsageAccessChecker {
    constructor(context: Context) : this(
        modeReader = {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName,
                )
            }
        }
    )

    override fun hasUsageAccess(): Boolean = modeReader() == AppOpsManager.MODE_ALLOWED
}

fun interface UsageAccessSettingsNavigator {
    fun open()
}

class AndroidUsageAccessSettingsNavigator(
    private val context: Context,
) : UsageAccessSettingsNavigator {
    override fun open() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

data class UsageAuditData(
    val today: DailyUsageSummary?,
    val lastNight: OvernightAudit?,
    val recentNights: List<OvernightAudit>,
    val recentDays: List<DailyUsageSummary> = emptyList(),
)

fun interface UsageAuditDataProvider {
    fun load(scope: kotlinx.coroutines.CoroutineScope, callback: (UsageAuditData) -> Unit)
}

object UsageAuditDependencies {
    @Volatile
    var usageAccessCheckerFactory: (Context) -> UsageAccessChecker = { context ->
        AndroidUsageAccessChecker(context)
    }

    @Volatile
    var settingsNavigatorFactory: (Context) -> UsageAccessSettingsNavigator = { context ->
        AndroidUsageAccessSettingsNavigator(context)
    }

    @Volatile
    var dataProviderFactory: (Context) -> UsageAuditDataProvider = { context ->
        AndroidUsageAuditDataProvider(context)
    }

    fun reset() {
        usageAccessCheckerFactory = { context -> AndroidUsageAccessChecker(context) }
        settingsNavigatorFactory = { context -> AndroidUsageAccessSettingsNavigator(context) }
        dataProviderFactory = { context -> AndroidUsageAuditDataProvider(context) }
    }
}
