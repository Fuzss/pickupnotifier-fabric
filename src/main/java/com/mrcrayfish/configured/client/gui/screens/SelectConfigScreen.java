package com.mrcrayfish.configured.client.gui.screens;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.gui.components.ConfigSelectionList;
import com.mrcrayfish.configured.client.gui.util.ScreenUtil;
import com.mrcrayfish.configured.client.util.ServerConfigUploader;
import com.mrcrayfish.configured.config.data.IEntryData;
import com.mrcrayfish.configured.network.client.message.C2SAskPermissionsMessage;
import fuzs.puzzleslib.core.ModLoaderEnvironment;
import fuzs.puzzleslib.network.NetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.ForgeConfigs;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Files;
import java.nio.file.Path;
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
	private List<FormattedCharSequence> activeTooltip;
	private EditBox searchBox;
	private ConfigSelectionList list;
	private Button openButton;
	private Button restoreButton;
	private Button fileButton;
	private Button openServerButton;
	private Button restoreServerButton;
	private Button copyServerButton;
	private boolean serverPermissions;

	public SelectConfigScreen(Screen lastScreen, Component displayName, ResourceLocation optionsBackground, Set<ModConfig> configs) {
		super(new TranslatableComponent("configured.gui.select.title", displayName));
		this.lastScreen = lastScreen;
		this.displayName = displayName;
		this.background = optionsBackground;
		this.configs = configs.stream().collect(Collectors.collectingAndThen(Collectors.toMap(Function.identity(), IEntryData::makeValueToDataMap), ImmutableMap::copyOf));
		this.initServerPermissions();
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
		final Button.OnPress onPressOpen = button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				selected.openConfig();
			}
		};
		this.openButton = this.addRenderableWidget(new Button(this.width / 2 - 154, this.height - 52, 150, 20, new TranslatableComponent("configured.gui.select.edit"), onPressOpen));
		this.openServerButton = this.addRenderableWidget(new Button(this.width / 2 - 50 - 104, this.height - 52, 100, 20, new TranslatableComponent("configured.gui.select.server.edit"), onPressOpen));
		final Button.OnPress onPressRestore = button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				Screen confirmScreen = ScreenUtil.makeConfirmationScreen(result -> {
					if (result) {
						final ModConfig config = selected.getConfig();
						this.getValueToDataMap(config).values().forEach(data -> {
							data.resetCurrentValue();
							data.saveConfigValue();
						});
						ServerConfigUploader.saveAndUpload(config);
					}
					this.minecraft.setScreen(this);
				}, new TranslatableComponent("configured.gui.message.restore"), TextComponent.EMPTY, this.background);
				this.minecraft.setScreen(confirmScreen);
			}
		};
		this.restoreButton = this.addRenderableWidget(new Button(this.width / 2 + 4, this.height - 52, 150, 20, new TranslatableComponent("configured.gui.select.restore"), onPressRestore));
		this.restoreServerButton = this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 52, 100, 20, new TranslatableComponent("configured.gui.select.server.restore"), onPressRestore));
		this.copyServerButton = this.addRenderableWidget(new Button(this.width / 2 - 50 + 104, this.height - 52, 100, 20, new TranslatableComponent("configured.gui.select.server.copy"), button -> {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			if (selected != null) {
				Path destination = ModLoaderEnvironment.getGameDir().resolve(ForgeConfigs.DEFAULT_CONFIG_NAME).resolve(selected.getConfig().getFileName());
				this.minecraft.setScreen(ScreenUtil.makeConfirmationScreen(result -> {
					if (result) {
//						this.valueToData.values().forEach(IEntryData::discardCurrentValue);
					}
					this.minecraft.setScreen(this);
				}, new TranslatableComponent("configured.gui.message.copy.title"), Files.exists(destination) ? new TranslatableComponent("configured.gui.message.copy.warning").withStyle(ChatFormatting.RED) : TextComponent.EMPTY, this.background));
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
		this.addRenderableWidget(new ImageButton(14, 14, 19, 23, 0, 0, 0, ConfigScreen.LOGO_TEXTURE, 32, 32, button -> {
			Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, Configured.URL));
			this.handleComponentClicked(style);
		}, (Button button, PoseStack poseStack, int mouseX, int mouseY) -> {
			this.renderTooltip(poseStack, this.font.split(ConfigScreen.INFO_TOOLTIP, 200), mouseX, mouseY);
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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
		this.activeTooltip = null;
		ScreenUtil.renderCustomBackground(this, this.background, 0);
		this.list.render(poseStack, mouseX, mouseY, partialTicks);
		this.searchBox.render(poseStack, mouseX, mouseY, partialTicks);
		drawCenteredString(poseStack, this.font, this.title, this.width / 2, 7, 16777215);
		super.render(poseStack, mouseX, mouseY, partialTicks);
		if (this.activeTooltip != null) {
			this.renderTooltip(poseStack, this.activeTooltip, mouseX, mouseY);
		}
	}

	public void updateButtonStatus(boolean active) {
		if (this.list != null && active) {
			final ConfigSelectionList.ConfigListEntry selected = this.list.getSelected();
			this.updateButtonVisibility(selected.serverConfig());
			this.openButton.active = true;
			this.restoreButton.active = this.restoreServerButton.active = selected.mayResetValue();
			this.fileButton.active = !selected.onMultiplayerServer();
			this.copyServerButton.active = true;
		} else {
			this.updateButtonVisibility(false);
			this.openButton.active = false;
			this.restoreButton.active = this.restoreServerButton.active = false;
			this.fileButton.active = false;
			this.copyServerButton.active = false;
		}
	}

	private void updateButtonVisibility(boolean server) {
		this.openButton.visible = !server;
		this.restoreButton.visible = !server;
		this.openServerButton.visible = server;
		this.restoreServerButton.visible = server;
		this.copyServerButton.visible = server;
	}

	public void setActiveTooltip(List<FormattedCharSequence> list) {
		this.activeTooltip = list;
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

	private void initServerPermissions() {
		// this.minecraft hasn't been set yet
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() != null) {
			if (minecraft.isLocalServer()) {
				this.serverPermissions = true;
			} else {
				NetworkHandler.INSTANCE.sendToServer(new C2SAskPermissionsMessage());
			}
		}
	}

	public boolean getServerPermissions() {
		return this.serverPermissions;
	}

	public void setServerPermissions() {
		this.serverPermissions = true;
	}
}
