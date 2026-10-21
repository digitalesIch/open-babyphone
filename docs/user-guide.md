# Open Babyphone User Guide

This guide describes the current setup and connection options. Screen wording
may evolve, but the child/parent roles and local-network model stay the same.

## Requirements

- Two Android devices running Android 11 or newer
- A local network path between the devices
- Microphone access on the child device
- Notification access where Android requires it for foreground services
- Camera access on the parent device only when scanning a pairing QR code

Open Babyphone does not need internet access for the audio connection itself.

## First-Time Setup

### Child Device

1. Open Open Babyphone and choose the child role.
2. Start monitoring. A child name and pairing information are generated
   automatically; rename the phone later in Settings if needed.
3. Leave the child device near the child, connected to power when practical.

The child device advertises itself on the attached local network while
monitoring is active. Its pairing information remains protected by app-private
storage. The pairing QR code is shown directly on the monitoring screen while no
parent is connected.

### Parent Device

1. Open Open Babyphone and choose the parent role.
2. Scan the child QR code, or choose **Can't scan the code?** for manual pairing.
3. Wait for **Ready to listen**. If the child cannot be found, use Retry, Scan
   again, or Connection Help.
4. Move the parent device away from the child device and choose **Start
   listening** to avoid audio feedback.

After successful authentication, the parent can remember the trusted child and
reconnect in later sessions with one **Listen** action. Use **Forget child** in
Settings or Connection Help when a stored relationship should be removed.

### Listening Controls

While listening, the parent can choose **Mute** to silence the speaker without
dropping the stream or the session. **Unmute** restores the previous volume.
Connection-loss and audio-interruption alerts still play while the stream is
muted. Use **Disconnect** to end the session; Back asks for the same
confirmation.

## Connection Options

### Same Wi-Fi Or LAN

This is the normal and most reliable setup. Both devices join the same Wi-Fi or
otherwise share a reachable local network. The parent discovers an active child
automatically where the network permits local service discovery.

### Phone Hotspot

When no router is available, either phone can provide a Wi-Fi hotspot and the
other device can join it. Open Babyphone only needs the local link; whether that
hotspot also provides internet access depends on the phone and its Android
configuration.

Hotspot use increases battery consumption. Keep the child device connected to
power when possible.

### Wi-Fi Direct

Wi-Fi Direct is an experimental alternative for nearby devices without an
existing Wi-Fi network. Device and ROM support varies. If discovery or group
formation fails, use a hotspot or another local-network setup instead.

### Manual Address

Manual address entry is an advanced fallback for trusted VPNs or local networks
where automatic discovery is unavailable. Both devices must still be mutually
reachable; Open Babyphone does not provide an internet relay.

## Pairing And Trusted Children

The supported setup uses a child pairing code for mutual authentication and
encrypted transport. The QR code is a convenient way to transfer the same
pairing information without typing it.

A parent stores credentials only after successful authentication. Resetting
pairing on the child invalidates older parent credentials. Re-pair the parent
after a reset, or remove the old relationship with **Forget child**.

## Multiple Parent Devices

More than one parent device can listen to the same active child session. Each
parent pairs independently. The current implementation accepts up to five
simultaneous parent connections and removes a slow or disconnected parent
without stopping the others.

## Microphone Sensitivity

Child mode provides software microphone sensitivity levels. Start with Normal
and increase sensitivity only when quiet sounds are not reaching the parent
clearly. Higher gain can also amplify room noise and clipping.

## Appearance

Settings offers System, Light, and Dark theme modes. On Android 12 and later,
**Use system colors** follows the system Material You palette; the Open
Babyphone brand colors remain the default. The option is shown disabled on
older Android versions.

## Connection Help

Use the in-app Connection Help when discovery or pairing fails. Check these
points first:

1. Child monitoring is active.
2. Both devices are on the same reachable local network.
3. Android has granted the permission needed for the selected flow: microphone
   for child monitoring, camera for QR scanning, nearby devices or location only
   for Wi-Fi Direct, and notifications for connection alerts. Normal LAN
   discovery does not require nearby-device, location, or camera permission.
4. The router or hotspot allows local devices to communicate with each other.
5. Battery optimization or OEM power management is not stopping the active
   foreground service.

If automatic discovery remains unavailable, try a phone hotspot, experimental
Wi-Fi Direct, or manual address entry on a trusted network.

## Privacy And Safety

Open Babyphone is intended for local audio monitoring and is not a medical or
safety-certified device. It is not a replacement for responsible supervision.

Read the [privacy policy](privacy.md) and
[security policy](security.md) for the current data-handling and security
model.
