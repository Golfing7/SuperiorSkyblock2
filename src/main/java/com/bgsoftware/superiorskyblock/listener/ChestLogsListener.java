package com.bgsoftware.superiorskyblock.listener;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandChest;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestAction;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestLogsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logs items added to/removed from island chests, similar to how CoreProtect logs container changes.
 * Rather than trying to interpret every possible {@code InventoryAction}/drag combination, this simply
 * diffs the chest's full contents (by item type) right before the click/drag is processed against right
 * after, on the next tick - this uniformly and correctly handles direct clicks, shift-clicks in either
 * direction, hotbar swaps and drags with a single code path.
 */
public class ChestLogsListener implements Listener {

    private final SuperiorSkyblockPlugin plugin;

    public ChestLogsListener(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        handle(event.getView().getTopInventory(), event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        handle(event.getView().getTopInventory(), event.getWhoClicked());
    }

    private void handle(Inventory topInventory, HumanEntity humanEntity) {
        if (topInventory == null || !(humanEntity instanceof Player))
            return;

        InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof IslandChest))
            return;

        IslandChest islandChest = (IslandChest) holder;
        Island island = islandChest.getIsland();
        UUID playerUUID = humanEntity.getUniqueId();
        int chestIndex = islandChest.getIndex();

        Map<ItemStack, Integer> before = countByType(topInventory.getContents());

        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<ItemStack, Integer> after = countByType(topInventory.getContents());
            logDifferences(island, playerUUID, chestIndex, before, after);
        });
    }

    private void logDifferences(Island island, UUID playerUUID, int chestIndex,
                                 Map<ItemStack, Integer> before, Map<ItemStack, Integer> after) {
        for (Map.Entry<ItemStack, Integer> entry : after.entrySet()) {
            int diff = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (diff > 0) {
                ChestLogsManager.logTransaction(island, playerUUID, chestIndex, -1, ChestAction.ADDED, entry.getKey(), diff);
            }
        }

        for (Map.Entry<ItemStack, Integer> entry : before.entrySet()) {
            int diff = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
            if (diff > 0) {
                ChestLogsManager.logTransaction(island, playerUUID, chestIndex, -1, ChestAction.REMOVED, entry.getKey(), diff);
            }
        }
    }

    private static Map<ItemStack, Integer> countByType(ItemStack[] contents) {
        Map<ItemStack, Integer> counts = new HashMap<>();
        for (ItemStack itemStack : contents) {
            if (itemStack == null || itemStack.getType() == Material.AIR)
                continue;

            ItemStack itemKey = itemStack.clone();
            itemKey.setAmount(1);
            counts.merge(itemKey, itemStack.getAmount(), Integer::sum);
        }
        return counts;
    }

}
