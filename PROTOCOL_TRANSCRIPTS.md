# PROTOCOL_TRANSCRIPTS.md

## JSONL transcript format (required)
Each line is one JSON object:
{
  "ts": "ISO8601",
  "dir": "TX" | "RX",
  "channel": "operator" | "node",
  "type": "<messageTypeOrRpc>",
  "payload": { ... },
  "note": "optional"
}

Save as:
- receipts/phase2/handshake.jsonl
- receipts/phase3/<menu>.jsonl
