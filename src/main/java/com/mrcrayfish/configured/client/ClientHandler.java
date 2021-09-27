package com.mrcrayfish.configured.client;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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

        EnumMap<ModConfig.Type, Set<ModConfig>> modConfigTypeSets = Maps.newEnumMap(ModConfig.Type.class);
        ConfigTracker.INSTANCE.configSets().forEach((type, modConfigs) -> {

            modConfigs.forEach(modConfig -> {

                if (modConfig.getModId().equals(modId)) {

                    final Set<ModConfig> modConfigTypeSet = modConfigTypeSets.computeIfAbsent(type, type1 -> Sets.newHashSet());
                    modConfigTypeSet.add(modConfig);
                }
            });
        });

        Set<ModConfig> clientConfigs = modConfigTypeSets.get(ModConfig.Type.CLIENT);
        Set<ModConfig> commonConfigs = modConfigTypeSets.get(ModConfig.Type.COMMON);

        List<ConfigScreen.ConfigFileEntry> clientConfigFileEntries = getConfigFileEntries(clientConfigs);
        List<ConfigScreen.ConfigFileEntry> commonConfigFileEntries = getConfigFileEntries(commonConfigs);
        if(clientConfigFileEntries != null || commonConfigFileEntries != null) {    // Only add if at least one config exists
            return screen -> new ConfigScreen(screen, displayName, clientConfigFileEntries, commonConfigFileEntries, optionsBackground);
        }

        return null;

//        // Constructs a map to get all configs registered by a mod
//        Map<String, Map<ModConfig.Type, Set<ModConfig>>> idToConfigs = new HashMap<>();
//        ConfigTracker.INSTANCE.configSets().forEach((type, modConfigs) ->
//        {
//            modConfigs.forEach(modConfig ->
//            {
//                Map<ModConfig.Type, Set<ModConfig>> typeToConfigSet = idToConfigs.computeIfAbsent(modConfig.getModId(), s -> new HashMap<>());
//                Set<ModConfig> configSet = typeToConfigSet.computeIfAbsent(type, t -> new HashSet<>());
//                configSet.add(modConfig);
//            });
//        });
//
//        PickUpNotifier.LOGGER.info("Creating config GUI factories...");
//        ModList.get().forEachModContainer((modId2, container) ->
//        {
//            // Ignore mods that already implement their own custom factory
//            if(container.getCustomExtension(ConfigGuiHandler.ConfigGuiFactory.class).isPresent())
//                return;
//
//            Map<ModConfig.Type, Set<ModConfig>> typeToConfigSet = idToConfigs.get(modId2);
//            if(typeToConfigSet == null)
//                return;
//
//            Set<ModConfig> clientConfigs = typeToConfigSet.get(ModConfig.Type.CLIENT);
//            Set<ModConfig> commonConfigs = typeToConfigSet.get(ModConfig.Type.COMMON);
//
//            List<ConfigScreen.ConfigFileEntry> clientConfigFileEntries = getConfigFileEntries(clientConfigs);
//            List<ConfigScreen.ConfigFileEntry> commonConfigFileEntries = getConfigFileEntries(commonConfigs);
//            if(clientConfigFileEntries != null || commonConfigFileEntries != null) // Only add if at least one config exists
//            {
//                PickUpNotifier.LOGGER.info("Registering config factory for mod {} (client: {}, common: {})", modId2, clientConfigFileEntries != null, commonConfigFileEntries != null);
//                ResourceLocation background = Screen.BACKGROUND_LOCATION;
//                if(container.getModInfo() instanceof ModInfo)
//                {
//                    String configBackground = (String) container.getModInfo().getModProperties().get("configuredBackground");
//                    if(configBackground == null)
//                    {
//                        // Fallback to old method to getting config background (since mods might not have updated)
//                        Optional<String> optional = ((ModInfo) container.getModInfo()).getConfigElement("configBackground");
//                        if(optional.isPresent())
//                        {
//                            configBackground = optional.get();
//                        }
//                    }
//                    if(configBackground != null)
//                    {
//                        background = new ResourceLocation(configBackground);
//                    }
//                }
//                String displayName = container.getModInfo().getDisplayName();
//                final ResourceLocation finalBackground = background;
//                container.registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class, () -> new ConfigGuiHandler.ConfigGuiFactory((mc, screen) -> new ConfigScreen(screen, displayName, clientConfigFileEntries, commonConfigFileEntries, finalBackground)));
//            }
//        });
    }

    /* Since ModConfig#getSpec now returns a generic interface, I need to cast check for ForgeConfigSpec */
    @Nullable
    private static List<ConfigScreen.ConfigFileEntry> getConfigFileEntries(@Nullable Set<ModConfig> configs)
    {
        if(configs != null && configs.size() > 0)
        {
            return configs.stream().filter(config -> config.getSpec() instanceof ForgeConfigSpec).map(config -> {
                ForgeConfigSpec spec = (ForgeConfigSpec) config.getSpec();
                return new ConfigScreen.ConfigFileEntry(spec, spec.getValues());
            }).collect(Collectors.toList());
        }
        return null;
    }

}
