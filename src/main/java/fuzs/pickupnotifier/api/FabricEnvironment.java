package fuzs.pickupnotifier.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.Supplier;

public enum FabricEnvironment implements LoaderEnvironment {

    INSTANCE;

    private Supplier<Minecraft> client;
    private Supplier<MinecraftServer> server;

    public static void init() {

        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> FabricEnvironment.INSTANCE.client = () -> minecraft);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> FabricEnvironment.INSTANCE.server = () -> server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FabricEnvironment.INSTANCE.server = () -> null);
    }

    @Override
    public <T> T getInstance(EnvType envType) {

        return (T) (envType == EnvType.CLIENT ? this.client.get() : this.server.get());
    }

    @Override
    public EnvType getEnvironmentType() {

        return FabricLoader.getInstance().getEnvironmentType();
    }

    @Override
    public boolean isClient() {

        return this.getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isServer() {

        return !this.isClient();
    }

    @Override
    public Path getGameDir() {

        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDir() {

        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

}
