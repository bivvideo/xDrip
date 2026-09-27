package com.eveningoutpost.dexdrip.services;

import static com.eveningoutpost.dexdrip.cgm.dex.ClassifierAction.lastReadingTimestamp;
import static com.eveningoutpost.dexdrip.models.JoH.msSince;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.UiBased;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.getDexCollectionType;
import static com.eveningoutpost.dexdrip.xdrip.gs;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.eveningoutpost.dexdrip.BestGlucose;
import com.eveningoutpost.dexdrip.BuildConfig;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.alert.Persist;
import com.eveningoutpost.dexdrip.cgm.dex.BlueTails;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;
import com.eveningoutpost.dexdrip.utilitymodels.PumpStatus;
import com.eveningoutpost.dexdrip.utilitymodels.Unitized;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.xdrip;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.val;

/**
 * JamOrHam
 * UI Based Collector
 */

public class UiBasedCollector extends NotificationListenerService {

    private static final String TAG = UiBasedCollector.class.getSimpleName();
    private static final String UI_BASED_STORE_LAST_VALUE = "UI_BASED_STORE_LAST_VALUE";
    private static final String UI_BASED_STORE_LAST_REPEAT = "UI_BASED_STORE_LAST_REPEAT";
    private static final String COMPANION_APP_IOB_ENABLED_PREFERENCE_KEY = "fetch_iob_from_companion_app";
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    private static final HashSet<String> coOptedPackages = new HashSet<>();
    private static final HashSet<String> coOptedPackagesAll = new HashSet<>();
    private static final HashSet<String> companionAppIoBPackages = new HashSet<>();
    private static final HashSet<Pattern> companionAppIoBRegexes = new HashSet<>();
    private static boolean debug = false;

    private static final String ICAN_RU_PACKAGE = "com.sinocare.ican.health.ru";
    private static final long ICAN_POLL_INTERVAL_MS = 60_000L;

    private final Handler iCanPollHandler = new Handler(Looper.getMainLooper());

    private long lastIcanNotificationToken = 0L;
    private String iCanDeliveryMode = "PUSH";

