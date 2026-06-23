How to Submit Patches to the Open Babyphone Project
===============================================================================
https://github.com/digitalesIch/open-babyphone

This document is intended to act as a guide to help you contribute to the
Open Babyphone project. It is not perfect, and there will always be exceptions
to the rules described here, but by following the instructions below you
should have a much easier time getting your work merged.

Open Babyphone is an independent fork of Child Monitor (which itself is a fork
of Protect Baby Monitor). We maintain attribution to the original projects
while developing Open Babyphone as a separate project focused on no-cloud,
local-network-first baby monitoring. Same Wi-Fi or same LAN use is the core
product goal; trusted VPN setups are treated as an advanced manual fallback,
not as a remote-access feature.

## Build Requirements

Use JDK 21 when building the project. The Android app is configured with
Java 17 source and target compatibility for Android tool compatibility.

## Test Your Code

There are three possible checks you can run while developing. The first is
unit tests, which check the basic functionality of the application, and can be
run by gradle using:

    # ./gradlew testReleaseUnitTest

The second check for common problems uses static analysis.
This is the Android lint checker, run using:

    # ./gradlew lintRelease

The final check is by testing the application on a live device and verifying
the basic functionality works as expected.

The release-grade verification command that runs all checks is:

    # ./gradlew assembleRelease testReleaseUnitTest lintRelease

For the real-device reliability test matrix and release verification guidance,
see [docs/testing.md](docs/testing.md).

## Explain Your Work

At the top of every patch you should include a description of the problem you
are trying to solve, how you solved it, and why you chose the solution you
implemented.  If you are submitting a bug fix, it is also incredibly helpful
if you can describe/include a reproducer for the problem in the description as
well as instructions on how to test for the bug and verify that it has been
fixed.

## License

By submitting a pull request, you confirm that:
- You have the right to submit this contribution under the project's GPLv3 license
- The contribution is your own work, or you have permission to submit it
- You understand the contribution will be publicly available under GPLv3

## Submit Patch(es) for Review

Finally, you will need to submit your patches so that they can be reviewed
and potentially merged into the Open Babyphone repository. The preferred
way to do this is to submit a Pull Request to the Open Babyphone project.
Changes need to apply cleanly onto the main branch and pass all
unit tests and produce no errors during static analysis.

## Language

The app is English-only. All user-facing strings must be in English.
Do not add or maintain translations in other languages.

All PR titles, PR descriptions, commit messages, and code comments must
be written in English, regardless of the language used in conversation.
