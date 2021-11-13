package net.minecraftforge;

import fuzs.puzzleslib.core.ModLoaderEnvironment;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.mixin.LevelResourceAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.config.ConfigSync;
import net.minecraftforge.fml.loading.FileUtils;
import net.minecraftforge.server.command.ConfigCommand;
import net.minecraftforge.server.command.EnumArgument;
import net.minecraftforge.server.command.ModIdArgument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.nio.file.Path;

public class ForgeConfigs implements ModInitializer {

    public static final String MODID = "forgeconfigs";
    public static final String NAME = "Forge Configs";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static final String SERVER_CONFIG_NAME = "serverconfig";
    public static final String DEFAULT_CONFIG_NAME = "defaultconfigs";

    public static final Marker CORE = MarkerManager.getMarker("CORE");

    @Override
    public void onInitialize() {

        ConfigSync.INSTANCE.init();
        // loaded immediately on fabric
//        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FabricEnvironment.getConfigDir());
        this.loadDefaultConfigPath();
        this.registerArgumentTypes();
        this.registerCallbacks();
    }

    private void loadDefaultConfigPath() {
        LOGGER.trace(CORE, "Default config paths at {}", DEFAULT_CONFIG_NAME);
        FileUtils.getOrCreateDirectory(ModLoaderEnvironment.getGameDir().resolve(DEFAULT_CONFIG_NAME), "default config directory");
    }

    private void registerArgumentTypes() {
        ArgumentTypes.register(new ResourceLocation(MODID, "enum").toString(), EnumArgument.class, (ArgumentSerializer) new EnumArgument.Serializer());
        ArgumentTypes.register(new ResourceLocation(MODID, "modid").toString(), ModIdArgument.class, new EmptyArgumentSerializer<>(ModIdArgument::modIdArgument));
    }

    private void registerCallbacks() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            if (!dedicated) ConfigCommand.register(dispatcher);
        });
        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, getServerConfigPath(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER, getServerConfigPath(server)));
    }

    private static final LevelResource SERVERCONFIG = LevelResourceAccessor.create(SERVER_CONFIG_NAME);

    public static Path getServerConfigPath(final MinecraftServer server) {
        final Path serverConfig = server.getWorldPath(SERVERCONFIG);
        FileUtils.getOrCreateDirectory(serverConfig, "server config directory");
        return serverConfig;
    }

    public static void registerConfig(String modId, ModConfig.Type type, IConfigSpec<?> spec) {
        new ModConfig(type, spec, FabricLoader.getInstance().getModContainer(modId).orElseThrow(() -> new IllegalArgumentException(String.format("no mod with mod id %s", modId))));
    }

    public static void registerConfig(String modId, ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
        new ModConfig(type, spec, FabricLoader.getInstance().getModContainer(modId).orElseThrow(() -> new IllegalArgumentException(String.format("no mod with mod id %s", modId))), fileName);
    }
}
