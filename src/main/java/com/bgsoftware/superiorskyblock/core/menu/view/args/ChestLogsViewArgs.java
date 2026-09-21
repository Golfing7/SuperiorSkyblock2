package com.bgsoftware.superiorskyblock.core.menu.view.args;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;

public class ChestLogsViewArgs extends IslandViewArgs {

    @Nullable
    private final SuperiorPlayer filteredPlayer;

    public ChestLogsViewArgs(Island island) {
        this(island, null);
    }

    public ChestLogsViewArgs(Island island, @Nullable SuperiorPlayer filteredPlayer) {
        super(island);
        this.filteredPlayer = filteredPlayer;
    }

    @Nullable
    public SuperiorPlayer getFilteredPlayer() {
        return filteredPlayer;
    }

}
