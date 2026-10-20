package com.eveningoutpost.dexdrip.utilitymodels;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
    private AlertDialog dialog;
    private Activity activity;
    // Expansion belongs to this wizard instance, so it survives the confirmation dialog.
    private final HashSet<Tree<Item>> expandedBranches = new HashSet<>();
    private ViewModel listModel;
    // Create the dialog decision tree
    private Tree<Item> root = new Tree<>(new Item(gs(R.string.choose_data_source), gs(R.string.which_system_do_you_use)));

    {
        root.addChild(new Item("Dex", DexCollectionType.DexcomG6, R.drawable.dex_icon));

        Tree<Item> libre = root.addChild(new Item(gs(R.string.libre), gs(R.string.what_type_of_libre_bridge_device_do_you_use), R.drawable.libre_icon_image));
        {
            libre.addChild(new Item(gs(R.string.bluetooth_bridge_device_blucon_limitter_bluereader_tomato_etc), DexCollectionType.LimiTTer, R.drawable.bluereader_icon));
            libre.addChild(new Item(gs(R.string.librealarm_app_using_sony_smartwatch), DexCollectionType.LibreAlarm, R.drawable.ic_watch_grey600_48dp));
            libre.addChild(new Item(gs(R.string.libre_patched), DexCollectionType.LibreReceiver, R.drawable.libre_icon_image));

        }
        Tree<Item> other = root.addChild(new Item(gs(R.string.other), gs(R.string.which_type_of_device), R.drawable.wikimedia_question_mark));
        {
            other.addChild(new Item("xDrip Sync Follower", DexCollectionType.Follower, R.drawable.x_sync_follow_icon));
            other.addChild(new Item("Inter-app broadcast", DexCollectionType.NSEmulator, R.drawable.mm600_series));
            other.addChild(new Item("CareSens Air", DexCollectionType.NSEmulator, R.drawable.caresens_air_icon_image));
            other.addChild(new Item("Medtrum A6 / S7", DexCollectionType.Medtrum, R.drawable.a6_icon));
            other.addChild(new Item("SmartGuide", DexCollectionType.GluPro,R.drawable.smartguide_jamorham));
            other.addChild(new Item("Nightscout Follower", DexCollectionType.NSFollow, R.drawable.nsfollow_icon));
            other.addChild(new Item("Dex Share Follower", DexCollectionType.SHFollow, R.drawable.nsfollow_icon));
            //
            other.addChild(new Item("EverSense", DexCollectionType.NSEmulator, R.drawable.wikimedia_eversense_icon_pbroks13));
        }
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

    // Use the same Android Holo family as xDrip's Preferences screen.
    // The legacy source selection and post-selection assistance stay unchanged.
    public void getTreeDialog(Tree<Item> branch) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_DARK);
        DialogTreeSelectorBinding binding = null;

        if (branch == null || !branch.data.isCollectionType()) {
            binding = DialogTreeSelectorBinding.inflate(LayoutInflater.from(builder.getContext()));
            listModel = new ViewModel();
            binding.setViewModel(listModel);
            // An expanded group must not fade out all other rows.
            binding.sourceWizardList.setItemAnimator(null);
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            // Keep title and Skip fixed, while the list uses most available screen height.
            binding.sourceWizardList.getLayoutParams().height = Math.round(screenHeight * 0.60f);
            builder.setTitle(root.data.name);
            builder.setMessage(root.data.description);
            builder.setView(binding.getRoot());
            // Do not silently dismiss the setup dialog by touching the background.
            builder.setCancelable(false);
            builder.setNegativeButton(R.string.source_wizard_skip, (ignored, which) -> {
                dismissDialog(); // Same behavior as previously cancelling by tapping outside.
                sw = null;
            });
        } else {
            final Item item = branch.data;
            builder.setTitle(R.string.are_you_sure);
            builder.setMessage(activity.getString(R.string.set_data_source_to, item.name));
            builder.setPositiveButton(R.string.yes, (ignored, which) -> {
                // Keep the original per-source setup path unchanged.
                DexCollectionType.setDexCollectionType(item.getCollectionType());
                dismissDialog();
                DexCollectionHelper.assistance(activity, item.getCollectionType());
                sw = null;
            });
            builder.setNegativeButton(R.string.no, (ignored, which) -> getTreeDialog(null));
        }

        dismissDialog();
        dialog = builder.create();
        try {
            dialog.show();
            if (binding != null) {
                final Window window = dialog.getWindow();
                if (window != null) {
                    // Reuse xDrip's own Holo gradient instead of defining another palette.
                    final Drawable background = builder.getContext().getDrawable(R.drawable.holo_background);
                    if (background instanceof GradientDrawable) {
                        ((GradientDrawable) background.mutate()).setCornerRadius(convertDpToPixel(6));
                    }
                    window.setBackgroundDrawable(background);
                    final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                    window.setLayout(screenWidth - Math.round(convertDpToPixel(12)), WindowManager.LayoutParams.WRAP_CONTENT);
                }
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception when trying to show source wizard: " + e);
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

        public void onClick(View v) {
            if (node == null) return;
            if (isCollectionType()) {
                getTreeDialog(node);
            } else if (listModel != null) {
                listModel.toggle(node);
            }
        }

        public int getRowInset() {
            return Math.round(convertDpToPixel(depth > 0 ? 10 : 0));
        }

        public Drawable getRowBackground() {
            return activity.getDrawable(depth > 0
                    ? R.drawable.source_wizard_nested_background
                    : android.R.color.transparent);
        }

        public String getDisplayTitle() {
            final int colon = depth > 0 ? name.indexOf(':') : -1;
            return colon > 0 ? name.substring(0, colon).trim() : name;
        }

        public String getDisplaySummary() {
            final int colon = depth > 0 ? name.indexOf(':') : -1;
            return colon > 0 ? name.substring(colon + 1).trim() : "";
        }

        public boolean isSummaryVisible() {
            return !getDisplaySummary().isEmpty();
        }

        public boolean isGroup() {
            return !isCollectionType();
        }

        public String getIndicator() {
            return isGroup() ? (expandedBranches.contains(node) ? "⌄" : "›") : "";
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

        private void rebuild() {
            items.clear();
            for (Tree<Item> child : root.getChildren()) insertVisible(child, 0, items.size());
        }

        // Insert or remove only the children of the tapped group (no full-list redraw).
        void toggle(Tree<Item> node) {
            final int position = indexOf(node);
            if (position < 0) return;
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
            // Refresh only the chevron of the expanded/collapsed parent row.
            items.set(position, node.data);
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
