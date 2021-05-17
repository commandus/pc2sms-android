# pc2sms

The pc2sms-android is an Android client sends SMS from Linux and Windows programs.

You need to install the [web service](https://github.com/commandus/pc2sms)

Android application is waiting for commands to send sms from the web service.

Commands for sending sms are sent from the web service one by one.

```
+---------------------+
| send-sms / your app |
+---------------------+
          |
+---------------------+
| pc2sms web service  |
+---------------------+
        |
        |-----------------+-----------------+
        |                 |                 |
+--------------+   +--------------+       . . .
|    Android   |   |    Android   |
| +18001112222 |   | +18001113333 |
+--------------+   +--------------+
```

Phones are selected in turn one by one.
