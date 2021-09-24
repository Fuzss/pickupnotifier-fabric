package fuzs.pickupnotifier.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import fuzs.pickupnotifier.PickUpNotifier;
import fuzs.pickupnotifier.network.message.Message;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fmllegacy.network.NetworkDirection;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
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
     * mostly from AutoRegLib, thanks Vazkii!
     * @param clazz     message class type
     * @param supplier supplier for message (called when receiving at executing end)
     *                 we use this additional supplier to avoid having to invoke the class via reflection
     *                 and so that a default constructor in every message cannot be forgotten
     * @param direction side this message is to be executed at
     * @param <T> message implementation
     */
    public <T extends Message> void register(Class<T> clazz, Supplier<T> supplier, NetworkDirection direction) {

        ResourceLocation identifier = nextIdentifier();
        MESSAGE_REGISTRY.put(clazz, identifier);

        switch (direction) {

            case PLAY_TO_CLIENT -> {

                ClientPlayNetworking.registerGlobalReceiver(identifier, (client, handler, buf, responseSender) -> {

                    T message = supplier.get();
                    message.read(buf);
                });
            }

        }

        BiConsumer<T, FriendlyByteBuf> encode = Message::write;
        Function<FriendlyByteBuf, T> decode = (buf) -> {

            T message = supplier.get();
            message.read(buf);
            return message;
        };
        BiConsumer<T, Supplier<NetworkEvent.Context>> handle = (msg, ctxSup) -> {

            NetworkEvent.Context ctx = ctxSup.get();
            if (ctx.getDirection() == direction) {

                msg.handle(ctx);
            } else {

                PickUpNotifier.LOGGER.warn("Received message {} at wrong side, was {}, expected {}", msg.getClass().getSimpleName(), ctx.getDirection().getReceptionSide(), direction.getReceptionSide());
            }

            ctx.setPacketHandled(true);
        };

        this.channel.registerMessage(this.DISCRIMINATOR.getAndIncrement(), clazz, encode, decode, handle);
    }

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
     * @param level   server level for server
     */
    public void sendToAll(Message message, ServerLevel level) {

        this.sendToAll(message, level.getServer());
    }

    /**
     * send message from server to all clients
     * @param message message to send
     * @param server  minecraft server
     */
    public void sendToAll(Message message, MinecraftServer server) {

        server.getPlayerList().broadcastAll(createS2CPacket(message));
    }

    /**
     * send message from server to all clients near given position
     * @param message message to send
     * @param pos source position
     * @param level dimension key provider level
     */
    public void sendToAllNear(Message message, BlockPos pos, ServerLevel level) {

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
    public void sendToAllNearExcept(Message message, @Nullable ServerPlayer exclude, double posX, double posY, double posZ, double distance, ServerLevel level) {

        level.getServer().getPlayerList().broadcast(exclude, posX, posY, posZ, distance, level.dimension(), createS2CPacket(message));
    }

    /**
     * send message from server to all clients in dimension
     * @param message message to send
     * @param level dimension key provider level
     */
    public void sendToDimension(Message message, ServerLevel level) {

        this.sendToDimension(message, level.dimension(), level.getServer());
    }

    /**
     * send message from server to all clients in dimension
     * @param message message to send
     * @param dimension dimension to send message in
     * @param server    minecraft server
     */
    public void sendToDimension(Message message, ResourceKey<Level> dimension, MinecraftServer server) {

        server.getPlayerList().broadcastAll(createS2CPacket(message), dimension);
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
