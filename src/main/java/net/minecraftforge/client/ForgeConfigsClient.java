package net.minecraftforge.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraftforge.network.client.config.ConfigSyncClient;

public class ForgeConfigsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ConfigSyncClient.INSTANCE.clientInit();
        // loaded immediately on fabric
//        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, ModLoaderEnvironment.getConfigDir());
    }
}