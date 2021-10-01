package com.mrcrayfish.configured.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Author: MrCrayfish
 */
public class EditStringScreen extends Screen {
    private final Screen lastScreen;
    private String value;
    private final Predicate<String> validator;
    private final Consumer<String> onSave;
    private EditBox textField;

    public EditStringScreen(Screen lastScreen, Component title, String value, Predicate<String> validator, Consumer<String> onSave) {
        super(title);
        this.lastScreen = lastScreen;
        this.value = value;
        this.validator = validator;
        this.onSave = onSave;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    protected void init() {
        final Button doneButton = this.addRenderableWidget(new Button(this.width / 2 - 1 - 150, this.height / 2 + 3, 148, 20, CommonComponents.GUI_DONE, button -> {
            this.onSave.accept(this.textField.getValue());
            this.minecraft.setScreen(this.lastScreen);
        }));
        this.addRenderableWidget(new Button(this.width / 2 + 3, this.height / 2 + 3, 148, 20, CommonComponents.GUI_CANCEL, button -> {
            this.minecraft.setScreen(this.lastScreen);
        }));
        this.textField = new EditBox(this.font, this.width / 2 - 150, this.height / 2 - 25, 300, 20, TextComponent.EMPTY) {

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // left click clears text
                if (this.isVisible() && button == 1) {
                    this.setValue("");
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        };
        this.textField.setMaxLength(32500);
        this.textField.setResponder(input -> {
            // save this as init is re-run on screen resizing
            this.value = input;
            if (this.validator.test(input)) {
                this.textField.setTextColor(14737632);
                doneButton.active = true;
            } else {
                this.textField.setTextColor(!input.isEmpty() ? 16711680 : 14737632);
                doneButton.active = false;
            }
        });
        this.textField.setValue(this.value);
        this.addRenderableWidget(this.textField);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        this.textField.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }
}
