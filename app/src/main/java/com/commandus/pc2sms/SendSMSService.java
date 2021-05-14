package com.commandus.pc2sms;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Iterator;

import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.pc2sms.*;

import io.grpc.ManagedChannel;

public class SendSMSService extends Service {
    private static final String TAG = "send-sms-service";;
    private ServiceListener listener;


    class SerialBinder extends Binder {
        SendSMSService getService() {
            return SendSMSService.this;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;

    private boolean connected;

    /**
     * Lifecylce
     */
    public SendSMSService() {
        log("send sms service created");
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("send sms service created");
        listen();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        log("send sms service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("send sms service bind");
        return binder;
    }

    public void attach(ServiceListener listener) {
        log("attach serial listener");
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        synchronized (this) {
            this.listener = listener;
        }
    }

    public void detach() {
        listener = null;
        log("detach service");
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

    private void cancelNotification() {
        // stopForeground(true);
    }

    public void listen() {
        try {
            ManagedChannel mChannel = OkHttpChannelBuilder.forAddress("157.230.125.14", 50053)
                    .build();
            smsGrpc.smsBlockingStub mStub = smsGrpc.newBlockingStub(mChannel);
            Settings mSettings = Settings.getSettings(this);
            Credentials c = Credentials.newBuilder()
                    .setLogin(mSettings.getUser())
                    .setPassword(mSettings.getPassword())
                    .build();
            Iterator<SMS> iter = mStub.listenSMSToSend(c);
            while(iter.hasNext()){
                sendSMS(iter.next());
            }
        } catch (Throwable e) {
            log("listen error "  + e.getMessage());
        }
    }
    public void sendSMS(SMS value) {
        try {
            synchronized (this) {
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
            log("send error "  + e.getMessage());
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
