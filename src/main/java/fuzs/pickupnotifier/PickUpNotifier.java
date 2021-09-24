package fuzs.pickupnotifier;

import fuzs.pickupnotifier.api.event.EntityItemPickupCallback;
import fuzs.pickupnotifier.config.ClientConfig;
import fuzs.pickupnotifier.config.ServerConfig;
import fuzs.pickupnotifier.config.core.ConfigHolder;
import fuzs.pickupnotifier.handler.ItemPickupHandler;
import fuzs.pickupnotifier.lib.core.DistExecutor;
import fuzs.pickupnotifier.lib.network.NetworkDirection;
import fuzs.pickupnotifier.lib.network.NetworkHandler;
import fuzs.pickupnotifier.lib.proxy.ClientProxy;
import fuzs.pickupnotifier.lib.proxy.IProxy;
import fuzs.pickupnotifier.lib.proxy.ServerProxy;
import fuzs.pickupnotifier.network.message.S2CTakeItemMessage;
import fuzs.pickupnotifier.network.message.S2CTakeItemStackMessage;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("Convert2MethodRef")
public class PickUpNotifier implements ModInitializer {

    public static final String MODID = "pickupnotifier";
    public static final String NAME = "Pick Up Notifier";
    public static final Logger LOGGER = LogManager.getLogger(PickUpNotifier.NAME);

    public static final IProxy PROXY = DistExecutor.runForDist(() -> () -> new ClientProxy(), () -> () -> new ServerProxy());
    public static final ConfigHolder<ClientConfig, ServerConfig> CONFIG = new ConfigHolder<>(() -> new ClientConfig(), () -> new ServerConfig());

    @Override
    public void onInitialize() {

        ItemPickupHandler handler = new ItemPickupHandler();
        EntityItemPickupCallback.EVENT.register(handler::onEntityItemPickup);
        this.registerMessages();
    }

    private void registerMessages() {

        NetworkHandler.INSTANCE.register(S2CTakeItemMessage.class, S2CTakeItemMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(S2CTakeItemStackMessage.class, S2CTakeItemStackMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }

}
