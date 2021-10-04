package net.minecraftforge.fml.event.config;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraftforge.fml.config.ModConfig;

public final class ModConfigEvent {

    /**
     * Called when a config is loaded or unloaded (only applies for server configs)
     * Unlike on forge there are is no mod event bus for firing mod specific events on fabric, meaning this event is fired for every mod for every config
     * So you should check for your mod id via {@link ModConfig#getModId()}
     */
    public static final Event<Loading> LOADING = EventFactory.createArrayBacked(Loading.class, listeners -> config -> {

        for (Loading event : listeners) {

            event.onModConfigLoading(config);
        }
    });

    /**
     * Called when a config is reloaded which happens when the file is updated by ConfigWatcher and when it is synced from the server
     * Unlike on forge there are is no mod event bus for firing mod specific events on fabric, meaning this event is fired for every mod for every config
     * So you should check for your mod id via {@link ModConfig#getModId()}
     */
    public static final Event<Reloading> RELOADING = EventFactory.createArrayBacked(Reloading.class, listeners -> config -> {

        for (Reloading event : listeners) {

            event.onModConfigReloading(config);
        }
    });

    @FunctionalInterface
    public interface Loading {

        void onModConfigLoading(ModConfig config);

    }

    @FunctionalInterface
    public interface Reloading {

        void onModConfigReloading(ModConfig config);

    }

}
