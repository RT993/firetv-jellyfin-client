# Graph Report - firetv-jellyfin-client  (2026-11-05)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 674 nodes · 1509 edges · 30 communities (10 shown, 20 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `2cb50347`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Home Screen UI
- Home Activity Navigation
- Animated Shards Background
- Jellyfin Client Login
- Details Screen UI
- Server Health Monitor
- Local Database Cache
- Card Presenter
- Credential Storage
- Release Workflow
- Playback Activity Controls
- Playback Decision Maker
- Device Profile Factory
- Box Blur Transformation
- Jellyfin Repository
- Player Event Listener
- TTL Cache

## God Nodes (most connected - your core abstractions)
1. `JellyfinRepository` - 50 edges
2. `PlaybackActivity` - 49 edges
3. `JellyfinClientHolder` - 30 edges
4. `fadeToNextScreen()` - 26 edges
5. `LoginActivity` - 21 edges
6. `HomeScreen()` - 15 edges
7. `CardPresenter` - 14 edges
8. `JellyfinDao` - 12 edges
9. `FocusableCard()` - 12 edges
10. `HomeActivity` - 11 edges

## Surprising Connections (you probably didn't know these)
- `JellyfinRepository` --calls--> `TtlCache`  [INFERRED]
  app/src/main/java/io/github/rt993/firetvjellyfin/data/JellyfinRepository.kt → app/src/main/java/io/github/rt993/firetvjellyfin/data/TtlCache.kt
- `PlaybackActivity` --references--> `JellyfinRepository`  [EXTRACTED]
  app/src/main/java/io/github/rt993/firetvjellyfin/ui/playback/PlaybackActivity.kt → app/src/main/java/io/github/rt993/firetvjellyfin/data/JellyfinRepository.kt
- `PlaybackActivity` --references--> `PlaybackMode`  [EXTRACTED]
  app/src/main/java/io/github/rt993/firetvjellyfin/ui/playback/PlaybackActivity.kt → app/src/main/java/io/github/rt993/firetvjellyfin/playback/PlaybackDecisionMaker.kt
- `DetailsBackdrop()` --calls--> `BoxBlurTransformation`  [EXTRACTED]
  app/src/main/java/io/github/rt993/firetvjellyfin/ui/details/DetailsScreen.kt → app/src/main/java/io/github/rt993/firetvjellyfin/ui/theme/BoxBlurTransformation.kt
- `JellyfinClientHolder` --references--> `JellyfinRepository`  [EXTRACTED]
  app/src/main/java/io/github/rt993/firetvjellyfin/data/JellyfinClientHolder.kt → app/src/main/java/io/github/rt993/firetvjellyfin/data/JellyfinRepository.kt

## Import Cycles
- None detected.

## Communities (30 total, 20 thin omitted)

### Community 0 - "Home Screen UI"
Cohesion: 0.06
Nodes (24): AccountMenu(), buildMetaLine(), healthColor(), HeroDepthCard(), HeroDepthCarousel(), HeroDepthInfo(), HomeScreen(), HomeUiState (+16 more)

### Community 1 - "Home Activity Navigation"
Cohesion: 0.05
Nodes (7): ItemDetailsActivity, HomeActivity, LibraryGridActivity, ItemClickedListener, LibraryGridFragment, ProfileSelectActivity, fadeToNextScreen()

### Community 2 - "Animated Shards Background"
Cohesion: 0.06
Nodes (14): AeroShardsBackground(), rotated(), ShardSpec, wrap(), drawStreak(), DynamicBlueBackground(), Streak, kenBurnsDrift() (+6 more)

### Community 3 - "Jellyfin Client Login"
Cohesion: 0.06
Nodes (9): JellyfinClientHolder, LoginActivity, OnBackPressedCallback, Step, CREDENTIALS, QUICK_CONNECT, SERVER, WELCOME (+1 more)

### Community 4 - "Details Screen UI"
Cohesion: 0.08
Nodes (21): audioBadge(), BoxSetMovieCard(), BoxSetMovies(), buildMetaLine(), buildTechnicalBadges(), DetailsBackdrop(), DetailsCast(), DetailsInfoPanel() (+13 more)

### Community 5 - "Server Health Monitor"
Cohesion: 0.07
Nodes (7): ServerHealth, HEALTHY, UNKNOWN, UNREACHABLE, ServerHealthMonitor, JellyfinTvApplication, CrashLogger

### Community 6 - "Local Database Cache"
Cohesion: 0.11
Nodes (4): AppDatabase, CachedItem, CachedUserView, JellyfinDao

### Community 9 - "Credential Storage"
Cohesion: 0.12
Nodes (4): CredentialStore, SavedProfile, SavedServer, ServerListActivity

### Community 10 - "Release Workflow"
Cohesion: 0.08
Nodes (24): runs-on, steps, tag_name, jobs, build-and-release, name, on, push (+16 more)

### Community 13 - "Playback Decision Maker"
Cohesion: 0.24
Nodes (7): PlaybackDecisionMaker, PlaybackMode, DIRECT_PLAY, DIRECT_STREAM, TRANSCODE, PlaybackSelection, resolveJellyfinUrl()

## Knowledge Gaps
- **10 isolated node(s):** `DIRECT_PLAY`, `DIRECT_STREAM`, `TRANSCODE`, `CREDENTIALS`, `QUICK_CONNECT` (+5 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 167 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **20 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `JellyfinRepository` connect `Jellyfin Repository` to `Home Screen UI`, `Jellyfin Client Login`, `Details Screen UI`, `Server Health Monitor`, `Local Database Cache`, `Card Presenter`, `Jellyfin API Requests`, `Playback Player Setup`, `Playback Activity Controls`, `Media Item Queries`, `TTL Cache`, `Quick Connect`, `Playback Progress Reporting`, `Playback Info`?**
  _High betweenness centrality (0.276) - this node is a cross-community bridge._
- **What connects `DIRECT_PLAY`, `DIRECT_STREAM`, `TRANSCODE` to the rest of the system?**
  _10 weakly-connected nodes found - possible missing edges._
- **Should `Home Screen UI` be split into smaller, more focused modules?**
  _Cohesion score 0.056179775280898875 - nodes in this community are weakly interconnected._
- **Why does `JellyfinClientHolder` connect `Jellyfin Client Login` to `Home Screen UI`, `Home Activity Navigation`, `Server Health Monitor`, `Credential Storage`, `Playback Player Setup`, `Jellyfin Repository`?**
  _High betweenness centrality (0.140) - this node is a cross-community bridge._
- **Should `Home Activity Navigation` be split into smaller, more focused modules?**
  _Cohesion score 0.05480769230769231 - nodes in this community are weakly interconnected._
- **Why does `PlaybackActivity` connect `Playback Activity Controls` to `Home Activity Navigation`, `Server Health Monitor`, `Playback Player Setup`, `Playback Decision Maker`, `Jellyfin Repository`, `Audio/Subtitle Track Selection`, `Playback Reporting and Input`, `Player Event Listener`, `Subtitle Configuration`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Should `Animated Shards Background` be split into smaller, more focused modules?**
  _Cohesion score 0.06398730830248546 - nodes in this community are weakly interconnected._