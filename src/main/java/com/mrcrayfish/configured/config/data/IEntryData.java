package com.mrcrayfish.configured.config.data;


import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mrcrayfish.configured.client.gui.data.EntryData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface IEntryData extends Comparable<IEntryData> {

    String getPath();

    @Nullable
    String getComment();

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

    void discardCurrentValue();

    /**
     * save to actual config, called when pressing done
     */
    void saveConfigValue();

    @Override
    default int compareTo(IEntryData other) {
        Comparator<IEntryData> comparator = Comparator.comparing(o -> o.getTitle().getString());
        if (this.withPath()) {
            // when searching sort by index of query, only if both match sort alphabetically
            comparator = Comparator.<IEntryData>comparingInt(o -> o.getSearchableTitle().indexOf(o.getSearchQuery())).thenComparing(comparator);
        }
        return comparator.compare(this, other);
    }

    static Map<Object, IEntryData> makeValueToDataMap(ModConfig config) {
        if (checkInvalid(config)) {
            return ImmutableMap.of();
        }
        Map<Object, IEntryData> allData = Maps.newHashMap();
        ForgeConfigSpec spec = (ForgeConfigSpec) config.getSpec();
        makeValueToDataMap(spec, spec.getValues(), config.getConfigData(), allData);
        return ImmutableMap.copyOf(allData);
    }

    static boolean checkInvalid(ModConfig config) {
        return config.getConfigData() == null || !(config.getSpec() instanceof ForgeConfigSpec spec) || !spec.isLoaded();
    }

    private static void makeValueToDataMap(ForgeConfigSpec spec, UnmodifiableConfig values, CommentedConfig comments, Map<Object, IEntryData> allData) {
        values.valueMap().forEach((path, value) -> {
            if (value instanceof UnmodifiableConfig category) {
                final EntryData.CategoryEntryData data = new EntryData.CategoryEntryData(path, category, comments.getComment(path));
                allData.put(category, data);
                makeValueToDataMap(spec, category, (CommentedConfig) comments.valueMap().get(path), allData);
            } else if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                final EntryData.ConfigEntryData<?> data = new EntryData.ConfigEntryData<>(path, configValue, spec.getRaw(configValue.getPath()));
                allData.put(configValue, data);
            }
        });
    }
}