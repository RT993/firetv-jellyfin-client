# TreeHouse

TreeHouse is a native Jellyfin client for Android TV, built to run well on older, low-spec Fire TV Stick hardware while keeping the UI polished and TV-first.

This is a personal project. It is not published to any app store and is not intended for general public distribution.

## Why TreeHouse

The app is optimized for older Fire TV hardware with limited RAM and weaker CPUs. Instead of a single heavy framework for everything, TreeHouse uses a hybrid stack:

- Jetpack Compose for TV for Home and Details screens
- Leanback for the dense library grid and browse surfaces
- Media3 / ExoPlayer for playback with a custom transport UI

This keeps the experience responsive on low-end hardware while still feeling modern and cinematic.

## Screens

1. Login & setup — server connection, sign-in, Quick Connect, and profile selection
2. Home — trending queue, continue watching, and library navigation in a TV-first layout
3. Details — poster view, metadata, season picker, watchlist controls, and rich media details
4. Playback — direct play, direct stream, or transcode handoff with a custom transport UI

## Installing on a Fire TV Stick

The easiest method needs no computer.

1. On the Fire TV Stick, open Settings -> My Fire TV -> Developer Options.
2. Turn on Apps from Unknown Sources.
3. If Developer Options is not visible, go to Settings -> My Fire TV -> About and tap the build number several times to unlock it.
4. Install Downloader from the Fire TV app store.
5. Open Downloader and enter:

   ```text
   tinyurl.com/25r4a86f
   ```

   This resolves to the latest APK:

   ```text
   https://github.com/RT993/firetv-jellyfin-client/releases/latest/download/TreeHouse.apk
   ```

6. Downloader will fetch and install it.
7. Launch TreeHouse from the Fire TV home screen.

This link always points to the newest release, so reusing the same URL later will install the latest update. See the Releases page for version history.

If you prefer installing from a machine on the same network using ADB, see the Building section below.

## Features

- Server login with address entry, username/password, or Quick Connect
- Profile and server switching backed by saved device sessions
- Home screen with a hero carousel, continue watching row, and library categories
- Details screen with metadata, media badges, watchlist controls, and inline season switching
- Playback decision logic that chooses direct play, direct stream, or HLS transcode
- Custom Apple-TV-inspired playback controls with skip, scrubber, and next-episode behavior
- Server health monitoring with persistent logs for troubleshooting
- Splash intro and smooth screen transitions

## Tech stack

This app is intentionally a hybrid rather than a single-framework build:

- Home and Details: Jetpack Compose for TV
- Library grid and browsing: Leanback
- Playback: Media3 / ExoPlayer with a custom SurfaceView transport UI
- Server API: Jellyfin SDK for Kotlin
- Images: Glide with shared OkHttp networking
- Color extraction: androidx.palette
- Async work: Kotlin coroutines
- Serialization: kotlinx.serialization

## Architecture

```text
app/src/main/java/io/github/rt993/firetvjellyfin/
├── JellyfinTvApplication.kt         Application entry point; initializes the Jellyfin client and wires Glide into OkHttp
├── data/
│   ├── JellyfinClientHolder.kt      Process-wide Jellyfin SDK instance and active API client
│   ├── CredentialStore.kt           SharedPreferences-backed storage for server info and auth state
│   └── JellyfinRepository.kt        App-facing wrapper for auth, browsing, and playback-info calls
├── playback/
│   ├── DeviceProfileFactory.kt      Declares playback capabilities sent to the server
│   └── PlaybackDecisionMaker.kt     Handles the direct-play/direct-stream/transcode decision
└── ui/
    ├── splash/                      Splash screen and launcher flow
    ├── login/                      Server and account sign-in flow
    ├── home/                       Compose home screen and browsing UI
    ├── library/                    Leanback library grid and item browsing
    ├── details/                    Compose details view with metadata and inline season selection
    ├── theme/                      Shared TV styling and visual building blocks
    └── playback/                   Custom playback activity with Media3 and transport controls
```

## Direct play vs. direct stream vs. transcode

