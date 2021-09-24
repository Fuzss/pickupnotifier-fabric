package fuzs.pickupnotifier.client.handler;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import fuzs.pickupnotifier.PickUpNotifier;
import fuzs.pickupnotifier.client.gui.PositionPreset;
import fuzs.pickupnotifier.client.gui.entry.DisplayEntry;
import fuzs.pickupnotifier.client.util.PickUpCollector;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableInt;

public class DrawEntriesHandler {

    public static final PickUpCollector PICK_UPS = new PickUpCollector();
    public static final Int2ObjectArrayMap<MutableInt> HANDLED_ENTITIES = new Int2ObjectArrayMap<>();

    public void onClientTick(final Minecraft client) {

        if (!client.isPaused()) {

            if (!PickUpNotifier.CONFIG.client().general().forceClient && !HANDLED_ENTITIES.isEmpty()) {

                HANDLED_ENTITIES.values().forEach(MutableInt::increment);
                HANDLED_ENTITIES.values().removeIf(time -> time.intValue() > 80);
            }

            if (PickUpNotifier.CONFIG.client().behavior().displayTime != 0 && !PICK_UPS.isEmpty()) {

                PICK_UPS.tick();
            }
        }
    }

    public void onRenderGameOverlayText(final PoseStack matrixStack, final float tickDelta) {

        if (!PICK_UPS.isEmpty()) {

            final float scale = PickUpNotifier.CONFIG.client().display().scale / 6.0F;
            Window window = Minecraft.getInstance().getWindow();
            int scaledWidth = (int) (window.getGuiScaledWidth() / scale);
            int scaledHeight = (int) (window.getGuiScaledHeight() / scale);
            PositionPreset position = PickUpNotifier.CONFIG.client().display().position;
            int posX = (int) (PickUpNotifier.CONFIG.client().display().xOffset / scale);
            int posY = (int) (PickUpNotifier.CONFIG.client().display().yOffset / scale);
            int offset = position.getY(DisplayEntry.ENTRY_HEIGHT, scaledHeight, posY);
            int totalFade = PickUpNotifier.CONFIG.client().behavior().move ? (int) (PICK_UPS.getTotalFade(tickDelta) * DisplayEntry.ENTRY_HEIGHT) : 0;
            int entryX;
            int entryY = offset + (position.isBottom() ? totalFade : -totalFade);
            int entryHeight = position.isBottom() ? DisplayEntry.ENTRY_HEIGHT : -DisplayEntry.ENTRY_HEIGHT;

            for (DisplayEntry entry : PICK_UPS) {

                boolean mayRender = false;
                if (position.isBottom()) {

                    if (entryY < offset + entryHeight) {

                        mayRender = true;
                    }
                } else if (entryY > offset + entryHeight) {

                    mayRender = true;
                }

                if (mayRender) {

                    entryX = position.getX(entry.getEntryWidth(), scaledWidth, posX);
                    float alpha;
                    if (PickUpNotifier.CONFIG.client().behavior().move) {

                        alpha = Mth.clamp((float) (entryY - offset) / entryHeight, 0.0F, 1.0F);
                    } else {

                        alpha = entry.getRemainingTicksRelative(tickDelta);
                    }

                    entry.render(matrixStack, entryX, entryY, alpha, scale);
                }

                entryY -= entryHeight;
            }
        }
    }

}
