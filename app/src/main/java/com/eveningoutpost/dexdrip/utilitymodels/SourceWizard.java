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
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.annotation.StringRes;
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
        // Sensors and systems: users find their device before the connection method.
        root.addChild(new Item(gs(R.string.source_wizard_sensors_section), true));

        Tree<Item> dex = root.addChild(new Item("Dexcom",
                (String) null, R.drawable.dex_icon));
        // Preserve the original direct-connection DexcomG6 -> DexcomG5 setup path.
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_models),
                gs(R.string.source_wizard_dexcom_direct_summary),
                DexCollectionType.DexcomG6, R.drawable.dexcom_g6_g7_icon)
                .withDeviceIcon().withConfirmationName("Dexcom"));
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_app),
                gs(R.string.source_wizard_dexcom_app_summary),
                DexCollectionType.UiBased, R.drawable.source_wizard_source_placeholder));

        // Device-first Libre choices. Several human-readable routes intentionally
        // share collectors; the actual acquisition implementations are untouched.
        Tree<Item> libre = root.addChild(new Item("FreeStyle Libre",
                (String) null, R.drawable.libre_icon_image));
        // Primary methods start with the model, rather than collector names.
        libre.addChild(new Item(gs(R.string.source_wizard_libre_one_bridge),
                gs(R.string.source_wizard_libre_one_bridge_summary),
                DexCollectionType.LimiTTer, R.drawable.libre_2_icon)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_one_alarm),
                gs(R.string.source_wizard_libre_one_alarm_summary),
                DexCollectionType.LibreAlarm, R.drawable.ic_watch_grey600_48dp)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_two_direct),
                gs(R.string.source_wizard_libre_two_direct_summary),
                DexCollectionType.LimiTTer, R.drawable.libre_2_icon)
                .withDeviceIcon());
        libre.addChild(new Item(gs(R.string.source_wizard_libre_juggluco),
                gs(R.string.source_wizard_libre_juggluco_summary),
                DexCollectionType.NSEmulator, R.drawable.libre_123_icon)
                .withDeviceIcon());

        // Optional methods: Libre 1 and 2 share the same network/combined
        // collector types. Keep a single visible choice for each method.
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
                DexCollectionType.LibreReceiver, R.drawable.libre_123_icon)
                .withDeviceIcon());

        // App-mediated MicroTech connections: broadcast from AiDEX App or notifications from LinX App.
        // Legacy GlucoRx AiDEX uses its dedicated broadcast receiver; newer
        // AiDEX X / LinX apps use the existing notification-based UiBased mode.
        Tree<Item> microtech = root.addChild(new Item(
                gs(R.string.source_wizard_microtech_group),
                gs(R.string.source_wizard_microtech_examples),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_microtech_examples)));
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_glucorx),
                gs(R.string.source_wizard_microtech_glucorx_summary),
                DexCollectionType.AidexReceiver, R.drawable.glucorx_icon)
                .withDeviceIcon());
        microtech.addChild(new Item(gs(R.string.source_wizard_microtech_linx),
                gs(R.string.source_wizard_microtech_linx_summary),
                DexCollectionType.UiBased, R.drawable.aidex_x_icon)
                .withDeviceIcon());
        // Generic MicroTech route opens the shared Companion App explanation,
        // not an immediate data-source confirmation.
        Item microtechOther = new Item(gs(R.string.source_wizard_microtech_other),
                (String) null, 0)
                .withAuxiliaryTitle()
                .withSummary(gs(R.string.source_wizard_microtech_other_summary));
        microtech.addChild(microtechOther);

        // Eversense presents its two existing app-based routes under one sensor family.
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

        // One known route per entry: direct, app-based, and inter-app paths live together.
        // Exact model names for some app aliases will be refined separately.
        Tree<Item> otherSensors = root.addChild(new Item(
                gs(R.string.source_wizard_other_sensors),
                gs(R.string.source_wizard_other_sensors),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_other_sensors_examples)));
        otherSensors.addChild(new Item(gs(R.string.source_wizard_medtrum_a6),
                gs(R.string.source_wizard_medtrum_a6_summary),
                DexCollectionType.Medtrum, R.drawable.medtrum_a6_icon)
                .withDeviceIcon());
        otherSensors.addChild(new Item(gs(R.string.source_wizard_smartguide),
                gs(R.string.source_wizard_smartguide_summary),
                DexCollectionType.GluPro, R.drawable.accucheksmartguide_icon)
                .withDeviceIcon());

        // Existing app aliases stay visible under the device-first catalog
        // until their exact sensor model names can be verified.
        addCompanion(otherSensors, "iCan Health", R.drawable.ican3_icon);
        addCompanion(otherSensors, "Ottai", R.drawable.ottai_icon);
        addCompanion(otherSensors, "CamAPS FX");
        addCompanion(otherSensors, "Medtronic (Guardian / MiniMed / Simplera)");
        addCompanion(otherSensors, "Sinocare CGM");
        addCompanion(otherSensors, "SiSensing Eco");
        addCompanion(otherSensors, "Kakao PASTA");
        addCompanion(otherSensors, "Suswel");
        addCompanion(otherSensors, "Glucotech");
        addCompanion(otherSensors, "Diabeloop");
        otherSensors.addChild(new Item("CareSens Air",
                DexCollectionType.NSEmulator, R.drawable.caresens_icon)
                .withDeviceIcon());

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
                gs(R.string.source_wizard_broadcast_explanation), 0).withInformationText());
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

        // Follower and synchronization services, distinct from direct sensors.
        root.addChild(new Item(gs(R.string.source_wizard_sync_section), true));
        Tree<Item> followers = root.addChild(new Item(gs(R.string.source_wizard_followers_group),
                gs(R.string.source_wizard_followers_section),
                R.drawable.x_sync_follow_icon));
        followers.addChild(new Item("xDrip+ Sync Follower", DexCollectionType.Follower,
                R.drawable.x_sync_follow_icon));
        followers.addChild(new Item("Nightscout Follower", DexCollectionType.NSFollow,
                R.drawable.nsfollow_icon));
        followers.addChild(new Item("Dex Share Follower", DexCollectionType.SHFollow,
                R.drawable.nsfollow_icon));
        followers.addChild(new Item("Web Follower", DexCollectionType.WebFollow,
                R.drawable.source_wizard_source_placeholder));
        followers.addChild(new Item("CareLink Follower", DexCollectionType.CLFollow,
                R.drawable.source_wizard_source_placeholder));
    }

    private void addCompanion(Tree<Item> parent, String label) {
        addCompanion(parent, label, R.drawable.source_wizard_source_placeholder, false);
    }

    private void addCompanion(Tree<Item> parent, String label, int drawable) {
        addCompanion(parent, label, drawable, true);
    }

    private void addCompanion(Tree<Item> parent, String label, int drawable, boolean withDeviceIcon) {
        // Individual app labels are aliases for the same existing UiBased collector.
        final Item item = new Item(label, DexCollectionType.UiBased, drawable);
        parent.addChild(withDeviceIcon ? item.withDeviceIcon() : item);
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
            return !section && !isGroup() && !auxiliaryTitle && !informationText && !callToAction;
        }

        public boolean isDeviceIconVisible() {
            return isIconVisible() && deviceIcon && resource != 0;
        }

        public boolean isLightIconPlateVisible() {
            return isDeviceIconVisible() && resource == R.drawable.eversence_360;
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
            if (navigationTarget != null) {
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
            return !section && !isCollectionType() && !informationText && navigationTarget == null;
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

        // A generic device-app link opens the single shared help section.
        void open(Tree<Item> target) {
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
