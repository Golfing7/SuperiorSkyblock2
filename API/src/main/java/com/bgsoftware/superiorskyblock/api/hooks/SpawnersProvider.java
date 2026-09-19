package com.bgsoftware.superiorskyblock.api.hooks;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public interface SpawnersProvider {

    /**
     * Get a pair that represents information about a spawner in a specific location.
     * This method is called async first, and if the string in the pair is null, it will be called synced later.
     * The integer represents the amount of spawners in that location, and the string represents the entity type.
     *
     * @param location The location to check.
     */
    Pair<Integer, String> getSpawner(Location location);

    /**
     * Get the spawner type from an item.
     * May return null in-case the spawner has no entity inside it.
     *
     * @param itemStack The item to check.
     */
    @Nullable
    String getSpawnerType(ItemStack itemStack);

    /**
     * Get the upgrade level of the spawner at the given location, if the provider supports leveled spawners.
     * Used to optionally scale a spawner's contribution to island worth by its level
     * (see {@code spawners-worth-scaled-by-level} in the config).
     *
     * @param location The location to check.
     * @return the spawner's level, or -1 if this provider doesn't support leveled spawners
     * or there's no spawner at the location.
     */
    default int getSpawnerLevel(Location location) {
        return -1;
    }

    /**
     * Get the maximum possible upgrade level for the spawner at the given location.
     *
     * @param location The location to check.
     * @return the maximum level, or -1 if this provider doesn't support leveled spawners.
     */
    default int getMaxSpawnerLevel(Location location) {
        return -1;
    }

}
