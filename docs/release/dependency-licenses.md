# Direct Dependency License Inventory

Reviewed 2026-07-23 for Open Babyphone `1.1.0-alpha.11` (`versionCode 26`).
This is a human-reviewed inventory of dependencies declared directly by the
Gradle build and of shipped attributed assets. It is not a complete transitive
software bill of materials. Gradle dependency verification records checksums,
not license conclusions, and the build does not currently generate a reviewed
CycloneDX/SPDX transitive inventory.

One relevant transitive artifact is AndroidX's `libandroidx.graphics.path.so`,
packaged for four ABIs. It is covered by the AndroidX Apache-2.0 licensing entry
but is not a directly declared dependency. The app has no NDK build and does not
use that library for cryptography.

## Runtime Dependencies

| Dependency | Version | License | Project |
|---|---:|---|---|
| Bouncy Castle `bcprov-jdk15to18` | 1.85.1 | MIT | https://www.bouncycastle.org/ |
| AndroidX Core/Core KTX | 1.19.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Lifecycle runtime/viewmodel Compose | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX AppCompat | 1.7.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Core Splashscreen | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Google Material Components | 1.14.0 | Apache-2.0 | https://github.com/material-components/material-components-android |
| Jetpack Compose BOM and declared Compose modules | 2026.06.01 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| AndroidX Navigation Compose | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Kotlinx Serialization JSON | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| ZXing Core | 3.5.3 | Apache-2.0 | https://github.com/zxing/zxing |
| JourneyApps ZXing Android Embedded | 4.3.0 | Apache-2.0 | https://github.com/journeyapps/zxing-android-embedded |

## Build And Test Dependencies

| Dependency | Version | License | Project |
|---|---:|---|---|
| Gradle wrapper | 9.4.1 | Apache-2.0 | https://gradle.org/ |
| Android Gradle Plugin | 9.2.1 | Apache-2.0 | https://developer.android.com/build |
| Kotlin Gradle, Compose, and Serialization plugins | 2.4.0 | Apache-2.0 | https://kotlinlang.org/ |
| Compose Screenshot plugin/API | 0.0.1-alpha15 | Apache-2.0 | https://developer.android.com/studio/preview/compose-screenshot-testing |
| Foojay toolchain resolver convention | 0.8.0 | Apache-2.0 | https://github.com/gradle/foojay-toolchains |
| JaCoCo | 0.8.14 | EPL-2.0 | https://www.jacoco.org/jacoco/ |
| JUnit 4 | 4.13.2 | EPL-1.0 | https://junit.org/junit4/ |
| Robolectric | 4.12.2 | MIT | https://robolectric.org/ |
| AndroidX Test libraries | 1.7.0 / 1.3.0 / 3.7.0 | Apache-2.0 | https://developer.android.com/training/testing |
| Mockito Core | 5.7.0 | MIT | https://github.com/mockito/mockito |
| Kotlinx Coroutines Test | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |

## Attributed Assets

| Asset | License | Source |
|---|---|---|
| Freesound 263655, "Upward Beep, chromatic fifths," by Mossy4; shipped as an OGG conversion | CC-BY-4.0 | https://freesound.org/people/Mossy4/sounds/263655/ |
| Material Icons by Google | Apache-2.0 | https://github.com/google/material-design-icons |
| Material Design Icons by Pictogrammers | Apache-2.0 | https://github.com/Templarian/MaterialDesign |

The full Bouncy Castle MIT text is in `BOUNCY_CASTLE_LICENSE.txt`. Project and
asset notices are in `NOTICE`. Before a release, review direct dependency
changes, regenerate SHA-256 metadata intentionally, and separately inspect the
resolved transitive graph; do not describe this inventory as a full SBOM.
