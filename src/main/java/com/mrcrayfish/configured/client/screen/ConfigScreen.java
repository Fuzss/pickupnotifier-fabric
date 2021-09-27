package com.mrcrayfish.configured.client.screen;

import com.electronwill.nightconfig.core.AbstractConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
import com.mrcrayfish.configured.client.util.ScreenUtil;
import fuzs.pickupnotifier.PickUpNotifier;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Author: MrCrayfish
 */
@Environment(EnvType.CLIENT)
public abstract class ConfigScreen extends Screen
{
    public static final ResourceLocation LOGO_TEXTURE = new ResourceLocation(Configured.MODID, "textures/gui/logo.png");
    public static final Comparator<Entry> COMPARATOR = (o1, o2) -> {
        if(o1 instanceof SubMenu && o2 instanceof SubMenu)
        {
            return o1.getLabel().compareTo(o2.getLabel());
        }
        if(!(o1 instanceof SubMenu) && o2 instanceof SubMenu)
        {
            return 1;
        }
        if(o1 instanceof SubMenu)
        {
            return -1;
        }
        return o1.getLabel().compareTo(o2.getLabel());
    };

    final Screen lastScreen;
    private final String displayName;
    private final ResourceLocation background;
    private ConfigList list;
    List<Entry> screenEntries;
    final List<Entry> allEntries;
    private ConfigEditBox activeTextField;
    private ConfigEditBox searchTextField;
    Button restoreDefaultsButton;
    private List<? extends FormattedCharSequence> activeTooltip;

    private ConfigScreen(Screen lastScreen, String displayName, ResourceLocation background, List<Entry> allEntries)
    {
        super(new TextComponent(displayName));
        this.lastScreen = lastScreen;
        this.displayName = displayName;
        this.background = background;
        this.allEntries = allEntries;
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }

    private static class Sub extends ConfigScreen {

        private final List<Pair<ForgeConfigSpec, UnmodifiableConfig>> entry;

        public Sub(Screen parent, String displayName, Pair<ForgeConfigSpec, UnmodifiableConfig> entry, ResourceLocation background, List<Entry> allEntries) {
            super(parent, displayName, background, allEntries);
            this.entry = Collections.singletonList(entry);
            this.screenEntries = this.constructEntries();
        }

        @Override
        List<Entry> constructEntries()
        {
            List<Entry> clientEntries = new ArrayList<>();
            this.entry.forEach(pair -> this.createEntriesFromConfig(pair.getLeft(), pair.getRight(), entry -> {
                clientEntries.add(entry);
                this.allEntries.add(entry);
            }));
            clientEntries.sort(COMPARATOR);
            return ImmutableList.copyOf(clientEntries);
        }

