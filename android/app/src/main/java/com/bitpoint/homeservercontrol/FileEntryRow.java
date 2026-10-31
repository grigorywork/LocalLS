package com.bitpoint.homeservercontrol;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;

/** The checkbox selects; the rest of a folder row navigates. Neither gesture does both. */
final class FileEntryRow extends LinearLayout implements Checkable {
    interface Selection { void changed(boolean checked); }
    private final CheckBox checkbox;
    private final TextView name,detail;
    private final ImageView icon;
    private boolean mirroring;
    private final Button more;

    FileEntryRow(Context context) {
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4),dp(4),dp(4),dp(4));setMinimumHeight(dp(64));
        checkbox=new CheckBox(context);checkbox.setFocusable(false);checkbox.setButtonTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscAccent),ThemeCatalog.color(context,com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary)}));checkbox.setMinimumWidth(dp(48));checkbox.setMinimumHeight(dp(48));
        addView(checkbox,new LayoutParams(dp(48),-1));
        icon=new ImageView(context);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams image=new LayoutParams(dp(32),dp(32));image.setMargins(dp(4),0,dp(12),0);addView(icon,image);
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(VERTICAL);
        addView(labels,new LayoutParams(0,-2,1));
        name=new TextView(context);name.setTextSize(18);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
        detail=new TextView(context);detail.setTextSize(13);labels.addView(detail);
        more=new Button(context);more.setText("⋮");more.setTag("file_overflow");more.setTextSize(22);more.setFocusable(false);more.setContentDescription("Действия с файлом");addView(more,new LayoutParams(dp(48),dp(48)));IconButtons.decorate(more);
        setBackgroundResource(android.R.drawable.list_selector_background);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void bind(FileNodes.Node node,boolean checked,Selection selection) {
        checkbox.setOnCheckedChangeListener(null);setChecked(checked);
        name.setText(node.name);detail.setText(node.directory?"Папка":node.size<0?"Размер неизвестен":android.text.format.Formatter.formatShortFileSize(getContext(),node.size));
        name.setTextColor(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextPrimary));
        detail.setTextColor(ThemeCatalog.color(getContext(),com.bitpoint.homeservercontrol.ui.R.attr.hscTextSecondary));
        icon.clearColorFilter();icon.setImageDrawable(FileTypeIcons.drawable(getContext(),node.name,node.directory));
        checkbox.setContentDescription("Выбрать «"+node.name+"»");
        checkbox.setOnCheckedChangeListener((button,value)->{if(!mirroring)selection.changed(value);});
    }
    void actions(Runnable action){more.setOnClickListener(v->action.run());}
    @Override public boolean isChecked(){return checkbox.isChecked();}
    @Override public void setChecked(boolean value){
        // ListView mirrors the model into recycled rows. Only user taps publish a selection.
        mirroring=true;try{checkbox.setChecked(value);setActivated(value);}finally{mirroring=false;}
    }
    @Override public void toggle(){setChecked(!isChecked());}
}
