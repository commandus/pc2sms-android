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
import io.grpc.stub.StreamObserver;

public class SendSMSService extends Service {
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    private static final String TAG = "send-sms-service";
    private static final int SVC_ID = 4250053;
    private static final String NOTIFICATION_CHANNEL_ID = "PC2SMS";

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
        log("Сервис отправки СМС создан.");
        Settings mSettings = Settings.getSettings(this);
        if (mSettings.getServiceOn())
            startListenSMS();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String a = intent.getAction();
        if (Objects.equals(a, ACTION_START)) {
            log("Сервис отправки СМС стартовал.");
            startListenSMS();
        }
        if (Objects.equals(a, ACTION_STOP)) {
            log("Сервис отправки СМС остановился.");
            stopListenSMS();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListenSMS();
        log("Сервис отправки СМС завершен");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("Сервис отправки СМС соединен с активностью");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        log("Сервис отправки СМС отоединен от активности");
        listener = null;
        return super.onUnbind(intent);
    }

    public void attach(ServiceListener listener) {
        log("Сервис отправки СМС подключил слушателя.");
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        synchronized (this) {
            this.listener = listener;
        }
    }

    public void detach() {
        log("Сервис отправки СМС отключил слушателя.");
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
        }
    }
    private boolean startFg() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No send SMS permission is granted, finish service");
            stopSelf();
            return false;
        }
        try {
            Resources res = getResources();
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent contentIntent = PendingIntent.getActivity(this, 111, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "pc2sms", NotificationManager.IMPORTANCE_HIGH);
                final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(nc);
            }
            Notification notification = new NotificationCompat.Builder(this, TAG)
                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                    .setContentIntent(contentIntent)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setTicker(res.getString(R.string.unused_app_restrictions_granted))
//                    .setAutoCancel(true)
                    .setContentTitle(res.getString(R.string.app_name))
                    .setContentText(res.getString(R.string.app_name))
                    .build();

            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
            }
            ServiceCompat.startForeground(this, SVC_ID, notification, type);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e instanceof ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
                // (e.g started from bg)
                Log.e(TAG, e.toString());
            }
            return false;
        }
        return true;
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
        log("Start listening..");
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
                    .build();
                Credentials c = Credentials.newBuilder()
                    .setLogin(mSettings.getUser())
                    .setPassword(mSettings.getPassword())
                    .build();

                mStub = smsGrpc.newBlockingStub(mChannel);
                Iterator<SMS> iter = mStub.listenSMSToSend(c);
                log("wait SMS..");
                indicateListenStatus(true);
                while (iter.hasNext()) {
                    failureCount = 0;
                    if (mSettings.getSimulateOn()) {
                        log("Как-бы отправлено СМС ");
                        simulateSendSMS(iter.next());
                    } else {
                        sendSMS(iter.next());
                        log("Отправлено СМС ");
                    }
                }
            } catch (Throwable e) {
                if (mStopRequest) {
                    log("listening interrupted");
                } else {
                    failureCount++;
                    log("listen error " + e.getMessage());
                }
            }
            if (mChannel != null) {
                mChannel.shutdown();
                mChannel = null;
            }
        }
        log("stop listening");
        mStopRequest = false;
        indicateListenStatus(false);
        mThread = null;
    }

    private void simulateSendSMS(SMS value) {
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(() -> {
                    if (listener != null) {
                        listener.onInfo("Как бы отправлено: '" + value.getMessage()
                                + "' на " + value.getPhone());
                    }
                });
            }
        }
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
        synchronized (this) {
            sendSMSMessage(value);
            if (listener != null) {
                mainLooper.post(() -> {
                    if (listener != null) {
                        listener.onInfo("Отправлено: '"
                                + value.getMessage()
                                + "' на " + value.getPhone());
                    }
                });
                }
            }
        } catch (final Exception e) {
            log("Ошибка отправки СМС  "  + e.getMessage());
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onError(e);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        log("Приложение для отправки СМС было удалено с экрана");
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
