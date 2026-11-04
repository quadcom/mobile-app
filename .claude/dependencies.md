# Dependencies

## UI
- **Compose Multiplatform** - Cross-platform UI
- **Material3** - Design system
- **Material Icons Extended** - Icon set
- **FontAwesome / Tabler Icons** - Additional icons
- **Haze 2.0.0** (`haze`, `haze-blur`) - Background blur behind the speaker sheets. The sheets sit in their own window, so they blur captured sources (`HazeInput.Sources`) from the `hazeSource` on `MainNavRoot`'s root, not the window backdrop. 2.0.0 matches Compose Multiplatform 1.12.0

## Networking
- **Ktor** - HTTP client + WebSocket (CIO/Darwin/Java engines). `androidApp` also takes `ktor-client-core` for the volmod self-updater (`update/AppUpdater.kt`), which reads GitHub releases through `HttpClientFactory`
- **WebSocket** - Real-time server connection
- **WebRTC KMP** (v0.125.11) - Remote access tunneling (✅ production ready on Android)
  - **Note**: Uses native `org.webrtc.DataChannel` API for TEXT message transmission (webrtc-kmp limitation workaround)

## Data
- **kotlinx.serialization** - JSON serialization
- **multiplatform-settings-no-arg** - Cross-platform preferences

## DI & Architecture
- **Koin** - Dependency injection (core + compose + viewmodel)
- **Navigation3** - Type-safe navigation + Material3 adaptive

## Media
- **Coil 3.5.0** - Async image decoding and Compose integration; a pre-memory interceptor resolves HTTP and `mawebrtc` artwork through the app-owned `ArtworkRepository`, which supplies encoded bytes and content-based identities (no Coil network artifact). Coil 3.6.2 was reverted because it requires Compose 1.12 alignment.
- **ExoPlayer (Media3)** - Android local playback
- **AVPlayer** - iOS local playback

## Utilities
- **Kermit** - Multiplatform logging
- **Reorderable** - Drag-drop list reordering
- **mDNS** - Sendspin service discovery/advertising

## Build
- **Gradle** - Build system
- **Kotlin Multiplatform** - Cross-platform compilation
- **JVM 17** - Target for Android

---

> **Maintenance**: Update this file when adding/removing libraries.