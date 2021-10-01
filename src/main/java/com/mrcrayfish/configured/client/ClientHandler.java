package com.mrcrayfish.configured.client;

import com.google.common.collect.Maps;
import com.mrcrayfish.configured.client.screen.ConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish
 */
@Environment(EnvType.CLIENT)
public class ClientHandler {

    public static Function<Screen, ConfigScreen> createConfigScreen(final String modId) {
        return createConfigScreen(modId, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, ConfigScreen> createConfigScreen(final String modId, ResourceLocation optionsBackground) {
        return createConfigScreen(modId, FabricLoader.getInstance().getModContainer(modId).get().getMetadata().getName(), optionsBackground);
    }

    public static Function<Screen, ConfigScreen> createConfigScreen(final String modId, String displayName) {
        return createConfigScreen(modId, displayName, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, ConfigScreen> createConfigScreen(final String modId, String displayName, ResourceLocation optionsBackground) {
        Map<ModConfig.Type, List<ForgeConfigSpec>> typeToConfigs = Maps.newEnumMap(ModConfig.Type.class);
        ConfigTracker.INSTANCE.configSets().forEach((type, modConfigs) -> {
            // exclude server config in multiplayer as local changes won't match the server anymore
            if (Minecraft.getInstance().isLocalServer() || type != ModConfig.Type.SERVER) {
                // configData for server configs will only be set when in a world
                final Set<ModConfig> configs = modConfigs.stream()
                        .filter(modConfig -> modConfig.getModId().equals(modId) && modConfig.getConfigData() != null)
                        .collect(Collectors.toSet());
                if (!configs.isEmpty()) {
                    typeToConfigs.put(type, configs.stream()
                            .filter(config -> config.getSpec() instanceof ForgeConfigSpec)
                            .map(config -> (ForgeConfigSpec) config.getSpec())
                            .toList());
                }
            }
        });

        // Only add screen if at least one config exists
        if (!typeToConfigs.isEmpty()) {
            return lastScreen -> ConfigScreen.create(lastScreen, new TextComponent(displayName), optionsBackground, typeToConfigs);
        }

        return lastScreen -> null;
    }

}
