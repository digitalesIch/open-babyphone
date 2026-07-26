# F-Droid Source-Build Checklist

This is local preparation for a future external submission. The actual recipe
must be reviewed and submitted to `fdroid/fdroiddata`; nothing in this repository
claims F-Droid acceptance, scanner success, or publication.

## Build Facts

- Application ID: `org.openbabyphone`
- Version: `1.1.0-alpha.11`, `versionCode 26`
- Minimum Android: API 30; target SDK remains 34; compile SDK is 37
- Required JDK: 21; generated JVM bytecode targets Java 17
- Checked-in Gradle wrapper: 9.4.1; Android Gradle Plugin: 9.2.1
- Source command: `./gradlew --dependency-verification strict assembleRelease`
- Unsigned output: `app/build/outputs/apk/release/app-release-unsigned.apk`
- The app has no NDK build and no native cryptography dependency. Cryptography
  uses the pure-Java Bouncy Castle dependency and Android JCA/Keystore; Bouncy
  Castle source and license information are publicly available upstream.
- The current release APK does contain the transitive AndroidX library
  `libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and
  `x86_64`. It is not a cryptography library. Review it explicitly in scanner
  output and verify every ELF `LOAD` alignment rather than claiming a
  native-library-free APK.
- R8 remains disabled for versionCode 26. Enabling shrinking this late could
  change reflection, serialization, and security-sensitive behavior; the
  Bouncy Castle size cost is accepted until dedicated rules and focused checks
  are reviewed.
- `targetSdk 36` is blocked on API 36 behavior checks on the maintainer's
  available phones; broader paid device-lab coverage is not required.

## Recipe Review

- [ ] Work in a clean clone of `fdroid/fdroiddata`, not this repository.
- [ ] Replace the template commit marker with an immutable reviewed tag or SHA.
- [ ] Confirm source version fields exactly match `app/build.gradle`.
- [ ] Confirm JDK 21, Gradle wrapper 9.4.1, AGP 9.2.1, and Android SDK 37 are available.
- [ ] Do not add prebuilt libraries, opaque binaries, signing material, or network downloads in build steps.
- [ ] Review `gradle/verification-metadata.xml` changes against declared dependency updates.
- [ ] Confirm `zipalign -c -P 16 4` and per-ELF `LOAD` alignment checks pass on the resulting APK.

## Scanner And Clean Builds

Run these in the external fdroidserver environment and record their real output;
do not pre-fill results in this repository:

```bash
fdroid readmeta
fdroid lint org.openbabyphone
fdroid scanner org.openbabyphone
fdroid build --verbose --test org.openbabyphone:26
fdroid build --verbose --on-server org.openbabyphone:26
```

Verify Gradle can repeat the source build without network access after the
approved dependency cache is populated:

```bash
./gradlew --stop
./gradlew --offline --dependency-verification strict clean assembleRelease
```

- [ ] Scanner output reviewed with no unexplained binaries or dependencies.
- [ ] Clean fdroidserver build passed from the immutable source revision.
- [ ] Offline Gradle build passed using only the prepared dependency cache.
- [ ] Built APK contains no unexpected `.so` files; if any appear, review every ABI and library.
- [ ] Reproducibility differences, if measured, are recorded rather than assumed.
- [ ] F-Droid signing behavior and its signature incompatibility with the GitHub APK channel are documented for users.
