package com.mrcrayfish.configured.client.gui.screens;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.gui.components.ConfigSelectionList;
import com.mrcrayfish.configured.client.gui.data.IEntryData;
import com.mrcrayfish.configured.client.gui.util.ScreenUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("ConstantConditions")
@Environment(EnvType.CLIENT)
public class SelectConfigScreen extends Screen {
	private final Screen lastScreen;
	private final ResourceLocation background;
	private final Component displayName;
	private final Map<ModConfig, Map<Object, IEntryData>> configs;
	private List<FormattedCharSequence> toolTip;
	private EditBox searchBox;
	private ConfigSelectionList list;
	private Button openButton;
	private Button restoreButton;
	private Button fileButton;
	private boolean serverPermissions;

	public SelectConfigScreen(Screen lastScreen, Component displayName, ResourceLocation optionsBackground, Set<ModConfig> configs) {
		super(new TranslatableComponent("configured.gui.select.title", displayName));
		this.lastScreen = lastScreen;
		this.displayName = displayName;
		this.background = optionsBackground;
		this.configs = configs.stream().collect(Collectors.collectingAndThen(Collectors.toMap(Function.identity(), config -> {
			return this.invalidData(config) ? Maps.<Object, IEntryData>newHashMap() : ConfigScreen.makeValueToDataMap((ForgeConfigSpec) config.getSpec());
		}), ImmutableMap::copyOf));
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
				if (this.isVisible() && button == 1) {
					this.setValue("");
				}
				return super.mouseClicked(mouseX, mouseY, button);
			}
		};
		this.searchBox.setResponder(query -> this.list.refreshList(query));
		this.addWidget(this.searchBox);
		this.addRenderableWidget(new Button(this.width / 2 + 4, this.height - 28, 150, 20, CommonComponents.GUI_DONE, button -> this.onClose()));
		this.openButton = this.addRenderableWidget(new Button(this.width / 2 - 154, this.height - 52, 150, 20, new TranslatableComponent("configured.gui.select.edit"), button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				selected.openConfig();
			}
		}));
		this.restoreButton = this.addRenderableWidget(new Button(this.width / 2 + 4, this.height - 52, 150, 20, new TranslatableComponent("configured.gui.select.restore"), button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				Screen confirmScreen = ScreenUtil.makeConfirmationScreen(result -> {
					if (result) {
						final ModConfig config = selected.getConfig();
						this.getValueToDataMap(config).values().forEach(data -> {
							data.resetCurrentValue();
							data.saveConfigValue();
						});
						((ForgeConfigSpec) config.getSpec()).save();
					}
					this.minecraft.setScreen(this);
				}, new TranslatableComponent("configured.gui.message.restore"), TextComponent.EMPTY, this.background);
				this.minecraft.setScreen(confirmScreen);
			}
		}));
		this.fileButton = this.addRenderableWidget(new Button(this.width / 2 - 154, this.height - 28, 150, 20, new TranslatableComponent("configured.gui.select.open"), button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				final Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, selected.getConfig().getFullPath().toAbsolutePath().toString()));
				this.handleComponentClicked(style);
			}
		}));
		this.updateButtonStatus(false);
		this.list = new ConfigSelectionList(this, this.minecraft, this.width, this.height, 50, this.height - 60, 36, this.searchBox.getValue());
		this.addWidget(this.list);
		final List<FormattedCharSequence> tooltip = this.font.split(new TranslatableComponent("configured.gui.info"), 200);
		this.addRenderableWidget(new ImageButton(23, 14, 19, 23, 0, 0, 0, ConfigScreen.LOGO_TEXTURE, 32, 32, button -> {
			Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Configured.URL));
			this.handleComponentClicked(style);
		}, (Button button, PoseStack poseStack, int mouseX, int mouseY) -> {
			this.renderTooltip(poseStack, tooltip, mouseX, mouseY);
		}, TextComponent.EMPTY));
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

	public void updateButtonStatus(boolean active) {
		this.openButton.active = active;
		this.fileButton.active = active;
		if (this.list != null) {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			active &= selected == null || selected.mayResetValue();
		}
		this.restoreButton.active = active;
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

	public Set<ModConfig> getConfigs() {
		return this.configs.keySet();
	}

	public Map<Object, IEntryData> getValueToDataMap(ModConfig config) {
		return this.configs.get(config);
	}

	public boolean getServerPermissions() {
		return this.serverPermissions;
	}

	public void setServerPermissions() {
		this.serverPermissions = true;
	}

	public boolean invalidData(ModConfig config) {
		return config.getConfigData() == null || !(config.getSpec() instanceof ForgeConfigSpec);
	}
}
