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
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish
 */
public abstract class EditListScreen<T> extends Screen
{
    private final Screen parent;
    private final List<MutableObject<T>> values = new ArrayList<>();
    private final ForgeConfigSpec.ValueSpec valueSpec;
    private final Consumer<List<T>> onSave;
    private EditListScreenList list;

    public EditListScreen(Screen parent, Component titleIn, List<T> listValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<T>> onSave)
    {
        super(titleIn);
        this.parent = parent;
        this.values.addAll(listValue.stream().map(MutableObject::new).collect(Collectors.toList()));
        this.valueSpec = valueSpec;
        this.onSave = onSave;
    }

    abstract String toString(T value);

    abstract T fromString(String value);

    @Override
    protected void init()
    {
        this.list = new EditListScreenList();
        this.addWidget(this.list);
        this.addRenderableWidget(new Button(this.width / 2 - 140, this.height - 29, 90, 20, CommonComponents.GUI_DONE, (button) -> {
            List<T> newValues = this.values.stream().map(MutableObject::getValue).collect(Collectors.toList());
            this.valueSpec.correct(newValues);
            this.onSave.accept(newValues);
            this.minecraft.setScreen(this.parent);
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 45, this.height - 29, 90, 20, new TranslatableComponent("configured.gui.add_value"), (button) -> {
            this.minecraft.setScreen(new EditStringScreen(EditListScreen.this, new TranslatableComponent("configured.gui.edit_value"), "", o -> true, s -> {
                MutableObject<T> holder = new MutableObject<>(this.fromString(s));
                this.values.add(holder);
                this.list.addEntry(new ListScreenEntry(this.list, holder));
            }));
        }));
        this.addRenderableWidget(new Button(this.width / 2 + 50, this.height - 29, 90, 20, CommonComponents.GUI_CANCEL, (button) -> {
            this.minecraft.setScreen(this.parent);
        }));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
    {
        this.renderBackground(poseStack);
        this.list.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Environment(EnvType.CLIENT)
    public class EditListScreenList extends ContainerObjectSelectionList<ListScreenEntry>
    {
        public EditListScreenList()
        {
            super(EditListScreen.this.minecraft, EditListScreen.this.width, EditListScreen.this.height, 36, EditListScreen.this.height - 36, 24);
            EditListScreen.this.values.forEach(value -> {
                this.addEntry(new ListScreenEntry(this, value));
            });
        }

        @Override
        protected int getScrollbarPosition()
        {
            return this.width / 2 + 144;
        }

        @Override
        public int getRowWidth()
        {
            return 260;
        }

        @Override
        public int addEntry(ListScreenEntry entry)
        {
            return super.addEntry(entry);
        }

        @Override
        public boolean removeEntry(ListScreenEntry entry)
        {
            return super.removeEntry(entry);
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
        {
            super.render(poseStack, mouseX, mouseY, partialTicks);
            this.children().forEach(entry ->
            {
                entry.children().forEach(o ->
                {
                    if(o instanceof AbstractSliderButton)
                    {
                        ((AbstractSliderButton)o).renderToolTip(poseStack, mouseX, mouseY);
                    }
                });
            });
        }
    }

    public class ListScreenEntry extends ContainerObjectSelectionList.Entry<ListScreenEntry>
    {
        private final MutableObject<T> holder;
        private final EditListScreenList list;
        private final Button editButton;
        private final Button deleteButton;

        public ListScreenEntry(EditListScreenList list, MutableObject<T> holder)
        {
            this.list = list;
            this.holder = holder;
            this.editButton = new Button(0, 0, 42, 20, new TextComponent("Edit"), onPress -> {
                EditListScreen.this.minecraft.setScreen(new EditStringScreen(EditListScreen.this, new TranslatableComponent("configured.gui.edit_value"), EditListScreen.this.toString(this.holder.getValue()), o -> true, value -> this.holder.setValue(EditListScreen.this.fromString(value))));
            });
            Button.OnTooltip tooltip = (button, matrixStack, mouseX, mouseY) -> {
                if(button.active && button.isHovered()) {
                    EditListScreen.this.renderTooltip(matrixStack, EditListScreen.this.minecraft.font.split(new TranslatableComponent("configured.gui.remove"), Math.max(EditListScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                }
            };
            this.deleteButton = new IconButton(0, 0, 20, 20, 11, 0, tooltip, onPress -> {
                EditListScreen.this.values.remove(this.holder);
                this.list.removeEntry(this);
            });
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int height, int mouseX, int mouseY, boolean selected, float partialTicks)
        {
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
        public List<? extends GuiEventListener> children()
        {
            return ImmutableList.of(this.editButton, this.deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return ImmutableList.of(new NarratableEntry()
            {
                public NarratableEntry.NarrationPriority narrationPriority()
                {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                public void updateNarration(NarrationElementOutput output)
                {
                    output.add(NarratedElementType.TITLE, EditListScreen.this.toString(ListScreenEntry.this.holder.getValue()));
                }
            }, ListScreenEntry.this.editButton, ListScreenEntry.this.deleteButton);
        }
    }
}
