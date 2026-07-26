# Open Babyphone Testing

This document defines the testing strategy for Open Babyphone, including
automated tests, the real-device reliability test matrix, and release
verification guidance.

## Automated Tests

The project intentionally keeps the existing JUnit 4 stack: Robolectric/JVM tests, instrumented Compose tests, Android JCA tests, and production-backed Compose Preview Screenshot Testing. Tests inject callbacks and immutable UI state directly; no dependency-injection framework is required.

### Local Verification Commands

| Purpose | Command |
|---|---|
| JVM and Robolectric tests | `./gradlew test` |
| Build instrumentation APKs | `./gradlew assembleDebugAndroidTest` |
| Run on a connected device/emulator | `./gradlew connectedDebugAndroidTest` |
| Generate screenshot references | `./gradlew updateDebugScreenshotTest` |
| Validate screenshot references | `./gradlew validateDebugScreenshotTest` |
| Generate debug JVM coverage | `./gradlew jacocoDebugUnitTestReport` |
| Release build and lint | `./gradlew assembleRelease lintRelease` |
| Full local host-side verification | `./gradlew --dependency-verification strict test lintRelease assembleRelease assembleDebugAndroidTest validateDebugScreenshotTest jacocoDebugUnitTestReport` |

Screenshot references live in `app/src/screenshotTestDebug/reference/`. Validation reports are written to `app/build/reports/screenshotTest/preview/debug/`. The debug JVM coverage report is written to `app/build/reports/jacoco/jacocoDebugUnitTestReport/` in XML and HTML formats.

Compose Preview Screenshot Testing is experimental and currently uses `com.android.compose.screenshot` `0.0.1-alpha15`. Reference images are LayoutLib renderings, not evidence of camera, notification, foreground-service, audio-device, OEM, or physical-display behavior. Regenerate baselines deliberately after reviewing visual changes; do not use baseline updates merely to make CI pass.

### Screen Behavior And Configuration Coverage

Instrumented Compose tests cover the first-child and first-parent journeys, returning-parent quick Listen, active child and parent states, quiet/sound/loud audio, disruption/reconnection/loss, and recovery actions. The QR journey injects scan outcomes and does not launch a camera. Interaction budgets and the absence of IP, port, service-name, child-ID, and pairing editors are asserted on core screens.

`DeviceConfigurationOverride` runs core content at 200% font scale in compact portrait (`360 x 640 dp`), short landscape (`640 x 260 dp`), and wide (`840 x 600 dp`) configurations. Tests scroll to critical Start, Stop, Retry, and Pair actions and verify state-hero/live-region, child-name, session-health, parent-count, freshness, warning, recovery, and traversal semantics.

Production-backed screenshot previews cover compact portrait, short landscape, wider layouts, light/dark themes, font scale 1.5, role choice, child setup/active, Parent Home states, Listen states, Connection Help, and Settings. Preview definitions are in `app/src/screenshotTest/kotlin/org/openbabyphone/CoreScreenPreviews.kt`.

### CI Device Matrix

The host job runs release assembly, JVM tests, release lint,
instrumentation-APK assembly, screenshot validation, and JaCoCo report
generation under strict dependency verification. It also checks the release APK
with the latest installed Build Tools using `zipalign -c -P 16 4`, extracts every
packaged `.so`, and requires every ELF `LOAD` segment alignment to be at least
`0x4000`.

Separate KVM-enabled emulator jobs run all instrumentation tests on API 30 and
API 36 with explicit job and boot timeouts. API 36 is current-platform behavior
coverage; the app still targets SDK 34. An API 35 `google_apis_ps16k` emulator
first asserts that `getconf PAGE_SIZE` is exactly `16384`, then runs Android
crypto, protected-credential, frame-crypto, handshake, and real `MainActivity`
launch/UI smoke tests. Every emulator job uploads connected-test reports and
logcat output with `if: always()`. The final job named `build` uses `if: always()`
and succeeds only when the host, API 30/API 36, and API 35 16 KB dependencies all
succeeded, so the existing required check gates the complete matrix.

The CI emulator matrix does not replace checks on the maintainer's available
phones. Notification shade behavior, physical microphone/playback, QR camera
scanning, router discovery, screen-off reliability, OEM process management,
Bluetooth/wired output, and overnight operation remain device-only checks.

Host tests verify jitter-buffer ordering, timestamp wrap arithmetic, target bounds, and concealment samples deterministically. They do not measure end-to-end latency, dropout audibility, `AudioRecord` short-read behavior on real hardware, or `AudioTrack` scheduling; those remain physical-device checks and no network or performance improvement should be inferred from the host tests alone.

The practical solo-maintainer walkthrough is at
[`docs/testing/maintainer-device-checklist.md`](testing/maintainer-device-checklist.md).
The project does not require moderated group studies or paid device-lab coverage.

### Unit Tests (JVM)

Run with:

    ./gradlew test

