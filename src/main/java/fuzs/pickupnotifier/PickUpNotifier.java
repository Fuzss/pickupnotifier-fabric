package fuzs.pickupnotifier;

import fuzs.pickupnotifier.api.event.EntityItemPickupCallback;
import fuzs.pickupnotifier.config.ClientConfig;
import fuzs.pickupnotifier.config.ServerConfig;
import fuzs.pickupnotifier.config.core.ConfigHolder;
import fuzs.pickupnotifier.config.core.ConfigManager;
import fuzs.pickupnotifier.handler.ItemPickupHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.command.command.ConfigCommand;
import net.minecraftforge.server.command.command.EnumArgument;
import net.minecraftforge.server.command.command.ModIdArgument;
import net.minecraftforge.fml.config.sync.ConfigSync;
import fuzs.pickupnotifier.lib.core.DistExecutor;
import fuzs.pickupnotifier.lib.core.FabricEnvironment;
import fuzs.pickupnotifier.lib.network.NetworkDirection;
import fuzs.pickupnotifier.lib.network.NetworkHandler;
import fuzs.pickupnotifier.lib.proxy.ClientProxy;
import fuzs.pickupnotifier.lib.proxy.IProxy;
import fuzs.pickupnotifier.lib.proxy.ServerProxy;
import fuzs.pickupnotifier.mixin.LevelResourceAccessor;
import fuzs.pickupnotifier.network.message.S2CTakeItemMessage;
import fuzs.pickupnotifier.network.message.S2CTakeItemStackMessage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        
        ModConfig.registerConfig(MODID, ModConfig.Type.COMMON, CONFIG.buildSpec(), ConfigManager.getSimpleName(MODID));
        ConfigManager.init(MODID);

        ConfigSync.INSTANCE.init();
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FabricEnvironment.getConfigDir());
        ArgumentTypes.register("forge:enum", EnumArgument.class, (ArgumentSerializer) new EnumArgument.Serializer());
        ArgumentTypes.register("forge:modid", ModIdArgument.class, new EmptyArgumentSerializer<>(ModIdArgument::modIdArgument));
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            if (!dedicated) {
                ConfigCommand.register(dispatcher);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, getServerConfigPath(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER, getServerConfigPath(server)));
    }

    private static final LevelResource SERVERCONFIG = LevelResourceAccessor.create("serverconfig");
    private static Path getServerConfigPath(final MinecraftServer server)
    {
        final Path serverConfig = server.getWorldPath(SERVERCONFIG);
        getOrCreateDirectory(serverConfig, "serverconfig");
        return serverConfig;
    }

    public static Path getOrCreateDirectory(Path dirPath, String dirLabel) {
        if (!Files.isDirectory(dirPath.getParent())) {
            getOrCreateDirectory(dirPath.getParent(), "parent of "+dirLabel);
        }
        if (!Files.isDirectory(dirPath))
        {
            LOGGER.debug("Making {} directory : {}", dirLabel, dirPath);
            try {
                Files.createDirectory(dirPath);
            } catch (IOException e) {
                if (e instanceof FileAlreadyExistsException) {
                    LOGGER.fatal("Failed to create {} directory - there is a file in the way", dirLabel);
                } else {
                    LOGGER.fatal("Problem with creating {} directory (Permissions?)", dirLabel, e);
                }
                throw new RuntimeException("Problem creating directory", e);
            }
            LOGGER.debug("Created {} directory : {}", dirLabel, dirPath);
        } else {
            LOGGER.debug("Found existing {} directory : {}", dirLabel, dirPath);
        }
        return dirPath;
    }

    private void registerMessages() {

        NetworkHandler.INSTANCE.register(S2CTakeItemMessage.class, S2CTakeItemMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(S2CTakeItemStackMessage.class, S2CTakeItemStackMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }

}
