package com.mrcrayfish.configured.client.gui.data;


import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.mrcrayfish.configured.client.gui.screens.ConfigScreen;
import com.mrcrayfish.configured.client.gui.util.ScreenUtil;
import com.mrcrayfish.configured.config.data.IEntryData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.stream.Stream;

public class EntryData implements IEntryData {

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
    public void discardCurrentValue() {

    }

    @Override
    public void saveConfigValue() {

    }

    public static class CategoryEntryData extends EntryData {

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

        @Override
        public int compareTo(IEntryData other) {
            // category entries always on top
            return !(other instanceof CategoryEntryData) ? -1 : super.compareTo(other);
        }
    }

    public static class ConfigEntryData<T> extends EntryData {

        private final ForgeConfigSpec.ConfigValue<T> configValue;
        private final ForgeConfigSpec.ValueSpec valueSpec;
        private T currentValue;

        public ConfigEntryData(ForgeConfigSpec.ConfigValue<T> configValue, ForgeConfigSpec.ValueSpec valueSpec) {
            super(createLabel(configValue, valueSpec));
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
        public void discardCurrentValue() {
            this.currentValue = this.configValue.get();
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
            return ScreenUtil.formatLabel(Iterables.getLast(configValue.getPath(), ""));
        }
    }
}