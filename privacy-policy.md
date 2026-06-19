# Privacy Policy for Open Babyphone

This privacy policy applies to Open Babyphone, an Android application that turns two Android devices into a local baby monitor.

Open Babyphone is an independent fork of Child Monitor and is maintained at https://github.com/digitalesIch/open-babyphone.

## Product Scope

Open Babyphone is designed for devices on the same Wi-Fi or local network. Advanced users may connect across a trusted VPN by entering the child device address manually. The app does not provide remote access, relay servers, cloud connectivity, user accounts, or internet-based discovery.

## Information Processed by the App

The app records microphone audio on the child device while child mode is active. That audio is streamed directly from the child device to paired parent devices over the local network.

The app may store the following information locally on the device:

- Pairing code
- Last manually entered child device address and port
- Theme preference

This information is stored on the device and is not sent to the developers.

## Network Transmission

Audio is transmitted directly between the child and parent devices. Open Babyphone does not route audio through project-operated servers or third-party relay services.

In the current implementation, audio transport is encrypted with ChaCha20-Poly1305 when a non-empty pairing code is configured. An empty pairing code means no authentication and no transport encryption. The project direction is to make pairing and encrypted transport the safe default before public release.

## Data Collection by the Developers

Open Babyphone does not include analytics, advertising SDKs, telemetry, or automatic crash reporting. The developers do not automatically receive audio, pairing codes, IP addresses, device identifiers, or usage data from the app.

If you voluntarily report a bug or security issue through GitHub or another communication channel, the information you provide in that report will be processed for project maintenance.

## Third Parties

Open Babyphone does not intentionally share app data with third parties. If you install the app through an app store or package repository, that distributor may process download or account information under its own privacy policy.

## Backups

Android app backup is disabled for Open Babyphone. Pairing information should not be copied through Android app backup.

## Children

Open Babyphone is intended to be operated by parents, guardians, or caregivers. The app is not used to knowingly collect information from children, and it does not provide registration or account features.

## Changes

This policy may be updated when the app changes. The current version is available in the project repository at https://github.com/digitalesIch/open-babyphone.

## Contact

For privacy questions, contact the project maintainers through the GitHub repository.
