package fuzs.pickupnotifier.lib.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FabricEnvironment {

    public static EnvType getEnvironmentType() {

        return FabricLoader.getInstance().getEnvironmentType();
    }

    public static boolean isClient() {

        return getEnvironmentType() == EnvType.CLIENT;
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

}
