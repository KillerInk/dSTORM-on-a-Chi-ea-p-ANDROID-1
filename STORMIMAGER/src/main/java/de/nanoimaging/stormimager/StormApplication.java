package de.nanoimaging.stormimager;

import android.app.Application;
import android.content.Context;

public class StormApplication extends Application {
    private static Context context;

    public static Context getContext(){return context;}

    @Override
    public void onCreate() {
        super.onCreate();
        //EventBus.builder().throwSubscriberException(BuildConfig.DEBUG).installDefaultEventBus();
        context = getApplicationContext();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        context = null;
    }
}