Covers:
- Audio protocol: `FrameCodec`, `FrameHeader`, `G711UCodec`, fixed 20 ms capture timing, sequence-ordered adaptive pre-roll, and bounded packet-loss concealment
- Client management: `ClientManager` lifecycle and queue behavior
- Crypto: `CryptoHelper` key derivation and encrypt/decrypt round-trip
- Volume: `VolumeStatistics` ring buffer and normalization, `VolumeHistory`
- Pairing: `PairingCode` validation, `PairingCodeGenerator`, `PairingQrCode` parsing
- Handshake: protocol handshake serialization
- ViewModels: `DiscoverViewModel`, `MonitorViewModel`, `ListenViewModel`
- Trusted child: protected credential encryption, migration, authenticated persistence, reset, deletion, and process recreation
- Microphone: `MicrophoneSensitivity` gain levels
- Wi-Fi Direct: `WifiDirectErrorsTest`, `WifiDirectTxtRecordParserTest`
- Listen service: `ListenServiceAlertTest`
- Device name: `DeviceNameTest`

### Instrumentation Tests (Device/Emulator)

Run with:

    ./gradlew connectedDebugAndroidTest

Covers:
- Crypto: `CryptoHelper` Android JCA behavior, `FrameCodecCryptoInstrumentedTest`
- Compose UI: `StartScreen`, `DiscoverScreen`, `MonitorScreen`, `DiscoverAddressScreen`
- Handshake: on-device challenge-response

### Lint

Run with:

    ./gradlew lintRelease

### Release-Grade Verification

The CI-grade verification command is:

    ./gradlew --dependency-verification strict test lintRelease assembleRelease assembleDebugAndroidTest validateDebugScreenshotTest jacocoDebugUnitTestReport

Dependency checksum metadata is generated only after reviewing dependency
changes, using the same task graph with `--write-verification-metadata sha256`.
Normal and CI builds use Gradle's strict mode. Android variant lockfiles are not
committed because variant-wide locking is not reliable enough here to add useful
coverage beyond the reviewed checksum metadata.

## Available-Device Reliability Checks

Open Babyphone's core promise is overnight reliability. Automated tests
cannot cover Android power management, OEM battery restrictions, router
behavior, screen-off operation, or long-running foreground services.
This section is a scenario catalogue for the two or three Android phones
available to the maintainer. It is not a requirement to own representative OEM
hardware, recruit external testers, or execute every scenario for every release.
Run the core walkthrough and the scenarios relevant to the changed risk, then
record the exact devices and omissions honestly.

### Power Management Strategy

Open Babyphone currently uses foreground services with explicit service types
for the long-running work:
- Child mode: `FOREGROUND_SERVICE_TYPE_MICROPHONE`
- Parent mode: `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`

The app does not currently hold an always-on `PARTIAL_WAKE_LOCK` or Wi-Fi lock.
That is intentional until real-device testing proves a lock is needed. Always-on
locks can increase battery drain and heat during overnight use, and the stream
itself uses TCP unicast after NSD discovery completes. Parent discovery uses a
short-lived multicast lock while searching for child devices.

The scheduled service heartbeat is a best-effort health check, not a guarantee
that Android will resurrect either foreground service. Android 12 and newer can
reject background foreground-service starts, and microphone monitoring is also
subject to the while-in-use permission restriction. The receiver contains these
rejections, schedules another health check, and posts a user-action recovery
notification. Trusted parent listening is retried when Android permits it; child
microphone monitoring requires the user to reopen the app when Android blocks a
background restart.

If any screen-off or overnight test below fails because Android suspends CPU,
Wi-Fi, microphone capture, or playback, file a bug with device details and add
the narrowest scoped lock needed for that mode. Do not add a broad permanent
lock without a reproducer.

### OEM Battery Optimization Guidance

Some Android distributions can stop or throttle foreground services despite the
notification being visible. Before treating an overnight failure as an app bug,
record whether battery optimization was enabled for Open Babyphone and whether
the device has OEM-specific power management.

When an available phone has aggressive OEM power management, compare the
relevant overnight scenario:
- With default battery optimization enabled
- With Open Babyphone exempted from battery optimization, if the default run
  fails or is unstable

Document any required OEM setting in the release notes and file a follow-up bug
if the app can improve its in-app guidance.

### Available Device Inventory

Record each available phone's model, Android version, security patch, and
battery-optimization setting in the release record. Use different Android
versions and OEMs when the existing phones provide them; missing categories are
known coverage limits rather than release blockers. CI remains responsible for
repeatable API 30, current-API, and 16 KB runtime coverage.

### Test Scenarios

Run applicable scenarios across the available devices. The baseline, one
screen-off run, and connection-loss recovery form the minimum public-release
walkthrough. Other scenarios are risk-based or opportunistic.

