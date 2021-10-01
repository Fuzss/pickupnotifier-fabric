package com.mrcrayfish.configured.client.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish
 */
@SuppressWarnings("ConstantConditions")
public abstract class EditListScreen<T> extends Screen {
    private final Screen lastScreen;
    private final Class<T> type;
    private final List<MutableObject<T>> values = new ArrayList<>();
    private final ForgeConfigSpec.ValueSpec valueSpec;
    private final Consumer<List<T>> onSave;
    private EditList list;
    private List<? extends FormattedCharSequence> activeTooltip;
    private int tooltipTicks;

    public EditListScreen(Screen lastScreen, Component titleIn, Class<T> type, List<T> listValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<T>> onSave) {
        super(new TranslatableComponent("configured.gui.list.edit", titleIn));
        this.lastScreen = lastScreen;
        this.type = type;
        this.values.addAll(listValue.stream().map(MutableObject::new).collect(Collectors.toList()));
        this.valueSpec = valueSpec;
        this.onSave = onSave;
    }

    abstract String toString(T value);

    abstract T fromString(String value);

    @Override
    protected void init() {
        this.list = new EditList();
        this.addWidget(this.list);
        this.addRenderableWidget(new Button(this.width / 2 - 50 - 105, this.height - 28, 100, 20, CommonComponents.GUI_DONE, button -> {
            List<T> newValues = this.values.stream().map(MutableObject::getValue).collect(Collectors.toList());
            this.valueSpec.correct(newValues);
            this.onSave.accept(newValues);
            this.minecraft.setScreen(this.lastScreen);
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 50 + 105, this.height - 28, 100, 20, new TranslatableComponent("configured.gui.add"), button -> {
            this.minecraft.setScreen(this.makeEditListScreen("configured.gui.value.add", "", input -> {
                MutableObject<T> holder = new MutableObject<>(this.fromString(input));
                this.values.add(holder);
                this.list.addEntry(new EditListEntry(this.list, holder));
            }));
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 29, 100, 20, CommonComponents.GUI_CANCEL, button -> {
            this.minecraft.setScreen(this.lastScreen);
        }));
    }

    private EditStringScreen makeEditListScreen(String translationKey, String value, Consumer<String> onSave) {
        return new EditStringScreen(EditListScreen.this, new TranslatableComponent(translationKey, this.type.getSimpleName()), value, input -> {
            try {
                this.fromString(input);
                return true;
            } catch (RuntimeException ignored) {
            }
            return false;
        }, onSave);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        List<? extends FormattedCharSequence> lastTooltip = this.activeTooltip;
        this.activeTooltip = null;
        this.renderBackground(poseStack);
        this.list.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        if (this.activeTooltip != lastTooltip) {
            this.tooltipTicks = 0;
        }
        if (this.activeTooltip != null && this.tooltipTicks >= 10) {
            this.renderTooltip(poseStack, this.activeTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void tick() {
        // makes tooltips not appear immediately
        if (this.tooltipTicks < 10) {
            this.tooltipTicks++;
        }
    }

    @Environment(EnvType.CLIENT)
    public class EditList extends ContainerObjectSelectionList<EditListEntry> {
        public EditList() {
            super(EditListScreen.this.minecraft, EditListScreen.this.width, EditListScreen.this.height, 36, EditListScreen.this.height - 36, 24);
            EditListScreen.this.values.forEach(value -> {
                this.addEntry(new EditListEntry(this, value));
            });
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 144;
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        public int addEntry(EditListEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public boolean removeEntry(EditListEntry entry) {
            return super.removeEntry(entry);
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
            super.render(poseStack, mouseX, mouseY, partialTicks);
            this.children().forEach(entry ->
            {
                entry.children().forEach(o ->
                {
                    if (o instanceof AbstractSliderButton) {
                        ((AbstractSliderButton) o).renderToolTip(poseStack, mouseX, mouseY);
                    }
                });
            });
        }
    }

    private class EditListEntry extends ContainerObjectSelectionList.Entry<EditListEntry> {
        private final MutableObject<T> holder;
        private final EditList list;
        private final Button editButton;
        private final Button deleteButton;

        private EditListEntry(EditList list, MutableObject<T> holder) {
            this.list = list;
            this.holder = holder;
            this.editButton = new Button(0, 0, 42, 20, new TranslatableComponent("configured.gui.edit"), onPress -> {
                EditListScreen.this.minecraft.setScreen(EditListScreen.this.makeEditListScreen("configured.gui.value.edit", EditListScreen.this.toString(this.holder.getValue()), input -> {
                    this.holder.setValue(EditListScreen.this.fromString(input));
                }));
            });
            final List<FormattedCharSequence> tooltip = EditListScreen.this.minecraft.font.split(new TranslatableComponent("configured.gui.tooltip.remove"), 200);
            this.deleteButton = new IconButton(0, 0, 20, 20, 11, 0, button -> {
                EditListScreen.this.values.remove(this.holder);
                this.list.removeEntry(this);
            }, (button, matrixStack, mouseX, mouseY) -> {
                if (button.active) {
                    EditListScreen.this.activeTooltip = tooltip;
                }
            });
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int height, int mouseX, int mouseY, boolean selected, float partialTicks) {
            EditListScreen.this.minecraft.font.drawShadow(poseStack, new TextComponent(EditListScreen.this.toString(this.holder.getValue())), left + 5, top + 6, 0xFFFFFF);
            this.editButton.visible = true;
            this.editButton.x = left + width - 65;
            this.editButton.y = top;
            this.editButton.render(poseStack, mouseX, mouseY, partialTicks);
            this.deleteButton.visible = true;
            this.deleteButton.x = left + width - 21;
            this.deleteButton.y = top;
            this.deleteButton.render(poseStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.editButton, this.deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, EditListScreen.this.toString(EditListEntry.this.holder.getValue()));
                }
            }, EditListEntry.this.editButton, EditListEntry.this.deleteButton);
        }
    }
}
