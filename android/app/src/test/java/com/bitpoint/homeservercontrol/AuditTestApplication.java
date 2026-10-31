package com.bitpoint.homeservercontrol;

/** Load Robolectric native SQLite on the test lifecycle thread before cancellable UI workers. */
public final class AuditTestApplication extends android.app.Application {
    @Override public void onCreate(){
        super.onCreate();
        try(android.database.sqlite.SQLiteDatabase database=android.database.sqlite.SQLiteDatabase.create(null)){
            if(!database.isOpen())throw new AssertionError("Native SQLite fixture failed to initialize");
        }
    }
}
