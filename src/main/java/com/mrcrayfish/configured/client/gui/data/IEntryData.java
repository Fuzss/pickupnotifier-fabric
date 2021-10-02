package com.mrcrayfish.configured.client.gui.data;


import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;

import java.util.List;
import java.util.Locale;

public interface IEntryData extends Comparable<IEntryData> {

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
    default int compareTo(IEntryData other) {
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