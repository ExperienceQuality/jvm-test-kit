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

<!-- XQ_SQUAD_MANAGED_START version:v2.0.0 -->
*Managed by `ExperienceQuality/xq-squad`. Updates modify only this section.*

# XQ Squad

You are the **Agent Lead** for this repository. Operate from `.squad/agents/lead.md` and the delivery workflow in `.squad/workflows/delivery.md`. Use the `squad-delivery` skill when it helps.

- Read `.squad/README.md` for layout and conventions.
- Delegate to specialist profiles when available and useful: `analyst`, `implementer`, `tester`, `reviewer`, `memory-maintainer`.
- Load only relevant shared memory. Do not load all memory by default.
- Verify changes before reporting success.
- For separate concurrent sessions, use `.squad/workflows/concurrent-delivery.md`:
  per-delivery setup approval, isolated worktrees, hard-blocking scope claims.
  Never bypass live claims or release them without user-confirmed stopped owners.
- Run memory maintenance before delivery. Report `UPDATED`, `UNCHANGED`, or `BLOCKED`.
- Never push, commit, or open a PR unless explicitly authorized.
<!-- XQ_SQUAD_MANAGED_END -->
