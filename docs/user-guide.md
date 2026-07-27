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
2. Review the child name and generated pairing information.
3. Start monitoring.
4. Leave the child device near the child, connected to power when practical.

The child device advertises itself on the attached local network while
monitoring is active. Its pairing information remains protected by app-private
storage.

### Parent Device

1. Open Open Babyphone and choose the parent role.
2. Select the child found on the local network.
3. Scan the pairing QR code shown by the child device, or use manual pairing
   when scanning is unavailable.
4. Confirm the connection and listen to the audio stream.

After successful authentication, the parent can remember the trusted child and
reconnect in later sessions without scanning again. Use **Forget child** when a
stored relationship should be removed.

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

## Connection Help

Use the in-app Connection Help when discovery or pairing fails. Check these
points first:

1. Child monitoring is active.
2. Both devices are on the same reachable local network.
3. Android has granted the requested nearby-device, network, camera, microphone,
   and notification permissions for the selected flow.
4. The router or hotspot allows local devices to communicate with each other.
5. Battery optimization or OEM power management is not stopping the active
   foreground service.

If automatic discovery remains unavailable, try a phone hotspot, experimental
Wi-Fi Direct, or manual address entry on a trusted network.

## Privacy And Safety

Open Babyphone is intended for local audio monitoring and is not a medical or
safety-certified device. It is not a replacement for responsible supervision.

Read the [privacy policy](../privacy-policy.md) and
[security policy](../SECURITY.md) for the current data-handling and security
model.
