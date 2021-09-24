package fuzs.pickupnotifier;

import fuzs.pickupnotifier.api.FabricEnvironment;
import fuzs.pickupnotifier.api.event.EntityItemPickupCallback;
import fuzs.pickupnotifier.client.handler.DrawEntriesHandler;
import fuzs.pickupnotifier.config.ClientConfig;
import fuzs.pickupnotifier.config.ServerConfig;
import fuzs.pickupnotifier.config.core.ConfigHolder;
import fuzs.pickupnotifier.config.core.ConfigManager;
import fuzs.pickupnotifier.handler.ItemPickupHandler;
import fuzs.pickupnotifier.network.NetworkDirection;
import fuzs.pickupnotifier.network.NetworkHandler;
import fuzs.pickupnotifier.network.message.S2CTakeItemMessage;
import fuzs.pickupnotifier.network.message.S2CTakeItemStackMessage;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PickUpNotifier implements ModInitializer {

    public static final String MODID = "pickupnotifier";
    public static final String NAME = "Pick Up Notifier";
    public static final Logger LOGGER = LogManager.getLogger(PickUpNotifier.NAME);

    @SuppressWarnings("Convert2MethodRef")
    public static final ConfigHolder<ClientConfig, ServerConfig> CONFIG = new ConfigHolder<>(() -> new ClientConfig(), () -> new ServerConfig());

    public PickUpNotifier() {

        this.addListeners(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG.buildSpec(), ConfigManager.getSimpleName(MODID));
    }

    private void addListeners(IEventBus bus) {

        bus.addListener(this::onCommonSetup);
        bus.addListener(this::onLoadComplete);
        bus.addListener(ConfigManager::onModConfig);
    }

    private void onCommonSetup(final FMLCommonSetupEvent evt) {

        this.registerMessages();
        MinecraftForge.EVENT_BUS.register(new ItemPickupHandler());
    }

    private void registerMessages() {

        NetworkHandler.INSTANCE.register(S2CTakeItemMessage.class, S2CTakeItemMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(S2CTakeItemStackMessage.class, S2CTakeItemStackMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }

    private void onLoadComplete(final FMLLoadCompleteEvent evt) {

        ConfigManager.sync();
    }

    @Override
    public void onInitialize() {

        ItemPickupHandler handler = new ItemPickupHandler();
        EntityItemPickupCallback.EVENT.register(handler::onEntityItemPickup);
        FabricEnvironment.init();
    }

}
