package com.commandus.pc2sms;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity
    implements ServiceConnection, ServiceListener {

    private static final String TAG = "pc2sms-main-activity";

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

        editTextServiceAddress.addTextChangedListener(mTextWatcher);
        editTextServicePort.addTextChangedListener(mTextWatcher);
        editTextUserName.addTextChangedListener(mTextWatcher);
        editTextPassword.addTextChangedListener(mTextWatcher);

        mSettings = Settings.getSettings(this);
        startService(new Intent(this, SendSMSService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
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

}