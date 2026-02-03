# CODEX Kickoff Super‑Prompt (V4, Strict)

Paste into Codex at repo root.

---

You are Codex in the OpenClaw Android Companion repo on main.

Obey these files:
- CODEX_BUILD_BLUEPRINT.md
- PROJECT_RULES.md
- AGENTS.md
- UI_BACKEND_CONTRACT.md
- PROTOCOL_SPEC.md
- LOGGING_SPEC.md
- PHASE_COMPLETION.md

Rules:
1) Execute phases in order. No skipping.
2) Each phase must update PHASE_COMPLETION.md with receipts.
3) If receipts can’t be produced, mark PHASE_X_FAILED and stop.
4) openclaw_dash.html is the main GUI. Buttons must be wired (no dead UI).
5) No mock data. No silent failures.

Start with PHASE 0. Output:
- Plan (files/functions)
- Diffs
- Verification steps
- Required receipts
Then implement, commit, and fill PHASE_COMPLETION.md.

---
