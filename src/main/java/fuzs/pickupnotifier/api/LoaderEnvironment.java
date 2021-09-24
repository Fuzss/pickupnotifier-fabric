package fuzs.pickupnotifier.api;

import net.fabricmc.api.EnvType;

import java.nio.file.Path;

public interface LoaderEnvironment {

    <T> T getInstance(EnvType envType);

    EnvType getEnvironmentType();

    boolean isClient();

    boolean isServer();

    Path getGameDir();

    Path getConfigDir();

    boolean isDevelopmentEnvironment();

    boolean isModLoaded(String modId);

}