    private final Runnable iCanPollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                pollIcanActiveNotification();
            } catch (Exception e) {
                UserError.Log.e(TAG, "iCan POLL failed: " + e);
            } finally {
                iCanPollHandler.postDelayed(this, ICAN_POLL_INTERVAL_MS);
            }
        }
    };

    @VisibleForTesting
    String lastPackage;

    static {
        coOptedPackages.add("com.dexcom.g6");
        coOptedPackages.add("com.dexcom.g6.region1.mmol");
        coOptedPackages.add("com.dexcom.g6.region2.mgdl");
        coOptedPackages.add("com.dexcom.g6.region3.mgdl");
        coOptedPackages.add("com.dexcom.g6.region4.mmol");
        coOptedPackages.add("com.dexcom.g6.region5.mmol");
        coOptedPackages.add("com.dexcom.g6.region6.mgdl");
        coOptedPackages.add("com.dexcom.g6.region7.mmol");
        coOptedPackages.add("com.dexcom.g6.region8.mmol");
        coOptedPackages.add("com.dexcom.g6.region9.mgdl");
        coOptedPackages.add("com.dexcom.g6.region10.mgdl");
        coOptedPackages.add("com.dexcom.g6.region11.mmol");
        coOptedPackages.add("com.dexcom.dexcomone");
        coOptedPackages.add("com.dexcom.stelo");
        coOptedPackages.add("com.dexcom.g7");
        coOptedPackages.add("com.dexcom.d1plus");
        coOptedPackages.add("com.camdiab.fx_alert.mmoll");
        coOptedPackages.add("com.camdiab.fx_alert.mgdl");
        coOptedPackages.add("com.camdiab.fx_alert.hx.mmoll");
        coOptedPackages.add("com.camdiab.fx_alert.hx.mgdl");
        coOptedPackages.add("com.camdiab.fx_alert.mmoll.ca");
        coOptedPackages.add("com.medtronic.diabetes.guardian");
        coOptedPackages.add("com.medtronic.diabetes.guardianconnect");
        coOptedPackages.add("com.medtronic.diabetes.guardianconnect.us");
        coOptedPackages.add("com.medtronic.diabetes.minimedmobile.eu");
        coOptedPackages.add("com.medtronic.diabetes.minimedmobile.us");
        coOptedPackages.add("com.medtronic.diabetes.simplera.eu");
        coOptedPackages.add("com.senseonics.gen12androidapp");
        coOptedPackages.add("com.senseonics.androidapp");
        coOptedPackages.add("com.microtech.aidexx.mgdl");
        coOptedPackages.add("com.microtech.aidexx.linxneo.mmoll");
        coOptedPackages.add("com.microtech.aidexx.equil.mmoll");
        coOptedPackages.add("com.microtech.aidexx.diaexport.mmoll"); //for microtech germany version, typo is intentional!
        coOptedPackages.add("com.microtech.aidexx.smart.mmoll"); //for microtech Brazil version
        coOptedPackages.add("com.microtech.aidexx.grx1.mmoll");
        coOptedPackages.add("com.ottai.seas");
        coOptedPackages.add("com.microtech.aidexx"); //for microtech china version
        coOptedPackages.add("com.sisensing.eco"); //for SiSensing Eco China version
        coOptedPackages.add("com.ottai.tag"); // //for ottai china version
        coOptedPackages.add("com.senseonics.eversense365.us");
        coOptedPackages.add("com.kakaohealthcare.pasta"); // A Health app for sensors that we already collect from
        coOptedPackages.add("com.sinocare.cgm.ce");
        coOptedPackages.add("com.sinocare.ican.health.ce");
        coOptedPackages.add("com.sinocare.ican.health.ru");
        coOptedPackages.add("com.suswel.ai");
        coOptedPackages.add("com.glucotech.app.android");

        coOptedPackagesAll.add("com.dexcom.dexcomone");
        coOptedPackagesAll.add("com.dexcom.d1plus");
        coOptedPackagesAll.add("com.dexcom.stelo");
        coOptedPackagesAll.add("com.medtronic.diabetes.guardian");
        coOptedPackagesAll.add("com.medtronic.diabetes.simplera.eu");
        coOptedPackagesAll.add("com.senseonics.gen12androidapp");
        coOptedPackagesAll.add("com.senseonics.androidapp");
        coOptedPackagesAll.add("com.microtech.aidexx.mgdl");
        coOptedPackagesAll.add("com.microtech.aidexx.linxneo.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.equil.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.diaexport.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.smart.mmoll"); //for microtech Brazil version
        coOptedPackagesAll.add("com.microtech.aidexx.grx1.mmoll");
        coOptedPackagesAll.add("com.ottai.seas");
        coOptedPackagesAll.add("com.microtech.aidexx"); //for microtech china version
        coOptedPackagesAll.add("com.sisensing.eco"); //for SiSensing Eco China version
        coOptedPackagesAll.add("com.ottai.tag"); // //for ottai china version
        coOptedPackagesAll.add("com.senseonics.eversense365.us");
        coOptedPackagesAll.add("com.kakaohealthcare.pasta"); // Experiment
        coOptedPackagesAll.add("com.sinocare.cgm.ce");
        coOptedPackagesAll.add("com.sinocare.ican.health.ce");
        coOptedPackagesAll.add("com.sinocare.ican.health.ru");
        coOptedPackagesAll.add("com.suswel.ai");
        coOptedPackagesAll.add("com.glucotech.app.android");

        companionAppIoBPackages.add("com.insulet.myblue.pdm");
        companionAppIoBPackages.add("com.medtronic.diabetes.minimedmobile.eu");

        // The IoB value should be captured into the first match group.
        // English localization of the Omnipod 5 App
        companionAppIoBRegexes.add(Pattern.compile("IOB: ([\\d\\.,]+) U"));
        // MiniMed Mobile (EU): "Active Insulin" label and "1.234 U" value in separate TextViews
        companionAppIoBRegexes.add(Pattern.compile("^([\\d\\.]+) U$"));
        // MiniMed Mobile (EU): "Aktives Insulin" label and "1,234 IE" value in separate TextViews
        companionAppIoBRegexes.add(Pattern.compile("^([\\d\\,]+) IE$"));
    }
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        UserError.Log.d(TAG, "iCan POLL fallback started");

        iCanPollHandler.removeCallbacks(iCanPollRunnable);

        // Первый контрольный опрос через 15 секунд,
        // дальше — раз в минуту.
        iCanPollHandler.postDelayed(iCanPollRunnable, 15_000L);
    }

    @Override
    public void onDestroy() {
        iCanPollHandler.removeCallbacks(iCanPollRunnable);
        super.onDestroy();
    }

    private void pollIcanActiveNotification() {

        if (getDexCollectionType() != UiBased) {
            return;
        }

        final StatusBarNotification[] notifications = getActiveNotifications();

        if (notifications == null) {
            UserError.Log.d(TAG, "iCan POLL: no active notifications");
            return;
        }

        for (final StatusBarNotification sbn : notifications) {

            if (!ICAN_RU_PACKAGE.equals(sbn.getPackageName())) {
                continue;
            }

            final Notification notification = sbn.getNotification();

            if (notification == null) {
                return;
            }

            // Android normally changes postTime when an ongoing
            // notification is updated. Notification.when is used
            // as an additional update token where available.
            final long token = Math.max(
                    sbn.getPostTime(),
                    notification.when
            );

            if (token > 0 && token <= lastIcanNotificationToken) {
                UserError.Log.d(TAG, "iCan POLL: notification unchanged");
                return;
            }

            if (token > 0) {
                lastIcanNotificationToken = token;
            }

            lastPackage = ICAN_RU_PACKAGE;
            iCanDeliveryMode = "POLL";

            UserError.Log.d(TAG, "iCan POLL: processing active notification");

            try {
                processNotification(notification);
                BlueTails.immortality();
            } finally {
                iCanDeliveryMode = "PUSH";
            }

            return;
        }

        UserError.Log.d(TAG, "iCan POLL: iCan notification not found");
    }
    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        val fromPackage = sbn.getPackageName();
        if (coOptedPackages.contains(fromPackage)) {
            if (getDexCollectionType() == UiBased) {
                UserError.Log.d(TAG, "Notification from: " + fromPackage);
                if (sbn.isOngoing() || coOptedPackagesAll.contains(fromPackage)) {
                    lastPackage = fromPackage;

                    if (ICAN_RU_PACKAGE.equals(fromPackage)) {
                        final Notification notification = sbn.getNotification();

                        if (notification != null) {
                            final long token = Math.max(
                                    sbn.getPostTime(),
                                    notification.when
                            );

                            if (token > 0) {
                                lastIcanNotificationToken = token;
                            }
                        }

                        iCanDeliveryMode = "PUSH";
                    }

                    processNotification(sbn.getNotification());
                    BlueTails.immortality();
                }
            } else {
                if (JoH.pratelimit("warn-notification-access", 7200)) {
                    UserError.Log.wtf(TAG, "Receiving notifications that we are not enabled to process: " + fromPackage);
                }
            }
        }

        if (companionAppIoBPackages.contains(fromPackage)) {
            processCompanionAppIoBNotification(sbn.getNotification());
        }
    }

    private void processCompanionAppIoBNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        if (notification.contentView != null) {
            processCompanionAppIoBNotificationCV(notification.contentView);
        } else {
            processCompanionAppIoBNotificationTitle(notification);
        }
    }

    private void processCompanionAppIoBNotificationTitle(final Notification notification) {
        Double iob = null;
        try {
            String notificationTitle = notification.extras.getString("android.title");
            iob = parseIoB(notificationTitle);

            if (iob != null) {
                if (debug) UserError.Log.d(TAG, "Inserting new IoB value extracted from title: " + iob);
                PumpStatus.setBolusIoB(iob);
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "exception in processCompanionAppIoBNotificationTitle: " + e);
        }
    }
    private void processCompanionAppIoBNotificationCV(final RemoteViews cview) {
        if (cview == null) return;
        val applied = cview.apply(this, null);
        val root = (ViewGroup) applied.getRootView();
        val texts = new ArrayList<TextView>();
        getTextViews(texts, root);
        if (debug) UserError.Log.d(TAG, "Text views: " + texts.size());
        Double iob = null;
        try {
            for (val view : texts) {
                val tv = (TextView) view;
                String text = tv.getText() != null ? tv.getText().toString() : "";
                val desc = tv.getContentDescription() != null ? tv.getContentDescription().toString() : "";
                if (debug) UserError.Log.d(TAG, "Examining: >" + text + "< : >" + desc + "<");
                iob = parseIoB(text);
                if (iob != null) {
                    break;
                }
            }

            if (iob != null) {
                if (debug) UserError.Log.d(TAG, "Inserting new IoB value extracted from CV: " + iob);
                PumpStatus.setBolusIoB(iob);
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "exception in processCompanionAppIoBNotificationCV: " + e);
        }

        texts.clear();
    }

    Double parseIoB(final String value) {
        for (Pattern pattern : companionAppIoBRegexes) {
            Matcher matcher = pattern.matcher(value);

            if (matcher.find()) {
                return JoH.tolerantParseDouble(matcher.group(1));
            }
        }

        return null;
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        //
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private void processNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }

        JoH.dumpBundle(notification.extras, TAG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val cid = notification.getChannelId();
            UserError.Log.d(TAG, "Channel ID: " + cid);
        }

        boolean handled = false;

        if (notification.contentView != null) {
            handled = processRemote(notification.contentView);
        }

        // iCan can place the glucose value only in the expanded notification.
        if (!handled
                && notification.bigContentView != null
                && notification.bigContentView != notification.contentView) {

            UserError.Log.d(TAG, "Trying bigContentView");
            handled = processRemote(notification.bigContentView);
        }

        // Some modern notifications don't expose normal TextViews,
        // but still put glucose into Notification extras.
        if (!handled) {
            handled = processNotificationExtras(notification);
        }

        if (!handled) {
            UserError.Log.e(TAG, "Content is empty or no glucose value was found");
        }
    }

    private boolean processNotificationExtras(final Notification notification) {
        if (notification.extras == null) return false;

        final String[] keys = new String[] {
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_INFO_TEXT
        };

        for (final String key : keys) {
            try {
                final CharSequence value = notification.extras.getCharSequence(key);

                if (value != null) {
                    final String text = value.toString();

                    UserError.Log.d(
                            TAG,
                            "Examining notification extra " + key + ": >" + text + "<"
                    );

                    final int mgdl = tryExtractNotificationString(text);

                    if (mgdl > 0) {
                        handleNewValue(mgdl);
                        return true;
                    }
                }
            } catch (Exception e) {
                UserError.Log.d(
                        TAG,
                        "Exception examining notification extra " + key + ": " + e
                );
            }
        }

        try {
            final CharSequence[] lines =
                    notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);

            if (lines != null) {
                for (final CharSequence line : lines) {
                    if (line == null) continue;

                    final int mgdl =
                            tryExtractNotificationString(line.toString());

                    if (mgdl > 0) {
                        handleNewValue(mgdl);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.d(
                    TAG,
                    "Exception examining notification text lines: " + e
            );
        }

        return false;
    }

    private int tryExtractNotificationString(final String text) {
        if (!isValidString(text)) return -1;

        int mgdl = tryExtractString(text);

        if (mgdl > 0) {
            return mgdl;
        }

        try {
            final Matcher mmol = Pattern.compile(
                    "(?i)([0-9]{1,2}[\\.,][0-9]+)\\s*mmol\\s*/\\s*l"
            ).matcher(text);

            if (mmol.find()) {
                final double value =
                        JoH.tolerantParseDouble(mmol.group(1), -1);

                if (value > 0) {
                    return (int) Math.round(Unitized.mgdlConvert(value));
                }
            }

            final Matcher mg = Pattern.compile(
                    "(?i)([0-9]{2,3})\\s*mg\\s*/\\s*dl"
            ).matcher(text);

            if (mg.find()) {
                return Integer.parseInt(mg.group(1));
            }

        } catch (Exception e) {
            UserError.Log.d(
                    TAG,
                    "Got exception in tryExtractNotificationString: " + e
            );
        }

        return -1;
    }

    private boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }

    String filterString(final String value) {
        if (lastPackage == null) return value;
        switch (lastPackage) {
            default:
                return (basicFilterString(arrowFilterString(value)))
                        .trim();
        }
    }

    String basicFilterString(final String value) {
        return value
                .replace("\u00a0", " ")
                .replace("\u2060", "")
                .replace("\\", "/")
                .replace("当前血糖:", "")
                .replace("mmol/L", "")
                .replace("mmol/l", "")
                .replace("mg/dL", "")
                .replace("mg/dl", "")
                .replace("≤", "")
                .replace("≥", "");
    }

    String arrowFilterString(final String value) {
        return filterUnicodeRange(filterUnicodeRange(filterUnicodeRange(filterUnicodeRange(value,
                '\u2190', '\u21FF'),
                '\u2700', '\u27BF'),
                '\u2900', '\u297F'),
                '\u2B00', '\u2BFF');
    }

    public String filterUnicodeRange(final String input, final char bottom, final char top) {
        if (bottom > top) {
            throw new RuntimeException("bottom and top of character range invalid");
        }
        val filtered = new StringBuilder(input.length());
        for (final char c : input.toCharArray()) {
            if (c < bottom || c > top) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private boolean processRemote(final RemoteViews cview) {
        if (cview == null) return false;
        val applied = cview.apply(this, null);
        val root = (ViewGroup) applied.getRootView();
        val texts = new ArrayList<TextView>();
        getTextViews(texts, root);
        UserError.Log.d(TAG, "Text views: " + texts.size());
        int matches = 0;
        int mgdl = 0;
        for (val view : texts) {
            try {
                val tv = (TextView) view;
                val text = tv.getText() != null ? tv.getText().toString() : "";
                val desc = tv.getContentDescription() != null ? tv.getContentDescription().toString() : "";
                UserError.Log.d(TAG, "Examining: >" + text + "< : >" + desc + "<");
                val lmgdl = tryExtractString(text);
                if (lmgdl > 0) {
                    mgdl = lmgdl;
                    matches++;
                }
            } catch (Exception e) {
                //
            }
        }
        texts.clear();
        if (matches == 0) {
            UserError.Log.d(TAG, "Did not find any matches");
        } else if (matches > 1) {
            UserError.Log.e(TAG, "Found too many matches: " + matches);
        } else {
            handleNewValue(mgdl);
            return true;
        }
        return false;
    }

    int tryExtractString(final String text) {
        int mgdl = -1;
        try {
            val ftext = filterString(text);
            if (Unitized.usingMgDl()) {
                mgdl = Integer.parseInt(ftext);
            } else {
                if (isValidMmol(ftext)) {
                    val result = JoH.tolerantParseDouble(ftext, -1);
                    if (result != -1) {
                        mgdl = (int) Math.round(Unitized.mgdlConvert(result));
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.d(TAG, "Got exception in tryExtractString: " + e);
        }
        return mgdl;
    }

    boolean handleNewValue(final int mgdl) {
        val timestamp = JoH.tsl();
        return handleNewValue(timestamp, mgdl);
    }

    boolean handleNewValue(final long timestamp, final int mgdl) {
        Sensor.createDefaultIfMissing();

        UserError.Log.d(TAG, "Found specific value: " + mgdl);

        if (ICAN_RU_PACKAGE.equals(lastPackage)) {
            UserError.Log.d(
                    TAG,
                    "iCan "
                            + iCanDeliveryMode
                            + ": "
                            + String.format(
                            Locale.US,
                            "%.1f mmol/L",
                            Unitized.mmolConvert(mgdl)
                    )
            );
        }

        if ((mgdl >= 40 && mgdl <= 405)) {
            val grace = DexCollectionType.getCurrentSamplePeriod() * 4;
            val recentbt = msSince(lastReadingTimestamp) < grace;
            val dedupe = (!recentbt && isDifferentToLast(mgdl)) ? Constants.SECOND_IN_MS * 10
                    : DexCollectionType.getCurrentDeduplicationPeriod();
            val period = recentbt ? grace : dedupe;
            val existing = BgReading.getForPreciseTimestamp(timestamp, period, false);
            if (existing == null) {
                if (isJammed(mgdl)) {
                    UserError.Log.wtf(TAG, "Apparently value is jammed at: " + mgdl);
                } else {
                    UserError.Log.d(TAG, "Inserting new value");
                    PersistentStore.setLong(UI_BASED_STORE_LAST_VALUE, mgdl);
                    val bgr = BgReading.bgReadingInsertFromG5(mgdl, timestamp);
                    if (bgr != null) {
                        bgr.find_slope();
                        bgr.noRawWillBeAvailable();
                        bgr.injectDisplayGlucose(BestGlucose.getDisplayGlucose());

                        sendLiveCgmToGoogleForm(timestamp, mgdl);

                        return true;
                    }
                }
            } else {
                UserError.Log.d(TAG, "Duplicate value: " + existing.timeStamp());
            }
        } else {
            UserError.Log.wtf(TAG, "Glucose value outside acceptable range: " + mgdl);
        }
        return false;
    }
    private void sendLiveCgmToGoogleForm(final long timestamp, final int mgdl) {

        final String formId = BuildConfig.ICAN_GOOGLE_FORM_ID;

        if (formId == null || formId.trim().isEmpty()) {
            UserError.Log.e(TAG, "CGM live upload skipped: Google Form ID is empty");
            return;
        }

        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                final String measuredAt =
                        new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ssXXX",
                                Locale.US
                        ).format(new Date(timestamp));

                final String glucose =
                        String.format(
                                Locale.US,
                                "%.1f",
                                Unitized.mmolConvert(mgdl)
                        );

                final String payload =
                        "entry.1982099995="
                                + URLEncoder.encode(
                                measuredAt,
                                StandardCharsets.UTF_8.name()
                        )
                                + "&entry.11066590="
                                + URLEncoder.encode(
                                glucose,
                                StandardCharsets.UTF_8.name()
                        )
                                + "&entry.1843424689="
                                + URLEncoder.encode(
                                "xDrip-iCan",
                                StandardCharsets.UTF_8.name()
                        );

                final URL url = new URL(
                        "https://docs.google.com/forms/d/e/"
                                + formId
                                + "/formResponse"
                );

                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                conn.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                );

                final byte[] body =
                        payload.getBytes(StandardCharsets.UTF_8);

                conn.setFixedLengthStreamingMode(body.length);

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }

                final int responseCode = conn.getResponseCode();

                if (responseCode >= 200 && responseCode < 400) {

                    UserError.Log.d(
                            TAG,
                            "CGM live upload OK: "
                                    + responseCode
                                    + " "
                                    + measuredAt
                                    + " "
                                    + glucose
                    );

                } else {

                    UserError.Log.e(
                            TAG,
                            "CGM live upload HTTP error: "
                                    + responseCode
                    );
                }

            } catch (Exception e) {

                UserError.Log.e(
                        TAG,
                        "CGM live upload failed: "
                                + e
                );

            } finally {

                if (conn != null) {
                    conn.disconnect();
                }
            }

        }, "iCan-CGM-FormUpload").start();
    }
    static boolean isValidMmol(final String text) {
        return text.matches("[0-9]+[.,][0-9]+");
    }

    private boolean shouldAllowTimeOffsetChange(final int mgdl) {
        return isDifferentToLast(mgdl); // TODO do we need to rate limit this or not?
    }

    // note this method only checks existing stored data
    private boolean isDifferentToLast(final int mgdl) {
        val previousValue = PersistentStore.getLong(UI_BASED_STORE_LAST_VALUE);
        return previousValue != mgdl;
    }

    // note this method actually updates the stored value
    private boolean isJammed(final int mgdl) {
        val previousValue = PersistentStore.getLong(UI_BASED_STORE_LAST_VALUE);
        if (previousValue == mgdl) {
            PersistentStore.incrementLong(UI_BASED_STORE_LAST_REPEAT);
        } else {
            PersistentStore.setLong(UI_BASED_STORE_LAST_REPEAT, 0);
        }
        val lastRepeat = PersistentStore.getLong(UI_BASED_STORE_LAST_REPEAT);
        UserError.Log.d(TAG, "Last repeat: " + lastRepeat);
        return lastRepeat > jamThreshold();
    }

    private int jamThreshold() {
        if (lastPackage != null) {
            if (lastPackage.startsWith("com.medtronic")) return 9;
        }
        return 6;
    }

    private void getTextViews(final List<TextView> output, final ViewGroup parent) {
        val children = parent.getChildCount();
        for (int i = 0; i < children; i++) {
            val view = parent.getChildAt(i);
            if (view.getVisibility() == View.VISIBLE) {
                if (view instanceof TextView) {
                    output.add((TextView) view);
                } else if (view instanceof ViewGroup) {
                    getTextViews(output, (ViewGroup) view);
                }
            }
        }
    }

    public static void onEnableCheckPermission(final Activity activity) {
        if (DexCollectionType.getDexCollectionType() == UiBased) {
            UserError.Log.d(TAG, "Detected that we are enabled");
            switchToAndEnable(activity);
        }
    }

    public static SharedPreferences.OnSharedPreferenceChangeListener getListener(final Activity activity) {
        return (prefs, key) -> {
            if (key.equals(DexCollectionType.DEX_COLLECTION_METHOD)) {
                try {
                    onEnableCheckPermission(activity);
                } catch (Exception e) {
                    //
                }
            }
            if (key.equals(COMPANION_APP_IOB_ENABLED_PREFERENCE_KEY)) {
                try {
                    enableNotificationService(activity);
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Exception when enabling NotificationService: " + e);
                }
            }
        };
    }

    public static void switchToAndEnable(final Activity activity) {
        DexCollectionType.setDexCollectionType(UiBased);
        Sensor.createDefaultIfMissing();
        enableNotificationService(activity);
    }

    private static void enableNotificationService(final Activity activity) {
        if (!isNotificationServiceEnabled()) {
            JoH.show_ok_dialog(activity, gs(R.string.please_allow_permission),
                    "Permission is needed to receive data from other applications. xDrip does not do anything beyond this scope. Please enable xDrip on the next screen",
                    () -> activity.startActivity(new Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
    }

    private static boolean isNotificationServiceEnabled() {
        val pkgName = xdrip.getAppContext().getPackageName();
        val flat = Settings.Secure.getString(xdrip.getAppContext().getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":");
            for (val name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
