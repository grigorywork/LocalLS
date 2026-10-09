package com.bitpoint.homeservercontrol;

import com.bitpoint.homeservercontrol.ui.R;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

/** Compact tiles with captions, and menu rows with a leading icon and expandable arrow. */
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
        boolean tile = "tile".equals(button.getTag());
        boolean header = "drawer_header".equals(button.getTag());
        // Other weighted toolbars keep an icon above its short caption.
        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (!header && params != null && params.width == 0) tile = true;
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(context.getColor(R.color.button_text));
        int shape = IconShapeCatalog.index(context);
        if (shape != 0) icon = new IconBadgeDrawable(icon, shape);
        int size = Math.round((shape == 0 ? 20 : 24) * density);
        icon.setBounds(0, 0, size, size);
        Drawable arrow = null;
        if (header) {
            arrow = context.getDrawable(button.isSelected() ? R.drawable.ic_action_expand : R.drawable.ic_action_chevron).mutate();
            arrow.setTint(context.getColor(R.color.button_text));
            int arrowSize = Math.round(16*density);arrow.setBounds(0,0,arrowSize,arrowSize);
        }
        if (tile) button.setCompoundDrawables(null,icon,null,null);
        else button.setCompoundDrawablesRelative(icon,null,arrow,null);
        button.setCompoundDrawablePadding(Math.round((tile?4:8)*density));
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(tile ? R.dimen.text_tile : R.dimen.text_caption));
        button.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        button.setLetterSpacing(0);
        button.setIncludeFontPadding(false);
        // Reserve the same caption area for one- and two-line tiles.
        button.setMinLines(tile ? 2 : 1);
        button.setAllCaps(false);button.setMaxLines(2);
        button.setGravity(tile?Gravity.CENTER:Gravity.CENTER_VERTICAL|Gravity.START);
        button.setPaddingRelative(Math.round((tile ? 6 : 12) * density), Math.round(4 * density),
                Math.round((tile ? 6 : 12) * density), Math.round(4 * density));
        int height = Math.round((tile ? 64 : 48) * density);
        int textHeight = button.getLineHeight() * 2;
        int contentHeight = tile ? size + button.getCompoundDrawablePadding() + textHeight
                : Math.max(size, textHeight);
        height = Math.max(height, contentHeight + button.getPaddingTop() + button.getPaddingBottom());
        if (params!=null && (params.height>0 || params.height==ViewGroup.LayoutParams.WRAP_CONTENT)) {
            params.height=height;button.setLayoutParams(params);
        }
        button.setMinimumHeight(height);
    }
    private static int icon(String name) {
        String id = name.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("save")) return R.drawable.ic_action_save;
        if (id.contains("minimize")) return R.drawable.ic_action_download;
        if (id.contains("export")) return R.drawable.ic_action_upload;
        if (id.contains("import")) return R.drawable.ic_action_download;
        if (id.contains("download")) return R.drawable.ic_action_download;
        if (id.contains("select")) return R.drawable.ic_action_check;
        if (id.contains("verify")) return R.drawable.ic_action_check;
        if (id.contains("back")) return R.drawable.ic_action_back;
        if (id.contains("close") || id.contains("cancel")) return R.drawable.ic_action_close;
        if (id.contains("restart") || id.contains("refresh")) return R.drawable.ic_action_restart;
        if (id.contains("wifi")) return R.drawable.ic_action_network;
        if (id.contains("appearance") || id.contains("shape")) return R.drawable.ic_action_palette;
        if (id.contains("connection")) return R.drawable.ic_action_network;
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
