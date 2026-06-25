# AGENTS.md

## Project Shape
- Single-module Android app: root `settings.gradle` includes only `:app`; application ID, Android namespace, and Kotlin package are all `org.openbabyphone`.
- Kotlin sources live under `app/src/main/kotlin/org/openbabyphone`; the current UI is Jetpack Compose with platform `Service` classes for long-running child/parent audio work.
- `MainActivity` is the launcher. Child mode uses `MonitorScreen`/`MonitorViewModel` -> `MonitorService`; parent mode uses `DiscoverScreen`/`DiscoverViewModel`, `DiscoverAddressScreen`, and `ListenScreen`/`ListenViewModel` -> `ListenService`.
- `SettingsActivity` is the only non-Compose screen; it is an XML `PreferenceActivity` for theme selection.
- Services communicate with ViewModels through singleton `StateFlow` repositories in `service/ServiceRepository.kt` (`MonitorServiceRepository`, `ListenServiceRepository`), not through data binding or LiveData.
- Navigation uses type-safe `@Serializable` routes defined in `navigation/NavRoutes.kt` and wired in `MainActivity.kt` via `NavHost`. To add a screen, add a route object/data class in `NavRoutes.kt` and a `composable<Route>` block in `MainActivity.kt`.
- A deep link `quiet-engine://listen?...` is registered in the manifest and `MainActivity` for resuming the listen session.
- The Compose theme lives under `ui/theme/` (`Theme.kt`, `Spacing.kt`, `Motion.kt`): full M3 light/dark color schemes, spacing tokens (`space2`..`space32`) replacing `dimens.xml`, and motion tokens for nav transitions. `VolumeCanvas` is theme-aware (`MaterialTheme.colorScheme`). `MainActivity` installs a `core-splashscreen` splash (`AppTheme.Splash`) and calls `enableEdgeToEdge()`.
- Adaptive/tablet strategy is responsive single-pane: screens cap content width with `Box` + `widthIn(max = 600.dp)`. No multi-pane layout (deferred; compileSdk 34 and no Navigation 3). Compose Preview Screenshot Testing requires Kotlin 2.x (the `screenshot-validation-api` artifact starts at plugin alpha10 which needs Kotlin 2.1+); it remains deferred until a Kotlin upgrade is scheduled.
- Audio/networking is service-driven: `MonitorService` advertises `_openbabyphone._tcp.` with Android NSD, binds from TCP port `10000` upward, optionally authenticates parents with a persistent alphanumeric pairing code, records mic audio, and streams G.711 u-law; `ListenService` performs the parent-side handshake, decodes, and plays the stream.
- Manual parent connection exists for advanced trusted VPN or unusual local-network setups; the product direction is same Wi-Fi/LAN first, and NSD discovery is LAN-only.

## Build And Checks
- Use the checked-in wrapper: `./gradlew ...`.
- Before any write operation (commit, push, branch creation), always sync the local repository first: `git fetch origin` then `git pull --ff-only origin main` (or the current base branch). This prevents stale local branches when multiple agent sessions work on the same repository. If the pull fails, stop and inform the user.
- Gradle wrapper is 8.7; Android Gradle Plugin is 8.5.2; Kotlin is 1.9.25; Compose BOM is 2024.09.03; Compose compiler extension is 1.5.15; navigation-compose is 2.8.9.
- Use JDK 21; Gradle emits Java 17 bytecode (`sourceCompatibility`, `targetCompatibility`, Kotlin `jvmTarget`) and sets `jvmToolchain(21)`. The comment in `build.gradle` explains that AGP 8.5 lint cannot analyze Java 21 class files reliably.
- CI builds a debug APK artifact, then runs the release-grade verification `./gradlew assembleRelease testReleaseUnitTest lintRelease`.
- Useful focused checks are `./gradlew testReleaseUnitTest`, `./gradlew assembleDebugAndroidTest`, `./gradlew lintRelease`, and `./gradlew assembleRelease`.
- Local JVM/Robolectric/Compose behavior tests live under `app/src/test/kotlin`; Android instrumentation tests live under `app/src/androidTest/kotlin` for Android/JNI runtime coverage such as libsodium crypto and selected Compose UI checks. Run instrumentation tests with `./gradlew connectedDebugAndroidTest`.
- Lint aborts on errors; `MissingTranslation` is downgraded to a warning in `app/build.gradle`.
- Release signing is conditional on a `keystore.properties` file at the project root (gitignored). When present, `build.gradle` loads it and signs the release APK. When absent, the build produces an unsigned APK. See `CONTRIBUTING.md` for setup instructions. Never commit keystore files or passwords.
- `proguard-rules.txt` is referenced in `build.gradle` but does not exist in the repository. `minifyEnabled` is currently `false`, so this is harmless. If you enable minification, create the file first.

## Android Config Gotchas
- `compileSdk`/`targetSdk` are 34; `minSdkVersion` is 30 and README promises Android 11+.
- Gradle `defaultConfig` has the effective release version (`versionCode 16`, `versionName "1.1.0"`); the manifest does not define version fields.
- `project.properties` is a generated legacy Android Tools file targeting `android-25`; do not edit it for Gradle behavior.
- `gradle.properties` enables AndroidX, disables Jetifier, enables configuration cache, and uses non-transitive/non-final R classes.

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

## Documentation Synchronization

**Rule:** Whenever `README.md`, `CONTRIBUTING.md`, `NEWS`, or `ROADMAP.md` are modified, review this file for consistency.

Specifically verify:
- Version numbers (Gradle, AGP, Kotlin, JDK) match `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.
- Statements about missing tests reflect `app/src/test/` and `app/src/androidTest/` existence.
- Claims about "stale" or "legacy" files match the current repository state.
- Translation policy references the actual remaining `values-*/` directories.

This file describes the project shape for AI agents; it must not contradict user-facing documentation.
