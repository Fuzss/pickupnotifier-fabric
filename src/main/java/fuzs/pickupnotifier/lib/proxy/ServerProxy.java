package fuzs.pickupnotifier.lib.proxy;

import fuzs.pickupnotifier.lib.network.message.Message;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

public class ServerProxy implements IProxy {

    private MinecraftServer gameServer;

    public ServerProxy() {

        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.gameServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.gameServer = null);
    }

    @Override
    public Player getClientPlayer() {

        return null;
    }

    @Override
    public Object getClientInstance() {

        return null;
    }

    @Override
    public MinecraftServer getGameServer() {

        return this.gameServer;
    }

    @Override
    public void registerClientReceiver(ResourceLocation channelName, Function<FriendlyByteBuf, Message> factory) {

    }

    @Override
    public void registerServerReceiver(ResourceLocation channelName, Function<FriendlyByteBuf, Message> factory) {

        ServerPlayNetworking.registerGlobalReceiver(channelName, (MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) -> {

            Message message = factory.apply(buf);
            server.execute(() -> message.handle(player, server));
        });
    }

}
