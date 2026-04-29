# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# Keep our models used in JSON serialization
-keep class dev.charly.paranoid.apps.netmap.model.** { *; }
-keep class dev.charly.paranoid.apps.netmap.data.** { *; }
-keep class dev.charly.paranoid.apps.netdiag.data.** { *; }
-keep class dev.charly.paranoid.apps.usageaudit.** { *; }
