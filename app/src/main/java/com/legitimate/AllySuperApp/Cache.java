package com.legitimate.AllySuperApp;

import android.os.Build;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Locale;

import com.legitimate.AllySuperApp.db.BaseDb;
import com.legitimate.AllySuperApp.media.VxCard;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.PrivateType;

/**
 * Shared resources.
 */
public class Cache {
    private static final String TAG = "Cache";

    //public static String sHost = "api.tinode.co"; // remote host
    public static final String HOST_NAME = "allysuperapp.co.ke:6060"; // local host
    public static final boolean PREFS_USE_TLS = false;
    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    private static int sVisibleCount = 0;

    public static Tinode getTinode() {
        if (sTinode == null) {
            Log.d(TAG, "Tinode instantiated");

            sTinode = new Tinode("Tindroid/0.15", API_KEY, BaseDb.getInstance().getStore(), null);
            sTinode.setOsString(Build.VERSION.RELEASE + " Application Version " +BuildConfig.VERSION_NAME +" Build Type "+ BuildConfig.BUILD_TYPE);

            // Default types for parsing Public, Private fields of messages
            sTinode.setDefaultTypeOfMetaPacket(VxCard.class, PrivateType.class);
            sTinode.setMeTypeOfMetaPacket(VxCard.class);
            sTinode.setFndTypeOfMetaPacket(VxCard.class);

            // Set device language
            sTinode.setLanguage(Locale.getDefault().getLanguage());
            sTinode.setAutologin(true);
        }

        sTinode.setDeviceToken(FirebaseInstanceId.getInstance().getToken());
        return sTinode;
    }

    // Invalidate and reinitialize existing cache.
    public static void invalidate() {
        if (sTinode != null) {
            sTinode.logout();
            sTinode = null;
        }
    }

    /**
     * Keep counter of visible activities
     *
     * @param visible true if some activity became visible
     * @return
     */
    public static int activityVisible(boolean visible) {
        sVisibleCount += visible ? 1 : -1;
        // Log.d(TAG, "Visible count: " + sVisibleCount);
        return sVisibleCount;
    }
}
