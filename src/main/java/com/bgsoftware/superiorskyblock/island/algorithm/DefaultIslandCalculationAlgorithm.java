package com.bgsoftware.superiorskyblock.island.algorithm;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandChunkFlags;
import com.bgsoftware.superiorskyblock.api.island.SpawnerLevelCounts;
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
import com.bgsoftware.superiorskyblock.core.values.BlockValue;
import com.bgsoftware.superiorskyblock.external.blocks.ICustomBlocksProvider;
import com.bgsoftware.superiorskyblock.island.IslandUtils;
import com.bgsoftware.superiorskyblock.island.SpawnerLevelValues;
import com.bgsoftware.superiorskyblock.world.chunk.ChunkLoadReason;
import org.bukkit.Location;
import org.bukkit.World;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

                    if (blockCount <= 0) {
                        Pair<Integer, String> spawnersProviderInfo = plugin.getProviders()
                                .getSpawnersProvider().getSpawner(spawnerInfo.location);

                        blockCount = spawnersProviderInfo.getKey();
                        String entityType = spawnersProviderInfo.getValue();
                        if (entityType != null)
                            blockKey = Keys.ofSpawner(entityType, spawnerInfo.location);
                    }

                    blockCounts.addCounts(blockKey, blockCount);
                    trackSpawnerLevel(blockCounts, spawnerInfo.location, blockKey, blockCount);
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
     * If the spawners provider exposes level info for this location, records the spawner's level in the
     * level breakdown of its key (shown in the counts GUI).
     * <p>
     * If spawner-level scaling is also enabled, records the (negative) deficits between this spawner's
     * full configured worth and island level and their level-scaled values, so they can be applied on top
     * of the flat values the block-count map produces. Block limits are unaffected by this.
     * </p>
     * <p>
     * Must be called from the main thread, as providers may only be able to read levels from there.
     * </p>
     */
    private void trackSpawnerLevel(BlockCountsTracker blockCounts, Location location, Key spawnerKey, int count) {
        if (count <= 0)
            return;

        int level = plugin.getProviders().getSpawnersProvider().getSpawnerLevel(location);
        if (level < 1)
            return;

        int maxLevel = plugin.getProviders().getSpawnersProvider().getMaxSpawnerLevel(location);
        blockCounts.addSpawnerLevel(spawnerKey, level, maxLevel, count);

        if (!plugin.getSettings().isSpawnerWorthScaledByLevel() || maxLevel < 1 || level >= maxLevel)
            return;

        BlockValue blockValue = plugin.getBlockValues().getBlockValue(spawnerKey);
        // How much of the spawner's full value is lost because it's not maxed out yet.
        BigDecimal lostValueRate = SpawnerLevelValues.getValueFactor(level, maxLevel).subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(count));

        BigDecimal fullWorth = blockValue.getWorth();
        if (fullWorth.compareTo(BigDecimal.ZERO) != 0)
            blockCounts.addSpawnerWorthAdjustment(fullWorth.multiply(lostValueRate));

        BigDecimal fullLevel = blockValue.getLevel();
        if (fullLevel.compareTo(BigDecimal.ZERO) != 0)
            blockCounts.addSpawnerIslandLevelAdjustment(fullLevel.multiply(lostValueRate));
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
        private final AtomicReference<BigDecimal> spawnerIslandLevelAdjustment = new AtomicReference<>(BigDecimal.ZERO);
        // Only accessed from the main thread (see trackSpawnerLevel).
        private final KeyMap<SpawnerLevels> spawnerLevels = KeyMaps.createHashMap(KeyIndicator.MATERIAL);

        @Override
        public Map<Key, BigInteger> getBlockCounts() {
            return blockCounts;
        }

        @Override
        public BigDecimal getSpawnerWorthAdjustment() {
            return spawnerWorthAdjustment.get();
        }

        @Override
        public BigDecimal getSpawnerIslandLevelAdjustment() {
            return spawnerIslandLevelAdjustment.get();
        }

        public void addCounts(Key blockKey, int amount) {
            blockCounts.put(blockKey, blockCounts.getRaw(blockKey, BigInteger.ZERO).add(BigInteger.valueOf(amount)));
        }

        public void addCounts(KeyMap<Counter> other) {
            other.forEach((key, counter) -> addCounts(key, counter.get()));
        }

        @Override
        public Map<Key, SpawnerLevelCounts> getSpawnerLevelCounts() {
            if (spawnerLevels.isEmpty())
                return Collections.emptyMap();

            KeyMap<SpawnerLevelCounts> spawnerLevelCounts = KeyMaps.createHashMap(KeyIndicator.MATERIAL);
            spawnerLevels.forEach((spawnerKey, levels) ->
                    spawnerLevelCounts.put(spawnerKey, new SpawnerLevelCounts(levels.counts, levels.maxLevel)));
            return spawnerLevelCounts;
        }

        public void addSpawnerWorthAdjustment(BigDecimal delta) {
            spawnerWorthAdjustment.updateAndGet(current -> current.add(delta));
        }

        public void addSpawnerIslandLevelAdjustment(BigDecimal delta) {
            spawnerIslandLevelAdjustment.updateAndGet(current -> current.add(delta));
        }

        public void addSpawnerLevel(Key spawnerKey, int level, int maxLevel, int amount) {
            // Using getRaw, as get may fall back to the global spawner key.
            SpawnerLevels levels = spawnerLevels.getRaw(spawnerKey, null);
            if (levels == null) {
                levels = new SpawnerLevels();
                spawnerLevels.put(spawnerKey, levels);
            }

            levels.counts.merge(level, BigInteger.valueOf(amount), BigInteger::add);
            levels.maxLevel = Math.max(levels.maxLevel, maxLevel);
        }

    }

    private static class SpawnerLevels {

        private final Map<Integer, BigInteger> counts = new HashMap<>();
        private int maxLevel = -1;

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
