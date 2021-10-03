package com.mrcrayfish.configured.client.util;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.network.client.message.C2SSendConfigMessage;
import fuzs.pickupnotifier.lib.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

public class ServerConfigUploader {

    private static final Field CHILD_CONFIG_FIELD;

    static {
        Field childConfig = null;
        try {
            childConfig = ForgeConfigSpec.class.getDeclaredField("childConfig");
            childConfig.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
        CHILD_CONFIG_FIELD = childConfig;
    }

    private static Config getChildConfig(ForgeConfigSpec spec) {
        if (CHILD_CONFIG_FIELD != null) {
            try {
                return (Config) CHILD_CONFIG_FIELD.get(spec);
            } catch (IllegalAccessException ignored) {
            }
        }
        Configured.LOGGER.warn("unable to sync server config data to dedicated server");
        return null;
    }

    public static void saveAndUpload(ModConfig modConfig) {
        final ForgeConfigSpec spec = (ForgeConfigSpec) modConfig.getSpec();
        spec.save();
        if (modConfig.getType() == ModConfig.Type.SERVER && !Minecraft.getInstance().isLocalServer()) {
            final Config childConfig = getChildConfig(spec);
            if (childConfig != null) {
                final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                TomlFormat.instance().createWriter().write(childConfig, stream);
                NetworkHandler.INSTANCE.sendToServer(new C2SSendConfigMessage(modConfig.getFileName(), stream.toByteArray()));
            }
        }
    }
}
