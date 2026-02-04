# AI_WORKFLOW.md — Using Codex + Gemini + Copilot Without Getting Lied To

## Codex (primary)
- Implements one phase at a time.
- Must update receipts + commit hash.
- Must refuse to proceed without required proof.

## Gemini (secondary)
- Only allowed to:
  - enumerate edge cases
  - threat model
  - propose tests
- Not allowed to invent protocol names/endpoints.

## Copilot (tertiary)
- Boilerplate/refactor only.
- Not allowed to implement protocol logic.

## Rule: Quote-before-code
Whenever any AI adds a new RPC name, it must add a comment referencing MENU_SPEC/RPC_MAP and store a transcript under receipts/.
