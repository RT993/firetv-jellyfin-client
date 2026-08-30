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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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

    // Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.leanback)
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
