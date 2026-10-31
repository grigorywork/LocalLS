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
            if(!listAction(button))button.setBackground(new ButtonOutlineDrawable(button.getContext(),button.getBackground()));
            String name = "";
            try { name = view.getResources().getResourceEntryName(view.getId()); }
            catch (android.content.res.Resources.NotFoundException ignored) { }
            if("file_overflow".equals(button.getTag())){button.setCompoundDrawables(null,null,null,null);button.setGravity(Gravity.CENTER);button.setTextSize(22);if("⋮".contentEquals(button.getText())){button.setIncludeFontPadding(false);button.setPadding(0,0,0,0);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22*button.getResources().getDisplayMetrics().density);}button.setMinHeight(Math.round(48*button.getResources().getDisplayMetrics().density));}
            else if("dashboard_tab".equals(button.getTag())||"file_tab".equals(button.getTag())){
                button.setCompoundDrawables(null,null,null,null);button.setGravity(Gravity.CENTER);button.setAllCaps(false);
                button.setSingleLine(false);
                button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,"file_tab".equals(button.getTag())?16:14);button.setMinHeight(Math.round(48*button.getResources().getDisplayMetrics().density));button.setMaxLines(2);
            }else if("qr_action".equals(button.getTag())){
                String caption=button.getText().toString();
                int image=caption.startsWith("Обновить")?R.drawable.ic_action_restart:caption.startsWith("Сохранить")?R.drawable.ic_action_save:
                        caption.startsWith("Разрешённые")?R.drawable.ic_action_key:caption.startsWith("Ключ")?R.drawable.ic_action_key:
                        caption.startsWith("Включить")?R.drawable.ic_action_power:caption.startsWith("Назад")?R.drawable.ic_action_back:
                        caption.contains("QR")?R.drawable.ic_action_qr:caption.contains("файлы")?R.drawable.ic_action_folder:R.drawable.ic_action_check;
                apply(button,image);
            }else if("queue_action".equals(button.getTag())){
                apply(button,button.getText().toString().startsWith("Очистить")?R.drawable.ic_action_delete:R.drawable.ic_action_close);
                button.setTextSize(12);button.setGravity(Gravity.CENTER);button.setBackgroundTintList(null);button.setBackground(SurfaceStyle.panel(button.getContext()));
                button.setTextColor(rowColours(ThemeCatalog.color(button.getContext(),R.attr.hscTextPrimary),ThemeCatalog.color(button.getContext(),R.attr.hscTextSecondary)));
                Drawable glyph=button.getCompoundDrawablesRelative()[0];if(glyph!=null)glyph.setTintList(rowColours(ThemeCatalog.color(button.getContext(),R.attr.hscAccent),ThemeCatalog.color(button.getContext(),R.attr.hscTextSecondary)));
            }else if("file_toolbar".equals(button.getTag())){
                String caption=button.getText().toString();
                int image=caption.equals("Выше")?R.drawable.ic_action_up:caption.equals("Обновить")?R.drawable.ic_action_restart:
                        caption.equals("Меню")?R.drawable.ic_action_menu:caption.startsWith("На сервер")||Boolean.TRUE.equals(button.getTag(R.id.fileTransferUpload))?R.drawable.ic_action_upload:R.drawable.ic_action_download;
                apply(button,image);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,16);
            }else apply(button, name.equals("serverQrToggle")?(button.getText().toString().startsWith("Скрыть")?R.drawable.ic_action_eye_off:R.drawable.ic_action_eye):name.isEmpty()?captionIcon(button.getText().toString()):icon(name));
        }
        if(view instanceof Button && !(view instanceof CompoundButton)){
            Button b=(Button)view;String tag=String.valueOf(b.getTag());
            if("dashboard_tab".equals(tag))dashboardTab(b);
            else if("file_tab".equals(tag))fileTab(b,b.isSelected());
            else if("file_overflow".equals(tag)&&ThemeCatalog.glass(b.getContext())){b.setBackgroundTintList(null);b.setBackground(new FileControlDrawable(b.getContext(),false));b.setTextColor(ThemeCatalog.color(b.getContext(),R.attr.hscTextPrimary));}
            else if("file_toolbar".equals(tag)&&ThemeCatalog.glass(b.getContext())){
                b.setBackgroundTintList(null);b.setBackground(new FileControlDrawable(b.getContext(),false));b.setTextColor(rowColours(ThemeCatalog.color(b.getContext(),R.attr.hscTextPrimary),ThemeCatalog.color(b.getContext(),R.attr.hscTextSecondary)));
                if(b instanceof CenteredActionButton)((CenteredActionButton)b).tintTileIcon(rowColours(ThemeCatalog.color(b.getContext(),R.attr.hscAccent),ThemeCatalog.color(b.getContext(),R.attr.hscTextSecondary)));
            }
            if(b.getParent() instanceof View&&"applicationToolsCard".equals(resourceName((View)b.getParent()))){
                int normal=ThemeCatalog.color(b.getContext(),"checkButton".equals(resourceName(b))?R.attr.hscAccent:R.attr.hscTextPrimary);
                android.content.res.ColorStateList colours=rowColours(normal,ThemeCatalog.color(b.getContext(),R.attr.hscTextSecondary));
                b.setBackgroundTintList(null);b.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                b.setTextColor(colours);
                if(b instanceof CenteredActionButton)((CenteredActionButton)b).tintTileIcon(colours);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) decorate(group.getChildAt(i));
        }
    }
    static void dashboardTab(Button button){
        Context c=button.getContext();if(ThemeCatalog.index(c)!=4)return;
        int fill=ThemeCatalog.color(c,"homeClientTab".equals(resourceName(button))?R.attr.hscSecondaryButton:R.attr.hscButton);
        if(button.getBackgroundTintList()!=null)fill=button.getBackgroundTintList().getColorForState(button.getDrawableState(),fill);
        button.setTextColor(android.graphics.Color.luminance(fill)>.4f?ThemeCatalog.color(c,R.attr.hscTextPrimary):c.getColor(R.color.button_text));
    }
    static void fileTab(Button button,boolean selected){if(ThemeCatalog.glass(button.getContext())){button.setBackgroundTintList(null);button.setBackground(new FileControlDrawable(button.getContext(),selected));button.setTextColor(ThemeCatalog.color(button.getContext(),R.attr.hscTextPrimary));}}
    static void apply(Button button, int resource) {
        if(listAction(button)){applyListRow(button,resource);return;}
        Context context = button.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        boolean tile = "tile".equals(button.getTag());
        boolean header = "drawer_header".equals(button.getTag());
        boolean compact = "compact_action".equals(button.getTag())||Boolean.TRUE.equals(button.getTag(R.id.compact_action_marker));
        // Other weighted toolbars keep an icon above its short caption.
        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (!header && !compact && !"dashboard_row".equals(button.getTag()) && params != null && params.width == 0) tile = true;
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(context.getColor(R.color.button_text));
        button.setBackground(new ButtonOutlineDrawable(context,button.getBackground()));
        int size = Math.round(20 * density);
        icon.setBounds(0, 0, size, size);
        Drawable arrow = null;
        if (header) {
            arrow = context.getDrawable(button.isSelected() ? R.drawable.ic_action_expand : R.drawable.ic_action_chevron).mutate();
            arrow.setTint(context.getColor(R.color.button_text));
            int arrowSize = Math.round(16*density);arrow.setBounds(0,0,arrowSize,arrowSize);
        }
        if(!tile&&button instanceof CenteredActionButton)((CenteredActionButton)button).setTileIcon(null);
        if (tile && button instanceof CenteredActionButton) ((CenteredActionButton) button).setTileIcon(icon);
        else if (tile) button.setCompoundDrawables(null,icon,null,null);
        else button.setCompoundDrawablesRelative(icon,null,arrow,null);
        button.setCompoundDrawablePadding(Math.round((tile?4:compact?6:8)*density));
        if(compact)button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,14);
        else button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(tile ? R.dimen.text_tile : R.dimen.text_caption));
        button.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        button.setLetterSpacing(0);
        button.setIncludeFontPadding(false);
        // Reserve the same caption area for one- and two-line tiles.
        button.setMinLines(tile ? 2 : 1);
        button.setAllCaps(false);button.setMaxLines("compact_action".equals(button.getTag())?2:Integer.MAX_VALUE);
        button.setGravity(tile?Gravity.CENTER:Gravity.CENTER_VERTICAL|Gravity.START);
        button.setPaddingRelative(Math.round((tile ? 6 : compact?10:12) * density), Math.round(4 * density),
                Math.round((tile ? 6 : compact?10:12) * density), Math.round(4 * density));
        int height = Math.round((tile ? 64 : 48) * density);
        int textHeight = button.getLineHeight() * 2;
        int contentHeight = tile ? size + button.getCompoundDrawablePadding() + textHeight
                : Math.max(size, textHeight);
        height = Math.max(height, contentHeight + button.getPaddingTop() + button.getPaddingBottom());
        if (params!=null && (params.height>0 || params.height==ViewGroup.LayoutParams.WRAP_CONTENT)) {
            // Menu rows need natural height at half-screen width and large fonts.
            // Tiles reserve at least two lines; narrow drawers may need more.
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            button.setLayoutParams(params);
        }
        button.setMinimumHeight(height);
    }
    private static String resourceName(View view){try{return view.getResources().getResourceEntryName(view.getId());}catch(android.content.res.Resources.NotFoundException e){return "";}}
    private static boolean listAction(Button button){return Boolean.TRUE.equals(button.getTag(R.id.compact_action_marker));}
    private static android.content.res.ColorStateList rowColours(int normal,int disabled){
        return new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{disabled,normal});
    }
    private static void applyListRow(Button button,int resource){
        Context context=button.getContext();float density=context.getResources().getDisplayMetrics().density;
        boolean server=button.getTag()!=null&&button.getTag().toString().startsWith("server_panel_");
        boolean drawer=false;for(android.view.ViewParent parent=button.getParent();parent instanceof View;parent=parent.getParent())if("action_drawer".equals(((View)parent).getTag())){drawer=true;break;}
        int accent=ThemeCatalog.color(context,R.attr.hscAccent),text=ThemeCatalog.color(context,R.attr.hscTextPrimary),muted=ThemeCatalog.color(context,R.attr.hscTextSecondary);
        button.setBackgroundTintList(null);button.setBackground(new ListActionDrawable(context,"serverQrToggle".equals(resourceName(button)),!java.util.Arrays.asList("serverReceiveButton","connectionCheckButton","localServerQuickButton","serverBackgroundButton","settingsBackButton","networkBackButton","settingsSaveButton").contains(resourceName(button)),server?50:52,server?0:12));
        button.setStateListAnimator(null);button.setElevation(0);
        Drawable icon=context.getDrawable(resource).mutate();icon.setTintList(rowColours(accent,muted));
        int size=Math.round(22*density);icon.setBounds(0,0,size,size);
        Drawable arrow=null;
        if("drawer_header".equals(button.getTag())||navigation(button,resource)){
            arrow=context.getDrawable(button.isSelected()&&"drawer_header".equals(button.getTag())?R.drawable.ic_action_expand:R.drawable.ic_action_chevron).mutate();
            arrow.setTintList(rowColours(muted,muted));int arrowSize=Math.round(16*density);arrow.setBounds(0,0,arrowSize,arrowSize);
        }
        if(button instanceof CenteredActionButton)((CenteredActionButton)button).setTileIcon(null);
        button.setCompoundDrawablesRelative(icon,null,arrow,null);button.setCompoundDrawablePadding(Math.round((drawer?6:16)*density));
        button.setTextColor(rowColours(text,muted));button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,server?14:16);
        button.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));button.setLetterSpacing(0);
        button.setSingleLine(false);button.setMinLines(1);button.setMaxLines(Integer.MAX_VALUE);button.setEllipsize(null);
        button.setAllCaps(false);button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        button.setPaddingRelative(Math.round((drawer?6:12)*density),Math.round((server?6:14)*density),Math.round((drawer?6:12)*density),Math.round((server?6:14)*density));
        button.setMinHeight(Math.round((server?48:56)*density));button.setMinimumHeight(Math.round((server?48:56)*density));
        if("server_panel_power".equals(button.getTag())&&button instanceof CenteredActionButton){((CenteredActionButton)button).setTileIcon(icon);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,12);button.setGravity(Gravity.CENTER);button.setPadding(0,0,0,0);button.setCompoundDrawablePadding(Math.round(4*density));button.setBackground(new CompactControlDrawable(context));button.setClipToOutline(true);}
        if("serverQrToggle".equals(resourceName(button))){button.setBackground(new StripActionDrawable(context));button.setClipToOutline(false);button.setCompoundDrawablePadding(Math.round(6*density));button.setTextColor(rowColours(text,muted));button.setGravity(Gravity.CENTER);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,13);button.setPadding(Math.round(4*density),0,Math.round(4*density),0);}
        if("connectionCheckButton".equals(resourceName(button))){Drawable callout=button.getCompoundDrawablesRelative()[0];if(callout!=null){callout.setBounds(0,0,Math.round(32*density),Math.round(32*density));Drawable[] rowIcons=button.getCompoundDrawablesRelative();button.setCompoundDrawablesRelative(callout,rowIcons[1],rowIcons[2],rowIcons[3]);}button.setPaddingRelative(Math.round(12*density),Math.round(8*density),Math.round(12*density),Math.round(8*density));}
        if(java.util.Arrays.asList("connectionCloseButton","connectionCheckButton","settingsBackButton","networkBackButton").contains(resourceName(button))){button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,18);button.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));}
        if("localServerQuickButton".equals(resourceName(button)))button.setCompoundDrawablesRelative(icon,null,null,null);
        if("server_panel_caption".equals(button.getTag())){button.setTextColor(rowColours(muted,muted));Drawable tail=context.getDrawable(R.drawable.ic_action_chevron).mutate();tail.setTint(muted);tail.setBounds(0,0,Math.round(14*density),Math.round(14*density));button.setCompoundDrawablesRelative(null,null,tail,null);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,11);button.setPadding(Math.round(4*density),0,0,0);button.setCompoundDrawablePadding(Math.round(4*density));button.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);}

    }
    private static boolean navigation(Button button,int icon){
        if("serverReceiveButton".equals(resourceName(button)))return true;
        String caption=button.getText().toString().toLowerCase(java.util.Locale.ROOT);
        for(String prefix:new String[]{"создать","сгенерировать","проверить","сохранить","сбросить","забыть","отозвать","удалить","обновить","повторить","экспорт","копировать","скопировать","показать","скрыть"})if(caption.startsWith(prefix))return false;

        return icon==R.drawable.ic_action_key||icon==R.drawable.ic_action_qr||icon==R.drawable.ic_action_folder||icon==R.drawable.ic_action_info||
                icon==R.drawable.ic_action_settings||icon==R.drawable.ic_action_palette||icon==R.drawable.ic_action_network||
                icon==R.drawable.ic_action_history||icon==R.drawable.ic_action_diagnostics||icon==R.drawable.ic_action_server;
    }
    private static int captionIcon(String caption){
        String text=caption.toLowerCase(java.util.Locale.ROOT);
        if(text.startsWith("назад")||text.startsWith("на главный"))return R.drawable.ic_action_back;
        if(text.startsWith("закрыть")||text.startsWith("отмен"))return R.drawable.ic_action_close;
        if(text.startsWith("обновить")||text.startsWith("повторить"))return R.drawable.ic_action_restart;
        if(text.startsWith("включить")||text.startsWith("остановить"))return R.drawable.ic_action_power;
        if(text.startsWith("скопировать")||text.startsWith("копировать"))return R.drawable.ic_action_copy;
        if(text.startsWith("переименовать"))return R.drawable.ic_action_settings;
        if(text.contains("инструкц")||text.startsWith("дополнительно"))return R.drawable.ic_action_info;
        if(text.startsWith("отозвать")||text.startsWith("удалить"))return R.drawable.ic_action_delete;
        if(text.contains("qr"))return R.drawable.ic_action_qr;
        if(text.startsWith("экспорт"))return R.drawable.ic_action_upload;
        if(text.startsWith("импорт")||text.startsWith("загрузить"))return R.drawable.ic_action_download;
        if(text.contains(".pub"))return R.drawable.ic_action_key;
        if(text.startsWith("сохранить"))return R.drawable.ic_action_save;
        if(text.startsWith("отправить"))return R.drawable.ic_action_send;
        if(text.contains("файл")||text.contains("папк")||text.contains("загрузки"))return R.drawable.ic_action_folder;
        if(text.contains("ключ")||text.contains("устройств"))return R.drawable.ic_action_key;
        if(text.startsWith("проверить")||text.contains("разрешить"))return R.drawable.ic_action_check;
        return R.drawable.ic_action_grid;
    }
    private static int icon(String name) {
        String id = name.toLowerCase(java.util.Locale.ROOT);
        if(id.equals("aboutappbutton"))return R.drawable.ic_action_info;
        if(id.equals("serverqrtoggle"))return R.drawable.ic_action_eye;
        if(id.contains("copy"))return R.drawable.ic_action_copy;
        if(id.equals("serverreceivebutton"))return R.drawable.ic_action_receive_folder;
        if(id.equals("localserverquickbutton")||id.equals("serverbackgroundbutton"))return R.drawable.ic_action_settings;
        if(id.equals("profiletransferbutton"))return R.drawable.ic_action_send;
        if(id.equals("preparetermuxbutton"))return R.drawable.ic_action_server;
        if(id.equals("networktestbutton"))return R.drawable.ic_action_check;
        if(id.equals("connectionclosebutton"))return R.drawable.ic_action_back;
        if (id.equals("connectioncheckbutton")) return R.drawable.ic_action_check;
        if (id.equals("transferqueuebutton")) return R.drawable.ic_action_queue;
        if (id.equals("toolsmenubutton")) return R.drawable.ic_action_navigation;
        if (id.contains("security") || id.contains("key")) return R.drawable.ic_action_key;
        if (id.contains("qr")) return R.drawable.ic_action_qr;
        if (id.contains("connectiondetails")) return R.drawable.ic_action_chart;
        if (id.contains("serverpower")) return R.drawable.ic_action_power;
        if (id.contains("serverlocalfiles") || id.contains("serverreceive")) return R.drawable.ic_action_folder;
        if (id.contains("serverbackground")) return R.drawable.ic_action_settings;
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
        if (id.contains("security") || id.contains("key")) return R.drawable.ic_action_key;
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
