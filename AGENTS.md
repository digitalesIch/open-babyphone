# AGENTS.md

## Project Shape
- Single-module Android app: root `settings.gradle` includes only `:app`; application ID, Android namespace, and Kotlin package are all `org.openbabyphone`.
- Kotlin sources live under `app/src/main/kotlin/org/openbabyphone`; UI is XML layouts plus platform `Activity`/`Service`, not Compose/AppCompat. Material Components are used for migrated screens, but several layouts still use platform widgets.
- `StartActivity` is the launcher. Child mode is `MonitorActivity` -> `MonitorService`; parent mode is `DiscoverActivity` -> `ListenActivity` -> `ListenService`.
- Audio/networking is service-driven: `MonitorService` advertises `_childmonitor._tcp.` with Android NSD, binds from TCP port `10000` upward, optionally authenticates parents with a persistent alphanumeric pairing code, records mic audio, and streams G.711 u-law; `ListenService` performs the parent-side handshake, decodes, and plays the stream.
- Manual parent connection exists for advanced trusted VPN or unusual local-network setups; the product direction is same Wi-Fi/LAN first, and NSD discovery is LAN-only.

## Build And Checks
- Use the checked-in wrapper: `./gradlew ...`.
- Gradle wrapper is 8.6; Android Gradle Plugin is 8.2.2; Kotlin is 1.9.22.
- Use JDK 21; Gradle emits Java 17 bytecode (`sourceCompatibility`, `targetCompatibility`, Kotlin `jvmTarget`) and sets `jvmToolchain(21)`.
- CI/release-grade verification is exactly `./gradlew assembleRelease testReleaseUnitTest lintRelease`; CI installs Android SDK platform/build-tools 34 first.
- Useful focused checks are `./gradlew testReleaseUnitTest`, `./gradlew assembleDebugAndroidTest`, `./gradlew lintRelease`, and `./gradlew assembleRelease`.
- Local JVM tests live under `app/src/test/kotlin`; Android instrumentation tests live under `app/src/androidTest/kotlin` for Android/JNI runtime coverage such as libsodium crypto.
- Lint aborts on errors; `MissingTranslation` is downgraded to a warning in `app/build.gradle`.

## Android Config Gotchas
- `compileSdk`/`targetSdk` are 34; `minSdkVersion` is 30 and README promises Android 11+.
- Gradle `defaultConfig` has the effective release version (`versionCode 16`, `versionName "1.1.0"`); the manifest does not define version fields.
- `project.properties` is a generated legacy Android Tools file targeting `android-25`; do not edit it for Gradle behavior.
- `gradle.properties` enables AndroidX, disables Jetifier, enables configuration cache, and uses non-transitive/non-final R classes.

## Contribution Constraints
- `CONTRIBUTING.md` expects patches to pass unit tests and lint; CI runs on `main` pushes and PRs.
- `CONTRIBUTING.md` expects each patch/PR description to explain the problem being solved, how it was solved, and why that solution was chosen; bug fixes should include a reproducer and verification instructions when practical.
- `CONTRIBUTING.md` requires a license confirmation statement in PR descriptions; no sign-off line required.
- Favor zero-cost, low-maintenance changes. Features that need servers, relays, paid SaaS, accounts, WebRTC/STUN/TURN infrastructure, or recurring infrastructure are outside the current product direction unless explicitly requested; offer local-network/no-server options first. Treat VPN only as an advanced manual setup note.
- **Language**: All PR titles, PR descriptions, commit messages, and code comments must be written in English, regardless of the language used in conversation with the user. The project's CONTRIBUTING.md and documentation are in English.
- **App Language**: The app is English-only. All user-facing strings must be in English. Do not add or maintain translations in other languages.

## Fork Direction
- This repository is now developed as an independent fork named `Open Babyphone` under `digitalesIch/open-babyphone`.
- Treat `origin` as the active repository (`digitalesIch/open-babyphone`) and `upstream`/`enguerrand/child-monitor` as reference-only.
- **CRITICAL: Never create, edit, close, or comment on GitHub issues, PRs, or discussions in `enguerrand/child-monitor` unless explicitly requested.**
- Upstream PRs to `enguerrand/child-monitor` should be limited to small maintenance fixes after explicit user request.
- Larger roadmap work, product decisions, UI changes, authentication, reconnect behavior, multi-client support, and releases belong in this fork.
- Keep upstream attribution and GPLv3 license notices intact.

## Repository Rules
- **All new issues and PRs go to the fork** (`digitalesIch/open-babyphone`), never to upstream.
- **All `gh` commands that read or write issues, PRs, releases, workflow runs, checks, discussions, or repository settings MUST explicitly target `digitalesIch/open-babyphone` using `--repo digitalesIch/open-babyphone` or the `digitalesIch/open-babyphone` repository argument when a command does not support `--repo`, except `gh repo set-default` itself.**
- Do not rely on the implicit `gh` default repository. GitHub CLI can resolve forked workspaces to the parent repository unless `gh repo set-default digitalesIch/open-babyphone` is set locally.
- Before creating, editing, closing, or commenting on an issue/PR, verify that the target URL starts with `https://github.com/digitalesIch/open-babyphone/`.
- If an upstream issue/PR was already closed or redirected by mistake, do not add more comments there unless the user explicitly asks.
- Git fetch/push to upstream should be disabled locally (`git remote set-url upstream DISABLED` and `git remote set-url --push upstream DISABLED`) unless the user explicitly requests upstream maintenance work.
- Bug reports, security issues, feature requests, and roadmap items **must** be created in the fork's issue tracker.
- The only exception for upstream interaction: small maintenance fixes (typos, build warnings) after explicit user request.

## Repository Settings Policy
- This is currently a solo-maintainer repository where the human maintainer and coding agents may operate through the same GitHub account.
- Recommended `main` branch protection: require pull requests, require the `build` status check, require branches to be up to date, prevent force pushes, and prevent branch deletion.
- Do not require approving reviews until a second maintainer with write access exists, because GitHub does not allow self-approval and shared-account agent workflows cannot satisfy that rule.
- Admin/maintainer bypass may remain available for solo-maintainer operation, but use it only after CI is green and the change has been reviewed locally.
- If a second maintainer is added, enable one required approving review and stop routine bypass usage.

## Documentation Synchronization

**Rule:** Whenever `README.md`, `CONTRIBUTING.md`, `NEWS`, or `ROADMAP.md` are modified, review this file for consistency.

Specifically verify:
- Version numbers (Gradle, AGP, Kotlin, JDK) match `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.
- Statements about missing tests reflect `app/src/test/` and `app/src/androidTest/` existence.
- Claims about "stale" or "legacy" files match the current repository state.
- Translation policy references the actual remaining `values-*/` directories.

This file describes the project shape for AI agents; it must not contradict user-facing documentation.

## Known Stale References

No intentionally stale references are currently tracked here. When an issue is completed, update this file in the same patch if its guidance changes.
