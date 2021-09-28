package com.mrcrayfish.configured.client.screen;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
import com.mrcrayfish.configured.client.util.ScreenUtil;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
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
@SuppressWarnings("ConstantConditions")
@Environment(EnvType.CLIENT)
public abstract class ConfigScreen extends Screen
{
    public static final ResourceLocation LOGO_TEXTURE = new ResourceLocation(Configured.MODID, "textures/gui/logo.png");

    final Screen lastScreen;
    private final String displayName;
    private final ResourceLocation background;
    private ConfigList configList;
    private List<EntryUnit> screenEntries;
    final Map<Object, EntryUnit> allEntries;
    private ConfigEditBox activeTextField;
    private ConfigEditBox searchTextField;
    private List<? extends FormattedCharSequence> activeTooltip;
    private final Set<Entry> invalidEntries = Sets.newHashSet();
    Button backButton;

    private ConfigScreen(Screen lastScreen, String displayName, ResourceLocation background, Map<Object, EntryUnit> allEntries)
    {
        super(new TextComponent(displayName));
        this.lastScreen = lastScreen;
        this.displayName = displayName;
        this.background = background;
        this.allEntries = allEntries;
    }

    public static class Main extends ConfigScreen {

        private final EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> configFileEntries;
        private Button restoreDefaultsButton;

        public Main(Screen parent, String displayName, EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> configFileEntries, ResourceLocation background) {
            super(parent, displayName, background, gatherAllUnits(configFileEntries));
            this.configFileEntries = configFileEntries;
        }

        @Override
        List<EntryUnit> gatherScreenUnits()
        {
            List<EntryUnit> screenEntries = new ArrayList<>();
            this.configFileEntries.forEach((type, pairs) -> {

                final String typeExtension = type.extension();
                screenEntries.add(new TitleEntryUnit(new TranslatableComponent("configured.gui.config_title", StringUtils.capitalize(typeExtension)), new TranslatableComponent(String.format("configured.gui.%s_config", typeExtension))));
                List<EntryUnit> entries = Lists.newArrayList();
                pairs.forEach(pair -> pair.getRight().valueMap().values().stream().map(this.allEntries::get).filter(Objects::nonNull).forEach(entries::add));
                Collections.sort(entries);
                screenEntries.addAll(entries);
            });
            return ImmutableList.copyOf(screenEntries);
        }

        @Override
        protected void init() {
            super.init();
            this.backButton = this.addRenderableWidget(new Button(this.width / 2 + 5, this.height - 29, 150, 20, CommonComponents.GUI_DONE, button -> {
                this.allEntries.values().forEach(EntryUnit::saveConfigValue);
                this.configFileEntries.values().stream().flatMap(Collection::stream).map(Pair::getLeft).forEach(ForgeConfigSpec::save);
                this.minecraft.setScreen(this.lastScreen);
            }));
            this.addRenderableWidget(new Button(this.width / 2 - 155, this.height - 29, 150, 20, CommonComponents.GUI_CANCEL, button -> this.onClose()));

            final Button.OnTooltip onTooltip = (button, matrixStack, mouseX, mouseY) -> {
                Font font = this.minecraft.font;
                List<FormattedText> lines = font.getSplitter().splitLines(new TranslatableComponent("configured.gui.restore"), 200, Style.EMPTY);
                final List<FormattedCharSequence> formattedCharSequences = Language.getInstance().getVisualOrder(lines);
                this.setActiveTooltip(formattedCharSequences);
            };
            final Button.OnPress onPress = button -> {
                Screen confirmScreen = this.makeConfirmationScreen(new TranslatableComponent("configured.gui.restore_message"), result -> {
                    if (result) {// Resets all config values
                        this.allEntries.values().forEach(EntryUnit::resetCurrentValue);
                        // Updates the current entries to process UI changes
                        this.refreshConfigList("");
                    }
                    this.minecraft.setScreen(this);
                });
                this.minecraft.setScreen(confirmScreen);
            };
            this.restoreDefaultsButton = this.addRenderableWidget(new IconButton(this.width / 2 - 185, this.height - 29, 20, 20, 0, 0, onTooltip, onPress));
            // Call during init to avoid the button flashing active
            this.updateRestoreDefaultButton();
        }

