package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * The same Android Doze exemption state and request intent used by Home and the
 * Source Wizard. This does not request anything until the caller starts the intent.
 * OEM autostart and recent-app locking are separate settings.
 */
public final class BatteryOptimizationHelper {

    private BatteryOptimizationHelper() { }

    // null means that the system did not provide a reliable answer.
    public static Boolean isExempt(final Context context) {
        if (context == null) return null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        final PowerManager manager = (PowerManager)
                context.getSystemService(Context.POWER_SERVICE);
        return manager == null ? null
                : manager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static Intent exemptionRequest(final Context context) {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.getPackageName()));
    }
}
