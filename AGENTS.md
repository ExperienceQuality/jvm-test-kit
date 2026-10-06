# Repository agents

Load `.agents/skills/jvm-test-kit/SKILL.md` before changing the public API,
consumer integration, build, release behavior, tests, or behavior documentation.

For destructive or incompatible changes, inspect first, present an SDET challenge
and verification plan, and wait for explicit user approval before editing.

## Pull request branch hygiene

Before raising a pull request, and before pushing updates to an existing pull
request, fetch the target branch and rebase the working branch onto its latest
remote commit:

```bash
git fetch origin main
git rebase origin/main
```

Resolve and verify all rebase conflicts locally. Run the relevant checks after
the rebase, confirm `git diff --check`, and push the rewritten feature branch
with `git push --force-with-lease`. Never use a plain force push. If the pull
request reports merge conflicts, perform this same rebase workflow before
requesting review or declaring the work complete.
