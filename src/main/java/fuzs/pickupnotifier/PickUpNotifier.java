package fuzs.pickupnotifier;

import fuzs.pickupnotifier.api.event.EntityItemPickupCallback;
import fuzs.pickupnotifier.config.ClientConfig;
import fuzs.pickupnotifier.config.ServerConfig;
import fuzs.pickupnotifier.handler.ItemPickupHandler;
import fuzs.pickupnotifier.network.message.S2CTakeItemMessage;
import fuzs.puzzleslib.config.ConfigHolder;
import fuzs.puzzleslib.config.ConfigHolderImpl;
import fuzs.puzzleslib.network.MessageDirection;
import fuzs.puzzleslib.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickUpNotifier implements ModInitializer {
    public static final String MOD_ID = "pickupnotifier";
    public static final String MOD_NAME = "Pick Up Notifier";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final NetworkHandler NETWORK = NetworkHandler.of(MOD_ID);
    @SuppressWarnings("Convert2MethodRef")
    public static final ConfigHolder<ClientConfig, ServerConfig> CONFIG = ConfigHolder.of(() -> new ClientConfig(), () -> new ServerConfig());

    @Override
    public void onInitialize() {
        ((ConfigHolderImpl<?, ?>) CONFIG).addConfigs(MOD_ID);
        registerHandlers();
        registerMessages();
    }

    private static void registerHandlers() {
        final ItemPickupHandler handler = new ItemPickupHandler();
        EntityItemPickupCallback.EVENT.register(handler::onEntityItemPickup);
    }

    private static void registerMessages() {
        NETWORK.register(S2CTakeItemMessage.class, S2CTakeItemMessage::new, MessageDirection.TO_CLIENT);
    }
}
