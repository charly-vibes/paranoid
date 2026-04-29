package dev.charly.paranoid

import android.app.Application
import android.os.Bundle
import dev.charly.paranoid.apps.usageaudit.UsageAuditRuntime

class ParanoidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val hook = UsageAuditRuntime.appOpenHook(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) {
                hook.onActivityResumed()
            }
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}
