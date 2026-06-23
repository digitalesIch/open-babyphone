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
- Volume: `VolumeStatistics` ring buffer and normalization
- Pairing: `PairingCode` validation
- Handshake: protocol handshake serialization
- ViewModels: `DiscoverViewModel`, `MonitorViewModel`, `ListenViewModel`

### Instrumentation Tests (Device/Emulator)

Run with:

    # ./gradlew connectedDebugAndroidTest

Covers:
- Crypto: `CryptoHelper` JNI/libsodium on-device behavior
- Compose UI: `DiscoverScreen`, `MonitorScreen`, `DiscoverAddressScreen`
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

#### Network Resilience

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 11 | Wi-Fi disconnects on child for 10 seconds, reconnects | Parent reconnects automatically or alert fires |
| 12 | Wi-Fi disconnects on parent for 10 seconds, reconnects | Parent reconnects automatically or alert fires |
| 13 | Router restart (30 second outage) | Both devices recover without manual intervention |
| 14 | Child app killed and restarted | Parent reconnects or alerts |
| 15 | Parent app killed and restarted | Parent reconnects to child |

#### Multi-Parent

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 16 | 2 parents connect simultaneously | Both receive audio |
| 17 | 1 of 2 parents disconnects | Other parent continues without interruption |
| 18 | 5 parents connect (max) | All 5 receive audio |
| 19 | 6th parent attempts connection | 6th parent rejected gracefully |
| 20 | 1 parent disconnects from max, new parent connects | New parent connects successfully |

#### Alert Audibility

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 21 | Child disconnects, parent on phone speaker | Alert is audible |
| 22 | Child disconnects, parent on Bluetooth headphones | Alert is audible |
| 23 | Child disconnects, parent on wired headphones | Alert is audible |
| 24 | Child disconnects, parent in Do Not Disturb mode | Alert behavior documented (may be silenced by DND) |

#### Foreground Service

| # | Scenario | Pass Criteria |
|---|----------|---------------|
| 25 | Child foreground notification persists | Notification visible for entire session |
| 26 | Parent foreground notification persists | Notification visible for entire session |
| 27 | App backgrounded on child | Stream continues, foreground service active |
| 28 | App backgrounded on parent | Stream continues, foreground service active |

### Release Verification Checklist

Before tagging a release, record the following:

- [ ] Release version: ___
- [ ] Date of testing: ___
- [ ] Tester: ___
- [ ] Devices used: ___
- [ ] Android versions: ___
- [ ] Scenarios passed: ___ / 28
- [ ] Known issues at release time: ___
- [ ] Bugs filed for failures: ___

File follow-up issues in the [issue tracker](https://github.com/digitalesIch/open-babyphone/issues)
for any failures found during matrix runs.
