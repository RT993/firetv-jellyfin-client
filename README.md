# TreeHouse

A lightweight, native Jellyfin client for Android TV, built specifically to run well on old,
low-spec Amazon Fire TV Stick hardware (1-1.5GB RAM, weak quad-core CPUs). No Compose, no
Flutter/React Native - just Leanback + Media3, which is about as light as a modern Android TV app
gets.

## Installing on your Fire TV Stick

The easiest way, with no computer required:

1. On the Fire TV Stick: **Settings -> My Fire TV -> Developer Options** -> turn on **Apps from
   Unknown Sources**. (If you don't see Developer Options, first go to **Settings -> My Fire TV ->
   About** and click on the build number a few times to unlock it.)
2. Install **Downloader** (search for it in the Fire TV's app store - it's free, published by
   AFTVnews).
3. Open Downloader and enter this URL:
   ```
   https://github.com/RT993/firetv-jellyfin-client/releases/latest/download/TreeHouse.apk
   ```
4. Downloader will fetch and install it. Launch **TreeHouse** from the Fire TV home screen.

This link always points at the newest release, so re-entering the same URL in Downloader later
gets you the latest update. See [Releases](https://github.com/RT993/firetv-jellyfin-client/releases)
for the full version history.

If you'd rather install from a machine on the same network via ADB, see
[Building](#building) below.

## Features

- **Server login**: server address entry, username/password, or Quick Connect (approve from
  another already-signed-in Jellyfin app/web page).
- **Home**: one row per library (Movies, TV Shows, etc.), plus **Movies**/**TV Shows** filters in
  the top nav bar that narrow the view to a single library's rows.
- **Details screen**: poster, title, rating/year/runtime, cast, and a translucent "glass" info
  panel over the item's own backdrop image (loaded full-bleed via `centerCrop`, not Leanback's
  built-in background controller - see [`ItemDetailsFragment.loadBackdrop`](app/src/main/java/io/github/rt993/firetvjellyfin/ui/details/ItemDetailsFragment.kt)
  for why).
- **TV series get per-episode playback**: instead of one "Play" button for the whole series, the
  details screen lists one row per season, each full of that season's episodes as landscape
  thumbnail cards - picking one plays that specific episode.
- **Playback**: direct-play when the server says the file's compatible with this device, HLS
  transcode fallback otherwise (see [below](#the-direct-play-vs-transcode-decision)), with D-pad
  transport controls.
- **Splash intro**: a short floating-logo animation on launch before handing off to Home (if
  already signed in) or the login flow.

## Tech stack

- **Language/UI**: Kotlin + [androidx.leanback](https://developer.android.com/reference/androidx/leanback/package-summary)
  (`BrowseSupportFragment`, `GuidedStepSupportFragment`, `DetailsSupportFragment`) for D-pad-first
  navigation on TV. Leanback is a thin, TV-optimized widget layer - much less overhead on old
  hardware than Compose for TV or a cross-platform framework.
- **Playback**: [Media3/ExoPlayer](https://developer.android.com/media/media3) via
  `androidx.media3.ui.leanback.LeanbackPlayerAdapter`, wired into Leanback's
  `PlaybackTransportControlGlue` for D-pad transport controls.
- **Server API**: the official [jellyfin-sdk-kotlin](https://github.com/jellyfin/jellyfin-sdk-kotlin)
  (`org.jellyfin.sdk:jellyfin-core` + `jellyfin-platform-android`) for auth, library browsing,
  media info, and playback URL construction. No hand-rolled REST calls.
- **Images**: Glide, for poster/backdrop loading into Leanback's `ImageCardView`. This one
  dependency isn't in a stock Leanback+Media3+SDK list but is close to unavoidable for a usable
  browse screen - it's mature, TV-tested (it's what Google's own Leanback samples use), and has a
  well-tuned disk/memory cache that matters more, not less, on constrained hardware.
- **Coroutines** (`kotlinx-coroutines-android`) for async server calls, **kotlinx-serialization**
  because jellyfin-sdk-kotlin's models use it.

## Architecture

```
app/src/main/java/io/github/rt993/firetvjellyfin/
├── JellyfinTvApplication.kt        Application entry point; boots JellyfinClientHolder
├── data/
│   ├── JellyfinClientHolder.kt     Process-wide Jellyfin SDK instance + current ApiClient
│   ├── CredentialStore.kt          SharedPreferences: server URL, access token, user id
│   └── JellyfinRepository.kt       App-facing wrapper over the raw SDK calls (auth, browse, playback info)
├── playback/
│   ├── DeviceProfileFactory.kt     Declares this device's playback capabilities to the server
│   └── PlaybackDecisionMaker.kt    The direct-play-vs-transcode decision point (see below)
└── ui/
    ├── splash/   SplashActivity: launcher activity, plays the intro then routes to Home/Login
    ├── login/    Server address -> username/password or Quick Connect (GuidedStepSupportFragment flow)
    ├── home/     BrowseSupportFragment: one row per library, poster cards via CardPresenter
    ├── details/  DetailsSupportFragment: item metadata, Play action (movies), season/episode rows (series)
    └── playback/ VideoSupportFragment wrapping a Media3 ExoPlayer instance
```

### The direct-play vs. transcode decision

This is the one architectural point the task called out explicitly, so it's deliberately isolated
rather than inlined into the player:

1. [`playback/DeviceProfileFactory.kt`](app/src/main/java/io/github/rt993/firetvjellyfin/playback/DeviceProfileFactory.kt)
   declares what this app/device claims to support - containers, video/audio codecs, and one HLS
   transcoding fallback profile. This is sent to the server, not decided locally.
2. `JellyfinRepository.getPlaybackInfo()` POSTs that profile to `/Items/{id}/PlaybackInfo`. The
   server matches it against the actual file and answers, per media source, whether it
   `supportsDirectPlay`, `supportsDirectStream`, or only `supportsTranscoding` (with a ready-made
   HLS URL).
3. [`playback/PlaybackDecisionMaker.kt`](app/src/main/java/io/github/rt993/firetvjellyfin/playback/PlaybackDecisionMaker.kt)
   turns that server answer into one concrete, playable URL. This is the single place in the app
   where that choice is made - `PlaybackVideoFragment` never inspects codecs itself, it just hands
   ExoPlayer whatever URL comes out.

The current codec list in `DeviceProfileFactory` is a reasonable generic baseline (H.264/HEVC,
common containers). It is **not** tuned to a specific Fire TV Stick generation - different stick
generations have different hardware decoders (some can't do HEVC or 4K at all). Querying
`MediaCodecList` at runtime and feeding the result into the device profile would make this
decision materially more accurate, and is the natural next step here.

## The minSdk situation

The task this was scaffolded from originally asked for **minSdk 21** (Fire OS 5 / Android 5.1, the
oldest common Fire TV Stick generation), with a note to verify it against the real device. That
verification turned up a hard blocker worth recording:

**As of `androidx.activity:activity:1.12.0-alpha06` (August 2025), AndroidX's own default minSdk
moved from API 21 to API 23 (Android 6.0) project-wide**, and that floor is already baked into the
current stable releases of `androidx.activity` (pulled in transitively by `fragment-ktx`, which
Leanback depends on). Building this project with `minSdk = 21` and today's stable AndroidX
versions fails at manifest-merge time:

```
uses-sdk:minSdkVersion 21 cannot be smaller than version 23 declared in library
[androidx.activity:activity:1.13.0] ... as the library might be using APIs not available in 21
```

So **this project targets `minSdk = 23`**, not 21. Reaching API 21/22 today would mean deliberately
pinning `androidx.activity`, `androidx.fragment`, `androidx.core`, and `androidx.lifecycle` to
versions older than their respective API-23 bumps and accepting unsupported/unpatched library
versions - not worth it here.

**Confirmed against the actual target device**: it reports Fire OS **7.7.1.5**, which is built on
**Android 9 (API 28)** - the Fire TV Stick (2020, 3rd gen) / Fire TV Stick 4K / Fire TV Stick Lite
generation (1.5GB RAM, quad-core 1.7GHz Cortex-A53). `minSdk = 23` comfortably covers this device;
no dependency pinning needed. (minSdk is a floor, not a target - it doesn't need to equal the
device's own API level, just be at or below it. `targetSdk` stays at the current SDK for
up-to-date behavior/APIs.) The hardware is still weak by modern standards, so the lightweight
Leanback + Media3 approach (rather than Compose/Flutter/RN) is still the right call.

## Building

Requires JDK 17+ and the Android SDK (compileSdk/targetSdk 36, build-tools 36.0.0). No local
Android Studio setup is required to build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Every push of a `v*` tag also
builds this via [`.github/workflows/release.yml`](.github/workflows/release.yml) and publishes it
as a [GitHub release](https://github.com/RT993/firetv-jellyfin-client/releases) - that's what the
Downloader link above points at, and it can also be triggered manually from the Actions tab.

### Installing via ADB instead

1. On the Fire TV Stick: **Settings -> My Fire TV -> Developer Options** -> turn on **ADB
   debugging** and **Apps from Unknown Sources**.
2. Find the Stick's IP address: **Settings -> My Fire TV -> About -> Network**.
3. From a machine on the same network:
   ```bash
   adb connect <fire-tv-ip>:5555
   ./gradlew installDebug
   # or, if you already have the APK:
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch it from the Fire TV home screen, or directly via:
   ```bash
   adb shell monkey -p io.github.rt993.firetvjellyfin -c android.intent.category.LEANBACK_LAUNCHER 1
   ```

## Known gaps / next steps

- No automated tests yet.
- `DeviceProfileFactory`'s codec list is generic, not queried from the actual device's
  `MediaCodecList` - see the direct-play/transcode section above.
- Login persists the access token in plain `SharedPreferences` (see `CredentialStore.kt`); move to
  `EncryptedSharedPreferences` before any wider use.
- `usesCleartextTraffic="true"` is set in the manifest because most self-hosted Jellyfin servers on
  a home network are `http://`-only; scope this to a network security config allow-listing trusted
  hosts before a wider release.
- Releases are signed with Gradle's auto-generated debug keystore, not a dedicated release key -
  fine for sideloading, but every install shares the same debug signature. Set up a real signing
  config before distributing this any more widely.
- A harmless manifest-merger warning appears about `org.jellyfin.sdk` being used as the namespace
  by both `jellyfin-platform-android` and `jellyfin-core-android-debug` - that's inside the SDK
  dependency itself, not this app's code.
