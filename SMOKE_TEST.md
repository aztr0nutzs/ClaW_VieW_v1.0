# SMOKE_TEST.md (5-minute)

1) Start OpenClaw gateway/controller.

2) Launch Android app.

3) Confirm:
   - Foreground notification present
   - Dashboard loads openclaw_dash.html

4) Connect.

5) If pairing required:
   - Approve device
   - Reconnect

6) Verify Chat:
   - history loads
   - send works

7) Verify Logs:
   - logs.tail streams

8) Verify Node/Camsnap:
   - trigger camsnap
   - capture logged
   - controller receipt logged

Collect receipts:
- logcat excerpt
- screenshots
- transcripts in receipts/
