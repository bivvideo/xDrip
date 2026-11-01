package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.SharedPreferences;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SW-023. Remembers the explicit named Companion selection, not the origin
 * of actual readings. Only the Source Wizard and explicit source Settings
 * selections write this preference; the collector must not depend on it.
 */
public final class SourceWizardCompanionSelection {
    private static final String TAG = "SourceWizardCompanion";
    private static final String KEY = "source_wizard_named_companion_snapshot";
    private static final int VERSION = 1;

    private SourceWizardCompanionSelection() { }

    public static final class Selection {
        public final String routeId;
        public final String appId;
        public final String appDisplayName;
        public final List<String> packages;

        private Selection(final String routeId, final String appId,
                final String appDisplayName, final List<String> packages) {
            this.routeId = routeId;
            this.appId = appId;
            this.appDisplayName = appDisplayName;
            this.packages = Collections.unmodifiableList(new ArrayList<>(packages));
        }
    }

    private static boolean present(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean validPackage(final String value) {
        return present(value) && value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }

    private static List<String> normalizePackages(final String[] raw) {
        final Set<String> unique = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (validPackage(value)) unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * One SharedPreferences entry: no partial route/app/package state is visible.
     * Called only after the named route's final Done has been accepted.
     */
    public static boolean save(final String routeId, final String appId,
            final String appDisplayName, final String[] packages) {
        final List<String> normalized = normalizePackages(packages);
        if (!present(routeId) || !present(appId)
                || !present(appDisplayName) || normalized.isEmpty()) {
            UserError.Log.e(TAG, "Cannot save incomplete named Companion selection");
            clear();
            return false;
        }
        final SharedPreferences prefs = Pref.getInstance();
        if (prefs == null) {
            UserError.Log.e(TAG, "Cannot save named Companion: preferences unavailable");
            return false;
        }
        try {
            final JSONObject record = new JSONObject();
            record.put("version", VERSION);
            record.put("routeId", routeId);
            record.put("appId", appId);
            record.put("appDisplayName", appDisplayName);
            final JSONArray array = new JSONArray();
            for (String pkg : normalized) array.put(pkg);
            record.put("packages", array);
            // The snapshot is small and this explicit Done is infrequent.
            // commit() reports persistence failure; collector activation never depends on it.
            if (prefs.edit().putString(KEY, record.toString()).commit()) return true;
            UserError.Log.e(TAG, "Could not persist named Companion selection");
            prefs.edit().remove(KEY).apply(); // Never expose a failed write as active.
        } catch (Exception e) {
            UserError.Log.e(TAG, "Named Companion persistence failed: " + e);
            try {
                prefs.edit().remove(KEY).apply();
            } catch (Exception ignored) {
                // An unavailable preference store must never prevent activation.
            }
        }
        return false;
    }

    /**
     * Invalidates only the wizard metadata; does not stop or change a collector.
     */
    public static void clear() {
        try {
            final SharedPreferences prefs = Pref.getInstance();
            if (prefs != null && prefs.contains(KEY)) prefs.edit().remove(KEY).apply();
        } catch (Exception e) {
            UserError.Log.e(TAG, "Cannot clear named Companion selection: " + e);
        }
    }

    /**
     * A future optional diagnostic (SW-021) may read this; it is not a
     * verified attribution of a glucose notification or an active sensor.
     */
    public static Selection getActive() {
        if (DexCollectionType.getDexCollectionType() != DexCollectionType.UiBased) {
            return null;
        }
        try {
            final SharedPreferences prefs = Pref.getInstance();
            if (prefs == null) return null;
            final String saved = prefs.getString(KEY, null);
            if (!present(saved)) return null;
            final JSONObject record = new JSONObject(saved);
            if (record.optInt("version", -1) != VERSION) return null;
            final String routeId = record.optString("routeId", "");
            final String appId = record.optString("appId", "");
            final String display = record.optString("appDisplayName", "");
            final JSONArray raw = record.optJSONArray("packages");
            if (!present(routeId) || !present(appId) || !present(display)
                    || raw == null || raw.length() == 0) return null;
            final String[] packageIds = new String[raw.length()];
            for (int i = 0; i < raw.length(); i++) {
                final Object value = raw.opt(i);
                if (!(value instanceof String) || !validPackage((String) value)) {
                    return null;
                }
                packageIds[i] = (String) value;
            }
            final List<String> packages = normalizePackages(packageIds);
            if (packages.isEmpty()) return null;
            return new Selection(routeId, appId, display, packages);
        } catch (Exception e) {
            UserError.Log.e(TAG, "Cannot read named Companion selection: " + e);
            return null;
        }
    }
}
