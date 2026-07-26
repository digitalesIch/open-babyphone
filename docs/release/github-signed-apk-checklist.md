# GitHub Signed APK Checklist

Use this checklist only in an isolated release environment. It documents future
publication work; it does not claim that versionCode 26 has been signed or
published. Never place keystores, passwords, or generated signing properties in
the repository or CI artifacts.

## Build And Sign

- [ ] Start from the exact reviewed release tag or immutable commit in a clean checkout.
- [ ] Use JDK 21, the checked-in wrapper, and strict dependency verification.
- [ ] Mount or copy the release keystore only into the isolated environment.
- [ ] Create `keystore.properties` outside version control with restrictive permissions.
- [ ] Run `./gradlew --dependency-verification strict clean assembleRelease`.
- [ ] Confirm the produced APK is signed and identify its exact path without renaming a different build.
- [ ] Remove temporary keystore/property copies and preserve the isolated key backup separately.

## Verify The Candidate

Record outputs; do not invent values:

```bash
apksigner verify --verbose --print-certs <signed-release.apk>
sha256sum <signed-release.apk> > <signed-release.apk>.sha256
```

- [ ] `apksigner verify` succeeds for the exact candidate.
- [ ] Certificate SHA-256: `_______________________________________________`
- [ ] Certificate matches the established GitHub APK release channel.
- [ ] Artifact SHA-256: `__________________________________________________`
- [ ] `zipalign -c -P 16 4 <signed-release.apk>` succeeds.
- [ ] Every packaged ELF and ABI passes the `LOAD` alignment check, or the APK contains no `.so` files.
- [ ] Install succeeds as an update over the previous public GitHub APK without uninstalling.
- [ ] The updated app starts and retained trusted-child behavior matches the release plan.
- [ ] Required API 30/API 36 and 16 KB CI results plus signed-candidate checks on the maintainer's available phones are attached to the release record.

## Publish And Recheck

- [ ] Upload the candidate and its checksum without exposing signing inputs.
- [ ] Record the exact downloadable release asset name and URL.
- [ ] Download that public asset into a new directory; do not reuse the local candidate path.
- [ ] Re-run `sha256sum` and `apksigner verify --verbose --print-certs` on the download.
- [ ] Install the exact downloaded asset as an update over the previous public APK.
- [ ] Publish the verified checksum and certificate SHA-256 with the release notes.
- [ ] State that F-Droid builds are independently signed and cannot update over the GitHub-signed channel.

R8 remains disabled for this release because enabling it without dedicated keep
rules and focused behavior checks on the available devices creates unacceptable release risk.
The resulting Bouncy Castle size overhead is accepted for versionCode 26.
