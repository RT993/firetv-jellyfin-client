import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 76
        versionName = "0.8.16"

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
            // Reuses the same checked-in debug keystore rather than a dedicated release one - this
            // app is never published anywhere (see README), just sideloaded onto one household's
            // own Fire Sticks, so a separate release signing identity would add key-management
            // overhead for no real benefit. isDebuggable stays false here (the default for this
            // block, unlike "debug" below) - that's the actual point of building this variant:
            // debug builds carry real, measurable runtime overhead on Compose (no dead-code/method
            // optimization, extra non-skippable-composable bookkeeping, tooling hooks left active)
            // independent of anything our own code does, which is why this exists at all. Minify
            // stays off for now - R8 shrinking/obfuscation needs real proguard rules worked out
            // against reflection-heavy libraries (kotlinx.serialization models, Glide's generated
            // API) before it's safe to turn on blind.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // org.jellyfin.sdk:jellyfin-core only publishes a "debug" Android target variant
            // upstream - no matching "release" one exists, so without this, Gradle's variant
            // matching fails to resolve it at all for this build type ("Could not find
            // jellyfin-core-android"). This tells Gradle to fall back to a dependency's debug
            // variant when it has no release one - standard fix for exactly this class of error,
            // and it only affects which variant of this one third-party library gets linked, not
            // our own app's build type.
            matchingFallbacks += listOf("debug")
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    // AGP runs a "lint vital" pass automatically before packaging any release build, which pulls
    // in its own separate dependency tree for the lint tooling itself (groovy, kotlin-reflect,
    // error-prone, an old httpclient...) - unrelated to this app's own code or dependencies, and
    // not something assembleDebug ever needed. Off, since it's gating release packaging on its own
    // tooling resolving rather than on anything this project actually did wrong.
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Android TV D-pad navigation / browse UI (Library grid screen - Home and Details are Compose)
    implementation(libs.androidx.leanback)

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

    // Poster/backdrop image loading for leanback card and details views. Glide's own default
    // network stack is a plain HttpURLConnection-based loader, completely separate from the
    // OkHttp-backed Ktor engine the Jellyfin SDK already uses for every other API call (OkHttp is
    // already pulled in transitively via jellyfin-core - no new networking library here, just
    // wiring Glide to use the one already in the dependency graph) - see JellyfinTvApplication for
    // where this actually gets registered.
    implementation(libs.glide)
    implementation(libs.glide.okhttp3)
    implementation(libs.okhttp)

    // Compose for TV (Home and Details screens) - plain compose-foundation LazyRow/Column (not
    // androidx.tv:tv-foundation's - that artifact turned out to contain no usable TvLazyRow/Column
    // at this version, see HomeScreen.kt) plus tv-material's Card/Button/Text for focus-aware
    // styling. Library grid and Playback stay on Leanback/Views for now.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.tv.material)
    implementation(libs.glide.compose)

    // Extracts a dominant/vibrant color from a poster for the Details screen's ambient glow.
    implementation(libs.androidx.palette.ktx)
}
