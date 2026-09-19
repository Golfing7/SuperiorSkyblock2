package com.bgsoftware.superiorskyblock.external.spawners;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.events.IslandWorthCalculatedEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.superiorskyblock.core.key.Keys;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.golfing8.dcore.module.spawner.SpawnerModuleConf;
import com.golfing8.dcore.module.spawner.api.SpawnerStackAddEvent;
import com.golfing8.dcore.module.spawner.api.SpawnerStackBreakEvent;
import com.golfing8.dcore.module.spawner.api.SpawnerStackCreateEvent;
import com.golfing8.dcore.module.spawner.struct.LeveledSpawnerEntity;
import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SpawnersProvider_DecypherCore implements SpawnersProvider_AutoDetect {

    private final SuperiorSkyblockPlugin plugin;
    /**
     * Islands that currently have at least one spawner whose worth is being scaled down by level.
     * Only populated/used when {@code spawners-worth-scaled-by-level} is enabled. Main-thread only.
     */
    private final Set<Island> islandsWithScaledSpawners = new LinkedHashSet<>();
    private int resyncCursor = 0;

    public SpawnersProvider_DecypherCore(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new StackerListener(), plugin);
        Log.info("Using DecypherCore as a spawners provider.");

        if (plugin.getSettings().isSpawnerWorthScaledByLevel()) {
            long intervalTicks = Math.max(20L, plugin.getSettings().getSpawnerWorthResyncIntervalSeconds() * 20L);
            Bukkit.getScheduler().runTaskTimer(plugin, this::resyncBoostedSpawnersBatch, intervalTicks, intervalTicks);
        }
    }

    @Override
    public int getSpawnerLevel(Location location) {
        // Loading a spawner touches block/chunk state, which is only safe on the main thread.
        if (!Bukkit.isPrimaryThread())
            return -1;

        return LeveledSpawnerEntity.load(location).map(LeveledSpawnerEntity::getLevel).orElse(-1);
    }

    @Override
    public int getMaxSpawnerLevel(Location location) {
        return SpawnerModuleConf.upgradeLevels.size();
    }

    /**
     * Passively catches in-place spawner level upgrades (which don't fire a stack event) by
     * re-running the normal, existing worth recalculation for a small, rotating batch of islands
     * that are known to have scaled spawners - never a server-wide recalculation.
     */
    private void resyncBoostedSpawnersBatch() {
        if (islandsWithScaledSpawners.isEmpty())
            return;

        List<Island> snapshot = new ArrayList<>(islandsWithScaledSpawners);
        int batchSize = Math.min(plugin.getSettings().getSpawnerWorthResyncBatchSize(), snapshot.size());
        for (int i = 0; i < batchSize; i++) {
            if (resyncCursor >= snapshot.size())
                resyncCursor = 0;

            Island island = snapshot.get(resyncCursor++);
            if (!island.isBeingRecalculated())
                island.calcIslandWorth(null);
        }
    }

    @Override
    public Pair<Integer, String> getSpawner(Location location) {
        Preconditions.checkNotNull(location, "location parameter cannot be null.");

        int blockCount = -1;

        if (Bukkit.isPrimaryThread()) {
            Optional<LeveledSpawnerEntity> stackedSpawner = LeveledSpawnerEntity.load(location);
            blockCount = stackedSpawner.map(LeveledSpawnerEntity::getStackCount).orElse(1);
        }

        return new Pair<>(blockCount, null);
    }

    @Override
    public String getSpawnerType(ItemStack itemStack) {
        return LeveledSpawnerEntity.load(itemStack).map(spawner -> spawner.getCreatureSpawner().getSpawnedType().name()).orElse(null);
    }

    @SuppressWarnings("unused")
    private class StackerListener implements Listener {

        /**
         * Called after every island worth recalculation. Tracks which islands actually have scaled
         * spawners so the periodic resync above only ever touches those, not every island on the server.
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorthCalculated(IslandWorthCalculatedEvent event) {
            Island island = event.getIsland();
            if (island.getSpawnerWorthAdjustment().compareTo(BigDecimal.ZERO) != 0) {
                islandsWithScaledSpawners.add(island);
            } else {
                islandsWithScaledSpawners.remove(island);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSpawnerStack(SpawnerStackCreateEvent e) {
            Location location = e.getLocation();
            Island island = plugin.getGrid().getIslandAt(location);
            if (island != null) {
                Key blockKey = Keys.of(e.getLocation().getBlock());

                int increaseAmount = e.getPlacedEntity().getStackCount();

                if (island.hasReachedBlockLimit(blockKey, increaseAmount)) {
                    e.setCancelled(true);
                } else {
                    island.handleBlockPlace(blockKey, increaseAmount);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSpawnerStackAdd(SpawnerStackAddEvent e) {
            Location location = e.getMergedLocation();
            Island island = plugin.getGrid().getIslandAt(location);
            if (island != null) {
                Key blockKey = Keys.of(e.getMergedLocation().getBlock());

                int increaseAmount = e.getAmountAdded();
                if (island.hasReachedBlockLimit(blockKey, increaseAmount)) {
                    e.setCancelled(true);
                } else {
                    island.handleBlockPlace(blockKey, increaseAmount);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSpawnerUnstack(SpawnerStackBreakEvent e) {
            Location location = e.getLocation();
            Island island = plugin.getGrid().getIslandAt(location);
            if (island != null) {
                island.handleBlockBreak(Keys.of(e.getLocation().getBlock()), e.getAmountRemoved());
            }
        }

    }

}
