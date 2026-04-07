package dev.charly.paranoid

import android.app.Activity

data class AppEntry(
    val id: String,
    val name: String,
    val description: String,
    val activityClass: Class<out Activity>
)