The playback decision is intentionally centralized instead of being scattered through the player.

1. `DeviceProfileFactory` declares what the app claims the device supports, including containers, codecs, and a fallback HLS transcode profile.
2. `JellyfinRepository.getPlaybackInfo()` sends that profile to the Jellyfin server.
3. `PlaybackDecisionMaker` converts the server response into a single playable URL.
4. `PlaybackActivity` simply feeds that URL into ExoPlayer.

This keeps the decision in one place and ensures the app handles all three outcomes:

- direct play
- direct stream
- transcode

The app specifically avoids jumping directly from direct play to full transcode when a server-supported direct stream would be sufficient.

The device codec list is currently a reasonable generic baseline rather than a runtime query of the actual device’s `MediaCodecList`. That is a natural next step for improved accuracy.

## The minSdk situation

The project originally targeted API 21, but modern AndroidX versions now raise the practical minimum to API 23.

As of recent stable AndroidX releases, `androidx.activity` and related libraries declare a minimum SDK of 23. Building with `minSdk = 21` fails during manifest merging because the app pulls in newer AndroidX libraries that require API 23 or higher.

The project therefore targets `minSdk = 23`, not 21.

This still covers the real target hardware: the Fire TV Stick in use reports Fire OS 7.7.1.5, built on Android 9 (API 28). That is well above the project floor, so the app remains compatible without dependency pinning or legacy library downgrades.

## Building

Requirements:

- JDK 17+
- Android SDK
- compileSdk / targetSdk 36
- build-tools 36.0.0

From the project root:

```bash
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Every push of a `v*` tag triggers a release workflow that publishes the APK as a GitHub release. That is the same path used by the Downloader install link above.

For real performance testing on the actual low-spec target hardware, `assembleRelease` is preferred over debug builds because debug builds carry extra runtime overhead unrelated to the app’s actual logic.

```bash
./gradlew assembleRelease
```

The release build signs with the checked-in debug keystore so it can replace an existing debug install without an uninstall/signature-mismatch step.

R8 minification remains off for now because the project uses reflection-heavy dependencies and needs real ProGuard rules before it is safe to enable.

### Installing via ADB instead

1. On the Fire TV Stick, enable Developer Options and ADB debugging.
2. Find the stick’s IP address in Settings -> My Fire TV -> About -> Network.
3. From a machine on the same network:

   ```bash
   adb connect <fire-tv-ip>:5555
   ./gradlew installDebug
   ```

   Or install the APK directly:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. Launch it from the Fire TV home screen, or directly with:

   ```bash
   adb shell monkey -p io.github.rt993.firetvjellyfin -c android.intent.category.LEANBACK_LAUNCHER 1
   ```

## Code graph

`graphify-out/` contains a Graphify-generated knowledge graph of the Kotlin source. Open `graphify-out/graph.html` in a browser to explore it, or read `graphify-out/GRAPH_REPORT.md` for the summary.

Regenerate it with:

```bash
graphify extract . --code-only
graphify cluster-only .
```

## Planned next

- Add skip-outro support using the same media-segment API used for intro skip
- Add an explicit "play from the beginning" action without losing the saved resume position
- Improve playback accuracy by querying `MediaCodecList` at runtime

## Known gaps / next steps

- No automated test suite yet
- The app uses both Compose and Leanback, which is intentional but adds APK size
- Login persists auth tokens in plain `SharedPreferences`; `EncryptedSharedPreferences` is the next step
- `usesCleartextTraffic="true"` is enabled because many self-hosted Jellyfin servers are still `http://`; this should be narrowed with a network security config
- Release builds use the generated debug keystore rather than a dedicated release key
- A harmless manifest-merger warning remains due to the Jellyfin SDK namespace arrangement

## Summary

TreeHouse is a focused, TV-first Jellyfin client built around the realities of older Fire TV hardware: limited memory, old CPUs, and a need for responsive browsing and playback without sacrificing polish.

It is a practical app for one household and one server, built to feel at home on a low-end Android TV device while still delivering a modern Jellyfin experience.
