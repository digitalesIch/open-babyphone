# Open Babyphone — App UI Redesign

This document captures the Open Design brand-based redesign of the Open Babyphone
Android app. It is a design handoff snapshot; the rendered mockups live in
`app-ui-redesign.html` (open in a browser).

## Brand palette

| Role | Token | Hex |
| --- | --- | --- |
| background (dark) | Night monitor canvas | `#080B12` |
| surface (dark) | Deep panel | `#101827` |
| foreground (dark) | Soft white text | `#F6F7FB` |
| muted (dark) | Quiet blue-grey | `#A5B2C8` |
| accent (live audio) | Live audio cyan | `#5FF2D2` |
| accent (network) | Network blue | `#5DA8FF` |
| background (light) | Soft white | `#F6F7FB` |
| foreground (light) | Deep panel | `#101827` |

## Layout tokens

- Radius: 8px (controls), 12px (mark), 28px (phone/large cards)
- Border: 1px translucent hairline
- Spacing: 2 / 4 / 8 / 12 / 16 / 24 / 32 dp

## Compose mapping

| OD concept | Compose target |
| --- | --- |
| Night canvas background | `ColorScheme.background` (dark) |
| Deep panel surface | `ColorScheme.surface` (dark) |
| Soft white text | `ColorScheme.onBackground` / `onSurface` (dark) |
| Quiet blue-grey muted | `ColorScheme.onSurfaceVariant` |
| Live audio cyan | `ColorScheme.primary` |
| Network blue | `ColorScheme.secondary` |
| Monitoring and listening state cards | `MonitorScreen`, `ListenScreen` |
| Six-step audio signal meter | `ListenScreen` `AudioSignalIndicator` |
| QR code card | `MonitorScreen` setup section |
| Paired CTA cards | `StartScreen` |

## Screens covered

1. Start — brand logo tile, tagline, two CTA cards
2. Child Setup — device name, pairing code + QR, Start Monitoring
3. Child Monitoring — monitoring state, parent count, pairing access, Stop Monitoring
4. Parent Discover — trusted child states, discovery, and advanced connection options
5. Parent Listen — verified playback state, six-step audio meter, Disconnect

## Note

Android 12+ dynamic color is disabled by default so the brand palette is stable.
A future "Use system colors" setting is tracked in issue #137.

The current Compose implementation and screenshot-test references are the source
of truth when this handoff differs from shipped UI details. Primary buttons are
solid theme colors, the launcher mark uses overlapping filled phones, and the
listening screen uses the six-step audio meter instead of a waveform.

## Preview

![App UI redesign](app-ui-redesign-preview.svg)
