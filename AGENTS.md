# Codex instructions

This repository contains a small personal CNG route-planner PWA intended
for a two-week trip. Keep the implementation simple and avoid
production/enterprise infrastructure.

Before implementing anything:
1. Read SPEC.md.
2. Read PROGRESS.md.
3. Continue from the first unfinished task.

SPEC.md is the source of truth for product and architecture decisions.

After completing each meaningful phase:
1. Run the relevant tests/checks.
2. Update PROGRESS.md.
3. Record what was completed, important discoveries/changes, any blockers,
   and the exact next task.

Do not rely on conversation history for project state. Recover state from
SPEC.md, PROGRESS.md, and the repository itself.

If context is compacted or you become uncertain about prior decisions,
re-read SPEC.md and PROGRESS.md before continuing.

Work autonomously. Do not ask implementation questions that SPEC.md
already answers.

Do not overengineer. This is a temporary personal travel tool.

Do not add Docker, server databases, authentication, cloud infrastructure,
or a backend unless a real CORS limitation requires the tiny proxy
described in SPEC.md.

Do not stop at scaffolding. Continue until the Definition of Done in
SPEC.md is satisfied.
