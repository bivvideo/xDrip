package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.Activity;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.services.UiBasedCollector;

/**
 * Readiness coordinator shared by the general readiness dialog and future
 * collector-specific routes. This class does not select a collector, create a
 * sensor, or duplicate Android permission checks.
 *
 * Only Notification Listener is live in the first pilot. Other rows stay
 * explicitly marked as visual examples until their providers are audited.
 */
public final class SourceWizardReadiness {

    private static final String TAG = "SourceWizardReadiness";

    public enum Requirement {
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

    private SourceWizardReadiness() {
        // Static coordinator.
    }

    public static Result inspect(final Requirement requirement) {
        if (requirement == null) return new Result(State.UNKNOWN, false);
        try {
            switch (requirement) {
                case NOTIFICATION_LISTENER:
                    // The existing UiBased collector is the source of truth.
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

    public static void requestFix(final Activity activity, final Requirement requirement) {
        if (activity == null || requirement == null) return;
        if (inspect(requirement).state != State.MISSING) return;
        switch (requirement) {
            case NOTIFICATION_LISTENER:
                // Reuse the existing rationale + Android Settings navigation.
                // Unlike switchToAndEnable(), this has no source/sensor side effects.
                UiBasedCollector.enableNotificationService(activity);
                break;
            default:
                break;
        }
    }
}
