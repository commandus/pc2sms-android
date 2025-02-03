package com.commandus.pc2sms;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

public class NotifySetPermissionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notify_set_permission);
        Button bSetPermission = findViewById(R.id.buttonSetPermission);
        bSetPermission.setOnClickListener(view -> {
            Intent intent = IntentCompat.createManageUnusedAppRestrictionsIntent(NotifySetPermissionActivity.this, NotifySetPermissionActivity.this.getPackageName());
            NotifySetPermissionActivity.this.startActivityForResult(intent, MainActivity.REQUEST_PERMISSION_SLEEP_DISABLE);
            NotifySetPermissionActivity.this.finish();
        });
    }
}