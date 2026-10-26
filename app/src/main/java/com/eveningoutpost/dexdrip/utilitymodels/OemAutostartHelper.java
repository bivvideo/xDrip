package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Method;

/**
 * Best-effort, read-only OEM autostart probe. Android has no public, portable
 * autostart-state API. Some MIUI versions expose Xiaomi AppOps operation 10008;
 * reflection may be blocked or its behavior may change. Never infer READY from
 * the phone manufacturer or from the presence of the Settings screen.
 * No hidden-API bypass, third-party library, or service manipulation.
 */
public final class OemAutostartHelper {

    private static final int MIUI_OP_AUTO_START = 10008;
    private static final String MIUI_AUTOSTART_SETTINGS = "miui.intent.action.OP_AUTO_START";

    private OemAutostartHelper() { }

    private static boolean isXiaomi() {
        return "Xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    // null = cannot verify; true/false only for explicit MIUI AppOps replies.
    public static Boolean isEnabled(final Context context) {
        if (context == null || !isXiaomi()) return null;
        try {
            final AppOpsManager appOps = (AppOpsManager)
                    context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return null;
            // The integer overload is OEM-private on some releases, so it may
            // throw or be blocked by Android's non-SDK interface restrictions.
            final Method method = AppOpsManager.class.getMethod("checkOpNoThrow",
                    int.class, int.class, String.class);
            final Object response = method.invoke(appOps, MIUI_OP_AUTO_START,
                    Process.myUid(), context.getPackageName());
            if (!(response instanceof Integer)) return null;
            final int mode = (Integer) response;
            if (mode == AppOpsManager.MODE_ALLOWED) return true;
            if (mode == AppOpsManager.MODE_IGNORED
                    || mode == AppOpsManager.MODE_ERRORED) return false;
            // MODE_DEFAULT and undocumented vendor values are inconclusive.
        } catch (Exception ignored) {
            // A blocked/nonexistent OEM interface is an unknown state, not a denial.
        }
        return null;
    }

    // Xiaomi's documented Settings entry point. May be absent on some ROMs.
    public static Intent settingsIntent(final Context context) {
        if (context == null || !isXiaomi()) return null;
        final Intent intent = new Intent(MIUI_AUTOSTART_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT);
        try {
            return intent.resolveActivity(context.getPackageManager()) != null
                    ? intent : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
