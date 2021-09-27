package com.mrcrayfish.configured.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.Maps;
import com.mrcrayfish.configured.client.screen.ConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
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

    // This is where the magic happens
    public static Function<Screen, ConfigScreen> createConfigScreen(final String modId, String displayName, ResourceLocation optionsBackground) {

        EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> modConfigTypeSets = Maps.newEnumMap(ModConfig.Type.class);
        ConfigTracker.INSTANCE.configSets().forEach((type, modConfigs) -> {

            // configData for server configs will only be set when on a server (internal or dedicated)
            final Set<ModConfig> modConfigTypeSet = modConfigs.stream().filter(modConfig -> modConfig.getModId().equals(modId) && modConfig.getConfigData() != null).collect(Collectors.toSet());
            if (!modConfigTypeSet.isEmpty())
                modConfigTypeSets.put(type, getConfigFileEntries(modConfigTypeSet));
        });

        if(!modConfigTypeSets.isEmpty()) {    // Only add if at least one config exists
            return screen -> new ConfigScreen.Main(screen, displayName, modConfigTypeSets, optionsBackground);
        }

        return screen -> null;
    }

    /* Since ModConfig#getSpec now returns a generic interface, I need to cast check for ForgeConfigSpec */
    @Nullable
    private static List<Pair<ForgeConfigSpec, UnmodifiableConfig>> getConfigFileEntries(@Nullable Set<ModConfig> configs)
    {
        if(configs != null && configs.size() > 0)
        {
            return configs.stream().filter(config -> config.getSpec() instanceof ForgeConfigSpec).map(config -> {
                ForgeConfigSpec spec = (ForgeConfigSpec) config.getSpec();
                return Pair.of(spec, spec.getValues());
            }).collect(Collectors.toList());
        }
        return null;
    }

}
