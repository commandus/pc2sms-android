package com.commandus.pc2sms;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Locale;

import io.grpc.pc2sms.SMS;

public class Send1SMSWorker extends Worker {
    private Pc2Sms pc2Sms;
    public static final String TAG = Send1SMSWorker.class.getSimpleName();
    private TextToSpeech tts;

    public Send1SMSWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        pc2Sms = new Pc2Sms(getApplicationContext());
        pc2Sms.mkNotificationChannel();
        Log.i(TAG, "Отработчик отправки СМС создан.");

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.getDefault());
                }
            }
        });
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            setForegroundAsync(new ForegroundInfo(Settings.NOTIFICATION_ID, pc2Sms.mkNotification("Ожидание SMS к отправке")));
            pc2Sms.open();

            while(true) {
                Log.i(TAG, "Проверяем есть SMS к отправке");
                SMS sms = pc2Sms.sms();
                if (sms.getPhone().isEmpty())
                    break;
                tts.speak(sms.getMessage(), TextToSpeech.QUEUE_FLUSH, null);
            }

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