        /**
         * Updates the active state of the restore default button. It will only be active if values are
         * different from their default.
         */
        @Override
        void updateRestoreDefaultButton()
        {
            if(this.restoreDefaultsButton != null)
            {
                this.restoreDefaultsButton.active = this.allEntries.values().stream().anyMatch(EntryUnit::mayResetValue);
            }
        }

        @Override
        public void onClose() {
            Screen confirmScreen;
            if (this.allEntries.values().stream().allMatch(EntryUnit::mayDiscardChanges)) {
                confirmScreen = this.lastScreen;
            } else {
                confirmScreen = this.makeConfirmationScreen(new TranslatableComponent("configured.gui.discard_message"), result -> {
                    if (result) {
                        this.minecraft.setScreen(this.lastScreen);
                    } else {
                        this.minecraft.setScreen(this);
                    }
                });
            }
            this.minecraft.setScreen(confirmScreen);
        }

        private static Map<Object, EntryUnit> gatherAllUnits(EnumMap<ModConfig.Type, List<Pair<ForgeConfigSpec, UnmodifiableConfig>>> configFileEntries)
        {
            Map<Object, EntryUnit> allUnits = Maps.newHashMap();
            configFileEntries.values().stream().flatMap(List::stream).forEach(pair -> {
                gatherTypeUnits(pair.getLeft(), pair.getRight(), allUnits);
            });
            return ImmutableMap.copyOf(allUnits);
        }

