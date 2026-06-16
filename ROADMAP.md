# Roadmap

This roadmap is based on the current project audit and prioritizes reliability,
privacy, and modernization before larger feature work.

## 1. Fix the Build and CI Baseline

- Ensure local and CI builds use JDK 21.
- Replace the obsolete Travis setup with a current CI workflow.
- Run `./gradlew assembleRelease testReleaseUnitTest lintRelease` as the release-grade check.
- Verify and fix the current `DiscoverActivity.kt` resolver call if the placeholder-like call is present in the actual source.

## 2. Add a Security Baseline Before New Features

- ✅ Add pairing/authentication before expanding connection support.
- ✅ Use a persistent alphanumeric pairing code so only approved parent devices can listen.
- Plan encrypted transport for the audio stream (see point 3).
- Avoid exposing the device model in the advertised service name.

## 3. Add Transport Encryption with Pairing Code

- Derive a 256-bit encryption key from the pairing code using a KDF (e.g., Argon2id).
- Use symmetric encryption with ChaCha20-Poly1305 (libsodium/NaCl) for the audio stream.
- Keep implementation lightweight: no certificates, no complex PKI.
- Ensure encryption only applies when a pairing code is configured (empty code = no auth, no encryption).
- Add a simple handshake to confirm both sides have the same pairing code before streaming.

## 4. Harden Audio and Foreground Services

- Validate `AudioRecord` and `AudioTrack` buffer sizes and initialization state.
- Handle negative `read()`/`write()` return values and audio device failures.
- Always stop and release audio resources safely.
- Rework foreground service behavior for modern Android versions.
- Add/request `POST_NOTIFICATIONS` for Android 13+.
- Avoid `START_REDELIVER_INTENT` for microphone recording services that should only start from explicit user action.

## 5. Modernize the Network Protocol

- Move toward a small frame-based audio stream.
- Include enough metadata to detect stale or missing frames, such as sequence numbers or timestamps.
- Prefer current audio over buffered old audio.
- Drop stale frames instead of building up latency.
- Add explicit connection, disconnect, timeout, and error handling.

## 6. Implement Multi-Client Listening

- Use pull request #17 as a technical reference, not as a direct merge.
- Keep one microphone recording pipeline on the child device.
- Fan out encoded audio frames to multiple parent clients.
- Use non-blocking I/O or bounded per-client queues so one slow parent does not block others.
- Add a maximum client limit.
- Drop or disconnect slow clients when their buffers fall behind.
- Show the number of connected parent devices in the child UI.

## 7. Improve Parent Playback

- Reduce playback latency with a small controlled jitter buffer.
- Detect stream loss quickly and show clear UI feedback.
- Add optional reconnect behavior.
- Play an alert only when the stream is truly lost, not for transient recoverable states.

## 8. Refresh the UI and UX

- Replace the Holo theme with a modern Material 3 or equivalent app theme.
- Add Day/Night support.
- Improve start, pairing, discovery, connected, disconnected, and error states.
- Make layouts robust for large font sizes, small screens, landscape, and long translations.
- Add accessibility semantics for inputs, status text, and the volume visualization.
- Replace visual-only volume feedback with accessible summary/status text.

## 9. Revisit the GSM Idea

- Reconsider the README item about using GSM when no internet connectivity is available.
- Android apps generally cannot use the GSM voice channel as a practical data transport.
- Prefer documenting VPN use for different networks.
- Treat WebRTC, STUN/TURN, or a relay server as a separate large feature if remote use becomes a goal.

## 10. Add Tests and Release Hardening

- Add tests for codec behavior, port validation, pairing, protocol framing, and frame dropping.
- Add integration or instrumentation tests for service startup and connection states where feasible.
- Test on real devices across older and newer Android versions.
- Cover screen-off behavior, background behavior, network loss, reconnects, and multiple parent devices.

## Suggested Order

Build and CI baseline first, then security and pairing, then transport encryption,
then audio/service stability, then the frame-based protocol, then multi-client support,
then parent playback, then the UI refresh, then README cleanup and broader test coverage.
