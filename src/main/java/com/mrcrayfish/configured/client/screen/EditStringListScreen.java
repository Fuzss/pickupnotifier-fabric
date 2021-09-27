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
public class EditStringListScreen extends Screen
{
    private final Screen parent;
    private final List<MutableObject<String>> values = new ArrayList<>();
    private final List<?> listValue;
    private final ForgeConfigSpec.ValueSpec valueSpec;
    private final Consumer<List<?>> onSave;
    private StringList list;

    public EditStringListScreen(Screen parent, Component titleIn, List<?> listValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<?>> onSave)
    {
        super(titleIn);
        this.parent = parent;
        this.listValue = listValue;
        this.valueSpec = valueSpec;
        this.onSave = onSave;
        this.values.addAll(listValue.stream().map(o -> new MutableObject<>(o.toString())).collect(Collectors.toList()));
    }

    @Override
    protected void init()
    {
        this.list = new StringList();
        this.addWidget(this.list);
        this.addRenderableWidget(new Button(this.width / 2 - 140, this.height - 29, 90, 20, CommonComponents.GUI_DONE, (button) -> {
            List<String> newValues = this.values.stream().map(MutableObject::getValue).collect(Collectors.toList());
            this.valueSpec.correct(newValues);
            this.onSave.accept(newValues);
            this.minecraft.setScreen(this.parent);
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 45, this.height - 29, 90, 20, new TranslatableComponent("configured.gui.add_value"), (button) -> {
            this.minecraft.setScreen(new EditStringScreen(EditStringListScreen.this, new TranslatableComponent("configured.gui.edit_value"), "", o -> true, s -> {
                MutableObject<String> holder = new MutableObject<>(s);
                this.values.add(holder);
                this.list.addEntry(new StringEntry(this.list, holder));
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
    public class StringList extends ContainerObjectSelectionList<StringEntry>
    {
        public StringList()
        {
            super(EditStringListScreen.this.minecraft, EditStringListScreen.this.width, EditStringListScreen.this.height, 36, EditStringListScreen.this.height - 36, 24);
            EditStringListScreen.this.values.forEach(value -> {
                this.addEntry(new StringEntry(this, value));
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
        public int addEntry(StringEntry entry)
        {
            return super.addEntry(entry);
        }

        @Override
        public boolean removeEntry(StringEntry entry)
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

    public class StringEntry extends ContainerObjectSelectionList.Entry<StringEntry>
    {
        private final MutableObject<String> holder;
        private final StringList list;
        private final Button editButton;
        private final Button deleteButton;

        public StringEntry(StringList list, MutableObject<String> holder)
        {
            this.list = list;
            this.holder = holder;
            this.editButton = new Button(0, 0, 42, 20, new TextComponent("Edit"), onPress -> {
                EditStringListScreen.this.minecraft.setScreen(new EditStringScreen(EditStringListScreen.this, new TranslatableComponent("configured.gui.edit_value"), this.holder.getValue(), o -> true, this.holder::setValue));
            });
            Button.OnTooltip tooltip = (button, matrixStack, mouseX, mouseY) -> {
                if(button.active && button.isHovered()) {
                    EditStringListScreen.this.renderTooltip(matrixStack, EditStringListScreen.this.minecraft.font.split(new TranslatableComponent("configured.gui.remove"), Math.max(EditStringListScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                }
            };
            this.deleteButton = new IconButton(0, 0, 20, 20, 11, 0, tooltip, onPress -> {
                EditStringListScreen.this.values.remove(this.holder);
                this.list.removeEntry(this);
            });
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean selected, float partialTicks)
        {
            EditStringListScreen.this.minecraft.font.drawShadow(poseStack, new TextComponent(this.holder.getValue()), left + 5, top + 6, 0xFFFFFF);
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
                    output.add(NarratedElementType.TITLE, StringEntry.this.holder.getValue());
                }
            }, StringEntry.this.editButton, StringEntry.this.deleteButton);
        }
    }
}
