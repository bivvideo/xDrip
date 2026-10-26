package com.eveningoutpost.dexdrip.utilitymodels;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.annotation.StringRes;
import androidx.annotation.DrawableRes;
import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.BindingAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.eveningoutpost.dexdrip.BR;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.databinding.DialogTreeSelectorBinding;
import com.eveningoutpost.dexdrip.utils.DexCollectionHelper;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.xdrip;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

import me.tatarka.bindingcollectionadapter2.ItemBinding;

import static com.eveningoutpost.dexdrip.ui.helpers.UiHelper.convertDpToPixel;

/**
 * Created by jamorham on 20/12/2017.
 *
 * Guides the user through the process of selecting a source from a hierarchical menu.
 *
 */

import static com.eveningoutpost.dexdrip.xdrip.gs;
public class SourceWizard {
    @SuppressLint("StaticFieldLeak")
    private static final String TAG = "SourceWizard";
    private static volatile SourceWizard sw;
    private Dialog dialog;
    private Activity activity;
    // Expansion belongs to this wizard instance, so it survives the confirmation dialog.
    private final HashSet<Tree<Item>> expandedBranches = new HashSet<>();
    private ViewModel listModel;
    private RecyclerView wizardList;
    // Navigation is organized by user intent, not by internal collector names.
    // Display-only changes: each leaf still uses an existing DexCollectionType.
    private Tree<Item> root = new Tree<>(new Item(
            gs(R.string.source_wizard_title), gs(R.string.source_wizard_subtitle)));

