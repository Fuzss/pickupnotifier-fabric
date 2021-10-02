package com.mrcrayfish.configured.client.screen;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.Configured;
import com.mrcrayfish.configured.client.screen.util.ScreenUtil;
import com.mrcrayfish.configured.client.screen.widget.ConfigEditBox;
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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
    final Map<Object, IEntryData> valueToData;
    private ConfigList list;
    EditBox searchTextField;
    @Nullable
    private ConfigEditBox activeTextField;
    @Nullable
    private List<FormattedCharSequence> activeTooltip;
    private int tooltipTicks;

    private ConfigScreen(Screen lastScreen, Component title, ResourceLocation background, Map<Object, IEntryData> valueToData) {
        super(title);
        this.lastScreen = lastScreen;
        this.background = background;
        this.valueToData = valueToData;
    }

    private static class Main extends ConfigScreen {

        /**
         * entries used when searching
         * includes entries from this screen {@link #screenEntries} and from all sub screens of this
         * type is for separators
         */
        private final Map<ModConfig.Type, List<IEntryData>> searchEntries;
        /**
         * default entries shown on screen when not searching
         * type is for separators
         */
        private final Map<ModConfig.Type, List<IEntryData>> screenEntries;
        /**
         * only used for saving when closing screen
         */
        private final List<ForgeConfigSpec> configSpecs;
        // done, cancel, restore, back (back only appears when search field is not empty)
        private final Button[] buttons = new Button[4];

        private Main(Screen lastScreen, Component title, ResourceLocation background, Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs, Map<Object, IEntryData> valueToData) {
            super(lastScreen, title, background, valueToData);
            this.searchEntries = this.gatherEntriesRecursive(typeToSpecs, valueToData);
            this.screenEntries = typeToSpecs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                            .flatMap(spec -> spec.getValues().valueMap().values().stream())
                            .map(valueToData::get)
                            .toList(), (o1, o2) -> o1, () -> Maps.newEnumMap(ModConfig.Type.class)));
            this.configSpecs = mergeValues(typeToSpecs.values());
            this.buildSubScreens(mergeValues(this.screenEntries.values()));
        }

        @SuppressWarnings("UnstableApiUsage")
        private Map<ModConfig.Type, List<IEntryData>> gatherEntriesRecursive(Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs, Map<Object, IEntryData> allEntries) {
            Map<ModConfig.Type, List<IEntryData>> entries = Maps.newEnumMap(ModConfig.Type.class);
            typeToSpecs.forEach((type, specs) -> {
                final List<IEntryData> typeEntries = entries.computeIfAbsent(type, key -> Lists.newArrayList());
                specs.forEach(spec -> this.gatherEntriesRecursive(spec.getValues(), typeEntries, allEntries));
            });
            return Maps.immutableEnumMap(entries);
        }

        @Override
        public List<ConfigScreen.Entry> getConfigListEntries(final String query) {
            return this.getConfigListEntries(!query.isEmpty() ? this.searchEntries : this.screenEntries, query);
        }

        private List<ConfigScreen.Entry> getConfigListEntries(Map<ModConfig.Type, List<IEntryData>> entries, final String query) {
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
            return new TitleEntry(title, description);
        }

        @Override
        protected void init() {
            super.init();
            this.buttons[0] = this.addRenderableWidget(new Button(this.width / 2 - 50 - 105, this.height - 28, 100, 20, CommonComponents.GUI_DONE, button -> {
                this.valueToData.values().forEach(IEntryData::saveConfigValue);
                this.configSpecs.forEach(ForgeConfigSpec::save);
                this.minecraft.setScreen(this.lastScreen);
            }));
            this.buttons[1] = this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 28, 100, 20, CommonComponents.GUI_CANCEL, button -> this.onClose()));
            this.buttons[2] = this.addRenderableWidget(new Button(this.width / 2 - 50 + 105, this.height - 28, 100, 20, new TranslatableComponent("configured.gui.restore"), button -> {
                Screen confirmScreen = this.makeConfirmationScreen(result -> {
                    if (result) {// Resets all config values
                        this.valueToData.values().forEach(IEntryData::resetCurrentValue);
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
                restoreButton.active = this.valueToData.values().stream().anyMatch(IEntryData::mayResetValue);
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
                if (this.valueToData.values().stream().allMatch(IEntryData::mayDiscardChanges)) {
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
        private final List<IEntryData> searchEntries;
        /**
         * default entries shown on screen when not searching
         */
        private final List<IEntryData> screenEntries;

        private Sub(ConfigScreen lastScreen, Component title, UnmodifiableConfig config) {
            super(lastScreen, title, lastScreen.background, lastScreen.valueToData);
            this.searchEntries = this.gatherEntriesRecursive(config, lastScreen.valueToData);
            this.screenEntries = config.valueMap().values().stream().map(lastScreen.valueToData::get).toList();
            this.buildSubScreens(this.screenEntries);
        }

        private List<IEntryData> gatherEntriesRecursive(UnmodifiableConfig mainConfig, Map<Object, IEntryData> allEntries) {
            List<IEntryData> entries = Lists.newArrayList();
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
            for (int i = 0, size = Math.min(maxSize, lastScreens.size()); i < size; i++) {
                Screen screen = lastScreens.get(size - 1 - i);
                final boolean otherScreen = screen != this;
                final Component title = i == 0 && lastScreens.size() > maxSize ? new TextComponent(". . .") : screen.getTitle();
                buttons.add(new Button(0, 1, Sub.this.font.width(title) + 4, 20, title, button -> {
                    if (otherScreen) {
                        Sub.this.minecraft.setScreen(screen);
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
        this.list = new ConfigList(this.getConfigListEntries(""));
        this.addWidget(this.list);
        this.searchTextField = new ConfigEditBox(this.font, this.width / 2 - 109, 22, 218, 20, () -> this.activeTextField, activeTextField -> this.activeTextField = activeTextField);
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
        if (this.list != null) {
            this.list.replaceEntries(this.getConfigListEntries(query.toLowerCase(Locale.ROOT).trim()));
        }
    }

    void onSearchFieldChanged(boolean isEmpty) {
        // sets bottom buttons visibility
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
        this.list.render(poseStack, mouseX, mouseY, partialTicks);
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
        ScreenUtil.renderCustomBackground(this, this.background, vOffset);
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

    void gatherEntriesRecursive(UnmodifiableConfig mainConfig, List<IEntryData> entries, Map<Object, IEntryData> allEntries) {
        mainConfig.valueMap().values().forEach(value -> {
            entries.add(allEntries.get(value));
            if (value instanceof UnmodifiableConfig config) {
                this.gatherEntriesRecursive(config, entries, allEntries);
            }
        });
    }

    abstract List<ConfigScreen.Entry> getConfigListEntries(String query);

    List<ConfigScreen.Entry> getConfigListEntries(List<IEntryData> entries, final String query) {
        return entries.stream()
                // set query to units so the can highlight matches
                .peek(unit -> unit.setSearchQuery(query))
                .filter(IEntryData::containsSearchQuery)
                .sorted()
                .map(this::makeEntry)
                // there might be an unsupported value which will return null
                .filter(Objects::nonNull)
                .toList();
    }

    void buildSubScreens(List<IEntryData> screenEntries) {
        // every screen must build their own direct sub screens so when searching we can jump between screens in their actual hierarchical order
        // having screens stored like this is ok as everything else will be reset during init when the screen is opened anyways
        for (IEntryData unit : screenEntries) {
            if (unit instanceof CategoryEntryData categoryEntryData) {
                categoryEntryData.setScreen(new Sub(this, categoryEntryData.getTitle(), categoryEntryData.getConfig()));
            }
        }
    }

    void setActiveTooltip(@Nullable List<FormattedCharSequence> activeTooltip) {
        this.activeTooltip = activeTooltip;
    }

    @SuppressWarnings("unchecked")
    Entry makeEntry(IEntryData entryData) {
        if (entryData instanceof CategoryEntryData categoryEntryData) {
            return new CategoryEntry(categoryEntryData.getDisplayTitle(), categoryEntryData.getScreen());
        } else if (entryData instanceof ConfigEntryData<?> configEntryData) {
            final Object currentValue = configEntryData.getCurrentValue();
            if (currentValue instanceof Boolean) {
                return new BooleanEntry((ConfigEntryData<Boolean>) entryData);
            } else if (currentValue instanceof Integer) {
                return new NumberEntry<>((ConfigEntryData<Integer>) entryData, Integer::parseInt);
            } else if (currentValue instanceof Double) {
                return new NumberEntry<>((ConfigEntryData<Double>) entryData, Double::parseDouble);
            } else if (currentValue instanceof Long) {
                return new NumberEntry<>((ConfigEntryData<Long>) entryData, Long::parseLong);
            } else if (currentValue instanceof Enum<?>) {
                return new EnumEntry((ConfigEntryData<Enum<?>>) entryData);
            } else if (currentValue instanceof String) {
                return new StringEntry((ConfigEntryData<String>) entryData);
            } else if (currentValue instanceof List<?> listValue) {
                Object value = this.getListValue(((ConfigEntryData<List<?>>) entryData).getDefaultValue(), listValue);
                try {
                    return this.makeListEntry(entryData, value);
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
    private ListEntry<?> makeListEntry(IEntryData entryData, Object value) throws RuntimeException {
        if (value instanceof Boolean) {
            return new ListEntry<>((ConfigEntryData<List<Boolean>>) entryData, Boolean.class, v -> switch (v.toLowerCase(Locale.ROOT)) {
                case "true" -> true;
                case "false" -> false;
                // is caught when editing
                default -> throw new IllegalArgumentException("unable to convert boolean value");
            });
        } else if (value instanceof Integer) {
            return new ListEntry<>((ConfigEntryData<List<Integer>>) entryData, Integer.class, Integer::parseInt);
        } else if (value instanceof Double) {
            return new ListEntry<>((ConfigEntryData<List<Double>>) entryData, Double.class, Double::parseDouble);
        } else if (value instanceof Long) {
            return new ListEntry<>((ConfigEntryData<List<Long>>) entryData, Long.class, Long::parseLong);
        } else if (value instanceof Enum<?>) {
            return new EnumListEntry((ConfigEntryData<List<Enum<?>>>) entryData, (Class<Enum<?>>) value.getClass());
        } else if (value instanceof String) {
            return new ListEntry<>((ConfigEntryData<List<String>>) entryData, String.class, s -> {
                if (s.isEmpty()) {
                    throw new IllegalArgumentException("string must not be empty");
                }
                return s;
            });
        } else {
            // string list with warning screen
            return new DangerousListEntry((ConfigEntryData<List<String>>) entryData);
        }
    }

    @Nullable
    private Object getListValue(List<?> defaultValue, List<?> currentValue) {
        // desperate attempt to somehow get some generic information out of a list
        // checking default values first is important as current values might be of a different type due to how configs are read
        // example: enum are read as strings, longs as integers
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
                ScreenUtil.renderCustomBackground(this, ConfigScreen.this.background, vOffset);
            }
        };
    }

    interface IEntryData extends Comparable<EntryData> {

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
        default int compareTo(@NotNull ConfigScreen.EntryData other) {
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

    static class EntryData implements IEntryData {

        private final Component title;
        private String searchQuery = "";

        public EntryData(Component title) {
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

    static class CategoryEntryData extends EntryData {

        private final UnmodifiableConfig config;
        private ConfigScreen screen;

        public CategoryEntryData(Component title, UnmodifiableConfig config) {
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

    static class ConfigEntryData<T> extends EntryData {

        private final ForgeConfigSpec.ConfigValue<T> configValue;
        private final ForgeConfigSpec.ValueSpec valueSpec;
        private T currentValue;

        public ConfigEntryData(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec) {
            super(ConfigScreen.createLabel(configValue, valueSpec));
            this.configValue = configValue;
            this.valueSpec = valueSpec;
            this.currentValue = configValue.get();
        }

        @Override
        public boolean mayResetValue() {
            return !listSafeEquals(this.currentValue, this.getDefaultValue());
        }

        @Override
        public boolean mayDiscardChanges() {
            return listSafeEquals(this.configValue.get(), this.currentValue);
        }

        private static <T> boolean listSafeEquals(T o1, T o2) {
            // attempts to solve an issue where types of lists won't match when one is read from file
            if (o1 instanceof List<?> list1 && o2 instanceof List<?> list2) {
                final Stream<String> stream1 = list1.stream().map(o -> o instanceof Enum<?> e ? e.name() : o.toString());
                final Stream<String> stream2 = list2.stream().map(o -> o instanceof Enum<?> e ? e.name() : o.toString());
                return Iterators.elementsEqual(stream1.iterator(), stream2.iterator());
            }
            return o1.equals(o2);
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

            if (ConfigScreen.this.list != null && this.isMouseOver(mouseX, mouseY) && mouseX < ConfigScreen.this.list.getRowLeft() + ConfigScreen.this.list.getRowWidth() - 67) {
                ConfigScreen.this.setActiveTooltip(this.tooltip);
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
                return font.plainSubstrByWidth(component, maxWidth - font.width(". . .")) + ". . .";
            } else {
                return component;
            }
        }

        FormattedText getTruncatedText(Font font, Component component, int maxWidth, Style style) {
            // trim component when too long
            if (font.width(component) > maxWidth) {
                return FormattedText.composite(font.getSplitter().headByWidth(component, maxWidth - font.width(". . ."), style), FormattedText.of(". . ."));
            } else {
                return component;
            }
        }
    }

    public class TitleEntry extends Entry {
        public TitleEntry(Component title, List<FormattedCharSequence> description) {
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
            final FormattedText truncatedText = this.getTruncatedText(ConfigScreen.this.font, title, 260 - 4, Style.EMPTY.withBold(true));
            this.button = new Button(10, 5, 260, 20, new TextComponent(truncatedText.getString()).withStyle(ChatFormatting.BOLD), button -> ConfigScreen.this.minecraft.setScreen(screen));
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
        private final ConfigEntryData<T> configEntryData;
        private final FormattedCharSequence visualTitle;
        final Button resetButton;

        public ConfigEntry(ConfigEntryData<T> configEntryData) {
            this(configEntryData, Object::toString);
        }

        public ConfigEntry(ConfigEntryData<T> configEntryData, Function<T, String> toString) {
            // default value converter (toString) is necessary for enum values (issue is visible when handling chatformatting values which would otherwise be converted to their corresponding formatting and therefore not display)
            super(configEntryData.getDisplayTitle(), makeTooltip(ConfigScreen.this.font, configEntryData, toString));
            this.configEntryData = configEntryData;
            FormattedText truncatedTitle = this.getTruncatedText(ConfigScreen.this.font, this.getTitle(), 260 - 70, Style.EMPTY);
            this.visualTitle = Language.getInstance().getVisualOrder(truncatedTitle);
            final List<FormattedCharSequence> formattedCharSequences = ConfigScreen.this.font.split(new TranslatableComponent("configured.gui.tooltip.reset"), 200);
            this.resetButton = new IconButton(0, 0, 20, 20, 0, 0, button -> {
                configEntryData.resetCurrentValue();
                this.onConfigValueChanged(configEntryData.getCurrentValue(), true);
            }, (button, matrixStack, mouseX, mouseY) -> {
                if (button.active) {
                    ConfigScreen.this.setActiveTooltip(formattedCharSequences);
                }
            });
            this.resetButton.active = configEntryData.mayResetValue();
            this.children.add(this.resetButton);
        }

        public void onConfigValueChanged(T newValue, boolean reset) {
            this.resetButton.active = this.configEntryData.mayResetValue();
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
                    String comment = ConfigEntry.this.configEntryData.getValueSpec().getComment();
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

        static <T> List<FormattedCharSequence> makeTooltip(Font font, ConfigEntryData<T> configEntryData, Function<T, String> toString) {
            return makeTooltip(font, configEntryData.getPath(), configEntryData.getValueSpec().getComment(), toString.apply(configEntryData.getDefaultValue()), configEntryData.withPath());
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
        private final ConfigEditBox textField;

        public NumberEntry(ConfigEntryData<T> configEntryData, Function<String, T> parser) {
            super(configEntryData);
            this.textField = new ConfigEditBox(ConfigScreen.this.font, 0, 0, 42, 18, () -> ConfigScreen.this.activeTextField, activeTextField -> ConfigScreen.this.activeTextField = activeTextField);
            this.textField.setResponder(input -> {
                T number = null;
                try {
                    T parsed = parser.apply(input);
                    if (configEntryData.getValueSpec().test(parsed)) {
                        number = parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
                if (number != null) {
                    this.textField.markInvalid(false);
                    configEntryData.setCurrentValue(number);
                    this.onConfigValueChanged(number, false);
                } else {
                    this.textField.markInvalid(true);
                    configEntryData.resetCurrentValue();
                    // provides an easy way to make text field usable again, even though default value is already set in background
                    this.resetButton.active = true;
                    ConfigScreen.this.updateRestoreButton();
                }
            });
            this.textField.setValue(configEntryData.getCurrentValue().toString());
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

        public BooleanEntry(ConfigEntryData<Boolean> configEntryData) {
            super(configEntryData);
            this.button = new Button(10, 5, 44, 20, CommonComponents.optionStatus(configEntryData.getCurrentValue()), button -> {
                final boolean newValue = !configEntryData.getCurrentValue();
                configEntryData.setCurrentValue(newValue);
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

        public EditScreenEntry(ConfigEntryData<T> configEntryData, Function<T, String> toString, Class<?> type) {
            super(configEntryData, toString);
            this.button = new Button(10, 5, 44, 20, new TranslatableComponent("configured.gui.edit"), button -> {
                // safety precaution for dealing with lists
                try {
                    ConfigScreen.this.minecraft.setScreen(this.makeEditScreen(type.getSimpleName(), configEntryData.getCurrentValue(), configEntryData.getValueSpec(), currentValue -> {
                        configEntryData.setCurrentValue(currentValue);
                        this.onConfigValueChanged(currentValue, false);
                    }));
                } catch (RuntimeException e) {
                    Configured.LOGGER.warn("Unable to handle list entry containing class type {}", type.getSimpleName(), e);
                    button.active = false;
                }
            });
            this.children().add(this.button);
        }

        abstract Screen makeEditScreen(String type, T currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<T> onSave);

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
        public EnumEntry(ConfigEntryData<Enum<?>> configEntryData) {
            super(configEntryData, v -> formatLabel(v.name().toLowerCase(Locale.ROOT)).getString(), Enum.class);
        }

        @Override
        Screen makeEditScreen(String type, Enum<?> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<Enum<?>> onSave) {
            return new EditEnumScreen(ConfigScreen.this, new TranslatableComponent("configured.gui.value.select", type), ConfigScreen.this.background, currentValue, currentValue.getDeclaringClass().getEnumConstants(), valueSpec::test, onSave);
        }
    }

    @Environment(EnvType.CLIENT)
    public class StringEntry extends EditScreenEntry<String> {
        public StringEntry(ConfigEntryData<String> configEntryData) {
            super(configEntryData, Function.identity(), String.class);
        }

        @Override
        Screen makeEditScreen(String type, String currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<String> onSave) {
            return new EditStringScreen(ConfigScreen.this, new TranslatableComponent("configured.gui.value.edit", type), ConfigScreen.this.background, currentValue, valueSpec::test, onSave);
        }
    }

    @Environment(EnvType.CLIENT)
    public class ListEntry<T> extends EditScreenEntry<List<T>> {
        private final Function<Object, String> toString;
        private final Function<String, T> fromString;

        public ListEntry(ConfigEntryData<List<T>> configEntryData, Class<T> type, Function<String, T> fromString) {
            this(configEntryData, type, Object::toString, fromString);
        }

        public ListEntry(ConfigEntryData<List<T>> configEntryData, Class<T> type, Function<Object, String> toString, Function<String, T> fromString) {
            super(configEntryData, v -> "[" + v.stream().map(toString).collect(Collectors.joining(", ")) + "]", type);
            this.toString = toString;
            this.fromString = fromString;
        }

        @Override
        Screen makeEditScreen(String type, List<T> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<T>> onSave) {
            return new EditListScreen(ConfigScreen.this, new TranslatableComponent("configured.gui.list.edit", type), ConfigScreen.this.background, currentValue.stream()
                    .map(this.toString)
                    .collect(Collectors.toList()), input -> {
                try {
                    this.fromString.apply(input);
                    return true;
                } catch (RuntimeException ignored) {
                }
                return false;
            }, list -> {
                final List<T> values = list.stream()
                        .map(this.fromString)
                        .collect(Collectors.toList());
                valueSpec.correct(valueSpec);
                onSave.accept(values);
            });
        }
    }

    public class EnumListEntry extends ListEntry<Enum<?>> {

        // mainly here to enable unchecked cast
        @SuppressWarnings("unchecked")
        public <T extends Enum<T>> EnumListEntry(ConfigEntryData<List<Enum<?>>> configEntryData, Class<Enum<?>> clazz) {
            // enums are read as strings from file
            super(configEntryData, clazz, v -> v instanceof Enum<?> e ? e.name() : v.toString(), v -> Enum.valueOf((Class<T>) clazz, v));
        }
    }

    @Environment(EnvType.CLIENT)
    public class DangerousListEntry extends ListEntry<String> {
        public DangerousListEntry(ConfigEntryData<List<String>> configEntryData) {
            super(configEntryData, String.class, s -> {
                if (s.isEmpty()) {
                    throw new IllegalArgumentException("string must not be empty");
                }
                return s;
            });
        }

        @Override
        Screen makeEditScreen(String type, List<String> currentValue, ForgeConfigSpec.ValueSpec valueSpec, Consumer<List<String>> onSave) {
            // displays a warning screen when editing a list of unknown type before allowing the edit
            return new ConfirmScreen(result -> {
                if (result) {
                    ConfigScreen.this.minecraft.setScreen(super.makeEditScreen(type, currentValue, valueSpec, onSave));
                } else {
                    ConfigScreen.this.minecraft.setScreen(ConfigScreen.this);
                }
            }, new TranslatableComponent("configured.gui.message.dangerous.title").withStyle(ChatFormatting.RED), new TranslatableComponent("configured.gui.message.dangerous.text"), CommonComponents.GUI_PROCEED, CommonComponents.GUI_BACK) {

                @Override
                public void renderDirtBackground(int vOffset) {
                    ScreenUtil.renderCustomBackground(this, ConfigScreen.this.background, vOffset);
                }

            };
        }
    }

    @Environment(EnvType.CLIENT)
    public class ConfigList extends CustomBackgroundContainerObjectSelectionList<Entry> {
        public ConfigList(List<ConfigScreen.Entry> entries) {
            super(ConfigScreen.this.minecraft, ConfigScreen.this.background, ConfigScreen.this.width, ConfigScreen.this.height, 50, ConfigScreen.this.height - 36, 24);
            entries.forEach(this::addEntry);
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
        protected void replaceEntries(Collection<ConfigScreen.Entry> entries) {
            super.replaceEntries(entries);
            // important when clearing search
            this.setScrollAmount(0.0);
        }

        @Override
        public void render(PoseStack poseStack, int i, int j, float f) {
            super.render(poseStack, i, j, f);
            ConfigScreen.Entry entry = this.getHovered();
            if (entry != null) {
                ConfigScreen.this.setActiveTooltip(entry.getTooltip());
            }
        }
    }

    public static ConfigScreen create(Screen lastScreen, Component title, ResourceLocation background, Map<ModConfig.Type, List<ForgeConfigSpec>> typeToSpecs) {
        return new ConfigScreen.Main(lastScreen, title, background, typeToSpecs, makeValueToDataMap(mergeValues(typeToSpecs.values())));
    }

    private static Map<Object, IEntryData> makeValueToDataMap(List<ForgeConfigSpec> specs) {
        Map<Object, IEntryData> allData = Maps.newHashMap();
        specs.forEach(spec -> makeValueToDataMap(spec, spec.getValues(), allData));
        return ImmutableMap.copyOf(allData);
    }

    private static void makeValueToDataMap(ForgeConfigSpec spec, UnmodifiableConfig values, Map<Object, IEntryData> allData) {
        values.valueMap().forEach((path, value) -> {
            if (value instanceof UnmodifiableConfig configValue) {
                allData.put(configValue, new CategoryEntryData(ConfigScreen.formatLabel(path), configValue));
                makeValueToDataMap(spec, configValue, allData);
            } else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                allData.put(configValue, new ConfigEntryData<>(configValue, spec.getRaw(configValue.getPath())));
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
