package com.bgsoftware.superiorskyblock.core.database.loader.sql.upgrade.v5;

import com.bgsoftware.superiorskyblock.core.database.sql.DBSession;

public class DatabaseUpgrade_V5 implements Runnable {

    public static final DatabaseUpgrade_V5 INSTANCE = new DatabaseUpgrade_V5();

    private DatabaseUpgrade_V5() {

    }

    @Override
    public void run() {
        DBSession.addColumn("chest_transactions", "item_name", "TEXT");
    }

}
