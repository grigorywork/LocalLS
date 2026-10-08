package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

/** Compact native controls: the icon is above its small, accessible text label. */
final class IconButtons {
    static void decorate(View view) {
        if (view instanceof Button && !(view instanceof CompoundButton)) {
            Button button = (Button) view;
            String name = "";
            try { name = view.getResources().getResourceEntryName(view.getId()); }
            catch (android.content.res.Resources.NotFoundException ignored) { }
            apply(button, icon(name));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) decorate(group.getChildAt(i));
        }
    }
    static void apply(Button button, int resource) {
        Context context = button.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(context.getColor(R.color.button_text));
        int size = Math.round(24 * density);
        icon.setBounds(0, 0, size, size);
        button.setCompoundDrawables(null, icon, null, null);
        button.setCompoundDrawablePadding(Math.round(2 * density));
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMaxLines(2);
        button.setPadding(Math.round(4*density), Math.round(4*density), Math.round(4*density), Math.round(4*density));
        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params != null && params.height > 0 && params.height < Math.round(60*density)) {
            params.height = Math.round(60*density);
            button.setLayoutParams(params);
        }
        button.setMinimumHeight(Math.round(60*density));
    }
    private static int icon(String name) {
        String id = name.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("minimize")) return R.drawable.ic_action_download;
        if (id.contains("export")) return R.drawable.ic_action_upload;
        if (id.contains("import")) return R.drawable.ic_action_download;
        if (id.contains("download")) return R.drawable.ic_action_download;
        if (id.contains("select")) return R.drawable.ic_action_check;
        if (id.contains("verify")) return R.drawable.ic_action_check;
        if (id.contains("close") || id.contains("back") || id.contains("cancel")) return R.drawable.ic_action_close;
        if ((id.contains("server") && !id.contains("password")) || id.contains("agentstatus")) return R.drawable.ic_action_server;
        if (id.contains("restart") || id.contains("refresh")) return R.drawable.ic_action_restart;
        if (id.contains("stop")) return R.drawable.ic_action_stop;
        if (id.contains("agentstart")) return R.drawable.ic_action_power;
        if (id.contains("theme")) return R.drawable.ic_action_palette;
        if (id.contains("vpn")) return R.drawable.ic_action_vpn;
        if (id.contains("clear") || id.contains("delete")) return R.drawable.ic_action_delete;
        if (id.contains("send")) return R.drawable.ic_action_send;
        if (id.contains("attach") || id.contains("upload")) return R.drawable.ic_action_attach;
        if (id.contains("mkdir")) return R.drawable.ic_action_new_folder;
        if (id.contains("filesup")) return R.drawable.ic_action_up;
        if (id.contains("files") || id.contains("folder") || id.contains("solid")) return R.drawable.ic_action_folder;
        if (id.contains("network") || id.contains("ipbutton")) return R.drawable.ic_action_network;
        if (id.contains("settings")) return R.drawable.ic_action_settings;
        if (id.contains("history") || id.contains("uptime")) return R.drawable.ic_action_history;
        if (id.contains("diagnostics") || id.contains("iperf")) return R.drawable.ic_action_diagnostics;
        if (id.contains("save")) return R.drawable.ic_action_save;
        if (id.contains("copy")) return R.drawable.ic_action_copy;
        if (id.contains("minimize")) return R.drawable.ic_action_download;
        if (id.contains("export")) return R.drawable.ic_action_upload;
        if (id.contains("import")) return R.drawable.ic_action_download;
        if (id.contains("trust")) return R.drawable.ic_action_key;
        if (id.contains("check") || id.contains("verify") || id.contains("sshd")) return R.drawable.ic_action_check;
        if (id.contains("help") || id.contains("discovery")) return R.drawable.ic_action_info;
        return R.drawable.ic_action_grid;
    }
}
