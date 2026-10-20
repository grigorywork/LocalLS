package com.bitpoint.homeservercontrol;

/** Retain the half-width drawer at normal scale, allow room for accessibility text. */
final class DrawerLayoutPolicy {
    private DrawerLayoutPolicy() { }

    static int width(int available, float fontScale, float density) {
        int half = Math.max(1, available / 2);
        int maximum = Math.max(half, available - Math.round(24 * density));
        return Math.min(maximum, Math.max(half, Math.round(half * Math.max(1f, fontScale))));
    }
}
