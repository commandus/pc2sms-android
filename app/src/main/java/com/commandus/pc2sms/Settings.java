package com.commandus.pc2sms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.content.UnusedAppRestrictionsConstants;
import androidx.preference.PreferenceManager;
import androidx.core.content.PackageManagerCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;


public class Settings {
    // static final String APPLICATION_ID = "com.commandus.pc2sms";
    private static final String TAG = Settings.class.getSimpleName();
    static final String DEF_SERVICE_ADDRESS = "127.0.0.1";
    static final int DEF_SERVICE_PORT = 50053;
    static final String DEF_LOGIN = "";
    static final String DEF_PASSWORD = "";

    private static final String PREF_SERVICE_ADDRESS = "address";
    private static final String PREF_SERVICE_PORT = "port";

    private static final String PREF_LOGIN = "login";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_AUTO_START = "auto_start";

    private static Settings mSettings = null;
    private final Context mContext;

    private String mAddress;
    private int mPort;
    private String mLogin;
    private String mPassword;
    private boolean mAutoStart;

    public String getAddress() {
        return mAddress;
    }
    public int getPort() {
        return mPort;
    }
    public String getUser() {
        return mLogin;
    }
    public String getPassword() {
        return mPassword;
    }

    public void setAddress(String value) {
        mAddress = value;
    }
    public void setPort(int value) {
        mPort = value;
    }
    public void setUser(String value) {
        mLogin = value;
    }
    public void setPassword(String value) {
        mPassword = value;
    }

    public void load() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mAddress = prefs.getString(PREF_SERVICE_ADDRESS, DEF_SERVICE_ADDRESS);
        mPort = prefs.getInt(PREF_SERVICE_PORT, DEF_SERVICE_PORT);
        mLogin = prefs.getString(PREF_LOGIN, DEF_LOGIN);
        mPassword = prefs.getString(PREF_PASSWORD, DEF_PASSWORD);
        mAutoStart = prefs.getBoolean(PREF_AUTO_START, true);
    }

    public void save() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SERVICE_ADDRESS, mAddress);
        editor.putInt(PREF_SERVICE_PORT, mPort);
        editor.putString(PREF_LOGIN, mLogin);
        editor.putString(PREF_PASSWORD, mPassword);
        editor.putBoolean(PREF_AUTO_START, mAutoStart);
        editor.apply();
    }

    public Settings(Context context) {
        mContext = context;
        load();
    }

    public synchronized static Settings getSettings(Context context) {
        if (mSettings == null) {
            mSettings = new Settings(context);
        }
        return mSettings;
    }

    static public void requestDisableSleep(Activity activity, int requestCode) {
        ListenableFuture<Integer> future = PackageManagerCompat.getUnusedAppRestrictionsStatus(activity);
        future.addListener(()->{
            int appRestrictionsStatus = 0;
            try {
                appRestrictionsStatus = future.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "requestDisableSleep failed");
            }
            switch (appRestrictionsStatus) {
                case UnusedAppRestrictionsConstants.ERROR:
                    Log.e(TAG, "requestDisableSleep error");
                    break;
                case UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE:
                    Log.e(TAG, "requestDisableSleep feature not available");
                    break;
                case UnusedAppRestrictionsConstants.DISABLED:
                    Log.e(TAG, "requestDisableSleep disabled");
                    break;
                case UnusedAppRestrictionsConstants.API_30_BACKPORT:
                case UnusedAppRestrictionsConstants.API_30:
                case UnusedAppRestrictionsConstants.API_31:
                    // If the user doesn't start your app for a few months, the system will place restrictions on it
                    Log.e(TAG, "requestDisableSleep successfully");
                    handleRestrictions(activity, requestCode, appRestrictionsStatus);
                    break;

            }
        }, ContextCompat.getMainExecutor(activity));
    }

    static private void handleRestrictions(Activity activity, int requestCode, int appRestrictionsStatus) {
        Intent intent = IntentCompat.createManageUnusedAppRestrictionsIntent(activity, activity.getPackageName());
        activity.startActivityForResult(intent, requestCode);
    }

    public void setNoSleep(boolean enabled) {
        if (enabled)
            Log.i(TAG, "User grant to disable app restrictions");
        else
            Log.i(TAG, "User do not grant to disable app restrictions");
    }
}

