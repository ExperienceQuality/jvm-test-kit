# Repository Agents

## sdet-rule

For SDK features, bug fixes, refactors, dependency upgrades, compatibility work, CI changes, or behavior documentation, delegate the full task to the `sdet` custom agent. Tiny documentation-only edits may stay with the main agent.

Ask `sdet` to inspect and return its challenge and plan without editing. Present that plan to the user and wait for explicit approval. Discussion or requested plan revisions are not approval. After approval, resume the same `sdet` agent with the approval and have it implement, verify, and report. Use a replacement only when the original agent is unavailable, and provide the approved plan and relevant evidence to the replacement.
