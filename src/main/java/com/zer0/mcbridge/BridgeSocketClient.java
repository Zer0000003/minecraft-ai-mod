package com.zer0.mcbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class BridgeSocketClient implements WebSocket.Listener {
    private final BridgeConfig config;
    private final Consumer<BridgeCommand> commandConsumer;
    private final Consumer<String> statusConsumer;
    private final HttpClient httpClient;

    private WebSocket webSocket;

    public BridgeSocketClient(BridgeConfig config, Consumer<BridgeCommand> commandConsumer, Consumer<String> statusConsumer) {
        this.config = config;
        this.commandConsumer = commandConsumer;
        this.statusConsumer = statusConsumer;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void connect() {
        if (webSocket != null) {
            statusConsumer.accept("already connected");
            return;
        }

        httpClient.newWebSocketBuilder()
                .header("X-Api-Key", config.apiKey())
                .buildAsync(URI.create(config.url()), this)
                .thenAccept(ws -> {
                    webSocket = ws;
                    statusConsumer.accept("connected to " + config.url());
                    ws.sendText("{\"event\":\"hello\",\"source\":\"minecraft\"}", true);
                })
                .exceptionally(ex -> {
                    statusConsumer.accept("connection failed: " + ex.getMessage());
                    return null;
                });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            webSocket = null;
        }
        statusConsumer.accept("disconnected");
    }

    public void sendPlayerChat(String message) {
        if (webSocket == null) {
            statusConsumer.accept("not connected");
            return;
        }
        webSocket.sendText("{\"event\":\"player_message\",\"text\":\"" + escape(message) + "\"}", true);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        handleIncoming(data.toString());
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        this.webSocket = null;
        statusConsumer.accept("socket closed: " + reason);
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.webSocket = null;
        statusConsumer.accept("socket error: " + error.getMessage());
    }

    private void handleIncoming(String rawJson) {
        final JsonObject payload;
        try {
            payload = JsonParser.parseString(rawJson).getAsJsonObject();
        } catch (Exception ex) {
            statusConsumer.accept("invalid payload: " + rawJson);
            return;
        }

        String type = getString(payload, "type");
        if (type == null) {
            statusConsumer.accept("missing command type");
            return;
        }

        switch (type) {
            case "chat" -> {
                String text = getString(payload, "text");
                commandConsumer.accept(client -> {
                    if (client.player != null && text != null && !text.isBlank()) {
                        client.player.networkHandler.sendChatMessage(text);
                    }
                });
            }
            case "system" -> {
                String text = getString(payload, "text");
                if (text != null) {
                    statusConsumer.accept(text);
                }
            }
            case "move" -> {
                String direction = getString(payload, "direction");
                commandConsumer.accept(client -> performMove(client, direction));
            }
            default -> statusConsumer.accept("unknown command type: " + type);
        }
    }

    private void performMove(MinecraftClient client, String direction) {
        if (client.player == null || direction == null) {
            return;
        }
        switch (direction) {
            case "forward" -> client.player.setVelocity(client.player.getRotationVector().multiply(0.35));
            case "jump" -> client.player.jump();
            case "stop" -> client.player.setVelocity(0, client.player.getVelocity().y, 0);
            default -> statusConsumer.accept("unknown move direction: " + direction);
        }
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
