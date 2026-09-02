import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.rt993.firetvjellyfin"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rt993.firetvjellyfin"
        // 23 (Android 6.0) is the practical floor with today's AndroidX releases - see the
        // "minSdk" section of README.md. Confirmed to comfortably cover the actual target
        // device (Fire OS 7.7.1.5 / Android 9, API 28).
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.3"

        // Vector drawable gradients (aapt:attr fillColor) render natively from API 24; the
        // support library backports them down to minSdk 23.
        vectorDrawables.useSupportLibrary = true
    }

    // Gradle's implicit debug signing config auto-generates a fresh, per-machine keystore
    // (~/.android/debug.keystore) the first time it's needed - fine for a single dev machine, but
    // it means every CI runner (a fresh machine each run) mints its own distinct key, so a debug
    // APK built by CI has a different signature than one built locally. Android refuses to install
    // an update whose signature doesn't match what's already installed ("App not installed"), so
    // sideloaded builds and GitHub Release builds would permanently conflict with each other.
    // Pin a real, checked-in keystore instead so every build - local or CI - is signed identically.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Android TV D-pad navigation / browse UI
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)

    // Playback. media3-ui is used only for AspectRatioFrameLayout, wrapping a plain SurfaceView -
    // the custom playback screen (see PlaybackActivity) doesn't use PlayerView/its built-in
    // controller UI, just this one letterboxing helper.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Jellyfin server communication. jellyfin-core has built-in Android support (JellyfinOptions
    // .Builder.context) as of 1.6.x - the separate jellyfin-platform-android artifact is stuck at
    // 1.0.3 (matching only jellyfin-core:1.0.3) and is binary-incompatible with current releases.
    implementation(libs.jellyfin.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Poster/backdrop image loading for leanback card and details views
    implementation(libs.glide)
}
