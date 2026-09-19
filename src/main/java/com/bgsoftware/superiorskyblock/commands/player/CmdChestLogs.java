package com.bgsoftware.superiorskyblock.commands.player;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.commands.CommandTabCompletes;
import com.bgsoftware.superiorskyblock.commands.IPermissibleCommand;
import com.bgsoftware.superiorskyblock.commands.arguments.CommandArguments;
import com.bgsoftware.superiorskyblock.core.menu.view.MenuViewWrapper;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.island.privilege.IslandPrivileges;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shows who added/removed items from the island's chests, similar to how CoreProtect logs block changes.
 */
public class CmdChestLogs implements IPermissibleCommand {

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
        return "chestlogs [" + Message.COMMAND_ARGUMENT_PLAYER_NAME.getMessage(locale) + "]";
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
        SuperiorPlayer filteredPlayer = null;

        if (args.length == 2) {
            filteredPlayer = CommandArguments.getPlayer(plugin, superiorPlayer, args[1]);

            if (filteredPlayer == null)
                return;
        }

        plugin.getMenus().openChestLogs(superiorPlayer, MenuViewWrapper.fromView(superiorPlayer.getOpenedView()),
                island, filteredPlayer);
    }

    @Override
    public List<String> tabComplete(SuperiorSkyblockPlugin plugin, SuperiorPlayer superiorPlayer, Island island, String[] args) {
        return args.length == 2 ? CommandTabCompletes.getIslandMembers(island, args[1]) : Collections.emptyList();
    }

}
