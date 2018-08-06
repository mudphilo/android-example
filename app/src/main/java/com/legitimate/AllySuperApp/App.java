package com.legitimate.AllySuperApp;

import android.app.Application;
import android.content.Context;

/**
 * A class for providing global context for database access
 */
public class App extends Application {

    private static App sContext;

    public App() {
        sContext = this;
    }

    public static Context getAppContext() {
        return sContext;
    }
}
