package com.tuantuan.pet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class PetService extends Service {
    private static final String CHANNEL = "tuantuan_pet";
    public static final String ACTION_STOP = "com.tuantuan.pet.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager window;
    private WindowManager.LayoutParams params;
    private WebView sprite;
    private SharedPreferences prefs;
    private boolean ready=false, down=false, dragging=false, sleeping=false, cat=false, autoWalk=true;
    private int maxX, maxY, viewWidth, viewHeight, direction=1, origX, origY;
    private float startX, startY;
    private long downTime=0, lastTap=0;
    private Runnable queuedTap;

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        window=(WindowManager)getSystemService(WINDOW_SERVICE);
        prefs=getSharedPreferences("tuan_settings",MODE_PRIVATE);
        startNotification();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        if (sprite==null) createOverlay();
        return START_STICKY;
    }
    private void startNotification() {
        NotificationManager mgr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.createNotificationChannel(new NotificationChannel(CHANNEL,"团团桌面陪伴",NotificationManager.IMPORTANCE_LOW));
        PendingIntent home = PendingIntent.getActivity(this,1,
            new Intent(this,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent chatIntent = new Intent(this, CompanionActivity.class);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent chat = PendingIntent.getActivity(this,2,chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this,3,
            new Intent(this,PetService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this,CHANNEL)
            .setContentTitle("团团正在陪你 ♡")
            .setContentText("轻点摸头 · 双击变身 · 长按休息 · 拖动移动")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentIntent(home).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_send,"语音和AI聊天",chat)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"收起团团",stop)
            .build();
        if (Build.VERSION.SDK_INT>=34)
            startForeground(2026,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(2026,note);
    }
    private int dp(float x) { return Math.round(x*getResources().getDisplayMetrics().density); }
    private void computeBounds() {
        Point size=new Point(); window.getDefaultDisplay().getRealSize(size);
        int percent=prefs.getInt("size",100);
        viewWidth=dp(184f*percent/100f);
        viewHeight=dp(231f*percent/100f);
        maxX=Math.max(0,size.x-viewWidth);
        maxY=Math.max(0,size.y-viewHeight);
    }
    private void createOverlay() {
        computeBounds();
        cat="cat".equals(prefs.getString("form","human"));
        autoWalk=prefs.getBoolean("autoWalk",true);
        sprite=new WebView(this);
        sprite.setBackgroundColor(Color.TRANSPARENT);
        sprite.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        sprite.getSettings().setJavaScriptEnabled(true);
        sprite.getSettings().setAllowFileAccess(true);
        sprite.setVerticalScrollBarEnabled(false);
        sprite.setHorizontalScrollBarEnabled(false);
        sprite.setOverScrollMode(View.OVER_SCROLL_NEVER);
        params=new WindowManager.LayoutParams(viewWidth,viewHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        params.x=Math.max(0,Math.min(maxX,prefs.getInt("pet_x",Math.max(0,maxX-dp(18)))));
        params.y=Math.max(dp(16),Math.min(maxY,prefs.getInt("pet_y",Math.max(dp(50),maxY-dp(115)))));
        sprite.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView w, String url) {
                ready=true; script("setInitial('"+(cat?"cat":"human")+"')");
            }
        });
        sprite.setOnTouchListener(this::touch);
        sprite.loadUrl("file:///android_asset/pet.html");
        try { window.addView(sprite,params); handler.postDelayed(roam,95); }
        catch (Exception e) { stopSelf(); }
    }
    private void script(String js) {
        if (ready && sprite!=null) sprite.evaluateJavascript("(function(){"+js+";})()",null);
    }
    private final Runnable roam=new Runnable() {
        @Override public void run() {
            if (sprite==null) return;
            if (cat&&!sleeping&&autoWalk&&!down&&!dragging) {
                params.x += direction*dp(.6f);
                if (params.x > maxX-dp(8)) {
                    params.x=Math.max(0,maxX-dp(8));
                    direction=-1; script("setFacing(true)");
                }
                if (params.x < dp(8)) {
                    params.x=dp(8); direction=1; script("setFacing(false)");
                }
                try { window.updateViewLayout(sprite,params); } catch(Exception ignored){}
            }
            handler.postDelayed(this,95);
        }
    };
    private boolean touch(View v, MotionEvent e) {
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down=true; dragging=false;
                startX=e.getRawX();startY=e.getRawY();
                origX=params.x;origY=params.y;downTime=SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!down) return true;
                float deltaX=e.getRawX()-startX,deltaY=e.getRawY()-startY;
                if (Math.abs(deltaX)+Math.abs(deltaY)>dp(10)) dragging=true;
                if (dragging) {
                    params.x=Math.max(0,Math.min(maxX,origX+Math.round(deltaX)));
                    params.y=Math.max(dp(15),Math.min(maxY,origY+Math.round(deltaY)));
                    try { window.updateViewLayout(sprite,params); } catch(Exception ignored){}
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!down) return true;
                down=false;
                if (dragging) {
                    dragging=false;
                    prefs.edit().putInt("pet_x",params.x).putInt("pet_y",params.y).apply();
                    return true;
                }
                if (e.getX()>viewWidth*.75f && e.getY()<viewHeight*.22f) {
                    openChat();return true;
                }
                long hold=SystemClock.uptimeMillis()-downTime;
                if (hold>=670) {
                    if (!cat) {cat=true;sleeping=true;}
                    else sleeping=!sleeping;
                    script("petControl('sleep')");
                    prefs.edit().putString("form",cat?"cat":"human").apply();
                    return true;
                }
                long time=SystemClock.uptimeMillis();
                if (time-lastTap<360) {
                    if (queuedTap!=null) handler.removeCallbacks(queuedTap);
                    cat=!cat;sleeping=false;
                    script("petControl('transform')");
                    prefs.edit().putString("form",cat?"cat":"human").apply();
                    lastTap=0;
                } else {
                    lastTap=time;
                    queuedTap=()->script("petControl('pat')");
                    handler.postDelayed(queuedTap,365);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                down=false;dragging=false;return true;
            default: return true;
        }
    }
    private void openChat() {
        Intent i=new Intent(this,CompanionActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {startActivity(i);}catch(Exception ignored){}
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(roam);
        if (queuedTap!=null) handler.removeCallbacks(queuedTap);
        if (sprite!=null) {
            try {window.removeView(sprite);}catch(Exception ignored){}
            sprite.stopLoading();sprite.destroy();sprite=null;
        }
        super.onDestroy();
    }
}
