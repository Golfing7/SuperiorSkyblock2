package com.bgsoftware.superiorskyblock.island.chest.logs;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.core.database.DatabaseResult;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import org.bukkit.Material;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * A single logged interaction with an island chest - an item being added or removed by a player.
 * Modeled after {@link com.bgsoftware.superiorskyblock.island.bank.SBankTransaction}.
 */
public class ChestTransaction {

    private final UUID player;
    private final int chestIndex;
    private final int slot;
    private final ChestAction action;
    private final Material itemType;
    private final int amount;
    private final int position;
    private final long time;
    private final String date;

    public ChestTransaction(UUID player, int chestIndex, int slot, ChestAction action, Material itemType,
                             int amount, int position, long time) {
        this.player = player;
        this.chestIndex = chestIndex;
        this.slot = slot;
        this.action = action;
        this.itemType = itemType;
        this.amount = amount;
        this.position = position;
        this.time = time;
        this.date = Formatters.DATE_FORMATTER.format(new Date(time));
    }

    public static Optional<ChestTransaction> fromDatabase(DatabaseResult resultSet) {
        Optional<ChestAction> action = resultSet.getEnum("action", ChestAction.class);
        if (!action.isPresent()) {
            Log.warn("Cannot load chest transaction with invalid action, skipping...");
            return Optional.empty();
        }

        Optional<String> itemTypeName = resultSet.getString("item_type");
        Material itemType;
        try {
            itemType = itemTypeName.isPresent() ? Material.valueOf(itemTypeName.get()) : null;
        } catch (IllegalArgumentException error) {
            itemType = null;
        }
        if (itemType == null) {
            Log.warn("Cannot load chest transaction with invalid item type, skipping...");
            return Optional.empty();
        }

        return Optional.of(new ChestTransaction(
                resultSet.getUUID("player").orElse(null),
                resultSet.getInt("chest_index").orElse(0),
                resultSet.getInt("slot").orElse(-1),
                action.get(),
                itemType,
                resultSet.getInt("amount").orElse(0),
                resultSet.getInt("position").orElse(1),
                resultSet.getLong("time").orElse(System.currentTimeMillis())
        ));
    }

    @Nullable
    public UUID getPlayer() {
        return player;
    }

    public int getChestIndex() {
        return chestIndex;
    }

    public int getSlot() {
        return slot;
    }

    public ChestAction getAction() {
        return action;
    }

    public Material getItemType() {
        return itemType;
    }

    public int getAmount() {
        return amount;
    }

    public int getPosition() {
        return position;
    }

    public long getTime() {
        return time;
    }

    public String getDate() {
        return date;
    }

}
