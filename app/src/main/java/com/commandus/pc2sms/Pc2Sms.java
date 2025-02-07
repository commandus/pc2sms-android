package com.commandus.pc2sms;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.pc2sms.Credentials;
import io.grpc.pc2sms.SMS;
import io.grpc.pc2sms.smsGrpc;

public class Pc2Sms {
    private final Context mContext;
    private static final String TAG = Pc2Sms.class.getSimpleName();
    Settings mSettings;
    private Credentials mCredentials;
    ManagedChannel mChannel;
    smsGrpc.smsBlockingStub mStub;
    public Pc2Sms(Context context) {
        mContext = context;
    }

    public boolean open() {
        mSettings = Settings.getSettings(mContext);
        mChannel = ManagedChannelBuilder.forAddress(mSettings.getAddress(), mSettings.getPort())
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(60, TimeUnit.SECONDS)
            .build();
        mCredentials = Credentials.newBuilder()
            .setLogin(mSettings.getUser())
            .setPassword(mSettings.getPassword())
            .build();
        mStub = smsGrpc.newBlockingStub(mChannel);
        return true;
    }

    public void close() {
        if (mChannel != null) {
            mChannel.shutdown();
            mChannel = null;
        }
    }

    private void sendSMSMessage(SMS value) {
        SmsManager smsManager = SmsManager.getDefault();
        PendingIntent sentPI;
        String SENT = "SMS_SENT";
        sentPI = PendingIntent.getBroadcast(mContext, 0,new Intent(SENT), PendingIntent.FLAG_IMMUTABLE);
        smsManager.sendTextMessage(value.getPhone(), null, value.getMessage(), sentPI, null);
    }

    private void simulateSendSMS(SMS value) {
        log(value.getPhone() + "~ '" + value.getMessage());
    }

    private void sendSMS(SMS value) {
        try {
            sendSMSMessage(value);
            log(value.getPhone() + ": '" + value.getMessage());
        } catch (final Exception e) {
            log("Не отправлено " + value.getPhone() + " " + value.getMessage() + " " + e.toString());
        }
    }

    public void mkNotificationChannel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel nc = new NotificationChannel(Settings.NOTIFICATION_CHANNEL_ID, Settings.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
                final NotificationManager nm = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(nc);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public Notification mkNotification(String msg) {
        Intent notificationIntent = new Intent(mContext, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 1,
            notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Resources res = mContext.getResources();
        return new NotificationCompat.Builder(mContext, TAG)
            .setChannelId(Settings.NOTIFICATION_CHANNEL_ID)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
//              .setTicker(res.getString(R.string.unused_app_restrictions_granted))
            .setContentTitle(res.getString(R.string.notification_title))
            .setContentText(msg)
            .setSilent(true)
            .setOngoing(true)
            .build();
    }

    private void log(
        final String message
    ) {
        Log.d(TAG, message);
        final NotificationManager nm = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);
        nm.notify(Settings.NOTIFICATION_ID, mkNotification(message));
    }

    public void listen() {
        Iterator<SMS> iter = mStub.listenSMSToSend(mCredentials);
        while (iter.hasNext()) {
            if (mSettings.getSimulateOn()) {
                Log.i(TAG, "Как-бы отправлено СМС");
                simulateSendSMS(iter.next());
            } else {
                sendSMS(iter.next());
                Log.i(TAG, "Отправлено СМС ");
            }
        }
    }

    public SMS sms() {
        SMS sms = mStub.lastSMSToSend(mCredentials);
        if (!sms.getPhone().isEmpty()) {
            if (mSettings.getSimulateOn()) {
                Log.i(TAG, "Как-бы отправлено СМС");
                simulateSendSMS(sms);
            } else {
                sendSMS(sms);
                Log.i(TAG, "Отправлено СМС");
            }
        }
        return sms;
    }
}
