package com.commandus.pc2sms;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Iterator;

import io.grpc.ManagedChannelBuilder;
import io.grpc.pc2sms.*;

import io.grpc.ManagedChannel;

public class SendSMSService extends Service {
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    private static final String TAG = "send-sms-service";;

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
        log("Сервис отправки СМС создан");
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SendSMSBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("Сервис отправки СМС...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent.getAction();
        if (a == ACTION_START) {
            startListenSMS();
        }
        if (a == ACTION_STOP) {
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
        log("Сервис отправки СМС соединен");
        return binder;
    }

    public void attach(ServiceListener listener) {
        log("attached");
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        stopListenSMS();
        synchronized (this) {
            this.listener = listener;
        }
    }

    public void detach() {
        listener = null;
        log("detached");
    }

    private void log(
            final String message
    ) {
        Log.d(TAG, message);
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onInfo(message);
                        }
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

    public void startListenSMS() {
        mThread = new Thread(new Runnable() {
            public void run(){
                listenSMS();
            }
        });
        mThread.start();
    }

    public void listenSMS() {
        int failureCount = 0;
        while (!mStopRequest) {
            try {
                int sleepTime = failureCount * 1000;
                if (sleepTime > 2 * 60 * 1000) {
                    sleepTime = 2 * 60 * 1000; // 2'
                }
                Thread.sleep(sleepTime);
                Settings mSettings = Settings.getSettings(this);
                ManagedChannel mChannel = ManagedChannelBuilder.forAddress(mSettings.getAddress(), mSettings.getPort())
                        .usePlaintext()
                        .build();
                smsGrpc.smsBlockingStub mStub = smsGrpc.newBlockingStub(mChannel);
                Credentials c = Credentials.newBuilder()
                        .setLogin(mSettings.getUser())
                        .setPassword(mSettings.getPassword())
                        .build();
                Iterator<SMS> iter = mStub.listenSMSToSend(c);
                log("listen..");
                isListening = true;
                failureCount = 0;
                while (iter.hasNext()) {
                    sendSMS(iter.next());
                }
            } catch (Throwable e) {
                if (!mStopRequest) {
                    failureCount++;
                    log("listen error " + e.getMessage());
                }
            }
        }
        log("stop listening");
        mStopRequest = false;
        isListening = false;
    }
    
    public void sendSMSMessage(SMS value) {
        SmsManager smsManager = SmsManager.getDefault();
        PendingIntent sentPI;
        String SENT = "SMS_SENT";
        sentPI = PendingIntent.getBroadcast(this, 0,new Intent(SENT), 0);
        smsManager.sendTextMessage(value.getPhone(), null, value.getMessage(), sentPI, null);
    }

    public void sendSMS(SMS value) {
    try {
        synchronized (this) {
            sendSMSMessage(value);
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onInfo("Отправлено: '"
                                    + value.getMessage()
                                    + "' на " + value.getPhone());
                        }
                    }
                });
                }
            }
        } catch (final Exception e) {
            log("Ошибка отправки СМС  "  + e.getMessage());
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onError(e);
                            }
                        }
                    });
                }
            }
        }
    }

}
