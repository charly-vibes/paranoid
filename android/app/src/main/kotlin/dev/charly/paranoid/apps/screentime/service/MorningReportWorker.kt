package dev.charly.paranoid.apps.screentime.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.charly.paranoid.apps.netmap.data.ParanoidDatabase
import dev.charly.paranoid.apps.screentime.AppUsageTotal
import dev.charly.paranoid.apps.screentime.DayUsage
import dev.charly.paranoid.apps.screentime.MorningReport
import dev.charly.paranoid.apps.screentime.MorningReportSchedule
import dev.charly.paranoid.apps.screentime.ReportAggregator
import dev.charly.paranoid.apps.screentime.RetentionPolicy
import dev.charly.paranoid.apps.screentime.data.toDomain
import java.util.concurrent.TimeUnit

/**
 * Builds and posts the 08:00 morning report (yesterday, 7-day rolling average, month-to-date),
 * then re-enqueues itself for the next 08:00. Scheduled as a one-time job rather than a periodic
 * one so it stays aligned with the wall clock (see [MorningReportSchedule]).
 */
class MorningReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val dao = ParanoidDatabase.getInstance(applicationContext).screenTimeDao()

        // A 32-day window comfortably covers month-to-date and the 7-day average.
        val windowStart = now - 32L * MILLIS_PER_DAY
        val sessions = dao.sessionsOverlapping(windowStart, now)
            .map { it.toDomain(dao.intervalsForSession(it.id)) }

        val report = ReportAggregator.build(sessions, now)
        postReportNotification(report)

        // Prune sessions older than the retention window (CASCADE removes their intervals).
        dao.pruneSessionsEndedBefore(RetentionPolicy.cutoffMillis(now))

        // Re-enqueue for the next day's 08:00.
        MorningReportScheduler.enqueueNext(applicationContext, now)
        return Result.success()
    }

    private fun postReportNotification(report: MorningReport) {
        val manager = getNotificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screen time reports",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Daily morning screen-time summary" },
        )

        val body = buildString {
            appendLine("Yesterday: ${formatDuration(report.yesterday.totalForegroundMillis)}")
            appendLine(topApps(report.yesterday))
            appendLine()
            appendLine("7-day average: ${formatDuration(report.sevenDayAverageMillis)}/day")
            append("This month: ${formatDuration(report.monthToDate.totalForegroundMillis)}")
        }.trim()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Yesterday's screen time")
            .setContentText("${formatDuration(report.yesterday.totalForegroundMillis)} • tap for breakdown")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun topApps(day: DayUsage, limit: Int = 3): String {
        if (day.appsByForeground.isEmpty()) return "No app activity"
        return day.appsByForeground.take(limit).joinToString(", ") { usage ->
            "${resolveLabel(usage)} ${formatDuration(usage.foregroundMillis)}"
        }
    }

    private fun resolveLabel(usage: AppUsageTotal): String = try {
        val pm: PackageManager = applicationContext.packageManager
        val info = pm.getApplicationInfo(usage.packageName, 0)
        pm.getApplicationLabel(info).toString().ifBlank { usage.packageName }
    } catch (_: Exception) {
        usage.packageName
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun getNotificationManager(): NotificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "screentime_reports"
        const val NOTIFICATION_ID = 1006
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}

/** Enqueues the next [MorningReportWorker] run at the upcoming 08:00. */
object MorningReportScheduler {
    const val WORK_NAME = "screentime_morning_report"

    fun enqueueNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val delayMillis = MorningReportSchedule.nextReportDelayMillis(nowMillis)
        val request = OneTimeWorkRequestBuilder<MorningReportWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}
