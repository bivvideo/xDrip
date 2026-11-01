package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.UserError;

/** SW-025: optional help AFTER source activation. SW-026 owns the final visual treatment. */
public final class SourceWizardTip {
    private static final String TAG = "SourceWizardTip";
    private static final String HIDE = "source_wizard_hide_post_tip";
    private static final String PENDING = "source_wizard_pending_post_tip";
    private static final long MAX_AGE_MS = 120000L;
    private static boolean showing;

    private SourceWizardTip() { }

    public static void queue() {
        try {
            final SharedPreferences prefs = Pref.getInstance();
            if (prefs != null && !prefs.getBoolean(HIDE, false)) {
                prefs.edit().putLong(PENDING, System.currentTimeMillis()).apply();
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Unable to queue optional tip: " + e);
        }
    }

    /** Called after Done for same-UiBased and from Home.onResume after Activity.recreate. */
    public static void maybeShow(final Activity host) {
        if (host == null || host.isFinishing() || host.isDestroyed()) return;
        final View decor = host.getWindow().getDecorView();
        decor.postDelayed(() -> {
            if (showing || host.isFinishing() || host.isDestroyed()) return;
            // Do not interrupt another dialog or an Activity that is not foreground.
            if (!host.hasWindowFocus()) return;
            try {
                final SharedPreferences prefs = Pref.getInstance();
                if (prefs == null) return;
                final long queued = prefs.getLong(PENDING, 0L);
                if (queued == 0L) return;
                prefs.edit().remove(PENDING).apply();
                if (prefs.getBoolean(HIDE, false) || queued > System.currentTimeMillis()
                        || System.currentTimeMillis() - queued > MAX_AGE_MS) return;
                show(host, prefs);
            } catch (Exception e) {
                UserError.Log.e(TAG, "Cannot show optional tip: " + e);
            }
        }, 300L);
    }

    private static void show(final Activity host, final SharedPreferences prefs) {
        final Dialog tip = new Dialog(host, android.R.style.Theme_Holo_Dialog);
        tip.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View root = LayoutInflater.from(host)
                .inflate(R.layout.dialog_source_wizard_tip, null);
        final Drawable background = host.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(
                    host.getResources().getDisplayMetrics().density * 6);
        }
        root.setBackground(background);
        final TextView glyph = root.findViewById(R.id.finishMiniMenuGlyph);
        final View rows = root.findViewById(R.id.finishMiniMenuRows);
        final View hint = root.findViewById(R.id.finishMiniMenuHint);
        final boolean[] opened = {false};
        root.findViewById(R.id.finishMiniMenuToggle).setOnClickListener(v -> {
            hint.setVisibility(View.GONE);
            opened[0] = !opened[0];
            rows.setVisibility(opened[0] ? View.VISIBLE : View.GONE);
            glyph.setText(opened[0] ? "←" : "☰");
        });
        root.findViewById(R.id.finishMiniMenuMaster).setOnClickListener(v -> {
            hint.setVisibility(View.GONE);
            opened[0] = false;
            rows.setVisibility(View.GONE);
            glyph.setText("☰");
        });
        final CheckBox checkbox = root.findViewById(R.id.sourceWizardTipDontShow);
        checkbox.setChecked(prefs.getBoolean(HIDE, false));
        checkbox.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(HIDE, checked).apply());
        root.findViewById(R.id.sourceWizardTipClose).setOnClickListener(v -> tip.dismiss());
        tip.setCancelable(true);
        tip.setContentView(root);
        tip.setOnDismissListener(ignored -> showing = false);
        final Window window = tip.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        showing = true;
        try {
            tip.show();
            if (window != null) {
                final int screenWidth = host.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(screenWidth - Math.round(host.getResources()
                        .getDisplayMetrics().density * 40),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        } catch (Exception e) {
            showing = false;
            UserError.Log.e(TAG, "Cannot open optional tip: " + e);
        }
    }
}
