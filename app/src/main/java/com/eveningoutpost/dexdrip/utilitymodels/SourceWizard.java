package com.eveningoutpost.dexdrip.utilitymodels;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.CheckBox;
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
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

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
    // One persistent window for the source chooser and named Companion pages.
    private Dialog wizardHostDialog;
    private FrameLayout wizardPageHost;
    private FrameLayout wizardFooterHost;
    private TextView wizardBackButton;
    private TextView wizardNextButton;
    private TextView wizardPageBackAction;
    private TextView wizardPageNextAction;
    private View wizardCurrentPage;
    private Runnable wizardPageCleanup;
    private int wizardPageIndex = -1;
    private static final long WIZARD_SLIDE_DURATION_MS = 210L;
    // Reserve the same scrollable distance covered by the stationary feather.
    private static final int WIZARD_BOTTOM_FEATHER_DP = 64;
    // Expansion belongs to this wizard instance, so it survives the confirmation dialog.
    private final HashSet<Tree<Item>> expandedBranches = new HashSet<>();
    private ViewModel listModel;
    private RecyclerView wizardList;
    private Item readinessItem;
    // Navigation is organized by user intent, not by internal collector names.
    // Display-only changes: each leaf still uses an existing DexCollectionType.
    private Tree<Item> root = new Tree<>(new Item(
            gs(R.string.source_wizard_title), gs(R.string.source_wizard_subtitle)));

    {
        // Alphabetical device-first catalog. Different app routes can share a
        // collector: these are named presets, not new acquisition protocols.
        // Preparation is a separate action, not an expandable sensor group.
        root.addChild(new Item(gs(R.string.source_wizard_before_connection), true));
        readinessItem = new Item(gs(R.string.source_wizard_readiness_title), (String) null, 0)
                .withReadinessAction();
        root.addChild(readinessItem);
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
                .withDeviceIcon()
                .withCompanionPackages("com.kakaohealthcare.pasta"));

        // SW-020: device-first Dexcom. No G7 15 Day and no Companion Bluetooth.
        Tree<Item> dex = root.addChild(new Item("Dexcom", (String) null, R.drawable.dex_icon));
        Tree<Item> dexg6 = addDexcomModel(dex, R.string.source_wizard_dexcom_model_g6, R.drawable.source_wizard_dexcom_g6_trial);
        addDexcomRoute(dexg6, R.string.source_wizard_dexcom_model_g6,
                "dexcom.g6.direct",
                R.string.source_wizard_dexcom_route_direct,
                R.string.source_wizard_dexcom_direct_summary,
                DexCollectionType.DexcomG6,
                RouteEvidence.EXISTING_CATALOG, null, null, null);
        addDexcomRoute(dexg6, R.string.source_wizard_dexcom_model_g6,
                "dexcom.g6.camaps",
                R.string.source_wizard_dexcom_route_camaps,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "camaps-fx", "CamAPS FX", "CamAPS FX", "com.camdiab.fx_alert.mmoll", "com.camdiab.fx_alert.mgdl", "com.camdiab.fx_alert.hx.mmoll", "com.camdiab.fx_alert.hx.mgdl", "com.camdiab.fx_alert.mmoll.ca");
        addDexcomRoute(dexg6, R.string.source_wizard_dexcom_model_g6,
                "dexcom.g6.g6app",
                R.string.source_wizard_dexcom_route_g6_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "dexcom-g6-app", "Dexcom G6 App", "Dexcom G6", "com.dexcom.g6", "com.dexcom.g6.region1.mmol", "com.dexcom.g6.region2.mgdl", "com.dexcom.g6.region3.mgdl", "com.dexcom.g6.region4.mmol", "com.dexcom.g6.region5.mmol", "com.dexcom.g6.region6.mgdl", "com.dexcom.g6.region7.mmol", "com.dexcom.g6.region8.mmol", "com.dexcom.g6.region9.mgdl", "com.dexcom.g6.region10.mgdl", "com.dexcom.g6.region11.mmol");
        Tree<Item> dexg7 = addDexcomModel(dex, R.string.source_wizard_dexcom_model_g7, R.drawable.dexcom_g7_icon);
        addDexcomRoute(dexg7, R.string.source_wizard_dexcom_model_g7,
                "dexcom.g7.direct",
                R.string.source_wizard_dexcom_route_direct,
                R.string.source_wizard_dexcom_direct_summary,
                DexCollectionType.DexcomG6,
                RouteEvidence.EXISTING_CATALOG, null, null, null);
        addDexcomRoute(dexg7, R.string.source_wizard_dexcom_model_g7,
                "dexcom.g7.camaps",
                R.string.source_wizard_dexcom_route_camaps,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "camaps-fx", "CamAPS FX", "CamAPS FX", "com.camdiab.fx_alert.mmoll", "com.camdiab.fx_alert.mgdl", "com.camdiab.fx_alert.hx.mmoll", "com.camdiab.fx_alert.hx.mgdl", "com.camdiab.fx_alert.mmoll.ca");
        addDexcomRoute(dexg7, R.string.source_wizard_dexcom_model_g7,
                "dexcom.g7.dblg2",
                R.string.source_wizard_dexcom_route_dblg2,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "dblg2", "DBLG2", "DBLG2", "com.diabeloop.dblg2");
        addDexcomRoute(dexg7, R.string.source_wizard_dexcom_model_g7,
                "dexcom.g7.g7app",
                R.string.source_wizard_dexcom_route_g7_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "dexcom-g7-app", "Dexcom G7 App", "Dexcom G7", "com.dexcom.g7");
        addDexcomRoute(dexg7, R.string.source_wizard_dexcom_model_g7,
                "dexcom.g7.pasta",
                R.string.source_wizard_dexcom_route_pasta_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "pasta-app", "PASTA App", "PASTA", "com.kakaohealthcare.pasta");
        Tree<Item> dexone = addDexcomModel(dex, R.string.source_wizard_dexcom_model_one, R.drawable.source_wizard_dexcom_g6_trial);
        addDexcomRoute(dexone, R.string.source_wizard_dexcom_model_one,
                "dexcom.one.direct",
                R.string.source_wizard_dexcom_route_direct,
                R.string.source_wizard_dexcom_direct_summary,
                DexCollectionType.DexcomG6,
                RouteEvidence.NEEDS_DEVELOPER_REVIEW, null, null, null);
        addDexcomRoute(dexone, R.string.source_wizard_dexcom_model_one,
                "dexcom.one.oneapp",
                R.string.source_wizard_dexcom_route_one_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "dexcom-one-app", "Dexcom ONE App", "Dexcom ONE", "com.dexcom.dexcomone");
        Tree<Item> dexoneplus = addDexcomModel(dex, R.string.source_wizard_dexcom_model_one_plus, R.drawable.dexcom_g7_icon);
        addDexcomRoute(dexoneplus, R.string.source_wizard_dexcom_model_one_plus,
                "dexcom.one_plus.direct",
                R.string.source_wizard_dexcom_route_direct,
                R.string.source_wizard_dexcom_direct_summary,
                DexCollectionType.DexcomG6,
                RouteEvidence.NEEDS_DEVELOPER_REVIEW, null, null, null);
        addDexcomRoute(dexoneplus, R.string.source_wizard_dexcom_model_one_plus,
                "dexcom.one_plus.oneplusapp",
                R.string.source_wizard_dexcom_route_one_plus_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "dexcom-one-plus-app", "Dexcom ONE+ App", "Dexcom ONE+", "com.dexcom.d1plus");
        Tree<Item> dexstelo = addDexcomModel(dex, R.string.source_wizard_dexcom_model_stelo, R.drawable.dexcom_g7_icon);
        addDexcomRoute(dexstelo, R.string.source_wizard_dexcom_model_stelo,
                "dexcom.stelo.direct",
                R.string.source_wizard_dexcom_route_direct,
                R.string.source_wizard_dexcom_direct_summary,
                DexCollectionType.DexcomG6,
                RouteEvidence.NEEDS_DEVELOPER_REVIEW, null, null, null);
        addDexcomRoute(dexstelo, R.string.source_wizard_dexcom_model_stelo,
                "dexcom.stelo.steloapp",
                R.string.source_wizard_dexcom_route_stelo_app,
                R.string.source_wizard_dexcom_app_summary,
                DexCollectionType.UiBased,
                RouteEvidence.EXISTING_CATALOG, "stelo-app", "Stelo App", "Stelo", "com.dexcom.stelo");

        // SW-020: Both Eversense models keep the two established mechanisms.
        // ESEL reads Eversense App notifications (or a patched app) and then
        // broadcasts to xDrip; it is not a direct sensor connection.
        Tree<Item> eversense = root.addChild(new Item("Eversense",
                (String) null, R.drawable.wikimedia_eversense_icon_pbroks13));
        Tree<Item> eversenseE3 = addEversenseModel(eversense, R.string.source_wizard_eversense_model_e3);
        addEversenseRoute(eversenseE3, R.string.source_wizard_eversense_model_e3,
                "eversense.e3.companion", R.string.source_wizard_eversense_route_e3_app,
                R.string.source_wizard_eversense_app_summary,
                DexCollectionType.UiBased, RouteEvidence.EXISTING_CATALOG,
                "eversense-app", "Eversense App", "Eversense",
                "com.senseonics.gen12androidapp", "com.senseonics.androidapp");
        addEversenseRoute(eversenseE3, R.string.source_wizard_eversense_model_e3,
                "eversense.e3.esel", R.string.source_wizard_eversense_route_e3_esel,
                R.string.source_wizard_eversense_esel_summary,
                DexCollectionType.NSEmulator, RouteEvidence.EXISTING_CATALOG,
                null, null, null);

        Tree<Item> eversense365 = addEversenseModel(eversense, R.string.source_wizard_eversense_model_365);
        addEversenseRoute(eversense365, R.string.source_wizard_eversense_model_365,
                "eversense.365.companion", R.string.source_wizard_eversense_route_365_app,
                R.string.source_wizard_eversense_app_summary,
                DexCollectionType.UiBased, RouteEvidence.EXISTING_CATALOG,
                "eversense-365-app", "Eversense 365 App", "Eversense 365",
                "com.senseonics.eversense365.us");
        // Preserve the existing combined E3/365 ESEL path, but its 365
        // compatibility is still awaiting developer confirmation.
        addEversenseRoute(eversense365, R.string.source_wizard_eversense_model_365,
                "eversense.365.esel", R.string.source_wizard_eversense_route_365_esel,
                R.string.source_wizard_eversense_esel_summary,
                DexCollectionType.NSEmulator, RouteEvidence.NEEDS_DEVELOPER_REVIEW,
                null, null, null);

        // SW-020: Compact model-first Libre routes. This preserves existing
        // mappings; presence in the catalog is not proof of compatibility.
        Tree<Item> libre = root.addChild(new Item("FreeStyle Libre",
                (String) null, R.drawable.libre_icon_image));
        Tree<Item> libre1 = addLibreModel(libre, R.string.source_wizard_libre_model_1);
        addLibreRoute(libre1, "1", R.string.source_wizard_libre_model_1,
                "bridge", R.string.source_wizard_libre_route_bridge,
                R.string.source_wizard_libre_one_bridge_summary, DexCollectionType.LimiTTer,
                null, null, null);
        addLibreRoute(libre1, "1", R.string.source_wizard_libre_model_1,
                "alarm", R.string.source_wizard_libre_route_alarm,
                R.string.source_wizard_libre_one_alarm_summary, DexCollectionType.LibreAlarm,
                null, null, null);
        addLibreRoute(libre1, "1", R.string.source_wizard_libre_model_1,
                "network", R.string.source_wizard_libre_route_network,
                R.string.source_wizard_libre_network_summary, DexCollectionType.LibreWifi,
                null, null, null);
        addLibreRoute(libre1, "1", R.string.source_wizard_libre_model_1,
                "combined", R.string.source_wizard_libre_route_combined,
                R.string.source_wizard_libre_combined_summary, DexCollectionType.LimiTTerWifi,
                null, null, null);

        Tree<Item> libre2 = addLibreModel(libre, R.string.source_wizard_libre_model_2);
        addLibreRoute(libre2, "2", R.string.source_wizard_libre_model_2,
                "direct", R.string.source_wizard_libre_route_direct,
                R.string.source_wizard_libre_two_direct_summary, DexCollectionType.LimiTTer,
                null, null, null);
        addLibreRoute(libre2, "2", R.string.source_wizard_libre_model_2,
                "network", R.string.source_wizard_libre_route_network,
                R.string.source_wizard_libre_network_summary, DexCollectionType.LibreWifi,
                null, null, null);
        addLibreRoute(libre2, "2", R.string.source_wizard_libre_model_2,
                "combined", R.string.source_wizard_libre_route_combined,
                R.string.source_wizard_libre_combined_summary, DexCollectionType.LimiTTerWifi,
                null, null, null);
        addLibreRoute(libre2, "2", R.string.source_wizard_libre_model_2,
                "juggluco-eversense", R.string.source_wizard_libre_route_juggluco_eversense,
                R.string.source_wizard_libre_juggluco_summary, DexCollectionType.NSEmulator,
                "juggluco", "Juggluco", "Juggluco");
        addLibreRoute(libre2, "2", R.string.source_wizard_libre_model_2,
                "juggluco-patched", R.string.source_wizard_libre_route_juggluco_patched,
                R.string.source_wizard_libre_juggluco_legacy_summary, DexCollectionType.LibreReceiver,
                "juggluco", "Juggluco", "Juggluco");

        Tree<Item> libre2plus = addLibreModel(libre, R.string.source_wizard_libre_model_2_plus);
        addLibreRoute(libre2plus, "2plus", R.string.source_wizard_libre_model_2_plus,
                "juggluco-eversense", R.string.source_wizard_libre_route_juggluco_eversense,
                R.string.source_wizard_libre_juggluco_summary, DexCollectionType.NSEmulator,
                "juggluco", "Juggluco", "Juggluco");
        addLibreRoute(libre2plus, "2plus", R.string.source_wizard_libre_model_2_plus,
                "juggluco-patched", R.string.source_wizard_libre_route_juggluco_patched,
                R.string.source_wizard_libre_juggluco_legacy_summary, DexCollectionType.LibreReceiver,
                "juggluco", "Juggluco", "Juggluco");

        Tree<Item> libre3 = addLibreModel(libre, R.string.source_wizard_libre_model_3);
        addLibreRoute(libre3, "3", R.string.source_wizard_libre_model_3,
                "camaps", R.string.source_wizard_libre_route_camaps,
                R.string.source_wizard_dexcom_app_summary, DexCollectionType.UiBased,
                "camaps-fx", "CamAPS FX", "CamAPS FX",
                "com.camdiab.fx_alert.mmoll", "com.camdiab.fx_alert.mgdl", "com.camdiab.fx_alert.hx.mmoll", "com.camdiab.fx_alert.hx.mgdl", "com.camdiab.fx_alert.mmoll.ca");
        addLibreRoute(libre3, "3", R.string.source_wizard_libre_model_3,
                "juggluco-eversense", R.string.source_wizard_libre_route_juggluco_eversense,
                R.string.source_wizard_libre_juggluco_summary, DexCollectionType.NSEmulator,
                "juggluco", "Juggluco", "Juggluco");
        addLibreRoute(libre3, "3", R.string.source_wizard_libre_model_3,
                "juggluco-patched", R.string.source_wizard_libre_route_juggluco_patched,
                R.string.source_wizard_libre_juggluco_legacy_summary, DexCollectionType.LibreReceiver,
                "juggluco", "Juggluco", "Juggluco");

        Tree<Item> libre3plus = addLibreModel(libre, R.string.source_wizard_libre_model_3_plus);
        addLibreRoute(libre3plus, "3plus", R.string.source_wizard_libre_model_3_plus,
                "camaps", R.string.source_wizard_libre_route_camaps,
                R.string.source_wizard_dexcom_app_summary, DexCollectionType.UiBased,
                "camaps-fx", "CamAPS FX", "CamAPS FX",
                "com.camdiab.fx_alert.mmoll", "com.camdiab.fx_alert.mgdl", "com.camdiab.fx_alert.hx.mmoll", "com.camdiab.fx_alert.hx.mgdl", "com.camdiab.fx_alert.mmoll.ca");
        addLibreRoute(libre3plus, "3plus", R.string.source_wizard_libre_model_3_plus,
                "juggluco-eversense", R.string.source_wizard_libre_route_juggluco_eversense,
                R.string.source_wizard_libre_juggluco_summary, DexCollectionType.NSEmulator,
                "juggluco", "Juggluco", "Juggluco");
        addLibreRoute(libre3plus, "3plus", R.string.source_wizard_libre_model_3_plus,
                "juggluco-patched", R.string.source_wizard_libre_route_juggluco_patched,
                R.string.source_wizard_libre_juggluco_legacy_summary, DexCollectionType.LibreReceiver,
                "juggluco", "Juggluco", "Juggluco");

        // iCAN and rebranded GlucoMen iCan share notification collectors.
        Tree<Item> ican = root.addChild(new Item("iCAN",
                gs(R.string.source_wizard_ican_examples), R.drawable.ican3_icon)
                .withSummary(gs(R.string.source_wizard_ican_examples)));
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i3_cgm),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon()
                .withCompanionPackages("com.sinocare.cgm.ce"));
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i3_health),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon()
                .withCompanionPackages("com.sinocare.ican.health.ce", "com.sinocare.ican.health.ru"));
        ican.addChild(new Item(gs(R.string.source_wizard_ican_i6_cgm),
                gs(R.string.source_wizard_pending_confirmation),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon()
                .withCompanionPackages("com.sinocare.cgm.ce"));
        ican.addChild(new Item(gs(R.string.source_wizard_glucomen_o3),
                gs(R.string.source_wizard_pending_confirmation),
                DexCollectionType.UiBased, R.drawable.ican3_icon).withDeviceIcon()
                .withCompanionPackages("com.sinocare.cgm.ce"));

        // SW-019/020: Medtronic device-first, one leaf per device/app route.
        // MiniMed 770G/780G are insulin pumps, NOT CGM sensor models.
        Tree<Item> medtronic = root.addChild(new Item("Medtronic",
                gs(R.string.source_wizard_medtronic_examples), 0)
                .withSummary(gs(R.string.source_wizard_medtronic_examples)));

        addMedtronicCompanion(medtronic,
                R.string.source_wizard_medtronic_model_guardian3,
                R.string.source_wizard_medtronic_route_guardian_connect,
                R.drawable.guardian_3_4_icon, "medtronic.guardian3.connect",
                "guardian-connect", "Guardian Connect", "Guardian Connect", false,
                "com.medtronic.diabetes.guardianconnect",
                "com.medtronic.diabetes.guardianconnect.us");
        addMedtronicCompanion(medtronic,
                R.string.source_wizard_medtronic_model_guardian4,
                R.string.source_wizard_medtronic_route_guardian_app,
                R.drawable.guardian_3_4_icon, "medtronic.guardian4.guardian-app",
                "guardian-app", "Guardian App", "Guardian", false,
                "com.medtronic.diabetes.guardian");
        addMedtronicCompanion(medtronic,
                R.string.source_wizard_medtronic_model_minimed,
                R.string.source_wizard_medtronic_route_minimed_mobile,
                R.drawable.medtronic_770_v2_icon, "medtronic.minimed.mobile",
                "minimed-mobile", "MiniMed Mobile", "MiniMed Mobile", true,
                "com.medtronic.diabetes.minimedmobile.eu",
                "com.medtronic.diabetes.minimedmobile.us");
        addMedtronicCompanion(medtronic,
                R.string.source_wizard_medtronic_model_simplera,
                R.string.source_wizard_medtronic_route_simplera_app,
                R.drawable.medtronic_simplera_icon, "medtronic.simplera.app",
                "simplera-app", "Simplera App", "Simplera", false,
                "com.medtronic.diabetes.simplera.eu");

        // CareLink is an existing shared Follower screen, not another sensor.
        Item carelinkLink = new Item(gs(R.string.source_wizard_medtronic_carelink),
                (String) null, 0).withAuxiliaryTitle();
        medtronic.addChild(carelinkLink);

        // SW-020: multi-route devices get model stacks first (alphabetical);
        // single-route devices follow as full-width leaves (alphabetical).
        // Each visible Companion route checks only its own APK package.
        Tree<Item> microtech = root.addChild(new Item(
                gs(R.string.source_wizard_microtech_group),
                gs(R.string.source_wizard_microtech_examples),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_microtech_examples)));

        // Multi-route model: LinX CGM / AiDEX X.
        Tree<Item> mtLinx = addMicrotechModel(microtech,
                R.string.source_wizard_microtech_model_linx,
                R.drawable.aidex_x_icon);
        addMicrotechRoute(mtLinx, R.string.source_wizard_microtech_model_linx,
                "microtech.linx.linx",
                R.string.source_wizard_microtech_route_linx,
                R.string.source_wizard_microtech_linx_summary,
                DexCollectionType.UiBased, RouteEvidence.EXISTING_CATALOG,
                "linx-cgm", "LinX CGM", "LinX CGM", "com.microtech.aidexx.mgdl");
        addMicrotechRoute(mtLinx, R.string.source_wizard_microtech_model_linx,
                "microtech.linx.vitatok",
                R.string.source_wizard_microtech_route_vitatok,
                R.string.source_wizard_microtech_linx_summary,
                DexCollectionType.UiBased, RouteEvidence.NEEDS_DEVELOPER_REVIEW,
                "vitatok", "VitaTok", "VitaTok", "com.suswel.ai");
        addMicrotechRoute(mtLinx, R.string.source_wizard_microtech_model_linx,
                "microtech.linx.vixxa",
                R.string.source_wizard_microtech_route_vixxa,
                R.string.source_wizard_microtech_linx_summary,
                DexCollectionType.UiBased, RouteEvidence.EXISTING_CATALOG,
                "vixxa-1", "Vixxa 1", "Vixxa 1", "com.microtech.aidexx.grx1.mmoll");

        // Single-route devices: DiaExpert, GlucoRx, LinX Vista (alphabetical).
        addMicrotechSingleRoute(microtech, R.string.source_wizard_microtech_model_diaexpert,
                R.string.source_wizard_microtech_route_diaexpert,
                R.string.source_wizard_microtech_linx_summary, R.drawable.aidex_x_icon,
                "microtech.diaexpert.diaexpert",
                DexCollectionType.UiBased, RouteEvidence.NEEDS_DEVELOPER_REVIEW,
                "diaexpert-cgm-2", "DiaExpert CGM 2", "DiaExpert CGM 2",
                "com.microtech.aidexx.diaexport.mmoll");
        addMicrotechSingleRoute(microtech, R.string.source_wizard_microtech_model_glucorx,
                R.string.source_wizard_microtech_route_glucorx,
                R.string.source_wizard_microtech_glucorx_summary, R.drawable.glucorx_icon,
                "microtech.glucorx.glucorx",
                DexCollectionType.AidexReceiver, RouteEvidence.EXISTING_CATALOG,
                null, null, null);
        addMicrotechSingleRoute(microtech, R.string.source_wizard_microtech_model_vista,
                R.string.source_wizard_microtech_route_vista,
                R.string.source_wizard_microtech_linx_summary, R.drawable.aidex_x_icon,
                "microtech.vista.vista",
                DexCollectionType.UiBased, RouteEvidence.NEEDS_DEVELOPER_REVIEW,
                "linx-vista", "LinX Vista", "LinX Vista",
                "com.microtech.aidexx.linxneo.mmoll");

        // Generic fall-back remains a link to the existing Companion App page.
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
                DexCollectionType.UiBased, R.drawable.sibionics_gs1_icon).withDeviceIcon()
                .withCompanionPackages("com.sisensing.eco"));

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
                DexCollectionType.UiBased, R.drawable.ottai_icon).withDeviceIcon()
                // Detect both international and China-only installations.
                // This does not confirm that either app publishes valid readings.
                .withCompanionPackages("com.ottai.seas", "com.ottai.tag"));

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

    // Use an explicit adapter so compact rows keep their own minimum height
    // without depending on Android's ambiguous minHeight setter mapping.
    @BindingAdapter("sourceWizardRowMinHeight")
    public static void bindSourceWizardRowMinHeight(View view, int minHeightPx) {
        view.setMinimumHeight(minHeightPx);
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

    private Dialog getWizardHostDialog() {
        if (wizardHostDialog != null && wizardHostDialog.isShowing()) return wizardHostDialog;
        // A dismissed host must not be recycled: its delayed onDismiss callback
        // can otherwise erase a newly opened page during permission recovery.
        if (wizardHostDialog != null) {
            if (wizardPageCleanup != null) wizardPageCleanup.run();
            wizardPageCleanup = null;
            wizardHostDialog = null;
            wizardCurrentPage = null;
            wizardPageIndex = -1;
        }
        final Dialog host = new Dialog(activity, android.R.style.Theme_Holo_Dialog);
        host.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout shell = new LinearLayout(host.getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        final Drawable background = activity.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
        }
        shell.setBackground(background);
        final FrameLayout pageViewport = new FrameLayout(host.getContext());
        wizardPageHost = new FrameLayout(host.getContext());
        wizardPageHost.setClipChildren(true);
        wizardPageHost.setClipToPadding(true);
        pageViewport.addView(wizardPageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // One noninteractive fixed overlay for EVERY wizard page, including the
        // catalog. The pages slide and scroll behind this bottom fade.
        final View bottomFeather = new View(host.getContext());
        bottomFeather.setBackgroundResource(R.drawable.source_wizard_bottom_feather);
        bottomFeather.setClickable(false);
        bottomFeather.setFocusable(false);
        final FrameLayout.LayoutParams featherParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(convertDpToPixel(WIZARD_BOTTOM_FEATHER_DP)),
                Gravity.BOTTOM);
        pageViewport.addView(bottomFeather, featherParams);
        shell.addView(pageViewport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wizardFooterHost = new FrameLayout(host.getContext());
        shell.addView(wizardFooterHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(convertDpToPixel(56))));
        // Reuse the existing button styling, but only inflate the visible footer
        // once. Each wizard page retains its own detached click-targets.
        final LinearLayout template = (LinearLayout) LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_source_wizard_instructions, null);
        final View controls = template.getChildAt(template.getChildCount() - 1);
        template.removeView(controls);
        wizardBackButton = controls.findViewById(R.id.instructionsBack);
        wizardNextButton = controls.findViewById(R.id.instructionsProceed);
        wizardBackButton.setOnClickListener(v -> {
            if (wizardPageBackAction != null && wizardPageBackAction.isEnabled()) {
                wizardPageBackAction.performClick();
            }
        });
        wizardNextButton.setOnClickListener(v -> {
            if (wizardPageNextAction != null && wizardPageNextAction.isEnabled()) {
                wizardPageNextAction.performClick();
            }
        });
        wizardFooterHost.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.setContentView(shell);
        host.setCancelable(false);
        host.setOnDismissListener(ignored -> {
            if (wizardHostDialog != host) return;
            if (wizardPageCleanup != null) wizardPageCleanup.run();
            wizardPageCleanup = null;
            if (wizardPageHost != null) wizardPageHost.removeAllViews();
            if (wizardFooterHost != null) wizardFooterHost.removeAllViews();
            wizardBackButton = null;
            wizardNextButton = null;
            wizardPageBackAction = null;
            wizardPageNextAction = null;
            wizardCurrentPage = null;
            wizardPageIndex = -1;
            wizardHostDialog = null;
        });
        wizardHostDialog = host;
        return host;
    }

    private void setWizardPageCleanup(final Runnable cleanup) {
        wizardPageCleanup = cleanup;
    }

    private boolean isCurrentWizardPage(final View page) {
        return wizardCurrentPage == page && wizardHostDialog != null
                && wizardHostDialog.isShowing();
    }

    private void syncWizardFooter() {
        if (wizardBackButton == null || wizardNextButton == null
                || wizardPageNextAction == null) return;
        final boolean hasBack = wizardPageBackAction != null;
        wizardBackButton.setVisibility(hasBack ? View.VISIBLE : View.GONE);
        if (hasBack) {
            wizardBackButton.setText(wizardPageBackAction.getText());
            wizardBackButton.setEnabled(wizardPageBackAction.isEnabled());
            wizardBackButton.setAlpha(wizardPageBackAction.getAlpha());
        }
        wizardNextButton.setText(wizardPageNextAction.getText());
        wizardNextButton.setEnabled(wizardPageNextAction.isEnabled());
        wizardNextButton.setAlpha(wizardPageNextAction.getAlpha());
        final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) wizardNextButton.getLayoutParams();
        params.width = hasBack ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        params.weight = hasBack ? 0f : 1f;
        wizardNextButton.setLayoutParams(params);
        wizardNextButton.setGravity((hasBack ? Gravity.CENTER : Gravity.END)
                | Gravity.CENTER_VERTICAL);
        final int normal = Math.round(convertDpToPixel(15));
        wizardNextButton.setPadding(
                hasBack ? normal : Math.round(convertDpToPixel(20)),
                0, hasBack ? normal : Math.round(convertDpToPixel(22)), 0);
    }

    // Keep the readiness popup the exact same height as the main wizard.
    // One geometry calculation avoids short dialogs exposing the dimmed Skip footer.
    private int wizardWindowHeight() {
        final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        return Math.min(Math.round(screenHeight * 0.82f),
                screenHeight - Math.round(convertDpToPixel(80)));
    }

    // The footer itself is never animated. Both header and body travel through
    // the clipping page viewport, so the Dialog never scales or changes height.
    private void presentWizardPage(final Dialog host, final View view, final int pageIndex) {
        if (host != wizardHostDialog || !(view instanceof LinearLayout)) {
            throw new IllegalStateException("Not a Source Wizard page");
        }
        final LinearLayout page = (LinearLayout) view;
        final View footer = page.getChildAt(page.getChildCount() - 1);
        page.removeView(footer);
        // Page footers are action sources only. The physical controls stay
        // mounted in wizardFooterHost throughout the full animated route.
        if (footer instanceof LinearLayout && ((LinearLayout) footer).getChildCount() >= 2) {
            wizardPageBackAction = (TextView) ((LinearLayout) footer).getChildAt(0);
            wizardPageNextAction = (TextView) ((LinearLayout) footer).getChildAt(1);
        } else if (footer instanceof FrameLayout && ((FrameLayout) footer).getChildCount() == 1) {
            wizardPageBackAction = null;
            wizardPageNextAction = (TextView) ((FrameLayout) footer).getChildAt(0);
        } else {
            throw new IllegalStateException("Unsupported Source Wizard footer");
        }
        syncWizardFooter();
        for (int i = 0; i < page.getChildCount(); i++) {
            final View child = page.getChildAt(i);
            if (child instanceof ScrollView || child instanceof RecyclerView) {
                final LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) child.getLayoutParams();
                params.height = 0;
                params.weight = 1f;
                child.setLayoutParams(params);
                // Allow the last row to scroll ABOVE the fixed feather and remain
                // entirely readable instead of disappearing behind the footer.
                child.setPadding(child.getPaddingLeft(), child.getPaddingTop(),
                        child.getPaddingRight(),
                        child.getPaddingBottom() + Math.round(convertDpToPixel(WIZARD_BOTTOM_FEATHER_DP)));
                ((ViewGroup) child).setClipToPadding(false);
                break;
            }
        }
        page.setBackgroundColor(Color.TRANSPARENT);
        if (wizardPageCleanup != null) {
            wizardPageCleanup.run();
            wizardPageCleanup = null;
        }
        final View outgoing = wizardCurrentPage;
        final boolean backwards = pageIndex < wizardPageIndex;
        wizardPageIndex = pageIndex;
        wizardCurrentPage = page;
        wizardPageHost.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog = host;
        if (!host.isShowing()) {
            final Window window = host.getWindow();
            if (window != null) window.setWindowAnimations(0);
            host.show();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(screenWidth - Math.round(convertDpToPixel(12)),
                        wizardWindowHeight());
            }
        }
        if (outgoing == null) return;
        // Consume footer touches for 210ms, without mutating button enabled state.
        final View touchBlocker = new View(activity);
        touchBlocker.setClickable(true);
        wizardFooterHost.addView(touchBlocker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final int width = Math.max(1, wizardPageHost.getWidth() > 0
                ? wizardPageHost.getWidth() : activity.getResources().getDisplayMetrics().widthPixels);
        final int direction = backwards ? -1 : 1;
        page.setTranslationX(direction * width);
        outgoing.animate().cancel();
        page.animate().cancel();
        outgoing.animate().translationX(-direction * width)
                .setDuration(WIZARD_SLIDE_DURATION_MS).start();
        page.animate().translationX(0).setDuration(WIZARD_SLIDE_DURATION_MS)
                .withEndAction(() -> {
                    if (outgoing.getParent() == wizardPageHost) wizardPageHost.removeView(outgoing);
                    if (touchBlocker.getParent() == wizardFooterHost) {
                        wizardFooterHost.removeView(touchBlocker);
                    }
                }).start();
    }

    // A standalone Holo dialog lets the xDrip gradient cover the entire visible
    // chooser, rather than hiding it below opaque AlertDialog title/button panels.
    // Leaf confirmation and its DexCollectionHelper path remain unchanged.
    public void getTreeDialog(Tree<Item> branch) {
        if (branch == null || !branch.data.isCollectionType()) {
            final Dialog picker = getWizardHostDialog();
            final DialogTreeSelectorBinding binding = DialogTreeSelectorBinding.inflate(
                    LayoutInflater.from(picker.getContext()));
            listModel = new ViewModel();
            binding.setViewModel(listModel);
            refreshReadinessSummary();
            binding.sourceWizardList.setItemAnimator(null);
            wizardList = binding.sourceWizardList;
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            binding.sourceWizardList.getLayoutParams().height = Math.round(screenHeight * 0.62f);

            presentWizardPage(picker, binding.getRoot(), 0);
        } else {
            final Item item = branch.data;
            // Generic Companion and all other source types retain the legacy path.
            if (item.isNamedCompanionRoute()) {
                showNamedCompanionConfirmation(item);
                return;
            }
            dismissDialog(); // Legacy source paths keep their existing confirmation.
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


    // SW-005/006 only. The later SW-014 checklist owns final source selection.
    private void showNamedCompanionConfirmation(final Item item) {
        // Model-based Libre routes do not parse translated labels for identity.
        // Existing non-Libre cards keep their former parser during SW-019 audit.
        final String sensor;
        final String appName;
        final String appSentenceName;
        if (item.routeMetadata != null && item.routeMetadata.appId != null) {
            sensor = item.routeMetadata.model;
            appName = item.routeMetadata.appDisplayName;
            appSentenceName = item.routeMetadata.appSentenceName;
        } else {
            final int boundary = item.name.indexOf(10);
            if (boundary < 1) {
                getTreeDialog(null);
                return;
            }
            sensor = item.name.substring(0, boundary).trim();
            String app = item.name.substring(boundary + 1).trim();
            if (app.startsWith("→")) app = app.substring(1).trim();
            if (app.isEmpty()) {
                getTreeDialog(null);
                return;
            }
            appName = app;
            appSentenceName = app;
        }
        final Dialog panel = getWizardHostDialog();
        final View root = LayoutInflater.from(panel.getContext())
                .inflate(R.layout.dialog_source_wizard_companion, null);
        ((TextView) root.findViewById(R.id.companionIntro))
                .setText(activity.getString(item.pumpDevice
                        ? R.string.source_wizard_companion_pump_intro
                        : R.string.source_wizard_companion_intro, sensor, appSentenceName));
        ((TextView) root.findViewById(R.id.companionExplanation))
                .setText(activity.getString(item.pumpDevice
                        ? R.string.source_wizard_companion_pump_setup_explanation
                        : R.string.source_wizard_companion_setup_explanation, appSentenceName));
        ((TextView) root.findViewById(R.id.companionAppName)).setText(appName);
        final ImageView icon = root.findViewById(R.id.companionStatusIcon);
        final TextView status = root.findViewById(R.id.companionStatusText);
        final TextView guidance = root.findViewById(R.id.companionGuidance);
        final CheckBox bypass = root.findViewById(R.id.companionBypassInstall);
        final TextView continueButton = root.findViewById(R.id.companionProceed);
        final CompanionInstallState[] state = {CompanionInstallState.UNKNOWN};
        final Runnable refresh = () -> {
            if (!isCurrentWizardPage(root)) return;
            state[0] = inspectCompanionPackages(item.companionPackages);
            final int picture, message;
            switch (state[0]) {
                case INSTALLED:
                    picture = R.drawable.source_wizard_readiness_ok;
                    message = R.string.source_wizard_companion_installed;
                    break;
                case MISSING:
                    picture = R.drawable.source_wizard_readiness_missing;
                    message = R.string.source_wizard_companion_missing;
                    break;
                default:
                    // Installation UNKNOWN is an attention state, not a failure.
                    // Other unknown readiness states keep their neutral gray icon.
                    picture = R.drawable.source_wizard_companion_install_unknown;
                    message = R.string.source_wizard_companion_unknown;
            }
            icon.setImageResource(picture);
            icon.setContentDescription(appName + ": " + activity.getString(message));
            status.setText(message);
            // This is ONE mutually exclusive prompt in the same position:
            // an installed app asks to continue; a missing app asks to install.
            final boolean missing = state[0] == CompanionInstallState.MISSING;
            if (missing) {
                guidance.setText(activity.getString(
                        R.string.source_wizard_companion_install_and_return, appSentenceName));
            } else if (state[0] == CompanionInstallState.UNKNOWN) {
                guidance.setText(R.string.source_wizard_companion_unknown_explanation);
            } else {
                guidance.setText(R.string.source_wizard_companion_question);
            }
            bypass.setVisibility(missing ? View.VISIBLE : View.GONE);
            if (!missing) bypass.setChecked(false);
            updateCompanionContinue(continueButton, state[0], bypass.isChecked());
            syncWizardFooter();
        };
        bypass.setOnCheckedChangeListener((view, checked) -> {
            updateCompanionContinue(continueButton, state[0], checked);
            syncWizardFooter();
        });
        root.findViewById(R.id.companionBack).setOnClickListener(v -> getTreeDialog(null));
        continueButton.setOnClickListener(v -> {
            if (state[0] == CompanionInstallState.MISSING && !bypass.isChecked()) return;
            // SW-014 preflight must not switch the collector before final confirmation.
            showNamedCompanionPermissions(item, appName);
        });
        presentWizardPage(panel, root, 1);
        final boolean[] lostFocus = {false};
        final ViewTreeObserver.OnWindowFocusChangeListener listener = focus -> {
            if (!focus) lostFocus[0] = true;
            else if (lostFocus[0]) {
                lostFocus[0] = false;
                refresh.run();
            }
        };
        final ViewTreeObserver observer = root.getViewTreeObserver();
        observer.addOnWindowFocusChangeListener(listener);
        setWizardPageCleanup(() -> {
            if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(listener);
        });
        refresh.run();
    }

    // SW-014: route-specific preflight, using the same checks/rows as SW-008.
    // SW-010/011 will own final source activation; this dialog never starts a collector.
    private void showNamedCompanionPermissions(final Item item, final String appName) {
        // Unknown means inspection itself failed. No normal UI or source activation.
        if (SourceWizardReadiness.inspect(activity,
                SourceWizardReadiness.Requirement.NOTIFICATION_LISTENER).state
                == SourceWizardReadiness.State.UNKNOWN) {
            // Fail closed: the shared route must not remain open behind the error.
            dismissDialog();
            showNotificationPermissionCheckError();
            return;
        }
        final Dialog permissions = getWizardHostDialog();
        final View content = LayoutInflater.from(permissions.getContext())
                .inflate(R.layout.dialog_source_wizard_permissions, null);
        final ImageView icon = content.findViewById(R.id.permissionsListenerIcon);
        final TextView status = content.findViewById(R.id.permissionsListenerStatus);
        final TextView guidance = content.findViewById(R.id.permissionsListenerGuidance);
        final TextView action = content.findViewById(R.id.permissionsListenerAction);
        final TextView proceed = content.findViewById(R.id.permissionsProceed);
        final LinearLayout stability = content.findViewById(R.id.permissionsStabilityRows);

        final List<Runnable> startChecks = new ArrayList<>();
        final List<Runnable> cancelChecks = new ArrayList<>();
        startChecks.add(addLiveReadinessRow(permissions, stability,
                R.string.source_wizard_readiness_battery,
                SourceWizardReadiness.Requirement.BATTERY_OPTIMIZATION,
                false, true, cancelChecks));
        startChecks.add(addLiveReadinessRow(permissions, stability,
                R.string.source_wizard_readiness_autostart,
                SourceWizardReadiness.Requirement.AUTOSTART,
                false, true, cancelChecks));
        startChecks.add(addLiveReadinessRow(permissions, stability,
                R.string.source_wizard_readiness_pin,
                SourceWizardReadiness.Requirement.APP_LOCK,
                true, false, cancelChecks));

        final Runnable checkListener = () -> {
            if (!isCurrentWizardPage(content)) return;
            final SourceWizardReadiness.Result result = SourceWizardReadiness.inspect(
                    activity, SourceWizardReadiness.Requirement.NOTIFICATION_LISTENER);
            if (result.state == SourceWizardReadiness.State.UNKNOWN) {
                // Fail closed and replace the checklist with a standalone error.
                proceed.setEnabled(false);
                permissions.dismiss();
                showNotificationPermissionCheckError();
                return;
            }
            final int drawable, description;
            switch (result.state) {
                case READY:
                    drawable = R.drawable.source_wizard_readiness_ok;
                    description = R.string.source_wizard_readiness_state_ready;
                    status.setText(R.string.source_wizard_permissions_granted);
                    status.setVisibility(View.VISIBLE);
                    action.setVisibility(View.GONE);
                    guidance.setVisibility(View.GONE);
                    break;
                case MISSING:
                    drawable = R.drawable.source_wizard_readiness_missing;
                    description = R.string.source_wizard_readiness_state_missing;
                    status.setVisibility(View.GONE);
                    action.setText(R.string.source_wizard_readiness_fix);
                    action.setVisibility(View.VISIBLE);
                    guidance.setText(activity.getString(
                            R.string.source_wizard_permissions_missing_detail, appName));
                    guidance.setVisibility(View.VISIBLE);
                    break;
                default:
                    // Normal rows are strictly READY or MISSING.
                    return;
            }
            icon.setImageResource(drawable);
            icon.setContentDescription(activity.getString(
                    R.string.source_wizard_readiness_notification_listener) + ": "
                    + activity.getString(description));
            final boolean allowed = result.state == SourceWizardReadiness.State.READY;
            proceed.setEnabled(allowed);
            proceed.setAlpha(allowed ? 1f : 0.65f);
            syncWizardFooter();
        };
        action.setOnClickListener(v ->
                com.eveningoutpost.dexdrip.services.UiBasedCollector
                        .openNotificationListenerSettings(activity));
        content.findViewById(R.id.permissionsBack).setOnClickListener(v -> {
            showNamedCompanionConfirmation(item);
        });
        proceed.setOnClickListener(v -> {
            if (!proceed.isEnabled()) return;
            // Check current system state again, never trust only the rendered state.
            checkListener.run();
            if (!permissions.isShowing() || !proceed.isEnabled()) return;
            // SW-010: still only navigating; changing the collector belongs to Done.
            showNamedCompanionInstructions(item, appName);
        });

        presentWizardPage(permissions, content, 2);

        final boolean[] lostFocus = {false};
        final ViewTreeObserver.OnWindowFocusChangeListener focusListener = focused -> {
            if (!focused) lostFocus[0] = true;
            else if (lostFocus[0]) {
                lostFocus[0] = false;
                checkListener.run();
                for (Runnable start : startChecks) start.run();
            }
        };
        final ViewTreeObserver observer = content.getViewTreeObserver();
        observer.addOnWindowFocusChangeListener(focusListener);
        setWizardPageCleanup(() -> {
            for (Runnable cancel : cancelChecks) cancel.run();
            if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(focusListener);
        });
        checkListener.run();
        for (Runnable start : startChecks) start.run();
    }

    // SW-010: optional, human-readable Companion app instructions.
    // This screen never switches the current source or touches the collector.
    private void showNamedCompanionInstructions(final Item item, final String appName) {
        final Dialog instructions = getWizardHostDialog();
        final View content = LayoutInflater.from(instructions.getContext())
                .inflate(R.layout.dialog_source_wizard_instructions, null);
        ((TextView) content.findViewById(R.id.instructionsHeading)).setText(
                activity.getString(R.string.source_wizard_instructions_title, appName));
        final LinearLayout steps = content.findViewById(R.id.instructionsSteps);
        final ScrollView body = content.findViewById(R.id.instructionsBodyScroll);

        // Reuse SW-006 package inspection on entry and after returning to xDrip+.
        // Installation is informative, not a gate for this UiBased route.
        final Runnable renderSteps = () -> {
            if (!isCurrentWizardPage(content)) return;
            final CompanionInstallState state = inspectCompanionPackages(item.companionPackages);
            steps.removeAllViews();
            int number = 1;
            if (state == CompanionInstallState.MISSING) {
                addCompanionInstructionStep(steps, number++,
                        activity.getString(R.string.source_wizard_instructions_install_title),
                        activity.getString(R.string.source_wizard_instructions_install_detail, appName));
            } else if (state == CompanionInstallState.UNKNOWN) {
                addCompanionInstructionStep(steps, number++,
                        activity.getString(R.string.source_wizard_instructions_unknown_title),
                        activity.getString(R.string.source_wizard_instructions_unknown_detail, appName));
            }
            addCompanionInstructionStep(steps, number++,
                    activity.getString(R.string.source_wizard_instructions_open_title),
                    activity.getString(item.pumpDevice
                            ? R.string.source_wizard_instructions_open_pump_detail
                            : R.string.source_wizard_instructions_open_detail, appName));
            addCompanionUnitsStep(steps, number++, appName);
            addCompanionInstructionStep(steps, number++,
                    activity.getString(R.string.source_wizard_instructions_notify_title),
                    activity.getString(R.string.source_wizard_instructions_notify_detail, appName));
            addCompanionInstructionStep(steps, number,
                    activity.getString(R.string.source_wizard_instructions_check_title),
                    activity.getString(R.string.source_wizard_instructions_check_detail, appName));
        };

        content.findViewById(R.id.instructionsBack).setOnClickListener(v -> {
            showNamedCompanionPermissions(item, appName);
        });
        final TextView continueButton = content.findViewById(R.id.instructionsProceed);
        final boolean[] checking = {false};
        continueButton.setOnClickListener(v -> {
            if (!instructions.isShowing() || checking[0]
                    || !canAdvanceNamedCompanionRoute(instructions, item, appName)) return;
            checking[0] = true;
            continueButton.setEnabled(false);
            continueButton.setAlpha(0.65f);
            // Keep "Continue" unchanged while the package scan runs: replacing
            // it with longer text briefly pushes the fixed "Back" button left.
            syncWizardFooter();
            // PackageManager queries run off the UI thread. No collector change.
            new Thread(() -> {
                final List<String> otherApps = findOtherInstalledCompanionApps(item);
                activity.runOnUiThread(() -> {
                    if (!isCurrentWizardPage(content) || dialog != instructions
                            || activity.isFinishing() || activity.isDestroyed()) return;
                    checking[0] = false;
                    continueButton.setEnabled(true);
                    continueButton.setAlpha(1f);
                    syncWizardFooter();
                    if (!canAdvanceNamedCompanionRoute(instructions, item, appName)) return;
                    if (otherApps.isEmpty()) {
                        showNamedCompanionFinish(item, appName, otherApps);
                    } else {
                        showOtherCompanionWarning(item, appName, otherApps);
                    }
                });
            }, "source-wizard-companion-check").start();
        });

        presentWizardPage(instructions, content, 3);
        final boolean[] lostFocus = {false};
        final ViewTreeObserver.OnWindowFocusChangeListener listener = focused -> {
            if (!focused) lostFocus[0] = true;
            else if (lostFocus[0]) {
                lostFocus[0] = false;
                renderSteps.run();
                fitWizardDialogBody(instructions, content, body);
            }
        };
        final ViewTreeObserver observer = content.getViewTreeObserver();
        observer.addOnWindowFocusChangeListener(listener);
        setWizardPageCleanup(() -> {
            if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(listener);
        });
        renderSteps.run();
    }

    private void addCompanionInstructionStep(final LinearLayout parent, final int number,
            final String title, final String detail) {
        final int[] numberedIcons = {
                R.drawable.source_wizard_step_1,
                R.drawable.source_wizard_step_2,
                R.drawable.source_wizard_step_3,
                R.drawable.source_wizard_step_4,
                R.drawable.source_wizard_step_5
        };
        if (number < 1 || number > numberedIcons.length) return;
        final View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.source_wizard_instruction_step, parent, false);
        final ImageView icon = row.findViewById(R.id.instructionStepIcon);
        icon.setImageResource(numberedIcons[number - 1]);
        icon.setContentDescription(activity.getString(
                R.string.source_wizard_instructions_step_number, number));
        ((TextView) row.findViewById(R.id.instructionStepTitle)).setText(title);
        ((TextView) row.findViewById(R.id.instructionStepDetail)).setText(detail);
        parent.addView(row);
    }

    // Display the active xDrip+ preference and the value the other app
    // should use. This educational card NEVER changes units or warning thresholds.
    private void addCompanionUnitsStep(final LinearLayout parent,
            final int number, final String appName) {
        final View row = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.source_wizard_instruction_units, parent, false);
        final int[] icons = {R.drawable.source_wizard_step_1,
                R.drawable.source_wizard_step_2, R.drawable.source_wizard_step_3,
                R.drawable.source_wizard_step_4, R.drawable.source_wizard_step_5};
        if (number < 1 || number > icons.length) return;
        final ImageView icon = row.findViewById(R.id.instructionUnitsIcon);
        icon.setImageResource(icons[number - 1]);
        icon.setContentDescription(activity.getString(
                R.string.source_wizard_instructions_step_number, number));
        ((TextView) row.findViewById(R.id.instructionUnitsDetail)).setText(
                activity.getString(R.string.source_wizard_instructions_units_detail, appName));
        ((TextView) row.findViewById(R.id.instructionUnitsAppLabel)).setText(appName);
        final int units = Unitized.usingMgDl()
                ? R.string.source_wizard_instructions_units_mgdl
                : R.string.source_wizard_instructions_units_mmol;
        ((TextView) row.findViewById(R.id.instructionUnitsXdripValue)).setText(units);
        ((TextView) row.findViewById(R.id.instructionUnitsAppValue)).setText(units);
        parent.addView(row);
    }

    // Advisory scan from the existing named UiBased wizard catalog.
    // Installed does not mean running; no collector behavior is modified.
    private void collectNamedCompanionPackages(final Tree<Item> node,
            final Set<String> candidates) {
        if (node == null) return;
        final Item route = node.data;
        if (route != null && route.type == DexCollectionType.UiBased
                && route.companionPackages != null) {
            Collections.addAll(candidates, route.companionPackages);
        }
        for (Tree<Item> child : node.getChildren()) {
            collectNamedCompanionPackages(child, candidates);
        }
    }

    private List<String> findOtherInstalledCompanionApps(final Item selected) {
        final android.content.Context context = xdrip.getAppContext();
        if (context == null) return Collections.emptyList();
        final PackageManager manager = context.getPackageManager();
        if (manager == null) return Collections.emptyList();

        final Set<String> candidates = new HashSet<>();
        collectNamedCompanionPackages(root, candidates);
        // Each selected route already lists its known regional package variants.
        if (selected.companionPackages != null) {
            for (String chosen : selected.companionPackages) candidates.remove(chosen);
        }
        // Labels are actual installed app labels, not hard-coded package descriptions.
        final Set<String> foundNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String packageName : candidates) {
            try {
                final ApplicationInfo app = manager.getApplicationInfo(packageName, 0);
                final CharSequence label = manager.getApplicationLabel(app);
                final String name = label == null || label.toString().trim().isEmpty()
                        ? packageName : label.toString().trim();
                foundNames.add(name);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed or not visible; no assertion about active notifications.
            } catch (SecurityException | IllegalArgumentException e) {
                UserError.Log.e(TAG, "Cannot inspect Companion package "
                        + packageName + ": " + e);
            }
        }
        return new ArrayList<>(foundNames);
    }

    // Optional warning, not a readiness failure and not permission to switch sources.
    private void showOtherCompanionWarning(final Item item, final String appName,
            final List<String> otherApps) {
        if (otherApps == null || otherApps.isEmpty()) {
            showNamedCompanionFinish(item, appName, Collections.emptyList());
            return;
        }
        final Dialog warning = getWizardHostDialog();
        final View content = LayoutInflater.from(warning.getContext())
                .inflate(R.layout.dialog_source_wizard_companion_warning, null);
        final LinearLayout apps = content.findViewById(R.id.companionWarningApps);
        final LayoutInflater inflater = LayoutInflater.from(content.getContext());
        // List selected app only when installed; this does not prove active readings.
        if (inspectCompanionPackages(item.companionPackages) == CompanionInstallState.INSTALLED) {
            final TextView selectedRow = (TextView) inflater.inflate(
                    android.R.layout.simple_list_item_1, apps, false);
            selectedRow.setText(appName + "  ·  "
                    + activity.getString(R.string.source_wizard_companion_warning_selected));
            selectedRow.setTextSize(15f);
            selectedRow.setMinHeight(Math.round(convertDpToPixel(48)));
            selectedRow.setPadding(Math.round(convertDpToPixel(14)), 0,
                    Math.round(convertDpToPixel(14)), 0);
            apps.addView(selectedRow);
            final View divider = new View(content.getContext());
            divider.setBackgroundResource(R.color.source_wizard_row_divider);
            apps.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(convertDpToPixel(1))));
        }
        for (int i = 0; i < otherApps.size(); i++) {
            final TextView label = (TextView) inflater.inflate(
                    android.R.layout.simple_list_item_1, apps, false);
            label.setText(otherApps.get(i));
            label.setTextSize(15f);
            label.setMinHeight(Math.round(convertDpToPixel(44)));
            label.setPadding(Math.round(convertDpToPixel(14)), 0,
                    Math.round(convertDpToPixel(14)), 0);
            apps.addView(label);
            if (i + 1 < otherApps.size()) {
                final View divider = new View(content.getContext());
                divider.setBackgroundResource(R.color.source_wizard_row_divider);
                apps.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(convertDpToPixel(1))));
            }
        }
        content.findViewById(R.id.companionWarningBack).setOnClickListener(v -> {
            showNamedCompanionInstructions(item, appName);
        });
        content.findViewById(R.id.companionWarningProceed).setOnClickListener(v -> {
            if (!warning.isShowing()
                    || !canAdvanceNamedCompanionRoute(warning, item, appName)) return;
            showNamedCompanionFinish(item, appName, otherApps);
        });

        presentWizardPage(warning, content, 4);
    }

    // Reuse the same mandatory Notification Listener check at the two forward
    // boundaries. A missing/unknown right never silently switches the source.
    private boolean canAdvanceNamedCompanionRoute(final Dialog current, final Item item,
            final String appName) {
        final SourceWizardReadiness.Result result = SourceWizardReadiness.inspect(
                activity, SourceWizardReadiness.Requirement.NOTIFICATION_LISTENER);
        if (result.state == SourceWizardReadiness.State.READY) return true;
        current.dismiss();
        if (result.state == SourceWizardReadiness.State.UNKNOWN) {
            showNotificationPermissionCheckError();
        } else {
            showNamedCompanionPermissions(item, appName);
        }
        return false;
    }

    // SW-011: only an explicit Done can alter the current data source.
    // No extra "Are you sure?" and no requirement for a fresh glucose reading.
    private void showNamedCompanionFinish(final Item item, final String appName,
            final List<String> otherApps) {
        final Dialog finish = getWizardHostDialog();
        final View content = LayoutInflater.from(finish.getContext())
                .inflate(R.layout.dialog_source_wizard_finish, null);
        ((TextView) content.findViewById(R.id.finishMessage))
                .setText(activity.getString(R.string.source_wizard_finish_message, appName));
        // Educational mini-drawer, no real source or navigation side effects.
        final TextView miniGlyph = content.findViewById(R.id.finishMiniMenuGlyph);
        final View miniRows = content.findViewById(R.id.finishMiniMenuRows);
        final View miniHint = content.findViewById(R.id.finishMiniMenuHint);
        final boolean[] miniOpen = {false};
        content.findViewById(R.id.finishMiniMenuToggle).setOnClickListener(v -> {
            miniHint.setVisibility(View.GONE);
            miniOpen[0] = !miniOpen[0];
            miniRows.setVisibility(miniOpen[0] ? View.VISIBLE : View.GONE);
            miniGlyph.setText(miniOpen[0] ? "←" : "☰");
        });
        content.findViewById(R.id.finishMiniMenuMaster).setOnClickListener(v -> {
            miniHint.setVisibility(View.GONE);
            miniOpen[0] = false;
            miniRows.setVisibility(View.GONE);
            miniGlyph.setText("☰");
        });
        final TextView done = content.findViewById(R.id.finishDone);

        content.findViewById(R.id.finishBack).setOnClickListener(v -> {
            if (otherApps.isEmpty()) {
                showNamedCompanionInstructions(item, appName);
            } else {
                showOtherCompanionWarning(item, appName, otherApps);
            }
        });
        done.setOnClickListener(v -> {
            if (!finish.isShowing() || !done.isEnabled()) return;
            // Mandatory permission can be revoked while the wizard is open.
            if (!canAdvanceNamedCompanionRoute(finish, item, appName)) return;
            if (!item.isNamedCompanionRoute()) return;
            done.setEnabled(false); // Guard against a second Done tap.
            done.setAlpha(0.65f);
            syncWizardFooter();
            // The same activation calls as the legacy source picker, but without
            // repeating its question after this explicit final confirmation.
            final DexCollectionType previousType = DexCollectionType.getDexCollectionType();
            final DexCollectionType selectedType = item.getCollectionType();
            DexCollectionType.setDexCollectionType(selectedType);
            finish.dismiss();
            DexCollectionHelper.assistance(activity, selectedType);
            sw = null;

            // The navigation drawer is built from the active collector. When a
            // named Companion route changes it, refresh the hosting Activity
            // after the dialog closes so the drawer has no stale row positions.
            // No menu or collector implementation changes are needed.
            if (previousType != selectedType) {
                final Activity host = activity;
                host.getWindow().getDecorView().post(() -> {
                    if (!host.isFinishing() && !host.isDestroyed()) host.recreate();
                });
            }
        });

        presentWizardPage(finish, content, 5);
    }

    // Exceptional case only. "Report" is a local, no-network placeholder for now.
    private void showNotificationPermissionCheckError() {
        UserError.Log.e(TAG, "Source Wizard: notification permission check failed");
        final Dialog error = new Dialog(activity, android.R.style.Theme_Holo_Dialog);
        error.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View content = LayoutInflater.from(error.getContext())
                .inflate(R.layout.dialog_source_wizard_permissions_error, null);
        final Drawable background = activity.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
        }
        content.setBackground(background);
        error.setContentView(content);
        error.setCancelable(false);
        dialog = error;
        content.findViewById(R.id.permissionsErrorReport).setOnClickListener(v -> {
            // Prototype only: no report or personal data sent.
            error.dismiss();
            getTreeDialog(null);
        });
        try {
            error.show();
            final Window window = error.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(screenWidth - Math.round(convertDpToPixel(12)),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Cannot display notification permission check error: " + e);
            getTreeDialog(null);
        }
    }

    private enum CompanionInstallState { INSTALLED, MISSING, UNKNOWN }

    private static CompanionInstallState inspectCompanionPackages(final String[] packages) {
        if (packages == null || packages.length == 0) return CompanionInstallState.UNKNOWN;
        final android.content.Context context = xdrip.getAppContext();
        if (context == null) return CompanionInstallState.UNKNOWN;
        final PackageManager manager = context.getPackageManager();
        if (manager == null) return CompanionInstallState.UNKNOWN;
        for (String name : packages) {
            try {
                if (manager.getPackageInfo(name, 0) != null) {
                    return CompanionInstallState.INSTALLED;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try another regional package variant.
            } catch (SecurityException | IllegalArgumentException e) {
                return CompanionInstallState.UNKNOWN;
            }
        }
        return CompanionInstallState.MISSING;
    }

    private static void updateCompanionContinue(final TextView button,
            final CompanionInstallState state, final boolean bypass) {
        final boolean enabled = state != CompanionInstallState.MISSING || bypass;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.65f);
    }

    // Common, adaptive target height for the named post-picker dialogs.
    // Fill spare space before the footer; never force scrolling on a normal screen.
    private void fitWizardDialogBody(final Dialog panel, final View content,
            final ScrollView body) {
        if (panel == wizardHostDialog) return; // Shared viewport has fixed dimensions.
        body.post(() -> {
            if (!panel.isShowing() || body.getChildCount() == 0) return;
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            final int maxBody = Math.max(0,
                    screenHeight - Math.round(convertDpToPixel(170)));
            final int preferredTotal = Math.round(screenHeight * 0.73f);
            final int contentHeight = body.getChildAt(0).getHeight();
            final int availableExtra = Math.max(0, preferredTotal - content.getHeight());
            final int targetHeight = Math.min(maxBody, contentHeight + availableExtra);
            final ViewGroup.LayoutParams params = body.getLayoutParams();
            if (params.height != targetHeight) {
                params.height = targetHeight;
                body.setLayoutParams(params);
            }
        });
    }

    // SW-008: the summary uses exactly the same six read-only Requirement
    // checks shown in the popup. UNKNOWN is not treated as READY or MISSING.
    // No source selection, permission requests or collector restarts here.
    private void refreshReadinessSummary() {
        if (readinessItem == null || activity == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        int ready = 0;
        int missing = 0;
        int unknown = 0;
        for (SourceWizardReadiness.Requirement requirement
                : SourceWizardReadiness.Requirement.values()) {
            final SourceWizardReadiness.Result result =
                    SourceWizardReadiness.inspect(activity, requirement);
            switch (result.state) {
                case READY: ready++; break;
                case MISSING: missing++; break;
                default: unknown++; break;
            }
        }
        if (readinessItem.setReadinessSummary(ready, missing, unknown) && listModel != null) {
            listModel.refreshVisibleRow(readinessItem);
        }
    }

    // SW-008 readiness window: stability and Notification Listener are live;
    // Every general-readiness row runs its real check; OEM app lock remains unknown by design.
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
        // Bluetooth and Location are independent live checks in the general,
        // nonblocking readiness window. Route-specific requirements come later.
        startChecks.add(addLiveReadinessRow(readiness, connection,
                R.string.source_wizard_readiness_bluetooth,
                SourceWizardReadiness.Requirement.BLUETOOTH,
                false, true, cancelChecks));
        startChecks.add(addLiveReadinessRow(readiness, connection,
                R.string.source_wizard_readiness_location,
                SourceWizardReadiness.Requirement.LOCATION,
                true, true, cancelChecks));

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

        // Give the popup its final scrollable height before it becomes visible;
        // resizing an already drawn Holo Dialog was causing an extra jump.
        final ScrollView readinessBody = content.findViewById(R.id.readinessPreviewScroll);
        final LinearLayout.LayoutParams bodyParams =
                (LinearLayout.LayoutParams) readinessBody.getLayoutParams();
        bodyParams.height = 0;
        bodyParams.weight = 1f;
        readinessBody.setLayoutParams(bodyParams);

        final Drawable background = activity.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
        }
        content.setBackground(background);
        readiness.setContentView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        try {
            final Window window = readiness.getWindow();
            if (window != null) {
                // A separate floating popup, not a page in the Source Wizard.
                // Its Android window animation covers both close and Back.
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setWindowAnimations(R.style.SourceWizardReadinessWindowAnimation);
                final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(screenWidth - Math.round(convertDpToPixel(12)),
                        wizardWindowHeight());
            }
            readiness.show();
            final ViewTreeObserver focusObserver = content.getViewTreeObserver();
            focusObserver.addOnWindowFocusChangeListener(focusListener);
            readiness.setOnDismissListener(ignored -> {
                for (Runnable cancel : cancelChecks) cancel.run();
                if (focusObserver.isAlive()) {
                    focusObserver.removeOnWindowFocusChangeListener(focusListener);
                }
                // The chooser stays open behind this dialog; refresh its
                // compact counts after returning from Android Settings.
                refreshReadinessSummary();
            });
            for (Runnable check : startChecks) check.run();
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
        fix.setOnClickListener(v -> {
            // Background Location on Android 10+ is configured in app settings.
            // Explain the manual steps before leaving the wizard.
            if (requirement == SourceWizardReadiness.Requirement.LOCATION
                    && SourceWizardReadiness.needsLocationPermission(activity)) {
                showLocationPermissionInstructions();
            } else {
                SourceWizardReadiness.requestFix(activity, requirement);
            }
        });
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
                    description = R.string.source_wizard_readiness_state_ready;
                    break;
                case MISSING:
                    drawable = R.drawable.source_wizard_readiness_missing;
                    description = R.string.source_wizard_readiness_state_missing;
                    break;
                default:
                    drawable = R.drawable.source_wizard_readiness_unknown;
                    description = R.string.source_wizard_readiness_state_unknown;
                    break;
            }
            icon.setImageResource(drawable);
            icon.setContentDescription(activity.getString(label) + ": " + activity.getString(description));
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
                        activity.getString(label) + ": "
                                + activity.getString(R.string.source_wizard_readiness_state_checking));
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

    private void showLocationPermissionInstructions() {
        final int message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? R.string.source_wizard_readiness_location_permission_steps_background
                : R.string.source_wizard_readiness_location_permission_steps_foreground;
        new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK)
                .setTitle(R.string.please_allow_permission)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.source_wizard_readiness_allow, (dialog, which) ->
                        SourceWizardReadiness.requestFix(activity,
                                SourceWizardReadiness.Requirement.LOCATION))
                .show();
    }

    // SW-009: compact, scrollable Source Wizard-style help for OEM recent-app locking.
    // Read-only UI: no collector changes, no settings changes and no guessed status.
    private void showAppLockHelp() {
        final Dialog help = new Dialog(activity, android.R.style.Theme_Holo_Dialog);
        help.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View content = LayoutInflater.from(help.getContext())
                .inflate(R.layout.dialog_source_wizard_app_lock_help, null);

        final Drawable background = activity.getDrawable(R.drawable.holo_background);
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
        }
        content.setBackground(background);
        content.findViewById(R.id.appLockHelpClose).setOnClickListener(v -> help.dismiss());

        final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        final int windowWidth = screenWidth - Math.round(convertDpToPixel(40));
        final ScrollView body = content.findViewById(R.id.appLockHelpScroll);
        // Measure before showing, to avoid the height jump seen in early SW-008.
        content.measure(
                View.MeasureSpec.makeMeasureSpec(windowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                        screenHeight - Math.round(convertDpToPixel(90)),
                        View.MeasureSpec.AT_MOST));
        final int maxBodyHeight = Math.round(screenHeight * 0.58f);
        if (body.getMeasuredHeight() > maxBodyHeight) {
            final ViewGroup.LayoutParams params = body.getLayoutParams();
            params.height = maxBodyHeight;
            body.setLayoutParams(params);
        }

        help.setContentView(content);
        final Window window = help.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Test: isolate the Lottie preview from the dialog window animation.
            window.setWindowAnimations(0);
        }
        help.show();
        if (window != null) {
            window.setLayout(windowWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showReadinessHelp(final SourceWizardReadiness.Requirement requirement,
            @StringRes final int label) {
        if (requirement == SourceWizardReadiness.Requirement.APP_LOCK) {
            showAppLockHelp();
            return;
        }
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
            case BLUETOOTH:
                message = R.string.source_wizard_readiness_bluetooth_help;
                break;
            case LOCATION:
                message = R.string.source_wizard_readiness_location_help;
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

    // A route is identified independently of its localized one-line label.
    // EXISTING_CATALOG records provenance, not universal verified compatibility.
    private enum RouteEvidence { EXISTING_CATALOG, NEEDS_DEVELOPER_REVIEW }

    private static final class SourceRoute {
        final String family;
        final String model;
        final String routeId;
        final String routeLabel;
        final String appId;
        final String appDisplayName;
        final String appSentenceName;
        final RouteEvidence evidence;
        SourceRoute(String family, String model, String routeId, String routeLabel,
                String appId, String appDisplayName, String appSentenceName,
                RouteEvidence evidence) {
            this.family = family;
            this.model = model;
            this.routeId = routeId;
            this.routeLabel = routeLabel;
            this.appId = appId;
            this.appDisplayName = appDisplayName;
            this.appSentenceName = appSentenceName;
            this.evidence = evidence;
        }
    }

    // Medtronic has only single-route device rows. Guardian, pumps and
    // standalone Simplera must never be combined into one app identity.
    private void addMedtronicCompanion(final Tree<Item> parent,
            @StringRes final int modelName, @StringRes final int routeName,
            @DrawableRes final int icon, final String routeId,
            final String appId, final String appDisplayName,
            final String appSentenceName, final boolean isPump,
            final String... packages) {
        final String model = gs(modelName);
        final String route = gs(routeName);
        final Item item = new Item(model + "\n" + route,
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, icon)
                .withDeviceIcon()
                .withRouteMetadata(new SourceRoute("Medtronic", model, routeId,
                        route, appId, appDisplayName, appSentenceName,
                        RouteEvidence.EXISTING_CATALOG))
                .withCompanionPackages(packages);
        if (isPump) item.withPumpDevice();
        parent.addChild(item);
    }

    // Group by device, keep each route bound to exactly one app package.
    // This is UI metadata only; it does not filter UiBasedCollector notifications.
    private Tree<Item> addMicrotechModel(final Tree<Item> parent,
            @StringRes final int modelName, @DrawableRes final int icon) {
        return parent.addChild(new Item(gs(modelName), (String) null, icon)
                .withModelGroup());
    }

    private void addMicrotechRoute(final Tree<Item> parent,
            @StringRes final int modelName, final String routeId,
            @StringRes final int routeName, @StringRes final int summaryName,
            final DexCollectionType type, final RouteEvidence evidence,
            final String appId, final String appDisplayName,
            final String appSentenceName, final String... packages) {
        final String label = gs(routeName);
        final Item item = new Item(label, gs(summaryName), type, 0)
                .withCompactRoute()
                .withRouteMetadata(new SourceRoute("MicroTech", gs(modelName), routeId,
                        label, appId, appDisplayName, appSentenceName, evidence));
        if (packages != null && packages.length > 0) item.withCompanionPackages(packages);
        parent.addChild(item);
    }

    // Single-route sensors are direct leaves: model icon and a visible
    // app-route subtitle, without an otherwise empty intermediate stack.
    private void addMicrotechSingleRoute(final Tree<Item> parent,
            @StringRes final int modelName, @StringRes final int routeName,
            @StringRes final int summaryName, @DrawableRes final int icon,
            final String routeId, final DexCollectionType type,
            final RouteEvidence evidence, final String appId,
            final String appDisplayName, final String appSentenceName,
            final String... packages) {
        final String model = gs(modelName);
        final String routeLabel = gs(routeName);
        final Item item = new Item(model + "\n" + routeLabel,
                gs(summaryName), type, icon)
                .withDeviceIcon()
                .withRouteMetadata(new SourceRoute("MicroTech", model, routeId,
                        routeLabel, appId, appDisplayName, appSentenceName, evidence));
        if (packages != null && packages.length > 0) item.withCompanionPackages(packages);
        parent.addChild(item);
    }

    // Eversense shares the compact model/route UI with Libre and Dexcom.
    // The transmitter artwork is temporary until model-specific icons arrive.
    private Tree<Item> addEversenseModel(final Tree<Item> parent,
            @StringRes final int modelName) {
        return parent.addChild(new Item(gs(modelName), (String) null,
                R.drawable.eversence_360).withModelGroup());
    }

    private void addEversenseRoute(final Tree<Item> parent,
            @StringRes final int modelName, final String routeId,
            @StringRes final int routeName, @StringRes final int summaryName,
            final DexCollectionType type, final RouteEvidence evidence,
            final String appId, final String appDisplayName,
            final String appSentenceName, final String... packages) {
        final String label = gs(routeName);
        final Item item = new Item(label, gs(summaryName), type, 0)
                .withCompactRoute()
                .withRouteMetadata(new SourceRoute("Eversense", gs(modelName), routeId,
                        label, appId, appDisplayName, appSentenceName, evidence));
        if (packages != null && packages.length > 0) item.withCompanionPackages(packages);
        parent.addChild(item);
    }

    // SW-020: Dexcom model-first routes reuse the metadata-backed Libre
    // Companion flow; direct routes retain their existing BLE entrypoint.
    private Tree<Item> addDexcomModel(final Tree<Item> parent,
            @StringRes final int modelName, @DrawableRes final int icon) {
        return parent.addChild(new Item(gs(modelName), (String) null, icon)
                .withModelGroup());
    }

    private void addDexcomRoute(final Tree<Item> parent,
            @StringRes final int modelName, final String routeId,
            @StringRes final int routeName, @StringRes final int summaryName,
            final DexCollectionType type, final RouteEvidence evidence,
            final String appId, final String appDisplayName,
            final String appSentenceName, final String... packages) {
        final String label = gs(routeName);
        final Item item = new Item(label, gs(summaryName), type, 0)
                .withCompactRoute()
                .withRouteMetadata(new SourceRoute("Dexcom", gs(modelName), routeId,
                        label, appId, appDisplayName, appSentenceName, evidence));
        if (packages != null && packages.length > 0) item.withCompanionPackages(packages);
        parent.addChild(item);
    }

    private Tree<Item> addLibreModel(final Tree<Item> family, @StringRes final int modelName) {
        return family.addChild(new Item(gs(modelName), (String) null,
                R.drawable.libra_1234_v3_icon).withModelGroup());
    }

    private void addLibreRoute(final Tree<Item> parent, final String modelCode,
            @StringRes final int modelName, final String routeCode,
            @StringRes final int routeName, @StringRes final int summary,
            final DexCollectionType type, final String appId,
            final String appDisplayName, final String appSentenceName,
            final String... packages) {
        final String label = gs(routeName);
        final Item item = new Item(label, gs(summary), type, 0)
                .withCompactRoute()
                .withRouteMetadata(new SourceRoute("FreeStyle Libre", gs(modelName),
                        "libre." + modelCode + "." + routeCode, label,
                        appId, appDisplayName, appSentenceName,
                        RouteEvidence.EXISTING_CATALOG));
        if (packages != null && packages.length > 0) item.withCompanionPackages(packages);
        parent.addChild(item);
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
        private String[] companionPackages;
        private SourceRoute routeMetadata;
        private boolean modelGroup;
        private boolean compactRoute;
        private boolean section;
        private boolean deviceIcon;
        private boolean pumpDevice;
        private boolean auxiliaryTitle;
        private boolean fullWidthDivider;
        private boolean informationText;
        private boolean broadcastInformation;
        private boolean readinessAction;
        private boolean callToAction;
        private int readinessReady;
        private int readinessMissing;
        private int readinessUnknown;
        private boolean readinessSummaryAvailable;
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

        Item withPumpDevice() {
            pumpDevice = true;
            return this;
        }

        Item withModelGroup() {
            modelGroup = true;
            deviceIcon = true;
            return this;
        }

        Item withCompactRoute() {
            compactRoute = true;
            return this;
        }

        Item withRouteMetadata(final SourceRoute route) {
            routeMetadata = route;
            return this;
        }

        public boolean isModelGroup() { return modelGroup; }
        public boolean isCompactRoute() { return compactRoute; }
        public int getPlateMinHeight() {
            return Math.round(convertDpToPixel(compactRoute ? 52 : 62));
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

        boolean setReadinessSummary(int ready, int missing, int unknown) {
            if (readinessSummaryAvailable && readinessReady == ready
                    && readinessMissing == missing && readinessUnknown == unknown) return false;
            readinessReady = ready;
            readinessMissing = missing;
            readinessUnknown = unknown;
            readinessSummaryAvailable = true;
            return true;
        }

        public boolean isReadinessReadyVisible() {
            return readinessAction && readinessSummaryAvailable && readinessReady > 0;
        }

        public boolean isReadinessMissingVisible() {
            return readinessAction && readinessSummaryAvailable && readinessMissing > 0;
        }

        public boolean isReadinessUnknownVisible() {
            return readinessAction && readinessSummaryAvailable && readinessUnknown > 0;
        }

        public String getReadinessReadyCountText() {
            return Integer.toString(readinessReady);
        }

        public String getReadinessMissingCountText() {
            return Integer.toString(readinessMissing);
        }

        public String getReadinessUnknownCountText() {
            return Integer.toString(readinessUnknown);
        }

        public String getReadinessSummaryDescription() {
            return readinessSummaryAvailable
                    ? activity.getString(R.string.source_wizard_readiness_summary_accessibility,
                            readinessReady, readinessMissing, readinessUnknown)
                    : "";
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
            return !section && (!isGroup() || modelGroup) && !compactRoute
                    && !auxiliaryTitle && !informationText
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
            return Math.round(convertDpToPixel(compactRoute ? 84 : (isIconVisible() ? 84 : 22)));
        }

        Item withConfirmationName(String sourceName) {
            this.confirmationName = sourceName;
            return this;
        }

        Item withCompanionPackages(final String... packages) {
            companionPackages = packages.clone();
            return this;
        }

        boolean isNamedCompanionRoute() {
            return type == DexCollectionType.UiBased && !callToAction
                    && (routeMetadata != null ? routeMetadata.appId != null : name.contains("→"));
        }

        public String getConfirmationName() {
            if (routeMetadata != null) {
                return routeMetadata.model
                        + (routeMetadata.routeLabel.startsWith("→") ? " " : " — ")
                        + routeMetadata.routeLabel;
            }
            // Retain legacy confirmation text for routes not yet migrated.
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
            return Math.round(convertDpToPixel(compactRoute ? 84 : (depth > 0 ? 22 : 12)));
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
