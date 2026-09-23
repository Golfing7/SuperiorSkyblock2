package com.bgsoftware.superiorskyblock.island.chest.logs;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.core.database.bridge.IslandsDatabaseBridge;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks chest transaction logs per island, independent of {@link Island}/{@code SIsland} to avoid
 * touching the core island API. Islands are looked up lazily by their unique id and their log store
 * is dropped from memory when the island is unloaded/disbanded (see {@link #forgetIsland(UUID)}).
 */
public final class ChestLogsManager {

    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private static final Map<UUID, IChestLogs> LOGS_BY_ISLAND = new ConcurrentHashMap<>();

    private ChestLogsManager() {

    }

    private static IChestLogs getOrCreate(Island island) {
        return LOGS_BY_ISLAND.computeIfAbsent(island.getUniqueId(), id ->
                SuperiorSkyblockPlugin.getPlugin().getSettings().getIslandChests().isCacheChestLogs() ?
                        new CacheChestLogs(island) : new DatabaseChestLogs(island));
    }

    public static void forgetIsland(UUID islandId) {
        LOGS_BY_ISLAND.remove(islandId);
    }

    public static List<ChestTransaction> getTransactions(Island island) {
        return getOrCreate(island).getTransactions();
    }

    public static List<ChestTransaction> getTransactions(Island island, UUID playerUUID) {
        return getOrCreate(island).getTransactions(playerUUID);
    }

    public static List<ChestTransaction> getConsoleTransactions(Island island) {
        return getOrCreate(island).getTransactions(CONSOLE_UUID);
    }

    /**
     * Logs a brand-new chest transaction, saving it to the database if chest-logs are enabled.
     */
    public static void logTransaction(Island island, UUID player, int chestIndex, int slot, ChestAction action,
                                      ItemStack item, int amount) {
        if (!SuperiorSkyblockPlugin.getPlugin().getSettings().getIslandChests().isChestLogs() || amount <= 0)
            return;

        IChestLogs chestLogs = getOrCreate(island);
        int position = chestLogs.getLastTransactionPosition() + 1;
        ItemMeta itemMeta = item.getItemMeta();
        String displayName = itemMeta != null && itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : "";
        ChestTransaction transaction = new ChestTransaction(player, chestIndex, slot, action, item.getType(), displayName, amount,
                position, System.currentTimeMillis());

        UUID senderUUID = player == null ? CONSOLE_UUID : player;
        chestLogs.addTransaction(transaction, senderUUID, false);

        IslandsDatabaseBridge.saveChestTransaction(island, transaction);

        SuperiorSkyblockPlugin.getPlugin().getMenus().refreshChestLogs(island);
    }

}
