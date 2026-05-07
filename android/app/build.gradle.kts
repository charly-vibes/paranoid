import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Derive versionName/versionCode from the most recent git tag (vX.Y.Z).
// Falls back to 0.0.0 / 1 when git or tags are unavailable
// (e.g., shallow clones, docker images without git).
data class AppVersion(val code: Int, val name: String)

fun resolveAppVersion(): AppVersion {
    val tag = runCatching {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
            workingDir = rootDir
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        stdout.toString().trim()
    }.getOrDefault("")

    val parts = tag.removePrefix("v").split(".")
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }

    val code = (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
    val name = if (tag.isEmpty()) "0.0.0" else "$major.$minor.$patch"
    return AppVersion(code, name)
}

val appVersion = resolveAppVersion()

android {
    namespace = "dev.charly.paranoid"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.charly.paranoid"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.code
        versionName = appVersion.name
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // JSON serialization (for cellsJson TypeConverter)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ICMP ping (NetDiag)
    implementation("com.marsounjan:icmp4a:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
