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

## 3. Add Transport Encryption with Pairing Code ✅

- ✅ Derive a 256-bit encryption key from the pairing code using a KDF (Argon2i).
- ✅ Use symmetric encryption with ChaCha20-Poly1305 (libsodium/NaCl) for the audio stream.
- ✅ Keep implementation lightweight: no certificates, no complex PKI.
- ✅ Ensure encryption only applies when a pairing code is configured (empty code = no auth, no encryption).
- ✅ Use 2-byte length prefix per chunk for robust TCP framing.
- ✅ Use 64-bit counter as nonce (big-endian, zero-padded to 12 bytes).

## 4. Harden Audio and Foreground Services ✅

- ✅ Validate `AudioRecord` and `AudioTrack` buffer sizes and initialization state.
- ✅ Handle negative `read()`/`write()` return values and audio device failures.
- ✅ Always stop and release audio resources safely (`release()` calls added).
- ✅ Rework foreground service behavior for modern Android versions.
- ✅ Add/request `POST_NOTIFICATIONS` for Android 13+.
- ✅ Changed `START_REDELIVER_INTENT` to `START_NOT_STICKY` for explicit user control.
- ✅ Socket timeout set to 20 seconds for timely stream-loss detection.

## 5. Modernize the Network Protocol ✅

- ✅ Implement frame-based audio streaming (11-byte header + payload).
- ✅ Sequence numbers (uint32) for gap detection and logging.
- ✅ Timestamps (uint32, ms since session start) for stale frame detection.
- ✅ Heartbeat frames every 5 seconds for connection monitoring.
- ✅ 10-second heartbeat timeout for faster dead connection detection (was 20-30s).
- ✅ Drop frames older than 200ms (prefer current audio over buffered old audio).
- ✅ Integration with existing ChaCha20-Poly1305 encryption.
- ✅ ~27 bytes overhead per frame (11 header + 16 auth tag).

## 6. Implement Multi-Client Listening ✅

- ✅ Keep one microphone recording pipeline on the child device (audio producer thread).
- ✅ Fan out encoded audio frames to multiple parent clients (ClientManager.broadcastFrame).
- ✅ Use bounded per-client queues (ArrayBlockingQueue, 100 frames = ~200ms) so slow parents don't block others.
- ✅ Maximum client limit of 5 (configurable in ClientManager.MAX_CLIENTS).
- ✅ Drop or disconnect slow clients when their buffers fall behind (MAX_DROPPED_FRAMES = 50).
- ✅ Show the number of connected parent devices in the child UI ("Connected: X parents").
- ✅ NSD unregisters only when max clients reached (new clients can connect until then).

## 7. Improve Parent Playback ✅

- ✅ Add 100ms jitter buffer for smooth playback (smooths network jitter).
- ✅ Two-stage stream loss detection: 5s "disrupted" (UI feedback) + 10s "lost" (alert).
- ✅ Auto-reconnect: 5 attempts, 2s delay between each (10s total recovery time).
- ✅ Alert plays ONLY when stream is truly lost (after reconnect exhausted or 10s timeout).
- ✅ Status updates: "Connected" → "Connection disrupted..." → "Reconnecting (X/5)..." → "Disconnected".
- ✅ Separate receive and playback loops (receive fills buffer, playback consumes from buffer).

## 8. Refresh the UI and UX ✅

- ✅ Replace Holo theme with Material 3 design system.
- ✅ Full Day/Night support with automatic system-follow.
- ✅ Manual theme override in Settings (System/Light/Dark).
- ✅ Blue primary color (calming for nighttime), orange accent (visible alerts).
- ✅ ConstraintLayout for responsive, robust layouts.
- ✅ Material components: MaterialButton, MaterialTextView, TextInputLayout.
- ✅ Accessibility improvements: content descriptions, label associations.
- ✅ Settings Activity for theme preferences.
- ✅ OpenBabyphoneApplication for theme persistence across restarts.

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
