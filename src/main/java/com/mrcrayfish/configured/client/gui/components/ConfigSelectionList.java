package com.mrcrayfish.configured.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.gui.screens.ConfigScreen;
import com.mrcrayfish.configured.client.gui.screens.SelectConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Comparator;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class ConfigSelectionList extends CustomBackgroundObjectSelectionList<ConfigSelectionList.ConfigListEntry> {
	private static final ResourceLocation ICON_LOCATION = new ResourceLocation(Configured.MODID, "textures/misc/pack.png");
	private static final ResourceLocation ICON_DISABLED_LOCATION = new ResourceLocation("textures/misc/unknown_server.png");
	private static final ResourceLocation ICON_OVERLAY_LOCATION = new ResourceLocation("textures/gui/world_selection.png");
	private static final Component NO_DATA_TOOLTIP = new TranslatableComponent("configured.gui.select.no_data").withStyle(ChatFormatting.RED);
	private static final Component NO_PERMISSIONS_TOOLTIP = new TranslatableComponent("configured.gui.select.no_permissions").withStyle(ChatFormatting.GOLD);

	private final SelectConfigScreen screen;

	public ConfigSelectionList(SelectConfigScreen selectConfigScreen, Minecraft minecraft, int i, int j, int k, int l, int m, String query) {
		super(minecraft, selectConfigScreen.getBackground(), i, j, k, l, m);
		this.screen = selectConfigScreen;
		this.refreshList(query);
	}

	public void refreshList(String query) {
		this.clearEntries();
		final String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
		this.screen.getConfigs().stream()
				.filter(config -> matchesConfigSearch(config, lowerCaseQuery))
				.sorted(Comparator.comparing(config -> config.getType().extension()))
				.forEach(config -> this.addEntry(new ConfigListEntry(this.screen, config)));
	}

	private static boolean matchesConfigSearch(ModConfig config, String query) {
		if (config.getFileName().toLowerCase(Locale.ROOT).contains(query)) {
			return true;
		} else {
			return config.getType().extension().contains(query);
		}
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
	protected boolean isFocused() {
		return this.screen.getFocused() == this;
	}

	@Override
	protected void moveSelection(SelectionDirection selectionDirection) {
		this.moveSelection(selectionDirection, (configListEntry) -> !configListEntry.isDisabled());
	}

	@Environment(EnvType.CLIENT)
	public class ConfigListEntry extends Entry<ConfigListEntry> {
		private final Minecraft minecraft;
		private final SelectConfigScreen screen;
		private final Component nameComponent;
		private final Component fileNameComponent;
		private final Component typeComponent;
		final ModConfig config;
		private long lastClickTime;

		public ConfigListEntry(SelectConfigScreen selectConfigScreen, ModConfig config) {
			this.screen = selectConfigScreen;
			this.config = config;
			this.minecraft = Minecraft.getInstance();
			this.nameComponent = this.getNameComponent();
			this.fileNameComponent = new TextComponent(config.getFileName());
			String extension = this.config.getType().extension();
			this.typeComponent = new TranslatableComponent("configured.gui.type.title", StringUtils.capitalize(extension));
		}

		@Override
		public Component getNarration() {
			Component component = this.getNameComponent();
			if (this.invalidData()) {
				component = CommonComponents.joinForNarration(component, ConfigSelectionList.NO_DATA_TOOLTIP);
			} else if (this.noPermissions()) {
				component = CommonComponents.joinForNarration(component, ConfigSelectionList.NO_PERMISSIONS_TOOLTIP);
			}

			return new TranslatableComponent("narrator.select", component);
		}

		@Override
		public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
			Font font = this.minecraft.font;
			font.draw(poseStack, this.nameComponent, (float)(entryLeft + 32 + 3), (float)(entryTop + 1), 16777215);
			font.draw(poseStack, this.fileNameComponent, (float)(entryLeft + 32 + 3), (float)(entryTop + 9 + 3), 8421504);
			font.draw(poseStack, this.typeComponent, (float)(entryLeft + 32 + 3), (float)(entryTop + 9 + 9 + 3), 8421504);
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.setShaderTexture(0, this.isDisabled() ? ConfigSelectionList.ICON_DISABLED_LOCATION : ConfigSelectionList.ICON_LOCATION);
			RenderSystem.enableBlend();
			GuiComponent.blit(poseStack, entryLeft, entryTop, 0.0F, 0.0F, 32, 32, 32, 32);
			RenderSystem.disableBlend();
			if (this.minecraft.options.touchscreen || hovered) {
				RenderSystem.setShaderTexture(0, ConfigSelectionList.ICON_OVERLAY_LOCATION);
				GuiComponent.fill(poseStack, entryLeft, entryTop, entryLeft + 32, entryTop + 32, -1601138544);
				RenderSystem.setShader(GameRenderer::getPositionTexShader);
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
				boolean leftHovered = mouseX - entryLeft < 32;
				int textureY = leftHovered ? 32 : 0;
				if (this.invalidData()) {
					GuiComponent.blit(poseStack, entryLeft, entryTop, 96.0F, (float)textureY, 32, 32, 256, 256);
					if (leftHovered) {
						this.screen.setToolTip(this.minecraft.font.split(ConfigSelectionList.NO_DATA_TOOLTIP, 175));
					}
				} else if (this.noPermissions()) {
					GuiComponent.blit(poseStack, entryLeft, entryTop, 64.0F, textureY, 32, 32, 256, 256);
					if (leftHovered) {
						this.screen.setToolTip(this.minecraft.font.split(ConfigSelectionList.NO_PERMISSIONS_TOOLTIP, 175));
					}
				} else {
					GuiComponent.blit(poseStack, entryLeft, entryTop, 0.0F, (float)textureY, 32, 32, 256, 256);
				}
			}

		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.isDisabled()) {
				return true;
			} else {
				ConfigSelectionList.this.setSelected(this);
				if (mouseX - (double) ConfigSelectionList.this.getRowLeft() <= 32.0D) {
					this.openConfig();
					return true;
				} else if (Util.getMillis() - this.lastClickTime < 250L) {
					this.openConfig();
					return true;
				} else {
					this.lastClickTime = Util.getMillis();
					return false;
				}
			}
		}

		private void openConfig() {
			final ConfigScreen configScreen = ConfigScreen.create(this.screen, this.screen.getDisplayName(), this.screen.getBackground(), (ForgeConfigSpec) this.config.getSpec());
			this.minecraft.setScreen(configScreen);
		}

		private Component getNameComponent() {
			String fullName = this.config.getFileName();
			int start = fullName.lastIndexOf(File.separator) + 1;
			int end = fullName.lastIndexOf(".");
			String fileName = fullName.substring(start, end < start ? fullName.length() : end);
			return new TextComponent(fileName);
		}

		private boolean noPermissions() {
			return !this.screen.getServerPermissions() && this.config.getType() == ModConfig.Type.SERVER;
		}

		private boolean invalidData() {
			return this.config.getConfigData() == null || !(this.config.getSpec() instanceof ForgeConfigSpec);
		}

		private boolean isDisabled() {
			return this.invalidData() || this.noPermissions();
		}
	}
}
