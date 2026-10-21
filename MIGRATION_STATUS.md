# Material 3 + Open Design Migration - Status

This document tracks the UI migration from legacy XML views to Jetpack Compose
with Material 3, and the subsequent Open Design brand redesign.

For current build versions, see `AGENTS.md` and `app/build.gradle`.

## Phase 1: Foundation & Dependencies (Complete)

- minSdkVersion 30 (Android 11), compileSdk 37
- Compose BOM 2026.09.00 + Material 3
- Navigation Compose 2.10.2 (Type-Safe)
- Kotlin Serialization for Type-Safe Routes
- Brand theme by default; opt-in Dynamic Color (SDK 31+) with M3 fallback
- Edge-to-Edge Support

## Phase 2: Architecture (Complete)

- Single-Activity Architecture (MainActivity)
- Navigation Compose with Type-Safe Routes
- ViewModel Architecture with StateFlow
- Repository Pattern for Service State

## Phase 3: Screen Migration & Service-Integration (Complete)

- 8 Compose screens (Start, Monitor, Settings, Discover, DiscoverAddress, DiscoverWifiDirect, ConnectionHelp, Listen)
- MonitorService & ListenService integrated via Repository pattern
- mDNS Discovery with NSD Manager
- VolumeView migrated to Compose Canvas
- Wi-Fi Direct screen for experimental P2P connection

## Phase 4: UX Polish (Complete)

**WP0: String Externalization**
- All hardcoded strings replaced with `stringResource()` calls
- English-only throughout the app

**WP1: Theme & Design System**
- M3 Expressive default Typography and Shapes
- Removed statusBarColor override (edge-to-edge handles it)
- Extended colors.xml with M3 color tokens
- All hardcoded `fontSize` replaced with `MaterialTheme.typography.*`

**WP2: Adaptive Layouts**
- WindowSizeClass computed in MainActivity
- StartScreen: Two-column layout on expanded widths
- DiscoverAddressScreen: Max-width 600dp centered
- LocalWindowWidthSizeClass CompositionLocal for screen-level adaptation

**WP3: Animations & Motion**
- Nav enter/exit transitions (fade + slide)
- `animateContentSize()` on StartScreen
- `animateFloatAsState` for audio signal card tint in ListenScreen
- `animateItem()` for discovered device list

**WP4: Accessibility**
- `testTag` on all key interactive elements
- `contentDescription` on icons and VolumeWaveform
- `liveRegion` for status announcements (TalkBack)
- Semantic descriptions for service status

**WP5: Component Polish & States**
- Card containers for grouped content
- Loading state with CircularProgressIndicator
- Empty state with icon for discovery
- Error state with retry button
- M3 Expressive component styling

## Phase 5: Cleanup & Testing (Complete)

- Legacy Activities and XML layouts removed
- AndroidManifest cleaned up
- Internal notification resume through a non-exported trampoline activity
- Unit tests covering codec, framing, jitter buffer, crypto, client management,
  handshake, ViewModels, pairing QR, trusted child store, microphone sensitivity,
  Wi-Fi Direct errors and TXT records, volume statistics and history
- Instrumentation tests covering Android JCA crypto, Compose UI screens, frame codec crypto
- All tests passing
- Lint passing

## Phase 6: Open Design Brand Redesign (Complete)

- App icon redesigned to paired-phones brand mark (#135)
- Brand palette: cyan `#5FF2D2`, blue `#5DA8FF`, night canvas `#080B12`
- Open Design components: `BrandMark`, `OdStatusPill` in `OpenDesignComponents.kt`
- All runtime screens redesigned to match Open Design mockups (#138, #140)
- StartScreen, MonitorScreen, SettingsScreen, DiscoverScreen, DiscoverAddressScreen,
  DiscoverWifiDirectScreen, ConnectionHelpScreen, ListenScreen
- Website landing page updated with brand mark (#136)
- Android 12+ dynamic color disabled by default; "Use system colors" opt-in in Settings
- Monochrome themed icon added (#109)
- Design handoff document: `docs/design/app-ui-redesign.md`

## Final Metrics

| Metric | Value |
|--------|-------|
| **Build** | Successful (Debug + Release) |
| **Unit test files** | 64 |
| **Instrumentation test files** | 16 |
| **Lint** | Passing |
| **Min SDK** | 30 (Android 11) |
| **Target SDK** | 34 |
| **Compose BOM** | 2026.09.00 |
| **Navigation** | Compose 2.10.2 (Type-Safe) |
