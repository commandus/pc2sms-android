package com.commandus.pc2sms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
    implements ServiceConnection, ServiceListener {

    private static final String TAG = "pc2sms-main-activity";
    private static final int REQUEST_PERMISSION_SEND_SMS = 1;
    public static final int REQUEST_PERMISSION_SLEEP_DISABLE = 2;

    TextView textViewMessage;
    EditText editTextServiceAddress;
    EditText editTextServicePort;
    EditText editTextUserName;
    EditText editTextPassword;
    SwitchCompat switchAllowSendSMS;

    private SendSMSService service;
    private Settings mSettings;

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            save();
        }
    };

    private final SwitchCompat.OnCheckedChangeListener mServiceOnListener = (compoundButton, b) -> toggleService(b);

    private boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewMessage = findViewById(R.id.textViewMessage);
        editTextServiceAddress = findViewById(R.id.editTextServiceAddress);
        editTextServicePort = findViewById(R.id.editTextServicePort);
        editTextUserName = findViewById(R.id.editTextUserName);
        editTextPassword = findViewById(R.id.editTextPassword);
        switchAllowSendSMS = findViewById(R.id.switchAllowSendSMS);

        mSettings = Settings.getSettings(this);

        load();

        editTextServiceAddress.addTextChangedListener(mTextWatcher);
        editTextServicePort.addTextChangedListener(mTextWatcher);
        editTextUserName.addTextChangedListener(mTextWatcher);
        editTextPassword.addTextChangedListener(mTextWatcher);

        if (mSettings.getServiceOn())
            turnOn();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (switchAllowSendSMS.isChecked())
            service.restartService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        switchAllowSendSMS.setOnCheckedChangeListener(mServiceOnListener);
        // Bind to LocalService
        bindService(new Intent(this, SendSMSService.class), this, Context.BIND_AUTO_CREATE);
        if (mSettings.getRequestDisableSleep())
            Settings.requestDisableSleep(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        switchAllowSendSMS.setOnCheckedChangeListener(null);
        // Unbind from the service
        unbindService(this);
        mBound = false;
    }
    
    private void toggleService(boolean on) {
        mSettings.save();
        if (on) {
            checkPermission();
        } else {
            turnOff();
        }
    }

    private void load() {
        editTextServiceAddress.setText(mSettings.getAddress());
        editTextServicePort.setText(Integer.toString(mSettings.getPort()));
        editTextUserName.setText(mSettings.getUser());
        editTextPassword.setText(mSettings.getPassword());
        switchAllowSendSMS.setChecked(mSettings.getServiceOn());
    }

    private void save() {
        mSettings.setAddress(editTextServiceAddress.getText().toString());
        mSettings.setPort(Integer.parseInt(editTextServicePort.getText().toString()));
        mSettings.setUser(editTextUserName.getText().toString());
        mSettings.setPassword(editTextPassword.getText().toString());
        mSettings.setServiceOn(switchAllowSendSMS.isChecked());
        mSettings.save();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d(TAG, "service connect..");
        service = ((SendSMSService.SendSMSBinder) binder).getService();
        service.attach(this);
        if (switchAllowSendSMS != null) {
            switchAllowSendSMS.setOnCheckedChangeListener(null);
            switchAllowSendSMS.setChecked(service.isListening);
            switchAllowSendSMS.setOnCheckedChangeListener(mServiceOnListener);
        }

        mBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "disconnected");
        mBound = false;
        service = null;
    }

    @Override
    public void onSent(String value) {
        addMessageLine(value);
    }

    private void addMessageLine(String value) {
        if (value == null)
            return;
        if (value.isEmpty())
            return;
        String s = textViewMessage.getText().toString();
        String[] lines = s.split("\\r?\\n");
        StringBuilder b = new StringBuilder();
        int c = lines.length;
        int f = c - 5;
        if (f < 0)
            f = 0;
        for (int i = f; i < c; i++) {
            b.append(lines[i]).append('\n');
        }
        b.append(value);
        textViewMessage.setText(b.toString());
    }

    @Override
    public void onInfo(String value) {
        addMessageLine(value);
    }

    @Override
    public void onError(Exception e) {
        addMessageLine(e.getMessage());
    }

    @Override
    public void onListen(boolean listen) {
        if (switchAllowSendSMS != null) {
            switchAllowSendSMS.setChecked(listen);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_PERMISSION_SLEEP_DISABLE:
                Toast.makeText(this, R.string.unused_app_restrictions_granted, Toast.LENGTH_LONG);
                mSettings.setNoSleep(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_SEND_SMS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    turnOn();
                }
                break;
            case REQUEST_PERMISSION_SLEEP_DISABLE:
                break;
            default:
                break;
        }
    }

    private void turnOn() {
        Context context = getApplicationContext();
        Intent intent = new Intent(MainActivity.this, SendSMSService.class);
        intent.setAction(SendSMSService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void turnOff() {
        Context context = getApplicationContext();
        Intent intent = new Intent(MainActivity.this, SendSMSService.class);
        intent.setAction(SendSMSService.ACTION_STOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            turnOn();
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        REQUEST_PERMISSION_SEND_SMS);
            }
        }
    }

}