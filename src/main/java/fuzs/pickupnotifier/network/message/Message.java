package fuzs.pickupnotifier.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.player.Player;

/**
 * network message template
 */
public interface Message {

    /**
     * writes message data to buffer
     * @param buf network data byte buffer
     */
    void write(final FriendlyByteBuf buf);

    /**
     * reads message data from buffer
     * @param buf network data byte buffer
     */
    void read(final FriendlyByteBuf buf);

    /**
     * handles message on receiving side
     */
    default void handle(Player player, Object gameInstance) {

        this.makeHandler().handle(this, player, gameInstance);
    }

    <T extends Message> PacketHandler<T> makeHandler();

    interface PacketHandler<T extends Message> {

        void handle(T packet, Player player, Object gameInstance);

    }

}
