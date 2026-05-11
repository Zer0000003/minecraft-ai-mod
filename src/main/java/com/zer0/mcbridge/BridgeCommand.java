package com.zer0.mcbridge;

import net.minecraft.client.MinecraftClient;

@FunctionalInterface
public interface BridgeCommand {
    void apply(MinecraftClient client);
}
