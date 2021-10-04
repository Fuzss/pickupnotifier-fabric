package fuzs.puzzleslib.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.Optional;

public class ModLoaderEnvironment {

    public static EnvType getEnvironmentType() {

        return FabricLoader.getInstance().getEnvironmentType();
    }

    public static boolean isEnvironmentType(EnvType envType) {

        return getEnvironmentType() == envType;
    }

    public static boolean isClient() {

        return isEnvironmentType(EnvType.CLIENT);
    }

    public static boolean isServer() {

        return !isClient();
    }

    public static Path getGameDir() {

        return FabricLoader.getInstance().getGameDir();
    }

    public static Path getConfigDir() {

        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static Optional<ModContainer> getModContainer(String modId) {

        return FabricLoader.getInstance().getModContainer(modId);
    }

}
