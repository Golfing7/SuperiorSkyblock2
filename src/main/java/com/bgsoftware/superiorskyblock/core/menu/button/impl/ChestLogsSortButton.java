package com.bgsoftware.superiorskyblock.core.menu.button.impl;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.menu.button.MenuTemplateButton;
import com.bgsoftware.superiorskyblock.api.world.GameSound;
import com.bgsoftware.superiorskyblock.core.menu.TemplateItem;
import com.bgsoftware.superiorskyblock.core.menu.button.AbstractMenuTemplateButton;
import com.bgsoftware.superiorskyblock.core.menu.button.AbstractMenuViewButton;
import com.bgsoftware.superiorskyblock.core.menu.button.MenuTemplateButtonImpl;
import com.bgsoftware.superiorskyblock.core.menu.impl.MenuChestLogs;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestTransaction;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ChestLogsSortButton extends AbstractMenuViewButton<MenuChestLogs.View> {

    private ChestLogsSortButton(AbstractMenuTemplateButton<MenuChestLogs.View> templateButton, MenuChestLogs.View menuView) {
        super(templateButton, menuView);
    }

    @Override
    public Template getTemplate() {
        return (Template) super.getTemplate();
    }

    @Override
    public void onButtonClick(InventoryClickEvent clickEvent) {
        menuView.setSorting(getTemplate().sortType.getSorting());
        menuView.refreshView();
    }

    public enum SortType {

        TIME((o1, o2) -> Long.compare(o2.getTime(), o1.getTime())),
        AMOUNT((o1, o2) -> Integer.compare(o2.getAmount(), o1.getAmount()));

        private final Comparator<ChestTransaction> sorting;

        SortType(Comparator<ChestTransaction> sorting) {
            this.sorting = sorting;
        }

        public Comparator<ChestTransaction> getSorting() {
            return sorting;
        }

    }

    public static class Builder extends AbstractMenuTemplateButton.AbstractBuilder<MenuChestLogs.View> {

        private SortType sortType;

        public Builder setSortType(SortType sortType) {
            this.sortType = sortType;
            return this;
        }

        @Override
        public MenuTemplateButton<MenuChestLogs.View> build() {
            return new Template(buttonItem, clickSound, commands, requiredPermission, lackPermissionSound, sortType);
        }

    }

    public static class Template extends MenuTemplateButtonImpl<MenuChestLogs.View> {

        private final SortType sortType;

        Template(@Nullable TemplateItem buttonItem, @Nullable GameSound clickSound, @Nullable List<String> commands,
                 @Nullable String requiredPermission, @Nullable GameSound lackPermissionSound, SortType sortType) {
            super(buttonItem, clickSound, commands, requiredPermission, lackPermissionSound,
                    ChestLogsSortButton.class, ChestLogsSortButton::new);
            this.sortType = Objects.requireNonNull(sortType, "sortType cannot be null");
        }

    }

}
