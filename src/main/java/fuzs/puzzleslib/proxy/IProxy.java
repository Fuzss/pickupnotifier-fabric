package fuzs.puzzleslib.proxy;

import fuzs.puzzleslib.network.message.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

public interface IProxy {

    Player getClientPlayer();

    Object getClientInstance();

    MinecraftServer getGameServer();

    void registerClientReceiver(ResourceLocation channelName, Function<FriendlyByteBuf, Message> factory);

    void registerServerReceiver(ResourceLocation channelName, Function<FriendlyByteBuf, Message> factory);

}
