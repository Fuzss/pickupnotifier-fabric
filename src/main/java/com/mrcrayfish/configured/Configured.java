package com.mrcrayfish.configured;

import com.mrcrayfish.configured.network.client.message.C2SAskPermissionsMessage;
import com.mrcrayfish.configured.network.client.message.C2SSendConfigMessage;
import com.mrcrayfish.configured.network.message.S2CGrantPermissionsMessage;
import com.mrcrayfish.configured.network.message.S2CUpdateConfigMessage;
import fuzs.pickupnotifier.lib.network.NetworkDirection;
import fuzs.pickupnotifier.lib.network.NetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Configured {

    public static final String MODID = "configured";
    public static final String NAME = "Configured";
    public static final String URL = "https://www.curseforge.com/minecraft/mc-mods/configured";
    public static final Logger LOGGER = LogManager.getLogger(Configured.NAME);

    public static void registerMessages() {
        NetworkHandler.INSTANCE.register(C2SAskPermissionsMessage.class, C2SAskPermissionsMessage::new, NetworkDirection.PLAY_TO_SERVER);
        NetworkHandler.INSTANCE.register(S2CGrantPermissionsMessage.class, S2CGrantPermissionsMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(C2SSendConfigMessage.class, C2SSendConfigMessage::new, NetworkDirection.PLAY_TO_SERVER);
        NetworkHandler.INSTANCE.register(S2CUpdateConfigMessage.class, S2CUpdateConfigMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }
}
