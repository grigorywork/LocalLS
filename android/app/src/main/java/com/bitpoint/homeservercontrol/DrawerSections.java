package com.bitpoint.homeservercontrol;

import android.app.Activity;
import android.view.View;
import android.widget.Button;

final class DrawerSections {
    private final Activity activity;
    private final int[] headers={R.id.drawerConnectionHeader,R.id.drawerSettingsHeader,R.id.drawerWifiHeader,R.id.drawerAppearanceHeader,R.id.drawerToolsHeader,R.id.drawerVpnHeader};
    private final int[] panes={R.id.drawerConnectionPane,R.id.drawerSettingsPane,R.id.drawerWifiPane,R.id.drawerAppearancePane,R.id.drawerToolsPane,R.id.drawerVpnPane};
    DrawerSections(Activity activity) {
        this.activity=activity;
        for (int i=0;i<headers.length;i++) {
            final int selected=i;
            activity.findViewById(headers[i]).setOnClickListener(v -> toggle(selected));
        }
    }
    void open(int paneId) {
        for (int i=0;i<panes.length;i++) set(i,panes[i]==paneId);
    }
    void togglePane(int paneId) {
        for (int i=0;i<panes.length;i++) if (panes[i]==paneId) { toggle(i); return; }
    }
    private void toggle(int selected) {
        boolean expand=activity.findViewById(panes[selected]).getVisibility()!=View.VISIBLE;
        for (int i=0;i<panes.length;i++)set(i,i==selected && expand);
    }
    private void set(int i,boolean visible) {
        activity.findViewById(panes[i]).setVisibility(visible?View.VISIBLE:View.GONE);
        Button header=activity.findViewById(headers[i]);
        header.setSelected(visible);
        IconButtons.decorate(header);
    }
}
