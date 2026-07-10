# Open Babyphone Testing

This document defines the testing strategy for Open Babyphone, including
automated tests, the real-device reliability test matrix, and release
verification guidance.

## Automated Tests

### Unit Tests (JVM)

Run with:

    # ./gradlew testReleaseUnitTest

Covers:
- Audio protocol: `FrameCodec`, `FrameHeader`, `G711UCodec`, `JitterBuffer`
- Client management: `ClientManager` lifecycle and queue behavior
- Crypto: `CryptoHelper` key derivation and encrypt/decrypt round-trip
- Volume: `VolumeStatistics` ring buffer and normalization, `VolumeHistory`
- Pairing: `PairingCode` validation, `PairingCodeGenerator`, `PairingQrCode` parsing
- Handshake: protocol handshake serialization
- ViewModels: `DiscoverViewModel`, `MonitorViewModel`, `ListenViewModel`
- Trusted child: `TrustedChildStore` persistence and lookup
- Microphone: `MicrophoneSensitivity` gain levels
- Wi-Fi Direct: `WifiDirectErrorsTest`, `WifiDirectTxtRecordParserTest`
- Listen service: `ListenServiceAlertTest`
- Device name: `DeviceNameTest`

### Instrumentation Tests (Device/Emulator)

Run with:

    # ./gradlew connectedDebugAndroidTest

Covers:
- Crypto: `CryptoHelper` JNI/libsodium on-device behavior, `FrameCodecCryptoInstrumentedTest`
- Compose UI: `StartScreen`, `DiscoverScreen`, `MonitorScreen`, `DiscoverAddressScreen`
- Handshake: on-device challenge-response

### Lint

Run with:

    # ./gradlew lintRelease

### Release-Grade Verification

The CI-grade verification command is:

    # ./gradlew assembleRelease testReleaseUnitTest lintRelease

## Real-Device Reliability Test Matrix

Open Babyphone's core promise is overnight reliability. Automated tests
cannot cover Android power management, OEM battery restrictions, router
behavior, screen-off operation, or long-running foreground services.
The following matrix must be run before each public release.

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

If any screen-off or overnight test below fails because Android suspends CPU,
Wi-Fi, microphone capture, or playback, file a bug with device details and add
the narrowest scoped lock needed for that mode. Do not add a broad permanent
lock without a reproducer.

### OEM Battery Optimization Guidance

Some Android distributions can stop or throttle foreground services despite the
notification being visible. Before treating an overnight failure as an app bug,
record whether battery optimization was enabled for Open Babyphone and whether
the device has OEM-specific power management.

For release validation, run the overnight scenarios twice on at least one
aggressive-OEM device:
- With default battery optimization enabled
- With Open Babyphone exempted from battery optimization, if the default run
  fails or is unstable

Document any required OEM setting in the release notes and file a follow-up bug
if the app can improve its in-app guidance.

### Device Coverage

| Category | Requirement | Example |
|----------|-------------|---------|
| Old Android | Device near minSdk (Android 11, API 30) | Pixel 3 or equivalent |
| Modern Android | Device with current Android (14+, API 34) | Pixel 7/8 or equivalent |
| Aggressive OEM | Device with known battery optimization behavior | Samsung, Xiaomi, or Huawei |

### Test Scenarios

Each scenario should be run on at least one device from each category.

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

#### Multi-Parent

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

### Release Verification Checklist

Before tagging a release, record the following:

- [ ] Release version: ___
- [ ] Date of testing: ___
- [ ] Tester: ___
- [ ] Devices used: ___
- [ ] Android versions: ___
- [ ] Scenarios passed: ___ / 40
- [ ] Release keystore and passwords backed up outside the repository: yes / no
- [ ] APK signing certificate SHA-256 matches expected fingerprint: yes / no
- [ ] Known issues at release time: ___
- [ ] Bugs filed for failures: ___

File follow-up issues in the [issue tracker](https://github.com/digitalesIch/open-babyphone/issues)
for any failures found during matrix runs.
