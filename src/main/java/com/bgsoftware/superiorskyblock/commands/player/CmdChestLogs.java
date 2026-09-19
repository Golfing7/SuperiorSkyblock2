package com.bgsoftware.superiorskyblock.commands.player;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.commands.IPermissibleCommand;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestLogsManager;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestTransaction;
import com.bgsoftware.superiorskyblock.island.privilege.IslandPrivileges;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shows who added/removed items from the island's chests, similar to how CoreProtect logs block changes.
 */
public class CmdChestLogs implements IPermissibleCommand {

    private static final int SHOWN_TRANSACTIONS = 10;

    @Override
    public List<String> getAliases() {
        return Arrays.asList("chestlogs", "chestlog");
    }

    @Override
    public String getPermission() {
        return "superior.island.chestlogs";
    }

    @Override
    public String getUsage(Locale locale) {
        return "chestlogs [player]";
    }

    @Override
    public String getDescription(Locale locale) {
        return "View the island's chest transaction logs";
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return false;
    }

    @Override
    public IslandPrivilege getPrivilege() {
        return IslandPrivileges.ISLAND_CHEST;
    }

    @Override
    public Message getPermissionLackMessage() {
        return Message.NO_ISLAND_CHEST_PERMISSION;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, SuperiorPlayer superiorPlayer, Island island, String[] args) {
        List<ChestTransaction> transactions;

        if (args.length == 2) {
            SuperiorPlayer filteredPlayer = plugin.getPlayers().getSuperiorPlayer(args[1]);
            if (filteredPlayer == null) {
                superiorPlayer.asPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cPlayer " + args[1] + " was not found."));
                return;
            }

            transactions = ChestLogsManager.getTransactions(island, filteredPlayer.getUniqueId());
        } else {
            transactions = ChestLogsManager.getTransactions(island);
        }

        if (transactions.isEmpty()) {
            superiorPlayer.asPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cNo chest transactions were logged for this island."));
            return;
        }

        List<String> lines = transactions.stream()
                .sorted((a, b) -> Long.compare(b.getTime(), a.getTime()))
                .limit(SHOWN_TRANSACTIONS)
                .map(transaction -> {
                    String playerName = transaction.getPlayer() == null ? "Console" :
                            plugin.getPlayers().getSuperiorPlayer(transaction.getPlayer()).getName();
                    String actionColor = transaction.getAction().name().equals("ADDED") ? "&a+" : "&c-";

                    return ChatColor.translateAlternateColorCodes('&', String.format("&7[%s] &e%s &7%s%s &7%s &f(chest #%s)",
                            transaction.getDate(), playerName, actionColor, transaction.getAmount(),
                            transaction.getItemType().name(), transaction.getChestIndex() + 1));
                })
                .collect(Collectors.toList());

        superiorPlayer.asPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6&lChest Logs &7(showing last " + lines.size() + " of " + transactions.size() + "):"));
        lines.forEach(superiorPlayer.asPlayer()::sendMessage);
    }

}