        private static void gatherTypeUnits(ForgeConfigSpec spec, UnmodifiableConfig values, Map<Object, EntryUnit> allUnits) {
            values.valueMap().forEach((path, value) ->
            {
                if (value instanceof UnmodifiableConfig configValue) {
                    allUnits.put(configValue, new CategoryEntryUnit(ConfigScreen.formatLabel(path), Pair.of(spec, configValue)));
                    gatherTypeUnits(spec, configValue, allUnits);
                } else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                    allUnits.put(configValue, new ConfigEntryUnit<>(configValue, spec.getRaw(configValue.getPath())));
                }
            });
        }
    }

    private static class Sub extends ConfigScreen {

        private final List<Pair<ForgeConfigSpec, UnmodifiableConfig>> entry;

        public Sub(Screen parent, String displayName, Pair<ForgeConfigSpec, UnmodifiableConfig> entry, ResourceLocation background, Map<Object, EntryUnit> allEntries) {
            super(parent, displayName, background, allEntries);
            this.entry = Collections.singletonList(entry);
        }

        @Override
        List<EntryUnit> gatherScreenUnits()
        {
            List<EntryUnit> entries = Lists.newArrayList();
            this.entry.forEach(pair -> pair.getRight().valueMap().values().stream().map(this.allEntries::get).filter(Objects::nonNull).forEach(entries::add));
            Collections.sort(entries);
            return ImmutableList.copyOf(entries);
        }

        @Override
        protected void init() {
            super.init();
            this.backButton = this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 29, 200, 20, CommonComponents.GUI_BACK, button -> {
                this.minecraft.setScreen(this.lastScreen);
            }));
        }
    }

    /**
     * Gathers the entries for each config spec to be later added to the option list
     */
    abstract List<EntryUnit> gatherScreenUnits();

    @SuppressWarnings("unchecked")
    Entry makeEntry(EntryUnit entryUnit) {

        if (entryUnit instanceof TitleEntryUnit titleEntryUnit) {
            return new TitleEntry(titleEntryUnit.getTitle(), titleEntryUnit.getDesciption());
        } else if (entryUnit instanceof CategoryEntryUnit categoryEntryUnit) {
            return new CategoryEntry(categoryEntryUnit.getTitle(), categoryEntryUnit.getSpecValuePair());
        } else if (entryUnit instanceof ConfigEntryUnit<?> configEntryUnit) {

            final Object currentValue = configEntryUnit.getCurrentValue();
            if(currentValue instanceof Boolean)
            {
                return new BooleanEntry((ConfigEntryUnit<Boolean>) entryUnit);
            }
            else if(currentValue instanceof Integer)
            {
                return new IntegerEntry((ConfigEntryUnit<Integer>) entryUnit);
            }
            else if(currentValue instanceof Double)
            {
                return new DoubleEntry((ConfigEntryUnit<Double>) entryUnit);
            }
            else if(currentValue instanceof Long)
            {
                return new LongEntry((ConfigEntryUnit<Long>) entryUnit);
            }
            else if(currentValue instanceof Enum)
            {
                return new EnumEntry((ConfigEntryUnit<Enum<?>>) entryUnit);
            }
            else if(currentValue instanceof String)
            {
                return new StringEntry((ConfigEntryUnit<String>) entryUnit);
            }
            else if(currentValue instanceof List<?>)
            {
                return new StringListEntry((ConfigEntryUnit<List<String>>) entryUnit);
            }
            Configured.LOGGER.warn("Unsupported config value of class type {}", currentValue.getClass().getSimpleName());
        }
        return null;
    }

    private void updateDoneButton() {
        this.backButton.active = this.invalidEntries.isEmpty();
    }

    void markInvalid(Entry ruleEntry) {
        this.invalidEntries.add(ruleEntry);
        this.updateDoneButton();
    }

    void clearInvalid(Entry ruleEntry) {
        this.invalidEntries.remove(ruleEntry);
        this.updateDoneButton();
    }

    @Override
    protected void init()
    {
        super.init();
        this.screenEntries = this.gatherScreenUnits();
        this.configList = new ConfigList(this.screenEntries.stream().map(this::makeEntry).collect(Collectors.toList()));
        this.addWidget(this.configList);

        this.searchTextField = new ConfigEditBox(this.font, this.width / 2 - 109, 22, 218, 20);
        this.searchTextField.setResponder(this::refreshConfigList);
        this.addWidget(this.searchTextField);
    }

    void refreshConfigList(String query) {
        if (this.configList != null && this.screenEntries != null) {
            Collection<ConfigScreen.Entry> entries;
            if (!query.isEmpty()) {
                entries = this.allEntries.values().stream().filter(entry -> entry.containsQuery(query)).map(this::makeEntry).collect(Collectors.toList());
            } else {
                entries = this.screenEntries.stream().map(this::makeEntry).collect(Collectors.toList());
            }
            this.configList.replaceEntries(entries);
        }
    }

    @NotNull
    ConfirmScreen makeConfirmationScreen(Component component, BooleanConsumer booleanConsumer) {
        return new ConfirmScreen(booleanConsumer, component, TextComponent.EMPTY) {

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
        if (this.activeTextField != null) {
            this.activeTextField.tick();
        }
    }

    void updateRestoreDefaultButton()
    {
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
        this.configList.render(poseStack, mouseX, mouseY, partialTicks);
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

    abstract class Entry extends ContainerObjectSelectionList.Entry<ConfigScreen.Entry>
    {
        private final MutableComponent title;
        @Nullable
        final List<? extends FormattedCharSequence> tooltip;

        public Entry(MutableComponent title, List<? extends FormattedCharSequence> tooltip)
        {
            this.title = title;
            this.tooltip = tooltip;
        }

        public MutableComponent getTitle()
        {
            return this.title;
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            if(ConfigScreen.this.configList != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.configList.getRowLeft() + ConfigScreen.this.configList.getRowWidth() - 67)
            {
                ConfigScreen.this.setActiveTooltip(this.tooltip);
            }
        }

        static List<FormattedCharSequence> makeTooltip(Font font, Component description)
        {
            List<FormattedText> lines = font.getSplitter().splitLines(description, 200, Style.EMPTY);
            return Language.getInstance().getVisualOrder(lines);
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
                    output.add(NarratedElementType.TITLE, ConfigScreen.Entry.this.title);
                }
            });
        }
    }

    public class TitleEntry extends Entry
    {
        public TitleEntry(MutableComponent title, Component description)
        {
            super(title, makeTooltip(ConfigScreen.this.minecraft.font, description));
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
            Component title = this.getTitle().withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.YELLOW);
            Screen.drawCenteredString(poseStack, ConfigScreen.this.minecraft.font, title, left + width / 2, top + 5, 16777215);
        }
    }

    public class CategoryEntry extends Entry
    {
        private final Button button;

        public CategoryEntry(MutableComponent title, Pair<ForgeConfigSpec, UnmodifiableConfig> specValuePair)
        {
            super(title, null);
            String newTitle = ConfigScreen.this.displayName + " > " + this.getTitle().getString();
            final Sub subScreen = new Sub(ConfigScreen.this, newTitle, specValuePair, ConfigScreen.this.background, ConfigScreen.this.allEntries);
            this.button = new Button(10, 5, 44, 20, this.getTitle().withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.WHITE), onPress -> ConfigScreen.this.minecraft.setScreen(subScreen));
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
                    output.add(NarratedElementType.TITLE, CategoryEntry.this.getTitle());
                }
            }, CategoryEntry.this.button);
        }
    }

    interface EntryUnit extends Comparable<EntryUnit> {

        MutableComponent getTitle();

        default boolean containsQuery(String query) {
            return this.getTitle().getString().toLowerCase(Locale.ROOT).trim().contains(query);
        }

        boolean mayResetValue();

        boolean mayDiscardChanges();

        void resetCurrentValue();

        void saveConfigValue();

        @Override
        default int compareTo(@NotNull EntryUnit o) {
            return this.getTitle().getString().compareTo(o.getTitle().getString());
        }
    }

    static class TitleEntryUnit implements EntryUnit {

        private final MutableComponent title;
        private final Component desciption;

        public TitleEntryUnit(MutableComponent title, Component description) {
            this.title = title;
            this.desciption = description;
        }

        @Override
        public MutableComponent getTitle() {
            return this.title;
        }

        public Component getDesciption() {
            return this.desciption;
        }

        @Override
        public boolean containsQuery(String query) {
            return false;
        }

        @Override
        public boolean mayResetValue() {
            return false;
        }

        @Override
        public boolean mayDiscardChanges() {
            return true;
        }

        @Override
        public void resetCurrentValue() {

        }

        @Override
        public void saveConfigValue() {

        }

    }

    static class CategoryEntryUnit implements EntryUnit {

        private final MutableComponent title;
        private final Pair<ForgeConfigSpec, UnmodifiableConfig> specValuePair;

        public CategoryEntryUnit(MutableComponent title, Pair<ForgeConfigSpec, UnmodifiableConfig> specValuePair) {
            this.title = title;
            this.specValuePair = specValuePair;
        }

        @Override
        public MutableComponent getTitle() {
            return this.title;
        }

        public Pair<ForgeConfigSpec, UnmodifiableConfig> getSpecValuePair() {
            return this.specValuePair;
        }

        @Override
        public boolean mayResetValue() {
            return false;
        }

        @Override
        public boolean mayDiscardChanges() {
            return true;
        }

        @Override
        public void resetCurrentValue() {

        }

        @Override
        public void saveConfigValue() {

        }

    }

    static class ConfigEntryUnit<T> implements EntryUnit {

        private final ForgeConfigSpec.ConfigValue<T> configValue;
        private final ForgeConfigSpec.ValueSpec valueSpec;
        private final MutableComponent title;
        private T currentValue;

        public ConfigEntryUnit(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec) {
            this.configValue = configValue;
            this.currentValue = configValue.get();
            this.valueSpec = valueSpec;
            this.title = ConfigScreen.createLabel(this.configValue, this.valueSpec);
        }

        @Override
        public MutableComponent getTitle() {
            return this.title;
        }

        @Override
        public boolean mayResetValue() {
            return !this.currentValue.equals(this.valueSpec.getDefault());
        }

        @Override
        public boolean mayDiscardChanges() {
            return this.configValue.get().equals(this.currentValue);
        }

        @Override
        public void resetCurrentValue() {
            this.currentValue = this.getDefaultValue();
        }

        @Override
        public void saveConfigValue() {
            this.configValue.set(this.currentValue);
        }

        @SuppressWarnings("unchecked")
        public T getDefaultValue() {
            return (T) this.valueSpec.getDefault();
        }

        public T getCurrentValue() {
            return this.currentValue;
        }

        public void setCurrentValue(T currentValue) {
            this.currentValue = currentValue;
        }

        public ForgeConfigSpec.ValueSpec getValueSpec() {
            return this.valueSpec;
        }

        @Nullable
        public List<FormattedCharSequence> makeToolTip(Font font) {
            return this.valueSpec.getComment() != null ? makeToolTip(font, this.configValue, this.valueSpec) : null;
        }

        private static List<FormattedCharSequence> makeToolTip(Font font, ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec.ValueSpec spec)
        {
            List<FormattedText> lines = font.getSplitter().splitLines(new TextComponent(spec.getComment()), 200, Style.EMPTY);
            String name = Iterables.getLast(value.getPath(), "");
            if (name != null && !name.isEmpty()) {
                lines.add(0, new TextComponent(name).withStyle(ChatFormatting.YELLOW));
            }
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
            lines.add(new TranslatableComponent("configured.gui.default", spec.getDefault()).withStyle(ChatFormatting.GRAY));
            return Language.getInstance().getVisualOrder(lines);
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class ConfigEntry<T> extends Entry
    {
        final List<AbstractWidget> children = Lists.newArrayList();
        private final ConfigEntryUnit<T> configEntryUnit;
        private final Button resetButton;

        public ConfigEntry(ConfigEntryUnit<T> configEntryUnit)
        {
            super(configEntryUnit.getTitle(), configEntryUnit.makeToolTip(ConfigScreen.this.minecraft.font));
            this.configEntryUnit = configEntryUnit;
            final Button.OnTooltip onTooltip = (button, matrixStack, mouseX, mouseY) -> {
                final List<FormattedCharSequence> formattedCharSequences = makeTooltip(ConfigScreen.this.minecraft.font, new TranslatableComponent("configured.gui.reset"));
                ConfigScreen.this.setActiveTooltip(formattedCharSequences);
            };
            this.resetButton = new IconButton(0, 0, 20, 20, 0, 0, onTooltip, button -> {
                configEntryUnit.resetCurrentValue();
                this.onConfigValueChanged(configEntryUnit.getCurrentValue(), true);
            });
            this.resetButton.active = configEntryUnit.mayResetValue();
            this.children.add(this.resetButton);
        }

        public void onConfigValueChanged(T newValue, boolean reset) {
            this.resetButton.active = this.configEntryUnit.mayResetValue();
            ConfigScreen.this.updateRestoreDefaultButton();
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

            final MutableComponent title = this.getTitle();
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
                    String comment = ConfigEntry.this.configEntryUnit.getValueSpec().getComment();
                    if(comment != null)
                    {
                        output.add(NarratedElementType.TITLE, ConfigEntry.this.getTitle().append(", " + comment));
                    }
                    else
                    {
                        output.add(NarratedElementType.TITLE, ConfigEntry.this.getTitle());
                    }
                }
            });
            builder.addAll(ConfigEntry.this.children);
            return builder.build();
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class NumberEntry<T> extends ConfigEntry<T>
    {
        private final ConfigEditBox textField;

        public NumberEntry(ConfigEntryUnit<T> configEntryUnit, Function<String, T> parser)
        {
            super(configEntryUnit);
            this.textField = new ConfigEditBox(ConfigScreen.this.font, 0, 0, 42, 18);
            this.textField.setValue(configEntryUnit.getCurrentValue().toString());
            this.textField.setResponder(input -> {
                T number = null;
                try
                {
                    T parsed = parser.apply(input);
                    if(configEntryUnit.getValueSpec().test(parsed))
                    {
                        number = parsed;
                    }
                }
                catch(NumberFormatException ignored)
                {
                }

                if (number != null) {
                    this.textField.setTextColor(14737632);
                    configEntryUnit.setCurrentValue(number);
                    this.onConfigValueChanged(number, false);
                    ConfigScreen.this.clearInvalid(this);
                } else {
                    this.textField.setTextColor(16711680);
                    super.onConfigValueChanged(null, false);
                    ConfigScreen.this.markInvalid(this);
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
        public void onConfigValueChanged(T newValue, boolean reset)
        {
            super.onConfigValueChanged(newValue, reset);
            if (reset) {
                this.textField.setValue(newValue.toString());
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public class IntegerEntry extends NumberEntry<Integer>
    {
        public IntegerEntry(ConfigEntryUnit<Integer> configEntryUnit)
        {
            super(configEntryUnit, Integer::parseInt);
        }
    }

    @Environment(EnvType.CLIENT)
    public class DoubleEntry extends NumberEntry<Double>
    {
        public DoubleEntry(ConfigEntryUnit<Double> configEntryUnit)
        {
            super(configEntryUnit, Double::parseDouble);
        }
    }

    @Environment(EnvType.CLIENT)
    public class LongEntry extends NumberEntry<Long>
    {
        public LongEntry(ConfigEntryUnit<Long> configEntryUnit)
        {
            super(configEntryUnit, Long::parseLong);
        }
    }

    @Environment(EnvType.CLIENT)
    public class BooleanEntry extends ConfigEntry<Boolean>
    {
        private final Button button;

        public BooleanEntry(ConfigEntryUnit<Boolean> configEntryUnit)
        {
            super(configEntryUnit);
            this.button = new Button(10, 5, 44, 20, CommonComponents.optionStatus(configEntryUnit.getCurrentValue()), button -> {
                final boolean newValue = !configEntryUnit.getCurrentValue();
                configEntryUnit.setCurrentValue(newValue);
                this.onConfigValueChanged(newValue, false);
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
        public void onConfigValueChanged(Boolean newValue, boolean reset)
        {
            super.onConfigValueChanged(newValue, reset);
            this.button.setMessage(CommonComponents.optionStatus(newValue));
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class EditScreenEntry<T> extends ConfigEntry<T>
    {
        private final Button button;

        public EditScreenEntry(ConfigEntryUnit<T> configEntryUnit)
        {
            super(configEntryUnit);
            this.button = new Button(10, 5, 44, 20, new TranslatableComponent("configured.gui.edit"), button -> {
                ConfigScreen.this.minecraft.setScreen(this.createEditScreen(this.getTitle(), configEntryUnit.getCurrentValue(), configEntryUnit.getValueSpec(), currentValue -> {
                    configEntryUnit.setCurrentValue(currentValue);
                    this.onConfigValueChanged(currentValue, false);
                }));
            });
            this.children.add(this.button);
        }

        abstract Screen createEditScreen(Component titleIn, T currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<T> onSave);

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks)
        {
            super.render(matrixStack, index, top, left, width, height, mouseX, mouseY, hovered, partialTicks);
            this.button.x = left + width - 67;
            this.button.y = top;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    @Environment(EnvType.CLIENT)
    public class StringEntry extends EditScreenEntry<String>
    {
        public StringEntry(ConfigEntryUnit<String> configEntryUnit)
        {
            super(configEntryUnit);
        }

        @Override
        Screen createEditScreen(Component titleIn, String currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<String> onSave) {
            return new EditStringScreen(ConfigScreen.this, titleIn, currentValue, valueSpec::test, onSave);
        }
    }

    @Environment(EnvType.CLIENT)
    public class StringListEntry extends EditScreenEntry<List<String>>
    {
        public StringListEntry(ConfigEntryUnit<List<String>> configEntryUnit)
        {
            super(configEntryUnit);
        }

        @Override
        Screen createEditScreen(Component titleIn, List<String> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<String>> onSave) {
            return new EditListScreen<>(ConfigScreen.this, titleIn, currentValue, valueSpec, onSave) {
                @Override
                String toString(String value) {
                    return value;
                }

                @Override
                String fromString(String value) {
                    return value;
                }
            };
        }
    }

    @Environment(EnvType.CLIENT)
    public class EnumEntry extends ConfigEntry<Enum<?>>
    {
        private final Button button;

        public EnumEntry(ConfigEntryUnit<Enum<?>> configEntryUnit)
        {
            super(configEntryUnit);
            this.button = new Button(10, 5, 44, 20, new TextComponent(configEntryUnit.getCurrentValue().name()), button -> {
                Object[] values = Stream.of(configEntryUnit.getCurrentValue().getDeclaringClass().getEnumConstants()).filter(configEntryUnit.getValueSpec()::test).toArray();
                final Enum<?> newValue = (Enum<?>) values[(configEntryUnit.getCurrentValue().ordinal() + 1) % values.length];
                configEntryUnit.setCurrentValue(newValue);
                this.onConfigValueChanged(newValue, false);
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
        public void onConfigValueChanged(Enum<?> newValue, boolean reset)
        {
            super.onConfigValueChanged(newValue, reset);
            this.button.setMessage(new TextComponent(newValue.name()));
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
            if(ConfigScreen.this.configList != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.configList.getRowLeft() + ConfigScreen.this.configList.getRowWidth() - 67)
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

    /**
     * A custom implementation of the text field widget to help reset the focus when it's used
     * in an option list. This class is specific to {@link ConfigScreen} and won't work anywhere
     * else.
     */
    @Environment(EnvType.CLIENT)
    public class ConfigEditBox extends EditBox
    {
        public ConfigEditBox(Font font, int x, int y, int width, int height)
        {
            super(font, x, y, width, height, TextComponent.EMPTY);
        }

        @Override
        public void setFocus(boolean focused)
        {
            super.setFocus(focused);
            if(focused)
            {
                if(ConfigScreen.this.activeTextField != null && ConfigScreen.this.activeTextField != this)
                {
                    ConfigScreen.this.activeTextField.setFocus(false);
                }
                ConfigScreen.this.activeTextField = this;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // left click clears text
            if (this.isVisible() && button == 1) {
                this.setValue("");
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
    private static MutableComponent createLabel(ForgeConfigSpec.ConfigValue<?> configValue, ForgeConfigSpec.ValueSpec valueSpec)
    {
        if(valueSpec.getTranslationKey() != null && I18n.exists(valueSpec.getTranslationKey()))
        {
            return new TranslatableComponent(valueSpec.getTranslationKey());
        }
        return formatLabel(Iterables.getLast(configValue.getPath(), ""));
    }

    /**
     * Tries to create a readable label from the given input. This input should be
     * the raw config value name. For example "shouldShowParticles" will be converted
     * to "Should Show Particles".
     *
     * @param input the config value name
     * @return a readable label string
     */
    private static MutableComponent formatLabel(String input)
    {
        if (input == null || input.isEmpty()) {
            return new TextComponent("");
        }
        // Try split by camel case
        String[] words = input.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for(int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        input = Strings.join(words, " ");
        // Try split by underscores
        words = input.split("_");
        for(int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        // Finally join words. Some mods have inputs like "Foo_Bar" and this causes a double space.
        // To fix this any whitespace is replaced with a single space
        return new TextComponent(Strings.join(words, " ").replaceAll("\\s++", " "));
    }
}
