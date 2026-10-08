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
    private int appliedTheme;
    private int appliedIconShape;
    private boolean screenActive;

    @Override
    protected void onCreate(Bundle state) {
        appliedTheme = ThemeCatalog.style(this);
        appliedIconShape = IconShapeCatalog.index(this);
        setTheme(appliedTheme);
        super.onCreate(state);
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
        if (appliedIconShape != IconShapeCatalog.index(this)) refreshIconStyle();
        NotificationHelper.clearTray(this);
        if (appliedTheme != ThemeCatalog.style(this) && canRecreateForTheme()) recreate();
    }

    @Override protected void onPause() { screenActive=false;super.onPause(); }
    protected boolean isUiActive() { return screenActive && !isFinishing() && !isDestroyed(); }
    protected void refreshIconStyle() {
        appliedIconShape=IconShapeCatalog.index(this);
        IconButtons.decorate(findViewById(android.R.id.content));
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
        IconButtons.decorate(findViewById(android.R.id.content));
        if (Build.VERSION.SDK_INT >= 35) {
            View content = findViewById(android.R.id.content);
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
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
