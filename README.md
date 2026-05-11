# Zer0 Minecraft Bridge Mod (Fabric)

This mod connects your Minecraft client to your **Zer0** project using WebSockets so Zer0 can:

- send chat messages in-game
- receive your player messages
- send basic movement commands (forward/jump/stop)

## Quick start

1. Run your Zer0 WebSocket server (example: `ws://127.0.0.1:8080/minecraft`).
2. Edit `src/main/resources/zer0-bridge.properties`:
   - `url`: Zer0 server URL
   - `apiKey`: auth key sent as `X-Api-Key`
3. Build the mod:
   ```bash
   ./gradlew build
   ```
4. Drop the generated jar from `build/libs/` into your Fabric `mods/` folder.

## In-game commands

- `/zer0connect` — connect manually
- `/zer0disconnect` — disconnect
- `/zer0say <text>` — send a player message payload to Zer0

## Zer0 message format

Incoming messages are simple JSON strings:

```json
{"type":"chat","text":"Hello from Zer0!"}
{"type":"move","direction":"forward"}
{"type":"move","direction":"jump"}
{"type":"move","direction":"stop"}
{"type":"system","text":"Thinking..."}
```

Outgoing payload example:

```json
{"event":"player_message","text":"hello zer0"}
```
