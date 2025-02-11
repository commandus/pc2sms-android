package com.commandus.pc2sms;

import android.Manifest;
import android.app.AlarmManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

public class SendSMSService extends Service {
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_INIT = "init";
    private static final String TAG = SendSMSService.class.getSimpleName();
    private static final String WAKE_LOCK_NAME = "pc2sms:fs";
    private static PowerManager.WakeLock mWakeLock;
    public boolean isListening = false;
    private static boolean mStopRequest = false;
    private static boolean mStopped = false;
    private ServiceListener listener;
    MediaPlayer mp = null;

    class SendSMSBinder extends Binder {
        SendSMSService getService() {
            return SendSMSService.this;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;

    private long callCounter = 0;

    private Pc2Sms pc2Sms;
    private Thread mThread;
    /**
     * Lifecylce
     */
    public SendSMSService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SendSMSBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pc2Sms = new Pc2Sms(getApplicationContext());
        pc2Sms.mkNotificationChannel();
        Log.i(TAG, "Сервис отправки СМС создан.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String a = intent.getAction();
            if (Objects.equals(a, ACTION_INIT)) {
                Log.i(TAG, "Сервис создан.");
            }
            if (Objects.equals(a, ACTION_START)) {
                Log.i(TAG, "Сервис отправки СМС стартовал.");
                startListenSMS();
            }
            if (Objects.equals(a, ACTION_STOP)) {
                Log.i(TAG, "Сервис отправки СМС остановлен.");
                stopListenSMS();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListenSMS();
        Log.i(TAG, "Сервис отправки СМС завершен");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Сервис отправки СМС соединен с активностью");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Сервис отправки СМС отоединен от активности");
        listener = null;
        return super.onUnbind(intent);
    }

    public void attach(ServiceListener listener) {
        Log.i(TAG, "Сервис отправки СМС подключил активность.");
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        synchronized (this) {
            this.listener = listener;
        }
    }

    public void detach() {
        Log.i(TAG, "Сервис отправки СМС отключил активность.");
        listener = null;
    }

    private void log(
        final String message
    ) {
        Log.d(TAG, message);
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(() -> {
                    if (listener != null) {
                        listener.onInfo(message);
                    }
                });
            }
        }
    }

    private void indicateListenStatus(
        final boolean listen
    ) {
        isListening = listen;
        log(listen ? "Начал работу" : "Прекратил работу");
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(() -> {
                    if (listener != null) {
                        listener.onListen(listen);
                    }
                });
            }
        }
    }

    private void stopListenSMS() {
        mStopRequest = true;
        stopFg();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) { // Android 12+
            if (mp != null)
                mp.stop();
        }
        int c = 0;
        while (!mStopped && c < 20) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, "Wait until stopped");
            }
            c++;
        }

        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        wakeUpRelease();
        stopSelf();
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (mWakeLock == null) {
            PowerManager mgr = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
            mWakeLock.setReferenceCounted(false);
        }
        return mWakeLock;
    }
    private void wakeUpAcquire() {
        try {
        getLock(this).acquire(24*60*60*1000L);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error acquire power lock: " + e.toString());
        }
    }

    private void wakeUpRelease() {
        try {
            getLock(this).release();
        } catch (RuntimeException e) {
            Log.e(TAG, "Error release power lock: " + e.toString());
        }
    }

    private boolean startFg() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No send SMS permission is granted, finish service");
            stopSelf();
            return false;
        }
        try {
            Notification notification = pc2Sms.mkNotification("Отправка SMS включена");
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            }
            ServiceCompat.startForeground(this, Settings.NOTIFICATION_ID, notification, type);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e instanceof ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service (e.g started from bg)
                Log.e(TAG, "App not in a valid state to start foreground service");
            }
            Log.e(TAG, "Start foreground service error");
            Log.e(TAG, e.toString());
            return false;
        }
        return true;
    }

    private boolean stopFg() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Exception e) {
            Log.e(TAG, "Stop foreground service error " + e.toString());
            return false;
        }
        return true;
    }

    public void startListenSMS() {
        wakeUpAcquire();
        if (startFg()) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) { // Android 12+
                mp = MediaPlayer.create(this, R.raw.silence);
                if (mp != null) {
                    mp.setLooping(true);
                    mp.start();
                }
            }
            mStopRequest = false;
            mThread = new Thread(() -> {
                wakeUpAcquire();
                if (startFg()) {
                    listenSMS();
                }
            });
            mThread.start();
        }
    }

    public void listenSMS() {
        mStopped = false;
        Log.i(TAG, "Начинаем получать SMS..");
        indicateListenStatus(true);
        while (!mStopRequest) {
            try {
                pc2Sms.open();
                /*
                while (true) {
                    Log.i(TAG, "проверяем есть ли SMS");
                    SMS sms = pc2Sms.sms();
                    if (sms.getPhone().isEmpty()) {
                        Log.i(TAG, "SMS нет");
                        break;
                    } else {
                        Log.i(TAG, "SMS есть");
                    }
                }
                */
                pc2Sms.listen();
                // Thread.sleep(6 * 1000);
            } catch (Throwable e) {
                if (mStopRequest) {
                    Log.i(TAG, "Пользователь запросил остановку");
                } else {
                    Log.e(TAG, "Ошибка работы с сервисом " + e.getMessage());
                }
            }
            try {
                pc2Sms.close();
            } catch (Exception ignore) {

            }
        }
        mStopped = true;
        Log.i(TAG, "Завершена отправка SMS");
        indicateListenStatus(false);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Приложение для отправки СМС было удалено с экрана");
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        restartServiceIntent.setAction(ACTION_START);

        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1,
                restartServiceIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);

        super.onTaskRemoved(rootIntent);
    }
}
