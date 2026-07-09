package com.bgsoftware.superiorskyblock.external.entities;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.hooks.EntitiesProvider;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.core.ObjectsPools;
import com.bgsoftware.superiorskyblock.core.collections.AutoRemovalCollection;
import com.bgsoftware.superiorskyblock.module.BuiltinModules;
import com.bgsoftware.superiorskyblock.module.upgrades.type.UpgradeTypeEntityLimits;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import com.golfing8.dcore.module.spawner.struct.DCStackedEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class EntitiesProvider_DecypherCore implements EntitiesProvider {

    private final AutoRemovalCollection<UUID> stackedEntityDeaths = AutoRemovalCollection.newHashSet(5, TimeUnit.SECONDS);

    private final SuperiorSkyblockPlugin plugin;

    public EntitiesProvider_DecypherCore(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;

        plugin.getServer().getPluginManager().registerEvents(new DeathListener(), plugin);
    }

    @Override
    public boolean shouldTrackEntity(Entity entity) {
        return !stackedEntityDeaths.contains(entity.getUniqueId());
    }

    private class DeathListener implements Listener {

        @EventHandler
        public void onEntityDeath(EntityDeathEvent e) {
            if (!BuiltinModules.UPGRADES.isUpgradeTypeEnabled(UpgradeTypeEntityLimits.class) ||
                    !BukkitEntities.canHaveLimit(e.getEntityType()) ||
                    BukkitEntities.canBypassEntityLimit(e.getEntity(), false))
                return;

            Optional<DCStackedEntity> stackedEntity = DCStackedEntity.load(e.getEntity());
            if (stackedEntity.isEmpty() || stackedEntity.get().getStackCount() <= 1)
                return;

            Island island;
            try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
                island = plugin.getGrid().getIslandAt(e.getEntity().getLocation(wrapper.getHandle()));
            }

            if (island == null)
                return;

            stackedEntityDeaths.add(e.getEntity().getUniqueId());
        }

    }

}
