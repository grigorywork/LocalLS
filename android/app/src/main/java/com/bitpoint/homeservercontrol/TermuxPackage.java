package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

/** Supported upstream signing identities; never accepts a package name alone. */
final class TermuxPackage {
    // Upstream TermuxConstants.java, verified 2026-10-09.
    static final String FDROID = "228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42";
    static final String GITHUB = "b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1";
    static final String PLAY = "738f0a30a04d3c8a1be304af18d0779bcf3ea88fb60808f657a3521861c2ebf9";
    static final String DEVS = "f7a038eb551f1be8fdf388686b784abab4552a5d82df423e3d8f1b5cbe1c69ae";
    static boolean accepted(String digest) {
        return FDROID.equals(digest) || GITHUB.equals(digest) || PLAY.equals(digest) || DEVS.equals(digest);
    }
    @SuppressWarnings("deprecation")
    static boolean supported(Context context) {
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = context.getPackageManager().getPackageInfo(TermuxSetup.PACKAGE, flags);
            Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signatures == null || signatures.length != 1) return false;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder value = new StringBuilder();
            for (byte b : hash) value.append(String.format(Locale.ROOT, "%02x", b & 255));
            if (!accepted(value.toString())) return false;
            android.content.pm.ProviderInfo provider = context.getPackageManager().resolveContentProvider("com.termux.documents", 0);
            return provider != null && TermuxSetup.PACKAGE.equals(provider.packageName);
        } catch (Exception e) { return false; }
    }
}
