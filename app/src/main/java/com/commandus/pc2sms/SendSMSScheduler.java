package com.commandus.pc2sms;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SendSMSScheduler {
    private static final String TAG = SendSMSScheduler.class.getSimpleName();

    static public void start(Context context) {
        PeriodicWorkRequest sendSMSRequest =
            new PeriodicWorkRequest.Builder(ListenSMSWorker.class, 15, TimeUnit.MINUTES)
            .build();
        PeriodicWorkRequest send1SMSRequest =
                new PeriodicWorkRequest.Builder(Send1SMSWorker.class, 15, TimeUnit.MINUTES)
                        .addTag(Send1SMSWorker.TAG)
                        .build();

        // mOperation = WorkManager.getInstance(mContext).enqueueUniquePeriodicWork(Settings.workName, ExistingPeriodicWorkPolicy.KEEP, sendSMSRequest)ж
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(Settings.workName, ExistingPeriodicWorkPolicy.KEEP, send1SMSRequest);
    }

    static public void stop(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(Send1SMSWorker.TAG);
    }

    public boolean running(Context context) {
        try {
            List<WorkInfo> l = WorkManager.getInstance(context).getWorkInfosByTag(Send1SMSWorker.TAG).get();
            if (l.isEmpty())
                return false;
            return l.get(0).getState() != WorkInfo.State.CANCELLED;
        } catch (ExecutionException e) {
            Log.e(TAG, e.toString());
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        return false;
    }
}
