package net.minecraftforge.fml.config.sync;

import fuzs.pickupnotifier.PickUpNotifier;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigSync {

    public static final ConfigSync INSTANCE = new ConfigSync(ConfigTracker.INSTANCE);
    public static final ResourceLocation SYNC_CONFIGS_CHANNEL = new ResourceLocation(PickUpNotifier.MODID, "sync_configs");
    private final ConfigTracker tracker;

    private ConfigSync(final ConfigTracker tracker) {
        this.tracker = tracker;
    }

    public void init() {

        ServerLoginConnectionEvents.QUERY_START.register((ServerLoginPacketListenerImpl handler, MinecraftServer server, PacketSender sender, ServerLoginNetworking.LoginSynchronizer synchronizer) -> {

            final List<Pair<String, FriendlyByteBuf>> pairs = this.syncConfigs();
            for (Pair<String, FriendlyByteBuf> pair : pairs) {

                synchronizer.waitFor(server.submit(() -> sender.sendPacket(SYNC_CONFIGS_CHANNEL, pair.getValue())));
            }
        });

        ServerLoginNetworking.registerGlobalReceiver(SYNC_CONFIGS_CHANNEL, (MinecraftServer server, ServerLoginPacketListenerImpl handler, boolean understood, FriendlyByteBuf buf, ServerLoginNetworking.LoginSynchronizer synchronizer, PacketSender responseSender) -> {
            if (!understood) {
                // The client is likely a vanilla client.
                return;
            }

//            String fileName = buf.readUtf(32767);
            PickUpNotifier.LOGGER.debug("Received config sync for {} from server", "fileName");
        });
    }

    private List<Pair<String, FriendlyByteBuf>> syncConfigs() {
        final Map<String, byte[]> configData = tracker.configSets().get(ModConfig.Type.SERVER).stream().collect(Collectors.toMap(ModConfig::getFileName, mc -> {
            try {
                return Files.readAllBytes(mc.getFullPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        return configData.entrySet().stream().map(e-> {

            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(e.getKey());
            buf.writeByteArray(e.getValue());
            return Pair.of("Config " + e.getKey(), buf);
        }).collect(Collectors.toList());
    }
}