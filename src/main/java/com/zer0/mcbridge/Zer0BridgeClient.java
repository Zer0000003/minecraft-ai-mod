package com.zer0.mcbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Zer0BridgeClient implements ClientModInitializer {
    private static Zer0BridgeClient instance;

    private final ConcurrentLinkedQueue<BridgeCommand> queuedCommands = new ConcurrentLinkedQueue<>();
    private BridgeSocketClient socketClient;
    private BridgeConfig config;

    public static Zer0BridgeClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = loadConfig();
        socketClient = new BridgeSocketClient(config, this::enqueueCommand, this::sayLocal);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("zer0connect")
                    .executes(ctx -> {
                        socketClient.connect();
                        return 1;
                    }));
            dispatcher.register(ClientCommandManager.literal("zer0disconnect")
                    .executes(ctx -> {
                        socketClient.disconnect();
                        return 1;
                    }));
            dispatcher.register(ClientCommandManager.literal("zer0say")
                    .then(ClientCommandManager.greedyString("message")
                            .executes(ctx -> {
                                String message = ctx.getArgument("message", String.class);
                                socketClient.sendPlayerChat(message);
                                return 1;
                            })));
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        if (config.announceConnection()) {
            socketClient.connect();
        }
    }

    private void onClientTick(MinecraftClient client) {
        while (!queuedCommands.isEmpty()) {
            BridgeCommand command = queuedCommands.poll();
            if (command != null) {
                command.apply(client);
            }
        }
    }

    private void enqueueCommand(BridgeCommand command) {
        queuedCommands.add(command);
    }

    private void sayLocal(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Zer0] " + message), false);
        }
    }

    private BridgeConfig loadConfig() {
        Properties props = new Properties();
        try (InputStream in = Zer0BridgeClient.class.getClassLoader().getResourceAsStream("zer0-bridge.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load zer0-bridge.properties", e);
        }

        return new BridgeConfig(
                props.getProperty("url", "ws://127.0.0.1:8080/minecraft"),
                props.getProperty("apiKey", "change-me"),
                Boolean.parseBoolean(props.getProperty("announceConnection", "true"))
        );
    }
}
