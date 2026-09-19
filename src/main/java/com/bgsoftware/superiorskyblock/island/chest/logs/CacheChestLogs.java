package com.bgsoftware.superiorskyblock.island.chest.logs;

import com.bgsoftware.superiorskyblock.api.data.DatabaseFilter;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.core.SequentialListBuilder;
import com.bgsoftware.superiorskyblock.core.database.DatabaseResult;
import com.bgsoftware.superiorskyblock.core.threads.Synchronized;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps all of an island's chest transactions in memory, loaded once from the database on creation.
 * Modeled after {@link com.bgsoftware.superiorskyblock.island.bank.logs.CacheBankLogs}, except loading
 * is done lazily here (on first use) rather than eagerly for every island at server startup.
 */
public class CacheChestLogs implements IChestLogs {

    private static final Comparator<ChestTransaction> POSITION_COMPARATOR = Comparator.comparingInt(ChestTransaction::getPosition);

    private final Synchronized<SortedSet<ChestTransaction>> transactions = Synchronized.of(new TreeSet<>(POSITION_COMPARATOR));
    private final Map<UUID, Synchronized<List<ChestTransaction>>> transactionsByPlayers = new ConcurrentHashMap<>();

    public CacheChestLogs(Island island) {
        island.getDatabaseBridge().loadObject("chest_transactions",
                DatabaseFilter.fromFilter("island", island.getUniqueId().toString()),
                chestTransactionRow -> ChestTransaction.fromDatabase(new DatabaseResult(chestTransactionRow))
                        .ifPresent(transaction -> addTransaction(transaction,
                                transaction.getPlayer() == null ? new UUID(0, 0) : transaction.getPlayer(), true)));
    }

    @Override
    public int getLastTransactionPosition() {
        return this.transactions.readAndGet(set -> set.isEmpty() ? 0 : set.last().getPosition());
    }

    @Override
    public List<ChestTransaction> getTransactions() {
        return transactions.readAndGet(chestTransactions -> new SequentialListBuilder<ChestTransaction>().build(chestTransactions));
    }

    @Override
    public List<ChestTransaction> getTransactions(UUID playerUUID) {
        Synchronized<List<ChestTransaction>> transactions = this.transactionsByPlayers.get(playerUUID);
        return transactions == null ? Collections.emptyList() : transactions.readAndGet(Collections::unmodifiableList);
    }

    @Override
    public void addTransaction(ChestTransaction chestTransaction, UUID senderUUID, boolean loadFromDatabase) {
        transactions.write(transactions -> transactions.add(chestTransaction));
        transactionsByPlayers.computeIfAbsent(senderUUID, p -> Synchronized.of(new LinkedList<>()))
                .write(transactions -> transactions.add(chestTransaction));
    }

}
