package fuzs.pickupnotifier;

import fuzs.pickupnotifier.api.event.EntityItemPickupCallback;
import fuzs.pickupnotifier.config.ClientConfig;
import fuzs.pickupnotifier.config.ServerConfig;
import fuzs.pickupnotifier.config.core.ConfigHolder;
import fuzs.pickupnotifier.config.core.ConfigManager;
import fuzs.pickupnotifier.handler.ItemPickupHandler;
import net.minecraftforge.ForgeConfigs;
import net.minecraftforge.fml.config.ModConfig;
import fuzs.puzzleslib.core.DistExecutor;
import fuzs.puzzleslib.network.NetworkDirection;
import fuzs.puzzleslib.network.NetworkHandler;
import fuzs.puzzleslib.proxy.ClientProxy;
import fuzs.puzzleslib.proxy.IProxy;
import fuzs.puzzleslib.proxy.ServerProxy;
import fuzs.pickupnotifier.network.message.S2CTakeItemMessage;
import fuzs.pickupnotifier.network.message.S2CTakeItemStackMessage;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("Convert2MethodRef")
public class PickUpNotifier implements ModInitializer {

    public static final String MODID = "pickupnotifier";
    public static final String NAME = "Pick Up Notifier";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static final IProxy PROXY = DistExecutor.runForDist(() -> () -> new ClientProxy(), () -> () -> new ServerProxy());
    public static final ConfigHolder<ClientConfig, ServerConfig> CONFIG = new ConfigHolder<>(() -> new ClientConfig(), () -> new ServerConfig());

    @Override
    public void onInitialize() {

        ItemPickupHandler handler = new ItemPickupHandler();
        EntityItemPickupCallback.EVENT.register(handler::onEntityItemPickup);
        this.registerMessages();
        
        ForgeConfigs.registerConfig(MODID, ModConfig.Type.COMMON, CONFIG.buildSpec(), ConfigManager.getSimpleName(MODID));
        ConfigManager.init(MODID);
    }

    private void registerMessages() {
        NetworkHandler.INSTANCE.register(S2CTakeItemMessage.class, S2CTakeItemMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(S2CTakeItemStackMessage.class, S2CTakeItemStackMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }
}
