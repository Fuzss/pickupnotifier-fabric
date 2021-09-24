package fuzs.pickupnotifier.lib.config;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class ModConfigEvents {

    public static final Event<Loading> LOADING = EventFactory.createArrayBacked(Loading.class, listeners -> config -> {

        for (Loading event : listeners) {

            event.onModConfigLoading(config);
        }
    });

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
