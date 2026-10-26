package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.services.UiBasedCollector;

/**
 * Read-only readiness coordinator. It never selects a collector, creates a
 * sensor, starts/stops services, or duplicates collector permission checks.
 * UI actions request system settings only after an explicit user tap.
 */
public final class SourceWizardReadiness {

    private static final String TAG = "SourceWizardReadiness";

    public enum Requirement {
        BATTERY_OPTIMIZATION,
        AUTOSTART,
        APP_LOCK,
        NOTIFICATION_LISTENER
    }

    public enum State {
        READY,
        MISSING,
        UNKNOWN
    }

    public static final class Result {
        public final State state;
        public final boolean canFix;

        private Result(final State state, final boolean canFix) {
            this.state = state;
            this.canFix = canFix;
        }
    }

    private SourceWizardReadiness() { }

    public static Result inspect(final Context context, final Requirement requirement) {
        if (context == null || requirement == null) return new Result(State.UNKNOWN, false);
        try {
            switch (requirement) {
                case BATTERY_OPTIMIZATION:
                    final Boolean exempt = BatteryOptimizationHelper.isExempt(context);
                    return fromNullable(exempt, exempt != null && !exempt
                            && canOpen(context, BatteryOptimizationHelper.exemptionRequest(context)));
                case AUTOSTART:
                    final Boolean autostart = OemAutostartHelper.isEnabled(context);
                    return fromNullable(autostart, autostart != null && !autostart
                            && OemAutostartHelper.settingsIntent(context) != null);
                case APP_LOCK:
                    // No reliable public query for OEM "lock in recents".
                    return new Result(State.UNKNOWN, false);
                case NOTIFICATION_LISTENER:
                    final boolean enabled = UiBasedCollector.isNotificationServiceEnabled();
                    return new Result(enabled ? State.READY : State.MISSING, !enabled);
                default:
                    return new Result(State.UNKNOWN, false);
            }
        } catch (RuntimeException e) {
            UserError.Log.e(TAG, "Readiness inspection failed for " + requirement + ": " + e);
            return new Result(State.UNKNOWN, false);
        }
    }

    private static Result fromNullable(final Boolean enabled, final boolean canFix) {
        return new Result(enabled == null ? State.UNKNOWN
                : (enabled ? State.READY : State.MISSING), canFix);
    }

    private static boolean canOpen(final Context context, final Intent intent) {
        try {
            return intent != null && intent.resolveActivity(context.getPackageManager()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean canOpenAutostartSettings(final Activity activity) {
        return OemAutostartHelper.settingsIntent(activity) != null;
    }

    public static void openAutostartSettings(final Activity activity) {
        if (activity == null) return;
        final Intent intent = OemAutostartHelper.settingsIntent(activity);
        if (intent == null) return;
        try {
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            UserError.Log.e(TAG, "Cannot open OEM autostart settings: " + e);
        }
    }

    public static void requestFix(final Activity activity, final Requirement requirement) {
        if (activity == null || requirement == null) return;
        if (inspect(activity, requirement).state != State.MISSING) return;
        try {
            switch (requirement) {
                case BATTERY_OPTIMIZATION:
                    final Intent batteryIntent = BatteryOptimizationHelper.exemptionRequest(activity);
                    if (canOpen(activity, batteryIntent)) activity.startActivity(batteryIntent);
                    break;
                case AUTOSTART:
                    openAutostartSettings(activity);
                    break;
                case NOTIFICATION_LISTENER:
                    // Reuse the existing UiBased rationale and Android Settings navigation.
                    UiBasedCollector.enableNotificationService(activity);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            UserError.Log.e(TAG, "Readiness action failed for " + requirement + ": " + e);
        }
    }
}
