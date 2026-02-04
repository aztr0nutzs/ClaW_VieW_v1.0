# MENU_SPEC.md (Template)

Fill this in BEFORE implementing the menu.

| Menu Item | Screen ID | Required RPCs | Inputs | Output Rendering | Failure States | Receipt Required |
|---|---|---|---|---|---|---|
| Chat | chat | chat.history, chat.send, chat.abort, chat.inject | prompt, sessionId | message list + stream | not authed / disconnected | screenshot + tx/rx |
| Logs | logs | logs.tail | level/filter | tail view | forbidden / disconnected | screenshot + tx/rx |
| Nodes | nodes | node.list | none | node list | disconnected | screenshot + tx/rx |
| Config | config | config.get, config.set, config.apply, config.schema | key/patch | key editor | schema mismatch | screenshot + tx/rx |
| ... | ... | ... | ... | ... | ... | ... |
