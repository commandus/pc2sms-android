package com.commandus.pc2sms;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SendSMSWorker extends Worker {
    private Pc2Sms pc2Sms;
    private static final String TAG = SendSMSWorker.class.getSimpleName();

    public SendSMSWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        pc2Sms = new Pc2Sms(getApplicationContext());
        pc2Sms.mkNotificationChannel();
        Log.i(TAG, "Отработчик отправки СМС создан.");
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            setForegroundAsync(new ForegroundInfo(Settings.NOTIFICATION_ID, pc2Sms.mkNotification("Ожидание SMS к отправке")));
            pc2Sms.open();
            Log.i(TAG, "Отрабатывается ожидание SMS к отправке");
            pc2Sms.listen();
        } catch (Throwable e) {
            Log.e(TAG, "Ошибка отработки сервиса " + e.getMessage());
        }
        pc2Sms.close();
        return Result.success();
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String msg) {
        // Build a notification using bytesRead and contentLength
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(Settings.NOTIFICATION_ID, pc2Sms.mkNotification(msg),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        } else {
            return new ForegroundInfo(Settings.NOTIFICATION_ID, pc2Sms.mkNotification(msg));
        }
    }
}
