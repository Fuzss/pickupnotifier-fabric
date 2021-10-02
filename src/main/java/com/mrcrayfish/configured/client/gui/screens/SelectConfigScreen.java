package com.mrcrayfish.configured.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.client.gui.components.ConfigSelectionList;
import com.mrcrayfish.configured.client.gui.util.ScreenUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

@SuppressWarnings("ConstantConditions")
@Environment(EnvType.CLIENT)
public class SelectConfigScreen extends Screen {
	private final Screen lastScreen;
	private final ResourceLocation background;
	private final Component displayName;
	private final List<ModConfig> configs;
	private List<FormattedCharSequence> toolTip;
	private EditBox searchBox;
	private ConfigSelectionList list;
	private boolean serverPermissions;

	public SelectConfigScreen(Screen lastScreen, Component displayName, ResourceLocation optionsBackground, List<ModConfig> configs) {
		super(new TranslatableComponent("configured.gui.select.title", displayName));
		this.lastScreen = lastScreen;
		this.displayName = displayName;
		this.background = optionsBackground;
		this.configs = configs;
		this.serverPermissions = Minecraft.getInstance().isLocalServer();
	}

	@Override
	public void tick() {
		this.searchBox.tick();
	}

	@Override
	protected void init() {
		this.minecraft.keyboardHandler.setSendRepeatsToGui(true);
		this.searchBox = new EditBox(this.font, this.width / 2 - 109, 22, 218, 20, this.searchBox, TextComponent.EMPTY) {

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				// left click clears text
				// only works for search field as entries inside of vanilla selection lists seem to be unable to handle left clicks properly
				if (this.isVisible() && button == 1) {
					this.setValue("");
				}
				return super.mouseClicked(mouseX, mouseY, button);
			}
		};
		this.searchBox.setResponder(query -> this.list.refreshList(query));
		this.list = new ConfigSelectionList(this, this.minecraft, this.width, this.height, 50, this.height - 36, 36, this.searchBox.getValue());
		this.addWidget(this.searchBox);
		this.addWidget(this.list);
		this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 28, 200, 20, CommonComponents.GUI_DONE, button -> this.onClose()));
		this.setInitialFocus(this.searchBox);
	}

	@Override
	public boolean keyPressed(int i, int j, int k) {
		return super.keyPressed(i, j, k) || this.searchBox.keyPressed(i, j, k);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	public boolean charTyped(char c, int i) {
		return this.searchBox.charTyped(c, i);
	}

	@Override
	public void render(PoseStack poseStack, int i, int j, float f) {
		this.toolTip = null;
		ScreenUtil.renderCustomBackground(this, this.background, 0);
		this.list.render(poseStack, i, j, f);
		this.searchBox.render(poseStack, i, j, f);
		drawCenteredString(poseStack, this.font, this.title, this.width / 2, 7, 16777215);
		super.render(poseStack, i, j, f);
		if (this.toolTip != null) {
			this.renderTooltip(poseStack, this.toolTip, i, j);
		}

	}

	public void setToolTip(List<FormattedCharSequence> list) {
		this.toolTip = list;
	}

	public Component getDisplayName() {
		return this.displayName;
	}

	public ResourceLocation getBackground() {
		return this.background;
	}

	public List<ModConfig> getConfigs() {
		return this.configs;
	}

	public boolean getServerPermissions() {
		return this.serverPermissions;
	}

	public void setServerPermissions() {
		this.serverPermissions = true;
	}
}
