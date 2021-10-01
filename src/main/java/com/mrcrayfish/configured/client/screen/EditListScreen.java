package com.mrcrayfish.configured.client.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.client.screen.widget.ConfigEditBox;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish
 */
@SuppressWarnings("ConstantConditions")
public class EditListScreen extends Screen {
    private final Screen lastScreen;
    private final List<MutableObject<String>> values;
    private final Predicate<String> validator;
    private final Consumer<List<String>> onSave;
    private final Set<EditListEntry> invalidEntries = Sets.newHashSet();
    private EditList list;
    private Button doneButton;
    @Nullable
    private ConfigEditBox activeTextField;
    @Nullable
    private List<? extends FormattedCharSequence> activeTooltip;
    private int tooltipTicks;

    public EditListScreen(Screen lastScreen, Component title, List<String> listValue, Predicate<String> validator, Consumer<List<String>> onSave) {
        super(title);
        this.lastScreen = lastScreen;
        this.values = listValue.stream()
                .map(MutableObject::new)
                .collect(Collectors.toList());
        this.validator = validator;
        this.onSave = onSave;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    protected void init() {
        this.list = new EditList();
        this.addWidget(this.list);
        this.doneButton = this.addRenderableWidget(new Button(this.width / 2 - 50 - 105, this.height - 28, 100, 20, CommonComponents.GUI_DONE, button -> {
            this.onSave.accept(this.values.stream()
                    .map(MutableObject::getValue)
                    .collect(Collectors.toList()));
            this.minecraft.setScreen(this.lastScreen);
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 50 + 105, this.height - 28, 100, 20, new TranslatableComponent("configured.gui.add"), button -> {
            MutableObject<String> holder = new MutableObject<>("");
            this.values.add(holder);
            this.list.addEntry(new EditListEntry(this.list, holder));
        }));
        this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 29, 100, 20, CommonComponents.GUI_CANCEL, button -> {
            this.minecraft.setScreen(this.lastScreen);
        }));
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
        // makes the cursor blink
        if (this.activeTextField != null) {
            this.activeTextField.tick();
        }
        // makes tooltips not appear immediately
        if (this.tooltipTicks < 10) {
            this.tooltipTicks++;
        }
    }

    private void updateDoneButton() {
        if (this.doneButton != null) {
            this.doneButton.active = this.invalidEntries.isEmpty();
        }
    }

    void markInvalid(EditListEntry entry) {
        this.invalidEntries.add(entry);
        this.updateDoneButton();
    }

    void clearInvalid(EditListEntry entry) {
        this.invalidEntries.remove(entry);
        this.updateDoneButton();
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
        protected int addEntry(EditListEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        protected boolean removeEntry(EditListEntry entry) {
            return super.removeEntry(entry);
        }
    }

    private class EditListEntry extends ContainerObjectSelectionList.Entry<EditListEntry> {
        private final MutableObject<String> holder;
        private final ConfigEditBox textField;
        private final Button deleteButton;

        private EditListEntry(EditList list, MutableObject<String> holder) {
            this.holder = holder;
            this.textField = new ConfigEditBox(EditListScreen.this.font, 0, 0, 260 - 24, 18, () -> EditListScreen.this.activeTextField, activeTextField -> EditListScreen.this.activeTextField = activeTextField) {

                @Override
                public void setFocus(boolean focused) {
                    super.setFocus(focused);
                    EditListScreen.this.activeTextField = focused ? this : null;
                }
            };
            this.textField.setResponder(input -> {
                if (EditListScreen.this.validator.test(input)) {
                    this.textField.markInvalid(false);
                    this.holder.setValue(input);
                    EditListScreen.this.clearInvalid(this);
                } else {
                    this.textField.markInvalid(true);
                    EditListScreen.this.markInvalid(this);
                }
            });
            this.textField.setValue(holder.getValue());
            final List<FormattedCharSequence> tooltip = EditListScreen.this.minecraft.font.split(new TranslatableComponent("configured.gui.tooltip.remove"), 200);
            this.deleteButton = new IconButton(0, 0, 20, 20, 11, 0, button -> {
                EditListScreen.this.values.remove(holder);
                list.removeEntry(this);
                EditListScreen.this.clearInvalid(this);
            }, (button, matrixStack, mouseX, mouseY) -> {
                if (button.active) {
                    EditListScreen.this.activeTooltip = tooltip;
                }
            });
        }

        @Override
        public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            EditListScreen.this.minecraft.font.drawShadow(poseStack, new TextComponent(this.holder.getValue()), entryLeft + 5, entryTop + 6, 0xFFFFFF);
            this.textField.x = entryLeft;
            this.textField.y = entryTop + 1;
            this.textField.render(poseStack, mouseX, mouseY, partialTicks);
            this.deleteButton.x = entryLeft + rowWidth - 21;
            this.deleteButton.y = entryTop;
            this.deleteButton.render(poseStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.textField, this.deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, EditListEntry.this.holder.getValue());
                }
            }, EditListEntry.this.textField, EditListEntry.this.deleteButton);
        }
    }
}
