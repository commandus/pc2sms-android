package com.commandus.pc2sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receive android.intent.action.BOOT_COMPLETED broadcast and start up
 */
public class ReceiverBoot extends BroadcastReceiver {
    private static final String TAG = ReceiverBoot.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        Log.d(TAG, "device rebooted, auto-start");
        context.startService(new Intent(context, SendSMSService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
        Log.d(TAG, "Service started " + a);
    }
}
