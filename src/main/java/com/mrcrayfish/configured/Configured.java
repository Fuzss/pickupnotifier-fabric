package com.mrcrayfish.configured;

import com.mrcrayfish.configured.config.TestConfig;
import com.mrcrayfish.configured.network.client.message.C2SAskPermissionsMessage;
import com.mrcrayfish.configured.network.client.message.C2SSendConfigMessage;
import com.mrcrayfish.configured.network.message.S2CGrantPermissionsMessage;
import com.mrcrayfish.configured.network.message.S2CUpdateConfigMessage;
import fuzs.pickupnotifier.PickUpNotifier;
import fuzs.puzzleslib.core.ModLoaderEnvironment;
import fuzs.puzzleslib.network.NetworkDirection;
import fuzs.puzzleslib.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.ForgeConfigs;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class Configured implements ModInitializer {

    public static final String MODID = "configured";
    public static final String NAME = "Configured";
    public static final String URL = "https://www.curseforge.com/minecraft/mc-mods/configured";
    public static final Logger LOGGER = LogManager.getLogger(Configured.NAME);

    @Override
    public void onInitialize() {
        this.registerMessages();
        this.initTestConfigs();
    }

    private void registerMessages() {
        NetworkHandler.INSTANCE.register(C2SAskPermissionsMessage.class, C2SAskPermissionsMessage::new, NetworkDirection.PLAY_TO_SERVER);
        NetworkHandler.INSTANCE.register(S2CGrantPermissionsMessage.class, S2CGrantPermissionsMessage::new, NetworkDirection.PLAY_TO_CLIENT);
        NetworkHandler.INSTANCE.register(C2SSendConfigMessage.class, C2SSendConfigMessage::new, NetworkDirection.PLAY_TO_SERVER);
        NetworkHandler.INSTANCE.register(S2CUpdateConfigMessage.class, S2CUpdateConfigMessage::new, NetworkDirection.PLAY_TO_CLIENT);
    }

    private void initTestConfigs() {
        if (ModLoaderEnvironment.isDevelopmentEnvironment()) {
            FileUtils.getOrCreateDirectory(ModLoaderEnvironment.getConfigDir().resolve(MODID), String.format("%s config directory", MODID));
            ForgeConfigs.registerConfig(PickUpNotifier.MODID, ModConfig.Type.CLIENT, TestConfig.CLIENT_SPEC, String.format("%s%s%s-%s.toml", MODID, File.separator, MODID, ModConfig.Type.CLIENT.extension()));
            ForgeConfigs.registerConfig(PickUpNotifier.MODID, ModConfig.Type.COMMON, TestConfig.COMMON_SPEC, String.format("%s-%s.toml", MODID, ModConfig.Type.COMMON.extension()));
            ForgeConfigs.registerConfig(PickUpNotifier.MODID, ModConfig.Type.SERVER, TestConfig.SERVER_SPEC, String.format("%s-%s.toml", MODID, ModConfig.Type.SERVER.extension()));
        }
    }
}
