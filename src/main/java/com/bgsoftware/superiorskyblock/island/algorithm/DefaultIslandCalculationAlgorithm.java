package com.bgsoftware.superiorskyblock.island.algorithm;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandChunkFlags;
import com.bgsoftware.superiorskyblock.api.island.algorithms.IslandCalculationAlgorithm;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.key.KeyMap;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.superiorskyblock.core.CalculatedChunk;
import com.bgsoftware.superiorskyblock.core.ChunkPosition;
import com.bgsoftware.superiorskyblock.core.Counter;
import com.bgsoftware.superiorskyblock.core.collections.Chunk2ObjectMap;
import com.bgsoftware.superiorskyblock.core.collections.CompletableFutureList;
import com.bgsoftware.superiorskyblock.core.key.ConstantKeys;
import com.bgsoftware.superiorskyblock.core.key.KeyIndicator;
import com.bgsoftware.superiorskyblock.core.key.Keys;
import com.bgsoftware.superiorskyblock.core.key.map.KeyMaps;
import com.bgsoftware.superiorskyblock.core.key.types.SpawnerKey;
import com.bgsoftware.superiorskyblock.core.logging.Debug;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.core.profiler.ProfileType;
import com.bgsoftware.superiorskyblock.core.profiler.Profiler;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.core.threads.Synchronized;
import com.bgsoftware.superiorskyblock.external.blocks.ICustomBlocksProvider;
import com.bgsoftware.superiorskyblock.island.IslandUtils;
import com.bgsoftware.superiorskyblock.world.chunk.ChunkLoadReason;
import org.bukkit.Location;
import org.bukkit.World;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultIslandCalculationAlgorithm implements IslandCalculationAlgorithm {

    public static final Synchronized<Chunk2ObjectMap<CalculatedChunk.Blocks>> CACHED_CALCULATED_CHUNKS =
            Synchronized.of(new Chunk2ObjectMap<>());

    private static final List<Pair<Key, Key>> MINECART_BLOCK_TYPES = createMinecartBlockTypes();
    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private static final DefaultIslandCalculationAlgorithm INSTANCE = new DefaultIslandCalculationAlgorithm();

    private DefaultIslandCalculationAlgorithm() {

    }

    public static DefaultIslandCalculationAlgorithm getInstance() {
        return INSTANCE;
    }

    @Override
    public CompletableFuture<IslandCalculationResult> calculateIsland(Island island) {
        CompletableFuture<IslandCalculationResult> result = new CompletableFuture<>();
        BukkitExecutor.ensureMain(() -> calculateIslandInternal(island, result));
        return result;
    }

    private void calculateIslandInternal(Island island, CompletableFuture<IslandCalculationResult> result) {
        CompletableFutureList<List<CalculatedChunk.Blocks>> chunksToLoad = new CompletableFutureList<>(plugin.getSettings().getRecalcTaskTimeout());

        long profiler = Profiler.start(ProfileType.CALCULATE_ISLAND);
        Log.debug(Debug.CHUNK_CALCULATION_BLOCKS, island.getOwner().getName());

        if (!plugin.getProviders().hasSnapshotsSupport()) {
            IslandUtils.getChunkCoords(island, IslandChunkFlags.ONLY_PROTECTED | IslandChunkFlags.NO_EMPTY_CHUNKS)
                    .forEach((worldInfo, worldChunks) -> {
                        // Load the world.
                        World world = plugin.getProviders().getWorldsProvider().getIslandsWorld(island, worldInfo.getDimension());
                        if (world != null)
                            chunksToLoad.add(plugin.getNMSChunks().calculateChunks(worldChunks, CACHED_CALCULATED_CHUNKS));
                    });
        } else {
            IslandUtils.getAllChunksAsync(island, IslandChunkFlags.ONLY_PROTECTED | IslandChunkFlags.NO_EMPTY_CHUNKS,
                    ChunkLoadReason.BLOCKS_RECALCULATE, plugin.getProviders()::takeSnapshots).forEach(completableFuture -> {
                CompletableFuture<List<CalculatedChunk.Blocks>> calculateCompletable = new CompletableFuture<>();
                completableFuture.whenComplete((chunk, ex) -> {
                    try (ChunkPosition chunkPosition = ChunkPosition.of(chunk)) {
                        plugin.getNMSChunks().calculateChunks(Collections.singletonList(chunkPosition), CACHED_CALCULATED_CHUNKS)
                                .whenComplete((pair, ex2) -> calculateCompletable.complete(pair));
                    }
                });
                chunksToLoad.add(calculateCompletable);
            });
        }

        BlockCountsTracker blockCounts = new BlockCountsTracker();
        Set<SpawnerInfo> spawnersToCheck = new HashSet<>();
        Set<ChunkPosition> chunksToCheck = new HashSet<>();

        BukkitExecutor.createTask().runAsync(v -> {
            chunksToLoad.forEachCompleted(worldCalculatedChunks -> worldCalculatedChunks.forEach(calculatedChunk -> {
                Log.debugResult(Debug.CHUNK_CALCULATION_BLOCKS, "Chunk Finished", calculatedChunk.getPosition());

                // We want to remove spawners from the chunkInfo, as it will be used later
                calculatedChunk.getBlockCounts().removeIf(key -> key instanceof SpawnerKey);

                blockCounts.addCounts(calculatedChunk.getBlockCounts());

                // Load spawners
                for (Location location : calculatedChunk.getSpawners()) {
                    Pair<Integer, String> spawnerInfo = plugin.getProviders().getSpawnersProvider().getSpawner(location);

                    if (spawnerInfo.getValue() == null) {
                        spawnersToCheck.add(new SpawnerInfo(location, spawnerInfo.getKey()));
                    } else {
                        Key spawnerKey = Keys.ofSpawner(spawnerInfo.getValue(), location);
                        blockCounts.addCounts(spawnerKey, spawnerInfo.getKey());
                        applySpawnerLevelWorthAdjustment(blockCounts, location, spawnerKey, spawnerInfo.getKey());
                    }
                }

                ChunkPosition chunkPosition = calculatedChunk.getPosition();
                if (!loadExternalBlocksForChunk(chunkPosition, blockCounts))
                    chunksToCheck.add(chunkPosition);

                // Load built-in stacked blocks
                plugin.getStackedBlocks().forEach(calculatedChunk.getPosition(), stackedBlock ->
                        blockCounts.addCounts(stackedBlock.getBlockKey(), stackedBlock.getAmount() - 1));

                plugin.getProviders().releaseSnapshots(calculatedChunk.getPosition());
            }), result::completeExceptionally);
        }).runSync(v -> {
            Key blockKey;
            int blockCount;

            // Calculate spawner counts
            for (SpawnerInfo spawnerInfo : spawnersToCheck) {
                try {
                    blockKey = Keys.of(spawnerInfo.location.getBlock());
                    blockCount = spawnerInfo.spawnerCount;
                    boolean isSpawnerKey = false;

                    if (blockCount <= 0) {
                        Pair<Integer, String> spawnersProviderInfo = plugin.getProviders()
                                .getSpawnersProvider().getSpawner(spawnerInfo.location);

                        blockCount = spawnersProviderInfo.getKey();

                        String entityType = spawnersProviderInfo.getValue();
                        if (entityType != null) {
                            blockKey = Keys.ofSpawner(entityType, spawnerInfo.location);
                            isSpawnerKey = true;
                        }
                    }

                    blockCounts.addCounts(blockKey, blockCount);
                    if (isSpawnerKey)
                        applySpawnerLevelWorthAdjustment(blockCounts, spawnerInfo.location, blockKey, blockCount);
                } catch (Throwable ignored) {
                }
            }
            spawnersToCheck.clear();

            // Calculate stacked block counts
            for (ChunkPosition chunkPosition : chunksToCheck) {
                loadExternalBlocksForChunk(chunkPosition, blockCounts);
            }

            // Calculate minecart block counts
            MINECART_BLOCK_TYPES.forEach(minecartTypes -> {
                int count = island.getEntitiesTracker().getEntityCount(minecartTypes.getKey());
                if (count > 0)
                    blockCounts.addCounts(minecartTypes.getValue(), count);
            });

            chunksToCheck.clear();

            Profiler.end(profiler);

            result.complete(blockCounts);
        });
    }

    private static List<Pair<Key, Key>> createMinecartBlockTypes() {
        List<Pair<Key, Key>> minecartBlockTypes = new LinkedList<>();

        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_COMMAND, ConstantKeys.COMMAND_BLOCK));
        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_CHEST, ConstantKeys.CHEST));
        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_FURNACE, ConstantKeys.FURNACE));
        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_TNT, ConstantKeys.TNT));
        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_HOPPER, ConstantKeys.HOPPER));
        minecartBlockTypes.add(new Pair<>(ConstantKeys.ENTITY_MINECART_MOB_SPAWNER, ConstantKeys.MOB_SPAWNER));

        return Collections.unmodifiableList(minecartBlockTypes);
    }

    /**
     * If spawner-level worth scaling is enabled and the spawners provider exposes level info for this
     * location, records the (negative) worth deficit between this spawner's full configured worth and
     * its level-scaled worth, so it can be applied on top of the flat worth the block-count map produces.
     * <p>
     * Does not affect {@code blockCounts} itself, so block limits, the counts GUI, and island level are
     * completely unaffected by this - only the final worth number changes.
     * </p>
     */
    private void applySpawnerLevelWorthAdjustment(BlockCountsTracker blockCounts, Location location, Key spawnerKey, int count) {
        if (count <= 0 || !plugin.getSettings().isSpawnerWorthScaledByLevel())
            return;

        int level = plugin.getProviders().getSpawnersProvider().getSpawnerLevel(location);
        int maxLevel = plugin.getProviders().getSpawnersProvider().getMaxSpawnerLevel(location);
        if (level < 1 || maxLevel < 1 || level >= maxLevel)
            return;

        BigDecimal fullWorth = plugin.getBlockValues().getBlockValue(spawnerKey).getWorth();
        if (fullWorth.compareTo(BigDecimal.ZERO) == 0)
            return;

        BigDecimal factor = BigDecimal.valueOf(level).divide(BigDecimal.valueOf(maxLevel), 10, RoundingMode.HALF_UP);
        BigDecimal deficit = fullWorth.multiply(BigDecimal.valueOf(count)).multiply(factor.subtract(BigDecimal.ONE));
        blockCounts.addSpawnerWorthAdjustment(deficit);
    }

    private boolean loadExternalBlocksForChunk(ChunkPosition chunkPosition, BlockCountsTracker blockCounts) {
        // Load stacked blocks
        Collection<Pair<Key, Integer>> stackedBlocks = plugin.getProviders().getStackedBlocksProvider()
                .getBlocks(chunkPosition.getWorld(), chunkPosition.getX(), chunkPosition.getZ());

        if (stackedBlocks == null)
            return false;

        BlockCountsTracker chunkBlockCounts = new BlockCountsTracker();

        for (ICustomBlocksProvider customBlocksProvider : plugin.getProviders().getCustomBlocksProviders()) {
            KeyMap<Integer> customBlocksCounts = customBlocksProvider.getBlockCountsForChunk(chunkPosition);
            if (customBlocksCounts == null)
                return false;

            customBlocksCounts.forEach(chunkBlockCounts::addCounts);
        }

        chunkBlockCounts.blockCounts.forEach((block, count) ->
                blockCounts.addCounts(block, count.intValue()));

        for (Pair<Key, Integer> pair : stackedBlocks) {
            blockCounts.addCounts(pair.getKey(), pair.getValue() - 1);
        }

        return true;
    }

    private static class BlockCountsTracker implements IslandCalculationResult {

        private final KeyMap<BigInteger> blockCounts = KeyMaps.createConcurrentHashMap(KeyIndicator.MATERIAL);
        private final AtomicReference<BigDecimal> spawnerWorthAdjustment = new AtomicReference<>(BigDecimal.ZERO);

        @Override
        public Map<Key, BigInteger> getBlockCounts() {
            return blockCounts;
        }

        @Override
        public BigDecimal getSpawnerWorthAdjustment() {
            return spawnerWorthAdjustment.get();
        }

        public void addCounts(Key blockKey, int amount) {
            blockCounts.put(blockKey, blockCounts.getRaw(blockKey, BigInteger.ZERO).add(BigInteger.valueOf(amount)));
        }

        public void addCounts(KeyMap<Counter> other) {
            other.forEach((key, counter) -> addCounts(key, counter.get()));
        }

        public void addSpawnerWorthAdjustment(BigDecimal delta) {
            spawnerWorthAdjustment.updateAndGet(current -> current.add(delta));
        }

    }

    private static class SpawnerInfo {

        private final Location location;
        private final int spawnerCount;

        SpawnerInfo(Location location, int spawnerCount) {
            this.location = location;
            this.spawnerCount = spawnerCount;
        }

    }

}
