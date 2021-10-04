package com.mrcrayfish.configured.client;

import com.mrcrayfish.configured.client.gui.screens.SelectConfigScreen;
import com.mrcrayfish.configured.client.gui.util.ScreenUtil;
import fuzs.puzzleslib.core.ModLoaderEnvironment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ClientHandler {

    public static Function<Screen, Screen> createConfigScreen(final String modId) {
        return createConfigScreen(modId, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, ResourceLocation optionsBackground) {
        return createConfigScreen(modId, ModLoaderEnvironment.getModContainer(modId).map(ModContainer::getMetadata).map(ModMetadata::getName).orElse(ScreenUtil.formatText(modId)), optionsBackground);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, String displayName) {
        return createConfigScreen(modId, displayName, GuiComponent.BACKGROUND_LOCATION);
    }

    public static Function<Screen, Screen> createConfigScreen(final String modId, String displayName, ResourceLocation optionsBackground) {
        final Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().values().stream()
                .flatMap(Set::stream)
                .filter(config -> config.getModId().equals(modId))
                .collect(Collectors.toSet());
        if (!configs.isEmpty()) {
            return lastScreen -> new SelectConfigScreen(lastScreen, new TextComponent(displayName), optionsBackground, configs);
        }
        return lastScreen -> null;
    }

}
