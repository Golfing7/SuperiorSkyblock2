package com.bgsoftware.superiorskyblock.core.menu.impl;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.menu.Menu;
import com.bgsoftware.superiorskyblock.api.menu.layout.MenuLayout;
import com.bgsoftware.superiorskyblock.api.menu.view.MenuView;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.io.MenuParserImpl;
import com.bgsoftware.superiorskyblock.core.menu.AbstractPagedMenu;
import com.bgsoftware.superiorskyblock.core.menu.MenuIdentifiers;
import com.bgsoftware.superiorskyblock.core.menu.MenuParseResult;
import com.bgsoftware.superiorskyblock.core.menu.MenuPatternSlots;
import com.bgsoftware.superiorskyblock.core.menu.button.impl.ChestLogsPagedObjectButton;
import com.bgsoftware.superiorskyblock.core.menu.button.impl.ChestLogsSortButton;
import com.bgsoftware.superiorskyblock.core.menu.view.AbstractPagedMenuView;
import com.bgsoftware.superiorskyblock.core.menu.view.IIslandMenuView;
import com.bgsoftware.superiorskyblock.core.menu.view.IPlayerMenuView;
import com.bgsoftware.superiorskyblock.core.menu.view.args.ChestLogsViewArgs;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestLogsManager;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestTransaction;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MenuChestLogs extends AbstractPagedMenu<MenuChestLogs.View, ChestLogsViewArgs, ChestTransaction> {

    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private MenuChestLogs(MenuParseResult<View> parseResult) {
        super(MenuIdentifiers.MENU_CHEST_LOGS, parseResult, false);
    }

    @Override
    protected View createViewInternal(SuperiorPlayer superiorPlayer, ChestLogsViewArgs args,
                                      @Nullable MenuView<?, ?> previousMenuView) {
        return new View(superiorPlayer, previousMenuView, this, args);
    }

    public void refreshViews(Island island) {
        this.refreshViews(view -> Objects.equals(view.island, island));
    }

    @Nullable
    public static MenuChestLogs createInstance() {
        MenuParseResult<View> menuParseResult = MenuParserImpl.getInstance().loadMenu("chest-logs.yml",
                null, new ChestLogsPagedObjectButton.Builder());

        if (menuParseResult == null)
            return null;

        MenuPatternSlots menuPatternSlots = menuParseResult.getPatternSlots();
        YamlConfiguration cfg = menuParseResult.getConfig();
        MenuLayout.Builder<View> patternBuilder = menuParseResult.getLayoutBuilder();

        patternBuilder.mapButtons(MenuParserImpl.getInstance().parseButtonSlots(cfg, "time-sort", menuPatternSlots),
                new ChestLogsSortButton.Builder().setSortType(ChestLogsSortButton.SortType.TIME));
        patternBuilder.mapButtons(MenuParserImpl.getInstance().parseButtonSlots(cfg, "amount-sort", menuPatternSlots),
                new ChestLogsSortButton.Builder().setSortType(ChestLogsSortButton.SortType.AMOUNT));

        return new MenuChestLogs(menuParseResult);
    }

    public static class View extends AbstractPagedMenuView<View, ChestLogsViewArgs, ChestTransaction> implements IIslandMenuView, IPlayerMenuView {

        private final Island island;

        private Comparator<ChestTransaction> sorting = ChestLogsSortButton.SortType.TIME.getSorting();
        private UUID filteredPlayer;

        View(SuperiorPlayer inventoryViewer, @Nullable MenuView<?, ?> previousMenuView,
             Menu<View, ChestLogsViewArgs> menu, ChestLogsViewArgs args) {
            super(inventoryViewer, previousMenuView, menu);
            this.island = args.getIsland();
            SuperiorPlayer filteredPlayer = args.getFilteredPlayer();
            if (filteredPlayer != null)
                this.filteredPlayer = filteredPlayer.getUniqueId();
        }

        @Override
        public Island getIsland() {
            return this.island;
        }

        @Override
        public SuperiorPlayer getSuperiorPlayer() {
            return this.filteredPlayer == null || this.filteredPlayer.equals(CONSOLE_UUID) ? null :
                    plugin.getPlayers().getSuperiorPlayer(filteredPlayer);
        }

        public void setSorting(Comparator<ChestTransaction> sorting) {
            this.sorting = sorting;
        }

        public void setFilteredPlayer(UUID filteredPlayer) {
            this.filteredPlayer = filteredPlayer == null ? CONSOLE_UUID : filteredPlayer;
            this.setCurrentPage(1);
        }

        @Override
        public String replaceTitle(String title) {
            return title.replace("{0}", getFilteredPlayerName(filteredPlayer));
        }

        @Override
        protected List<ChestTransaction> requestObjects() {
            List<ChestTransaction> transactions = getTransactions();

            if (sorting == null) {
                return transactions;
            }

            transactions = new LinkedList<>(transactions);

            transactions.sort(sorting);

            return Collections.unmodifiableList(transactions);
        }

        private List<ChestTransaction> getTransactions() {
            if (filteredPlayer == null) {
                return ChestLogsManager.getTransactions(island);
            } else if (filteredPlayer.equals(CONSOLE_UUID)) {
                return ChestLogsManager.getConsoleTransactions(island);
            } else {
                return ChestLogsManager.getTransactions(island, filteredPlayer);
            }
        }

        private static String getFilteredPlayerName(UUID filteredPlayer) {
            if (filteredPlayer == null) {
                return "";
            } else if (filteredPlayer.equals(CONSOLE_UUID)) {
                return "Console";
            } else {
                return plugin.getPlayers().getSuperiorPlayer(filteredPlayer).getName();
            }
        }

    }

}
