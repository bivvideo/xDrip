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
    // Navigation is organized by user intent, not by internal collector names.
    // Display-only changes: each leaf still uses an existing DexCollectionType.
    private Tree<Item> root = new Tree<>(new Item(
            gs(R.string.choose_data_source), gs(R.string.which_system_do_you_use)));

    {
        // Sensors and systems: users find their device before the connection method.
        root.addChild(new Item(gs(R.string.source_wizard_sensors_section), true));

        Tree<Item> dex = root.addChild(new Item("Dex",
                gs(R.string.source_wizard_direct_details), R.drawable.dex_icon));
        // Preserve the original direct-connection DexcomG6 -> DexcomG5 setup path.
        dex.addChild(new Item(gs(R.string.source_wizard_direct),
                DexCollectionType.DexcomG6, R.drawable.dex_icon));
        dex.addChild(new Item(gs(R.string.source_wizard_dexcom_app),
                DexCollectionType.UiBased, R.drawable.source_wizard_source_placeholder));

        Tree<Item> libre = root.addChild(new Item(gs(R.string.libre),
                gs(R.string.what_type_of_libre_bridge_device_do_you_use),
                R.drawable.libre_icon_image));
        libre.addChild(new Item(gs(R.string.bluetooth_bridge_device_blucon_limitter_bluereader_tomato_etc),
                DexCollectionType.LimiTTer, R.drawable.bluereader_icon));
        libre.addChild(new Item(gs(R.string.source_wizard_libre_bluetooth_wifi),
                DexCollectionType.LimiTTerWifi, R.drawable.source_wizard_source_placeholder));
        libre.addChild(new Item(gs(R.string.source_wizard_libre_wifi),
                DexCollectionType.LibreWifi, R.drawable.source_wizard_source_placeholder));
        libre.addChild(new Item(gs(R.string.librealarm_app_using_sony_smartwatch),
                DexCollectionType.LibreAlarm, R.drawable.ic_watch_grey600_48dp));
        libre.addChild(new Item(gs(R.string.libre_patched),
                DexCollectionType.LibreReceiver, R.drawable.libre_icon_image));

        Tree<Item> eversense = root.addChild(new Item("Eversense",
                gs(R.string.source_wizard_notifications_details),
                R.drawable.wikimedia_eversense_icon_pbroks13));
        eversense.addChild(new Item(gs(R.string.source_wizard_eversense_app),
                DexCollectionType.UiBased, R.drawable.wikimedia_eversense_icon_pbroks13));
        eversense.addChild(new Item(gs(R.string.source_wizard_eversense_broadcast),
                DexCollectionType.NSEmulator, R.drawable.mm600_series));

        // These devices deserve visible entries, not a hidden "Other" category.
        root.addChild(new Item("Medtrum A6 / S7", DexCollectionType.Medtrum,
                R.drawable.a6_icon));
        root.addChild(new Item("SmartGuide", DexCollectionType.GluPro,
                R.drawable.smartguide_jamorham));
        // CareSens Air is a legacy NSEmulator alias, not a new collector.
        root.addChild(new Item("CareSens Air", DexCollectionType.NSEmulator,
                R.drawable.caresens_air_icon_image));
        root.addChild(new Item("GlucoRx/Aidex App", DexCollectionType.AidexReceiver,
                R.drawable.source_wizard_source_placeholder));

        // App-based paths; branded Dexcom/Eversense shortcuts live above.
        root.addChild(new Item(gs(R.string.source_wizard_apps_section), true));
        Tree<Item> companion = root.addChild(new Item(
                gs(R.string.source_wizard_companion_group),
                gs(R.string.source_wizard_companion_details),
                R.drawable.source_wizard_source_placeholder)
                .withSummary(gs(R.string.source_wizard_companion_examples)));
        addCompanion(companion, "iCan Health");
        addCompanion(companion, "Ottai");
        addCompanion(companion, "CamAPS FX");
        addCompanion(companion, "Medtronic (Guardian / MiniMed / Simplera)");
        addCompanion(companion, "Sinocare CGM");
        addCompanion(companion, "SiSensing Eco");
        addCompanion(companion, "Kakao PASTA");
        addCompanion(companion, "Suswel");
        addCompanion(companion, "Glucotech");
        addCompanion(companion, "Diabeloop");
        addCompanion(companion, gs(R.string.source_wizard_other_notifications));
        root.addChild(new Item("Inter-app broadcast", DexCollectionType.NSEmulator,
                R.drawable.mm600_series));

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
        // Individual app labels are navigation aliases for the same UiBased mode.
        parent.addChild(new Item(label, DexCollectionType.UiBased,
                R.drawable.source_wizard_source_placeholder));
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
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            binding.sourceWizardList.getLayoutParams().height = Math.round(screenHeight * 0.60f);

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
            builder.setMessage(activity.getString(R.string.set_data_source_to, item.name));
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

    // Existing source labels and DexCollectionType mappings are intentionally unchanged.
    public class Item {
        public String name;
        public int resource;
        String description;
        DexCollectionType type;
        private Tree<Item> node;
        private int depth;
        private String summary;
        private boolean section;

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

        public void onClick(View v) {
            if (node == null || section) return;
            if (isCollectionType()) {
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

        public String getDisplayTitle() {
            if (summary != null) return name;
            final int colon = depth > 0 ? name.indexOf(':') : -1;
            return colon > 0 ? name.substring(0, colon).trim() : name;
        }

        public String getDisplaySummary() {
            if (summary != null) return summary;
            final int colon = depth > 0 ? name.indexOf(':') : -1;
            return colon > 0 ? name.substring(colon + 1).trim() : "";
        }

        public boolean isSummaryVisible() {
            return !getDisplaySummary().isEmpty();
        }

        public boolean isSection() {
            return section;
        }

        public boolean isFirstSection() {
            return section && node != null && node == root.getChildren().get(0);
        }

        public boolean isGroup() {
            return !section && !isCollectionType();
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

        // Insert or remove only the children of the tapped group (no full-list redraw).
        void toggle(Tree<Item> node) {
            final int position = indexOf(node);
            if (position < 0) return;
            final Item oldLast = items.isEmpty() ? null : items.get(items.size() - 1);
            if (expandedBranches.remove(node)) {
                final int depth = node.data.depth;
                while (position + 1 < items.size() && items.get(position + 1).depth > depth) {
                    items.remove(position + 1);
                }
            } else {
                expandedBranches.add(node);
                int at = position + 1;
                for (Tree<Item> child : node.getChildren()) {
                    at = insertVisible(child, node.data.depth + 1, at);
                }
            }
            // Also refresh the visibility of the bottom divider whenever the
            // last visible row changes as a result of expanding/collapsing.
            items.set(position, node.data);
            refreshVisibleRow(oldLast);
            if (!items.isEmpty()) refreshVisibleRow(items.get(items.size() - 1));
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
