package com.tuantuan.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_REQUEST = 2026;
    private WebView page;
    private boolean awaitingOverlay;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        page = new WebView(this);
        page.setBackgroundColor(Color.rgb(247,242,238));
        page.getSettings().setJavaScriptEnabled(true);
        page.getSettings().setDomStorageEnabled(true);
        page.getSettings().setAllowFileAccess(true);
        page.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        page.addJavascriptInterface(new Bridge(), "Android");
        page.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("https://chatgpt.com")) { openChat(); return true; }
                return !url.startsWith("file:///android_asset/");
            }
        });
        setContentView(page);
        page.loadUrl("file:///android_asset/index.html");
    }

    public final class Bridge {
        @JavascriptInterface public void startPet() {
            runOnUiThread(() -> requestStart());
        }
        @JavascriptInterface public void stopPet() {
            runOnUiThread(() -> { stopService(new Intent(MainActivity.this, PetService.class)); message("团团已经休息啦"); });
        }
        @JavascriptInterface public void openChat() {
            runOnUiThread(() -> MainActivity.this.openChat());
        }
        @JavascriptInterface public void saveSettings(String form, int size, boolean walk) {
            SharedPreferences prefs = getSharedPreferences("tuan_settings", MODE_PRIVATE);
            prefs.edit().putString("form", "cat".equals(form)?"cat":"human")
                .putInt("size", Math.max(70,Math.min(130,size)))
                .putBoolean("autoWalk",walk).apply();
        }
    }
    private void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    private void requestStart() {
        if (!Settings.canDrawOverlays(this)) {
            awaitingOverlay = true;
            message("请允许团团显示在其他应用上层");
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            startActivity(i);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            awaitingOverlay = false;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQUEST);
            return;
        }
        startOverlay();
    }
    @Override protected void onResume() {
        super.onResume();
        if (awaitingOverlay && Settings.canDrawOverlays(this)) {
            awaitingOverlay = false;
            requestStart();
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == NOTIFICATION_REQUEST) startOverlay();
    }
    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(this,PetService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            message("团团出来陪你啦！返回桌面看看 ♡");
        } catch (RuntimeException e) {
            message("启动失败，请检查悬浮窗权限与省电设置");
        }
    }
    private void openChat() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { message("暂时无法打开 ChatGPT"); }
    }
    @Override protected void onDestroy() {
        if (page!=null) {
            page.removeJavascriptInterface("Android");
            page.destroy(); page=null;
        }
        super.onDestroy();
    }
}
