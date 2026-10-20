package com.bitpoint.homeservercontrol;
import java.io.File;
import java.io.IOException;

/** Local UI uses the same private share boundary as the network server. */
final class SharedFolderAccess {
    static File within(File root, File requested) throws IOException {
        File base=root.getCanonicalFile(), file=requested.getCanonicalFile();
        if(!file.equals(base) && !file.getPath().startsWith(base.getPath()+File.separator))
            throw new IOException("Outside server folder");
        return file;
    }
}