        @Override
        protected void init() {
            super.init();
            this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 29, 200, 20, CommonComponents.GUI_BACK, (button) -> {
                this.minecraft.setScreen(this.lastScreen);
            }));
        }

        @Override
        List<Entry> getSearchEntries() {
            return this.screenEntries;
        }
    }

    public static class Main extends ConfigScreen {

        private final EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> configFileEntries;

        public Main(Screen parent, String displayName, EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> configFileEntries, ResourceLocation background) {
            super(parent, displayName, background, Lists.newArrayList());
            this.configFileEntries = configFileEntries;
            this.screenEntries = this.constructEntries();
        }

        @Override
        List<Entry> constructEntries()
        {
            List<Entry> entries = new ArrayList<>();
            this.configFileEntries.forEach((key, value) -> {

                final String typeExtension = key.extension();
                entries.add(new TitleEntry(new TranslatableComponent("configured.gui.config_title", StringUtils.capitalize(typeExtension)), new TranslatableComponent(String.format("configured.gui.%s_config", typeExtension))));
                List<Entry> clientEntries = new ArrayList<>();
                value.forEach(pair -> this.createEntriesFromConfig(pair.getLeft(), pair.getRight(), entry -> {
                    clientEntries.add(entry);
                    this.allEntries.add(entry);
                }));
                clientEntries.sort(COMPARATOR);
                entries.addAll(clientEntries);
            });
            return ImmutableList.copyOf(entries);
        }

        @Override
        protected void init() {
            super.init();
            this.addRenderableWidget(new Button(this.width / 2 - 155 + 160, this.height - 29, 150, 20, CommonComponents.GUI_DONE, button -> {
                this.allEntries.forEach(Entry::saveConfigValue);
                this.configFileEntries.values().stream().flatMap(Collection::stream).map(Pair::getLeft).forEach(ForgeConfigSpec::save);
                this.minecraft.setScreen(this.lastScreen);
            }));
            this.restoreDefaultsButton = this.addRenderableWidget(new Button(this.width / 2 - 155, this.height - 29, 150, 20, new TranslatableComponent("configured.gui.restore_defaults"), (button) -> {
                Screen confirmScreen = this.makeConfirmationScreen();
                this.minecraft.setScreen(confirmScreen);
            }));
            // Call during init to avoid the button flashing active
            this.updateRestoreDefaultButton();
        }

        @Override
        List<Entry> getSearchEntries() {
            return this.allEntries;
        }
    }

    /**
     * Gathers the entries for each config spec to be later added to the option list
     */
    abstract List<Entry> constructEntries();

    /**
     * Scans the given unmodifiable config and creates an entry for each scanned
     * config value based on it's type.
     *  @param spec    the spec of config
     * @param values  the values to scan
     * @param addTo   consumer to add the entries to
     */
    @SuppressWarnings("unchecked")
    void createEntriesFromConfig(ForgeConfigSpec spec, UnmodifiableConfig values, Consumer<Entry> addTo)
    {
        values.valueMap().forEach((key, value) ->
        {
            if(value instanceof AbstractConfig)
            {
                addTo.accept(new SubMenu(key, spec, (AbstractConfig) value));
            }
            else if(value instanceof ForgeConfigSpec.ConfigValue<?> configValue)
            {
                ForgeConfigSpec.ValueSpec valueSpec = spec.getRaw(configValue.getPath());
                Object value1 = configValue.get();
                if(value1 instanceof Boolean)
                {
                    addTo.accept(new BooleanEntry((ForgeConfigSpec.ConfigValue<Boolean>) configValue, valueSpec));
                }
                else if(value1 instanceof Integer)
                {
                    addTo.accept(new IntegerEntry((ForgeConfigSpec.ConfigValue<Integer>) configValue, valueSpec));
                }
                else if(value1 instanceof Double)
                {
                    addTo.accept(new DoubleEntry((ForgeConfigSpec.ConfigValue<Double>) configValue, valueSpec));
                }
                else if(value1 instanceof Long)
                {
                    addTo.accept(new LongEntry((ForgeConfigSpec.ConfigValue<Long>) configValue, valueSpec));
                }
                else if(value1 instanceof Enum)
                {
                    addTo.accept(new EnumEntry((ForgeConfigSpec.ConfigValue<Enum<?>>) configValue, valueSpec));
                }
                else if(value1 instanceof String)
                {
                    addTo.accept(new StringEntry((ForgeConfigSpec.ConfigValue<String>) configValue, valueSpec));
                }
                else if(value1 instanceof List<?>)
                {
                    addTo.accept(new ListStringEntry((ForgeConfigSpec.ConfigValue<List<?>>) configValue, valueSpec));
                }
                else
                {
                    PickUpNotifier.LOGGER.info("Unsupported config value: " + configValue.getPath());
                }
            }
        });
    }

    @Override
    protected void init()
    {
        super.init();
        this.list = new ConfigList(this.screenEntries);
        this.addWidget(this.list);

        this.searchTextField = new ConfigEditBox(this.font, this.width / 2 - 110, 22, 220, 20, new TranslatableComponent("configured.gui.search"));
        this.searchTextField.setResponder(query -> {
            if(!query.isEmpty())
            {
                this.list.replaceEntries(this.getSearchEntries().stream().filter(entry -> entry.containsQuery(query)).collect(Collectors.toList()));
            }
            else
            {
                this.list.replaceEntries(this.screenEntries);
            }
        });
        this.addWidget(this.searchTextField);
    }

    abstract List<Entry> getSearchEntries();

    @NotNull
    ConfirmScreen makeConfirmationScreen() {
        return new ConfirmScreen(result -> {
            if (result) {// Resets all config values
                ConfigScreen.this.allEntries.forEach(Entry::resetConfigValue);
                // Updates the current entries to process UI changes
                ConfigScreen.this.screenEntries.stream().filter(entry -> entry instanceof ConfigEntry).forEach(entry -> {
                    ((ConfigEntry<?>) entry).resetConfigValue();
                });
            }
            ConfigScreen.this.minecraft.setScreen(ConfigScreen.this);
        }, new TranslatableComponent("configured.gui.restore_message"), TextComponent.EMPTY) {

            @Override
            public void renderDirtBackground(int vOffset)
            {
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder builder = tesselator.getBuilder();
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, ConfigScreen.this.background);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                float size = 32.0F;
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                builder.vertex(0.0D, this.height, 0.0D).uv(0.0F, this.height / size + vOffset).color(64, 64, 64, 255).endVertex();
                builder.vertex(this.width, this.height, 0.0D).uv(this.width / size, this.height / size + vOffset).color(64, 64, 64, 255).endVertex();
                builder.vertex(this.width, 0.0D, 0.0D).uv(this.width / size, vOffset).color(64, 64, 64, 255).endVertex();
                builder.vertex(0.0D, 0.0D, 0.0D).uv(0.0F, vOffset).color(64, 64, 64, 255).endVertex();
                tesselator.end();
            }

        };
    }

    @Override
    public void tick()
    {
        this.updateRestoreDefaultButton();
    }

    /**
     * Updates the active state of the restore default button. It will only be active if values are
     * different from their default.
     */
    void updateRestoreDefaultButton()
    {
        if(this.restoreDefaultsButton != null)
        {
            this.restoreDefaultsButton.active = this.allEntries.stream().anyMatch(Entry::mayResetValue);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
    {
        this.activeTooltip = null;
        this.renderBackground(poseStack);
        this.list.render(poseStack, mouseX, mouseY, partialTicks);
        this.searchTextField.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 7, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, LOGO_TEXTURE);
        blit(poseStack, 10, 13, this.getBlitOffset(), 0, 0, 23, 23, 32, 32);
        if(ScreenUtil.isMouseWithin(10, 13, 23, 23, mouseX, mouseY))
        {
            this.setActiveTooltip(this.minecraft.font.split(new TranslatableComponent("configured.gui.info"), 200));
        }
        if(this.activeTooltip != null)
        {
            this.renderTooltip(poseStack, this.activeTooltip, mouseX, mouseY);
        }
        this.children().forEach(o ->
        {
            if(o instanceof Button.OnTooltip)
            {
                ((Button.OnTooltip) o).onTooltip((Button) o, poseStack, mouseX, mouseY);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(ScreenUtil.isMouseWithin(10, 13, 23, 23, (int) mouseX, (int) mouseY))
        {
            Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/configured"));
            this.handleComponentClicked(style);
            return true;
        }
//        if (this.activeTextField != null) {
//            if (this.activeTextField.mouseClicked(mouseX, mouseY, button)) {
//                return true;
//            } else {
//                this.activeTextField.setFocus(false);
//            }
//        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Sets the tool tip to render. Must be actively called in the render method as
     * the tooltip is reset every draw call.
     *
     * @param activeTooltip a tooltip list to show
     */
    public void setActiveTooltip(@Nullable List<? extends FormattedCharSequence> activeTooltip)
    {
        this.activeTooltip = activeTooltip;
    }

    abstract class Entry extends ContainerObjectSelectionList.Entry<ConfigScreen.Entry>
    {
        protected String label;
        @Nullable
        protected List<? extends FormattedCharSequence> tooltip;

        public Entry(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return this.label;
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            if(ConfigScreen.this.list != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.list.getRowLeft() + ConfigScreen.this.list.getRowWidth() - 67)
            {
                ConfigScreen.this.setActiveTooltip(this.tooltip);
            }
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return ImmutableList.of(new NarratableEntry()
            {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority()
                {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output)
                {
                    output.add(NarratedElementType.TITLE, ConfigScreen.Entry.this.label);
                }
            });
        }

        public boolean containsQuery(String query) {
            return this.label.toLowerCase(Locale.ROOT).contains(query);
        }

        public boolean mayResetValue() {
            return false;
        }

        public void resetConfigValue() {

        }

        public void saveConfigValue() {

        }
    }

    public class TitleEntry extends Entry
    {
        public TitleEntry(Component title, Component description)
        {
            super(title.getString());
            this.tooltip = this.makeTooltip(description);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return ImmutableList.of();
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int p_230432_7_, int p_230432_8_, boolean p_230432_9_, float p_230432_10_)
        {
            super.render(poseStack, x, top, left, width, p_230432_6_, p_230432_7_, p_230432_8_, p_230432_9_, p_230432_10_);
            Component title = new TextComponent(this.label).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.YELLOW);
            Screen.drawCenteredString(poseStack, ConfigScreen.this.minecraft.font, title, left + width / 2, top + 5, 16777215);
        }

        private List<FormattedCharSequence> makeTooltip(Component description)
        {
            Font font = ConfigScreen.this.minecraft.font;
            List<FormattedText> lines = font.getSplitter().splitLines(description, 200, Style.EMPTY);
            return Language.getInstance().getVisualOrder(lines);
        }

        @Override
        public boolean containsQuery(String query) {
            return false;
        }
    }

    public class SubMenu extends Entry
    {
        private final Button button;

        public SubMenu(String label, ForgeConfigSpec spec, AbstractConfig values)
        {
            super(createLabel(label));
            String newTitle = ConfigScreen.this.displayName + " > " + this.getLabel();
            final Sub subScreen = new Sub(ConfigScreen.this, newTitle, Pair.of(spec, values), ConfigScreen.this.background, ConfigScreen.this.allEntries);
            this.button = new Button(10, 5, 44, 20, new TextComponent(this.getLabel()).withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.WHITE), onPress -> ConfigScreen.this.minecraft.setScreen(subScreen));
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return ImmutableList.of(this.button);
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int height, int mouseX, int mouseY, boolean selected, float partialTicks)
        {
            super.render(poseStack, x, top, left, width, height, mouseX, mouseY, selected, partialTicks);
            this.button.x = left - 1;
            this.button.y = top;
            this.button.setWidth(width);
            this.button.render(poseStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return ImmutableList.of(new NarratableEntry()
            {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority()
                {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output)
                {
                    output.add(NarratedElementType.TITLE, SubMenu.this.label);
                }
            }, SubMenu.this.button);
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class ConfigEntry<T> extends Entry
    {
        protected final List<AbstractWidget> children = Lists.newArrayList();
        protected final ForgeConfigSpec.ConfigValue<T> configValue;
        private final ForgeConfigSpec.ValueSpec valueSpec;
        private final Button resetButton;
        T currentValue;

        public ConfigEntry(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(createLabelFromConfig(configValue, valueSpec));
            this.configValue = configValue;
            this.currentValue = configValue.get();
            this.valueSpec = valueSpec;
            if(valueSpec.getComment() != null)
            {
                this.tooltip = this.createToolTip(configValue, valueSpec);
            }
            Button.OnTooltip tooltip = (button, matrixStack, mouseX, mouseY) ->
            {
                if(button.active && button.isHovered())
                {
                    ConfigScreen.this.renderTooltip(matrixStack, ConfigScreen.this.minecraft.font.split(new TranslatableComponent("configured.gui.reset"), Math.max(ConfigScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                }
            };
            this.resetButton = new IconButton(0, 0, 20, 20, 0, 0, tooltip, onPress -> this.resetConfigValue());
            this.children.add(this.resetButton);
        }

        @Override
        public boolean mayResetValue() {
            return !this.currentValue.equals(this.valueSpec.getDefault());
        }

        @Override
        public void resetConfigValue() {
            this.currentValue = (T) this.valueSpec.getDefault();
        }

        @Override
        public void saveConfigValue() {
            this.configValue.set(this.currentValue);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return this.children;
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(poseStack, x, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.resetButton.active = this.mayResetValue();

            Component title = new TextComponent(this.label);
            if(ConfigScreen.this.minecraft.font.width(title) > width - 75)
            {
                String trimmed = ConfigScreen.this.minecraft.font.substrByWidth(title, width - 75).getString() + "...";
                ConfigScreen.this.minecraft.font.drawShadow(poseStack, new TextComponent(trimmed), left, top + 6, 0xFFFFFF);
            }
            else
            {
                ConfigScreen.this.minecraft.font.drawShadow(poseStack, title, left, top + 6, 0xFFFFFF);
            }

            this.resetButton.x = left + width - 21;
            this.resetButton.y = top;
            this.resetButton.render(poseStack, mouseX, mouseY, partialTicks);
        }

        private List<FormattedCharSequence> createToolTip(ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec.ValueSpec spec)
        {
            Font font = ConfigScreen.this.minecraft.font;
            List<FormattedText> lines = font.getSplitter().splitLines(new TextComponent(spec.getComment()), 200, Style.EMPTY);
            String name = lastValue(value.getPath(), "");
            lines.add(0, new TextComponent(name).withStyle(ChatFormatting.YELLOW));
            int rangeIndex = -1;
            for(int i = 0; i < lines.size(); i++)
            {
                String text = lines.get(i).getString();
                if(text.startsWith("Range: ") || text.startsWith("Allowed Values: "))
                {
                    rangeIndex = i;
                    break;
                }
            }
            if(rangeIndex != -1)
            {
                for(int i = rangeIndex; i < lines.size(); i++)
                {
                    lines.set(i, new TextComponent(lines.get(i).getString()).withStyle(ChatFormatting.GRAY));
                }
            }
            return Language.getInstance().getVisualOrder(lines);
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            ImmutableList.Builder<NarratableEntry> builder = ImmutableList.builder();
            builder.add(new NarratableEntry()
            {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority()
                {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output)
                {
                    String comment = ConfigEntry.this.valueSpec.getComment();
                    if(comment != null)
                    {
                        output.add(NarratedElementType.TITLE, ConfigEntry.this.label + ", " + comment);
                    }
                    else
                    {
                        output.add(NarratedElementType.TITLE, ConfigEntry.this.label);
                    }
                }
            });
            builder.addAll(ConfigEntry.this.children);
            return builder.build();
        }
    }

    @Environment(EnvType.CLIENT)
    public class ConfigList extends ContainerObjectSelectionList<Entry>
    {
        public ConfigList(List<ConfigScreen.Entry> entries)
        {
            super(ConfigScreen.this.minecraft, ConfigScreen.this.width, ConfigScreen.this.height, 50, ConfigScreen.this.height - 36, 24);
            entries.forEach(this::addEntry);
        }

        @Override
        public int getRowLeft()
        {
            return super.getRowLeft();
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
        public void replaceEntries(Collection<ConfigScreen.Entry> entries)
        {
            super.replaceEntries(entries);
        }

        private void renderToolTips(PoseStack poseStack, int mouseX, int mouseY)
        {
            if(ConfigScreen.this.list != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.list.getRowLeft() + ConfigScreen.this.list.getRowWidth() - 67)
            {
                ConfigScreen.Entry entry = this.getEntryAtPosition(mouseX, mouseY);
                if(entry != null)
                {
                    ConfigScreen.this.setActiveTooltip(entry.tooltip);
                }
            }
            this.children().forEach(entry ->
            {
                entry.children().forEach(o ->
                {
                    if(o instanceof AbstractSliderButton)
                    {
                        ((AbstractSliderButton) o).renderToolTip(poseStack, mouseX, mouseY);
                    }
                });
            });
        }

        /**
         * Literally just a copy of the original since the background can't be changed
         *
         * @param poseStack  the current matrix stack
         * @param mouseX       the current mouse x position
         * @param mouseY       the current mouse y position
         * @param partialTicks the partial ticks
         */
        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
        {
            this.renderBackground(poseStack);
            int scrollBarStart = this.getScrollbarPosition();
            int scrollBarEnd = scrollBarStart + 6;

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, ConfigScreen.this.background);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(this.x0, this.y1, 0.0D).uv(this.x0 / 32.0F, (this.y1 + (int) this.getScrollAmount()) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.vertex(this.x1, this.y1, 0.0D).uv(this.x1 / 32.0F, (this.y1 + (int) this.getScrollAmount()) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.vertex(this.x1, this.y0, 0.0D).uv(this.x1 / 32.0F, (this.y0 + (int) this.getScrollAmount()) / 32.0F).color(32, 32, 32, 255).endVertex();
            buffer.vertex(this.x0, this.y0, 0.0D).uv(this.x0 / 32.0F, (this.y0 + (int) this.getScrollAmount()) / 32.0F).color(32, 32, 32, 255).endVertex();
            tesselator.end();

            int rowLeft = this.getRowLeft();
            int scrollOffset = this.y0 + 4 - (int) this.getScrollAmount();
            this.renderList(poseStack, rowLeft, scrollOffset, mouseX, mouseY, partialTicks);

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, ConfigScreen.this.background);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(519);

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(this.x0, this.y0, -100.0D).uv(0.0F, this.y0 / 32.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex((this.x0 + this.width), this.y0, -100.0D).uv(this.width / 32.0F, this.y0 / 32.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex((this.x0 + this.width), 0.0D, -100.0D).uv(this.width / 32.0F, 0.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex(this.x0, 0.0D, -100.0D).uv(0.0F, 0.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex(this.x0, this.height, -100.0D).uv(0.0F, this.height / 32.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex((this.x0 + this.width), this.height, -100.0D).uv(this.width / 32.0F, this.height / 32.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex((this.x0 + this.width), this.y1, -100.0D).uv(this.width / 32.0F, this.y1 / 32.0F).color(64, 64, 64, 255).endVertex();
            buffer.vertex(this.x0, this.y1, -100.0D).uv(0.0F, this.y1 / 32.0F).color(64, 64, 64, 255).endVertex();
            tesselator.end();

            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            RenderSystem.disableTexture();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(this.x0, this.y0 + 4, 0.0D).uv(0.0F, 1.0F).color(0, 0, 0, 0).endVertex();
            buffer.vertex(this.x1, this.y0 + 4, 0.0D).uv(1.0F, 1.0F).color(0, 0, 0, 0).endVertex();
            buffer.vertex(this.x1, this.y0, 0.0D).uv(1.0F, 0.0F).color(0, 0, 0, 255).endVertex();
            buffer.vertex(this.x0, this.y0, 0.0D).uv(0.0F, 0.0F).color(0, 0, 0, 255).endVertex();
            buffer.vertex(this.x0, this.y1, 0.0D).uv(0.0F, 1.0F).color(0, 0, 0, 255).endVertex();
            buffer.vertex(this.x1, this.y1, 0.0D).uv(1.0F, 1.0F).color(0, 0, 0, 255).endVertex();
            buffer.vertex(this.x1, this.y1 - 4, 0.0D).uv(1.0F, 0.0F).color(0, 0, 0, 0).endVertex();
            buffer.vertex(this.x0, this.y1 - 4, 0.0D).uv(0.0F, 0.0F).color(0, 0, 0, 0).endVertex();
            tesselator.end();

            int maxScroll = Math.max(0, this.getMaxPosition() - (this.y1 - this.y0 - 4));
            if(maxScroll > 0)
            {
                RenderSystem.disableTexture();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                int scrollBarStartY = (int) ((float) ((this.y1 - this.y0) * (this.y1 - this.y0)) / (float) this.getMaxPosition());
                scrollBarStartY = Mth.clamp(scrollBarStartY, 32, this.y1 - this.y0 - 8);
                int scrollBarEndY = (int) this.getScrollAmount() * (this.y1 - this.y0 - scrollBarStartY) / maxScroll + this.y0;
                if(scrollBarEndY < this.y0)
                {
                    scrollBarEndY = this.y0;
                }

                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                buffer.vertex(scrollBarStart, this.y1, 0.0D).uv(0.0F, 1.0F).color(0, 0, 0, 255).endVertex();
                buffer.vertex(scrollBarEnd, this.y1, 0.0D).uv(1.0F, 1.0F).color(0, 0, 0, 255).endVertex();
                buffer.vertex(scrollBarEnd, this.y0, 0.0D).uv(1.0F, 0.0F).color(0, 0, 0, 255).endVertex();
                buffer.vertex(scrollBarStart, this.y0, 0.0D).uv(0.0F, 0.0F).color(0, 0, 0, 255).endVertex();
                buffer.vertex(scrollBarStart, scrollBarEndY + scrollBarStartY, 0.0D).uv(0.0F, 1.0F).color(128, 128, 128, 255).endVertex();
                buffer.vertex(scrollBarEnd, scrollBarEndY + scrollBarStartY, 0.0D).uv(1.0F, 1.0F).color(128, 128, 128, 255).endVertex();
                buffer.vertex(scrollBarEnd, scrollBarEndY, 0.0D).uv(1.0F, 0.0F).color(128, 128, 128, 255).endVertex();
                buffer.vertex(scrollBarStart, scrollBarEndY, 0.0D).uv(0.0F, 0.0F).color(128, 128, 128, 255).endVertex();
                buffer.vertex(scrollBarStart, scrollBarEndY + scrollBarStartY - 1, 0.0D).uv(0.0F, 1.0F).color(192, 192, 192, 255).endVertex();
                buffer.vertex(scrollBarEnd - 1, scrollBarEndY + scrollBarStartY - 1, 0.0D).uv(1.0F, 1.0F).color(192, 192, 192, 255).endVertex();
                buffer.vertex(scrollBarEnd - 1, scrollBarEndY, 0.0D).uv(1.0F, 0.0F).color(192, 192, 192, 255).endVertex();
                buffer.vertex(scrollBarStart, scrollBarEndY, 0.0D).uv(0.0F, 0.0F).color(192, 192, 192, 255).endVertex();
                tesselator.end();
            }

            this.renderDecorations(poseStack, mouseX, mouseY);

            RenderSystem.enableTexture();
            RenderSystem.disableBlend();

            this.renderToolTips(poseStack, mouseX, mouseY);
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class NumberEntry<T> extends ConfigEntry<T>
    {
        private final ConfigEditBox textField;

        public NumberEntry(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec, Function<String, Number> parser)
        {
            super(configValue, valueSpec);
            this.textField = new ConfigEditBox(ConfigScreen.this.font, 0, 0, 42, 18, TextComponent.EMPTY);
            this.textField.setValue(this.currentValue.toString());
            this.textField.setResponder((s) -> {
                try
                {
                    Number number = parser.apply(s);
                    if(valueSpec.test(number))
                    {
                        this.textField.setTextColor(14737632);
                        this.currentValue = (T) number;
                    }
                    else
                    {
                        this.textField.setTextColor(16711680);
                    }
                }
                catch(Exception ignored)
                {
                    this.textField.setTextColor(16711680);
                }
            });
            this.children.add(this.textField);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.textField.x = left + width - 66;
            this.textField.y = top + 1;
            this.textField.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public void resetConfigValue()
        {
            super.resetConfigValue();
            this.textField.setValue(this.currentValue.toString());
        }
    }

    @Environment(EnvType.CLIENT)
    public class IntegerEntry extends NumberEntry<Integer>
    {
        public IntegerEntry(ForgeConfigSpec.ConfigValue<Integer> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec, Integer::parseInt);
        }
    }

    @Environment(EnvType.CLIENT)
    public class DoubleEntry extends NumberEntry<Double>
    {
        public DoubleEntry(ForgeConfigSpec.ConfigValue<Double> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec, Double::parseDouble);
        }
    }

    @Environment(EnvType.CLIENT)
    public class LongEntry extends NumberEntry<Long>
    {
        public LongEntry(ForgeConfigSpec.ConfigValue<Long> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec, Long::parseLong);
        }
    }

    @Environment(EnvType.CLIENT)
    public class BooleanEntry extends ConfigEntry<Boolean>
    {
        private final Button button;

        public BooleanEntry(ForgeConfigSpec.ConfigValue<Boolean> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec);
            this.button = new Button(10, 5, 44, 20, CommonComponents.optionStatus(this.currentValue), (button) -> {
                this.currentValue = !this.currentValue;
                button.setMessage(CommonComponents.optionStatus(this.currentValue));
            });
            this.children.add(this.button);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.button.x = left + width - 67;
            this.button.y = top;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public void resetConfigValue()
        {
            super.resetConfigValue();
            this.button.setMessage(CommonComponents.optionStatus(this.currentValue));
        }
    }

    @Environment(EnvType.CLIENT)
    public class StringEntry extends ConfigEntry<String>
    {
        private final Button button;

        public StringEntry(ForgeConfigSpec.ConfigValue<String> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec);
            String title = createLabelFromConfig(configValue, valueSpec);
            this.button = new Button(10, 5, 44, 20, new TranslatableComponent("configured.gui.edit"), (button) -> {
                ConfigScreen.this.minecraft.setScreen(new EditStringScreen(ConfigScreen.this, new TextComponent(title), this.currentValue, valueSpec::test, v -> this.currentValue = v));
            });
            this.children.add(this.button);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.button.x = left + width - 67;
            this.button.y = top;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    @Environment(EnvType.CLIENT)
    public class ListStringEntry extends ConfigEntry<List<?>>
    {
        private final Button button;

        public ListStringEntry(ForgeConfigSpec.ConfigValue<List<?>> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec);
            String title = createLabelFromConfig(configValue, valueSpec);
            this.button = new Button(10, 5, 44, 20, new TranslatableComponent("configured.gui.edit"), (button) -> {
                ConfigScreen.this.minecraft.setScreen(new EditStringListScreen(ConfigScreen.this, new TextComponent(title), this.currentValue, valueSpec, v -> this.currentValue = v));
            });
            this.children.add(this.button);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.button.x = left + width - 67;
            this.button.y = top;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    @Environment(EnvType.CLIENT)
    public class EnumEntry extends ConfigEntry<Enum<?>>
    {
        private final Button button;

        public EnumEntry(ForgeConfigSpec.ConfigValue<Enum<?>> configValue, ForgeConfigSpec.ValueSpec valueSpec)
        {
            super(configValue, valueSpec);
            this.button = new Button(10, 5, 44, 20, this.getButtonMessage(), (button) -> {
                if(this.currentValue != null)
                {
                    Object[] values = Stream.of(this.currentValue.getDeclaringClass().getEnumConstants()).filter(valueSpec::test).toArray();
                    this.currentValue = (Enum<?>) values[(this.currentValue.ordinal() + 1) % values.length];
                    button.setMessage(this.getButtonMessage());
                }
            });
            this.children.add(this.button);
        }

        @NotNull
        private TextComponent getButtonMessage() {
            return new TextComponent(createLabel(this.currentValue.name()));
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, p_230432_6_, mouseX, mouseY, hovered, partialTicks);
            this.button.x = left + width - 67;
            this.button.y = top;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public void resetConfigValue()
        {
            super.resetConfigValue();
            this.button.setMessage(this.getButtonMessage());
        }
    }

    /**
     * A custom implementation of the text field widget to help reset the focus when it's used
     * in an option list. This class is specific to {@link ConfigScreen} and won't work anywhere
     * else.
     */
    @Environment(EnvType.CLIENT)
    public class ConfigEditBox extends EditBox
    {
        private final Component emptyMessage;

        public ConfigEditBox(Font font, int x, int y, int width, int height, Component emptyMessage)
        {
            super(font, x, y, width, height, TextComponent.EMPTY);
            this.emptyMessage = emptyMessage;
        }

        @Override
        public void setFocus(boolean focused)
        {
            super.setFocus(focused);
//            if(focused)
//            {
//                if(ConfigScreen.this.activeTextField != null && ConfigScreen.this.activeTextField != this)
//                {
//                    ConfigScreen.this.activeTextField.setFocus(false);
//                    ConfigScreen.this.activeTextField = this;
//                }
//                else
//                {
//                    ConfigScreen.this.activeTextField = this;
//                }
//            }
        }

        @Override
        public void renderButton(PoseStack poseStack, int i, int j, float f) {
            super.renderButton(poseStack, i, j, f);
            if (this.active && !this.getValue().isEmpty()) {
                Screen.drawString(poseStack, ConfigScreen.this.minecraft.font, this.emptyMessage, this.x + 4, this.y + 6, 11184810);
            }
        }
    }

    /**
     * Gets the last element in a list
     *
     * @param list         the list of get the value from
     * @param defaultValue if the list is empty, return this value instead
     * @param <V>          the type of list
     * @return the last element
     */
    private static <V> V lastValue(List<V> list, V defaultValue)
    {
        if(list.size() > 0)
        {
            return list.get(list.size() - 1);
        }
        return defaultValue;
    }

    /**
     * Tries to create a readable label from the given config value and spec. This will
     * first attempt to create a label from the translation key in the spec, otherwise it
     * will create a readable label from the raw config value name.
     *
     * @param configValue the config value
     * @param valueSpec   the associated value spec
     * @return a readable label string
     */
    private static String createLabelFromConfig(ForgeConfigSpec.ConfigValue<?> configValue, ForgeConfigSpec.ValueSpec valueSpec)
    {
        if(valueSpec.getTranslationKey() != null && I18n.exists(valueSpec.getTranslationKey()))
        {
            return new TranslatableComponent(valueSpec.getTranslationKey()).getString();
        }
        return createLabel(lastValue(configValue.getPath(), ""));
    }

    /**
     * Tries to create a readable label from the given input. This input should be
     * the raw config value name. For example "shouldShowParticles" will be converted
     * to "Should Show Particles".
     *
     * @param input the config value name
     * @return a readable label string
     */
    private static String createLabel(String input)
    {
        String valueName = input;
        // Try split by camel case
        String[] words = valueName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for(int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        valueName = Strings.join(words, " ");
        // Try split by underscores
        words = valueName.split("_");
        for(int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        // Finally join words. Some mods have inputs like "Foo_Bar" and this causes a double space.
        // To fix this any whitespace is replaced with a single space
        return Strings.join(words, " ").replaceAll("\\s++", " ");
    }

    @Override
    public void renderDirtBackground(int vOffset)
    {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, this.background);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float size = 32.0F;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(0.0D, this.height, 0.0D).uv(0.0F, this.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(this.width, this.height, 0.0D).uv(this.width / size, this.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(this.width, 0.0D, 0.0D).uv(this.width / size, vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(0.0D, 0.0D, 0.0D).uv(0.0F, vOffset).color(64, 64, 64, 255).endVertex();
        tesselator.end();
    }
}
