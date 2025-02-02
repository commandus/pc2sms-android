package com.commandus.pc2sms;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
// import androidx.core.content.PackageManagerCompat;


public class Settings {
    static final String APPLICATION_ID = "com.commandus.pc2sms";

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
    }

    public void save() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_SERVICE_ADDRESS, mAddress);
        editor.putInt(PREF_SERVICE_PORT, mPort);
        editor.putString(PREF_LOGIN, mLogin);
        editor.putString(PREF_PASSWORD, mPassword);
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

    public void requestDisableSleep() {
        // PackageManagerCompat.getUnusedAppRestrictionsStatus(mContext).get();

    }

}
