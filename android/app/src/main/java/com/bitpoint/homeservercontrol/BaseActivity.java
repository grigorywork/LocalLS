package com.bitpoint.homeservercontrol;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;

/** Compatibility for recent Android while retaining Android 7 support. */
public abstract class BaseActivity extends Activity {
    private static final int REQUEST_LOCAL_NETWORK = 7301;
    private static final int REQUEST_TRAY_NOTIFICATION = 7302;
    private int appliedTheme,appliedAppearance;
    private boolean screenActive;

    @Override
    protected void onCreate(Bundle state) {
        appliedTheme = ThemeCatalog.style(this);
        setTheme(appliedTheme);ThemeCatalog.applyOverlays(this);appliedAppearance=ThemeCatalog.signature(this);
        super.onCreate(state);
        if(ThemeCatalog.index(this)==4&&Build.VERSION.SDK_INT>=26){
            getWindow().setNavigationBarColor(ThemeCatalog.color(this,com.bitpoint.homeservercontrol.ui.R.attr.hscBackground));
            View decor=getWindow().getDecorView();decor.setSystemUiVisibility(decor.getSystemUiVisibility()|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (Build.VERSION.SDK_INT >= 37
                && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQUEST_LOCAL_NETWORK);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        screenActive = true;
        polishControls();
        NotificationHelper.clearTray(this);
        if (appliedAppearance != ThemeCatalog.signature(this) && canRecreateForTheme()) recreate();
    }

    @Override protected void onPause() { screenActive=false;super.onPause(); }
    protected boolean isUiActive() { return screenActive && !isFinishing() && !isDestroyed(); }
    protected final void polishControls(){
        View root=findViewById(android.R.id.content);
        CompactActions.arrange(root);IconButtons.decorate(root);ThemeCatalog.surfaces(root);
    }

    protected boolean canRecreateForTheme() { return true; }

    protected void minimizeToTray() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_TRAY_NOTIFICATION);
            return;
        }
        if (!NotificationHelper.showTray(this)) Toast.makeText(this,
                "Уведомления выключены. Вернуться можно через значок LocalLS.", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    @Override
    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);
        configureContentView();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        configureContentView();
    }

    @Override
    public void setContentView(View view, android.view.ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        configureContentView();
    }

    private void configureContentView() {
        polishControls();
        if (Build.VERSION.SDK_INT >= 35) {
            View content = findViewById(android.R.id.content);
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            content.requestApplyInsets();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_TRAY_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) minimizeToTray();
            else Toast.makeText(this, "Для сворачивания в шторку разреши уведомления LocalLS.", Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_LOCAL_NETWORK && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Для подключения к серверу разреши доступ к локальной сети в настройках приложения.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
