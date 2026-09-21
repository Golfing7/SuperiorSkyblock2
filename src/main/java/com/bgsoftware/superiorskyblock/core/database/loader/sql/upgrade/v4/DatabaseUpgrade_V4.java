package com.bgsoftware.superiorskyblock.core.database.loader.sql.upgrade.v4;

import com.bgsoftware.superiorskyblock.core.database.sql.DBSession;

public class DatabaseUpgrade_V4 implements Runnable {

    public static final DatabaseUpgrade_V4 INSTANCE = new DatabaseUpgrade_V4();

    private DatabaseUpgrade_V4() {

    }

    @Override
    public void run() {
        DBSession.addColumn("islands", "spawner_level_counts", "LONGTEXT");
    }

}