#### Baseline

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 1 | Child starts, parent discovers via NSD on same Wi-Fi | Parent connects within 10 seconds |
| 2 | Parent hears audio stream from child | Audio is audible, no dropouts > 3 seconds |
| 3 | Parent connects via manual IP/port entry | Connection succeeds, audio plays |
| 4 | Pairing code set on child, parent enters correct code | Connection succeeds, audio plays |
| 5 | Pairing code set on child, parent enters wrong code | Connection rejected |

#### Overnight Reliability

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 6 | Child runs 8+ hours with screen off | Audio stream continuous, no crash |
| 7 | Parent runs 8+ hours with screen off | Audio continuous, alert fires on disconnect |
| 8 | Child on charger, parent on battery | Both stable for 8+ hours |
| 9 | Child on battery, parent on charger | Both stable for 8+ hours |
| 10 | Locked screen on both devices | Stream continues, foreground notification visible |
| 11 | Child screen off, parent screen on for 30 minutes | Parent receives uninterrupted audio |
| 12 | Parent screen off, child screen on for 30 minutes | Parent playback continues and notification remains visible |
| 13 | Child charger connected during active monitoring | Stream continues without service restart |
| 14 | Child charger disconnected during active monitoring | Stream continues without service restart |
| 15 | Parent charger connected during active listening | Playback continues without service restart |
| 16 | Parent charger disconnected during active listening | Playback continues without service restart |

#### Network Resilience

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 17 | Wi-Fi disconnects on child for 10 seconds, reconnects | Parent reconnects automatically or alert fires |
| 18 | Wi-Fi disconnects on parent for 10 seconds, reconnects | Parent reconnects automatically or alert fires |
| 19 | Router restart (30 second outage) | Both devices recover without manual intervention or parent alert fires |
| 20 | Child service stopped and restarted | Parent reconnects or alerts clearly |
| 21 | Parent service stopped and restarted | Parent reconnects to child |
| 22 | Child switches Wi-Fi access point on same LAN | Parent reconnects or alerts clearly |
| 23 | Parent switches Wi-Fi access point on same LAN | Parent reconnects or alerts clearly |

#### Multi-Parent (Optional Manual Coverage)

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 24 | 2 parents connect simultaneously | Both receive audio |
| 25 | 1 of 2 parents disconnects | Other parent continues without interruption |
| 26 | 5 parents connect (max) | All 5 receive audio |
| 27 | 6th parent attempts connection | 6th parent rejected gracefully |
| 28 | 1 parent disconnects from max, new parent connects | New parent connects successfully |

#### Alert Audibility

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 29 | Child disconnects, parent on phone speaker | Alert is audible |
| 30 | Child disconnects, parent on Bluetooth headphones | Alert is audible on the active media output |
| 31 | Child disconnects, parent on wired headphones | Alert is audible on the active media output |
| 32 | Child disconnects, parent in Do Not Disturb mode | Alert behavior documented (may be silenced by DND) |
| 33 | Another media app is playing before parent connects | Open Babyphone requests audio focus and monitoring audio remains understandable |
| 34 | Another media app starts while parent is listening | Open Babyphone remains understandable or Android focus behavior is documented |

#### Foreground Service

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 35 | Child foreground notification persists | Notification visible for entire session |
| 36 | Parent foreground notification persists | Notification visible for entire session |
| 37 | App backgrounded on child | Stream continues, foreground service active |
| 38 | App backgrounded on parent | Stream continues, foreground service active |
| 39 | Child app removed from recents while monitoring | Stream continues or stops with a clear parent alert |
| 40 | Parent app removed from recents while listening | Playback continues or the foreground service stops cleanly |

For scenarios 39 and 40 on Android 12+, also kill the process without force-stop and
record whether the heartbeat start is accepted. If it is rejected, verify that
the app does not crash or claim that monitoring resumed, that one recovery
notification is posted, and that tapping it reaches the correct active-session
resume or trusted retry flow. This behavior remains OEM- and device-policy-
dependent and cannot be established by host-side tests.

### Release Verification Checklist

Before tagging a release, record the following without implying unavailable
coverage:

- [ ] Release version: ___
- [ ] Date of testing: ___
- [ ] Tester: ___
- [ ] Devices used: ___
- [ ] Android versions: ___
- [ ] Core walkthrough: passed / failed / not run
- [ ] Additional scenarios run: ___
- [ ] Scenarios skipped and reason: ___
- [ ] Release keystore and passwords backed up outside the repository: yes / no
- [ ] APK signing certificate SHA-256 matches expected fingerprint: yes / no
- [ ] Known issues at release time: ___
- [ ] Bugs filed for failures: ___

File follow-up issues in the [issue tracker](https://github.com/digitalesIch/open-babyphone/issues)
for any failures found during matrix runs.

Raising `targetSdk` from 34 to 36 is blocked on successful API 36 emulator
coverage plus behavior checks on the maintainer's available phones, including
notifications, foreground services, permissions, audio routing, discovery,
reconnect behavior, and screen-off runs. Passing the API 36 emulator job alone
does not satisfy that release decision, but no additional hardware purchase or
external device lab is required.
