package com.commandus.pc2sms;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class SendSMSScheduler {
    private final Context mContext;
    private Operation mOperation;

    public SendSMSScheduler(Context context) {
        mContext = context;
    }

    public void start() {
        PeriodicWorkRequest sendSMSRequest =
            new PeriodicWorkRequest.Builder(SendSMSWorker.class, 15, TimeUnit.MINUTES)
            .build();
         mOperation = WorkManager.getInstance(mContext)
                .enqueueUniquePeriodicWork(Settings.workName, ExistingPeriodicWorkPolicy.UPDATE, sendSMSRequest);
    }

    public void stop() {
    }
}
