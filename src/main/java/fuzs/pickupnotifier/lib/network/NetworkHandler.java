package fuzs.pickupnotifier.lib.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import fuzs.pickupnotifier.PickUpNotifier;
import fuzs.pickupnotifier.lib.network.message.Message;
import fuzs.pickupnotifier.lib.util.PuzzlesUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * handler for network communications of all puzzles lib mods
 */
public enum NetworkHandler {

    INSTANCE;

    /**
     * registry for class to identifier relation
     */
    private static final BiMap<Class<? extends Message>, ResourceLocation> MESSAGE_REGISTRY = HashBiMap.create();
    /**
     * message index
     */
    private static final AtomicInteger DISCRIMINATOR = new AtomicInteger();

    /**
     * register a message for a side
     * @param clazz     message class type
     * @param supplier supplier for message (called when receiving at executing end)
     *                 we use this additional supplier to avoid having to invoke the class via reflection
     *                 and so that a default constructor in every message cannot be forgotten
     * @param direction side this message is to be executed at
     * @param <T> message implementation
     */
    public <T extends Message> void register(Class<T> clazz, Supplier<T> supplier, NetworkDirection direction) {

        ResourceLocation channelName = nextIdentifier();
        MESSAGE_REGISTRY.put(clazz, channelName);
        final Function<FriendlyByteBuf, Message> decode = buf -> PuzzlesUtil.make(supplier.get(), message -> message.read(buf));
        switch (direction) {

            case PLAY_TO_CLIENT -> PickUpNotifier.PROXY.registerClientReceiver(channelName, decode);
            case PLAY_TO_SERVER -> PickUpNotifier.PROXY.registerServerReceiver(channelName, decode);
        }
    }

    /**
     * use discriminator to generate identifier for package
     * @return unique identifier
     */
    private static ResourceLocation nextIdentifier() {

        return new ResourceLocation(PickUpNotifier.MODID, "main/" + DISCRIMINATOR.getAndIncrement());
    }

    /**
     * send message from client to server
     * @param message message to send
     */
    public void sendToServer(Message message) {

        // copied from ClientPlayNetworking::send
        if (Minecraft.getInstance().getConnection() == null) {

            throw new IllegalStateException("Cannot send packets when not in game!");
        }

        Minecraft.getInstance().getConnection().send(createC2SPacket(message));
    }

    /**
     * send message from server to client
     * @param message message to send
     * @param player client player to send to
     */
    public void sendTo(Message message, ServerPlayer player) {

        player.connection.send(createS2CPacket(message));
    }

    /**
     * send message from server to all clients
     * @param message message to send
     */
    public void sendToAll(Message message) {

        PickUpNotifier.PROXY.getGameServer().getPlayerList().broadcastAll(createS2CPacket(message));
    }

    /**
     * send message from server to all clients except one
     * @param message message to send
     * @param exclude client to exclude
     */
    public void sendToAllExcept(Message message, ServerPlayer exclude) {

        final Packet<?> packet = createS2CPacket(message);
        for (ServerPlayer player : PickUpNotifier.PROXY.getGameServer().getPlayerList().getPlayers()) {
            if (player != exclude) {
                player.connection.send(packet);
            }
        }
    }

    /**
     * send message from server to all clients near given position
     * @param message message to send
     * @param pos source position
     * @param level dimension key provider level
     */
    public void sendToAllNear(Message message, BlockPos pos, Level level) {

        this.sendToAllNearExcept(message, null, pos.getX(), pos.getY(), pos.getZ(), 64.0, level);
    }

    /**
     * send message from server to all clients near given position
     * @param message message to send
     * @param exclude exclude player having caused this event
     * @param posX     source position x
     * @param posY     source position y
     * @param posZ     source position z
     * @param distance distance from source to receive message
     * @param level dimension key provider level
     */
    public void sendToAllNearExcept(Message message, @Nullable ServerPlayer exclude, double posX, double posY, double posZ, double distance, Level level) {

        PickUpNotifier.PROXY.getGameServer().getPlayerList().broadcast(exclude, posX, posY, posZ, distance, level.dimension(), createS2CPacket(message));
    }

    /**
     * send message from server to all clients in dimension
     * @param message message to send
     * @param level dimension key provider level
     */
    public void sendToDimension(Message message, Level level) {

        this.sendToDimension(message, level.dimension());
    }

    /**
     * send message from server to all clients in dimension
     * @param message message to send
     * @param dimension dimension to send message in
     */
    public void sendToDimension(Message message, ResourceKey<Level> dimension) {

        PickUpNotifier.PROXY.getGameServer().getPlayerList().broadcastAll(createS2CPacket(message), dimension);
    }

    /**
     * @param message message to create packet from
     * @return      packet for message
     */
    private static Packet<?> createC2SPacket(Message message) {

        ResourceLocation identifier = MESSAGE_REGISTRY.get(message.getClass());
        FriendlyByteBuf byteBuf = PacketByteBufs.create();
        message.write(byteBuf);
        return ClientPlayNetworking.createC2SPacket(identifier, byteBuf);
    }

    /**
     * @param message message to create packet from
     * @return      packet for message
     */
    private static Packet<?> createS2CPacket(Message message) {

        ResourceLocation identifier = MESSAGE_REGISTRY.get(message.getClass());
        FriendlyByteBuf byteBuf = PacketByteBufs.create();
        message.write(byteBuf);
        return ServerPlayNetworking.createS2CPacket(identifier, byteBuf);
    }

}
