# Open Babyphone

[![CI](https://github.com/digitalesIch/open-babyphone/actions/workflows/ci.yml/badge.svg)](https://github.com/digitalesIch/open-babyphone/actions/workflows/ci.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%2B-brightgreen.svg)](#requirements)

Open Babyphone is a local-network Android baby monitor. It turns two Android
devices into a direct audio monitor for use at home: one device stays near the
child and streams microphone audio, the other device stays with the parent and
plays the stream.

The project is intentionally local-first. There are no cloud accounts, relay
servers, analytics SDKs, advertising SDKs, or remote listening features. Same
Wi-Fi or same LAN is the normal setup. Trusted VPN use is possible for advanced
users through manual address entry, but it is not the product goal.

## Project Status

Open Babyphone is under active fork development. It already has the core pieces
of a local audio monitor, but it should still be treated as pre-beta software
until overnight reliability, pairing defaults, and release packaging are fully
hardened.

Current foundations include:

- Same-Wi-Fi discovery with Android Network Service Discovery (NSD)
- Manual address entry for trusted VPNs or unusual local-network setups
- Persistent alphanumeric pairing code support
- Challenge-response authentication when a pairing code is configured
- ChaCha20-Poly1305 encrypted audio frames when a pairing code is configured
- Multi-parent audio fan-out from one microphone recording pipeline
- Parent-side reconnect behavior and a small jitter buffer
- GitHub Actions CI for release build, unit tests, and Android lint
- Unit and instrumentation tests for codec, framing, crypto, jitter buffer, and
  client management

## What Open Babyphone Is Not

Open Babyphone is not:

- An internet baby monitor
- A cloud relay service
- A video monitor
- A medical or safety-certified device
- A replacement for responsible supervision

The app is designed to be transparent and auditable, not infrastructure-heavy.
Features that require accounts, hosted backends, WebRTC/STUN/TURN services,
push infrastructure, or recurring SaaS dependencies are outside the current
direction.

## How It Works

1. Start Open Babyphone on the child device and choose **Use as Child Device**.
2. The child device advertises itself on the local network and listens for
   parent connections, starting at TCP port `10000`.
3. Start Open Babyphone on the parent device and choose **Use as Parent Device**.
4. Select the discovered child device, or enter its address and port manually.
5. Use the same pairing code on both devices to enable authentication and
   encrypted transport.

If the pairing code is empty, the connection is open: there is no parent
authentication and no transport encryption. Use a pairing code for normal use.

## Requirements

- Android 11 (R) or newer, SDK 30+
- Two Android devices on the same Wi-Fi or local network
- Microphone permission on the child device
- Notification permission on Android 13+ for foreground service notifications
- A trusted local network; VPN use is an advanced manual fallback

The Android application ID is `org.openbabyphone`. The internal Kotlin package
and Android namespace still use the inherited `de.rochefort.childmonitor` name
for now; that does not affect the installable app identity.

## Building From Source

Use the checked-in Gradle wrapper and JDK 21:

```bash
./gradlew assembleRelease testReleaseUnitTest lintRelease
```

Useful focused checks:

```bash
./gradlew assembleRelease
./gradlew testReleaseUnitTest
./gradlew assembleDebugAndroidTest
./gradlew lintRelease
```

Release signing is intentionally not configured in the repository. Do not commit
keystores, passwords, or signing secrets.

## Privacy And Security

Open Babyphone does not collect analytics, telemetry, crash reports, device
identifiers, audio recordings, or usage data for the developers. Audio is sent
directly from the child device to connected parent devices.

Security-relevant behavior:

- A non-empty pairing code enables challenge-response authentication.
- A non-empty pairing code also enables ChaCha20-Poly1305 encrypted audio frames.
- An empty pairing code means open local connections and unencrypted audio.
- Android backup is disabled so pairing data is not copied through app backup.

Read the full [privacy policy](privacy-policy.md) and [security policy](SECURITY.md)
before relying on the app in a sensitive environment.

## Roadmap

The current roadmap focuses on making the app safe, understandable, and reliable
before broad public distribution:

- Make pairing the normal safe default
- Improve the child/parent setup flow
- Finish the Material UX refresh
- Remove remaining legacy fork-era naming from user-visible paths
- Prove overnight reliability on real devices
- Finalize F-Droid metadata, release notes, and signing documentation

See [ROADMAP.md](ROADMAP.md) for the detailed plan.

## Contributing

Contributions are welcome if they align with the local-only, no-cloud product
direction. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request.

For security issues, do not open a public issue. Follow [SECURITY.md](SECURITY.md).

## Fork History And Attribution

Open Babyphone is an independent fork of [Child Monitor](https://github.com/enguerrand/child-monitor),
which itself is a fork of [Protect Baby Monitor](https://github.com/brarcher/protect-baby-monitor).
The original projects remain credited and licensed under GPLv3.

Audio file originals are from [freesound](https://freesound.org).

## License

Open Babyphone is licensed under the GPLv3. See [LICENSE](LICENSE).

The G.711 u-law codec code is derived from the Android Open Source Project and is
licensed under the Apache License, Version 2.0.
