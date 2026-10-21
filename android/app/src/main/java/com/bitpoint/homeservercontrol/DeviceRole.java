package com.bitpoint.homeservercontrol;

import android.content.Context;

/** One role policy for client controls and client monitoring; local server work is independent. */
final class DeviceRole {
    static boolean serverOnly(Context c){
        var p=c.getSharedPreferences(EmbeddedServerService.PREFS,0);
        return p.getBoolean("server_role",false) && !p.getBoolean("show_client",false);
    }
}
