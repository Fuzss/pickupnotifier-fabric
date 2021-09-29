package fuzs.pickupnotifier.compat;

import com.mrcrayfish.configured.client.ClientHandler;
import com.mrcrayfish.configured.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fuzs.pickupnotifier.PickUpNotifier;

public class ModMenuMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<ConfigScreen> getModConfigScreenFactory() {
        return lastScreen -> ClientHandler.createConfigScreen(PickUpNotifier.MODID).apply(lastScreen);
    }
}
