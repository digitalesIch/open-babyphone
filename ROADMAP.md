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

- Keep the Android application ID, namespace, and internal package aligned on `org.openbabyphone` before release
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
- Add focused unit or instrumentation tests for service lifecycle, reconnect, and failure states
- Maintain a real-device test matrix across old and modern Android versions (see [docs/testing.md](docs/testing.md))

### 2a. Support Multiple Simultaneous Parent Devices

Goal: make multi-parent listening reliable enough for real household use.

The child device already has one microphone pipeline and fans out encoded audio
frames to up to 5 connected parent devices via `ClientManager`. The basic mechanism
exists, but the lifecycle around connect, disconnect, max-clients, and runtime
pairing changes needs hardening before public beta.

- Keep the server socket lifecycle stable when max clients are reached
- Re-register NSD when capacity becomes available again after a parent disconnects
- Add a callback from `ClientManager` to `MonitorService` for client count changes and removals
- Update child UI reliably when parents connect, disconnect, or are dropped as slow clients
- Prevent busy-looping when max clients are reached
- Avoid AEAD nonce reuse when multiple parents authenticate in the same session
- Handle pairing code changes while parents are connected without breaking their audio
- Add integration tests covering 2-5 parents connecting, disconnecting, and reconnecting
- Document multi-parent support in README, store metadata, and UI

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
- Keep Android 11+ compatibility unless a future product decision changes the minimum SDK

## 5. Future Local-Only Improvements

Goal: improve the in-home baby monitor without expanding into remote infrastructure.

- Improve local discovery diagnostics for routers that block multicast/mDNS
- Consider configurable sensitivity or noise-gate features only if they do not compromise safety
- Improve local troubleshooting guidance for trusted VPN users without making VPN the main flow
- Revisit larger features only if they preserve the local-only, no-cloud product promise

### 5a. Improve No-Wi-Fi Local Setups

Goal: make Open Babyphone practical in places without an existing Wi-Fi network,
such as holiday homes.

Open Babyphone already works over any local network path. The immediate approach
is documentation for Android hotspot setups: either the child device creates a
local hotspot and the parent joins it, or the parent device creates a hotspot and
the child joins it. Internet access is not required for Open Babyphone itself;
internet availability depends on which device provides the hotspot and whether
that device has an upstream mobile data connection.

- Document child-device hotspot setup and its battery implications
- Document parent-device hotspot setup and mobile-data considerations
- Explain that all local-only modes require both devices to stay within wireless range
- Keep third-device/travel-router setups as a simple no-code option in troubleshooting docs

### 5b. Add WiFi Direct Connection Support

Goal: support a direct local device-to-device connection without an existing Wi-Fi
network or normal Android hotspot setup.

WiFi Direct should be explored as the medium-term local-only answer for holiday
homes and other no-infrastructure setups. It may also allow the parent device to
keep mobile data available for other apps while Open Babyphone uses the WiFi
Direct data path, but routing behavior must be verified on real devices.

- Add `WifiP2pManager`-based discovery and group formation
- Keep existing NSD/manual-address flows as fallbacks
- Verify parent mobile data behavior while WiFi Direct is active
- Test on OnePlus 3T (LineageOS 18.1) and modern Android devices
- Document known vendor/ROM limitations

### 5c. Explore Wi-Fi Aware / NAN

Goal: evaluate whether Wi-Fi Aware is practical for future local peer-to-peer use.

Wi-Fi Aware can discover nearby devices and create direct data paths without a
normal access point, but Android device support and real-world reliability vary.
This is a research item, not an implementation commitment.

- Check API and hardware availability across target devices
- Test whether continuous audio streaming is reliable
- Verify whether parent mobile data remains usable for other apps
- Compare complexity and reliability against WiFi Direct

### 5d. Migrate Audio Codec from G.711 u-law to Opus

Goal: replace the legacy 8 kHz G.711 u-law codec with Opus for better audio quality,
lower bandwidth, and broader frequency response.

Depends on: #25 (protocol versioning and capability negotiation).

The current G.711 u-law codec at 8 kHz limits the audio to about 3.4 kHz usable
bandwidth and 64 kbps. Opus at 16-24 kbps delivers clearly better voice quality
and supports up to 48 kHz full-band audio. This migration is a prerequisite for
meaningful noise suppression because DSP and ML-based filters need broader
spectrum than G.711 provides.

- Replace `G711UCodec` with Opus via Android `MediaCodec`
- Raise sample rate from 8000 Hz to 16000 or 24000 Hz
- Adapt frame sizes, jitter buffer timing, and heartbeat intervals to Opus frame durations
- Rename `ulawData` to a codec-agnostic name throughout the codebase
- Use protocol versioning from #25 to negotiate codec between child and parent
- Test on OnePlus 3T (LineageOS 18.1) and modern devices
- Keep bandwidth and latency within limits for multi-parent fan-out

### 5e. White Noise on Child Device with Noise Suppression on Parent Device

Goal: let the child device play soothing white noise for the baby while the parent
device filters it out so parents hear the baby clearly.

Depends on: Opus migration (5d), because noise suppression needs broader spectrum
than G.711 8 kHz provides.

White noise without suppression is not practical: parents would hear constant noise
and might turn down volume, missing the baby. These two features must ship together
as a coupled package.

- Child device plays white noise (or similar soothing sounds) via `AudioTrack`
- Microphone continues recording and streaming to parents
- Parent device applies noise suppression to remove the white noise from the stream
- Consider spectral subtraction, Wiener filter, or ML-based suppression
- Learn noise profile from a short silence calibration or from known white-noise spectrum
- Ensure suppression does not muffle or remove baby cries
- Add UI controls on child device: start/stop white noise, volume, sound type
- Add UI indicator on parent device: noise suppression active
- Test: baby audibility with white noise running and suppression active

## Immediate Technical Risk Backlog

These items are tracked as GitHub issues and should be handled before a public beta:

- #2 Remove or guard production logs containing local network details
- #13 Centralize service type, default port, and discovery naming constants
- #19 Harden the max-client accept loop and NSD re-registration behavior
- #20 Fix TCP frame reads so partial headers and payload reads are handled correctly
- #21 Keep the Android application ID, namespace, and internal package aligned on `org.openbabyphone` before release
- #22 Finish the Material UX refresh for monitor, discovery, and listen screens
- #23 Stop exposing device model and legacy `ChildMonitor` name in NSD
- #24 Define and run an overnight reliability test matrix on real devices
- #25 Add protocol versioning and capability negotiation
- #26 Update the privacy policy and security model whenever pairing/encryption defaults change
- #27 Make pairing default-safe with generated codes and a QR/code parent flow
- #30 Prevent pairing code changes from breaking audio for connected parents
- #31 Fix MonitorService busy-loop when max clients are reached
- #35 Prevent concurrent listen threads from causing mixed streams and leaks
- #47 Avoid AEAD nonce reuse when multiple parents authenticate in the same session
- #51 Add multi-parent integration test
- #52 Document multi-parent support in README and UI
- #69 Migrate audio codec from G.711 u-law to Opus (depends on #25)
- #70 Add white noise on child device with noise suppression on parent device (depends on #69)
- #77 Add WiFi Direct connection support
- #78 Explore Wi-Fi Aware (NAN) for local peer-to-peer connections
