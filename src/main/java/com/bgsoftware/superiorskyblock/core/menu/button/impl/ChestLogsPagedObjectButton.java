package com.bgsoftware.superiorskyblock.core.menu.button.impl;

import com.bgsoftware.superiorskyblock.api.menu.button.MenuTemplateButton;
import com.bgsoftware.superiorskyblock.api.menu.button.PagedMenuTemplateButton;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.itemstack.ItemBuilder;
import com.bgsoftware.superiorskyblock.core.menu.button.AbstractPagedMenuButton;
import com.bgsoftware.superiorskyblock.core.menu.button.PagedMenuTemplateButtonImpl;
import com.bgsoftware.superiorskyblock.core.menu.impl.MenuChestLogs;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestAction;
import com.bgsoftware.superiorskyblock.island.chest.logs.ChestTransaction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ChestLogsPagedObjectButton extends AbstractPagedMenuButton<MenuChestLogs.View, ChestTransaction> {

    private static final int MAX_STACK = 64;

    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private ChestLogsPagedObjectButton(MenuTemplateButton<MenuChestLogs.View> templateButton, MenuChestLogs.View menuView) {
        super(templateButton, menuView);
    }

    @Override
    public void onButtonClick(InventoryClickEvent clickEvent) {
        menuView.setFilteredPlayer(pagedObject.getPlayer());
        menuView.refreshView();
    }

    @Override
    public ItemStack modifyViewItem(ItemStack buttonItem) {
        SuperiorPlayer inventoryViewer = menuView.getInventoryViewer();
        return new ItemBuilder(buttonItem)
                .withType(pagedObject.getItemType())
                .withAmount(Math.min(MAX_STACK, pagedObject.getAmount()))
                .replaceAll("{0}", pagedObject.getPosition() + "")
                .replaceAll("{1}", getFilteredPlayerName(pagedObject.getPlayer() == null ? CONSOLE_UUID : pagedObject.getPlayer()))
                .replaceAll("{2}", (pagedObject.getAction() == ChestAction.ADDED ?
                        Message.CHEST_LOGS_ITEM_ADDED : Message.CHEST_LOGS_ITEM_REMOVED).getMessage(inventoryViewer.getUserLocale()))
                .replaceAll("{3}", pagedObject.getDate())
                .replaceAll("{4}", pagedObject.getAmount() + "")
                .replaceAll("{5}", Formatters.CAPITALIZED_FORMATTER.format(pagedObject.getItemType().name()))
                .replaceAll("{6}", (pagedObject.getChestIndex() + 1) + "")
                .build(inventoryViewer);
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

    public static class Builder extends PagedMenuTemplateButtonImpl.AbstractBuilder<MenuChestLogs.View, ChestTransaction> {

        @Override
        public PagedMenuTemplateButton<MenuChestLogs.View, ChestTransaction> build() {
            return new PagedMenuTemplateButtonImpl<>(buttonItem, clickSound, commands, requiredPermission,
                    lackPermissionSound, nullItem, getButtonIndex(), ChestLogsPagedObjectButton.class, ChestLogsPagedObjectButton::new);
        }

    }

}
