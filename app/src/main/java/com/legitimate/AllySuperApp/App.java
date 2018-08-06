package com.legitimate.AllySuperApp;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import co.tinode.tinodesdk.Tinode;

/**
 * A class for providing global context for database access
 */
public class App extends Application {

    private static App sContext;

    private static Tinode sTinodeCache;
    public App() {
        Log.d("TindroidApp", "INSTANTIATED");
        sContext = this;
    }
    public static Context getAppContext() {
        return sContext;
    }
    public static void retainTinodeCache(Tinode tinode) {
        sTinodeCache = tinode;
    }
}
