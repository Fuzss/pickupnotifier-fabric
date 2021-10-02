package fuzs.pickupnotifier.compat;

import com.mrcrayfish.configured.client.ClientHandler;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fuzs.pickupnotifier.PickUpNotifier;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
        // new ResourceLocation("textures/block/honeycomb_block.png")
        return lastScreen -> ClientHandler.createConfigScreen(PickUpNotifier.MODID).apply(lastScreen);
    }
}
