# firetv-jellyfin-client

A lightweight, native Jellyfin client for Android TV, built specifically to run well on old,
low-spec Amazon Fire TV Stick hardware (1-1.5GB RAM, weak quad-core CPUs). No Compose, no
Flutter/React Native - just Leanback + Media3, which is about as light as a modern Android TV app
gets.

This is a scaffold: the screens are functional (real server login, real library browsing, real
playback) but intentionally minimal. It's a starting point to build out, not a finished client.

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
    ├── login/    Server address -> username/password or Quick Connect (GuidedStepSupportFragment flow)
    ├── home/     BrowseSupportFragment: one row per library, poster cards via CardPresenter
    ├── details/  DetailsSupportFragment: item metadata + Play action
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

## The minSdk situation (please read before assuming 21 works)

The task this was scaffolded from asked for **minSdk 21** (Fire OS 5 / Android 5.1, the oldest
common Fire TV Stick generation), with a note to verify it against the real device. That
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

So **this project currently targets `minSdk = 23`**, not 21. Two ways to reconcile this with real
hardware:

- **If the target Fire TV Stick is Android 6.0 (Marshmallow, API 23) or newer** - which covers the
  large majority of "Fire TV Stick" devices still in use, including most that shipped with Fire OS
  5.2+ - no action needed, `minSdk = 23` already covers it.
- **If the target device is genuinely Android 5.0/5.1 (API 21/22)** - e.g. the original 2014/2016
  Fire TV Stick - hitting that floor today requires deliberately pinning `androidx.activity`,
  `androidx.fragment`, `androidx.core`, and `androidx.lifecycle` to versions older than their
  respective API-23 bumps, verifying they still satisfy each other's minimum-version constraints,
  and accepting you're on unsupported/unpatched library versions. That's a real path, just not one
  this scaffold takes automatically - it trades a hard requirement (21) for a fragile,
  high-maintenance dependency graph.

**Action item before relying on this**: check the actual device's Android version (Settings ->
My Fire TV -> About -> Fire OS version, cross-referenced against
[Amazon's Fire OS/Android version table](https://developer.amazon.com/docs/fire-tv/device-specifications.html)).
If it's API 23+, this scaffold is already correct. If it's older, see above.

## Building

Requires JDK 17+ and the Android SDK (compileSdk/targetSdk 36, build-tools 36.0.0). No local
Android Studio setup is required to build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. This project was built and
verified with this exact command in a sandboxed CI-like environment (JDK 21, Gradle 8.14.3,
AGP 8.13.2) with no emulator available, so only compilation/packaging was verified - the flows
below have not been exercised on an actual device or emulator yet.

## Sideloading onto a Fire TV Stick

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
- A harmless manifest-merger warning appears about `org.jellyfin.sdk` being used as the namespace
  by both `jellyfin-platform-android` and `jellyfin-core-android-debug` - that's inside the SDK
  dependency itself, not this app's code.
