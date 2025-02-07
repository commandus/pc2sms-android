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
    private static final String TAG = SendSMSService.class.getSimpleName();
    private static final String WAKE_LOCK_NAME = "pc2sms:fs";
    private static PowerManager.WakeLock mWakeLock;
    public boolean isListening = false;

    private Thread mThread;
    private ServiceListener listener;
    private static boolean mStopRequest = false;
    class SendSMSBinder extends Binder {
        SendSMSService getService() {
            return SendSMSService.this;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;

    private long callCounter = 0;

    private Pc2Sms pc2Sms;

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
        wakeUpRelease();
        if (mThread != null) {
            mStopRequest = true;
            mThread.interrupt();
            mThread = null;
        }
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
        getLock(getApplicationContext()).acquire(24*60*60*1000L);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error acquire power lock: " + e.toString());
        }
    }

    private void wakeUpRelease() {
        try {
            getLock(getApplicationContext()).release();
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
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
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

    public void startListenSMS() {
        if (mThread == null) {
            mThread = new Thread(() -> {
                wakeUpAcquire();
                if (startFg()) {
                    mStopRequest = false;
                    listenSMS();
                }
            });
            mThread.start();
        }
    }


    public void listenSMS() {
        int failureCount = 0;
        Log.i(TAG, "Начинаем получать SMS..");
        indicateListenStatus(true);
        while (!mStopRequest) {
            try {
                int sleepTime = 2 * failureCount * 1000;
                if (sleepTime > 2 * 60 * 1000) {
                    sleepTime = 1000; // 2'
                    failureCount = 0;
                }
                Thread.sleep(sleepTime);
                pc2Sms.open();
/*
                ResponseCount r = mStub.countSMSToSend(c);
                Log.i(TAG, "countSMSToSend = " + Integer.toString(r.getCount()));
                SMS sms = mStub.lastSMSToSend(c);
                Log.i(TAG, "lastSMSToSend = " + sms.getPhone() + " " + sms.getMessage());
*/
                Log.i(TAG, "Ждём SMS..");
                log("Вызов " + Long.toString(callCounter));
                callCounter++;
                pc2Sms.listen();
            } catch (Throwable e) {
                if (mStopRequest) {
                    Log.i(TAG, "Пользовтаель запросил остановку");
                } else {
                    failureCount++;
                    Log.e(TAG, "Ошибка работы с сервисом " + e.getMessage());
                }
            }
            pc2Sms.close();
        }
        Log.i(TAG, "Завершена отправка SMS");
        mStopRequest = false;
        indicateListenStatus(false);
        mThread = null;
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
