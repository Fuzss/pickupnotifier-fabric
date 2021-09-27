package fuzs.pickupnotifier.client;

import fuzs.pickupnotifier.client.handler.DrawEntriesHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.sync.client.ConfigSyncClient;
import fuzs.pickupnotifier.lib.core.FabricEnvironment;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class PickUpNotifierClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        DrawEntriesHandler handler = new DrawEntriesHandler();
        ClientTickEvents.END_CLIENT_TICK.register(handler::onClientTick);
        HudRenderCallback.EVENT.register(handler::onRenderGameOverlayText);

        ConfigSyncClient.INSTANCE.clientInit();
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FabricEnvironment.getConfigDir());
    }

}
