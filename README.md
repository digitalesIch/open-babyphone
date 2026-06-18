# Open Babyphone
Local-network Android baby monitor without cloud accounts or remote access.

_Open Babyphone_ allows two Android devices to act as a baby monitor. The first device,
left in the room with the baby, advertises itself on the local network and streams audio
directly to a paired parent device. The second device, with the parent, connects on the
same Wi-Fi or local network and plays the received audio stream.

The product direction is intentionally local-only: no cloud service, no accounts, no relay
servers, and no remote listening feature. Reliability, privacy, and a simple in-home setup
are higher priorities than internet-based remote access.

The project is an independent fork of _Child Monitor_, which itself is a fork of
_Protect Baby Monitor_. The original projects remain credited and licensed under GPLv3.

_Open Babyphone_ works on Android 5.0 (Lollipop) and newer, i.e. Android SDK 21.

# Advanced: Trusted VPNs

Open Babyphone is designed for devices on the same Wi-Fi or local network. Advanced users
may connect across a trusted VPN by entering the child device address manually. Auto
discovery is LAN-only and is not expected to work across VPNs. The child device usually
binds to port 10000 unless that port is already taken.

VPN support is a manual advanced setup note, not a remote-access product goal.

# License information
_Open Babyphone_ is licensed under the GPLv3. The Ulaw encoding / decoding code is licensed under the Apache License, Version 2.0 and taken from the Android Open Source Project.

# Thanks
Audio file originals from [freesound](https://freesound.org).
This whole project originally created by [brarcher](https://github.com/brarcher/protect-baby-monitor).
