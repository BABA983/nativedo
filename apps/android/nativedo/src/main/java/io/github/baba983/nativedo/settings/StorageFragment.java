package io.github.baba983.nativedo.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * What the app's caches cost and the button that empties them, drawn the way Telegram's cells
 * draw a settings value.
 *
 * <p>The caches are telegram-shaped because the loaders are: avatars, post images, emoji and
 * attachments are written into the directories upstream's {@code FileLoader} reads, so the same
 * measurement and cleanup upstream's storage screen performs apply here. This screen is our own
 * rather than a hosted {@code CacheControlActivity} because hosting would open Telegram's
 * file-path index database ({@code FilePathDatabase}) and its per-file deletion path writes to
 * it - a Telegram artifact this client deliberately does not create. The measurement and the
 * sweep here cover the same directories, and neither touches the session store the reader's
 * login lives in.</p>
 */
public class StorageFragment extends BaseFragment {

    private static final int ROW_IMAGES = 1;
    private static final int ROW_DOCUMENTS = 2;
    private static final int ROW_OTHER = 3;
    private static final int ROW_CLEAR = 4;

    private UniversalRecyclerView listView;
    private StorageSizes sizes;
    private boolean measuring = true;
    private boolean clearing;

    /** The day/night switch recolors this page in place, like the settings tab's own. */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> {
                if (id != org.telegram.messenger.NotificationCenter.needSetDayNightTheme) {
                    return;
                }
                notifyRowsChanged();
                if (simpleActionBar != null) {
                    simpleActionBar.updateColors();
                    simpleActionBar.invalidate();
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            };

    /** Upstream's simple glass header, the same one its storage screen uses. */
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setTitle("存储使用");
        simpleActionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        simpleActionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        simpleActionBar.setBackground(null);

        listView = new UniversalRecyclerView(this,
                (items, adapter) -> fillItems(items),
                (item, view, position, x, y) -> onClick(item),
                (item, view, position, x, y) -> false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.setVerticalScrollBarEnabled(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = contentView;
        loadSizes();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private void loadSizes() {
        measuring = true;
        notifyRowsChanged();
        StorageLoader.INSTANCE.sizes(loaded -> {
            sizes = loaded;
            measuring = false;
            notifyRowsChanged();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void notifyRowsChanged() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("存储"));
        items.add(UItem.asSettingsCell(ROW_IMAGES, "图片与视频", sizeText(sizes == null ? 0 : sizes.getImagesAndVideos())));
        items.add(UItem.asSettingsCell(ROW_DOCUMENTS, "文件", sizeText(sizes == null ? 0 : sizes.getDocuments())));
        items.add(UItem.asSettingsCell(ROW_OTHER, "其他缓存", sizeText(sizes == null ? 0 : sizes.getOtherCache())));
        items.add(UItem.asShadow(measuring
                ? "正在计算缓存占用…"
                : "共 " + sizeText(sizes == null ? 0 : sizes.getTotal())
                        + "。缓存只包含图片、视频与附件；账号、草稿与阅读进度不在其中，清理缓存不会让你退出登录。"));
        items.add(UItem.asButton(ROW_CLEAR, "清理缓存").red());
    }

    private String sizeText(long bytes) {
        return AndroidUtilities.formatFileSize(bytes);
    }

    private void onClick(UItem item) {
        if (item.id == ROW_CLEAR) {
            confirmClear();
        }
    }

    private void confirmClear() {
        if (getParentActivity() == null || clearing) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("清理缓存");
        builder.setMessage("将删除已缓存的图片、视频与附件，下次打开时会重新下载。登录状态与草稿不受影响。");
        builder.setPositiveButton("清理", (dialog, which) -> clear());
        builder.setNegativeButton("取消", null);
        showDialog(builder.create());
    }

    private void clear() {
        clearing = true;
        StorageLoader.INSTANCE.clear(freed -> {
            clearing = false;
            loadSizes();
            if (getParentActivity() != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle("缓存已清理");
                builder.setMessage("已释放 " + AndroidUtilities.formatFileSize(freed));
                builder.setPositiveButton("好", null);
                showDialog(builder.create());
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(
                themeObserver, org.telegram.messenger.NotificationCenter.needSetDayNightTheme);
        super.onFragmentDestroy();
    }
}
