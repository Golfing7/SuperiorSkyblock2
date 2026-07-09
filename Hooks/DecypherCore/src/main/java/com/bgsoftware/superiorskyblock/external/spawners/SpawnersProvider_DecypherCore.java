package com.bgsoftware.superiorskyblock.external.spawners;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.superiorskyblock.core.key.Keys;
import com.bgsoftware.superiorskyblock.core.logging.Log;
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

import java.util.Optional;

public class SpawnersProvider_DecypherCore implements SpawnersProvider_AutoDetect {

    private final SuperiorSkyblockPlugin plugin;

    public SpawnersProvider_DecypherCore(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new StackerListener(), plugin);
        Log.info("Using RoseStacker as a spawners provider.");
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
