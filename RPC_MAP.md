# RPC_MAP.md (Template)

This file prevents “buttons that do nothing”. Every UI action must map to a backend handler.

| UI Element | HTML ID | JS Function | Bridge Method | Gateway RPC | Expected Response | Log Trace |
|---|---|---|---|---|---|---|
| Connect | btnConnect | connectGateway() | OpenClawBridge.connectGateway | (connect/hello) | {ok,code,message,data{state}} | UI_EVENT → TX → RX |
| Disconnect | btnDisconnect | disconnectGateway() | OpenClawBridge.disconnectGateway | (disconnect) | {ok,code,message,data{state}} | UI_EVENT → TX → RX |
| Status | btnStatus | requestStatus() | OpenClawBridge.requestStatus | status | {ok,code,message,data{state}} | UI_EVENT → STATE |
| Camsnap | btnCamsnap | triggerCamsnap() | OpenClawBridge.triggerCamsnap | capability invoke | {ok,...,data{image}} | UI_EVENT → CAPTURE → TX |
