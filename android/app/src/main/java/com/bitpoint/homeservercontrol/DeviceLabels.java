package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.content.res.Configuration;

final class DeviceLabels {
    static DeviceKind local(Context context){
        Configuration c=context.getResources().getConfiguration();
        // Use smallest width, rather than the current landscape window width.
        return c.smallestScreenWidthDp>=600 || (c.smallestScreenWidthDp==0 &&
                (c.screenLayout&Configuration.SCREENLAYOUT_SIZE_MASK)>=Configuration.SCREENLAYOUT_SIZE_LARGE)
                ?DeviceKind.TABLET:DeviceKind.PHONE;
    }
}
