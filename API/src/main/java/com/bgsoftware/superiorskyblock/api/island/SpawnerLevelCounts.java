package com.bgsoftware.superiorskyblock.api.island;

import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An immutable breakdown of how many spawners of a single type an island has at each upgrade level.
 * Only available when the spawners provider supports leveled spawners
 * (see {@link com.bgsoftware.superiorskyblock.api.hooks.SpawnersProvider#getSpawnerLevel(org.bukkit.Location)}).
 */
public final class SpawnerLevelCounts {

    private final SortedMap<Integer, BigInteger> levelCounts;
    private final int maxLevel;
    private final BigInteger totalCount;

    /**
     * @param levelCounts The amount of spawners at each level. Levels must be at least 1, and amounts positive.
     * @param maxLevel    The maximum level spawners of this type can reach, or -1 if unknown.
     */
    public SpawnerLevelCounts(Map<Integer, BigInteger> levelCounts, int maxLevel) {
        Preconditions.checkNotNull(levelCounts, "levelCounts parameter cannot be null.");

        SortedMap<Integer, BigInteger> sortedCounts = new TreeMap<>();
        BigInteger totalCount = BigInteger.ZERO;

        for (Map.Entry<Integer, BigInteger> entry : levelCounts.entrySet()) {
            Preconditions.checkArgument(entry.getKey() != null && entry.getKey() >= 1, "levels must be at least 1.");
            Preconditions.checkArgument(entry.getValue() != null && entry.getValue().signum() > 0, "amounts must be positive.");
            sortedCounts.put(entry.getKey(), entry.getValue());
            totalCount = totalCount.add(entry.getValue());
        }

        this.levelCounts = Collections.unmodifiableSortedMap(sortedCounts);
        this.maxLevel = maxLevel;
        this.totalCount = totalCount;
    }

    /**
     * Get the amount of spawners at each level, sorted by level in ascending order.
     */
    public SortedMap<Integer, BigInteger> getLevelCounts() {
        return levelCounts;
    }

    /**
     * Get the amount of spawners at a specific level.
     *
     * @param level The level to check.
     */
    public BigInteger getCount(int level) {
        return levelCounts.getOrDefault(level, BigInteger.ZERO);
    }

    /**
     * Get the total amount of spawners across all levels.
     */
    public BigInteger getTotalCount() {
        return totalCount;
    }

    /**
     * Get the maximum level spawners of this type can reach, or -1 if unknown.
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Combine this breakdown with another one, summing the amounts of each level.
     *
     * @param other The other breakdown.
     * @return A new breakdown containing the counts of both.
     */
    public SpawnerLevelCounts merge(SpawnerLevelCounts other) {
        Preconditions.checkNotNull(other, "other parameter cannot be null.");

        Map<Integer, BigInteger> mergedCounts = new TreeMap<>(this.levelCounts);
        other.levelCounts.forEach((level, count) -> mergedCounts.merge(level, count, BigInteger::add));

        return new SpawnerLevelCounts(mergedCounts, Math.max(this.maxLevel, other.maxLevel));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpawnerLevelCounts that = (SpawnerLevelCounts) o;
        return maxLevel == that.maxLevel && levelCounts.equals(that.levelCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(levelCounts, maxLevel);
    }

    @Override
    public String toString() {
        return "SpawnerLevelCounts{levelCounts=" + levelCounts + ", maxLevel=" + maxLevel + "}";
    }

}
