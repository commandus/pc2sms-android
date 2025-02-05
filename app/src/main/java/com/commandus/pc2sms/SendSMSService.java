package com.commandus.pc2sms;

import android.Manifest;
import android.app.AlarmManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannelBuilder;
import io.grpc.pc2sms.*;

import io.grpc.ManagedChannel;

public class SendSMSService extends Service {
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    private static final String TAG = "send-sms-service";
    private static final String NOTIFICATION_CHANNEL_ID = "pc2sms";
    private static final String NOTIFICATION_CHANNEL_NAME = "SMS sent from the PC";
    private static final int NOTIFICATION_ID = 1;
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
        mkNotificationChannel();
        Log.i(TAG, "Сервис отправки СМС создан.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String a = intent.getAction();
        if (Objects.equals(a, ACTION_START)) {
            Log.i(TAG, "Сервис отправки СМС стартовал.");
            startListenSMS();
        }
        if (Objects.equals(a, ACTION_STOP)) {
            Log.i(TAG, "Сервис отправки СМС остановлен.");
            stopListenSMS();
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
        // restartService();
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
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, mkNotification(message));
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
        if (mThread != null) {
            mStopRequest = true;
            mThread.interrupt();
            mThread = null;
        }
    }

    private void mkNotificationChannel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(nc);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private boolean startFg() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No send SMS permission is granted, finish service");
            stopSelf();
            return false;
        }
        try {
            Notification notification = mkNotification("Отправка SMS включена");
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
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

    private Notification mkNotification(String msg) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Resources res = getResources();
        return new NotificationCompat.Builder(this, TAG)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
//              .setTicker(res.getString(R.string.unused_app_restrictions_granted))
                .setContentTitle(res.getString(R.string.notification_title))
                .setContentText(msg)
                .setSilent(true)
                .setOngoing(true)
                .build();
    }

    public void startListenSMS() {
        if (mThread == null) {
            mThread = new Thread(() -> {
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
            ManagedChannel mChannel = null;
            Settings mSettings = Settings.getSettings(this);
            smsGrpc.smsBlockingStub mStub;
            try {
                int sleepTime = 2 * failureCount * 1000;
                if (sleepTime > 2 * 60 * 1000) {
                    sleepTime = 1000; // 2'
                    failureCount = 0;
                }
                Thread.sleep(sleepTime);
                mChannel = ManagedChannelBuilder.forAddress(mSettings.getAddress(), mSettings.getPort())
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(60, TimeUnit.SECONDS)
                    .build();
                Credentials c = Credentials.newBuilder()
                    .setLogin(mSettings.getUser())
                    .setPassword(mSettings.getPassword())
                    .build();


                mStub = smsGrpc.newBlockingStub(mChannel);
/*
                ResponseCount r = mStub.countSMSToSend(c);
                Log.i(TAG, "countSMSToSend = " + Integer.toString(r.getCount()));
                SMS sms = mStub.lastSMSToSend(c);
                Log.i(TAG, "lastSMSToSend = " + sms.getPhone() + " " + sms.getMessage());
*/
                Log.i(TAG, "Ждём SMS..");
                log("Вызов " + Long.toString(callCounter));
                callCounter++;

                Iterator<SMS> iter = mStub.listenSMSToSend(c);

                while (iter.hasNext()) {
                    failureCount = 0;
                    if (mSettings.getSimulateOn()) {
                        Log.i(TAG, "Как-бы отправлено СМС");
                        simulateSendSMS(iter.next());
                    } else {
                        sendSMS(iter.next());
                        Log.i(TAG, "Отправлено СМС ");
                    }
                }

            } catch (Throwable e) {
                if (mStopRequest) {
                    Log.i(TAG, "Пользовтаель запросил остановку");
                } else {
                    failureCount++;
                    Log.e(TAG, "Ошибка работы с сервисом " + e.getMessage());
                }
            }
            if (mChannel != null) {
                mChannel.shutdown();
                mChannel = null;
            }
        }
        Log.i(TAG, "Завершена отправка SMS");
        mStopRequest = false;
        indicateListenStatus(false);
        mThread = null;
    }

    private void simulateSendSMS(SMS value) {
        log(value.getPhone() + "~ '" + value.getMessage());
    }

    public void sendSMSMessage(SMS value) {
        SmsManager smsManager = SmsManager.getDefault();
        PendingIntent sentPI;
        String SENT = "SMS_SENT";
        sentPI = PendingIntent.getBroadcast(this, 0,new Intent(SENT), PendingIntent.FLAG_IMMUTABLE);
        smsManager.sendTextMessage(value.getPhone(), null, value.getMessage(), sentPI, null);
    }

    public void sendSMS(SMS value) {
        try {
            sendSMSMessage(value);
            log(value.getPhone() + ": '" + value.getMessage());
        } catch (final Exception e) {
            log("Не отправлено " + value.getPhone() + " " + value.getMessage() + " " + e.toString());
        }
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
