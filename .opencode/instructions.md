# Upstream Policy

**CRITICAL: Do NOT create issues, pull requests, or any contributions to upstream repository `enguerrand/child-monitor` unless explicitly requested.**

This project is now developed as an independent fork:
- **Fork (origin):** `digitalesIch/open-babyphone`
- **Upstream:** `enguerrand/child-monitor`

## Rules

1. Only interact with the active repository `digitalesIch/open-babyphone`
2. Never create, edit, close, or comment on GitHub issues, PRs, or discussions in `enguerrand/child-monitor`
3. All development work belongs in this fork
4. All `gh` commands that read or write issues, PRs, releases, workflow runs, checks, discussions, or repository settings must explicitly target `digitalesIch/open-babyphone` using `--repo digitalesIch/open-babyphone` or the `digitalesIch/open-babyphone` repository argument when a command does not support `--repo`, except `gh repo set-default` itself
5. Do not rely on the implicit `gh` repository selection; in forked repositories it can resolve to the upstream parent
6. Upstream PRs should only be submitted for critical security fixes when explicitly requested
7. If an upstream issue or PR was already closed or redirected by mistake, do not add additional comments there unless explicitly requested

## Repository Structure

```
origin   -> digitalesIch/open-babyphone (active development)
upstream -> enguerrand/child-monitor (reference only; fetch/push should be disabled locally)
```

## Local GitHub CLI Safety

Run and verify this in the workspace before using `gh`:

```bash
gh repo set-default digitalesIch/open-babyphone
gh repo set-default --view
```

Even after setting a default repository, prefer explicit commands such as:

```bash
gh issue list --repo digitalesIch/open-babyphone
gh issue create --repo digitalesIch/open-babyphone
gh pr list --repo digitalesIch/open-babyphone
gh pr create --repo digitalesIch/open-babyphone
```

## Language Policy

**All external-facing output must be in English only:**
- Code comments
- Commit messages
- Pull request descriptions and comments
- Documentation files

**AI chat responses:** Can be in the user's preferred language (German).

This ensures the project remains accessible to international contributors.
