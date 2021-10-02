package com.mrcrayfish.configured.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.client.screen.util.ScreenUtil;
import com.mrcrayfish.configured.client.screen.widget.ConfigEditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Author: MrCrayfish
 */
@SuppressWarnings("ConstantConditions")
public class EditStringScreen extends Screen {
    private final Screen lastScreen;
    private final ResourceLocation background;
    private String value;
    private final Predicate<String> validator;
    private final Consumer<String> onSave;
    private ConfigEditBox textField;

    public EditStringScreen(Screen lastScreen, Component title, ResourceLocation background, String value, Predicate<String> validator, Consumer<String> onSave) {
        super(title);
        this.lastScreen = lastScreen;
        this.background = background;
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
        this.textField = new ConfigEditBox(this.font, this.width / 2 - 150, this.height / 2 - 25, 300, 20);
        this.textField.setMaxLength(32500);
        this.textField.setResponder(input -> {
            // save this as init is re-run on screen resizing
            this.value = input;
            if (this.validator.test(input)) {
                this.textField.markInvalid(false);
                doneButton.active = true;
            } else {
                this.textField.markInvalid(true);
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

    @Override
    public void renderDirtBackground(int vOffset) {
        ScreenUtil.renderCustomBackground(this, this.background, vOffset);
    }
}
