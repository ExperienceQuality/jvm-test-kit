# CI/CD maintainer runbook

This runbook defines the repository controls for validating and publishing
`com.xq:jvm-test-kit`. It describes settings that only a repository maintainer
can configure; it does not contain credentials or publish a package.

## Required GitHub controls

Configure these rulesets in `ExperienceQuality/jvm-test-kit`:

- Protect `main`. Require pull requests, at least one maintainer approval, and
  the required validation check (`./gradlew clean check`). Dismiss stale
  approvals, require conversation resolution, and prevent force-pushes and
  branch deletion.
- Protect version tags matching `v*.*.*`. Only maintainers may create, update,
  or delete these tags. A release workflow must reject a tag whose version does
  not match the Gradle project version.
- Restrict Actions to the approved GitHub and organization actions. Pin third-
  party actions to full commit SHAs when adding them. Review workflow changes as
  code.
- Give workflows only required permissions. Verification should use
  `contents: read`; release needs `contents: write`, `packages: write`, and
  `id-token: write` only when provenance attestation is enabled.

Create a protected GitHub Environment named `release` for publishing. Require
maintainer approval, limit deployment branches/tags to `v*.*.*`, and keep any
future environment variables or secrets scoped to this environment. Prefer the
automatic `GITHUB_TOKEN`; never commit a PAT or registry credential.

## Artifact and version policy

- Keep test reports, SBOMs, and other CI evidence finite. Set an explicit
  retention period appropriate for the repository (for example, 14 days for PR
  evidence and 90 days for release evidence); do not use indefinite retention.
- Release versions must be immutable Maven versions such as `1.0.0`. Do not
  publish `SNAPSHOT` or reuse a released version. The tag, Gradle version, and
  GitHub Release must agree.
- Treat the Git commit, tag, workflow run, published coordinates, checksum, and
  validation result as the release evidence record.

## First release checklist

A maintainer performs these steps once the release workflow exists:

1. Confirm `main` is green with `./gradlew clean check` and review the generated
   reports and SBOM.
2. Set the next non-SNAPSHOT version in the release change and merge it through
   the protected `main` path.
3. Create and push the matching annotated tag, for example `v1.0.0`.
4. Approve the `release` environment deployment only after checking the commit,
   tag, version, and workflow permissions.
5. Confirm the GitHub Packages coordinates and checksums, then create or verify
   the GitHub Release and retain its evidence link.
6. Verify consumption from a clean Gradle project using the released immutable
   version. Never validate a release by resolving a local build directory.

## Rollback and no-release behavior

Published Maven artifacts are immutable. Do not delete or overwrite a released
coordinate to roll back. If a release is defective:

- Stop promotion and mark the GitHub Release as withdrawn or superseded.
- Disable or pause the release workflow if repeated publication is possible.
- Open a corrective change, publish the next patch version, and communicate the
  superseding coordinate.
- Revoke or rotate a credential immediately if exposure is suspected; inspect
  workflow logs and package access records.
- Preserve the failed run, tag, checksum, SBOM, and incident notes for audit.

If validation fails, the workflow must stop before publication. If environment
approval is denied, the run must remain unpublished. If tag/version checks,
dependency verification, SBOM generation, or consumer-resolution checks fail,
fix the source or workflow and create a new commit/tag; never bypass the gate.

## Maintainer-only decisions

The repository maintainer owns GitHub rulesets, Actions policy, the `release`
environment approvers, tag permissions, package visibility, retention settings,
and the release/rollback decision. Contributors may propose workflow changes,
but cannot self-approve a production publication or add release credentials.

Reference: [GitHub rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets),
[environment protection rules](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/reviewing-deployments),
[workflow permissions](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#permissions),
and [artifact retention](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts#configuring-a-custom-artifact-retention-period).
