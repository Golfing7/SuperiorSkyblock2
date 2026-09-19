package com.bgsoftware.superiorskyblock.external.placeholders;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.service.placeholders.PlaceholdersService;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.service.placeholders.PlaceholdersServiceImpl;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class PlaceholdersProvider_PlaceholderAPI implements PlaceholdersProvider {

    private final SuperiorSkyblockPlugin plugin;

    public PlaceholdersProvider_PlaceholderAPI(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        new EZPlaceholder((PlaceholdersServiceImpl) plugin.getServices().getService(PlaceholdersService.class)).register();

        Log.info("Using PlaceholderAPI for placeholders support.");
    }

    @Override
    public String parsePlaceholders(OfflinePlayer offlinePlayer, String value) {
        return PlaceholderAPI.setPlaceholders(offlinePlayer, value);
    }

    private class EZPlaceholder extends PlaceholderExpansion implements Relational {

        private final PlaceholdersServiceImpl placeholdersService;

        public EZPlaceholder(PlaceholdersServiceImpl placeholdersService) {
            this.placeholdersService = placeholdersService;
        }

        @Override
        public String getIdentifier() {
            return "superior";
        }

        @Override
        public String getAuthor() {
            return "Ome_R";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String placeholder) {
            return placeholdersService.handlePluginPlaceholder(player, placeholder);
        }

        @Override
        public String onPlaceholderRequest(Player player, String placeholder) {
            return onRequest(player, placeholder);
        }

        /**
         * PlaceholderAPI's relational hook, used for e.g. {@code %rel_superior_name_colored%}.
         * PAPI/consumers (e.g. TAB) call {@code setRelationalPlaceholders(viewer, target, ...)}, so
         * {@code one} is the player viewing the placeholder text and {@code two} is the player the
         * text is about - see {@link PlaceholdersServiceImpl#handlePluginPlaceholder(OfflinePlayer, OfflinePlayer, String)}.
         */
        @Override
        public String onPlaceholderRequest(Player one, Player two, String placeholder) {
            return placeholdersService.handlePluginPlaceholder(two, one, placeholder);
        }
    }

}
