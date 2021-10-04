package com.mrcrayfish.configured.network.message;

import com.mrcrayfish.configured.client.gui.screens.SelectConfigScreen;
import fuzs.puzzleslib.network.message.Message;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public class S2CGrantPermissionsMessage implements Message {

    public S2CGrantPermissionsMessage() {
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public void read(FriendlyByteBuf buf) {
    }

    @Override
    public GrantPermissionsHandler makeHandler() {
        return new GrantPermissionsHandler();
    }

    private static class GrantPermissionsHandler implements PacketHandler<S2CGrantPermissionsMessage> {

        @Override
        public void handle(S2CGrantPermissionsMessage packet, Player player, Object gameInstance) {

            if (((Minecraft) gameInstance).screen instanceof SelectConfigScreen screen) {
                screen.setServerPermissions();
            }
        }
    }
}
