# AGENTS.md

## Project Shape
- Single-module Android app: root `settings.gradle` includes only `:app`; application ID, Android namespace, and Kotlin package are all `org.openbabyphone`.
- Kotlin sources live under `app/src/main/kotlin/org/openbabyphone`; the current UI is Jetpack Compose with platform `Service` classes for long-running child/parent audio work.
- `MainActivity` is the launcher. Child mode uses `MonitorScreen`/`MonitorViewModel` -> `MonitorService`; parent mode uses `DiscoverScreen`/`DiscoverViewModel`, `DiscoverAddressScreen`, `DiscoverWifiDirectScreen`/`WifiDirectParentViewModel`, and `ListenScreen`/`ListenViewModel` -> `ListenService`.
- `SettingsActivity` is the only non-Compose screen; it is an XML `PreferenceActivity` for theme selection.
- Services communicate with ViewModels through singleton `StateFlow` repositories in `service/ServiceRepository.kt` (`MonitorServiceRepository`, `ListenServiceRepository`), not through data binding or LiveData.
- Navigation uses type-safe `@Serializable` routes defined in `navigation/NavRoutes.kt` and wired in `MainActivity.kt` via `NavHost`. To add a screen, add a route object/data class in `NavRoutes.kt` and a `composable<Route>` block in `MainActivity.kt`. Current routes: `Start`, `Monitor`, `Discover`, `DiscoverAddress`, `DiscoverWifiDirect`, `Listen`.
- A deep link `quiet-engine://listen?...` is registered in the manifest and `MainActivity` for resuming the listen session. Pairing codes are no longer embedded in deep-link URIs (#132); the parent uses stored trusted-child credentials or manual entry.
- The Compose theme lives under `ui/theme/` (`Theme.kt`, `Spacing.kt`, `Motion.kt`): full M3 light/dark color schemes, spacing tokens (`space2`..`space32`) replacing `dimens.xml`, and motion tokens for nav transitions. `VolumeCanvas` is theme-aware (`MaterialTheme.colorScheme`). `MainActivity` installs a `core-splashscreen` splash (`AppTheme.Splash`) and calls `enableEdgeToEdge()`.
- Open Design brand components live in `OpenDesignComponents.kt` and `BrandMark.kt`: `BrandMark` (paired-phones glyph), `OdStatusPill`, and other brand-aware UI elements. The brand palette is defined in `docs/design/app-ui-redesign.md` and uses cyan `#5FF2D2` and blue `#5DA8FF` as accent colors. Android 12+ dynamic color is disabled by default so the brand palette is stable; a future "Use system colors" setting is tracked in issue #137.
- Adaptive/tablet strategy is responsive single-pane: screens cap content width with `Box` + `widthIn(max = 600.dp)`. No multi-pane layout (Navigation 3 remains deferred). Compose Preview Screenshot Testing is not enabled yet, though the build now uses Kotlin 2.x.
- Audio/networking is service-driven: `MonitorService` advertises `_openbabyphone._tcp.` with Android NSD, binds from TCP port `10000` upward, optionally authenticates parents with a persistent alphanumeric pairing code, records mic audio, and streams G.711 u-law; `ListenService` performs the parent-side handshake, decodes, and plays the stream. `MicrophoneSensitivity` applies software gain to PCM samples before encoding; levels are Normal, High, Very high.
- Wi-Fi Direct is implemented experimentally via `WifiDirectController` and `WifiDirectPermissions`. The child device can start a Wi-Fi Direct group from `MonitorScreen`; the parent discovers and connects via `DiscoverWifiDirectScreen`. It is OEM/ROM-dependent and falls back to hotspot or manual address entry.
- Trusted child pairing is implemented via `TrustedChildStore` and `ChildDeviceIdentityStore`. The QR code uses `PairingQrCode` to encode a structured payload with child ID, pairing ID, and pairing code. Parents remember known children and reconnect without re-scanning. The child device identity persists in SharedPreferences.
- Manual parent connection exists for advanced trusted VPN or unusual local-network setups; the product direction is same Wi-Fi/LAN first, and NSD discovery is LAN-only.

## Build And Checks
- Use the checked-in wrapper: `./gradlew ...`.
- Before any write operation (commit, push, branch creation), always sync the local repository first: `git fetch origin` then `git pull --ff-only origin main` (or the current base branch). This prevents stale local branches when multiple agent sessions work on the same repository. If the pull fails, stop and inform the user.
- Gradle wrapper is 9.4.1; Android Gradle Plugin is 9.2.1; Kotlin is 2.4.0; Compose BOM is 2026.06.01; Compose Compiler is configured through the Kotlin Compose compiler Gradle plugin; navigation-compose is 2.9.8.
- Use JDK 21; Gradle emits Java 17 bytecode (`sourceCompatibility`, `targetCompatibility`, Kotlin `jvmTarget`) and sets `jvmToolchain(21)`.
- CI builds a debug APK artifact, then runs the release-grade verification `./gradlew assembleRelease test lintRelease`.
- Useful focused checks are `./gradlew test`, `./gradlew assembleDebugAndroidTest`, `./gradlew lintRelease`, and `./gradlew assembleRelease`.
- Local JVM/Robolectric/Compose behavior tests live under `app/src/test/kotlin`; Android instrumentation tests live under `app/src/androidTest/kotlin` for Android/JNI runtime coverage such as libsodium crypto and selected Compose UI checks. Run instrumentation tests with `./gradlew connectedDebugAndroidTest`.
- Lint aborts on errors; `MissingTranslation` is downgraded to a warning in `app/build.gradle`.
- Release signing is conditional on a `keystore.properties` file at the project root (gitignored). When present, `build.gradle` loads it and signs the release APK. When absent, the build produces an unsigned APK. See `CONTRIBUTING.md` for setup instructions. Never commit keystore files or passwords.
- `proguard-rules.txt` is referenced in `build.gradle` but does not exist in the repository. `minifyEnabled` is currently `false`, so this is harmless. If you enable minification, create the file first.

## Android Config Gotchas
- `compileSdk` is 37, `targetSdk` is 34; `minSdkVersion` is 30 and README promises Android 11+.
- Gradle `defaultConfig` has the effective release version (`versionCode 24`, `versionName "1.1.0-alpha.9"`); the manifest does not define version fields.
- `project.properties` is a generated legacy Android Tools file targeting `android-25`; do not edit it for Gradle behavior.
- `gradle.properties` enables AndroidX, disables Jetifier, enables configuration cache, and uses non-transitive R classes.

## Contribution Constraints
- `CONTRIBUTING.md` expects patches to pass unit tests and lint; CI runs on `main` pushes and PRs.
- `CONTRIBUTING.md` expects each patch/PR description to explain the problem being solved, how it was solved, and why that solution was chosen; bug fixes should include a reproducer and verification instructions when practical.
- `CONTRIBUTING.md` requires a license confirmation statement in PR descriptions; no sign-off line required. License confirmation is required only for pull requests that contribute code, not for issues, bug reports, or feature requests.
- Favor zero-cost, low-maintenance changes. Features that need hosted servers, relays, paid SaaS, accounts, WebRTC/STUN/TURN infrastructure, or recurring infrastructure are outside the current product direction unless explicitly requested; offer local-network/no-server options first. Treat VPN only as an advanced manual setup note.
- **Language**: All PR titles, PR descriptions, commit messages, and code comments must be written in English, regardless of the language used in conversation with the user. The project's CONTRIBUTING.md and documentation are in English.
- **App Language**: The app is English-only. All user-facing strings must be in English. Do not add or maintain translations in other languages.

## Fork Direction
- This repository is developed as an independent fork named `Open Babyphone` under `digitalesIch/open-babyphone`. `origin` is the active repository; `upstream` (`enguerrand/child-monitor`) is reference-only and its local fetch/push URLs are already set to `DISABLED`.
- **CRITICAL: Never create, edit, close, or comment on GitHub issues, PRs, or discussions in `enguerrand/child-monitor` unless explicitly requested.**
- Keep upstream attribution and GPLv3 license notices intact.
- Full `gh` targeting rules, upstream safety checklist, and git sync policy are in `.opencode/instructions.md`; follow them.

## Repository Settings Policy
- This is currently a solo-maintainer repository where the human maintainer and coding agents may operate through the same GitHub account.
- Recommended `main` branch protection: require pull requests, require the `build` status check, require branches to be up to date, prevent force pushes, and prevent branch deletion.
- For the current solo-maintainer/shared-account setup, the required approving review count **must remain 0**. Do not restore required approving reviews unless the user explicitly says that a second maintainer with write access exists.
- GitHub self-approval is impossible, so requiring approving reviews blocks human/agent workflows that operate through the same GitHub account.
- Admin/maintainer bypass may remain available for solo-maintainer operation, but use it only after CI is green and the change has been reviewed locally. "Reviewed locally" means manual/local inspection by the maintainer or agent, not a GitHub approving review.
- If a second maintainer is added, enable one required approving review and stop routine bypass usage.

## Roadmap And Project Tracking
- The GitHub Project `Open Babyphone Roadmap` is the source of truth for both strategic and operational roadmap planning.
- `ROADMAP.md` is a historical archive, not the active roadmap. Do not update it for routine issue status changes unless the user explicitly asks for an archive update.
- Roadmap-relevant issues should be added to the Project and assigned a `Phase` value.
- Use issue labels (`priority:*`, `cluster:*`, `security`, `ci`, etc.) for filtering; use the Project for roadmap phase/status.
- Small one-off bugs do not need to be added to the Project unless they affect roadmap planning.

## Documentation Synchronization

**Rule:** Whenever `README.md`, `CONTRIBUTING.md`, `NEWS`, or `ROADMAP.md` are modified, review this file for consistency.

Specifically verify:
- Version numbers (Gradle, AGP, Kotlin, JDK) match `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.
- Statements about missing tests reflect `app/src/test/` and `app/src/androidTest/` existence.
- Claims about "stale" or "legacy" files match the current repository state.
- Translation policy references the actual remaining `values-*/` directories.
- Roadmap references point to the GitHub Project as the active roadmap, with `ROADMAP.md` treated as an archive.

This file describes the project shape for AI agents; it must not contradict user-facing documentation.
