package com.bgsoftware.superiorskyblock.island;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.SpawnerLevelCounts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Worth and island-level calculations for leveled spawners
 * (see {@code spawners-worth-scaled-by-level} in the config).
 */
public class SpawnerLevelValues {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private SpawnerLevelValues() {

    }

    /**
     * Get the factor a spawner's worth and island-level values are multiplied by at the given level:
     * (level / max-level), or 1 if the level is unknown or maxed.
     */
    public static BigDecimal getValueFactor(int level, int maxLevel) {
        if (level < 1 || maxLevel < 1 || level >= maxLevel)
            return BigDecimal.ONE;

        return BigDecimal.valueOf(level).divide(BigDecimal.valueOf(maxLevel), 10, RoundingMode.HALF_UP);
    }

    /**
     * Get the value (worth or island-level) of {@code amount} spawners at the given level, taking level
     * scaling into account if it's enabled.
     */
    public static BigDecimal getValue(BigDecimal unitValue, BigInteger amount, int level, int maxLevel) {
        BigDecimal value = unitValue.multiply(new BigDecimal(amount));
        return plugin.getSettings().isSpawnerWorthScaledByLevel() ?
                value.multiply(getValueFactor(level, maxLevel)) : value;
    }

    /**
     * Get the total value (worth or island-level) of {@code amount} spawners of a single type, taking
     * level scaling into account if it's enabled. Spawners that are not part of the level breakdown
     * (for example, placed since the last recalculation) are counted at their full value.
     */
    public static BigDecimal getTotalValue(BigDecimal unitValue, BigInteger amount, @Nullable SpawnerLevelCounts levelCounts) {
        BigDecimal fullValue = unitValue.multiply(new BigDecimal(amount));

        if (levelCounts == null || !plugin.getSettings().isSpawnerWorthScaledByLevel())
            return fullValue;

        BigDecimal totalValue = fullValue;
        for (Map.Entry<Integer, BigInteger> entry : levelCounts.getLevelCounts().entrySet()) {
            BigDecimal factor = getValueFactor(entry.getKey(), levelCounts.getMaxLevel());
            totalValue = totalValue.add(unitValue.multiply(new BigDecimal(entry.getValue()))
                    .multiply(factor.subtract(BigDecimal.ONE)));
        }

        // The breakdown may be stale if spawners were broken since the last recalculation.
        return totalValue.max(BigDecimal.ZERO);
    }

}
