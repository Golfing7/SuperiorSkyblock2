package com.bgsoftware.superiorskyblock.core.menu;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.island.SpawnerLevelCounts;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.island.SpawnerLevelValues;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

/**
 * Builds the lore lines showing how many spawners an island has at each level, shared by the
 * counts and values menus.
 */
public class SpawnerLevelsLore {

    private static final String[] NO_LINES = new String[0];

    private SpawnerLevelsLore() {

    }

    /**
     * Build the level breakdown lines of a single spawner type.
     *
     * @param levelCounts The level breakdown, or null if the type has no level data.
     * @param header      The line shown above the breakdown, or an empty string to hide it.
     * @param lineFormat  The format of each level line.
     * @param worthValue  The worth of a single spawner of this type.
     * @param levelValue  The island level value of a single spawner of this type.
     * @param viewer      The player viewing the menu, used for formatting numbers.
     * @return The lines to add to the lore, or an empty array if there's nothing to show.
     */
    public static String[] build(@Nullable SpawnerLevelCounts levelCounts, String header, String lineFormat,
                                 BigDecimal worthValue, BigDecimal levelValue, SuperiorPlayer viewer) {
        if (levelCounts == null || levelCounts.getLevelCounts().isEmpty())
            return NO_LINES;

        int maxLevel = levelCounts.getMaxLevel();
        String maxLevelDisplay = maxLevel < 1 ? "?" : maxLevel + "";

        List<String> lines = new LinkedList<>();

        if (!header.isEmpty())
            lines.add(header.replace("{0}", maxLevelDisplay));

        levelCounts.getLevelCounts().forEach((level, count) -> {
            BigDecimal worth = SpawnerLevelValues.getValue(worthValue, count, level, maxLevel);
            BigDecimal islandLevel = SpawnerLevelValues.getValue(levelValue, count, level, maxLevel);
            lines.add(lineFormat
                    .replace("{0}", level + "")
                    .replace("{1}", count + "")
                    .replace("{2}", Formatters.NUMBER_FORMATTER.format(worth))
                    .replace("{3}", Formatters.FANCY_NUMBER_FORMATTER.format(worth, viewer.getUserLocale()))
                    .replace("{4}", maxLevelDisplay)
                    .replace("{5}", Formatters.NUMBER_FORMATTER.format(islandLevel))
                    .replace("{6}", Formatters.FANCY_NUMBER_FORMATTER.format(islandLevel, viewer.getUserLocale())));
        });

        return lines.toArray(NO_LINES);
    }

}
