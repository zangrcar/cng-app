# Codex instructions

This is a small native Android app for personally finding CNG stations while travelling in Italy.

Before changing code:
1. Read DESIGN.md.
2. Read PROGRESS.md.
3. Inspect the relevant existing code.

DESIGN.md is the source of truth for product and UX decisions.
PROGRESS.md is persistent implementation state.

Work only on the task explicitly requested in the current Codex goal.
Do not implement future phases early.

Priorities:
1. Actually working behavior.
2. Smooth, simple Android UX.
3. Reliability.
4. Straightforward code.
5. Architecture/abstraction only where it genuinely helps.

Do not create fake or placeholder controls that appear usable but do nothing.

After each task:
1. Run relevant Gradle tests/checks.
2. Update PROGRESS.md with what was actually implemented and verified.
3. Record any important discovery or deviation.
4. Do not mark anything complete unless it was implemented and verified.

If context is compacted, recover state from DESIGN.md, PROGRESS.md and the repository.

This is Android-only.
Use Kotlin and Jetpack Compose.
Do not reintroduce the old React/PWA implementation.
Do not add a backend unless explicitly requested.