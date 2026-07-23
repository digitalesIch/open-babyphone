# Maintainer Device And UX Checklist

Open Babyphone is a no-budget project maintained by one developer. Release
validation uses the Android phones already available to the maintainer. It does
not require external participants, paid device labs, or purchasing devices for
OEM coverage.

## Available Devices

Record the actual inventory used for a release. Do not imply broader coverage.

| Device | Android | Child mode | Parent mode | Notes |
|---|---|---|---|---|
| Not recorded | Not recorded | Not run | Not run | Fill in for the release candidate |

## Core Walkthrough

Run the following on at least two available phones before a public release:

- Start child mode, grant the microphone permission, and confirm monitoring.
- Pair the parent by QR code and confirm that listening starts.
- Stop and resume both roles using the visible controls and notifications.
- Interrupt Wi-Fi briefly and confirm recovery or a clear parent alert.
- Lock both phones for at least 30 minutes and confirm capture and playback.
- Run one overnight session when audio, service, networking, or power behavior changed.
- Check the primary screens with 200 percent font scale and TalkBack when UI changed.

Use a third available phone for multi-parent behavior when practical. Higher
client counts are covered by automated tests and are not a manual release gate.

## Release Record

- Release/version: Not selected
- Date: Not run
- Maintainer: Not recorded
- Devices and Android versions: Not recorded
- Core walkthrough result: Not run
- Overnight result, if applicable: Not run
- Accessibility result, if applicable: Not run
- Known device-specific limitations: None recorded
- Follow-up issues: None recorded

Record only checks that were actually run. Missing OEM or Android-version
coverage is a documented limitation, not a reason to fabricate results.
