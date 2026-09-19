package com.bgsoftware.superiorskyblock.api.island.algorithms;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.SpawnerLevelCounts;
import com.bgsoftware.superiorskyblock.api.key.Key;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface IslandCalculationAlgorithm {

    /**
     * Calculate the island blocks of the island.
     *
     * @return CompletableFuture instance of the result.
     * @deprecated See {@link #calculateIsland(Island)}
     */
    @Deprecated
    default CompletableFuture<IslandCalculationResult> calculateIsland() {
        throw new UnsupportedOperationException("This method is not supported anymore. Use calculateIsland(Island) instead.");
    }

    /**
     * Calculate the island blocks of the island.
     *
     * @param island The island to calculate blocks for.
     * @return CompletableFuture instance of the result.
     */
    CompletableFuture<IslandCalculationResult> calculateIsland(Island island);

    /**
     * Represents calculation result.
     */
    interface IslandCalculationResult {

        /**
         * Get all block-counts that were calculated.
         */
        Map<Key, BigInteger> getBlockCounts();

        /**
         * Get the total spawner-level worth adjustment calculated for the island (see
         * {@link Island#getSpawnerWorthAdjustment()}). Zero when no leveled spawners were found or
         * the feature is disabled.
         */
        default BigDecimal getSpawnerWorthAdjustment() {
            return BigDecimal.ZERO;
        }

        /**
         * Get the total spawner-level island-level adjustment calculated for the island (see
         * {@link Island#getSpawnerIslandLevelAdjustment()}). Zero when no leveled spawners were found
         * or the feature is disabled.
         */
        default BigDecimal getSpawnerIslandLevelAdjustment() {
            return BigDecimal.ZERO;
        }

        /**
         * Get the spawner level breakdowns calculated for the island, keyed by spawner block key (see
         * {@link Island#getSpawnerLevelCounts()}). Empty when the spawners provider doesn't support
         * leveled spawners.
         */
        default Map<Key, SpawnerLevelCounts> getSpawnerLevelCounts() {
            return Collections.emptyMap();
        }

    }

}
