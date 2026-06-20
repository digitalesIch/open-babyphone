# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| Latest release on `main` | ✅ |
| Older releases | ❌ |

## Reporting a Vulnerability

We take security issues seriously. If you discover a vulnerability, please report it **privately** before disclosing it publicly.

### How to Report

1. **Do not** create a public GitHub issue for security vulnerabilities
2. Send an email to the maintainers or use GitHub's private vulnerability reporting feature
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### What to Expect

- We will acknowledge your report within 48 hours
- We will provide an initial response within 7 days
- We will keep you informed of our progress
- We request that you allow us reasonable time to fix the issue before public disclosure

## Security Model

Open Babyphone is designed for local-network use in a trusted home environment.
The supported product model is same Wi-Fi or same LAN. Advanced users may connect
across a trusted VPN by entering the child device address manually, but the app
does not provide remote access, relay servers, accounts, cloud connectivity, or
internet-based discovery.

Known limitations in the current implementation:
- Audio streaming is encrypted with ChaCha20-Poly1305 only when a non-empty pairing code is configured
- Empty pairing code means no authentication and no transport encryption
- Pairing uses challenge-response authentication; the pairing code itself is not sent over the connection
- Pairing codes are stored in SharedPreferences and are excluded from Android backups

The project direction is to make pairing and encrypted transport the safe default
before a public release.

These are documented design decisions. Please do not report them as vulnerabilities unless you have a concrete, low-maintenance improvement that aligns with the project's no-cloud constraints.

## Security Fixes

Security patches will be released as soon as possible. Depending on severity, we may:
- Release an immediate patch version
- Coordinate with F-Droid for rapid inclusion
- Publish a security advisory on GitHub

Thank you for helping keep Open Babyphone secure.
