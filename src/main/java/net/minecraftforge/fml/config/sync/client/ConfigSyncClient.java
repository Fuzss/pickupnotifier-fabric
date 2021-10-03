package net.minecraftforge.fml.config.sync.client;

import fuzs.pickupnotifier.PickUpNotifier;
import net.minecraftforge.fml.config.sync.ConfigSync;
import net.minecraftforge.fml.config.ConfigTracker;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.impl.networking.NetworkingImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ConfigSyncClient {

    public static final ConfigSyncClient INSTANCE = new ConfigSyncClient(ConfigTracker.INSTANCE);
    private final ConfigTracker tracker;

    private ConfigSyncClient(final ConfigTracker tracker) {
        this.tracker = tracker;
    }

    public void clientInit() {

        ClientLoginNetworking.registerGlobalReceiver(ConfigSync.SYNC_CONFIGS_CHANNEL, (client, handler, buf, listenerAdder) -> {

            final String fileName = this.receiveSyncedConfig(buf);
            PickUpNotifier.LOGGER.debug("Received config sync for {} from server", fileName);

            FriendlyByteBuf response = PacketByteBufs.create();
//            buf.writeUtf(fileName);
            NetworkingImpl.LOGGER.debug("Sent config sync for {} to server", fileName);
            return CompletableFuture.completedFuture(response);
        });
    }

    private String receiveSyncedConfig(final FriendlyByteBuf buf) {
        String fileName = buf.readUtf(32767);
        byte[] fileData = buf.readByteArray();
        if (!Minecraft.getInstance().isLocalServer()) {
            Optional.ofNullable(this.tracker.fileMap().get(fileName)).ifPresent(config -> config.acceptSyncedConfig(fileData));
        }
        return fileName;
    }
}