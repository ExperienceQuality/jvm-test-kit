# CI and release contract

Delivery separates build, test, publish, and deploy responsibilities. The
`Delivery` workflow routes an event through the reusable workflows in
`.github/workflows`.

## Event and tag routing

| Event | Product | Version | Result |
| --- | --- | --- | --- |
| Pull request, `main` push, manual run | both | `0.0.0-ci.<run>.<attempt>` | Build and test only |
| `vMAJOR.MINOR.PATCH` | JVM Test Kit library | tag without `v` | Publish library, then create GitHub Release |
| `plugin-vMAJOR.MINOR.PATCH` | Service Gradle Plugin | tag without `plugin-v` | Publish plugin implementation and marker, then create GitHub Release |

Release tags must identify the supplied commit on protected `main`. A release
publishes exactly one product; the other product's version is unchanged.

## Build once and promote

The build stage publishes the selected product into an isolated Maven
repository under `.ci-staging`. It records product, version, commit, every file
path, and every SHA-256 digest in `manifest.json` and `SHA256SUMS`, then uploads
the directory as one uniquely named workflow artifact.

The test stage downloads that artifact, verifies its identity and byte set,
and resolves consumers from the staged repository. It does not rebuild the
publication being tested. Library validation includes compatibility and the
clean-consumer fixture. Plugin validation includes TestKit, plugin validation,
and exact marker resolution.

The publish stage downloads and verifies the same artifact, creates provenance
attestations, and promotes the staged Maven files byte-for-byte. Promotion is
idempotent only when an existing remote file is identical; a different remote
byte fails closed. Mutable Maven metadata is not promoted. After upload, a
clean remote consumer resolves the library or the plugin marker at the exact
version.

The deploy stage runs only after remote verification. It creates the GitHub
Release and attaches the staging manifest and checksums. Package publication
and release creation are never performed for pull requests, branch pushes, or
manual verification runs.

## Permissions and credentials

Workflows default to `contents: read`. Test adds `actions: read` and
`packages: read`. Publish receives `packages: write`, `id-token: write`, and
`attestations: write` inside the protected `release` environment. Only the
final release job receives `contents: write`.

GitHub Packages credentials come from the runtime `GITHUB_ACTOR` and
`GITHUB_TOKEN`. Never print or persist them. Consumer repositories need
`packages: read` and explicit package access.

## Rollback

Published Maven versions are immutable. Do not overwrite or delete a bad
version. Roll back consumers by restoring the preceding known-good exact
library or plugin version. Correct the source, run all gates, and issue a new
SemVer tag for only the affected product. The manifest, checksums, test
evidence, provenance, and GitHub Release preserve the audit trail.

## Local acceptance

```bash
./gradlew --no-daemon check :service-plugin:validatePlugins
node /path/to/hub/scripts/hub.mjs workflow-check --repo jvm-test-kit
git diff --check
```

For product-specific commands, use the canonical module READMEs:
[test-kit](../modules/test-kit/README.md) and
[service-plugin](../modules/service-plugin/README.md).
