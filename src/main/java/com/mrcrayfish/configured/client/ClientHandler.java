package com.mrcrayfish.configured.client;

import com.mrcrayfish.configured.client.gui.screens.ConfigScreen;
import com.mrcrayfish.configured.client.gui.screens.SelectConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ClientHandler {

    public static Function<Screen, Screen> createConfigScreen(final String modId) {
        return createConfigScreen(modId, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, ResourceLocation optionsBackground) {
        return createConfigScreen(modId, FabricLoader.getInstance().getModContainer(modId).get().getMetadata().getName(), optionsBackground);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, String displayName) {
        return createConfigScreen(modId, displayName, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, String displayName, ResourceLocation optionsBackground) {
        final List<ModConfig> configs = ConfigTracker.INSTANCE.configSets().values().stream()
                .flatMap(Set::stream)
                .filter(config -> config.getModId().equals(modId))
                .collect(Collectors.toList());
        if (!configs.isEmpty()) {
            return createConfigScreen(displayName, optionsBackground, configs);
        }
        return lastScreen -> null;
    }

    private static Function<Screen, Screen> createConfigScreen(String displayName, ResourceLocation optionsBackground, List<ModConfig> configs) {
        final TextComponent title = new TextComponent(displayName);
        if (configs.size() == 1) {
            final ModConfig config = configs.get(0);
            if (config.getType() != ModConfig.Type.SERVER && config.getSpec() instanceof ForgeConfigSpec spec) {
                return lastScreen -> ConfigScreen.create(lastScreen, title, optionsBackground, spec);
            }
        }
        return lastScreen -> new SelectConfigScreen(lastScreen, title, optionsBackground, configs);
    }
}
