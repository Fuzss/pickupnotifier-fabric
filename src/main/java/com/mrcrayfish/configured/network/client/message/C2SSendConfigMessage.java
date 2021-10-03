package com.mrcrayfish.configured.network.client.message;

import com.mrcrayfish.configured.network.message.S2CUpdateConfigMessage;
import fuzs.pickupnotifier.lib.network.NetworkHandler;
import fuzs.pickupnotifier.lib.network.message.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.config.ConfigTracker;

import java.util.Optional;

public class C2SSendConfigMessage implements Message {

    private String fileName;
    private byte[] fileData;

    public C2SSendConfigMessage() {
    }

    public C2SSendConfigMessage(String fileName, byte[] fileData) {
        this.fileName = fileName;
        this.fileData = fileData;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.fileName);
        buf.writeByteArray(this.fileData);
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.fileName = buf.readUtf();
        this.fileData = buf.readByteArray();
    }

    @Override
    public SendConfigHandler makeHandler() {
        return new SendConfigHandler();
    }

    private static class SendConfigHandler implements PacketHandler<C2SSendConfigMessage> {

        @Override
        public void handle(C2SSendConfigMessage packet, Player player, Object gameInstance) {

            final MinecraftServer server = (MinecraftServer) gameInstance;
            if (server.isDedicatedServer() && player.hasPermissions(server.getOperatorUserPermissionLevel())) {
                Optional.ofNullable(ConfigTracker.INSTANCE.fileMap().get(packet.fileName)).ifPresent(config -> config.acceptSyncedConfig(packet.fileData));
                NetworkHandler.INSTANCE.sendToAllExcept(new S2CUpdateConfigMessage(packet.fileName, packet.fileData), (ServerPlayer) player);
            }
        }
    }
}
