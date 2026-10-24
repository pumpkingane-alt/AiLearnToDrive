package com.ailearn.drive;

import android.app.Application;

/** Installs the crash catcher before any activity exists. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Application app = this;
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                CrashLog.write(app, "thread " + t.getName(), e);
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }
}
