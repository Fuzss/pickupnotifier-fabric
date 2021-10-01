package com.mrcrayfish.configured.client.screen;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
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
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.StringUtils;
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
public abstract class ConfigScreen extends Screen {
    public static final ResourceLocation LOGO_TEXTURE = new ResourceLocation(Configured.MODID, "textures/gui/logo.png");

    final Screen lastScreen;
    private final ResourceLocation background;
    /**
     * all values of this mod's configs stored as our custom units
     * they are accessible by configvalue and unmodifiableconfig
     * only used when building screen specific search and display lists and for certain actions on main screen
     * same for all sub menus
     */
    final Map<Object, IEntryUnit> valueToUnit;
    private final int configListRowWidth = 260;
    private ConfigList configList;
    EditBox searchTextField;
    @Nullable
    private EditBox activeTextField;
    @Nullable
    private List<FormattedCharSequence> activeTooltip;
    private int tooltipTicks;

    private ConfigScreen(Screen lastScreen, Component title, ResourceLocation background, Map<Object, IEntryUnit> valueToUnit) {
        super(title);
        this.lastScreen = lastScreen;
        this.background = background;
        this.valueToUnit = valueToUnit;
    }

    private static class Main extends ConfigScreen {

        /**
         * entries used when searching
         * includes entries from this screen {@link #screenEntries} and from all sub screens of this
         * type is for separators
         */
        private final Map<ModConfig.Type, List<IEntryUnit>> searchEntries;
        /**
         * default entries shown on screen when not searching
         * type is for separators
         */
        private final Map<ModConfig.Type, List<IEntryUnit>> screenEntries;
        /**
         * only used for saving when closing screen
         */
        private final List<ForgeConfigSpec> configSpecs;
        // done, cancel, restore, back (back only appears when search field is not empty)
        private final Button[] buttons = new Button[4];