    {
        // Alphabetical device-first catalog. Different app routes can share a
        // collector: these are named presets, not new acquisition protocols.
        // Preparation is a separate action, not an expandable sensor group.
        root.addChild(new Item(gs(R.string.source_wizard_before_connection), true));
        root.addChild(new Item(gs(R.string.source_wizard_readiness_title), (String) null, 0)
                .withReadinessAction());
        root.addChild(new Item(gs(R.string.source_wizard_sensors_section), true));

        // CareSens Air: official broadcast or glucose notifications from PASTA.
        Tree<Item> caresens = root.addChild(new Item("CareSens Air",
                (String) null, R.drawable.caresens_icon));
        caresens.addChild(new Item(gs(R.string.source_wizard_caresens_air_app),
                gs(R.string.source_wizard_broadcast_route),
                DexCollectionType.NSEmulator, R.drawable.caresens_icon)
                .withDeviceIcon());
        caresens.addChild(new Item(gs(R.string.source_wizard_caresens_pasta),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.caresens_icon)
                .withDeviceIcon());

        Tree<Item> dex = root.addChild(new Item("Dexcom",
                (String) null, R.drawable.dex_icon));
        // Preserve the original direct-connection DexcomG6 -> DexcomG5 setup path.
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_models),
                gs(R.string.source_wizard_dexcom_direct_summary),
                DexCollectionType.DexcomG6, R.drawable.dexcom_g6_g7_icon)
                .withDeviceIcon().withConfirmationName("Dexcom"));
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_camaps),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.dexcom_g6_g7_icon)
                .withDeviceIcon());
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_dblg2),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.dexcom_g7_icon)
                .withDeviceIcon());
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_pasta),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.dexcom_g7_icon)
                .withDeviceIcon());
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_app),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.source_wizard_source_placeholder));

        // Two existing Eversense app-based routes, unchanged.
        Tree<Item> eversense = root.addChild(new Item("Eversense",
                (String) null, R.drawable.wikimedia_eversense_icon_pbroks13));
        eversense.addChild(new Item(gs(R.string.source_wizard_eversense_app),
                gs(R.string.source_wizard_eversense_app_summary),
                DexCollectionType.UiBased, R.drawable.eversence_360)
                .withDeviceIcon());
        eversense.addChild(new Item(gs(R.string.source_wizard_eversense_esel),
                gs(R.string.source_wizard_eversense_esel_summary),
                DexCollectionType.NSEmulator, R.drawable.eversence_360)
                .withDeviceIcon());

        Tree<Item> libre = root.addChild(new Item("FreeStyle Libre",
                (String) null, R.drawable.libre_icon_image));
        libre.addChild(new Item(gs(R.string.source_wizard_libre_one_bridge),
                gs(R.string.source_wizard_libre_one_bridge_summary),
                DexCollectionType.LimiTTer, R.drawable.libre_1_bridge_icon)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_one_alarm),
                gs(R.string.source_wizard_libre_one_alarm_summary),
                DexCollectionType.LibreAlarm, R.drawable.ic_watch_grey600_48dp)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_two_direct),
                gs(R.string.source_wizard_libre_two_direct_summary),
                DexCollectionType.LimiTTer, R.drawable.libre_2_icon)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_camaps),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.libre_123_icon)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_juggluco),
                gs(R.string.source_wizard_libre_juggluco_summary),
                DexCollectionType.NSEmulator, R.drawable.libra_1234_v2_icon)
                .withDeviceIcon());
        Tree<Item> libreMore = libre.addChild(new Item(
                gs(R.string.source_wizard_libre_more),
                (String) null, R.drawable.source_wizard_source_placeholder)
                .withAuxiliaryTitle()
                .withFullWidthDivider());
        libreMore.addChild(new Item(gs(R.string.source_wizard_libre_network),
                gs(R.string.source_wizard_libre_network_summary),
                DexCollectionType.LibreWifi, R.drawable.libre_2_icon)
                .withDeviceIcon());
        libreMore.addChild(new Item(gs(R.string.source_wizard_libre_combined),
                gs(R.string.source_wizard_libre_combined_summary),
                DexCollectionType.LimiTTerWifi, R.drawable.libre_2_icon)
                .withDeviceIcon());
        libreMore.addChild(new Item(gs(R.string.source_wizard_libre_juggluco_legacy),
                gs(R.string.source_wizard_libre_juggluco_legacy_summary),
                DexCollectionType.LibreReceiver, R.drawable.libra_1234_v2_icon)
                .withDeviceIcon());

        // iCAN and rebranded GlucoMen iCan share notification collectors.
        Tree<Item> ican = root.addChild(new Item("iCAN",
                gs(R.string.source_wizard_ican_examples), R.drawable.ican3_icon)
                .withSummary(gs(R.string.source_wizard_ican_examples)));
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i3_cgm),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon());
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i3_health),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon());
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i6_cgm),
                gs(R.string.source_wizard_pending_confirmation),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon());
        ican.addChild(new Item(gs(R.string.source_wizard_glucomen_o3),
                gs(R.string.source_wizard_pending_confirmation),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon());

        // MiniMed names refer to pump systems; Guardian and Simplera to CGMs.
        Tree<Item> medtronic = root.addChild(new Item("Medtronic",
                gs(R.string.source_wizard_medtronic_examples), 0)
                .withSummary(gs(R.string.source_wizard_medtronic_examples)));
        medtronic.addChild(new Item(gs(R.string.source_wizard_medtronic_guardian),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.guardian_3_4_icon).withDeviceIcon());
        medtronic.addChild(new Item(gs(R.string.source_wizard_medtronic_minimed),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.medtronic_770_v2_icon).withDeviceIcon());
        medtronic.addChild(new Item(gs(R.string.source_wizard_medtronic_simplera),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.medtronic_simplera_icon).withDeviceIcon());
        Item carelinkLink = new Item(gs(R.string.source_wizard_medtronic_carelink),
                (String) null, 0).withAuxiliaryTitle();

        medtronic.addChild(carelinkLink);

        // MicroTech branded models and regional notification apps.
        Tree<Item> microtech = root.addChild(new Item(
                gs(R.string.source_wizard_microtech_group),
                gs(R.string.source_wizard_microtech_examples),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_microtech_examples)));
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_glucotech),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.aidex_x_icon).withDeviceIcon());
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_linx),
                gs(R.string.source_wizard_microtech_linx_summary),
                DexCollectionType.UiBased, R.drawable.aidex_x_icon).withDeviceIcon());
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_glucorx),
                gs(R.string.source_wizard_microtech_glucorx_summary),
                DexCollectionType.AidexReceiver, R.drawable.glucorx_icon).withDeviceIcon());
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_vitatok),
                gs(R.string.source_wizard_pending_confirmation),
                DexCollectionType.UiBased, R.drawable.aidex_x_icon).withDeviceIcon());
        Item microtechOther = new Item(gs(R.string.source_wizard_microtech_other),
                (String) null, 0).withAuxiliaryTitle()
                .withSummary(gs(R.string.source_wizard_microtech_other_summary));
        microtech.addChild(microtechOther);

        // GS1 works through Juggluco's compatible broadcast. SiSensing Eco
        // is a CHINESE companion package; it is not the international ECO app.
        Tree<Item> sibionics = root.addChild(new Item("SIBIONICS",
                gs(R.string.source_wizard_sibionics_examples), 0)
                .withSummary(gs(R.string.source_wizard_sibionics_examples)));
        sibionics.addChild(new Item(gs(R.string.source_wizard_sibionics_gs1),
                gs(R.string.source_wizard_broadcast_route),
                DexCollectionType.NSEmulator, R.drawable.sibionics_gs1_icon).withDeviceIcon());
        sibionics.addChild(new Item(gs(R.string.source_wizard_sibionics_eco),
                gs(R.string.source_wizard_sibionics_china_only),
                DexCollectionType.UiBased, R.drawable.sibionics_gs1_icon).withDeviceIcon());

        // Catch-all is intentionally last, with only real ungrouped devices.
        Tree<Item> otherSensors = root.addChild(new Item(
                gs(R.string.source_wizard_other_sensors),
                gs(R.string.source_wizard_other_sensors),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_other_sensors_examples)));
        otherSensors.addChild(new Item(gs(R.string.source_wizard_smartguide),
                gs(R.string.source_wizard_smartguide_summary),
                DexCollectionType.GluPro, R.drawable.accucheksmartguide_icon)
                .withDeviceIcon());
        otherSensors.addChild(new Item(gs(R.string.source_wizard_medtrum_a6),
                gs(R.string.source_wizard_medtrum_a6_summary),
                DexCollectionType.Medtrum, R.drawable.medtrum_a6_icon)
                .withDeviceIcon());
        otherSensors.addChild(new Item(gs(R.string.source_wizard_ottai_m8),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.ottai_icon).withDeviceIcon());

        // These are universal fallback methods, not a second sensor catalog.
        root.addChild(new Item(gs(R.string.source_wizard_apps_section), true));
        Tree<Item> companion = root.addChild(new Item(
                gs(R.string.source_wizard_companion_group),
                gs(R.string.source_wizard_companion_details),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_companion_examples)));
        companion.addChild(new Item(gs(R.string.source_wizard_not_found),
                gs(R.string.source_wizard_companion_explanation), 0).withInformationText());
        companion.addChild(new Item(gs(R.string.source_wizard_try_connection),
                DexCollectionType.UiBased, R.drawable.source_wizard_source_placeholder)
                .withCallToAction()
                .withFullWidthDivider()
                .withConfirmationName(gs(R.string.source_wizard_companion_group)));
        // Follow the MicroTech generic entry into the same explanation.
        microtechOther.withNavigationTarget(companion);

        Tree<Item> broadcast = root.addChild(new Item("Inter-app broadcast",
                gs(R.string.source_wizard_broadcast_examples),
                R.drawable.mm600_series)
                .withSummary(gs(R.string.source_wizard_broadcast_examples)));
        broadcast.addChild(new Item(gs(R.string.source_wizard_not_found),
                gs(R.string.source_wizard_broadcast_explanation), 0)
                .withInformationText().withBroadcastInformation());
        broadcast.addChild(new Item(gs(R.string.source_wizard_try_connection),
                DexCollectionType.NSEmulator, R.drawable.source_wizard_source_placeholder)
                .withCallToAction()
                .withFullWidthDivider()
                .withConfirmationName("Inter-app broadcast"));

        // Hardware bridges: expose all five original Wixel technical modes.
        root.addChild(new Item(gs(R.string.source_wizard_bridges_section), true));
        Tree<Item> bridges = root.addChild(new Item(gs(R.string.source_wizard_wixel_section),
                gs(R.string.source_wizard_wixel_section),
                R.drawable.source_wizard_source_placeholder));
        bridges.addChild(new Item("Bluetooth Wixel", DexCollectionType.BluetoothWixel,
                R.drawable.source_wizard_source_placeholder));
        bridges.addChild(new Item("xBridge Wixel", DexCollectionType.DexbridgeWixel,
                R.drawable.source_wizard_source_placeholder));
        bridges.addChild(new Item("Wifi Wixel", DexCollectionType.WifiWixel,
                R.drawable.source_wizard_source_placeholder));
        bridges.addChild(new Item("Wifi Wixel + BT Wixel", DexCollectionType.WifiBlueToothWixel,
                R.drawable.source_wizard_source_placeholder));
        bridges.addChild(new Item("Wifi Wixel + xBridge Wixel", DexCollectionType.WifiDexBridgeWixel,
                R.drawable.source_wizard_source_placeholder));

        // Followers are reusable explanation stacks. A Medtronic deep-link
        // opens the same CareLink stack, not a second copy of its settings.
        root.addChild(new Item(gs(R.string.source_wizard_sync_section), true));
        Tree<Item> followers = root.addChild(new Item(gs(R.string.source_wizard_followers_group),
                gs(R.string.source_wizard_followers_section),
                R.drawable.x_sync_follow_icon));
        addFollower(followers, "CareLink Follower", R.drawable.source_wizard_source_placeholder,
                R.string.source_wizard_follower_carelink_body, DexCollectionType.CLFollow);
        addFollower(followers, "Dex Share Follower", R.drawable.nsfollow_icon,
                R.string.source_wizard_follower_dexshare_body, DexCollectionType.SHFollow);
        addFollower(followers, "Nightscout Follower", R.drawable.nsfollow_icon,
                R.string.source_wizard_follower_nightscout_body, DexCollectionType.NSFollow);
        addFollower(followers, "Web Follower", R.drawable.source_wizard_source_placeholder,
                R.string.source_wizard_follower_web_body, DexCollectionType.WebFollow);
        addFollower(followers, "xDrip+ Sync Follower", R.drawable.x_sync_follow_icon,
                R.string.source_wizard_follower_xdrip_body, DexCollectionType.Follower);
        // CareLink is the first child; no duplicate selection or explanation.
        carelinkLink.withNavigationTarget(followers.getChildren().get(0));
    }

    private Tree<Item> addFollower(Tree<Item> parent, String name, int icon,
            @StringRes int explanation, DexCollectionType type) {
        final Tree<Item> stack = parent.addChild(new Item(name, (String) null, icon));
        stack.addChild(new Item(gs(R.string.source_wizard_follower_about),
                gs(explanation), 0).withInformationText());
        stack.addChild(new Item(gs(R.string.source_wizard_try_connection),
                type, R.drawable.source_wizard_source_placeholder)
                .withCallToAction().withFullWidthDivider()
                .withConfirmationName(name));
        return stack;
    }

    // Rows without icons need dividers to start at their text, not an empty icon slot.
    @BindingAdapter("sourceWizardDividerInset")
    public static void bindSourceWizardDividerInset(View view, int startInset) {
        final ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            if (params.getMarginStart() != startInset) {
                params.setMarginStart(startInset);
                view.setLayoutParams(params);
            }
        }
    }

    // Data Binding has no built-in setter for layout_margin* attributes.
    // Apply the group-specific margins from one supported custom adapter,
    // instead of binding Android LayoutParams directly in XML.
    @BindingAdapter("sourceWizardPlate")
    public static void bindSourceWizardPlate(View view, Item item) {
        if (item == null) return;
        final ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            final int top = item.getPlateTopMargin();
            final int bottom = item.getPlateBottomMargin();
            if (params.topMargin != top || params.bottomMargin != bottom) {
                params.topMargin = top;
                params.bottomMargin = bottom;
                view.setLayoutParams(params);
            }
        }
        view.setBackground(item.getPlateBackground());
    }

    public SourceWizard(Activity activity) {
        this.activity = activity;
    }

    public static void start(Activity activity) {
        start(activity, false);
    }

    public synchronized static void start(Activity activity, boolean force) {
        if (sw == null || sw.activity != activity) sw = new SourceWizard(activity);
        if (force) {
            if (sw.showing()) sw.dismiss();
        }
        if (!sw.showing()) sw.getTreeDialog(null);
    }

    private static String gs(@StringRes final int id) {
        return xdrip.getAppContext().getString(id);
    }

    private boolean showing() {
        return (dialog != null) && dialog.isShowing();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception e) {
            //
        }
    }

    // A standalone Holo dialog lets the xDrip gradient cover the entire visible
    // chooser, rather than hiding it below opaque AlertDialog title/button panels.
    // Leaf confirmation and its DexCollectionHelper path remain unchanged.
    public void getTreeDialog(Tree<Item> branch) {
        dismissDialog();
        if (branch == null || !branch.data.isCollectionType()) {
            final Dialog picker = new Dialog(activity, android.R.style.Theme_Holo_Dialog);
            picker.requestWindowFeature(Window.FEATURE_NO_TITLE);
            final DialogTreeSelectorBinding binding = DialogTreeSelectorBinding.inflate(
                    LayoutInflater.from(picker.getContext()));
            listModel = new ViewModel();
            binding.setViewModel(listModel);
            binding.sourceWizardList.setItemAnimator(null);
            wizardList = binding.sourceWizardList;
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            binding.sourceWizardList.getLayoutParams().height = Math.round(screenHeight * 0.62f);

            final Drawable background = picker.getContext().getDrawable(R.drawable.holo_background);
            if (background instanceof GradientDrawable) {
                ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
            }
            binding.getRoot().setBackground(background);
            picker.setContentView(binding.getRoot());
            picker.setCancelable(false);
            dialog = picker;
            try {
                picker.show();
                final Window window = picker.getWindow();
                if (window != null) {
                    // The background belongs to the content, so no opaque Holo panel hides it.
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                    window.setLayout(screenWidth - Math.round(convertDpToPixel(12)),
                            WindowManager.LayoutParams.WRAP_CONTENT);
                }
            } catch (Exception e) {
                UserError.Log.e(TAG, "Exception showing source wizard: " + e);
            }
        } else {
            final Item item = branch.data;
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK);
            builder.setTitle(R.string.are_you_sure);
            builder.setMessage(activity.getString(R.string.set_data_source_to, item.getConfirmationName()));
            builder.setPositiveButton(R.string.yes, (ignored, which) -> {
                DexCollectionType.setDexCollectionType(item.getCollectionType());
                dismissDialog();
                DexCollectionHelper.assistance(activity, item.getCollectionType());
                sw = null;
            });
            builder.setNegativeButton(R.string.no, (ignored, which) -> getTreeDialog(null));
            dialog = builder.create();
            try {
                dialog.show();
            } catch (Exception e) {
                UserError.Log.e(TAG, "Exception showing source confirmation: " + e);
            }
        }
    }

    // SW-008 readiness window: stability and Notification Listener are live;
    // Bluetooth and location remain explicitly visual examples.
    // The parent source picker stays open behind this second Holo dialog.
    private void showReadinessPlaceholder() {
        final Dialog readiness = new Dialog(activity, android.R.style.Theme_Holo_Dialog);
        readiness.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View content = LayoutInflater.from(readiness.getContext())
                .inflate(R.layout.dialog_source_wizard_readiness, null);
        final LinearLayout stability = content.findViewById(R.id.readinessStabilityRows);
        final LinearLayout connection = content.findViewById(R.id.readinessConnectionRows);

        // Real, side-effect-free stability probes. App locking is unknown on
        // current Android APIs; never display a guessed success or failure.
        final List<Runnable> startChecks = new ArrayList<>();
        final List<Runnable> cancelChecks = new ArrayList<>();
        startChecks.add(addLiveReadinessRow(readiness, stability,
                R.string.source_wizard_readiness_battery,
                SourceWizardReadiness.Requirement.BATTERY_OPTIMIZATION,
                false, true, cancelChecks));
        startChecks.add(addLiveReadinessRow(readiness, stability,
                R.string.source_wizard_readiness_autostart,
                SourceWizardReadiness.Requirement.AUTOSTART,
                false, true, cancelChecks));
        startChecks.add(addLiveReadinessRow(readiness, stability,
                R.string.source_wizard_readiness_pin,
                SourceWizardReadiness.Requirement.APP_LOCK,
                true, false, cancelChecks));

        // The existing UiBased collector remains the source of truth here.
        startChecks.add(addLiveReadinessRow(readiness, connection,
                R.string.source_wizard_readiness_notification_listener,
                SourceWizardReadiness.Requirement.NOTIFICATION_LISTENER,
                false, true, cancelChecks));
        // Bluetooth and location still use visual examples in this iteration.
        final ImageView checkingIcon = addReadinessPreviewRow(connection,
                R.string.source_wizard_readiness_bluetooth,
                R.drawable.source_wizard_readiness_checking,
                R.string.source_wizard_readiness_status_checking,
                false, false, false);
        addReadinessPreviewRow(connection, R.string.source_wizard_readiness_location,
                R.drawable.source_wizard_readiness_unknown, R.string.source_wizard_readiness_status_unknown,
                false, true, true);

        // Recheck all live settings when returning from a system Settings screen.
        final boolean[] lostFocus = {false};
        final ViewTreeObserver.OnWindowFocusChangeListener focusListener = hasFocus -> {
            if (!hasFocus) {
                lostFocus[0] = true;
            } else if (lostFocus[0]) {
                lostFocus[0] = false;
                for (Runnable check : startChecks) check.run();
            }
        };
        content.findViewById(R.id.readinessClose).setOnClickListener(v -> readiness.dismiss());

        final Drawable background = activity.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
        }
        content.setBackground(background);
        readiness.setContentView(content);
        try {
            readiness.show();
            // Only Bluetooth still uses the perpetual demo spinner.
            final Animation checkingAnimation = AnimationUtils.loadAnimation(
                    activity, R.anim.source_wizard_readiness_spin);
            checkingIcon.startAnimation(checkingAnimation);

            final ViewTreeObserver focusObserver = content.getViewTreeObserver();
            focusObserver.addOnWindowFocusChangeListener(focusListener);
            readiness.setOnDismissListener(ignored -> {
                checkingIcon.clearAnimation();
                for (Runnable cancel : cancelChecks) cancel.run();
                if (focusObserver.isAlive()) {
                    focusObserver.removeOnWindowFocusChangeListener(focusListener);
                }
            });
            for (Runnable check : startChecks) check.run();

            final Window window = readiness.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(screenWidth - Math.round(convertDpToPixel(12)),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
            // Allow the readiness body to grow to fit these rows, closer to the
            // source picker height. Keep ScrollView active on smaller displays.
            // Do not modify the header/footer or remove the scrolling fallback.
            final ScrollView scroll = content.findViewById(R.id.readinessPreviewScroll);
            final int maximum = Math.round(
                    activity.getResources().getDisplayMetrics().heightPixels * 0.68f);
            scroll.post(() -> {
                if (!readiness.isShowing() || scroll.getChildCount() == 0) return;
                if (scroll.getChildAt(0).getHeight() > maximum) {
                    final ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                    lp.height = maximum;
                    scroll.setLayoutParams(lp);
                }
            });
        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception showing readiness preview: " + e);
        }
    }

    private Runnable addLiveReadinessRow(final Dialog readiness, final LinearLayout parent,
            @StringRes final int label, final SourceWizardReadiness.Requirement requirement,
            final boolean last, final boolean animate, final List<Runnable> cancelChecks) {
        final ImageView icon = addReadinessPreviewRow(parent, label,
                R.drawable.source_wizard_readiness_unknown,
                R.string.source_wizard_readiness_status_unknown, false, false, last);
        final View row = (View) icon.getParent();
        final TextView fix = row.findViewById(R.id.readinessRowFix);
        final TextView info = row.findViewById(R.id.readinessRowInfo);
        fix.setOnClickListener(v -> SourceWizardReadiness.requestFix(activity, requirement));
        info.setOnClickListener(v -> showReadinessHelp(requirement, label));

        final Runnable finish = () -> {
            if (!readiness.isShowing()) return;
            icon.clearAnimation();
            final SourceWizardReadiness.Result result =
                    SourceWizardReadiness.inspect(activity, requirement);
            final int drawable, description;
            switch (result.state) {
                case READY:
                    drawable = R.drawable.source_wizard_readiness_ok;
                    description = R.string.source_wizard_readiness_real_ready;
                    break;
                case MISSING:
                    drawable = R.drawable.source_wizard_readiness_missing;
                    description = R.string.source_wizard_readiness_real_missing;
                    break;
                default:
                    drawable = R.drawable.source_wizard_readiness_unknown;
                    description = R.string.source_wizard_readiness_real_unknown;
                    break;
            }
            icon.setImageResource(drawable);
            icon.setContentDescription(activity.getString(description));
            fix.setVisibility(result.state == SourceWizardReadiness.State.MISSING && result.canFix
                    ? View.VISIBLE : View.GONE);
            info.setVisibility(result.state == SourceWizardReadiness.State.UNKNOWN
                    || (result.state == SourceWizardReadiness.State.MISSING && !result.canFix)
                    ? View.VISIBLE : View.GONE);
        };
        final Runnable start = () -> {
            if (!readiness.isShowing()) return;
            icon.removeCallbacks(finish);
            fix.setVisibility(View.GONE);
            info.setVisibility(View.GONE);
            if (animate) {
                icon.setImageResource(R.drawable.source_wizard_readiness_checking);
                icon.setContentDescription(
                        activity.getString(R.string.source_wizard_readiness_real_checking));
                icon.startAnimation(AnimationUtils.loadAnimation(
                        activity, R.anim.source_wizard_readiness_spin));
                icon.postDelayed(finish, 450L);
            } else {
                // No Android/OEM API can report recent-app lock reliably.
                finish.run();
            }
        };
        cancelChecks.add(() -> {
            icon.clearAnimation();
            icon.removeCallbacks(finish);
        });
        return start;
    }

    private void showReadinessHelp(final SourceWizardReadiness.Requirement requirement,
            @StringRes final int label) {
        final int message;
        switch (requirement) {
            case BATTERY_OPTIMIZATION:
                message = R.string.source_wizard_readiness_battery_help;
                break;
            case AUTOSTART:
                message = R.string.source_wizard_readiness_autostart_help;
                break;
            case APP_LOCK:
                message = R.string.source_wizard_readiness_pin_help;
                break;
            case NOTIFICATION_LISTENER:
                message = R.string.source_wizard_readiness_real_unknown_help;
                break;
            default:
                return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK)
                .setTitle(label)
                .setMessage(message)
                .setNegativeButton(android.R.string.ok, null);
        if (requirement == SourceWizardReadiness.Requirement.AUTOSTART
                && SourceWizardReadiness.canOpenAutostartSettings(activity)) {
            builder.setPositiveButton(R.string.source_wizard_readiness_open_settings,
                    (dialog, which) -> SourceWizardReadiness.openAutostartSettings(activity));
        }
        builder.show();
    }

    private ImageView addReadinessPreviewRow(final LinearLayout parent, @StringRes int label,
            @DrawableRes int statusIcon, @StringRes int statusDescription,
            boolean fix, boolean info, boolean last) {
        final View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.source_wizard_readiness_row, parent, false);
        ((TextView) row.findViewById(R.id.readinessRowLabel)).setText(label);
        final ImageView icon = row.findViewById(R.id.readinessRowStatus);
        icon.setImageResource(statusIcon);
        icon.setContentDescription(activity.getString(statusDescription));

        final TextView fixAction = row.findViewById(R.id.readinessRowFix);
        fixAction.setVisibility(fix ? View.VISIBLE : View.GONE);
        if (fix) fixAction.setOnClickListener(v -> showReadinessPreviewNotice());

        final TextView infoAction = row.findViewById(R.id.readinessRowInfo);
        infoAction.setVisibility(info ? View.VISIBLE : View.GONE);
        if (info) infoAction.setOnClickListener(v -> showReadinessPreviewNotice());

        row.findViewById(R.id.readinessRowDivider)
                .setVisibility(last ? View.GONE : View.VISIBLE);
        parent.addView(row);
        return icon;
    }

    private void showReadinessPreviewNotice() {
        Toast.makeText(activity, R.string.source_wizard_readiness_preview_action,
                Toast.LENGTH_SHORT).show();
    }

    private synchronized void dismissDialog() {
        if ((dialog != null) && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // Source labels are UI-only; DexCollectionType mappings remain unchanged.
    public class Item {
        public String name;
        public int resource;
        String description;
        DexCollectionType type;
        private Tree<Item> node;
        private int depth;
        private String summary;
        private String confirmationName;
        private boolean section;
        private boolean deviceIcon;
        private boolean auxiliaryTitle;
        private boolean fullWidthDivider;
        private boolean informationText;
        private boolean broadcastInformation;
        private boolean readinessAction;
        private boolean callToAction;
        private Tree<Item> navigationTarget;

        Item(String name, boolean section) {
            this(name, (String) null, 0);
            this.section = section;
        }

        Item(String name, String description) {
            this(name, description, 0);
        }

        Item(String name, String description, int resource) {
            this.name = name;
            this.description = description;
            this.resource = resource;
        }

        Item(String name, DexCollectionType type) {
            this(name, type, 0);
        }

        Item(String name, DexCollectionType type, int resource) {
            this.name = name;
            this.description = "^" + type.name();
            this.resource = resource;
            this.type = type;
        }

        Item(String name, String summary, DexCollectionType type, int resource) {
            this(name, type, resource);
            this.summary = summary;
        }

        Item withSummary(String subtitle) {
            this.summary = subtitle;
            return this;
        }

        Item withDeviceIcon() {
            deviceIcon = true;
            return this;
        }

        Item withAuxiliaryTitle() {
            auxiliaryTitle = true;
            return this;
        }

        Item withFullWidthDivider() {
            fullWidthDivider = true;
            return this;
        }

        Item withInformationText() {
            informationText = true;
            return this;
        }

        Item withBroadcastInformation() {
            broadcastInformation = true;
            return this;
        }

        public boolean isBroadcastInformation() {
            return broadcastInformation;
        }

        Item withReadinessAction() {
            readinessAction = true;
            return this;
        }

        public boolean isReadinessAction() {
            return readinessAction;
        }

        Item withCallToAction() {
            callToAction = true;
            return this;
        }

        Item withNavigationTarget(Tree<Item> target) {
            navigationTarget = target;
            return this;
        }

        public boolean isInformationText() {
            return informationText;
        }

        public String getInformationBody() {
            return description != null ? description : "";
        }

        public boolean isCallToAction() {
            return callToAction;
        }

        public boolean isNavigationLink() {
            return navigationTarget != null;
        }

        public boolean isNavigationChevronVisible() {
            return isGroup() || callToAction || isNavigationLink();
        }

        public boolean isIconVisible() {
            // All ordinary leaves share the same icon column. Parents and
            // auxiliary navigation rows remain icon-free on purpose.
            return !section && !isGroup() && !auxiliaryTitle && !informationText
                    && !callToAction && !readinessAction;
        }

        public boolean isDeviceIconVisible() {
            return isIconVisible() && deviceIcon && resource != 0;
        }

        public boolean isLightIconPlateVisible() {
            return isDeviceIconVisible() && resource == R.drawable.eversence_360;
        }

        public boolean isWhiteIconPlateVisible() {
            // The MiniMed pump has a dark casing that needs its own white plate.
            return isDeviceIconVisible() && resource == R.drawable.medtronic_770_v2_icon;
        }

        public boolean isPlaceholderIconVisible() {
            return isIconVisible() && !isDeviceIconVisible();
        }

        public boolean isAuxiliaryTitle() {
            return auxiliaryTitle;
        }

        public float getDeviceIconScale() {
            // Keep round sensors optically smaller than low-profile wide bodies.
            if (resource == R.drawable.libre_2_icon ||
                    resource == R.drawable.accucheksmartguide_icon) return 0.84f;
            if (resource == R.drawable.aidex_x_icon) return 0.88f;
            if (resource == R.drawable.ican3_icon) return 0.84f;
            // Leave a visible margin between dark artwork and its 44dp light backing.
            if (resource == R.drawable.eversence_360) return 0.77f;
            return 1f;
        }

        public int getNestedDividerInset() {
            // The line before Libre's auxiliary "Additional connection methods"
            // is full width, matching the separator below it when expanded.
            if (fullWidthDivider) return 0;
            return Math.round(convertDpToPixel(isIconVisible() ? 84 : 22));
        }

        Item withConfirmationName(String sourceName) {
            this.confirmationName = sourceName;
            return this;
        }

        public String getConfirmationName() {
            // Two visual title lines should remain one line in the
            // existing source confirmation dialog.
            return confirmationName != null ? confirmationName : name.replace('\n', ' ');
        }

        public void onClick(View v) {
            if (node == null || section || informationText) return;
            if (readinessAction) {
                showReadinessPlaceholder();
            } else if (navigationTarget != null) {
                if (listModel != null) listModel.open(navigationTarget);
            } else if (isCollectionType()) {
                getTreeDialog(node);
            } else if (listModel != null) {
                listModel.toggle(node);
            }
        }

        public int getRowInset() {
            // The indent applies to the entire row, including its divider.
            return Math.round(convertDpToPixel(depth > 0 ? 28 : 0));
        }

        public boolean isLastVisible() {
            return listModel != null && !listModel.items.isEmpty()
                    && listModel.items.get(listModel.items.size() - 1) == this;
        }

        // Separate device names and connection routes without changing the
        // existing localized labels or the collector selection.
        private String getFirstTitleLine() {
            final int lineBreak = name.indexOf('\n');
            return lineBreak >= 0 ? name.substring(0, lineBreak).trim() : name;
        }

        public String getDisplayTitle() {
            final String title = getFirstTitleLine();
            if (summary != null) return title;
            final int colon = depth > 0 ? title.indexOf(':') : -1;
            return colon > 0 ? title.substring(0, colon).trim() : title;
        }

        public String getDisplayRoute() {
            final int lineBreak = name.indexOf('\n');
            return lineBreak >= 0 ? name.substring(lineBreak + 1).trim() : "";
        }

        public boolean isRouteVisible() {
            return !informationText && !getDisplayRoute().isEmpty();
        }

        public String getDisplaySummary() {
            if (summary != null) return summary;
            final int colon = depth > 0 ? name.indexOf(':') : -1;
            return colon > 0 ? name.substring(colon + 1).trim() : "";
        }

        public boolean isSummaryVisible() {
            return !informationText && !getDisplaySummary().isEmpty();
        }

        public boolean isSection() {
            return section;
        }

        public boolean isFirstSection() {
            return section && node != null && node == root.getChildren().get(0);
        }

        public int getSectionTopPadding() {
            // The first heading needs the same breathing space below the fixed
            // header as subsequent headings have above their sections.
            return Math.round(convertDpToPixel(30));
        }

        public boolean isBeforeSection() {
            if (section || listModel == null) return false;
            final int index = listModel.items.indexOf(this);
            return index >= 0 && index + 1 < listModel.items.size()
                    && listModel.items.get(index + 1).section;
        }

        // Rows are still a single flat RecyclerView, but the drawable of each
        // row joins an expanded group into one visually continuous rounded plate.
        // This is only presentation; it does not add nested RecyclerViews or modify
        // any collection types, settings, or the leaf confirmation flow.
        private boolean isLastNestedItem() {
            if (section || depth == 0 || listModel == null) return false;
            final int index = listModel.items.indexOf(this);
            return index >= 0 && (index + 1 >= listModel.items.size()
                    || listModel.items.get(index + 1).depth < depth);
        }

        public Drawable getPlateBackground() {
            if (section) return null;
            final int resource;
            if (depth > 0) {
                resource = isLastNestedItem()
                        ? R.drawable.source_wizard_plate_bottom
                        : R.drawable.source_wizard_plate_middle;
            } else if (isExpanded()) {
                resource = R.drawable.source_wizard_plate_top;
            } else {
                resource = R.drawable.source_wizard_plate_single;
            }
            return activity.getDrawable(resource);
        }

        public int getPlateTopMargin() {
            if (depth > 0) return 0;
            // Reduce the label-to-card gap by one quarter (16dp -> 12dp),
            // without compressing the gaps between ordinary cards.
            final int index = listModel != null ? listModel.items.indexOf(this) : -1;
            final boolean followsSection = index > 0
                    && listModel.items.get(index - 1).isSection();
            return Math.round(convertDpToPixel(followsSection ? 4 : 8));
        }

        // Keep the 24dp scrollable gap attached to the last visible card.
        public int getPlateBottomMargin() {
            return isLastVisible() ? Math.round(convertDpToPixel(24)) : 0;
        }

        public int getPlateRowStartPadding() {
            return Math.round(convertDpToPixel(depth > 0 ? 22 : 12));
        }

        // One full-weight separator after the expanded parent, followed by
        // hairlines between its children. No changes to selection behavior.
        public boolean isNested() {
            return depth > 0;
        }

        public boolean isFirstNested() {
            if (!isNested() || listModel == null) return false;
            final int index = listModel.items.indexOf(this);
            return index > 0 && listModel.items.get(index - 1).depth < depth;
        }

        public boolean isInnerNested() {
            return isNested() && !isFirstNested();
        }

        public boolean isGroup() {
            return !section && !isCollectionType() && !informationText
                    && !readinessAction && navigationTarget == null;
        }

        public boolean isExpanded() {
            return isGroup() && expandedBranches.contains(node);
        }

        boolean isCollectionType() {
            return type != null;
        }

        public DexCollectionType getCollectionType() {
            return type;
        }
    }

    public class ViewModel {
        public final ObservableList<Item> items = new ObservableArrayList<>();
        public final ItemBinding<Item> itemBinding = ItemBinding.of(BR.item, R.layout.dialog_tree_item);

        ViewModel() {
            rebuild();
        }

        public void skip(View ignored) {
            dismissDialog();
            sw = null; // No new preference: same effect as the old outside tap.
        }

        private void rebuild() {
            items.clear();
            for (Tree<Item> child : root.getChildren()) insertVisible(child, 0, items.size());
        }

        // Deep links open the entire chain of parents before their destination.
        // This also keeps the existing MicroTech -> Companion App link working.
        void open(Tree<Item> target) {
            if (target == null) return;
            final java.util.ArrayList<Tree<Item>> parents = new java.util.ArrayList<>();
            for (Tree<Item> parent = target.getParent();
                    parent != null && parent != root; parent = parent.getParent()) {
                parents.add(0, parent);
            }
            for (Tree<Item> parent : parents) {
                if (indexOf(parent) < 0) return;
                if (!expandedBranches.contains(parent)) toggle(parent);
            }
            if (indexOf(target) < 0) return;
            if (!expandedBranches.contains(target)) toggle(target);
            if (wizardList != null) {
                wizardList.post(() -> {
                    if (listModel == this) {
                        final int position = indexOf(target);
                        if (position >= 0) wizardList.scrollToPosition(position);
                    }
                });
            }
        }

        // Insert or remove only the children of the tapped group (no full-list redraw).
        void toggle(Tree<Item> node) {
            final int position = indexOf(node);
            if (position < 0) return;
            final Item oldLast = items.isEmpty() ? null : items.get(items.size() - 1);
            if (expandedBranches.remove(node)) {
                // Forget nested spoilers too, so reopening a parent starts clean.
                for (Tree<Item> open : new HashSet<>(expandedBranches)) {
                    if (isAncestor(node, open)) expandedBranches.remove(open);
                }
                final int depth = node.data.depth;
                while (position + 1 < items.size() && items.get(position + 1).depth > depth) {
                    items.remove(position + 1);
                }
            } else {
                // Only one active branch at each level, but keep ancestors open.
                // Otherwise tapping Libre > Additional would close Libre itself.
                for (Tree<Item> open : new HashSet<>(expandedBranches)) {
                    if (isAncestor(open, node)) continue;
                    expandedBranches.remove(open);
                    final int openPosition = indexOf(open);
                    if (openPosition < 0) continue;
                    final int openDepth = open.data.depth;
                    while (openPosition + 1 < items.size()
                            && items.get(openPosition + 1).depth > openDepth) {
                        items.remove(openPosition + 1);
                    }
                    // Restore the closed group's ordinary plate and chevron.
                    items.set(openPosition, open.data);
                }

                expandedBranches.add(node);
                // Closing an earlier group may have shifted the tapped position.
                int at = indexOf(node) + 1;
                for (Tree<Item> child : node.getChildren()) {
                    at = insertVisible(child, node.data.depth + 1, at);
                }
            }
            // Also refresh the visibility of the bottom divider whenever the
            // last visible row changes as a result of expanding/collapsing.
            // The clicked row can shift when an earlier group is collapsed.
            items.set(indexOf(node), node.data);
            refreshVisibleRow(oldLast);
            if (!items.isEmpty()) refreshVisibleRow(items.get(items.size() - 1));
        }

        private boolean isAncestor(Tree<Item> ancestor, Tree<Item> descendant) {
            for (Tree<Item> parent = descendant.getParent(); parent != null; parent = parent.getParent()) {
                if (parent == ancestor) return true;
            }
            return false;
        }

        private void refreshVisibleRow(Item item) {
            if (item == null) return;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == item) {
                    items.set(i, item);
                    return;
                }
            }
        }

        private int indexOf(Tree<Item> node) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).node == node) return i;
            }
            return -1;
        }

        private int insertVisible(Tree<Item> node, int depth, int index) {
            final Item item = node.data;
            item.node = node;
            item.depth = depth;
            items.add(index++, item);
            if (item.isGroup() && expandedBranches.contains(node)) {
                for (Tree<Item> child : node.getChildren()) {
                    index = insertVisible(child, depth + 1, index);
                }
            }
            return index;
        }
    }

}
