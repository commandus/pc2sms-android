package com.commandus.pc2sms;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.grpc.pc2sms.SMS;

public class MainActivity extends AppCompatActivity
    implements ServiceConnection, ServiceListener {

    private static final String TAG = "pc2sms-main-activity";
    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;

    TextView textViewMessage;
    EditText editTextServiceAddress;
    EditText editTextServicePort;
    EditText editTextUserName;
    EditText editTextPassword;
    Switch switchAllowSendSMS;

    private SendSMSService service;
    private Settings mSettings;

    private TextWatcher mTextWatcher = new TextWatcher() {
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

        switchAllowSendSMS.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                toggleService(b);
            }
        });

        startService(new Intent(this, SendSMSService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    private void toggleService(boolean on) {
        if (on) {
            checkPermission();
        } else {
            Intent intent = new Intent(MainActivity.this, SendSMSService.class);
            intent.setAction(SendSMSService.ACTION_STOP);
            startService(intent);
        }
    }


    private void load() {
        editTextServiceAddress.setText(mSettings.getAddress());
        editTextServicePort.setText(Integer.toString(mSettings.getPort()));
        editTextUserName.setText(mSettings.getUser());
        editTextPassword.setText(mSettings.getPassword());
    }

    private void save() {
        mSettings.setAddress(editTextServiceAddress.getText().toString());
        mSettings.setPort(Integer.valueOf(editTextServicePort.getText().toString()));
        mSettings.setUser(editTextUserName.getText().toString());
        mSettings.setPassword(editTextPassword.getText().toString());
        mSettings.save();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d(TAG, "service connect..");
        service = ((SendSMSService.SerialBinder) binder).getService();
        service.attach(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewMessage.setText("service connected.");
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "disconnected");
        service = null;
    }

    @Override
    public void onSent(String value) {
        textViewMessage.setText(value);
    }

    @Override
    public void onInfo(String value) {
        textViewMessage.setText(value);
    }

    @Override
    public void onError(Exception e) {
        textViewMessage.setText(e.getMessage());
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(MainActivity.this, SendSMSService.class);
                    intent.setAction(SendSMSService.ACTION_START);
                    startService(intent);
                    Toast.makeText(this, "Granted", Toast.LENGTH_LONG);
                } else {
                    Toast.makeText(this, "Not granted", Toast.LENGTH_LONG);
                    return;
                }
            }
        }
    }

    public void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.SEND_SMS)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_SEND_SMS);
            }
        }
    }


}