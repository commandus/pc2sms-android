package com.commandus.pc2sms;

public interface ServiceListener {
    void onSent(final String value);
    void onInfo(final String e);
    void onError(final Exception e);
}