        private Main(Screen lastScreen, Component title, ResourceLocation background, Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs, Map<Object, IEntryUnit> valueToUnit) {
            super(lastScreen, title, background, valueToUnit);
            this.searchEntries = this.gatherEntriesRecursive(typeToSpecs, valueToUnit);
            this.screenEntries = typeToSpecs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                            .flatMap(spec -> spec.getValues().valueMap().values().stream())
                            .map(valueToUnit::get)
                            .toList(), (o1, o2) -> o1, () -> Maps.newEnumMap(ModConfig.Type.class)));
            this.configSpecs = mergeValues(typeToSpecs.values());
            this.buildSubScreens(mergeValues(this.screenEntries.values()));
        }

        @SuppressWarnings("UnstableApiUsage")
        private Map<ModConfig.Type, List<IEntryUnit>> gatherEntriesRecursive(Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs, Map<Object, IEntryUnit> allEntries) {
            Map<ModConfig.Type, List<IEntryUnit>> entries = Maps.newEnumMap(ModConfig.Type.class);
            typeToSpecs.forEach((type, specs) -> {
                final List<IEntryUnit> typeEntries = entries.computeIfAbsent(type, key -> Lists.newArrayList());
                specs.forEach(spec -> this.gatherEntriesRecursive(spec.getValues(), typeEntries, allEntries));
            });
            return Maps.immutableEnumMap(entries);
        }

        @Override
        public List<ConfigScreen.Entry> getConfigListEntries(final String query) {
            return this.getConfigListEntries(!query.isEmpty() ? this.searchEntries : this.screenEntries, query);
        }

        private List<ConfigScreen.Entry> getConfigListEntries(Map<ModConfig.Type, List<IEntryUnit>> entries, final String query) {
            final ImmutableList.Builder<ConfigScreen.Entry> builder = ImmutableList.builder();
            entries.forEach((type, units) -> {
                List<ConfigScreen.Entry> typeEntries = this.getConfigListEntries(units, query);
                if (!typeEntries.isEmpty()) {
                    builder.add(this.makeTitleEntry(type));
                    builder.addAll(typeEntries);
                }
            });
            return builder.build();
        }

        private ConfigScreen.Entry makeTitleEntry(ModConfig.Type type) {
            String typeExtension = type.extension();
            Component title = new TranslatableComponent("configured.gui.type.title", StringUtils.capitalize(typeExtension)).withStyle(ChatFormatting.YELLOW);
            // description copied from modconfig.type enum comments
            Component comment = new TranslatableComponent(String.format("configured.gui.type.%s", typeExtension));
            List<FormattedCharSequence> description = Stream.of(title, comment).map(component -> this.font.split(component, 200)).flatMap(List::stream).toList();
            return new TypeEntry(title, description);
        }

        @Override
        protected void init() {
            super.init();
            this.buttons[0] = this.addRenderableWidget(new Button(this.width / 2 - 50 - 105, this.height - 28, 100, 20, CommonComponents.GUI_DONE, button -> {
                this.valueToUnit.values().forEach(IEntryUnit::saveConfigValue);
                this.configSpecs.forEach(ForgeConfigSpec::save);
                this.minecraft.setScreen(this.lastScreen);
            }));
            this.buttons[1] = this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 28, 100, 20, CommonComponents.GUI_CANCEL, button -> this.onClose()));
            this.buttons[2] = this.addRenderableWidget(new Button(this.width / 2 - 50 + 105, this.height - 28, 100, 20, new TranslatableComponent("configured.gui.restore"), button -> {
                Screen confirmScreen = this.makeConfirmationScreen(result -> {
                    if (result) {// Resets all config values
                        this.valueToUnit.values().forEach(IEntryUnit::resetCurrentValue);
                        // Updates the current entries to process UI changes
                        this.refreshConfigListEntries("");
                    }
                    this.minecraft.setScreen(this);
                }, new TranslatableComponent("configured.gui.message.restore"));
                this.minecraft.setScreen(confirmScreen);
            }));
            // Call during init to avoid the button flashing active
            this.updateRestoreButton();
            // button is made visible when search field is active
            this.buttons[3] = this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 28, 200, 20, CommonComponents.GUI_BACK, button -> this.searchTextField.setValue("")));
            this.buttons[3].visible = false;
        }

        @Override
        void onSearchFieldChanged(boolean isEmpty) {
            Stream.of(this.buttons).limit(3).forEach(button -> button.visible = isEmpty);
            this.buttons[3].visible = !isEmpty;
        }

        /**
         * Updates the active state of the restore default button. It will only be active if values are
         * different from their default.
         */
        @Override
        void updateRestoreButton() {
            final Button restoreButton = this.buttons[2];
            if (restoreButton != null) {
                restoreButton.active = this.valueToUnit.values().stream().anyMatch(IEntryUnit::mayResetValue);
            }
        }

        @Override
        public void onClose() {
            // exit out of search before closing screen
            if (!this.searchTextField.getValue().isEmpty()) {
                this.searchTextField.setValue("");
            } else {
                // when canceling display confirm screen if any values have been changed
                Screen confirmScreen;
                if (this.valueToUnit.values().stream().allMatch(IEntryUnit::mayDiscardChanges)) {
                    confirmScreen = this.lastScreen;
                } else {
                    confirmScreen = this.makeConfirmationScreen(result -> {
                        if (result) {
                            this.minecraft.setScreen(this.lastScreen);
                        } else {
                            this.minecraft.setScreen(this);
                        }
                    }, new TranslatableComponent("configured.gui.message.discard"));
                }
                this.minecraft.setScreen(confirmScreen);
            }
        }
    }

    private static class Sub extends ConfigScreen {

        /**
         * entries used when searching
         * includes entries from this screen {@link #screenEntries} and from all sub screens of this
         */
        private final List<IEntryUnit> searchEntries;
        /**
         * default entries shown on screen when not searching
         */
        private final List<IEntryUnit> screenEntries;

        private Sub(ConfigScreen lastScreen, Component title, UnmodifiableConfig config) {
            super(lastScreen, title, lastScreen.background, lastScreen.valueToUnit);
            this.searchEntries = this.gatherEntriesRecursive(config, lastScreen.valueToUnit);
            this.screenEntries = config.valueMap().values().stream().map(lastScreen.valueToUnit::get).toList();
            this.buildSubScreens(this.screenEntries);
        }

        private List<IEntryUnit> gatherEntriesRecursive(UnmodifiableConfig mainConfig, Map<Object, IEntryUnit> allEntries) {
            List<IEntryUnit> entries = Lists.newArrayList();
            this.gatherEntriesRecursive(mainConfig, entries, allEntries);
            return ImmutableList.copyOf(entries);
        }

        @Override
        public List<ConfigScreen.Entry> getConfigListEntries(final String query) {
            return this.getConfigListEntries(!query.isEmpty() ? this.searchEntries : this.screenEntries, query);
        }

        @Override
        protected void init() {
            super.init();
            this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 28, 200, 20, CommonComponents.GUI_BACK, button -> this.onClose()));
            this.makeNavigationButtons().forEach(this::addRenderableWidget);
        }

        private List<Button> makeNavigationButtons() {
            List<Screen> lastScreens = this.getLastScreens();
            final int maxSize = 5;
            List<Button> buttons = Lists.newLinkedList();
            final List<FormattedCharSequence> tooltip = Lists.newArrayList(CommonComponents.GUI_BACK.getVisualOrderText());
            for (int i = 0, size = Math.min(maxSize, lastScreens.size()); i < size; i++) {
                Screen screen = lastScreens.get(size - 1 - i);
                final boolean otherScreen = screen != this;
                final Component title = i == 0 && lastScreens.size() > maxSize ? new TextComponent(". . .") : screen.getTitle();
                buttons.add(new Button(0, 1, Sub.this.font.width(title) + 4, 20, title, button -> {
                    if (otherScreen) {
                        Sub.this.minecraft.setScreen(screen);
                    }
                }, (Button button, PoseStack poseStack, int mouseX, int mouseY) -> {
                    if (otherScreen && button.active) {
                        this.setActiveTooltip(tooltip);
                    }
                }) {

                    @Override
                    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
                        // yellow when hovered
                        int color = otherScreen && this.isHovered() ? 16777045 : 16777215;
                        drawCenteredString(poseStack, Sub.this.font, this.getMessage(), this.x + this.width / 2, this.y + (this.height - 8) / 2, color);
                        if (this.isHovered()) {
                            // move down as this is right at screen top
                            this.renderToolTip(poseStack, mouseX, mouseY);
                        }
                    }

                    @Override
                    public void playDownSound(SoundManager soundManager) {
                        if (otherScreen) {
                            super.playDownSound(soundManager);
                        }
                    }
                });
                if (i < size - 1) {
                    buttons.add(new Button(0, 1, Sub.this.font.width(">") + 4, 20, new TextComponent(">"), button -> {
                    }) {

                        @Override
                        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
                            drawCenteredString(poseStack, Sub.this.font, this.getMessage(), this.x + this.width / 2, this.y + (this.height - 8) / 2, 16777215);
                        }

                        @Override
                        public void playDownSound(SoundManager soundManager) {
                        }
                    });
                }
            }
            this.setButtonPosX(buttons);
            return buttons;
        }

        private List<Screen> getLastScreens() {
            Screen lastScreen = this;
            List<Screen> lastScreens = Lists.newLinkedList();
            while (lastScreen instanceof ConfigScreen configScreen) {
                lastScreens.add(lastScreen);
                lastScreen = configScreen.lastScreen;
            }
            return lastScreens;
        }

        private void setButtonPosX(List<Button> buttons) {
            int posX = (this.width - buttons.stream().mapToInt(AbstractWidget::getWidth).sum()) / 2;
            for (Button navigationButton : buttons) {
                navigationButton.x = posX;
                posX += navigationButton.getWidth();
            }
        }

        @Override
        void drawBaseTitle(PoseStack poseStack) {
        }
    }

    @Override
    protected void init() {
        super.init();
        this.configList = new ConfigList(this.getConfigListEntries(""));
        this.addWidget(this.configList);
        this.searchTextField = new ConfigSearchBox(this.font, this.width / 2 - 109, 22, 218, 20, TextComponent.EMPTY);
        this.searchTextField.setResponder(query -> {
            this.refreshConfigListEntries(query);
            this.onSearchFieldChanged(query.isEmpty());
        });
        this.addWidget(this.searchTextField);

        final List<FormattedCharSequence> tooltip = this.font.split(new TranslatableComponent("configured.gui.info"), 200);
        final ImageButton configuredButton = new ImageButton(10, 13, 23, 23, 0, 0, 0, LOGO_TEXTURE, 32, 32, button -> {
            Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/configured"));
            this.handleComponentClicked(style);
        }, (Button button, PoseStack poseStack, int mouseX, int mouseY) -> {
            this.setActiveTooltip(tooltip);
        }, TextComponent.EMPTY);
        this.addRenderableWidget(configuredButton);
    }

    void refreshConfigListEntries(String query) {
        if (this.configList != null) {
            this.configList.replaceEntries(this.getConfigListEntries(query.toLowerCase(Locale.ROOT).trim()));
        }
    }

    void onSearchFieldChanged(boolean isEmpty) {
        // sets bottom button visibilities
    }

    void updateRestoreButton() {
        // button only present on main screen
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

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        List<FormattedCharSequence> lastTooltip = this.activeTooltip;
        this.activeTooltip = null;
        this.renderBackground(poseStack);
        this.configList.render(poseStack, mouseX, mouseY, partialTicks);
        this.searchTextField.render(poseStack, mouseX, mouseY, partialTicks);
        this.drawBaseTitle(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        if (this.activeTooltip != lastTooltip) {
            this.tooltipTicks = 0;
        }
        if (this.activeTooltip != null && this.tooltipTicks >= 10) {
            this.renderTooltip(poseStack, this.activeTooltip, mouseX, mouseY);
        }
        this.children().forEach(o ->
        {
            if (o instanceof Button.OnTooltip) {
                ((Button.OnTooltip) o).onTooltip((Button) o, poseStack, mouseX, mouseY);
            }
        });
    }

    void drawBaseTitle(PoseStack poseStack) {
        drawCenteredString(poseStack, this.font, this.getTitle(), this.width / 2, 7, 16777215);
    }

    @Override
    public void renderDirtBackground(int vOffset) {
        renderDirtBackground(this, this.background, vOffset);
    }

    public static void renderDirtBackground(Screen screen, ResourceLocation background, int vOffset) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, background);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float size = 32.0F;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(0.0D, screen.height, 0.0D).uv(0.0F, screen.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(screen.width, screen.height, 0.0D).uv(screen.width / size, screen.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(screen.width, 0.0D, 0.0D).uv(screen.width / size, vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(0.0D, 0.0D, 0.0D).uv(0.0F, vOffset).color(64, 64, 64, 255).endVertex();
        tesselator.end();
    }

    @Override
    public void onClose() {
        // exit out of search before closing screen
        if (!this.searchTextField.getValue().isEmpty()) {
            this.searchTextField.setValue("");
        } else {
            this.minecraft.setScreen(this.lastScreen);
        }
    }

    void gatherEntriesRecursive(UnmodifiableConfig mainConfig, List<IEntryUnit> entries, Map<Object, IEntryUnit> allEntries) {
        mainConfig.valueMap().values().forEach(value -> {
            entries.add(allEntries.get(value));
            if (value instanceof UnmodifiableConfig config) {
                this.gatherEntriesRecursive(config, entries, allEntries);
            }
        });
    }

    abstract List<ConfigScreen.Entry> getConfigListEntries(String query);

    List<ConfigScreen.Entry> getConfigListEntries(List<IEntryUnit> entries, final String query) {
        return entries.stream()
                // set query to units so the can highlight matches
                .peek(unit -> unit.setSearchQuery(query))
                .filter(IEntryUnit::containsSearchQuery)
                .sorted()
                .map(this::makeEntry)
                // there might be an unsupported value which will return null
                .filter(Objects::nonNull)
                .toList();
    }

    void buildSubScreens(List<IEntryUnit> screenEntries) {
        // every screen must build their own direct sub screens so when searching we can jump between screens in their actual hierarchical order
        // having screens stored like this is ok as everything else will be reset during init when the screen is opened anyways
        for (IEntryUnit unit : screenEntries) {
            if (unit instanceof CategoryEntryUnit categoryEntryUnit) {
                categoryEntryUnit.setScreen(new Sub(this, categoryEntryUnit.getTitle(), categoryEntryUnit.getConfig()));
            }
        }
    }

    void setActiveTooltip(@Nullable List<FormattedCharSequence> activeTooltip) {
        this.activeTooltip = activeTooltip;
    }

    @SuppressWarnings("unchecked")
    Entry makeEntry(IEntryUnit entryUnit) {
        if (entryUnit instanceof CategoryEntryUnit categoryEntryUnit) {
            return new CategoryEntry(categoryEntryUnit.getDisplayTitle(), categoryEntryUnit.getScreen());
        } else if (entryUnit instanceof ConfigEntryUnit<?> configEntryUnit) {
            final Object currentValue = configEntryUnit.getCurrentValue();
            if (currentValue instanceof Boolean) {
                return new BooleanEntry((ConfigEntryUnit<Boolean>) entryUnit);
            } else if (currentValue instanceof Integer) {
                return new NumberEntry<>((ConfigEntryUnit<Integer>) entryUnit, Integer::parseInt);
            } else if (currentValue instanceof Double) {
                return new NumberEntry<>((ConfigEntryUnit<Double>) entryUnit, Double::parseDouble);
            } else if (currentValue instanceof Long) {
                return new NumberEntry<>((ConfigEntryUnit<Long>) entryUnit, Long::parseLong);
            } else if (currentValue instanceof Enum<?>) {
                return new EnumEntry((ConfigEntryUnit<Enum<?>>) entryUnit);
            } else if (currentValue instanceof String) {
                return new StringEntry((ConfigEntryUnit<String>) entryUnit);
            } else if (currentValue instanceof List<?> listValue) {
                Object value = this.getListValue(((ConfigEntryUnit<List<?>>) entryUnit).getDefaultValue(), listValue);
                try {
                    return this.makeListEntry(entryUnit, value);
                } catch (RuntimeException e) {
                    Configured.LOGGER.warn("Unable to add list entry containing class type {}", value.getClass().getSimpleName(), e);
                }
                return null;
            }
            Configured.LOGGER.warn("Unsupported config value of class type {}", currentValue.getClass().getSimpleName());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ListEntry<?> makeListEntry(IEntryUnit entryUnit, Object value) throws RuntimeException {
        if (value instanceof Boolean) {
            return new ListEntry<>((ConfigEntryUnit<List<Boolean>>) entryUnit, Boolean.class, Object::toString, v -> switch (v.toLowerCase(Locale.ROOT)) {
                case "true" -> true;
                case "false" -> false;
                // is caught when editing
                default -> throw new IllegalArgumentException("unable to convert boolean value");
            });
        } else if (value instanceof Integer) {
            return new ListEntry<>((ConfigEntryUnit<List<Integer>>) entryUnit, Integer.class, Object::toString, Integer::parseInt);
        } else if (value instanceof Double) {
            return new ListEntry<>((ConfigEntryUnit<List<Double>>) entryUnit, Double.class, Object::toString, Double::parseDouble);
        } else if (value instanceof Long) {
            return new ListEntry<>((ConfigEntryUnit<List<Long>>) entryUnit, Long.class, Object::toString, Long::parseLong);
        } else if (value instanceof Enum<?>) {
            return new EnumListEntry((ConfigEntryUnit<List<Enum<?>>>) entryUnit, (Class<Enum<?>>) value.getClass());
        } else if (value instanceof String) {
            return new ListEntry<>((ConfigEntryUnit<List<String>>) entryUnit, String.class, Function.identity(), Function.identity());
        } else {
            // string list with warning screen
            return new DangerousListEntry((ConfigEntryUnit<List<String>>) entryUnit);
        }
    }

    @Nullable
    private Object getListValue(List<?> defaultValue, List<?> currentValue) {
        // desperate attempt to somehow get some generic information out of a list
        // checking default values first is important as current values might be of a different type due to how configs are read
        // example: enum are often read as strings (especially this case needs a couple more work-around later)
        if (!defaultValue.isEmpty()) {
            return defaultValue.get(0);
        } else if (!currentValue.isEmpty()) {
            return currentValue.get(0);
        }
        return null;
    }

    ConfirmScreen makeConfirmationScreen(BooleanConsumer booleanConsumer, Component component) {
        // just a confirmation screen with a custom background
        return new ConfirmScreen(booleanConsumer, component, TextComponent.EMPTY) {

            @Override
            public void renderDirtBackground(int vOffset) {
                ConfigScreen.renderDirtBackground(this, ConfigScreen.this.background, vOffset);
            }

        };
    }

    interface IEntryUnit extends Comparable<EntryUnit> {

        Component getTitle();

        /**
         * @return title or colored title for search
         */
        default Component getDisplayTitle() {
            if (this.withPath()) {
                List<Integer> indices = this.getSearchIndices(this.getSearchableTitle(), this.getSearchQuery());
                if (!indices.isEmpty()) {
                    return this.getColoredTitle(this.getTitle().getString(), this.getSearchQuery().length(), indices);
                }
            }
            return this.getTitle();
        }

        /**
         * title used in search results highlighting current query
         */
        private Component getColoredTitle(String title, int length, List<Integer> indices) {
            MutableComponent component = new TextComponent(title.substring(0, indices.get(0))).withStyle(ChatFormatting.GRAY);
            for (int i = 0, indicesSize = indices.size(); i < indicesSize; i++) {
                int start = indices.get(i);
                int end = start + length;
                component.append(new TextComponent(title.substring(start, end)).withStyle(ChatFormatting.WHITE));
                int nextStart;
                int j = i;
                if (++j < indicesSize) {
                    nextStart = indices.get(j);
                } else {
                    nextStart = title.length();
                }
                component.append(new TextComponent(title.substring(end, nextStart)).withStyle(ChatFormatting.GRAY));
            }
            return component;
        }

        /**
         * all starting indices of query for highlighting text
         */
        private List<Integer> getSearchIndices(String title, String query) {
            List<Integer> indices = Lists.newLinkedList();
            if (!query.isEmpty()) {
                int index = title.indexOf(query);
                while (index >= 0) {
                    indices.add(index);
                    index = title.indexOf(query, index + 1);
                }
            }
            return indices;
        }

        default String getSearchableTitle() {
            return this.getTitle().getString().toLowerCase(Locale.ROOT);
        }

        default boolean containsSearchQuery() {
            return this.getSearchableTitle().contains(this.getSearchQuery());
        }

        void setSearchQuery(String query);

        String getSearchQuery();

        default boolean withPath() {
            return !this.getSearchQuery().isEmpty();
        }

        boolean mayResetValue();

        /**
         * @return can cancel without warning
         */
        boolean mayDiscardChanges();

        void resetCurrentValue();

        /**
         * save to actual config, called when pressing done
         */
        void saveConfigValue();

        @Override
        default int compareTo(@NotNull EntryUnit other) {
            int compare = this.getTitle().getString().compareTo(other.getTitle().getString());
            if (this.withPath()) {
                // when searching sort by index of query, only if both match sort alphabetically
                final String query = this.getSearchQuery();
                final int compareIndex = this.getSearchableTitle().indexOf(query) - other.getSearchableTitle().indexOf(query);
                if (compareIndex != 0) {
                    compare = compareIndex;
                }
            }
            return compare;
        }
    }

    static class EntryUnit implements IEntryUnit {

        private final Component title;
        private String searchQuery = "";

        public EntryUnit(Component title) {
            this.title = title;
        }

        @Override
        public Component getTitle() {
            return this.title;
        }

        @Override
        public void setSearchQuery(String query) {
            this.searchQuery = query;
        }

        @Override
        public String getSearchQuery() {
            return this.searchQuery;
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

    static class CategoryEntryUnit extends EntryUnit {

        private final UnmodifiableConfig config;
        private ConfigScreen screen;

        public CategoryEntryUnit(Component title, UnmodifiableConfig config) {
            super(title);
            this.config = config;
        }

        public UnmodifiableConfig getConfig() {
            return this.config;
        }

        public ConfigScreen getScreen() {
            return this.screen;
        }

        public void setScreen(ConfigScreen screen) {
            this.screen = screen;
        }
    }

    static class ConfigEntryUnit<T> extends EntryUnit {

        private final ForgeConfigSpec.ConfigValue<T> configValue;
        private final ForgeConfigSpec.ValueSpec valueSpec;
        private T currentValue;

        public ConfigEntryUnit(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec) {
            super(ConfigScreen.createLabel(configValue, valueSpec));
            this.configValue = configValue;
            this.valueSpec = valueSpec;
            this.currentValue = configValue.get();
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

        public List<String> getPath() {
            return this.configValue.getPath();
        }
    }

    abstract class Entry extends ContainerObjectSelectionList.Entry<ConfigScreen.Entry> {
        private final Component title;
        @Nullable
        private final List<FormattedCharSequence> tooltip;

        public Entry(Component title, List<FormattedCharSequence> tooltip) {
            this.title = title;
            this.tooltip = tooltip;
        }

        public final Component getTitle() {
            return this.title;
        }

        @Nullable
        public final List<FormattedCharSequence> getTooltip() {
            return this.tooltip;
        }

        @Override
        public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            if (ConfigScreen.this.configList != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.configList.getRowLeft() + ConfigScreen.this.configList.getRowWidth() - 67) {
                ConfigScreen.this.setActiveTooltip(this.getTooltip());
            }
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, ConfigScreen.Entry.this.title);
                }
            });
        }

        String getTruncatedText(Font font, String component, int maxWidth) {
            // trim component when too long
            if (font.width(component) > maxWidth) {
                return font.plainSubstrByWidth(component, maxWidth - font.width("...")) + "...";
            } else {
                return component;
            }
        }

        FormattedText getTruncatedText(Font font, Component component, int maxWidth, Style style) {
            // trim component when too long
            if (font.width(component) > maxWidth) {
                return FormattedText.composite(font.getSplitter().headByWidth(component, maxWidth - font.width("..."), style), FormattedText.of("..."));
            } else {
                return component;
            }
        }
    }

    public class TypeEntry extends Entry {
        public TypeEntry(Component title, List<FormattedCharSequence> description) {
            super(new TextComponent("").append(title).withStyle(ChatFormatting.BOLD), description);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of();
        }

        @Override
        public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(poseStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, hovered, partialTicks);
            Screen.drawCenteredString(poseStack, ConfigScreen.this.font, this.getTitle(), entryLeft + rowWidth / 2, entryTop + 5, 16777215);
        }
    }

    public class CategoryEntry extends Entry {
        private final Button button;

        public CategoryEntry(Component title, ConfigScreen screen) {
            super(title, null);
            final FormattedText truncatedText = this.getTruncatedText(ConfigScreen.this.font, title, ConfigScreen.this.configListRowWidth - 4, Style.EMPTY.withBold(true));
            this.button = new Button(10, 5, ConfigScreen.this.configListRowWidth, 20, new TextComponent(truncatedText.getString()).withStyle(ChatFormatting.BOLD), button -> ConfigScreen.this.minecraft.setScreen(screen));
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.button);
        }

        @Override
        public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean selected, float partialTicks) {
            super.render(poseStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, selected, partialTicks);
            this.button.x = entryLeft - 1;
            this.button.y = entryTop;
            this.button.render(poseStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    output.add(NarratedElementType.TITLE, CategoryEntry.this.getTitle());
                }
            }, CategoryEntry.this.button);
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class ConfigEntry<T> extends Entry {
        private final List<AbstractWidget> children = Lists.newArrayList();
        private final ConfigEntryUnit<T> configEntryUnit;
        private final FormattedCharSequence visualTitle;
        final Button resetButton;

        public ConfigEntry(ConfigEntryUnit<T> configEntryUnit, Function<T, String> toString) {
            // default value converter (toString) is necessary for enum values (issue is visible when handling chatformatting values which would otherwise be converted to their corresponding formatting and therefore not display)
            super(configEntryUnit.getDisplayTitle(), makeTooltip(ConfigScreen.this.font, configEntryUnit, toString));
            this.configEntryUnit = configEntryUnit;
            FormattedText truncatedTitle = this.getTruncatedText(ConfigScreen.this.font, this.getTitle(), ConfigScreen.this.configListRowWidth - 70, Style.EMPTY);
            this.visualTitle = Language.getInstance().getVisualOrder(truncatedTitle);
            final List<FormattedCharSequence> formattedCharSequences = ConfigScreen.this.font.split(new TranslatableComponent("configured.gui.tooltip.reset"), 200);
            this.resetButton = new IconButton(0, 0, 20, 20, 0, 0, button -> {
                configEntryUnit.resetCurrentValue();
                this.onConfigValueChanged(configEntryUnit.getCurrentValue(), true);
            }, (button, matrixStack, mouseX, mouseY) -> {
                if (button.active) {
                    ConfigScreen.this.setActiveTooltip(formattedCharSequences);
                }
            });
            this.resetButton.active = configEntryUnit.mayResetValue();
            this.children.add(this.resetButton);
        }

        public void onConfigValueChanged(T newValue, boolean reset) {
            this.resetButton.active = this.configEntryUnit.mayResetValue();
            ConfigScreen.this.updateRestoreButton();
        }

        @Override
        public List<AbstractWidget> children() {
            return this.children;
        }

        @Override
        public void render(PoseStack poseStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(poseStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, hovered, partialTicks);
            // value button start: end - 67
            // value button width: 44
            // gap: 2
            // reset button start: end - 21
            // reset button width: 20
            ConfigScreen.this.font.drawShadow(poseStack, this.visualTitle, entryLeft, entryTop + 6, 16777215);
            this.resetButton.x = entryLeft + rowWidth - 21;
            this.resetButton.y = entryTop;
            this.resetButton.render(poseStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            ImmutableList.Builder<NarratableEntry> builder = ImmutableList.builder();
            builder.add(new NarratableEntry() {
                @Override
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarratableEntry.NarrationPriority.HOVERED;
                }

                @Override
                public void updateNarration(NarrationElementOutput output) {
                    String comment = ConfigEntry.this.configEntryUnit.getValueSpec().getComment();
                    if (comment != null) {
                        output.add(NarratedElementType.TITLE, new TextComponent("").append(ConfigEntry.this.getTitle()).append(", " + comment));
                    } else {
                        output.add(NarratedElementType.TITLE, ConfigEntry.this.getTitle());
                    }
                }
            });
            builder.addAll(ConfigEntry.this.children);
            return builder.build();
        }

        static <T> List<FormattedCharSequence> makeTooltip(Font font, ConfigEntryUnit<T> configEntryUnit, Function<T, String> toString) {
            return makeTooltip(font, configEntryUnit.getPath(), configEntryUnit.getValueSpec().getComment(), toString.apply(configEntryUnit.getDefaultValue()), configEntryUnit.withPath());
        }

        private static List<FormattedCharSequence> makeTooltip(Font font, List<String> path, String comment, String defaultValue, boolean withPath) {
            final List<FormattedText> lines = Lists.newArrayList();
            // get title
            String name = Iterables.getLast(path, "");
            if (name != null && !name.isEmpty()) {
                final Component component = new TextComponent(name).withStyle(ChatFormatting.YELLOW);
                lines.addAll(font.getSplitter().splitLines(component, 200, Style.EMPTY));
            }
            if (comment != null && !comment.isEmpty()) {
                final List<FormattedText> splitLines = font.getSplitter().splitLines(comment, 200, Style.EMPTY);
                int rangeIndex = -1;
                // finds index of range (number types) / allowed values (enums) line
                for (int i = 0; i < splitLines.size(); i++) {
                    String text = splitLines.get(i).getString();
                    if (text.startsWith("Range: ") || text.startsWith("Allowed Values: ")) {
                        rangeIndex = i;
                        break;
                    }
                }
                // sets text color from found index to end to gray
                if (rangeIndex != -1) {
                    for (int i = rangeIndex; i < splitLines.size(); i++) {
                        splitLines.set(i, new TextComponent(splitLines.get(i).getString()).withStyle(ChatFormatting.GRAY));
                    }
                }
                lines.addAll(splitLines);
            }
            // default value
            lines.addAll(font.getSplitter().splitLines(new TranslatableComponent("configured.gui.tooltip.default", defaultValue).withStyle(ChatFormatting.GRAY), 200, Style.EMPTY));
            if (withPath) { // path is only added when searching as there would be no way to tell otherwise where the entry is located
                final Component pathComponent = path.stream().map(ConfigScreen::formatLabel).reduce((o1, o2) -> new TextComponent("").append(o1).append(" > ").append(o2)).orElse(TextComponent.EMPTY);
                lines.addAll(font.getSplitter().splitLines(new TranslatableComponent("configured.gui.tooltip.path", pathComponent).withStyle(ChatFormatting.GRAY), 200, Style.EMPTY));
            }
            return Language.getInstance().getVisualOrder(lines);
        }
    }

    @Environment(EnvType.CLIENT)
    public class NumberEntry<T> extends ConfigEntry<T> {
        private final EditBox textField;

        public NumberEntry(ConfigEntryUnit<T> configEntryUnit, Function<String, T> parser) {
            super(configEntryUnit, Object::toString);
            this.textField = new EditBox(ConfigScreen.this.font, 0, 0, 42, 18, TextComponent.EMPTY) {

                @Override
                public void setFocus(boolean focused) {
                    super.setFocus(focused);
                    ConfigScreen.this.activeTextField = focused ? this : null;
                }
            };
            this.textField.setResponder(input -> {
                T number = null;
                try {
                    T parsed = parser.apply(input);
                    if (configEntryUnit.getValueSpec().test(parsed)) {
                        number = parsed;
                    }
                } catch (NumberFormatException ignored) {
                }

                if (number != null) {
                    // white text color
                    this.textField.setTextColor(14737632);
                    configEntryUnit.setCurrentValue(number);
                    this.onConfigValueChanged(number, false);
                } else {
                    // red text color
                    this.textField.setTextColor(16711680);
                    configEntryUnit.resetCurrentValue();
                    // provides an easy way to make text field usable again, even though default value is already set in background
                    this.resetButton.active = true;
                    ConfigScreen.this.updateRestoreButton();
                }
            });
            this.textField.setValue(configEntryUnit.getCurrentValue().toString());
            this.children().add(this.textField);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(matrixStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, hovered, partialTicks);
            this.textField.x = entryLeft + rowWidth - 66;
            this.textField.y = entryTop + 1;
            this.textField.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public void onConfigValueChanged(T newValue, boolean reset) {
            super.onConfigValueChanged(newValue, reset);
            if (reset) {
                this.textField.setValue(newValue.toString());
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public class BooleanEntry extends ConfigEntry<Boolean> {
        private final Button button;

        public BooleanEntry(ConfigEntryUnit<Boolean> configEntryUnit) {
            super(configEntryUnit, Object::toString);
            this.button = new Button(10, 5, 44, 20, CommonComponents.optionStatus(configEntryUnit.getCurrentValue()), button -> {
                final boolean newValue = !configEntryUnit.getCurrentValue();
                configEntryUnit.setCurrentValue(newValue);
                this.onConfigValueChanged(newValue, false);
            });
            this.children().add(this.button);
        }

        @Override
        public void render(PoseStack matrixStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(matrixStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, hovered, partialTicks);
            this.button.x = entryLeft + rowWidth - 67;
            this.button.y = entryTop;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        @Override
        public void onConfigValueChanged(Boolean newValue, boolean reset) {
            super.onConfigValueChanged(newValue, reset);
            this.button.setMessage(CommonComponents.optionStatus(newValue));
        }
    }

    @Environment(EnvType.CLIENT)
    public abstract class EditScreenEntry<T> extends ConfigEntry<T> {
        private final Button button;

        public EditScreenEntry(ConfigEntryUnit<T> configEntryUnit, Function<T, String> toString, Class<?> type) {
            super(configEntryUnit, toString);
            this.button = new Button(10, 5, 44, 20, new TranslatableComponent("configured.gui.edit"), button -> {
                // safety precaution for dealing with lists
                try {
                    ConfigScreen.this.minecraft.setScreen(this.makeEditScreen(configEntryUnit.getTitle(), type, configEntryUnit.getCurrentValue(), configEntryUnit.getValueSpec(), currentValue -> {
                        configEntryUnit.setCurrentValue(currentValue);
                        this.onConfigValueChanged(currentValue, false);
                    }));
                } catch (RuntimeException e) {
                    Configured.LOGGER.warn("Unable to handle list entry containing class type {}", type.getSimpleName(), e);
                    button.active = false;
                }
            });
            this.children().add(this.button);
        }

        abstract Screen makeEditScreen(Component titleIn, Class<?> type, T currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<T> onSave);

        @Override
        public void render(PoseStack matrixStack, int index, int entryTop, int entryLeft, int rowWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(matrixStack, index, entryTop, entryLeft, rowWidth, entryHeight, mouseX, mouseY, hovered, partialTicks);
            this.button.x = entryLeft + rowWidth - 67;
            this.button.y = entryTop;
            this.button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    @Environment(EnvType.CLIENT)
    public class EnumEntry extends EditScreenEntry<Enum<?>> {
        public EnumEntry(ConfigEntryUnit<Enum<?>> configEntryUnit) {
            super(configEntryUnit, v -> formatLabel(v.name().toLowerCase(Locale.ROOT)).getString(), Enum.class);
        }

        @Override
        Screen makeEditScreen(Component titleIn, Class<?> type, Enum<?> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<Enum<?>> onSave) {
            return new EditEnumScreen(ConfigScreen.this, new TranslatableComponent("configured.gui.value.select", type.getSimpleName()), currentValue, currentValue.getDeclaringClass().getEnumConstants(), valueSpec::test, onSave);
        }
    }

    @Environment(EnvType.CLIENT)
    public class StringEntry extends EditScreenEntry<String> {
        public StringEntry(ConfigEntryUnit<String> configEntryUnit) {
            super(configEntryUnit, Function.identity(), String.class);
        }

        @Override
        Screen makeEditScreen(Component titleIn, Class<?> type, String currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<String> onSave) {
            return new EditStringScreen(ConfigScreen.this, new TranslatableComponent("configured.gui.value.edit", type.getSimpleName()), currentValue, valueSpec::test, onSave);
        }
    }

    @Environment(EnvType.CLIENT)
    public class ListEntry<T> extends EditScreenEntry<List<T>> {
        private final Function<T, String> toString;
        private final Function<String, T> fromString;

        public ListEntry(ConfigEntryUnit<List<T>> configEntryUnit, Class<T> type, Function<T, String> toString, Function<String, T> fromString) {
            super(configEntryUnit, v -> "[" + v.stream().map(t -> {
                // enums are sometimes read as strings which end up here
                if (t instanceof Enum<?> e) {
                    return e.name();
                }
                return toString.apply(t);
            }).collect(Collectors.joining(", ")) + "]", type);
            this.toString = toString;
            this.fromString = fromString;
        }

        @SuppressWarnings("unchecked")
        @Override
        Screen makeEditScreen(Component titleIn, Class<?> type, List<T> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<T>> onSave) {
            return new EditListScreen<>(ConfigScreen.this, titleIn, (Class<T>) type, currentValue, valueSpec, onSave) {
                @Override
                String toString(T value) {
                    // enums are sometimes read as strings which end up here
                    if (value instanceof String s) {
                        return s;
                    }
                    return ListEntry.this.toString.apply(value);
                }

                @Override
                T fromString(String value) {
                    return ListEntry.this.fromString.apply(value);
                }
            };
        }
    }

    // only here to enable unchecked cast
    public class EnumListEntry extends ListEntry<Enum<?>> {

        public EnumListEntry(ConfigEntryUnit<List<Enum<?>>> configEntryUnit, Class<Enum<?>> clazz) {
            // last two are unused
            super(configEntryUnit, clazz, Enum::name, v -> valueOf(clazz, v));
        }

        @SuppressWarnings("unchecked")
        private static <T extends Enum<T>> T valueOf(Class<?> clazz, String v) {
            return Enum.valueOf((Class<T>) clazz, v);
        }
    }

    @Environment(EnvType.CLIENT)
    public class DangerousListEntry extends ListEntry<String> {
        public DangerousListEntry(ConfigEntryUnit<List<String>> configEntryUnit) {
            super(configEntryUnit, String.class, Function.identity(), Function.identity());
        }

        @Override
        Screen makeEditScreen(Component titleIn, Class<?> type, List<String> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<String>> onSave) {
            // displays a warning screen when editing a list of unknown type before allowing the edit
            return new ConfirmScreen(result -> {
                if (result) {
                    ConfigScreen.this.minecraft.setScreen(super.makeEditScreen(titleIn, type, currentValue, valueSpec, onSave));
                } else {
                    ConfigScreen.this.minecraft.setScreen(ConfigScreen.this);
                }
            }, new TranslatableComponent("configured.gui.message.dangerous.title").withStyle(ChatFormatting.RED), new TranslatableComponent("configured.gui.message.dangerous.text"), CommonComponents.GUI_PROCEED, CommonComponents.GUI_BACK) {

                @Override
                public void renderDirtBackground(int vOffset) {
                    ConfigScreen.renderDirtBackground(this, ConfigScreen.this.background, vOffset);
                }

            };
        }
    }

    @Environment(EnvType.CLIENT)
    public class ConfigList extends ContainerObjectSelectionList<Entry> {
        public ConfigList(List<ConfigScreen.Entry> entries) {
            super(ConfigScreen.this.minecraft, ConfigScreen.this.width, ConfigScreen.this.height, 50, ConfigScreen.this.height - 36, 24);
            entries.forEach(this::addEntry);
        }

        @Override
        public int getRowLeft() {
            return super.getRowLeft();
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 144;
        }

        @Override
        public int getRowWidth() {
            return ConfigScreen.this.configListRowWidth;
        }

        @Override
        public void replaceEntries(Collection<ConfigScreen.Entry> entries) {
            super.replaceEntries(entries);
            // important when clearing search
            this.setScrollAmount(0.0);
        }

        private void renderToolTips(PoseStack poseStack, int mouseX, int mouseY) {
            if (ConfigScreen.this.configList != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.configList.getRowLeft() + ConfigScreen.this.configList.getRowWidth() - 67) {
                ConfigScreen.Entry entry = this.getEntryAtPosition(mouseX, mouseY);
                if (entry != null) {
                    ConfigScreen.this.setActiveTooltip(entry.getTooltip());
                }
            }
            this.children().forEach(entry ->
            {
                entry.children().forEach(o ->
                {
                    if (o instanceof AbstractSliderButton) {
                        ((AbstractSliderButton) o).renderToolTip(poseStack, mouseX, mouseY);
                    }
                });
            });
        }

        /**
         * Literally just a copy of the original since the background can't be changed
         *
         * @param poseStack    the current matrix stack
         * @param mouseX       the current mouse x position
         * @param mouseY       the current mouse y position
         * @param partialTicks the partial ticks
         */
        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
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
            if (maxScroll > 0) {
                RenderSystem.disableTexture();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                int scrollBarStartY = (int) ((float) ((this.y1 - this.y0) * (this.y1 - this.y0)) / (float) this.getMaxPosition());
                scrollBarStartY = Mth.clamp(scrollBarStartY, 32, this.y1 - this.y0 - 8);
                int scrollBarEndY = (int) this.getScrollAmount() * (this.y1 - this.y0 - scrollBarStartY) / maxScroll + this.y0;
                if (scrollBarEndY < this.y0) {
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
    public class ConfigSearchBox extends EditBox {

        private static final MutableComponent SEARCH_COMPONENT = new TranslatableComponent("configured.gui.search").withStyle(ChatFormatting.GRAY);

        public ConfigSearchBox(Font font, int x, int y, int width, int height, Component value) {
            super(font, x, y, width, height, value);
        }

        @Override
        public void setFocus(boolean focused) {
            super.setFocus(focused);
            ConfigScreen.this.activeTextField = focused ? this : null;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // left click clears text
            // only works for search field as entries inside of vanilla selection lists seem to be unable to handle left clicks properly
            if (this.isVisible() && button == 1) {
                this.setValue("");
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTime) {
            super.renderButton(poseStack, mouseX, mouseY, partialTime);
            if (this.isVisible() && !this.isFocused() && this.getValue().isEmpty()) {
                ConfigScreen.this.font.draw(poseStack, SEARCH_COMPONENT, this.x + 4, this.y + 6, 16777215);
            }
        }
    }

    public static ConfigScreen create(Screen lastScreen, Component title, ResourceLocation background, Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs) {
        return new ConfigScreen.Main(lastScreen, title, background, typeToSpecs, makeValueToUnitMap(mergeValues(typeToSpecs.values())));
    }

    private static Map<Object, IEntryUnit> makeValueToUnitMap(List<ForgeConfigSpec> specs) {
        Map<Object, IEntryUnit> allUnits = Maps.newHashMap();
        specs.forEach(spec -> makeValueToUnitMap(spec, spec.getValues(), allUnits));
        return ImmutableMap.copyOf(allUnits);
    }

    private static void makeValueToUnitMap(ForgeConfigSpec spec, UnmodifiableConfig values, Map<Object, IEntryUnit> allUnits) {
        values.valueMap().forEach((path, value) -> {
            if (value instanceof UnmodifiableConfig configValue) {
                allUnits.put(configValue, new CategoryEntryUnit(ConfigScreen.formatLabel(path), configValue));
                makeValueToUnitMap(spec, configValue, allUnits);
            } else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                allUnits.put(configValue, new ConfigEntryUnit<>(configValue, spec.getRaw(configValue.getPath())));
            }
        });
    }

    private static <T, R extends Collection<T>> List<T> mergeValues(Collection<R> toMerge) {
        return toMerge.stream()
                .flatMap(Collection::stream)
                .toList();
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
    private static Component createLabel(ForgeConfigSpec.ConfigValue<?> configValue, ForgeConfigSpec.ValueSpec valueSpec) {
        if (valueSpec.getTranslationKey() != null && I18n.exists(valueSpec.getTranslationKey())) {
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
    public static Component formatLabel(String input) {
        if (input == null || input.isEmpty()) {
            return new TextComponent("");
        }
        // Try split by camel case
        String[] words = input.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for (int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        input = Strings.join(words, " ");
        // Try split by underscores
        words = input.split("_");
        for (int i = 0; i < words.length; i++) words[i] = StringUtils.capitalize(words[i]);
        // Finally join words. Some mods have inputs like "Foo_Bar" and this causes a double space.
        // To fix this any whitespace is replaced with a single space
        return new TextComponent(Strings.join(words, " ").replaceAll("\\s++", " "));
    }
}
