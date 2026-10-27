package com.bililite.app;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.install(this); // 全局崩溃收集
    }
}
