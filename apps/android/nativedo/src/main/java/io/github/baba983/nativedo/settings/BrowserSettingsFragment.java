package io.github.baba983.nativedo.settings;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Local browser preference on upstream cells; no account settings or shared-cookie deletion. */
public class BrowserSettingsFragment extends UniversalFragment {

    private static final int ROW_OPEN_IN_APP = 1;

    @Override
    protected CharSequence getTitle() {
        return "浏览器设置";
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final Context context = getContext();

        items.add(UItem.asRippleCheck(ROW_OPEN_IN_APP, "在应用内打开链接")
                .setChecked(LinkOpenSettings.INSTANCE.isInApp(context)));
        items.add(UItem.asShadow("关闭后普通链接交给系统的自定义标签页；本站话题链接仍在本应用内打开。"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_OPEN_IN_APP) {
            // The reader's choice; the browser hook reads the same preference on the next link.
            final boolean inApp = !LinkOpenSettings.INSTANCE.isInApp(getContext());
            LinkOpenSettings.INSTANCE.setInApp(getContext(), inApp);
            ((TextCheckCell) view).setChecked(inApp);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        listView.setPadding(0, listView.getPaddingTop(), 0, bottom);
        listView.setClipToPadding(false);
    }
}
