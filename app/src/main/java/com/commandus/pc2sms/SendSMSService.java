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

    private Thread mThread;

    private ServiceListener listener;
    public boolean isListening = false;


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
        log("send sms service created");
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SendSMSBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("send sms service created");
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
        stopListenSMS();
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

    private void stopListenSMS() {
        if (mThread != null) {
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
        try {
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
            isListening = true;
            while(iter.hasNext()){
                sendSMS(iter.next());
            }
        } catch (Throwable e) {
            isListening = false;
            log("listen error "  + e.getMessage());
        }
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
