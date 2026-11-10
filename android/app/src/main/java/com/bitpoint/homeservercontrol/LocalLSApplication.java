package com.bitpoint.homeservercontrol;
public final class LocalLSApplication extends android.app.Application {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.apply(base));}
}
