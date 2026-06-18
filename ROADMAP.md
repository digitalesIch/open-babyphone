# Roadmap 2.0

This roadmap reflects the current product direction for Open Babyphone: a modern,
open source, local-only Android baby monitor for use at home.

## Product Vision

Open Babyphone should become a trustworthy local baby monitor that:

- Works reliably overnight on real Android devices
- Streams audio directly between devices on the same Wi-Fi or local network
- Uses safe pairing and encrypted transport by default
- Is understandable for non-technical parents and caregivers
- Is transparent, auditable, and suitable for F-Droid distribution
- Avoids cloud services, accounts, relay servers, tracking, and recurring infrastructure

Trusted VPN use remains an advanced manual setup note. It is not the normal setup
flow and not a remote-access product goal.

## Non-Goals

- No internet-based remote listening feature
- No cloud relay, hosted backend, user accounts, push service, or paid SaaS dependency
- No WebRTC/STUN/TURN infrastructure unless explicitly revisited as a separate product
  direction, which is currently not planned
- No video feature before audio reliability, privacy, and release quality are solved
- No AI, sleep tracking, or extra sensors before the core baby monitor experience is stable

## Current Baseline

The fork already has several important foundations:

- GitHub Actions CI for release build, unit tests, and lint
- Optional persistent pairing code for parent authentication
- Optional ChaCha20-Poly1305 transport encryption when a pairing code is configured
- Frame-based audio protocol with sequence numbers, timestamps, and heartbeats
- Jitter buffer and parent-side reconnect behavior
- Multi-client audio fan-out from one microphone pipeline
- Initial Material 3 theme work and day/night support
- Unit tests for codec, framing, jitter buffer, crypto, and client management

The remaining work is mostly product hardening: safe defaults, real-device reliability,
clear UX, release readiness, and removing stale fork-era assumptions.

## 1. Open Beta Basis

Goal: make the fork coherent, safe by default, and honest in its public materials.

- Decide whether to migrate the `applicationId` before public release
- Remove or contain user-visible legacy `ChildMonitor` naming in code paths, logs, and NSD
- Stop exposing the Android device model in the advertised NSD service name
- Keep README, privacy policy, security policy, store metadata, and roadmap aligned with
  the local-only product direction
- Make pairing the normal safe path: generate a strong child pairing code automatically
- Avoid presenting an empty pairing code as the normal configuration
- Add a simple QR/code-based parent setup path before treating manual address entry as normal
- Add protocol versioning and capability negotiation before further protocol changes
- Keep manual address entry as an advanced fallback for trusted VPN or unusual LAN setups

## 2. Reliability Release

Goal: prove the app can run through the night on real devices.

- Test child and parent mode with screen off, locked screen, and charger connected/disconnected
- Define a clear WakeLock/Wi-Fi lock strategy or document why it is not needed
- Handle Android Doze, OEM battery restrictions, and foreground service edge cases
- Reconnect cleanly after Wi-Fi loss, network changes, router sleep, and child restarts
- Make parent-side alert behavior reliable and understandable
- Review audio focus, volume stream, Bluetooth, headphones, and Do Not Disturb limitations
- Harden multi-client handling when the max client count is reached or clients disconnect
- Add focused unit or instrumentation tests for service lifecycle, reconnect, and failure states
- Maintain a real-device test matrix across old and modern Android versions

## 3. UX Release

Goal: make normal use simple enough for tired parents at night.

- Build the default flow around "Start child device" and "Pair parent device"
- Make discovery the normal path and manual IP/port entry clearly advanced
- Replace remaining legacy XML/platform-widget screens with consistent Material components
- Show large, calm status states: connected, waiting, disrupted, reconnecting, lost
- Show child-side essentials: microphone active, parents connected, network, battery, address fallback
- Improve parent-side essentials: connected child, stream health, alert state, volume visualization
- Reduce Toast-based feedback for important states and prefer persistent UI state
- Improve accessibility labels, text scaling, contrast, and landscape/tablet behavior

## 4. F-Droid Release

Goal: make the project installable and maintainable as an open source Android app.

- Finalize F-Droid metadata, screenshots, icon assets, privacy policy, and release notes
- Audit native and crypto dependencies for F-Droid build compatibility
- Document release signing and local verification without committing secrets
- Decide minification/R8 posture and keep rules before enabling release optimizations
- Tag releases with clear version names and changelogs
- Ensure `./gradlew assembleRelease testReleaseUnitTest lintRelease` remains the release-grade check
- Keep Android 5.0+ compatibility unless a future product decision changes the minimum SDK

## 5. Future Local-Only Improvements

Goal: improve the in-home baby monitor without expanding into remote infrastructure.

- Improve local discovery diagnostics for routers that block multicast/mDNS
- Add local-only audio quality improvements after reliability is stable
- Consider configurable sensitivity or noise-gate features only if they do not compromise safety
- Improve local troubleshooting guidance for trusted VPN users without making VPN the main flow
- Revisit larger features only if they preserve the local-only, no-cloud product promise

## Immediate Technical Risk Backlog

These items are tracked as GitHub issues and should be handled before a public beta:

- #2 Remove or guard production logs containing local network details
- #13 Centralize service type, default port, and discovery naming constants
- #19 Harden the max-client accept loop and NSD re-registration behavior
- #20 Fix TCP frame reads so partial headers and payload reads are handled correctly
- #21 Decide and migrate `applicationId` before public release if the fork should be a distinct app
- #22 Finish the Material UX refresh for monitor, discovery, and listen screens
- #23 Stop exposing device model and legacy `ChildMonitor` name in NSD
- #24 Define and run an overnight reliability test matrix on real devices
- #25 Add protocol versioning and capability negotiation
- #26 Update the privacy policy and security model whenever pairing/encryption defaults change
- #27 Make pairing default-safe with generated codes and a QR/code parent flow
