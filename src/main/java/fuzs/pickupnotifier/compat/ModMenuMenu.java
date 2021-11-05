package fuzs.pickupnotifier.compat;

import fuzs.configmenusforge.client.handler.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fuzs.pickupnotifier.PickUpNotifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

public class ModMenuMenu implements ModMenuApi {

    @Override
    public com.terraformersmc.modmenu.api.ConfigScreenFactory<Screen> getModConfigScreenFactory() {
        return lastScreen -> ConfigScreenFactory.createConfigScreen(PickUpNotifier.MODID, new ResourceLocation("textures/block/cobblestone.png")).orElse(screen -> null).apply(lastScreen);
    }
}
