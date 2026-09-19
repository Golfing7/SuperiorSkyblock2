package com.bgsoftware.superiorskyblock.island.chest.logs;

import java.util.List;
import java.util.UUID;

/**
 * Storage abstraction for an island's chest transaction logs.
 * Modeled after {@link com.bgsoftware.superiorskyblock.island.bank.logs.IBankLogs}.
 */
public interface IChestLogs {

    int getLastTransactionPosition();

    List<ChestTransaction> getTransactions();

    List<ChestTransaction> getTransactions(UUID playerUUID);

    void addTransaction(ChestTransaction chestTransaction, UUID senderUUID, boolean loadFromDatabase);

}
