package com.bitpoint.homeservercontrol;

final class RemoteEntry {
    final String name;
    final String path;
    final boolean directory;
    final long size;
    final int mtime;

    RemoteEntry(String name, String path, boolean directory, long size, int mtime) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.mtime = mtime;
    }
}
