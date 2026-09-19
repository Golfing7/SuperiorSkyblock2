package com.bgsoftware.superiorskyblock.island.chest.logs;

import com.bgsoftware.common.annotations.NotNull;
import com.bgsoftware.superiorskyblock.api.data.DatabaseFilter;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.core.SequentialListBuilder;
import com.bgsoftware.superiorskyblock.core.database.DatabaseResult;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Loads an island's chest transactions from the database on demand, caching them for a short while.
 * Modeled after {@link com.bgsoftware.superiorskyblock.island.bank.logs.DatabaseBankLogs}.
 */
@SuppressWarnings("UnstableApiUsage")
public class DatabaseChestLogs implements IChestLogs {

    private final LoadingCache<Integer, List<ChestTransaction>> cachedChestTransactions = CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, List<ChestTransaction>>() {
                @Override
                public List<ChestTransaction> load(@NotNull Integer ignored) {
                    sessionChestTransactions.invalidateAll();
                    return loadTransactionsFromDatabase();
                }
            });
    private final LoadingCache<Integer, List<ChestTransaction>> sessionChestTransactions = CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Integer, List<ChestTransaction>>() {
                @Override
                public List<ChestTransaction> load(@NotNull Integer ignored) {
                    return new LinkedList<>();
                }
            });

    private final Island island;
    private int lastTransactionPosition = -1;

    public DatabaseChestLogs(Island island) {
        this.island = island;
    }

    @Override
    public int getLastTransactionPosition() {
        if (lastTransactionPosition == -1) {
            lastTransactionPosition = 0;
            for (ChestTransaction transaction : getTransactions()) {
                int position = transaction.getPosition();
                if (position > lastTransactionPosition) {
                    lastTransactionPosition = position;
                }
            }
        }

        return lastTransactionPosition++;
    }

    @Override
    public List<ChestTransaction> getTransactions() {
        return collectChestTransactions();
    }

    @Override
    public List<ChestTransaction> getTransactions(UUID playerUUID) {
        return new SequentialListBuilder<ChestTransaction>()
                .filter(chestTransaction -> playerUUID.equals(chestTransaction.getPlayer()))
                .build(collectChestTransactions());
    }

    @Override
    public void addTransaction(ChestTransaction chestTransaction, UUID senderUUID, boolean loadFromDatabase) {
        sessionChestTransactions.getUnchecked(0).add(chestTransaction);
    }

    private List<ChestTransaction> collectChestTransactions() {
        List<ChestTransaction> chestTransactionList = new LinkedList<>();
        chestTransactionList.addAll(cachedChestTransactions.getUnchecked(0));
        chestTransactionList.addAll(sessionChestTransactions.getUnchecked(0));
        return chestTransactionList;
    }

    private List<ChestTransaction> loadTransactionsFromDatabase() {
        List<ChestTransaction> chestTransactionsList = new LinkedList<>();
        island.getDatabaseBridge().loadObject("chest_transactions",
                DatabaseFilter.fromFilter("island", island.getUniqueId().toString()),
                chestTransactionRow -> ChestTransaction.fromDatabase(new DatabaseResult(chestTransactionRow))
                        .ifPresent(chestTransactionsList::add));
        return chestTransactionsList;
    }

}
